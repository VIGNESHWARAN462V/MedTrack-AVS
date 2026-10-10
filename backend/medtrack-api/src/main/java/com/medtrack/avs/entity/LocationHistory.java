package com.medtrack.avs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "location_history", indexes = {
        @Index(name = "idx_loc_asset_recorded", columnList = "asset_id, recorded_at"),
        @Index(name = "idx_loc_recorded_at", columnList = "recorded_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private Double accuracy;

    private Double speed;

    private Double heading;

    @Column(length = 50)
    private String source;

    @Column(name = "recorded_at", nullable = false, precision = 6)
    private Instant recordedAt;
}
