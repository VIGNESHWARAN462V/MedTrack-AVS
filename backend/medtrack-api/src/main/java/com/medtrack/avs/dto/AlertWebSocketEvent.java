package com.medtrack.avs.dto;

import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertWebSocketEvent {
    private String eventType; // "GEOFENCE_ALERT"
    private Long alertId;
    private Long assetId;
    private String assetCode;
    private Long geofenceId;
    private String geofenceName;
    private String department;
    private String alertType; // "GEOFENCE_ENTER", "GEOFENCE_EXIT"
    private String severity;  // "INFO", "WARNING", "CRITICAL"
    private String message;
    private Double latitude;
    private Double longitude;
    private Double distanceMeters;
    private Instant occurredAt;
}
