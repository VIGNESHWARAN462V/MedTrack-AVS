package com.medtrack.avs.dto;

import com.medtrack.avs.entity.AssetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetResponse {
    private Long id;
    private String assetCode;
    private String assetName;
    private String category;
    private String serialNumber;
    private AssetStatus status;
    private Long departmentId;
    private String departmentName;
    private Double latitude;
    private Double longitude;
    private Double gpsAccuracy;
    private Integer batteryLevel;
    private String qrCode;
    private Instant lastSeen;
    private Long hospitalId;
    private String hospitalName;
    private Long buildingId;
    private String buildingName;
    private Long floorId;
    private String floorDisplayName;
    private Long roomId;
    private String roomName;
    private String roomNumber;
    @com.fasterxml.jackson.annotation.JsonProperty("indoorX")
    private Double indoorX;
    @com.fasterxml.jackson.annotation.JsonProperty("indoorY")
    private Double indoorY;
    private com.medtrack.avs.entity.IndoorLocationSource indoorLocationSource;
    private Instant indoorUpdatedAt;
    private String locationStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
