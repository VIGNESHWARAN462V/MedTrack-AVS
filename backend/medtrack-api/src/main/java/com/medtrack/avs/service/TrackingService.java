package com.medtrack.avs.service;

import com.medtrack.avs.dto.*;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ConflictException;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.repository.AssetAssignmentRepository;
import com.medtrack.avs.repository.AssetRepository;
import com.medtrack.avs.repository.GpsSessionRepository;
import com.medtrack.avs.repository.LocationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medtrack.avs.dto.TrackingTelemetryEvent;
import com.medtrack.avs.event.TelemetryPersistedEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingService {

    private final AssetRepository assetRepository;
    private final GpsSessionRepository gpsSessionRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final GeofenceService geofenceService;

    /**
     * Ingest and persist real mobile GPS telemetry.
     */
    @Transactional
    public LocationTelemetryResponse recordLocation(LocationTelemetryRequest request, User currentUser) {
        // Defensive validation against NaN / Infinity
        if (request.getLatitude() == null || request.getLatitude().isNaN() || request.getLatitude().isInfinite() ||
                request.getLongitude() == null || request.getLongitude().isNaN() || request.getLongitude().isInfinite() ||
                request.getAccuracy() == null || request.getAccuracy().isNaN() || request.getAccuracy().isInfinite()) {
            throw new BadRequestException("Latitude, longitude, and accuracy cannot be null, NaN, or Infinite");
        }

        if (request.getLatitude() < -90.0 || request.getLatitude() > 90.0) {
            throw new BadRequestException("Latitude must be between -90 and 90");
        }
        if (request.getLongitude() < -180.0 || request.getLongitude() > 180.0) {
            throw new BadRequestException("Longitude must be between -180 and 180");
        }
        if (request.getAccuracy() < 0.0) {
            throw new BadRequestException("Accuracy must be positive or zero");
        }

        if (request.getSpeed() != null) {
            if (request.getSpeed().isNaN() || request.getSpeed().isInfinite()) {
                throw new BadRequestException("Speed cannot be NaN or Infinite");
            }
            if (request.getSpeed() < 0.0) {
                throw new BadRequestException("Speed must be positive or zero when present");
            }
        }

        if (request.getHeading() != null) {
            if (request.getHeading().isNaN() || request.getHeading().isInfinite()) {
                throw new BadRequestException("Heading cannot be NaN or Infinite");
            }
            if (request.getHeading() < 0.0 || request.getHeading() > 360.0) {
                throw new BadRequestException("Heading must be between 0 and 360 when present");
            }
        }

        if (request.getRecordedAt() == null || request.getRecordedAt().trim().isEmpty()) {
            throw new BadRequestException("recordedAt timestamp is required");
        }

        Instant recordedAtTime = parseRecordedAt(request.getRecordedAt());

        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "id", request.getAssetId()));

        verifyUserCanTrackAsset(currentUser, asset);

        if (asset.getStatus() == AssetStatus.MAINTENANCE) {
            throw new ConflictException("Asset '" + asset.getAssetCode() + "' is currently in MAINTENANCE and cannot be actively tracked");
        }

        // Verify active GPS session exists - orphan telemetry is strictly rejected
        GpsSession activeSession = gpsSessionRepository.findByAssetIdAndActiveTrue(asset.getId())
                .orElseThrow(() -> new ConflictException("No active GPS tracking session exists for asset: " + asset.getAssetCode() + ". An active session must be started before transmitting telemetry."));

        if (!activeSession.isActive() || activeSession.getEndedAt() != null) {
            throw new ConflictException("GPS tracking session for asset " + asset.getAssetCode() + " is already closed. Telemetry rejected.");
        }

        // Authoritative server-side UTC timestamp model using Instant normalized to microsecond precision
        Instant serverTime = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // Strict invariant: session.startedAt <= location.recordedAt <= next telemetry
        Instant minValidTime = activeSession.getStartedAt();
        if (asset.getLastSeen() != null && asset.getLastSeen().isAfter(minValidTime)) {
            minValidTime = asset.getLastSeen();
        }
        if (serverTime.isBefore(minValidTime)) {
            serverTime = minValidTime;
        }

        // Append to LocationHistory (append-only)
        LocationHistory history = LocationHistory.builder()
                .asset(asset)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracy(request.getAccuracy())
                .speed(request.getSpeed())
                .heading(request.getHeading())
                .source("BROWSER_GPS")
                .recordedAt(serverTime)
                .build();
        locationHistoryRepository.save(history);

        // Update current location on Asset
        asset.setLatitude(request.getLatitude());
        asset.setLongitude(request.getLongitude());
        asset.setGpsAccuracy(request.getAccuracy());
        asset.setLastSeen(serverTime);
        asset.setStatus(AssetStatus.MOVING);
        Asset savedAsset = assetRepository.save(asset);

        // Evaluate active geofences and generate transition alerts inside the transaction
        geofenceService.evaluateGeofences(savedAsset, request.getLatitude(), request.getLongitude(), serverTime);

        log.debug("Telemetry persisted for asset {}: lat={}, lon={}, acc={}m, recordedAt={}",
                asset.getAssetCode(), request.getLatitude(), request.getLongitude(), request.getAccuracy(), serverTime);

        // Broadcast over STOMP WebSocket after successful transaction commit
        String deptName = (savedAsset.getDepartment() != null) ? savedAsset.getDepartment().getName() : null;
        String assignedUserName = assetAssignmentRepository.findByAssetIdAndActiveTrue(savedAsset.getId())
                .map(a -> a.getAssignedUser().getName())
                .orElse(null);

        TrackingTelemetryEvent wsEvent = TrackingTelemetryEvent.builder()
                .eventType("LOCATION_UPDATED")
                .assetId(savedAsset.getId())
                .assetCode(savedAsset.getAssetCode())
                .assetName(savedAsset.getAssetName())
                .latitude(savedAsset.getLatitude())
                .longitude(savedAsset.getLongitude())
                .accuracy(savedAsset.getGpsAccuracy())
                .speed(request.getSpeed())
                .heading(request.getHeading())
                .recordedAt(serverTime)
                .status(savedAsset.getStatus().name())
                .department(deptName)
                .assignedUser(assignedUserName)
                .source("BROWSER_GPS")
                .build();
        eventPublisher.publishEvent(new TelemetryPersistedEvent(this, wsEvent));

        return LocationTelemetryResponse.builder()
                .assetId(savedAsset.getId())
                .assetCode(savedAsset.getAssetCode())
                .latitude(savedAsset.getLatitude())
                .longitude(savedAsset.getLongitude())
                .accuracy(savedAsset.getGpsAccuracy())
                .speed(request.getSpeed())
                .heading(request.getHeading())
                .recordedAt(serverTime)
                .status(savedAsset.getStatus().name())
                .build();
    }

    /**
     * Start a new GPS tracking session.
     */
    @Transactional
    public GpsSessionResponse startSession(GpsSessionStartRequest request, User currentUser) {
        if (request.getAssetId() == null) {
            throw new BadRequestException("assetId is required");
        }

        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "id", request.getAssetId()));

        verifyUserCanTrackAsset(currentUser, asset);

        if (asset.getStatus() == AssetStatus.MAINTENANCE) {
            throw new ConflictException("Asset '" + asset.getAssetCode() + "' is currently under MAINTENANCE and cannot be tracked");
        }

        Optional<GpsSession> existingActive = gpsSessionRepository.findByAssetIdAndActiveTrue(asset.getId());
        if (existingActive.isPresent()) {
            throw new ConflictException("An active GPS tracking session already exists for asset: " + asset.getAssetCode());
        }

        GpsSession session = GpsSession.builder()
                .asset(asset)
                .startedBy(currentUser)
                .startedAt(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .active(true)
                .build();
        GpsSession savedSession = gpsSessionRepository.save(session);

        asset.setStatus(AssetStatus.MOVING);
        Asset savedAsset = assetRepository.save(asset);

        auditService.logAssetEvent(currentUser, "TRACKING_STARTED", asset.getId(),
                "GPS tracking session started for asset: " + asset.getAssetCode());

        // Broadcast session start over STOMP WebSocket
        String startDept = (savedAsset.getDepartment() != null) ? savedAsset.getDepartment().getName() : null;
        String startUser = assetAssignmentRepository.findByAssetIdAndActiveTrue(savedAsset.getId())
                .map(a -> a.getAssignedUser().getName())
                .orElse(null);

        TrackingTelemetryEvent startWsEvent = TrackingTelemetryEvent.builder()
                .eventType("SESSION_STARTED")
                .assetId(savedAsset.getId())
                .assetCode(savedAsset.getAssetCode())
                .assetName(savedAsset.getAssetName())
                .latitude(savedAsset.getLatitude())
                .longitude(savedAsset.getLongitude())
                .accuracy(savedAsset.getGpsAccuracy())
                .recordedAt(savedSession.getStartedAt())
                .status(savedAsset.getStatus().name())
                .department(startDept)
                .assignedUser(startUser)
                .source("BROWSER_GPS")
                .build();
        eventPublisher.publishEvent(new TelemetryPersistedEvent(this, startWsEvent));

        return mapToSessionResponse(savedSession);
    }

    /**
     * Stop a GPS tracking session by session ID.
     */
    @Transactional
    public GpsSessionResponse stopSession(Long sessionId, User currentUser) {
        GpsSession session = gpsSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("GpsSession", "id", sessionId));

        verifyUserCanStopSession(currentUser, session);

        if (session.isActive()) {
            session.setActive(false);
            Instant endedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

            // Invariant: session.startedAt <= location.recordedAt <= session.endedAt
            Instant minEndedAt = session.getStartedAt();
            Asset asset = session.getAsset();
            if (asset != null && asset.getLastSeen() != null && asset.getLastSeen().isAfter(minEndedAt)) {
                minEndedAt = asset.getLastSeen();
            }
            if (endedAt.isBefore(minEndedAt)) {
                endedAt = minEndedAt;
            }
            session.setEndedAt(endedAt);
            session = gpsSessionRepository.save(session);

            if (asset != null && asset.getStatus() != AssetStatus.MAINTENANCE) {
                boolean isAssigned = assetAssignmentRepository.existsByAssetIdAndActiveTrue(asset.getId());
                asset.setStatus(isAssigned ? AssetStatus.ASSIGNED : AssetStatus.AVAILABLE);
                Asset savedAsset = assetRepository.save(asset);

                auditService.logAssetEvent(currentUser, "TRACKING_STOPPED", asset.getId(),
                        "GPS tracking session stopped for asset: " + asset.getAssetCode());

                // Broadcast session stop over STOMP WebSocket
                String stopDept = (savedAsset.getDepartment() != null) ? savedAsset.getDepartment().getName() : null;
                String stopUser = assetAssignmentRepository.findByAssetIdAndActiveTrue(savedAsset.getId())
                        .map(a -> a.getAssignedUser().getName())
                        .orElse(null);

                TrackingTelemetryEvent stopWsEvent = TrackingTelemetryEvent.builder()
                        .eventType("SESSION_STOPPED")
                        .assetId(savedAsset.getId())
                        .assetCode(savedAsset.getAssetCode())
                        .assetName(savedAsset.getAssetName())
                        .latitude(savedAsset.getLatitude())
                        .longitude(savedAsset.getLongitude())
                        .accuracy(savedAsset.getGpsAccuracy())
                        .recordedAt(session.getEndedAt())
                        .status(savedAsset.getStatus().name())
                        .department(stopDept)
                        .assignedUser(stopUser)
                        .source("BROWSER_GPS")
                        .build();
                eventPublisher.publishEvent(new TelemetryPersistedEvent(this, stopWsEvent));
            }
        }

        return mapToSessionResponse(session);
    }

    /**
     * Stop an active GPS tracking session by asset ID.
     */
    @Transactional
    public GpsSessionResponse stopSessionByAssetId(Long assetId, User currentUser) {
        GpsSession session = gpsSessionRepository.findByAssetIdAndActiveTrue(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Active GPS session for asset", "assetId", assetId));

        return stopSession(session.getId(), currentUser);
    }

    /**
     * Retrieve recent location history with pagination limit.
     */
    @Transactional(readOnly = true)
    public List<LocationHistoryResponse> getHistory(Long assetId, Integer limit, User currentUser) {
        return getHistory(assetId, limit, "desc", currentUser);
    }

    /**
     * Retrieve location history with pagination limit and chronological direction (asc / desc).
     */
    @Transactional(readOnly = true)
    public List<LocationHistoryResponse> getHistory(Long assetId, Integer limit, String direction, User currentUser) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "id", assetId));

        int effectiveLimit = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        Pageable pageable = PageRequest.of(0, effectiveLimit);

        boolean asc = "asc".equalsIgnoreCase(direction);
        List<LocationHistory> list = asc
                ? locationHistoryRepository.findByAssetIdOrderByRecordedAtAsc(assetId, pageable)
                : locationHistoryRepository.findByAssetIdOrderByRecordedAtDesc(assetId, pageable);

        return list.stream()
                .map(h -> LocationHistoryResponse.builder()
                        .id(h.getId())
                        .assetId(asset.getId())
                        .assetCode(asset.getAssetCode())
                        .latitude(h.getLatitude())
                        .longitude(h.getLongitude())
                        .accuracy(h.getAccuracy())
                        .speed(h.getSpeed())
                        .heading(h.getHeading())
                        .source(h.getSource())
                        .recordedAt(h.getRecordedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Retrieve the latest known GPS location for an asset.
     */
    @Transactional(readOnly = true)
    public LatestLocationResponse getLatestLocation(Long assetId, User currentUser) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "id", assetId));

        Optional<LocationHistory> latestOpt = locationHistoryRepository.findFirstByAssetIdOrderByRecordedAtDesc(assetId);

        if (latestOpt.isPresent()) {
            LocationHistory h = latestOpt.get();
            return LatestLocationResponse.builder()
                    .assetId(asset.getId())
                    .assetCode(asset.getAssetCode())
                    .assetName(asset.getAssetName())
                    .latitude(h.getLatitude())
                    .longitude(h.getLongitude())
                    .accuracy(h.getAccuracy())
                    .speed(h.getSpeed())
                    .heading(h.getHeading())
                    .source(h.getSource())
                    .recordedAt(h.getRecordedAt())
                    .status(asset.getStatus().name())
                    .hasLocation(true)
                    .build();
        }

        return LatestLocationResponse.builder()
                .assetId(asset.getId())
                .assetCode(asset.getAssetCode())
                .assetName(asset.getAssetName())
                .latitude(null)
                .longitude(null)
                .accuracy(null)
                .speed(null)
                .heading(null)
                .source(null)
                .recordedAt(null)
                .status(asset.getStatus().name())
                .hasLocation(false)
                .build();
    }

    /**
     * Authorization check for tracking an asset:
     * - ADMIN: can track any asset
     * - SUPERVISOR: can track any asset
     * - STAFF: can track an asset if they have an active assignment, or started the current active session
     */
    public void verifyUserCanTrackAsset(User user, Asset asset) {
        if (user == null || user.getRole() == null) {
            throw new AccessDeniedException("Access denied: user identity not authenticated");
        }

        RoleType role = user.getRole().getName();
        if (role == RoleType.ADMIN || role == RoleType.SUPERVISOR) {
            return;
        }

        if (role == RoleType.STAFF) {
            boolean isAssigned = assetAssignmentRepository.existsByAssetIdAndAssignedUserIdAndActiveTrue(asset.getId(), user.getId());
            if (isAssigned) {
                return;
            }

            boolean isSessionOwner = gpsSessionRepository.findByAssetIdAndActiveTrue(asset.getId())
                    .map(s -> s.getStartedBy().getId().equals(user.getId()))
                    .orElse(false);
            if (isSessionOwner) {
                return;
            }

            throw new AccessDeniedException("Access denied: you are not authorized to track asset " + asset.getAssetCode());
        }

        throw new AccessDeniedException("Access denied: insufficient permissions to track asset");
    }

    private void verifyUserCanStopSession(User user, GpsSession session) {
        if (user == null || user.getRole() == null) {
            throw new AccessDeniedException("Access denied: user identity not authenticated");
        }

        RoleType role = user.getRole().getName();
        if (role == RoleType.ADMIN || role == RoleType.SUPERVISOR) {
            return;
        }

        if (session.getStartedBy() != null && session.getStartedBy().getId().equals(user.getId())) {
            return;
        }

        throw new AccessDeniedException("Access denied: you cannot stop a tracking session started by another user");
    }

    private Instant parseRecordedAt(String recordedAtStr) {
        try {
            // First try parsing ISO-8601 UTC Instant: e.g. 2026-09-28T12:30:00Z
            return Instant.parse(recordedAtStr);
        } catch (DateTimeParseException e1) {
            try {
                // Next try LocalDateTime format: e.g. 2026-09-28T12:30:00
                LocalDateTime ldt = LocalDateTime.parse(recordedAtStr);
                return ldt.toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                throw new BadRequestException("Invalid recordedAt timestamp format: " + recordedAtStr + ". Expected ISO-8601 string");
            }
        }
    }

    private GpsSessionResponse mapToSessionResponse(GpsSession s) {
        return GpsSessionResponse.builder()
                .id(s.getId())
                .assetId(s.getAsset() != null ? s.getAsset().getId() : null)
                .assetCode(s.getAsset() != null ? s.getAsset().getAssetCode() : null)
                .assetName(s.getAsset() != null ? s.getAsset().getAssetName() : null)
                .startedByUserId(s.getStartedBy() != null ? s.getStartedBy().getId() : null)
                .startedByUserName(s.getStartedBy() != null ? s.getStartedBy().getName() : null)
                .startedAt(s.getStartedAt())
                .endedAt(s.getEndedAt())
                .active(s.isActive())
                .build();
    }
}
