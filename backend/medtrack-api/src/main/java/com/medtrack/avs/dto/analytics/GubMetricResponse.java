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
public class GubMetricResponse {
    private String metricName;
    private Double hospitalEquilibriumScore;
    private Double averageBalanceScore;
    private Long totalUsableAssets;
    private Long totalDemand;
    private Integer departmentCount;
    private String supplyDefinition;
    private String demandDefinition;
    private String formula;
    private String status; // MEASURED, NO_DATA
    private String unit;
    private Instant periodStart;
    private Instant periodEnd;
    private String interpretation;
    private List<DepartmentGubDto> departments;
}
