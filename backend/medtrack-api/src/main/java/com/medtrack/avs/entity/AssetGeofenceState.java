package com.medtrack.avs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "asset_geofence_states", uniqueConstraints = {
        @UniqueConstraint(name = "uk_asset_geofence_state", columnNames = {"asset_id", "geofence_id"})
}, indexes = {
        @Index(name = "idx_asset_geo_asset", columnList = "asset_id"),
        @Index(name = "idx_asset_geo_geofence", columnList = "geofence_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetGeofenceState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geofence_id", nullable = false)
    private Geofence geofence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GeofenceStatus state = GeofenceStatus.UNKNOWN;

    @Column(name = "last_distance_meters")
    private Double lastDistanceMeters;

    @Column(name = "last_transition_at", precision = 6)
    private Instant lastTransitionAt;

    @Column(name = "updated_at", nullable = false, precision = 6)
    private Instant updatedAt;
}
