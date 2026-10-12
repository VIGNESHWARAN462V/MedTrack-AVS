package com.medtrack.avs.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "buildings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_building_hospital_code", columnNames = {"hospital_id", "code"})
}, indexes = {
        @Index(name = "idx_building_hospital", columnList = "hospital_id"),
        @Index(name = "idx_building_code", columnList = "code"),
        @Index(name = "idx_building_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospital_id", nullable = false)
    private Hospital hospital;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 255)
    private String description;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, precision = 6)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", precision = 6)
    private Instant updatedAt;
}
