package com.medtrack.avs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Dedicated WebSocket Telemetry Event DTO.
 * Transmitted over STOMP topics:
 * - /topic/tracking/assets (global authorized feed)
 * - /topic/tracking/assets/{assetId} (asset-specific feed)
 *
 * recordedAt MUST be the exact authoritative persisted Instant from LocationHistory / Asset.lastSeen.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackingTelemetryEvent {

    private String eventType;      // LOCATION_UPDATED, SESSION_STARTED, SESSION_STOPPED
    private Long assetId;
    private String assetCode;
    private String assetName;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private Double speed;
    private Double heading;
    private Instant recordedAt;     // Exact microsecond Instant
    private String status;         // MOVING, ASSIGNED, AVAILABLE, etc.
    private String department;
    private String assignedUser;
    private String source;
}
