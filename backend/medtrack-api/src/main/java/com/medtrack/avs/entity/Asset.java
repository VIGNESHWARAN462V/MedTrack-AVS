package com.medtrack.avs.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "assets", indexes = {
        @Index(name = "idx_asset_code", columnList = "asset_code"),
        @Index(name = "idx_asset_serial", columnList = "serial_number"),
        @Index(name = "idx_asset_status", columnList = "status"),
        @Index(name = "idx_asset_qr", columnList = "qr_code"),
        @Index(name = "idx_asset_dept", columnList = "department_id"),
        @Index(name = "idx_asset_floor", columnList = "floor_id"),
        @Index(name = "idx_asset_room", columnList = "room_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_code", nullable = false, unique = true, length = 50)
    private String assetCode;

    @Column(name = "asset_name", nullable = false, length = 120)
    private String assetName;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(name = "serial_number", nullable = false, unique = true, length = 80)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private AssetStatus status = AssetStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(name = "gps_accuracy")
    private Double gpsAccuracy;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "qr_code", unique = true, length = 255)
    private String qrCode;

    @Column(name = "last_seen", precision = 6)
    private Instant lastSeen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private Floor floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "indoor_x")
    private Double indoorX;

    @Column(name = "indoor_y")
    private Double indoorY;

    @Enumerated(EnumType.STRING)
    @Column(name = "indoor_location_source", length = 30)
    private IndoorLocationSource indoorLocationSource;

    @Column(name = "indoor_updated_at", precision = 6)
    private Instant indoorUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, precision = 6)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", precision = 6)
    private Instant updatedAt;
}
