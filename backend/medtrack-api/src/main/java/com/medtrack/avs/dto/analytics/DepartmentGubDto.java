package com.medtrack.avs.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentGubDto {
    private Long departmentId;
    private String departmentName;
    private String departmentCode;
    private Integer floorNumber;
    private Long usableAssetCount;
    private Long availableAssetCount;
    private Long assignedAssetCount;
    private Long inUseAssetCount;
    private Long maintenanceAssetCount;
    private Long inboundTransferCount;
    private Long outboundTransferCount;
    private Long demandCount;
    private Double balanceScore;
    private String status; // SURPLUS, BALANCED, DEFICIT
}
