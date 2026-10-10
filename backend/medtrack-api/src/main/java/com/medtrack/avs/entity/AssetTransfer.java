package com.medtrack.avs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "asset_transfers", indexes = {
        @Index(name = "idx_transfer_asset", columnList = "asset_id"),
        @Index(name = "idx_transfer_status", columnList = "status"),
        @Index(name = "idx_transfer_from_dept", columnList = "from_department_id"),
        @Index(name = "idx_transfer_to_dept", columnList = "to_department_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_department_id")
    private Department fromDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id")
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_department_id", nullable = false)
    private Department toDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id")
    private User toUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TransferStatus status = TransferStatus.REQUESTED;

    @Column(name = "requested_at", columnDefinition = "DATETIME(6)", nullable = false)
    @Builder.Default
    private Instant requestedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Column(name = "accepted_at", columnDefinition = "DATETIME(6)")
    private Instant acceptedAt;

    @Column(name = "started_at", columnDefinition = "DATETIME(6)")
    private Instant startedAt;

    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private Instant completedAt;

    @Column(name = "cancelled_at", columnDefinition = "DATETIME(6)")
    private Instant cancelledAt;

    @Column(name = "rejected_at", columnDefinition = "DATETIME(6)")
    private Instant rejectedAt;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    @Builder.Default
    private Instant updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (requestedAt == null) requestedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
