package com.medtrack.avs.dto;

import jakarta.validation.constraints.Min;
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
public class FloorCreateRequest {

    @NotNull(message = "Floor number is required (0 for Ground, 1 for Floor 1, -1 for Basement 1)")
    private Integer floorNumber;

    @NotBlank(message = "Display name is required (e.g. Ground Floor, Floor 1)")
    @Size(max = 80, message = "Display name cannot exceed 80 characters")
    private String displayName;

    @Size(max = 500, message = "Floor plan image URL cannot exceed 500 characters")
    private String floorPlanImageUrl;

    @Min(value = 1, message = "Floor plan width must be a positive integer")
    private Integer floorPlanWidth;

    @Min(value = 1, message = "Floor plan height must be a positive integer")
    private Integer floorPlanHeight;

    @Builder.Default
    private Boolean active = true;
}
