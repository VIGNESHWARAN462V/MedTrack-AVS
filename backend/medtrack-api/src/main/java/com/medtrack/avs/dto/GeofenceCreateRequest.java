package com.medtrack.avs.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeofenceCreateRequest {

    @NotBlank(message = "Geofence name is required")
    @Size(max = 120, message = "Geofence name cannot exceed 120 characters")
    private String name;

    private Long departmentId;

    @NotNull(message = "Center latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and +90 degrees")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and +90 degrees")
    private Double centerLatitude;

    @NotNull(message = "Center longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and +180 degrees")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and +180 degrees")
    private Double centerLongitude;

    @NotNull(message = "Radius is required")
    @DecimalMin(value = "0.1", message = "Radius must be greater than 0 meters")
    private Double radiusMeters;

    @Builder.Default
    private Boolean active = true;
}
