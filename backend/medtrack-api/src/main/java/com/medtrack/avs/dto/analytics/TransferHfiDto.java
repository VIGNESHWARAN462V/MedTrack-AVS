package com.medtrack.avs.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferHfiDto {
    private Long transferId;
    private Long assetId;
    private String assetCode;
    private String assetName;
    private Long sourceDepartmentId;
    private String sourceDepartmentName;
    private Long destinationDepartmentId;
    private String destinationDepartmentName;
    private Long transferDurationSeconds;
    private Long handoffDelaySeconds;
    private Long trackingGapDurationSeconds;
    private Double trackingGapRatio;
    private Double durationScore;
    private Double delayScore;
    private Double gapScore;
    private Double hfiScore;
    private Instant requestedAt;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;
}
