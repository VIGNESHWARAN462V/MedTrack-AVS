package com.medtrack.avs.dto;

import com.medtrack.avs.entity.AlertSeverity;
import com.medtrack.avs.entity.AlertType;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertResponse {
    private Long id;
    private Long assetId;
    private String assetCode;
    private String assetName;
    private Long geofenceId;
    private String geofenceName;
    private Long departmentId;
    private String departmentName;
    private AlertType type;
    private AlertSeverity severity;
    private String message;
    private Double latitude;
    private Double longitude;
    private Double distanceMeters;
    private boolean resolved;
    private Instant occurredAt;
    private Instant createdAt;
    private Instant resolvedAt;
}
