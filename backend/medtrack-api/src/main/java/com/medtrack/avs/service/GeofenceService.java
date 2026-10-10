package com.medtrack.avs.service;

import com.medtrack.avs.dto.*;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.event.AlertPersistedEvent;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeofenceService {

    private final GeofenceRepository geofenceRepository;
    private final DepartmentRepository departmentRepository;
    private final AlertRepository alertRepository;
    private final AssetGeofenceStateRepository assetGeofenceStateRepository;
    private final GeofenceCalculator geofenceCalculator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<GeofenceResponse> getAllGeofences() {
        return geofenceRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GeofenceResponse> getActiveGeofences() {
        return geofenceRepository.findByActiveTrue().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GeofenceResponse getGeofenceById(Long id) {
        Geofence geofence = geofenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Geofence not found with id: " + id));
        return mapToResponse(geofence);
    }

    @Transactional
    public GeofenceResponse createGeofence(GeofenceCreateRequest request, User currentUser) {
        validateCoordinatesAndRadius(request.getCenterLatitude(), request.getCenterLongitude(), request.getRadiusMeters());

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
        }

        Geofence geofence = Geofence.builder()
                .name(request.getName().trim())
                .department(department)
                .centerLatitude(request.getCenterLatitude())
                .centerLongitude(request.getCenterLongitude())
                .radiusMeters(request.getRadiusMeters())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Geofence saved = geofenceRepository.save(geofence);
        log.info("Created Geofence: {} (id={}) by user {}", saved.getName(), saved.getId(), currentUser.getEmail());
        return mapToResponse(saved);
    }

    @Transactional
    public GeofenceResponse updateGeofence(Long id, GeofenceUpdateRequest request, User currentUser) {
        Geofence geofence = geofenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Geofence not found with id: " + id));

        if (request.getCenterLatitude() != null || request.getCenterLongitude() != null || request.getRadiusMeters() != null) {
            double lat = request.getCenterLatitude() != null ? request.getCenterLatitude() : geofence.getCenterLatitude();
            double lon = request.getCenterLongitude() != null ? request.getCenterLongitude() : geofence.getCenterLongitude();
            double radius = request.getRadiusMeters() != null ? request.getRadiusMeters() : geofence.getRadiusMeters();
            validateCoordinatesAndRadius(lat, lon, radius);

            geofence.setCenterLatitude(lat);
            geofence.setCenterLongitude(lon);
            geofence.setRadiusMeters(radius);
        }

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            geofence.setName(request.getName().trim());
        }

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
            geofence.setDepartment(department);
        }

        if (request.getActive() != null) {
            geofence.setActive(request.getActive());
        }

        Geofence updated = geofenceRepository.save(geofence);
        log.info("Updated Geofence: {} (id={}) by user {}", updated.getName(), updated.getId(), currentUser.getEmail());
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteGeofence(Long id, User currentUser) {
        if (!currentUser.getRole().getName().equals(RoleType.ADMIN)) {
            throw new AccessDeniedException("Only administrators can delete geofences");
        }

        Geofence geofence = geofenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Geofence not found with id: " + id));

        assetGeofenceStateRepository.deleteByGeofenceId(id);
        geofenceRepository.delete(geofence);
        log.info("Deleted Geofence: {} (id={}) by user {}", geofence.getName(), id, currentUser.getEmail());
    }

    /**
     * Evaluates all active geofences against accepted persisted telemetry for the given asset.
     * Transitions are detected using persistent state (UNKNOWN, INSIDE, OUTSIDE).
     * Alerts are created and published strictly inside the transaction boundary.
     */
    @Transactional
    public void evaluateGeofences(Asset asset, double latitude, double longitude, Instant recordedAt) {
        List<Geofence> activeGeofences = geofenceRepository.findByActiveTrue();
        if (activeGeofences.isEmpty()) {
            return;
        }

        for (Geofence geofence : activeGeofences) {
            double distance = geofenceCalculator.calculateDistanceMeters(
                    latitude, longitude, geofence.getCenterLatitude(), geofence.getCenterLongitude());
            boolean isInside = geofenceCalculator.isInside(distance, geofence.getRadiusMeters());

            AssetGeofenceState stateRecord = assetGeofenceStateRepository
                    .findByAssetIdAndGeofenceId(asset.getId(), geofence.getId())
                    .orElseGet(() -> AssetGeofenceState.builder()
                            .asset(asset)
                            .geofence(geofence)
                            .state(GeofenceStatus.UNKNOWN)
                            .updatedAt(recordedAt)
                            .build());

            GeofenceStatus prevState = stateRecord.getState();

            if (isInside) {
                // INSIDE condition
                if (prevState == GeofenceStatus.UNKNOWN || prevState == GeofenceStatus.OUTSIDE) {
                    // Transition: ENTER
                    createAndPublishAlert(asset, geofence, AlertType.GEOFENCE_ENTER, AlertSeverity.INFO,
                            latitude, longitude, distance, recordedAt);

                    stateRecord.setState(GeofenceStatus.INSIDE);
                    stateRecord.setLastDistanceMeters(distance);
                    stateRecord.setLastTransitionAt(recordedAt);
                    stateRecord.setUpdatedAt(recordedAt);
                    assetGeofenceStateRepository.save(stateRecord);
                    log.info("Asset {} ENTERED geofence {} (distance: {}m, radius: {}m)",
                            asset.getAssetCode(), geofence.getName(), distance, geofence.getRadiusMeters());
                } else {
                    // INSIDE -> INSIDE: no transition alert
                    stateRecord.setLastDistanceMeters(distance);
                    stateRecord.setUpdatedAt(recordedAt);
                    assetGeofenceStateRepository.save(stateRecord);
                }
            } else {
                // OUTSIDE condition
                if (prevState == GeofenceStatus.INSIDE) {
                    // Transition: EXIT
                    createAndPublishAlert(asset, geofence, AlertType.GEOFENCE_EXIT, AlertSeverity.WARNING,
                            latitude, longitude, distance, recordedAt);

                    stateRecord.setState(GeofenceStatus.OUTSIDE);
                    stateRecord.setLastDistanceMeters(distance);
                    stateRecord.setLastTransitionAt(recordedAt);
                    stateRecord.setUpdatedAt(recordedAt);
                    assetGeofenceStateRepository.save(stateRecord);
                    log.warn("Asset {} EXITED geofence {} (distance: {}m, radius: {}m)",
                            asset.getAssetCode(), geofence.getName(), distance, geofence.getRadiusMeters());
                } else if (prevState == GeofenceStatus.UNKNOWN) {
                    // UNKNOWN -> OUTSIDE: establish baseline state without false EXIT alert
                    stateRecord.setState(GeofenceStatus.OUTSIDE);
                    stateRecord.setLastDistanceMeters(distance);
                    stateRecord.setUpdatedAt(recordedAt);
                    assetGeofenceStateRepository.save(stateRecord);
                } else {
                    // OUTSIDE -> OUTSIDE: no transition alert
                    stateRecord.setLastDistanceMeters(distance);
                    stateRecord.setUpdatedAt(recordedAt);
                    assetGeofenceStateRepository.save(stateRecord);
                }
            }
        }
    }

    private void createAndPublishAlert(Asset asset, Geofence geofence, AlertType alertType, AlertSeverity severity,
                                       double latitude, double longitude, double distance, Instant occurredAt) {
        String action = (alertType == AlertType.GEOFENCE_ENTER) ? "entered" : "exited";
        String message = String.format("Asset %s %s %s (distance: %.1fm, radius: %.1fm)",
                asset.getAssetCode(), action, geofence.getName(), distance, geofence.getRadiusMeters());

        Alert alert = Alert.builder()
                .asset(asset)
                .geofence(geofence)
                .type(alertType)
                .severity(severity)
                .message(message)
                .latitude(latitude)
                .longitude(longitude)
                .distanceMeters(distance)
                .resolved(false)
                .occurredAt(occurredAt)
                .createdAt(occurredAt)
                .build();

        Alert savedAlert = alertRepository.save(alert);

        String deptName = (geofence.getDepartment() != null) ? geofence.getDepartment().getName() :
                ((asset.getDepartment() != null) ? asset.getDepartment().getName() : "Hospital");

        AlertWebSocketEvent wsEvent = AlertWebSocketEvent.builder()
                .eventType("GEOFENCE_ALERT")
                .alertId(savedAlert.getId())
                .assetId(asset.getId())
                .assetCode(asset.getAssetCode())
                .geofenceId(geofence.getId())
                .geofenceName(geofence.getName())
                .department(deptName)
                .alertType(alertType.name())
                .severity(severity.name())
                .message(message)
                .latitude(latitude)
                .longitude(longitude)
                .distanceMeters(distance)
                .occurredAt(occurredAt)
                .build();

        eventPublisher.publishEvent(new AlertPersistedEvent(this, wsEvent, savedAlert));
    }

    private void validateCoordinatesAndRadius(Double lat, Double lon, Double radius) {
        if (lat == null || lat < -90.0 || lat > 90.0) {
            throw new BadRequestException("Latitude must be between -90.0 and +90.0 degrees");
        }
        if (lon == null || lon < -180.0 || lon > 180.0) {
            throw new BadRequestException("Longitude must be between -180.0 and +180.0 degrees");
        }
        if (radius == null || radius <= 0.0) {
            throw new BadRequestException("Radius must be greater than 0 meters");
        }
    }

    public GeofenceResponse mapToResponse(Geofence g) {
        return GeofenceResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .departmentId(g.getDepartment() != null ? g.getDepartment().getId() : null)
                .departmentName(g.getDepartment() != null ? g.getDepartment().getName() : null)
                .departmentCode(g.getDepartment() != null ? g.getDepartment().getCode() : null)
                .centerLatitude(g.getCenterLatitude())
                .centerLongitude(g.getCenterLongitude())
                .radiusMeters(g.getRadiusMeters())
                .active(g.isActive())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt())
                .build();
    }
}
