package com.medtrack.avs.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HospitalUpdateRequest {

    @Size(max = 150, message = "Hospital name cannot exceed 150 characters")
    private String name;

    @Size(max = 255, message = "Address cannot exceed 255 characters")
    private String address;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and +90 degrees")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and +90 degrees")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and +180 degrees")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and +180 degrees")
    private Double longitude;

    @DecimalMin(value = "1.0", message = "Geofence radius must be at least 1.0 meter")
    @DecimalMax(value = "10000.0", message = "Geofence radius cannot exceed 10000 meters")
    private Double geofenceRadiusMeters;

    private Boolean active;
}
