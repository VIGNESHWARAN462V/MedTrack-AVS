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
public class AnalyticsOverviewResponse {
    private Instant periodStart;
    private Instant periodEnd;
    private Long departmentId;
    private String departmentName;
    private Long assetId;
    private String assetCode;

    // AMC summary
    private Double amcPercentage;
    private String amcStatus;
    private Long successfulGpsUpdates;
    private Long expectedGpsUpdates;
    private Integer trackedAssetsCount;

    // HFI summary
    private Double hfiScore;
    private String hfiStatus;
    private Integer completedTransfersCount;
    private Double averageTransferDurationSeconds;
    private Double averageHandoffDelaySeconds;

    // GUB summary
    private Double hospitalEquilibriumScore;
    private String gubStatus;
    private Long totalUsableAssets;
    private Long totalDemand;

    // Visual datasets
    private List<AmcDataPointDto> amcTimeSeries;
    private List<DepartmentGubDto> departmentBalances;
    private List<TransferHfiDto> recentTransfers;
}
