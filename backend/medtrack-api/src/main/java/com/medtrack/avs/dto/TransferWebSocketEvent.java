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
public class TransferWebSocketEvent {

    private String eventType; // TRANSFER_REQUESTED, TRANSFER_ACCEPTED, TRANSFER_STARTED, TRANSFER_COMPLETED, TRANSFER_REJECTED, TRANSFER_CANCELLED
    private Long transferId;
    private Long assetId;
    private String assetCode;

    // Explicit department fields for parity across Database, REST, and STOMP
    private Long sourceDepartmentId;
    private String sourceDepartmentName;
    private Long destinationDepartmentId;
    private String destinationDepartmentName;

    // Legacy department names for backward compatibility
    private String sourceDepartment;
    private String destinationDepartment;

    // Explicit user fields for parity across Database, REST, and STOMP
    private Long sourceUserId;
    private String sourceUserName;
    private Long destinationUserId;
    private String destinationUserName;

    // Legacy user names for backward compatibility
    private String sourceUser;
    private String destinationUser;

    // Requester fields
    private Long requestedByUserId;
    private String requestedByUserName;
    private String requestedBy;

    // Status & Timestamp
    private TransferStatus status;
    private Instant occurredAt;
}
