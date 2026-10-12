package com.medtrack.avs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medtrack.avs.entity.AssetStatus;
import com.medtrack.avs.entity.IndoorLocationSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloorAssetResponse {

    private Long id;
    private String assetCode;
    private String assetName;
    private String category;
    private String serialNumber;
    private AssetStatus status;

    // Facility Hierarchy
    private Long hospitalId;
    private String hospitalName;
    private Long buildingId;
    private String buildingName;
    private Long floorId;
    private String floorDisplayName;
    private Integer floorNumber;
    private Long roomId;
    private String roomName;
    private String roomNumber;

    // Indoor Coordinates & Telemetry
    @JsonProperty("indoorX")
    private Double indoorX;

    @JsonProperty("indoorY")
    private Double indoorY;

    private IndoorLocationSource indoorLocationSource;
    private Instant indoorUpdatedAt;

    // Location Status Semantics (VERIFIED_INDOOR, CONFIGURED_INDOOR, OUTDOOR_GPS, LAST_KNOWN, LOCATION_UNAVAILABLE)
    private String locationStatus;

    // Department Information
    private Long departmentId;
    private String departmentName;
    private String departmentCode;

    // Separate Outdoor GPS fix (authoritatively preserved)
    private Double outdoorLatitude;
    private Double outdoorLongitude;
    private Double outdoorGpsAccuracy;
    private Instant outdoorLastSeen;

    private Integer batteryLevel;
    private String qrCode;
}
