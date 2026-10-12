package com.medtrack.avs.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomCreateRequest {

    private Long departmentId;

    @NotBlank(message = "Room number is required (e.g. ROOM-101, ER-BAY-1)")
    @Size(max = 50, message = "Room number cannot exceed 50 characters")
    private String roomNumber;

    @NotBlank(message = "Room name is required")
    @Size(max = 120, message = "Room name cannot exceed 120 characters")
    private String name;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    @com.fasterxml.jackson.annotation.JsonProperty("xPosition")
    @NotNull(message = "xPosition percentage (0.0 to 100.0) is required")
    @DecimalMin(value = "0.0", message = "xPosition must be >= 0.0%")
    @DecimalMax(value = "100.0", message = "xPosition must be <= 100.0%")
    private Double xPosition;

    @com.fasterxml.jackson.annotation.JsonProperty("yPosition")
    @NotNull(message = "yPosition percentage (0.0 to 100.0) is required")
    @DecimalMin(value = "0.0", message = "yPosition must be >= 0.0%")
    @DecimalMax(value = "100.0", message = "yPosition must be <= 100.0%")
    private Double yPosition;

    @NotNull(message = "width percentage (0.1 to 100.0) is required")
    @DecimalMin(value = "0.1", message = "width must be > 0.0%")
    @DecimalMax(value = "100.0", message = "width must be <= 100.0%")
    private Double width;

    @NotNull(message = "height percentage (0.1 to 100.0) is required")
    @DecimalMin(value = "0.1", message = "height must be > 0.0%")
    @DecimalMax(value = "100.0", message = "height must be <= 100.0%")
    private Double height;

    @Builder.Default
    private Boolean active = true;
}
