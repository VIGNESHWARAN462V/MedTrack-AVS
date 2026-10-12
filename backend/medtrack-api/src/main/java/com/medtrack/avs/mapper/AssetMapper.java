package com.medtrack.avs.mapper;

import com.medtrack.avs.dto.AssetCreateRequest;
import com.medtrack.avs.dto.AssetResponse;
import com.medtrack.avs.entity.Asset;
import com.medtrack.avs.entity.AssetStatus;
import com.medtrack.avs.entity.IndoorLocationSource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class AssetMapper {

    /**
     * Freshness window for QR code check-in positions (12-hour clinical shift).
     * After this window expires without a new check-in or position update,
     * the asset's location status transitions to LAST_KNOWN.
     */
    public static final Duration QR_CHECKIN_FRESHNESS_WINDOW = Duration.ofHours(12);

    /**
     * Freshness window for outdoor GPS telemetry fixes (2 hours).
     */
    public static final Duration GPS_FRESHNESS_WINDOW = Duration.ofHours(2);

    /**
     * Freshness window for manual indoor position configurations (30 days).
     */
    public static final Duration MANUAL_CONFIG_FRESHNESS_WINDOW = Duration.ofDays(30);

    /**
     * Freshness window for continuous live indoor sensor telemetry (15 minutes),
     * only evaluated when a genuine hardware integration is active.
     */
    public static final Duration LIVE_INDOOR_FRESHNESS_WINDOW = Duration.ofMinutes(15);

    public AssetResponse toResponse(Asset entity) {
        if (entity == null) {
            return null;
        }

        String locationStatus = resolveLocationStatus(entity);

        return AssetResponse.builder()
                .id(entity.getId())
                .assetCode(entity.getAssetCode())
                .assetName(entity.getAssetName())
                .category(entity.getCategory())
                .serialNumber(entity.getSerialNumber())
                .status(entity.getStatus())
                .departmentId(entity.getDepartment() != null ? entity.getDepartment().getId() : null)
                .departmentName(entity.getDepartment() != null ? entity.getDepartment().getName() : null)
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .gpsAccuracy(entity.getGpsAccuracy())
                .batteryLevel(entity.getBatteryLevel())
                .qrCode(entity.getQrCode())
                .lastSeen(entity.getLastSeen())
                .hospitalId(entity.getHospital() != null ? entity.getHospital().getId() : null)
                .hospitalName(entity.getHospital() != null ? entity.getHospital().getName() : null)
                .buildingId(entity.getBuilding() != null ? entity.getBuilding().getId() : null)
                .buildingName(entity.getBuilding() != null ? entity.getBuilding().getName() : null)
                .floorId(entity.getFloor() != null ? entity.getFloor().getId() : null)
                .floorDisplayName(entity.getFloor() != null ? entity.getFloor().getDisplayName() : null)
                .roomId(entity.getRoom() != null ? entity.getRoom().getId() : null)
                .roomName(entity.getRoom() != null ? entity.getRoom().getName() : null)
                .roomNumber(entity.getRoom() != null ? entity.getRoom().getRoomNumber() : null)
                .indoorX(entity.getIndoorX())
                .indoorY(entity.getIndoorY())
                .indoorLocationSource(entity.getIndoorLocationSource())
                .indoorUpdatedAt(entity.getIndoorUpdatedAt())
                .locationStatus(locationStatus)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Checks if a genuine hardware integration is actively connected and providing telemetry.
     * Currently returns false as physical BLE gateway/Wi-Fi daemons are not physically connected.
     */
    public static boolean isGenuineHardwareIntegrationActive(IndoorLocationSource source) {
        if (source == null) {
            return false;
        }
        return false;
    }

    public static String resolveLocationStatus(Asset entity) {
        return resolveLocationStatus(entity, Instant.now());
    }

    public static String resolveLocationStatus(Asset entity, Instant referenceTime) {
        if (entity == null) {
            return "LOCATION_UNAVAILABLE";
        }

        Instant now = referenceTime != null ? referenceTime : Instant.now();

        boolean hasIndoorCoords = entity.getFloor() != null &&
                                  entity.getIndoorX() != null &&
                                  entity.getIndoorY() != null;
        boolean hasGpsCoords = entity.getLatitude() != null &&
                               entity.getLongitude() != null;

        // Check if outdoor GPS is fresh
        boolean isGpsFresh = false;
        if (hasGpsCoords && entity.getLastSeen() != null) {
            Duration gpsAge = Duration.between(entity.getLastSeen(), now);
            isGpsFresh = !gpsAge.isNegative() && gpsAge.compareTo(GPS_FRESHNESS_WINDOW) <= 0;
        }

        // 1. Evaluate Indoor Facility Position
        if (hasIndoorCoords) {
            IndoorLocationSource source = entity.getIndoorLocationSource();
            Instant indoorTime = entity.getIndoorUpdatedAt();
            Duration indoorAge = indoorTime != null ? Duration.between(indoorTime, now) : null;

            // Distinguish recent QR check-in from continuous live tracking
            if (source == IndoorLocationSource.QR_CHECKIN) {
                if (indoorAge != null && !indoorAge.isNegative() && indoorAge.compareTo(QR_CHECKIN_FRESHNESS_WINDOW) <= 0) {
                    return "RECENT_CHECK_IN";
                }
            }

            // Continuous live indoor tracking (only if genuine hardware integration is active)
            if (source == IndoorLocationSource.BLE_BEACON || source == IndoorLocationSource.WIFI_POSITIONING) {
                if (isGenuineHardwareIntegrationActive(source)) {
                    if (indoorAge != null && !indoorAge.isNegative() && indoorAge.compareTo(LIVE_INDOOR_FRESHNESS_WINDOW) <= 0) {
                        return "VERIFIED_INDOOR";
                    }
                }
            }

            // If outdoor GPS is actively streaming and newer than indoor position, GPS takes precedence
            if (isGpsFresh && (indoorTime == null || entity.getLastSeen().isAfter(indoorTime))) {
                return "OUTDOOR_GPS";
            }

            // Treat MANUAL_CONFIG as CONFIGURED_INDOOR (with freshness threshold)
            if (source == IndoorLocationSource.MANUAL_CONFIG) {
                if (indoorAge != null && !indoorAge.isNegative() && indoorAge.compareTo(MANUAL_CONFIG_FRESHNESS_WINDOW) > 0) {
                    return "LAST_KNOWN";
                }
                return "CONFIGURED_INDOOR";
            }

            // Expired QR check-in, unintegrated sensor source, or stale coordinates
            return "LAST_KNOWN";
        }

        // 2. Evaluate Outdoor GPS Fix
        if (hasGpsCoords) {
            if (isGpsFresh) {
                return "OUTDOOR_GPS";
            }
            return "LAST_KNOWN";
        }

        // 3. No usable position available
        return "LOCATION_UNAVAILABLE";
    }

    public Asset toEntity(AssetCreateRequest request) {
        if (request == null) {
            return null;
        }

        return Asset.builder()
                .assetCode(request.getAssetCode())
                .assetName(request.getAssetName())
                .category(request.getCategory())
                .serialNumber(request.getSerialNumber())
                .status(request.getStatus() != null ? request.getStatus() : AssetStatus.AVAILABLE)
                .batteryLevel(request.getBatteryLevel() != null ? request.getBatteryLevel() : 100)
                .qrCode(request.getQrCode())
                .build();
    }
}
