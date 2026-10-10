package com.medtrack.avs.service;

import com.medtrack.avs.dto.*;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.event.TransferPersistedEvent;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ConflictException;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AssetTransferRepository transferRepository;
    private final AssetRepository assetRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    private static final List<TransferStatus> ACTIVE_TRANSFER_STATUSES = List.of(
            TransferStatus.REQUESTED,
            TransferStatus.ACCEPTED,
            TransferStatus.IN_TRANSIT
    );

    /**
     * Request a new asset transfer.
     */
    @Transactional
    public TransferResponse requestTransfer(TransferCreateRequest request, User currentUser) {
        log.info("User {} requesting transfer for asset ID: {}", currentUser.getEmail(), request.getAssetId());

        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with ID: " + request.getAssetId()));

        Department destDept = departmentRepository.findById(request.getDestinationDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + request.getDestinationDepartmentId()));

        User destUser = null;
        if (request.getDestinationUserId() != null) {
            destUser = userRepository.findById(request.getDestinationUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination user not found with ID: " + request.getDestinationUserId()));
        }

        // Validate asset eligibility
        if (asset.getStatus() == AssetStatus.MAINTENANCE) {
            throw new ConflictException("Asset " + asset.getAssetCode() + " is under maintenance and cannot be transferred");
        }

        // Check for conflicting active transfer
        if (transferRepository.existsByAssetIdAndStatusIn(asset.getId(), ACTIVE_TRANSFER_STATUSES)) {
            throw new ConflictException("Asset " + asset.getAssetCode() + " already has an active transfer in progress");
        }

        // Authoritatively derive current assignment from DB
        Optional<AssetAssignment> currentAssignOpt = assetAssignmentRepository.findByAssetIdAndActiveTrue(asset.getId());
        Department fromDept = currentAssignOpt.map(AssetAssignment::getDepartment).orElse(asset.getDepartment());
        User fromUser = currentAssignOpt.map(AssetAssignment::getAssignedUser).orElse(null);

        // Authorization check for STAFF: can only request transfer for asset currently assigned to them
        if (currentUser.getRole().getName() == RoleType.STAFF) {
            if (fromUser == null || !fromUser.getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Staff members can only request transfers for assets currently assigned to them");
            }
        }

        // Validate that destination differs from source
        if (fromDept != null && fromDept.getId().equals(destDept.getId())) {
            if (destUser == null || (fromUser != null && destUser.getId().equals(fromUser.getId()))) {
                throw new BadRequestException("Destination department/user must differ from current source department/user");
            }
        }

        Instant now = nowMicro();
        AssetTransfer transfer = AssetTransfer.builder()
                .asset(asset)
                .fromDepartment(fromDept)
                .fromUser(fromUser)
                .toDepartment(destDept)
                .toUser(destUser)
                .requestedBy(currentUser)
                .status(TransferStatus.REQUESTED)
                .requestedAt(now)
                .notes(request.getNotes())
                .createdAt(now)
                .updatedAt(now)
                .build();

        transfer = transferRepository.save(transfer);

        auditService.logTransferEvent(currentUser, "TRANSFER_REQUESTED", transfer.getId(), asset.getId(),
                "Transfer requested for " + asset.getAssetCode() + " to " + destDept.getName());

        publishWebSocketEvent("TRANSFER_REQUESTED", transfer);

        return mapToResponse(transfer);
    }

    /**
     * Get transfers matching optional query filters.
     */
    @Transactional(readOnly = true)
    public List<TransferResponse> getTransfers(TransferStatus status, Long assetId, Long departmentId, Long requestedBy, User currentUser) {
        List<AssetTransfer> transfers = transferRepository.findWithFilters(status, assetId, departmentId, requestedBy);

        // Security check for STAFF: only view transfers where staff member is requester, source user, or destination user
        if (currentUser.getRole().getName() == RoleType.STAFF) {
            transfers = transfers.stream()
                    .filter(t -> (t.getRequestedBy() != null && t.getRequestedBy().getId().equals(currentUser.getId())) ||
                                 (t.getFromUser() != null && t.getFromUser().getId().equals(currentUser.getId())) ||
                                 (t.getToUser() != null && t.getToUser().getId().equals(currentUser.getId())))
                    .collect(Collectors.toList());
        }

        return transfers.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Get single transfer by ID.
     */
    @Transactional(readOnly = true)
    public TransferResponse getTransferById(Long id, User currentUser) {
        AssetTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with ID: " + id));

        if (currentUser.getRole().getName() == RoleType.STAFF) {
            boolean isParty = (transfer.getRequestedBy() != null && transfer.getRequestedBy().getId().equals(currentUser.getId())) ||
                              (transfer.getFromUser() != null && transfer.getFromUser().getId().equals(currentUser.getId())) ||
                              (transfer.getToUser() != null && transfer.getToUser().getId().equals(currentUser.getId()));
            if (!isParty) {
                throw new AccessDeniedException("Access denied: you are not authorized to view transfer #" + id);
            }
        }

        return mapToResponse(transfer);
    }

    /**
     * Accept a pending transfer request.
     * Transitions: REQUESTED -> ACCEPTED
     */
    @Transactional
    public TransferResponse acceptTransfer(Long id, User currentUser) {
        AssetTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with ID: " + id));

        if (transfer.getStatus() != TransferStatus.REQUESTED) {
            throw new ConflictException("Transfer #" + id + " cannot be accepted in status " + transfer.getStatus());
        }

        // Authorization: Admin, Destination Department Supervisor, or Destination User
        boolean isAuthorized = currentUser.getRole().getName() == RoleType.ADMIN ||
                currentUser.getRole().getName() == RoleType.SUPERVISOR ||
                (transfer.getToUser() != null && transfer.getToUser().getId().equals(currentUser.getId()));

        if (!isAuthorized) {
            throw new AccessDeniedException("Only the receiving supervisor or designated recipient can accept this transfer");
        }

        Instant now = nowMicro();
        transfer.setStatus(TransferStatus.ACCEPTED);
        transfer.setApprovedBy(currentUser);
        transfer.setAcceptedAt(now);
        transfer.setUpdatedAt(now);
        transfer = transferRepository.save(transfer);

        auditService.logTransferEvent(currentUser, "TRANSFER_ACCEPTED", transfer.getId(), transfer.getAsset().getId(),
                "Transfer #" + id + " accepted by " + currentUser.getName());

        publishWebSocketEvent("TRANSFER_ACCEPTED", transfer);

        return mapToResponse(transfer);
    }

    /**
     * Reject a pending transfer request.
     * Transitions: REQUESTED -> REJECTED
     */
    @Transactional
    public TransferResponse rejectTransfer(Long id, TransferRejectRequest request, User currentUser) {
        AssetTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with ID: " + id));

        if (transfer.getStatus() != TransferStatus.REQUESTED) {
            throw new ConflictException("Transfer #" + id + " cannot be rejected in status " + transfer.getStatus());
        }

        boolean isAuthorized = currentUser.getRole().getName() == RoleType.ADMIN ||
                currentUser.getRole().getName() == RoleType.SUPERVISOR ||
                (transfer.getToUser() != null && transfer.getToUser().getId().equals(currentUser.getId()));

        if (!isAuthorized) {
            throw new AccessDeniedException("Only the receiving supervisor or designated recipient can reject this transfer");
        }

        Instant now = nowMicro();
        transfer.setStatus(TransferStatus.REJECTED);
        transfer.setApprovedBy(currentUser);
        transfer.setRejectedAt(now);
        transfer.setRejectionReason(request != null ? request.getReason() : null);
        transfer.setUpdatedAt(now);
        transfer = transferRepository.save(transfer);

        auditService.logTransferEvent(currentUser, "TRANSFER_REJECTED", transfer.getId(), transfer.getAsset().getId(),
                "Transfer #" + id + " rejected: " + (request != null ? request.getReason() : "No reason provided"));

        publishWebSocketEvent("TRANSFER_REJECTED", transfer);

        return mapToResponse(transfer);
    }

    /**
     * Start transfer physical movement.
     * Transitions: ACCEPTED / APPROVED -> IN_TRANSIT
     */
    @Transactional
    public TransferResponse startTransfer(Long id, User currentUser) {
        AssetTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with ID: " + id));

        if (transfer.getStatus() != TransferStatus.ACCEPTED) {
            throw new ConflictException("Transfer #" + id + " must be accepted before starting transit (current status: " + transfer.getStatus() + ")");
        }

        // Authorization: Admin, Source Supervisor, Requester, or Source User
        boolean isAuthorized = currentUser.getRole().getName() == RoleType.ADMIN ||
                currentUser.getRole().getName() == RoleType.SUPERVISOR ||
                (transfer.getRequestedBy() != null && transfer.getRequestedBy().getId().equals(currentUser.getId())) ||
                (transfer.getFromUser() != null && transfer.getFromUser().getId().equals(currentUser.getId()));

        if (!isAuthorized) {
            throw new AccessDeniedException("You are not authorized to start transport for transfer #" + id);
        }

        Instant now = nowMicro();
        transfer.setStatus(TransferStatus.IN_TRANSIT);
        transfer.setStartedAt(now);
        transfer.setUpdatedAt(now);

        // Update asset status to IN_TRANSIT while moving
        Asset asset = transfer.getAsset();
        asset.setStatus(AssetStatus.IN_TRANSIT);
        assetRepository.save(asset);

        transfer = transferRepository.save(transfer);

        auditService.logTransferEvent(currentUser, "TRANSFER_STARTED", transfer.getId(), asset.getId(),
                "Asset " + asset.getAssetCode() + " transport started by " + currentUser.getName());

        publishWebSocketEvent("TRANSFER_STARTED", transfer);

        return mapToResponse(transfer);
    }

    /**
     * Complete transfer and transactionally reassign the asset.
     * Transitions: IN_TRANSIT (or ACCEPTED/APPROVED) -> COMPLETED
     */
    @Transactional
    public TransferResponse completeTransfer(Long id, User currentUser) {
        AssetTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with ID: " + id));

        if (transfer.getStatus() != TransferStatus.IN_TRANSIT &&
            transfer.getStatus() != TransferStatus.ACCEPTED) {
            throw new ConflictException("Transfer #" + id + " cannot be completed in status " + transfer.getStatus());
        }

        // Authorization: Admin, Destination Supervisor, or Destination User
        boolean isAuthorized = currentUser.getRole().getName() == RoleType.ADMIN ||
                currentUser.getRole().getName() == RoleType.SUPERVISOR ||
                (transfer.getToUser() != null && transfer.getToUser().getId().equals(currentUser.getId()));

        if (!isAuthorized) {
            throw new AccessDeniedException("Only the receiving supervisor or designated recipient can complete this transfer");
        }

        Asset asset = transfer.getAsset();

        // 1. Transactionally inactivate old assignments
        List<AssetAssignment> currentAssignments = assetAssignmentRepository.findByAssetId(asset.getId());
        for (AssetAssignment assign : currentAssignments) {
            if (assign.isActive()) {
                assign.setActive(false);
                assign.setReleasedAt(LocalDateTime.now(ZoneOffset.UTC));
                assetAssignmentRepository.save(assign);
            }
        }

        // 2. Create destination assignment
        User targetUser = transfer.getToUser();
        if (targetUser == null && currentUser.getRole().getName() != RoleType.ADMIN) {
            targetUser = currentUser;
        }

        if (targetUser != null) {
            AssetAssignment newAssign = AssetAssignment.builder()
                    .asset(asset)
                    .assignedUser(targetUser)
                    .department(transfer.getToDepartment())
                    .assignedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .active(true)
                    .build();
            assetAssignmentRepository.save(newAssign);
        }

        // 3. Update asset department and status
        asset.setDepartment(transfer.getToDepartment());
        asset.setStatus(targetUser != null ? AssetStatus.ASSIGNED : AssetStatus.AVAILABLE);
        assetRepository.save(asset);

        // 4. Update transfer record
        Instant now = nowMicro();
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setCompletedAt(now);
        transfer.setUpdatedAt(now);
        transfer = transferRepository.save(transfer);

        auditService.logTransferEvent(currentUser, "TRANSFER_COMPLETED", transfer.getId(), asset.getId(),
                "Transfer completed: " + asset.getAssetCode() + " reassigned to " + transfer.getToDepartment().getName());

        publishWebSocketEvent("TRANSFER_COMPLETED", transfer);

        return mapToResponse(transfer);
    }

    /**
     * Cancel a transfer before completion.
     * Transitions: REQUESTED / ACCEPTED / IN_TRANSIT -> CANCELLED
     */
    @Transactional
    public TransferResponse cancelTransfer(Long id, TransferCancelRequest request, User currentUser) {
        AssetTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with ID: " + id));

        if (transfer.getStatus() == TransferStatus.COMPLETED ||
            transfer.getStatus() == TransferStatus.REJECTED ||
            transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new ConflictException("Transfer #" + id + " is in terminal state " + transfer.getStatus() + " and cannot be cancelled");
        }

        // Authorization: Admin, Requester, or Source Supervisor
        boolean isAuthorized = currentUser.getRole().getName() == RoleType.ADMIN ||
                currentUser.getRole().getName() == RoleType.SUPERVISOR ||
                (transfer.getRequestedBy() != null && transfer.getRequestedBy().getId().equals(currentUser.getId()));

        if (!isAuthorized) {
            throw new AccessDeniedException("You are not authorized to cancel transfer #" + id);
        }

        Asset asset = transfer.getAsset();

        // If asset was IN_TRANSIT, restore status
        if (asset.getStatus() == AssetStatus.IN_TRANSIT) {
            boolean hasActive = assetAssignmentRepository.existsByAssetIdAndActiveTrue(asset.getId());
            asset.setStatus(hasActive ? AssetStatus.ASSIGNED : AssetStatus.AVAILABLE);
            assetRepository.save(asset);
        }

        Instant now = nowMicro();
        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setCancelledAt(now);
        transfer.setRejectionReason(request != null ? request.getReason() : null);
        transfer.setUpdatedAt(now);
        transfer = transferRepository.save(transfer);

        auditService.logTransferEvent(currentUser, "TRANSFER_CANCELLED", transfer.getId(), asset.getId(),
                "Transfer #" + id + " cancelled: " + (request != null ? request.getReason() : "No reason provided"));

        publishWebSocketEvent("TRANSFER_CANCELLED", transfer);

        return mapToResponse(transfer);
    }

    /**
     * Check if asset has an active transfer in progress.
     */
    @Transactional(readOnly = true)
    public Optional<TransferResponse> getActiveTransferForAsset(Long assetId) {
        return transferRepository.findFirstByAssetIdAndStatusInOrderByCreatedAtDesc(assetId, ACTIVE_TRANSFER_STATUSES)
                .map(this::mapToResponse);
    }

    private void publishWebSocketEvent(String eventType, AssetTransfer transfer) {
        Instant occurredAt = switch (transfer.getStatus()) {
            case REQUESTED -> transfer.getRequestedAt();
            case ACCEPTED -> transfer.getAcceptedAt();
            case IN_TRANSIT -> transfer.getStartedAt();
            case COMPLETED -> transfer.getCompletedAt();
            case CANCELLED -> transfer.getCancelledAt();
            case REJECTED -> transfer.getRejectedAt();
        };
        if (occurredAt == null) {
            occurredAt = transfer.getUpdatedAt() != null ? transfer.getUpdatedAt() : nowMicro();
        }
        occurredAt = toMicro(occurredAt);

        TransferWebSocketEvent event = TransferWebSocketEvent.builder()
                .eventType(eventType)
                .transferId(transfer.getId())
                .assetId(transfer.getAsset().getId())
                .assetCode(transfer.getAsset().getAssetCode())
                .sourceDepartmentId(transfer.getFromDepartment() != null ? transfer.getFromDepartment().getId() : null)
                .sourceDepartmentName(transfer.getFromDepartment() != null ? transfer.getFromDepartment().getName() : null)
                .destinationDepartmentId(transfer.getToDepartment() != null ? transfer.getToDepartment().getId() : null)
                .destinationDepartmentName(transfer.getToDepartment() != null ? transfer.getToDepartment().getName() : null)
                .sourceDepartment(transfer.getFromDepartment() != null ? transfer.getFromDepartment().getName() : null)
                .destinationDepartment(transfer.getToDepartment() != null ? transfer.getToDepartment().getName() : null)
                .sourceUserId(transfer.getFromUser() != null ? transfer.getFromUser().getId() : null)
                .sourceUserName(transfer.getFromUser() != null ? transfer.getFromUser().getName() : null)
                .destinationUserId(transfer.getToUser() != null ? transfer.getToUser().getId() : null)
                .destinationUserName(transfer.getToUser() != null ? transfer.getToUser().getName() : null)
                .sourceUser(transfer.getFromUser() != null ? transfer.getFromUser().getName() : null)
                .destinationUser(transfer.getToUser() != null ? transfer.getToUser().getName() : null)
                .requestedByUserId(transfer.getRequestedBy() != null ? transfer.getRequestedBy().getId() : null)
                .requestedByUserName(transfer.getRequestedBy() != null ? transfer.getRequestedBy().getName() : null)
                .requestedBy(transfer.getRequestedBy() != null ? transfer.getRequestedBy().getName() : null)
                .status(transfer.getStatus())
                .occurredAt(occurredAt)
                .build();

        eventPublisher.publishEvent(new TransferPersistedEvent(this, event, transfer));
    }

    private Instant nowMicro() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private Instant toMicro(Instant instant) {
        return instant != null ? instant.truncatedTo(ChronoUnit.MICROS) : null;
    }

    private TransferResponse mapToResponse(AssetTransfer t) {
        return TransferResponse.builder()
                .id(t.getId())
                .assetId(t.getAsset() != null ? t.getAsset().getId() : null)
                .assetCode(t.getAsset() != null ? t.getAsset().getAssetCode() : null)
                .assetName(t.getAsset() != null ? t.getAsset().getAssetName() : null)
                .sourceDepartmentId(t.getFromDepartment() != null ? t.getFromDepartment().getId() : null)
                .sourceDepartmentName(t.getFromDepartment() != null ? t.getFromDepartment().getName() : null)
                .sourceDepartmentCode(t.getFromDepartment() != null ? t.getFromDepartment().getCode() : null)
                .sourceUserId(t.getFromUser() != null ? t.getFromUser().getId() : null)
                .sourceUserName(t.getFromUser() != null ? t.getFromUser().getName() : null)
                .destinationDepartmentId(t.getToDepartment() != null ? t.getToDepartment().getId() : null)
                .destinationDepartmentName(t.getToDepartment() != null ? t.getToDepartment().getName() : null)
                .destinationDepartmentCode(t.getToDepartment() != null ? t.getToDepartment().getCode() : null)
                .destinationUserId(t.getToUser() != null ? t.getToUser().getId() : null)
                .destinationUserName(t.getToUser() != null ? t.getToUser().getName() : null)
                .requestedById(t.getRequestedBy() != null ? t.getRequestedBy().getId() : null)
                .requestedByName(t.getRequestedBy() != null ? t.getRequestedBy().getName() : null)
                .approvedById(t.getApprovedBy() != null ? t.getApprovedBy().getId() : null)
                .approvedByName(t.getApprovedBy() != null ? t.getApprovedBy().getName() : null)
                .status(t.getStatus())
                .requestedAt(toMicro(t.getRequestedAt()))
                .acceptedAt(toMicro(t.getAcceptedAt()))
                .startedAt(toMicro(t.getStartedAt()))
                .completedAt(toMicro(t.getCompletedAt()))
                .cancelledAt(toMicro(t.getCancelledAt()))
                .rejectedAt(toMicro(t.getRejectedAt()))
                .notes(t.getNotes())
                .rejectionReason(t.getRejectionReason())
                .createdAt(toMicro(t.getCreatedAt()))
                .updatedAt(toMicro(t.getUpdatedAt()))
                .build();
    }
}
