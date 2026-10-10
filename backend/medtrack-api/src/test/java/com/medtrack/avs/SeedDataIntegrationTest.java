package com.medtrack.avs;

import com.medtrack.avs.config.DataInitializer;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SeedDataIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetAssignmentRepository assetAssignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("Verify roles are seeded properly")
    void testRolesSeeded() {
        assertTrue(roleRepository.existsByName(RoleType.ADMIN));
        assertTrue(roleRepository.existsByName(RoleType.SUPERVISOR));
        assertTrue(roleRepository.existsByName(RoleType.STAFF));
        assertEquals(3, roleRepository.count());
    }

    @Test
    @DisplayName("Verify departments are seeded properly")
    void testDepartmentsSeeded() {
        assertTrue(departmentRepository.existsByCode("ER"));
        assertTrue(departmentRepository.existsByCode("ICU"));
        assertTrue(departmentRepository.existsByCode("CARD"));
        assertTrue(departmentRepository.existsByCode("RAD"));
        assertTrue(departmentRepository.existsByCode("GW"));
        assertEquals(5, departmentRepository.count());
    }

    @Test
    @DisplayName("Verify development users are seeded with BCrypt passwords")
    void testUsersSeeded() {
        Optional<User> adminOpt = userRepository.findByEmail("admin@medtrack.local");
        assertTrue(adminOpt.isPresent());
        User admin = adminOpt.get();
        assertEquals(RoleType.ADMIN, admin.getRole().getName());
        assertEquals(AuthProvider.LOCAL, admin.getAuthProvider());
        assertTrue(admin.isEnabled());
        assertNotNull(admin.getPasswordHash());
        assertTrue(admin.getPasswordHash().startsWith("$2a$") || admin.getPasswordHash().startsWith("$2b$") || admin.getPasswordHash().startsWith("$2y$"),
                "Admin password must be BCrypt hashed");

        Optional<User> supervisorOpt = userRepository.findByEmail("supervisor@medtrack.local");
        assertTrue(supervisorOpt.isPresent());
        assertEquals(RoleType.SUPERVISOR, supervisorOpt.get().getRole().getName());

        Optional<User> staffOpt = userRepository.findByEmail("staff@medtrack.local");
        assertTrue(staffOpt.isPresent());
        assertEquals(RoleType.STAFF, staffOpt.get().getRole().getName());

        assertEquals(3, userRepository.count());
    }

    @Test
    @DisplayName("Verify medical assets and departmental links are seeded")
    void testAssetsSeeded() {
        assertTrue(assetRepository.count() >= 12);

        Optional<Asset> ecgOpt = assetRepository.findByAssetCode("ECG-001");
        assertTrue(ecgOpt.isPresent());
        Asset ecg = ecgOpt.get();
        assertEquals("Portable ECG Machine", ecg.getAssetName());
        assertEquals("SN-ECG-1001", ecg.getSerialNumber());
        assertEquals("MEDTRACK:ASSET:ECG-001", ecg.getQrCode());
        assertNotNull(ecg.getDepartment());
        assertEquals("CARD", ecg.getDepartment().getCode());
        assertNull(ecg.getLatitude(), "Live coordinates should be null during seed phase");
        assertNull(ecg.getLongitude(), "Live coordinates should be null during seed phase");

        // Verify status queries
        List<Asset> assignedAssets = assetRepository.findByStatus(AssetStatus.ASSIGNED);
        assertFalse(assignedAssets.isEmpty());

        List<Asset> availableAssets = assetRepository.findByStatus(AssetStatus.AVAILABLE);
        assertFalse(availableAssets.isEmpty());
    }

    @Test
    @DisplayName("Verify active asset assignments are seeded")
    void testAssignmentsSeeded() {
        List<AssetAssignment> assignments = assetAssignmentRepository.findAll();
        assertTrue(assignments.size() >= 3);

        Asset ecg = assetRepository.findByAssetCode("ECG-001").orElseThrow();
        Optional<AssetAssignment> ecgAssignment = assetAssignmentRepository.findByAssetIdAndActiveTrue(ecg.getId());
        assertTrue(ecgAssignment.isPresent());
        assertEquals("staff@medtrack.local", ecgAssignment.get().getAssignedUser().getEmail());
        assertTrue(ecgAssignment.get().isActive());
    }

    @Test
    @DisplayName("Verify seed process is idempotent and does not create duplicate rows")
    void testSeedIdempotency() {
        long roleCountBefore = roleRepository.count();
        long deptCountBefore = departmentRepository.count();
        long userCountBefore = userRepository.count();
        long assetCountBefore = assetRepository.count();
        long assignCountBefore = assetAssignmentRepository.count();

        // Run dataInitializer again
        dataInitializer.run();

        assertEquals(roleCountBefore, roleRepository.count(), "Role count must remain unchanged after re-run");
        assertEquals(deptCountBefore, departmentRepository.count(), "Department count must remain unchanged after re-run");
        assertEquals(userCountBefore, userRepository.count(), "User count must remain unchanged after re-run");
        assertEquals(assetCountBefore, assetRepository.count(), "Asset count must remain unchanged after re-run");
        assertEquals(assignCountBefore, assetAssignmentRepository.count(), "Assignment count must remain unchanged after re-run");
    }
}
