package com.medtrack.avs.dto;

import com.medtrack.avs.entity.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponse {

    private Long id;

    private Long assetId;
    private String assetCode;
    private String assetName;

    private Long sourceDepartmentId;
    private String sourceDepartmentName;
    private String sourceDepartmentCode;

    private Long sourceUserId;
    private String sourceUserName;

    private Long destinationDepartmentId;
    private String destinationDepartmentName;
    private String destinationDepartmentCode;

    private Long destinationUserId;
    private String destinationUserName;

    private Long requestedById;
    private String requestedByName;

    private Long approvedById;
    private String approvedByName;

    private TransferStatus status;

    private Instant requestedAt;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private Instant rejectedAt;

    private String notes;
    private String rejectionReason;

    private Instant createdAt;
    private Instant updatedAt;
}
