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
public class AmcMetricResponse {
    private String metricName;
    private Double continuityPercentage;
    private Long successfulGpsUpdates;
    private Long expectedGpsUpdates;
    private Long activeTrackingDurationSeconds;
    private Integer sessionCount;
    private Integer assetsTrackedCount;
    private Double averageAccuracyMeters;
    private Double bestAccuracyMeters;
    private Double worstAccuracyMeters;
    private Integer trackingGapsCount;
    private Long totalGapDurationSeconds;
    private Integer telemetryIntervalSeconds;
    private String status; // MEASURED, NO_DATA
    private String unit;
    private Instant periodStart;
    private Instant periodEnd;
    private Long departmentId;
    private String departmentName;
    private Long assetId;
    private String assetCode;
    private String interpretation;
    private List<AmcDataPointDto> timeSeries;
}
