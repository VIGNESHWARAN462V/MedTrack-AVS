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
public class HospitalResponse {
    private Long id;
    private String name;
    private String code;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double geofenceRadiusMeters;
    private boolean active;
    private Long buildingCount;
    private Instant createdAt;
    private Instant updatedAt;
}
