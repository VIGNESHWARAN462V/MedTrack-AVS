package com.medtrack.avs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferCreateRequest {

    @NotNull(message = "Asset ID is required")
    private Long assetId;

    @NotNull(message = "Destination Department ID is required")
    private Long destinationDepartmentId;

    private Long destinationUserId;

    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;
}
