package com.medtrack.avs.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloorUpdateRequest {

    @Size(max = 80, message = "Display name cannot exceed 80 characters")
    private String displayName;

    @Size(max = 500, message = "Floor plan image URL cannot exceed 500 characters")
    private String floorPlanImageUrl;

    @Min(value = 1, message = "Floor plan width must be a positive integer")
    private Integer floorPlanWidth;

    @Min(value = 1, message = "Floor plan height must be a positive integer")
    private Integer floorPlanHeight;

    private Boolean active;
}
