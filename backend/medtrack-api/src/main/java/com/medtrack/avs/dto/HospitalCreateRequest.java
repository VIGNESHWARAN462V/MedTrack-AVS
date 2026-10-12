package com.medtrack.avs.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HospitalCreateRequest {

    @NotBlank(message = "Hospital name is required")
    @Size(max = 150, message = "Hospital name cannot exceed 150 characters")
    private String name;

    @NotBlank(message = "Hospital code is required")
    @Size(max = 50, message = "Hospital code cannot exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Hospital code must contain only alphanumeric characters, dashes, or underscores")
    private String code;

    @Size(max = 255, message = "Address cannot exceed 255 characters")
    private String address;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and +90 degrees")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and +90 degrees")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and +180 degrees")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and +180 degrees")
    private Double longitude;

    @DecimalMin(value = "1.0", message = "Geofence radius must be at least 1.0 meter")
    @DecimalMax(value = "10000.0", message = "Geofence radius cannot exceed 10000 meters")
    @Builder.Default
    private Double geofenceRadiusMeters = 150.0;

    @Builder.Default
    private Boolean active = true;
}
