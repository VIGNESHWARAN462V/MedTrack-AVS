package com.medtrack.avs.service.ai;

import com.medtrack.avs.dto.AiSourceReference;
import com.medtrack.avs.dto.AlertResponse;
import com.medtrack.avs.dto.LatestLocationResponse;
import com.medtrack.avs.dto.TransferResponse;
import com.medtrack.avs.dto.analytics.AmcMetricResponse;
import com.medtrack.avs.dto.analytics.DepartmentGubDto;
import com.medtrack.avs.dto.analytics.GubMetricResponse;
import com.medtrack.avs.dto.analytics.HfiMetricResponse;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.repository.*;
import com.medtrack.avs.service.*;
import com.medtrack.avs.service.ai.model.AiIntentClassification;
import com.medtrack.avs.service.ai.model.AiToolExecutionResult;
import com.medtrack.avs.service.ai.model.AiToolName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Executes allowlisted, strictly read-only operational tools for MedTrack - AVS (Phase 20.2).
 * Enforces role-based and department-scoped server-side authorization before every data retrieval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiOperationsToolService {

    private final AssetRepository assetRepository;
    private final DepartmentRepository departmentRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final GpsSessionRepository gpsSessionRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final TrackingService trackingService;
    private final AlertService alertService;
    private final TransferService transferService;
    private final AnalyticsService analyticsService;

    private static final int STALE_THRESHOLD_MINUTES = 15;
    private static final int MAX_TOOL_INVOCATIONS = 3;

    /**
     * Dispatches allowlisted read-only tools based on validated classification (strictly <= 3 calls).
     */
    @Transactional(readOnly = true)
    public AiToolExecutionResult execute(AiIntentClassification classification, User currentUser) {
        List<AiToolName> targetTools = classification.getTargetTools();
        if (targetTools == null || targetTools.isEmpty()) {
            return AiToolExecutionResult.builder()
                    .hasData(false)
                    .groundedSummary("No operational tool was matched for this request.")
                    .build();
        }

        // Limit tool invocations to maximum 3
        List<AiToolName> toolsToRun = targetTools.stream().limit(MAX_TOOL_INVOCATIONS).collect(Collectors.toList());

        List<AiSourceReference> combinedSources = new ArrayList<>();
        Map<String, Object> combinedData = new LinkedHashMap<>();
        StringBuilder summaryBuilder = new StringBuilder();
        Instant latestTimestamp = null;

        for (AiToolName tool : toolsToRun) {
            switch (tool) {
                case LOOKUP_ASSET -> {
                    try {
                        Map<String, Object> result = lookupAsset(classification.getAssetCode(), currentUser, combinedSources);
                        combinedData.put("asset_details", result.get("data"));
                        summaryBuilder.append(result.get("summary")).append("\n");
                    } catch (ResourceNotFoundException ex) {
                        combinedSources.add(AiSourceReference.builder()
                                .label("Asset inventory")
                                .resourceType("ASSET")
                                .identifier(classification.getAssetCode() != null ? classification.getAssetCode().toUpperCase() : "UNKNOWN")
                                .freshness("NO_DATA")
                                .build());
                        summaryBuilder.append(String.format("Asset %s was not found in the MedTrack asset inventory.\n", classification.getAssetCode()));
                    }
                }
                case GET_LATEST_LOCATION -> {
                    try {
                        Map<String, Object> result = getLatestLocation(classification.getAssetCode(), currentUser, combinedSources);
                        combinedData.put("location_telemetry", result.get("data"));
                        summaryBuilder.append(result.get("summary")).append("\n");
                    } catch (ResourceNotFoundException ex) {
                        combinedSources.add(AiSourceReference.builder()
                                .label("Latest GPS telemetry")
                                .resourceType("GPS_LOCATION")
                                .identifier(classification.getAssetCode() != null ? classification.getAssetCode().toUpperCase() : "UNKNOWN")
                                .freshness("NO_DATA")
                                .build());
                        summaryBuilder.append(String.format("No telemetry or tracking record found for asset %s.\n", classification.getAssetCode()));
                    }
                }
                case GET_TRACKING_STATUS -> {
                    try {
                        Map<String, Object> result = getTrackingStatus(classification.getAssetCode(), currentUser, combinedSources);
                        combinedData.put("tracking_status", result.get("data"));
                        summaryBuilder.append(result.get("summary")).append("\n");
                    } catch (ResourceNotFoundException ex) {
                        combinedSources.add(AiSourceReference.builder()
                                .label("Active tracking session")
                                .resourceType("GPS_SESSION")
                                .identifier(classification.getAssetCode() != null ? classification.getAssetCode().toUpperCase() : "UNKNOWN")
                                .freshness("NO_DATA")
                                .build());
                        summaryBuilder.append(String.format("No active tracking session found for asset %s.\n", classification.getAssetCode()));
                    }
                }
                case GET_RECENT_ALERTS -> {
                    Map<String, Object> result = getRecentAlerts(classification.getResolved(), classification.getLimit(), currentUser, combinedSources);
                    combinedData.put("geofence_alerts", result.get("data"));
                    summaryBuilder.append(result.get("summary")).append("\n");
                }
                case GET_ACTIVE_TRANSFERS -> {
                    Map<String, Object> result = getActiveTransfers(classification.getDepartmentNameOrCode(), classification.getAssetCode(), classification.getLimit(), currentUser, combinedSources);
                    combinedData.put("active_transfers", result.get("data"));
                    summaryBuilder.append(result.get("summary")).append("\n");
                }
                case GET_ANALYTICS_METRIC -> {
                    Map<String, Object> result = getAnalyticsMetric(classification.getMetricName(), classification.getFrom(), classification.getTo(), classification.getDepartmentNameOrCode(), classification.getAssetCode(), currentUser, combinedSources);
                    combinedData.put("analytics_metric", result.get("data"));
                    summaryBuilder.append(result.get("summary")).append("\n");
                }
                case GET_DEPARTMENT_BALANCE -> {
                    Map<String, Object> result = getDepartmentBalance(classification.getDepartmentNameOrCode(), currentUser, combinedSources);
                    combinedData.put("department_balance", result.get("data"));
                    summaryBuilder.append(result.get("summary")).append("\n");
                }
            }
        }

        for (AiSourceReference ref : combinedSources) {
            if (ref.getTimestamp() != null && (latestTimestamp == null || ref.getTimestamp().isAfter(latestTimestamp))) {
                latestTimestamp = ref.getTimestamp();
            }
        }

        return AiToolExecutionResult.builder()
                .executedTools(toolsToRun)
                .data(combinedData)
                .groundedSummary(summaryBuilder.toString().trim())
                .sources(combinedSources)
                .dataAsOf(latestTimestamp != null ? latestTimestamp : Instant.now().truncatedTo(ChronoUnit.MICROS))
                .hasData(!combinedData.isEmpty())
                .build();
    }

    /**
     * Tool 1: lookup_asset
     */
    @Transactional(readOnly = true)
    public Map<String, Object> lookupAsset(String assetCode, User currentUser, List<AiSourceReference> sources) {
        if (assetCode == null || assetCode.trim().isEmpty()) {
            return Map.of("data", Map.of("error", "Asset code is required"), "summary", "No asset code provided.");
        }

        String cleanCode = assetCode.trim().toUpperCase();
        Asset asset = assetRepository.findByAssetCode(cleanCode)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "assetCode", cleanCode));

        // Enforce authorization for STAFF
        verifyUserCanViewAsset(currentUser, asset);

        Optional<AssetAssignment> assignmentOpt = assetAssignmentRepository.findByAssetIdAndActiveTrue(asset.getId());
        String assignedUserName = assignmentOpt.map(a -> a.getAssignedUser().getName()).orElse("Unassigned");

        Instant recordTimestamp = asset.getLastSeen() != null
                ? asset.getLastSeen()
                : (asset.getCreatedAt() != null ? asset.getCreatedAt() : Instant.now().truncatedTo(ChronoUnit.MICROS));

        sources.add(AiSourceReference.builder()
                .label("Asset inventory")
                .resourceType("ASSET")
                .identifier(asset.getAssetCode())
                .timestamp(recordTimestamp)
                .freshness("LIVE")
                .build());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assetCode", asset.getAssetCode());
        data.put("assetName", asset.getAssetName());
        data.put("category", asset.getCategory());
        data.put("status", asset.getStatus().name());
        data.put("department", asset.getDepartment() != null ? asset.getDepartment().getName() : "None");
        data.put("assignedUser", assignedUserName);
        data.put("batteryLevel", asset.getBatteryLevel() != null ? asset.getBatteryLevel() + "%" : "Unknown");
        data.put("lastSeen", asset.getLastSeen());

        String summary = String.format("Asset %s (%s) is categorized under %s in %s. Status is %s. Currently assigned to: %s.",
                asset.getAssetCode(), asset.getAssetName(), asset.getCategory(),
                asset.getDepartment() != null ? asset.getDepartment().getName() : "None",
                asset.getStatus().name(), assignedUserName);

        return Map.of("data", data, "summary", summary);
    }

    /**
     * Tool 2: get_latest_location
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLatestLocation(String assetCode, User currentUser, List<AiSourceReference> sources) {
        if (assetCode == null || assetCode.trim().isEmpty()) {
            return Map.of("data", Map.of("error", "Asset code is required"), "summary", "No asset code provided.");
        }

        String cleanCode = assetCode.trim().toUpperCase();
        Asset asset = assetRepository.findByAssetCode(cleanCode)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "assetCode", cleanCode));

        // Enforce authorization
        verifyUserCanViewAsset(currentUser, asset);

        LatestLocationResponse location = trackingService.getLatestLocation(asset.getId(), currentUser);

        boolean isStale = false;
        String freshness = "NO_DATA";
        String summary;

        if (location.isHasLocation() && location.getRecordedAt() != null) {
            Instant recordedAt = location.getRecordedAt();
            Duration age = Duration.between(recordedAt, Instant.now());
            if (age.toMinutes() >= STALE_THRESHOLD_MINUTES) {
                isStale = true;
                freshness = "STALE (" + formatDuration(age) + " ago)";
                summary = String.format("Asset %s was last reported at coordinates [%.6f, %.6f] (accuracy: %.1fm) on %s (%s). Note: This GPS update is STALE.",
                        cleanCode, location.getLatitude(), location.getLongitude(), location.getAccuracy(),
                        recordedAt, formatDuration(age) + " ago");
            } else {
                freshness = "LIVE";
                summary = String.format("Asset %s is currently reported at coordinates [%.6f, %.6f] with accuracy %.1fm recorded at %s.",
                        cleanCode, location.getLatitude(), location.getLongitude(), location.getAccuracy(), recordedAt);
            }

            sources.add(AiSourceReference.builder()
                    .label("Latest GPS telemetry")
                    .resourceType("GPS_LOCATION")
                    .identifier(cleanCode)
                    .timestamp(recordedAt)
                    .freshness(freshness)
                    .build());
        } else {
            summary = String.format("Asset %s has NO GPS location reported yet in the system.", cleanCode);
            sources.add(AiSourceReference.builder()
                    .label("Latest GPS telemetry")
                    .resourceType("GPS_LOCATION")
                    .identifier(cleanCode)
                    .timestamp(null)
                    .freshness("NO_DATA")
                    .build());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assetCode", cleanCode);
        data.put("hasLocation", location.isHasLocation());
        data.put("latitude", location.getLatitude());
        data.put("longitude", location.getLongitude());
        data.put("accuracy", location.getAccuracy());
        data.put("recordedAt", location.getRecordedAt());
        data.put("isStale", isStale);
        data.put("freshness", freshness);

        return Map.of("data", data, "summary", summary);
    }

    /**
     * Tool 3: get_tracking_status
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTrackingStatus(String assetCode, User currentUser, List<AiSourceReference> sources) {
        if (assetCode != null && !assetCode.trim().isEmpty()) {
            String cleanCode = assetCode.trim().toUpperCase();
            Asset asset = assetRepository.findByAssetCode(cleanCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Asset", "assetCode", cleanCode));

            verifyUserCanViewAsset(currentUser, asset);

            Optional<GpsSession> sessionOpt = gpsSessionRepository.findByAssetIdAndActiveTrue(asset.getId());
            boolean activeSession = sessionOpt.isPresent();
            Instant startedAt = sessionOpt.map(GpsSession::getStartedAt).orElse(null);
            String startedByName = sessionOpt.map(s -> s.getStartedBy() != null ? s.getStartedBy().getName() : "Unknown").orElse(null);

            boolean isStale = false;
            String freshness = "INACTIVE";

            if (activeSession) {
                Instant lastTelemetry = asset.getLastSeen();
                if (lastTelemetry == null || Duration.between(lastTelemetry, Instant.now()).toMinutes() >= STALE_THRESHOLD_MINUTES) {
                    isStale = true;
                    freshness = "STALE_SESSION";
                } else {
                    freshness = "ACTIVE_TRACKING";
                }
            } else if (asset.getLastSeen() != null && Duration.between(asset.getLastSeen(), Instant.now()).toMinutes() >= STALE_THRESHOLD_MINUTES) {
                isStale = true;
                freshness = "STALE";
            }

            sources.add(AiSourceReference.builder()
                    .label("GPS tracking status")
                    .resourceType("GPS_SESSION")
                    .identifier(cleanCode)
                    .timestamp(startedAt != null ? startedAt : asset.getLastSeen())
                    .freshness(freshness)
                    .build());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("assetCode", cleanCode);
            data.put("hasActiveSession", activeSession);
            data.put("startedAt", startedAt);
            data.put("startedBy", startedByName);
            data.put("lastSeen", asset.getLastSeen());
            data.put("freshness", freshness);
            data.put("isStale", isStale);

            String summary = String.format("Tracking for %s: Active session=%b. Freshness=%s. Last seen=%s.",
                    cleanCode, activeSession, freshness, asset.getLastSeen() != null ? asset.getLastSeen() : "Never");

            return Map.of("data", data, "summary", summary);
        } else {
            // Unbounded or hospital-wide inquiry: Which authorized assets have stale GPS updates?
            List<Asset> accessibleAssets;
            if (currentUser.getRole().getName() == RoleType.STAFF) {
                List<Long> assignedIds = assetAssignmentRepository.findByAssignedUserId(currentUser.getId()).stream()
                        .map(a -> a.getAsset().getId())
                        .collect(Collectors.toList());
                accessibleAssets = new ArrayList<>(assetRepository.findAllById(assignedIds));
            } else {
                accessibleAssets = new ArrayList<>(assetRepository.findAll());
            }

            // Sort by staleness: null lastSeen first, then earliest lastSeen
            accessibleAssets.sort((a1, a2) -> {
                if (a1.getLastSeen() == null && a2.getLastSeen() == null) return 0;
                if (a1.getLastSeen() == null) return -1;
                if (a2.getLastSeen() == null) return 1;
                return a1.getLastSeen().compareTo(a2.getLastSeen());
            });

            Instant now = Instant.now();
            List<Map<String, Object>> staleList = new ArrayList<>();

            for (Asset a : accessibleAssets) {
                boolean stale = false;
                String reason;
                if (a.getLastSeen() == null) {
                    stale = true;
                    reason = "No telemetry ever recorded";
                } else {
                    Duration diff = Duration.between(a.getLastSeen(), now);
                    if (diff.toMinutes() >= STALE_THRESHOLD_MINUTES) {
                        stale = true;
                        reason = "Last update " + formatDuration(diff) + " ago";
                    } else {
                        reason = "Active";
                    }
                }

                if (stale) {
                    staleList.add(Map.of(
                            "assetCode", a.getAssetCode(),
                            "assetName", a.getAssetName(),
                            "department", a.getDepartment() != null ? a.getDepartment().getName() : "None",
                            "status", a.getStatus().name(),
                            "lastSeen", a.getLastSeen() != null ? a.getLastSeen().toString() : "None",
                            "stalenessReason", reason
                    ));
                }
            }

            // Cap list to 15
            List<Map<String, Object>> boundedStaleList = staleList.stream().limit(15).collect(Collectors.toList());

            String freshness = staleList.isEmpty() ? "LIVE" : "STALE";
            sources.add(AiSourceReference.builder()
                    .label("GPS tracking staleness review")
                    .resourceType("GPS_SESSION")
                    .identifier("Stale Assets Review")
                    .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
                    .freshness(freshness)
                    .build());

            String stalestInfo = boundedStaleList.isEmpty()
                    ? "No stale assets detected in permitted scope."
                    : String.format("Stalest asset in permitted scope: %s (%s).",
                            boundedStaleList.get(0).get("assetCode"),
                            boundedStaleList.get(0).get("stalenessReason"));

            String summary = String.format("Found %d authorized asset(s) with stale or missing GPS updates (evaluated against %d-minute threshold). %s",
                    staleList.size(), STALE_THRESHOLD_MINUTES, stalestInfo);

            return Map.of("data", Map.of("totalStaleCount", staleList.size(), "staleAssets", boundedStaleList), "summary", summary);
        }
    }

    /**
     * Tool 4: get_recent_alerts
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRecentAlerts(Boolean resolved, Integer limit, User currentUser, List<AiSourceReference> sources) {
        int boundedLimit = (limit != null && limit > 0) ? Math.min(limit, 20) : 10;
        List<AlertResponse> alerts = alertService.getAlerts(resolved, null, null, currentUser);

        List<AlertResponse> limitedAlerts = alerts.stream().limit(boundedLimit).collect(Collectors.toList());

        for (AlertResponse a : limitedAlerts.stream().limit(5).collect(Collectors.toList())) {
            sources.add(AiSourceReference.builder()
                    .label("Geofence alerts")
                    .resourceType("ALERT")
                    .identifier("Alert #" + a.getId() + " (" + a.getAssetCode() + ")")
                    .timestamp(a.getOccurredAt())
                    .freshness(a.isResolved() ? "RESOLVED" : "UNRESOLVED")
                    .build());
        }

        long unresolvedCount = alerts.stream().filter(a -> !a.isResolved()).count();
        String summary = String.format("Found %d %s geofence alert(s) within your permitted scope (showing latest %d). Unresolved: %d.",
                alerts.size(), Boolean.FALSE.equals(resolved) ? "unresolved" : "total", limitedAlerts.size(), unresolvedCount);

        return Map.of("data", Map.of("totalAlerts", alerts.size(), "unresolvedCount", unresolvedCount, "alerts", limitedAlerts), "summary", summary);
    }

    /**
     * Tool 5: get_active_transfers
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getActiveTransfers(String departmentNameOrCode, String assetCode, Integer limit, User currentUser, List<AiSourceReference> sources) {
        int boundedLimit = (limit != null && limit > 0) ? Math.min(limit, 20) : 10;
        Long deptId = resolveDepartmentId(departmentNameOrCode);
        Long assetId = null;
        if (assetCode != null) {
            assetId = assetRepository.findByAssetCode(assetCode.trim().toUpperCase()).map(Asset::getId).orElse(null);
        }

        List<TransferResponse> transfers = transferService.getTransfers(null, assetId, deptId, null, currentUser);

        List<TransferResponse> activeTransfers = transfers.stream()
                .filter(t -> t.getStatus() == TransferStatus.REQUESTED ||
                             t.getStatus() == TransferStatus.ACCEPTED ||
                             t.getStatus() == TransferStatus.IN_TRANSIT)
                .limit(boundedLimit)
                .collect(Collectors.toList());

        for (TransferResponse t : activeTransfers.stream().limit(5).collect(Collectors.toList())) {
            sources.add(AiSourceReference.builder()
                    .label("Active transfer records")
                    .resourceType("TRANSFER")
                    .identifier("Transfer #" + t.getId() + " (" + t.getAssetCode() + ")")
                    .timestamp(t.getRequestedAt())
                    .freshness(t.getStatus().name())
                    .build());
        }

        String summary = String.format("Found %d active transfer(s) in progress within your permitted scope (showing up to %d).",
                activeTransfers.size(), boundedLimit);

        return Map.of("data", Map.of("activeTransferCount", activeTransfers.size(), "transfers", activeTransfers), "summary", summary);
    }

    /**
     * Tool 6: get_analytics_metric
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAnalyticsMetric(String metricName, Instant from, Instant to, String departmentNameOrCode, String assetCode, User currentUser, List<AiSourceReference> sources) {
        Long deptId = resolveDepartmentId(departmentNameOrCode);
        Long assetId = (assetCode != null)
                ? assetRepository.findByAssetCode(assetCode.trim().toUpperCase()).map(Asset::getId).orElse(null)
                : null;

        String upperMetric = (metricName != null) ? metricName.trim().toUpperCase() : "AMC";

        return switch (upperMetric) {
            case "AMC" -> {
                AmcMetricResponse amc = analyticsService.calculateAmc(from, to, deptId, assetId, currentUser);
                sources.add(AiSourceReference.builder()
                        .label("AMC analytics")
                        .resourceType("ANALYTICS_AMC")
                        .identifier("AMC Scope")
                        .timestamp(amc.getPeriodEnd())
                        .freshness(amc.getStatus())
                        .build());

                String summary = String.format("Asset Movement Continuity (AMC): %s. Overall continuity: %s. Expected updates: %d, successful: %d. Period: %s to %s.",
                        amc.getStatus(),
                        amc.getContinuityPercentage() != null ? amc.getContinuityPercentage() + "%" : "NO_DATA",
                        amc.getExpectedGpsUpdates(), amc.getSuccessfulGpsUpdates(),
                        amc.getPeriodStart(), amc.getPeriodEnd());

                yield Map.of("data", amc, "summary", summary);
            }
            case "HFI" -> {
                HfiMetricResponse hfi = analyticsService.calculateHfi(from, to, deptId, assetId, currentUser);
                sources.add(AiSourceReference.builder()
                        .label("HFI analytics")
                        .resourceType("ANALYTICS_HFI")
                        .identifier("HFI Scope")
                        .timestamp(hfi.getPeriodEnd())
                        .freshness(hfi.getStatus())
                        .build());

                String summary = String.format("Handoff Friction Index (HFI): %s. Score: %s (0-100). Completed transfers: %d. Components: duration=%.1f, delay=%.1f, gap=%.1f. Interpretation: %s.",
                        hfi.getStatus(),
                        hfi.getOverallHfi() != null ? String.valueOf(hfi.getOverallHfi()) : "NO_DATA",
                        hfi.getCompletedTransferCount(),
                        hfi.getDurationComponentScore(), hfi.getDelayComponentScore(), hfi.getGapComponentScore(),
                        hfi.getInterpretation());

                yield Map.of("data", hfi, "summary", summary);
            }
            case "GUB" -> {
                GubMetricResponse gub = analyticsService.calculateGub(from, to, deptId, currentUser);
                sources.add(AiSourceReference.builder()
                        .label("GUB analytics")
                        .resourceType("ANALYTICS_GUB")
                        .identifier("Hospital GUB")
                        .timestamp(gub.getPeriodEnd())
                        .freshness("MEASURED")
                        .build());

                String summary = String.format("Geo-Utilization Balance (GUB): Hospital equilibrium score is %.2f. Average balance: %.2f. Interpretation: %s.",
                        gub.getHospitalEquilibriumScore() != null ? gub.getHospitalEquilibriumScore() : 0.0,
                        gub.getAverageBalanceScore() != null ? gub.getAverageBalanceScore() : 0.0,
                        gub.getInterpretation());

                yield Map.of("data", gub, "summary", summary);
            }
            default -> throw new IllegalArgumentException("Unsupported metric name: " + metricName);
        };
    }

    /**
     * Tool 7: get_department_balance
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDepartmentBalance(String departmentNameOrCode, User currentUser, List<AiSourceReference> sources) {
        Long targetDeptId = resolveDepartmentId(departmentNameOrCode);
        GubMetricResponse gub = analyticsService.calculateGub(null, null, targetDeptId, currentUser);

        sources.add(AiSourceReference.builder()
                .label("GUB department balance")
                .resourceType("ANALYTICS_GUB")
                .identifier("GUB Balance")
                .timestamp(gub.getPeriodEnd())
                .freshness("MEASURED")
                .build());

        List<DepartmentGubDto> depts = gub.getDepartments();

        DepartmentGubDto greatestDeficit = depts.stream()
                .min(Comparator.comparingDouble(DepartmentGubDto::getBalanceScore))
                .orElse(null);

        String summary;
        if (greatestDeficit != null && greatestDeficit.getBalanceScore() < 0) {
            summary = String.format("Department with the greatest equipment deficit: %s (Code: %s) with balance score %.2f (%s). Usable assets: %d, Demand: %d.",
                    greatestDeficit.getDepartmentName(), greatestDeficit.getDepartmentCode(),
                    greatestDeficit.getBalanceScore(), greatestDeficit.getStatus(),
                    greatestDeficit.getUsableAssetCount(), greatestDeficit.getDemandCount());
        } else {
            summary = String.format("All evaluated departments are balanced or have surplus. Hospital equilibrium is %.2f.",
                    gub.getHospitalEquilibriumScore() != null ? gub.getHospitalEquilibriumScore() : 0.0);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hospitalEquilibrium", gub.getHospitalEquilibriumScore());
        data.put("greatestDeficitDepartment", greatestDeficit);
        data.put("departmentBreakdowns", depts);

        return Map.of("data", data, "summary", summary);
    }

    private void verifyUserCanViewAsset(User user, Asset asset) {
        if (user == null || user.getRole() == null) {
            throw new AccessDeniedException("Access denied: user not authenticated");
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

            throw new AccessDeniedException("Access denied: You do not have permission to view asset " + asset.getAssetCode() + " under your current STAFF role");
        }

        throw new AccessDeniedException("Access denied: insufficient permissions");
    }

    private Long resolveDepartmentId(String departmentNameOrCode) {
        if (departmentNameOrCode == null || departmentNameOrCode.trim().isEmpty()) {
            return null;
        }
        String clean = departmentNameOrCode.trim();
        return departmentRepository.findByCode(clean.toUpperCase())
                .map(Department::getId)
                .orElseGet(() -> departmentRepository.findAll().stream()
                        .filter(d -> d.getName().equalsIgnoreCase(clean))
                        .map(Department::getId)
                        .findFirst()
                        .orElse(null));
    }

    private String formatDuration(Duration d) {
        long minutes = d.toMinutes();
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = d.toHours();
        if (hours < 24) {
            return hours + "h " + (minutes % 60) + "m";
        }
        return d.toDays() + "d " + (hours % 24) + "h";
    }
}
