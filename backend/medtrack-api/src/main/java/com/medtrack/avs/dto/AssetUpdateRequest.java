package com.medtrack.avs.dto;

import com.medtrack.avs.entity.AssetStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetUpdateRequest {

    @Size(max = 120, message = "Asset name must not exceed 120 characters")
    private String assetName;

    @Size(max = 80, message = "Category must not exceed 80 characters")
    private String category;

    @Size(max = 80, message = "Serial number must not exceed 80 characters")
    private String serialNumber;

    private AssetStatus status;

    private Long departmentId;

    @PositiveOrZero(message = "Battery level cannot be less than 0")
    @Max(value = 100, message = "Battery level cannot exceed 100")
    private Integer batteryLevel;

    @Size(max = 255, message = "QR code must not exceed 255 characters")
    private String qrCode;
}

