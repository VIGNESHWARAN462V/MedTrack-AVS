package com.medtrack.avs.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeofenceResponse {
    private Long id;
    private String name;
    private Long departmentId;
    private String departmentName;
    private String departmentCode;
    private Double centerLatitude;
    private Double centerLongitude;
    private Double radiusMeters;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
