package com.medtrack.avs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrCheckInRequest {

    @NotNull(message = "Hospital ID is required for QR check-in")
    private Long hospitalId;

    @NotNull(message = "Building ID is required for QR check-in")
    private Long buildingId;

    @NotNull(message = "Floor ID is required for QR check-in")
    private Long floorId;

    private Long roomId;

    @JsonProperty("indoorX")
    @DecimalMin(value = "0.0", message = "Indoor X coordinate must be >= 0.0%")
    @DecimalMax(value = "100.0", message = "Indoor X coordinate must be <= 100.0%")
    private Double indoorX;

    @JsonProperty("indoorY")
    @DecimalMin(value = "0.0", message = "Indoor Y coordinate must be >= 0.0%")
    @DecimalMax(value = "100.0", message = "Indoor Y coordinate must be <= 100.0%")
    private Double indoorY;
}
