package com.medtrack.avs.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentHfiDto {
    private Long departmentId;
    private String departmentName;
    private Integer transferCount;
    private Double averageHfi;
    private Double averageDurationSeconds;
    private Double averageDelaySeconds;
}
