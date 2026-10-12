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
public class RoomResponse {
    private Long id;
    private Long floorId;
    private String floorDisplayName;
    private Long buildingId;
    private String buildingName;
    private Long hospitalId;
    private Long departmentId;
    private String departmentName;
    private String departmentCode;
    private String roomNumber;
    private String name;
    private String description;
    @com.fasterxml.jackson.annotation.JsonProperty("xPosition")
    private Double xPosition;

    @com.fasterxml.jackson.annotation.JsonProperty("yPosition")
    private Double yPosition;
    private Double width;
    private Double height;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
