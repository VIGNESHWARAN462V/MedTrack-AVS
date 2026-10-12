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
public class RoomUpdateRequest {

    private Long departmentId;

    @Size(max = 120, message = "Room name cannot exceed 120 characters")
    private String name;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    @com.fasterxml.jackson.annotation.JsonProperty("xPosition")
    @DecimalMin(value = "0.0", message = "xPosition must be >= 0.0%")
    @DecimalMax(value = "100.0", message = "xPosition must be <= 100.0%")
    private Double xPosition;

    @com.fasterxml.jackson.annotation.JsonProperty("yPosition")
    @DecimalMin(value = "0.0", message = "yPosition must be >= 0.0%")
    @DecimalMax(value = "100.0", message = "yPosition must be <= 100.0%")
    private Double yPosition;

    @DecimalMin(value = "0.1", message = "width must be > 0.0%")
    @DecimalMax(value = "100.0", message = "width must be <= 100.0%")
    private Double width;

    @DecimalMin(value = "0.1", message = "height must be > 0.0%")
    @DecimalMax(value = "100.0", message = "height must be <= 100.0%")
    private Double height;

    private Boolean active;
}
