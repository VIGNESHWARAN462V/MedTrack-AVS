package com.medtrack.avs.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "floors", uniqueConstraints = {
        @UniqueConstraint(name = "uk_floor_building_number", columnNames = {"building_id", "floor_number"})
}, indexes = {
        @Index(name = "idx_floor_building", columnList = "building_id"),
        @Index(name = "idx_floor_number", columnList = "floor_number"),
        @Index(name = "idx_floor_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Floor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "floor_plan_image_url", length = 500)
    private String floorPlanImageUrl;

    @Column(name = "floor_plan_width")
    private Integer floorPlanWidth;

    @Column(name = "floor_plan_height")
    private Integer floorPlanHeight;

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
