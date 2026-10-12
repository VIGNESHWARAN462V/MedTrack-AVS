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
public class BuildingResponse {
    private Long id;
    private Long hospitalId;
    private String hospitalName;
    private String name;
    private String code;
    private String description;
    private Double latitude;
    private Double longitude;
    private boolean active;
    private Long floorCount;
    private Instant createdAt;
    private Instant updatedAt;
}
