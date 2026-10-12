package com.medtrack.avs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medtrack.avs.entity.AssetStatus;
import com.medtrack.avs.entity.IndoorLocationSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Dedicated WebSocket event for real-time indoor asset positioning updates.
 * Broadcast over STOMP topic:
 * - /topic/tracking/indoor (global indoor asset stream)
 * - /topic/tracking/indoor/floors/{floorId} (floor-specific stream)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndoorTrackingEvent {

    private String eventType; // INDOOR_LOCATION_UPDATED, QR_CHECKIN_UPDATED
    private Long assetId;
    private String assetCode;
    private String assetName;
    private String category;
    private AssetStatus status;

    private Long hospitalId;
    private String hospitalName;
    private Long buildingId;
    private String buildingName;
    private Long floorId;
    private String floorDisplayName;
    private Integer floorNumber;
    private Long roomId;
    private String roomName;
    private String roomNumber;

    @JsonProperty("indoorX")
    private Double indoorX;

    @JsonProperty("indoorY")
    private Double indoorY;

    private IndoorLocationSource indoorLocationSource;
    private Instant indoorUpdatedAt;
    private String locationStatus;

    private Long departmentId;
    private String departmentName;
    private String departmentCode;
}
