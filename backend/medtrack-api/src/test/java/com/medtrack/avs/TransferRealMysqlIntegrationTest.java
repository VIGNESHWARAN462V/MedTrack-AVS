package com.medtrack.avs;

import com.medtrack.avs.dto.TransferCreateRequest;
import com.medtrack.avs.dto.TransferResponse;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ConflictException;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.repository.*;
import com.medtrack.avs.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class TransferRealMysqlIntegrationTest {

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

    @Autowired
    private AuditLogRepository auditLogRepository;

    private User adminUser;
    private User supervisorUser;
    private User staffUser;
    private User otherStaffUser;
    private Department cardDept;
    private Department erDept;
    private Asset testAsset;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseThrow();
        Role superRole = roleRepository.findByName(RoleType.SUPERVISOR).orElseThrow();
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseThrow();

        adminUser = userRepository.save(User.builder()
                .name("Admin Integration")
                .email("admin-trans-int@medtrack.local")
                .passwordHash("hashed")
                .role(adminRole)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build());

        supervisorUser = userRepository.save(User.builder()
                .name("Supervisor Integration")
                .email("super-trans-int@medtrack.local")
                .passwordHash("hashed")
                .role(superRole)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build());

        staffUser = userRepository.save(User.builder()
                .name("Staff Integration")
                .email("staff-trans-int@medtrack.local")
                .passwordHash("hashed")
                .role(staffRole)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build());

        otherStaffUser = userRepository.save(User.builder()
                .name("Other Staff Integration")
                .email("other-staff-trans-int@medtrack.local")
                .passwordHash("hashed")
                .role(staffRole)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build());

        cardDept = departmentRepository.findByCode("CARD").orElseThrow();
        erDept = departmentRepository.findByCode("ER").orElseThrow();

        testAsset = assetRepository.save(Asset.builder()
                .assetCode("TRANS-INT-001")
                .assetName("Integration Transfer Asset")
                .category("Infusion")
                .serialNumber("SN-TRANS-INT-001")
                .status(AssetStatus.ASSIGNED)
                .department(cardDept)
                .batteryLevel(95)
                .latitude(11.496155)
                .longitude(77.278545)
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
    @DisplayName("Validation Rules: Non-existent asset or department, identical source/destination, maintenance")
    void testValidationRules() {
        // 1. Non-existent asset
        TransferCreateRequest invalidAssetReq = TransferCreateRequest.builder()
                .assetId(999999L)
                .destinationDepartmentId(erDept.getId())
                .build();
        assertThrows(ResourceNotFoundException.class, () -> transferService.requestTransfer(invalidAssetReq, adminUser));

        // 2. Non-existent department
        TransferCreateRequest invalidDeptReq = TransferCreateRequest.builder()
                .assetId(testAsset.getId())
                .destinationDepartmentId(999999L)
                .build();
        assertThrows(ResourceNotFoundException.class, () -> transferService.requestTransfer(invalidDeptReq, adminUser));

        // 3. Identical source & destination without target user change
        TransferCreateRequest sameDeptReq = TransferCreateRequest.builder()
                .assetId(testAsset.getId())
                .destinationDepartmentId(cardDept.getId())
                .destinationUserId(staffUser.getId())
                .build();
        assertThrows(BadRequestException.class, () -> transferService.requestTransfer(sameDeptReq, adminUser));

        // 4. Asset under maintenance cannot be transferred
        Asset maintAsset = assetRepository.save(Asset.builder()
                .assetCode("MAINT-ASSET-01")
                .assetName("Broken Defib")
                .category("Defibrillator")
                .serialNumber("SN-MAINT-01")
                .status(AssetStatus.MAINTENANCE)
                .department(cardDept)
                .build());

        TransferCreateRequest maintReq = TransferCreateRequest.builder()
                .assetId(maintAsset.getId())
                .destinationDepartmentId(erDept.getId())
                .build();
        assertThrows(ConflictException.class, () -> transferService.requestTransfer(maintReq, adminUser));
    }

    @Test
    @DisplayName("Conflict Rule: No conflicting active transfers for the same asset")
    void testConflictingActiveTransferPrevention() {
        TransferCreateRequest req = TransferCreateRequest.builder()
                .assetId(testAsset.getId())
                .destinationDepartmentId(erDept.getId())
                .build();

        transferService.requestTransfer(req, adminUser);

        // Second transfer request while first is active -> ConflictException
        assertThrows(ConflictException.class, () -> transferService.requestTransfer(req, adminUser));
    }

    @Test
    @DisplayName("Assignment Integrity: Exactly one active assignment after completion, old assignment closed")
    void testAssignmentIntegrityOnCompletion() {
        TransferCreateRequest req = TransferCreateRequest.builder()
                .assetId(testAsset.getId())
                .destinationDepartmentId(erDept.getId())
                .destinationUserId(otherStaffUser.getId())
                .notes("Reassign to ER nurse")
                .build();

        TransferResponse created = transferService.requestTransfer(req, adminUser);
        transferService.acceptTransfer(created.getId(), supervisorUser);
        transferService.startTransfer(created.getId(), adminUser);
        TransferResponse completed = transferService.completeTransfer(created.getId(), supervisorUser);

        assertEquals(TransferStatus.COMPLETED, completed.getStatus());

        // Verify assignment invariant: Exactly 1 active assignment
        List<AssetAssignment> allAssignments = assignmentRepository.findByAssetId(testAsset.getId());
        assertEquals(2, allAssignments.size(), "Should have exactly 2 assignments in total (1 inactive, 1 active)");

        List<AssetAssignment> activeAssignments = allAssignments.stream().filter(AssetAssignment::isActive).toList();
        assertEquals(1, activeAssignments.size(), "Must have exactly ONE active assignment");

        AssetAssignment newActive = activeAssignments.get(0);
        assertEquals(otherStaffUser.getId(), newActive.getAssignedUser().getId());
        assertEquals(erDept.getId(), newActive.getDepartment().getId());

        List<AssetAssignment> inactiveAssignments = allAssignments.stream().filter(a -> !a.isActive()).toList();
        assertEquals(1, inactiveAssignments.size());
        AssetAssignment oldInactive = inactiveAssignments.get(0);
        assertEquals(staffUser.getId(), oldInactive.getAssignedUser().getId());
        assertNotNull(oldInactive.getReleasedAt(), "Old assignment must have releasedAt timestamp");

        // Verify Asset entity updated
        Asset updatedAsset = assetRepository.findById(testAsset.getId()).orElseThrow();
        assertEquals(erDept.getId(), updatedAsset.getDepartment().getId());
        assertEquals(AssetStatus.ASSIGNED, updatedAsset.getStatus());
        // Verify GPS location was preserved!
        assertEquals(11.496155, updatedAsset.getLatitude());
        assertEquals(77.278545, updatedAsset.getLongitude());
    }

    @Test
    @DisplayName("Audit Log: Major transfer actions are recorded in AuditLog")
    void testAuditLogRecorded() {
        TransferCreateRequest req = TransferCreateRequest.builder()
                .assetId(testAsset.getId())
                .destinationDepartmentId(erDept.getId())
                .build();

        long auditCountBefore = auditLogRepository.count();

        TransferResponse created = transferService.requestTransfer(req, adminUser);
        transferService.acceptTransfer(created.getId(), supervisorUser);
        transferService.startTransfer(created.getId(), adminUser);
        transferService.completeTransfer(created.getId(), supervisorUser);

        long auditCountAfter = auditLogRepository.count();
        assertTrue(auditCountAfter >= auditCountBefore + 4, "Should record at least 4 audit log entries for full transfer lifecycle");
    }

    @Test
    @DisplayName("Security & Authorization: Staff cannot transfer unassigned assets or accept transfers")
    void testStaffAuthorizationLimits() {
        // Staff cannot request transfer for asset not assigned to them
        Asset unassignedAsset = assetRepository.save(Asset.builder()
                .assetCode("UNASSIGNED-01")
                .assetName("Unassigned Pump")
                .category("Pump")
                .serialNumber("SN-UNASSIGNED-01")
                .status(AssetStatus.AVAILABLE)
                .department(cardDept)
                .build());

        TransferCreateRequest req = TransferCreateRequest.builder()
                .assetId(unassignedAsset.getId())
                .destinationDepartmentId(erDept.getId())
                .build();

        assertThrows(AccessDeniedException.class, () -> transferService.requestTransfer(req, staffUser));

        // Create transfer by admin
        TransferResponse transfer = transferService.requestTransfer(req, adminUser);

        // Staff cannot accept transfer
        assertThrows(AccessDeniedException.class, () -> transferService.acceptTransfer(transfer.getId(), staffUser));
    }
}
