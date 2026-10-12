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
public class BuildingUpdateRequest {

    @Size(max = 150, message = "Building name cannot exceed 150 characters")
    private String name;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and +90 degrees")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and +90 degrees")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and +180 degrees")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and +180 degrees")
    private Double longitude;

    private Boolean active;
}
