package com.medtrack.avs.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeofenceUpdateRequest {

    @Size(max = 120, message = "Geofence name cannot exceed 120 characters")
    private String name;

    private Long departmentId;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and +90 degrees")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and +90 degrees")
    private Double centerLatitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and +180 degrees")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and +180 degrees")
    private Double centerLongitude;

    @DecimalMin(value = "0.1", message = "Radius must be greater than 0 meters")
    private Double radiusMeters;

    private Boolean active;
}
