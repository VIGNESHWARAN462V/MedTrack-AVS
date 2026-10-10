package com.medtrack.avs.dto;

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
public class LocationTelemetryRequest {

    @NotNull(message = "assetId is required")
    private Long assetId;

    @NotNull(message = "latitude is required")
    @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "longitude is required")
    @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
    private Double longitude;

    @NotNull(message = "accuracy is required")
    @DecimalMin(value = "0.0", message = "accuracy must be positive or zero")
    private Double accuracy;

    @DecimalMin(value = "0.0", message = "speed must be positive or zero when present")
    private Double speed;

    @DecimalMin(value = "0.0", message = "heading must be between 0 and 360 when present")
    @DecimalMax(value = "360.0", message = "heading must be between 0 and 360 when present")
    private Double heading;

    @NotNull(message = "recordedAt is required")
    private String recordedAt;
}
