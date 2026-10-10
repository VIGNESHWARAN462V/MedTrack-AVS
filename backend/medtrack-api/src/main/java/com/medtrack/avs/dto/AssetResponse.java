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
    private Instant createdAt;
    private Instant updatedAt;
}
