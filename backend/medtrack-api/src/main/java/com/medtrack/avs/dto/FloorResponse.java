package com.medtrack.avs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloorResponse {
    private Long id;
    private Long buildingId;
    private String buildingName;
    private Integer floorNumber;
    private String displayName;
    private String floorPlanImageUrl;
    private Integer floorPlanWidth;
    private Integer floorPlanHeight;
    private boolean active;
    private Long roomCount;
    private Instant createdAt;
    private Instant updatedAt;
}
