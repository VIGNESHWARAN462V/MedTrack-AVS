package com.medtrack.avs;

import com.medtrack.avs.dto.TransferCreateRequest;
import com.medtrack.avs.dto.TransferResponse;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.exception.ConflictException;
import com.medtrack.avs.repository.*;
import com.medtrack.avs.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class TransferTransitionTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AssetTransferRepository transferRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AssetAssignmentRepository assignmentRepository;

    private User adminUser;
    private User supervisorUser;
    private User staffUser;
    private Department cardDept;
    private Department erDept;
    private Asset testAsset;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseThrow();
        Role superRole = roleRepository.findByName(RoleType.SUPERVISOR).orElseThrow();
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseThrow();

        adminUser = userRepository.save(User.builder()
                .name("Admin Tester")
                .email("admin-trans-test@medtrack.local")
                .passwordHash("hashed")
                .role(adminRole)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build());

        supervisorUser = userRepository.save(User.builder()
                .name("Supervisor Tester")
                .email("super-trans-test@medtrack.local")
                .passwordHash("hashed")
                .role(superRole)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build());

        staffUser = userRepository.save(User.builder()
                .name("Staff Tester")
                .email("staff-trans-test@medtrack.local")
                .passwordHash("hashed")
                .role(staffRole)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build());

        cardDept = departmentRepository.findByCode("CARD").orElseGet(() ->
                departmentRepository.save(Department.builder().name("Cardiology").code("CARD").build()));

        erDept = departmentRepository.findByCode("ER").orElseGet(() ->
                departmentRepository.save(Department.builder().name("Emergency").code("ER").build()));

        testAsset = assetRepository.save(Asset.builder()
                .assetCode("TRANS-TEST-001")
                .assetName("Transition Test Monitor")
                .category("Telemetry")
                .serialNumber("SN-TRANS-001")
                .status(AssetStatus.ASSIGNED)
                .department(cardDept)
                .batteryLevel(90)
                .build());

        // Assign to staffUser
        assignmentRepository.save(AssetAssignment.builder()
                .asset(testAsset)
                .assignedUser(staffUser)
                .department(cardDept)
                .assignedAt(LocalDateTime.now(ZoneOffset.UTC))
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Legal Happy Path: REQUESTED -> ACCEPTED -> IN_TRANSIT -> COMPLETED")
    void testHappyPathLifecycle() {
        // 1. REQUESTED
        TransferCreateRequest req = TransferCreateRequest.builder()
                .assetId(testAsset.getId())
                .destinationDepartmentId(erDept.getId())
                .notes("Move monitor to ER")
                .build();
        TransferResponse t1 = transferService.requestTransfer(req, adminUser);
        assertEquals(TransferStatus.REQUESTED, t1.getStatus());
        assertNotNull(t1.getRequestedAt());

        // 2. ACCEPTED
        TransferResponse t2 = transferService.acceptTransfer(t1.getId(), supervisorUser);
        assertEquals(TransferStatus.ACCEPTED, t2.getStatus());
        assertNotNull(t2.getAcceptedAt());

        // 3. IN_TRANSIT
        TransferResponse t3 = transferService.startTransfer(t1.getId(), adminUser);
        assertEquals(TransferStatus.IN_TRANSIT, t3.getStatus());
        assertNotNull(t3.getStartedAt());
        assertEquals(AssetStatus.IN_TRANSIT, assetRepository.findById(testAsset.getId()).orElseThrow().getStatus());

        // 4. COMPLETED
        TransferResponse t4 = transferService.completeTransfer(t1.getId(), supervisorUser);
        assertEquals(TransferStatus.COMPLETED, t4.getStatus());
        assertNotNull(t4.getCompletedAt());

        Asset updatedAsset = assetRepository.findById(testAsset.getId()).orElseThrow();
        assertEquals(erDept.getId(), updatedAsset.getDepartment().getId());
    }

    @Test
    @DisplayName("Alternative Path 1: REQUESTED -> REJECTED")
    void testRequestedToRejected() {
        TransferCreateRequest req = TransferCreateRequest.builder()
                .assetId(testAsset.getId())
                .destinationDepartmentId(erDept.getId())
                .notes("Move monitor to ER")
                .build();
        TransferResponse t1 = transferService.requestTransfer(req, adminUser);

        TransferResponse t2 = transferService.rejectTransfer(t1.getId(),
                new com.medtrack.avs.dto.TransferRejectRequest("No space available in ER"), supervisorUser);

        assertEquals(TransferStatus.REJECTED, t2.getStatus());
        assertNotNull(t2.getRejectedAt());
        assertEquals("No space available in ER", t2.getRejectionReason());
    }

    @Test
    @DisplayName("Alternative Path 2: REQUESTED -> CANCELLED")
    void testRequestedToCancelled() {
        TransferCreateRequest req = TransferCreateRequest.builder()
                .assetId(testAsset.getId())
                .destinationDepartmentId(erDept.getId())
                .build();
        TransferResponse t1 = transferService.requestTransfer(req, adminUser);

        TransferResponse t2 = transferService.cancelTransfer(t1.getId(),
                new com.medtrack.avs.dto.TransferCancelRequest("No longer needed"), adminUser);

        assertEquals(TransferStatus.CANCELLED, t2.getStatus());
        assertNotNull(t2.getCancelledAt());
    }

    @Test
    @DisplayName("Alternative Path 3: ACCEPTED -> CANCELLED")
    void testAcceptedToCancelled() {
        TransferCreateRequest req = TransferCreateRequest.builder()
                .assetId(testAsset.getId())
                .destinationDepartmentId(erDept.getId())
                .build();
        TransferResponse t1 = transferService.requestTransfer(req, adminUser);
        transferService.acceptTransfer(t1.getId(), supervisorUser);

        TransferResponse t2 = transferService.cancelTransfer(t1.getId(),
                new com.medtrack.avs.dto.TransferCancelRequest("Cancelled by admin"), adminUser);

        assertEquals(TransferStatus.CANCELLED, t2.getStatus());
        assertNotNull(t2.getCancelledAt());
    }

    @Test
    @DisplayName("Alternative Path 4: IN_TRANSIT -> CANCELLED (Restores asset status)")
    void testInTransitToCancelled() {
        TransferCreateRequest req = TransferCreateRequest.builder()
                .assetId(testAsset.getId())
                .destinationDepartmentId(erDept.getId())
                .build();
        TransferResponse t1 = transferService.requestTransfer(req, adminUser);
        transferService.acceptTransfer(t1.getId(), supervisorUser);
        transferService.startTransfer(t1.getId(), adminUser);
        assertEquals(AssetStatus.IN_TRANSIT, assetRepository.findById(testAsset.getId()).orElseThrow().getStatus());

        TransferResponse t2 = transferService.cancelTransfer(t1.getId(),
                new com.medtrack.avs.dto.TransferCancelRequest("Transport aborted"), adminUser);

        assertEquals(TransferStatus.CANCELLED, t2.getStatus());
        Asset reverted = assetRepository.findById(testAsset.getId()).orElseThrow();
        assertEquals(AssetStatus.ASSIGNED, reverted.getStatus(), "Asset status should be restored after cancelled transit");
    }

    @Test
    @DisplayName("Illegal Transitions: COMPLETED -> IN_TRANSIT, REJECTED -> ACCEPTED, CANCELLED -> COMPLETED")
    void testIllegalTransitionsRejected() {
        // Complete a transfer
        TransferCreateRequest req = TransferCreateRequest.builder()
                .assetId(testAsset.getId())
                .destinationDepartmentId(erDept.getId())
                .build();
        TransferResponse t1 = transferService.requestTransfer(req, adminUser);
        transferService.acceptTransfer(t1.getId(), supervisorUser);
        transferService.startTransfer(t1.getId(), adminUser);
        transferService.completeTransfer(t1.getId(), supervisorUser);

        // Illegal: COMPLETED -> IN_TRANSIT
        assertThrows(ConflictException.class, () -> transferService.startTransfer(t1.getId(), adminUser));
        // Illegal: COMPLETED -> CANCELLED
        assertThrows(ConflictException.class, () -> transferService.cancelTransfer(t1.getId(), null, adminUser));

        // Create second asset to test REJECTED -> ACCEPTED
        Asset testAsset2 = assetRepository.save(Asset.builder()
                .assetCode("TRANS-TEST-002")
                .assetName("Second Monitor")
                .category("Telemetry")
                .serialNumber("SN-TRANS-002")
                .status(AssetStatus.AVAILABLE)
                .department(cardDept)
                .build());

        TransferCreateRequest req2 = TransferCreateRequest.builder()
                .assetId(testAsset2.getId())
                .destinationDepartmentId(erDept.getId())
                .build();
        TransferResponse t2 = transferService.requestTransfer(req2, adminUser);
        transferService.rejectTransfer(t2.getId(), new com.medtrack.avs.dto.TransferRejectRequest("Rejected"), supervisorUser);

        // Illegal: REJECTED -> ACCEPTED
        assertThrows(ConflictException.class, () -> transferService.acceptTransfer(t2.getId(), supervisorUser));

        // Create third asset to test REQUESTED -> IN_TRANSIT (skipping ACCEPTED)
        Asset testAsset3 = assetRepository.save(Asset.builder()
                .assetCode("TRANS-TEST-003")
                .assetName("Third Monitor")
                .category("Telemetry")
                .serialNumber("SN-TRANS-003")
                .status(AssetStatus.AVAILABLE)
                .department(cardDept)
                .build());

        TransferResponse t3 = transferService.requestTransfer(
                TransferCreateRequest.builder().assetId(testAsset3.getId()).destinationDepartmentId(erDept.getId()).build(),
                adminUser);

        // Illegal: REQUESTED -> IN_TRANSIT (cannot start transit before acceptance)
        assertThrows(ConflictException.class, () -> transferService.startTransfer(t3.getId(), adminUser));
    }
}
