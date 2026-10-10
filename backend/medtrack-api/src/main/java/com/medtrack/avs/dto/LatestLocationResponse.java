package com.medtrack.avs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LatestLocationResponse {
    private Long assetId;
    private String assetCode;
    private String assetName;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private Double speed;
    private Double heading;
    private String source;
    private Instant recordedAt;
    private String status;
    private boolean hasLocation;
}
