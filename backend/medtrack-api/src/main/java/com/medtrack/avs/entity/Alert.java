package com.medtrack.avs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_asset", columnList = "asset_id"),
        @Index(name = "idx_alert_geofence", columnList = "geofence_id"),
        @Index(name = "idx_alert_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_alert_resolved", columnList = "resolved"),
        @Index(name = "idx_alert_res_occ", columnList = "resolved, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geofence_id")
    private Geofence geofence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AlertType type;

    @Column(nullable = false, length = 255)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AlertSeverity severity = AlertSeverity.INFO;

    private Double latitude;

    private Double longitude;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Column(nullable = false)
    @Builder.Default
    private boolean resolved = false;

    @Builder.Default
    @Column(name = "occurred_at", precision = 6)
    private Instant occurredAt = Instant.now();

    @Builder.Default
    @Column(name = "created_at", precision = 6)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at", precision = 6)
    private Instant resolvedAt;
}
