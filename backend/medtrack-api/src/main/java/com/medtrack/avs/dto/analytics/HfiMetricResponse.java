package com.medtrack.avs.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HfiMetricResponse {
    private String metricName;
    private Double overallHfi;
    private Integer completedTransferCount;
    private Double averageTransferDurationSeconds;
    private Double averageHandoffDelaySeconds;
    private Double averageTrackingGapRatio;
    private Double durationComponentScore;
    private Double delayComponentScore;
    private Double gapComponentScore;
    private Double durationWeight;
    private Double delayWeight;
    private Double gapWeight;
    private String formula;
    private String status; // MEASURED, NO_DATA
    private String unit;
    private Instant periodStart;
    private Instant periodEnd;
    private Long departmentId;
    private String departmentName;
    private Long assetId;
    private String assetCode;
    private String interpretation;
    private Double transitReferenceMinutes;
    private Double handoffDelayReferenceMinutes;
    private String thresholdDocumentation;
    private List<TransferHfiDto> transfers;
    private List<DepartmentHfiDto> departmentBreakdown;
}
