package com.medtrack.avs.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "floor_plan_images", indexes = {
        @Index(name = "idx_floor_plan_image_floor", columnList = "floor_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloorPlanImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", nullable = false, unique = true)
    private Floor floor;

    @Column(name = "file_name", nullable = false, length = 180)
    private String fileName;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    @Lob
    @Column(name = "image_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] imageData;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, precision = 6)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", precision = 6)
    private Instant updatedAt;
}
