package com.medtrack.avs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medtrack.avs.entity.IndoorLocationSource;
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
public class IndoorLocationUpdateRequest {

    @NotNull(message = "Hospital ID is required")
    private Long hospitalId;

    @NotNull(message = "Building ID is required")
    private Long buildingId;

    @NotNull(message = "Floor ID is required")
    private Long floorId;

    private Long roomId;

    @JsonProperty("indoorX")
    @NotNull(message = "Indoor X coordinate percentage (0.0 to 100.0) is required")
    @DecimalMin(value = "0.0", message = "Indoor X coordinate must be >= 0.0%")
    @DecimalMax(value = "100.0", message = "Indoor X coordinate must be <= 100.0%")
    private Double indoorX;

    @JsonProperty("indoorY")
    @NotNull(message = "Indoor Y coordinate percentage (0.0 to 100.0) is required")
    @DecimalMin(value = "0.0", message = "Indoor Y coordinate must be >= 0.0%")
    @DecimalMax(value = "100.0", message = "Indoor Y coordinate must be <= 100.0%")
    private Double indoorY;

    @Builder.Default
    private IndoorLocationSource indoorLocationSource = IndoorLocationSource.MANUAL_CONFIG;
}
