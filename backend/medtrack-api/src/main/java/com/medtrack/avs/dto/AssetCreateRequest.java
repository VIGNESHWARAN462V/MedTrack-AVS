package com.medtrack.avs.dto;

import com.medtrack.avs.entity.AssetStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetCreateRequest {

    @NotBlank(message = "Asset code is required")
    @Size(max = 50, message = "Asset code must not exceed 50 characters")
    private String assetCode;

    @NotBlank(message = "Asset name is required")
    @Size(max = 120, message = "Asset name must not exceed 120 characters")
    private String assetName;

    @NotBlank(message = "Category is required")
    @Size(max = 80, message = "Category must not exceed 80 characters")
    private String category;

    @NotBlank(message = "Serial number is required")
    @Size(max = 80, message = "Serial number must not exceed 80 characters")
    private String serialNumber;

    private AssetStatus status;

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    @PositiveOrZero(message = "Battery level cannot be less than 0")
    @Max(value = 100, message = "Battery level cannot exceed 100")
    private Integer batteryLevel;

    @Size(max = 255, message = "QR code must not exceed 255 characters")
    private String qrCode;
}

