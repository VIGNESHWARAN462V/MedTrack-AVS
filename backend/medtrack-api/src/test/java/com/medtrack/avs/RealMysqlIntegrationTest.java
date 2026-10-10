package com.medtrack.avs;

import com.medtrack.avs.config.DataInitializer;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/medtrack_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC",
        "spring.datasource.driverClassName=com.mysql.cj.jdbc.Driver",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
class RealMysqlIntegrationTest {

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
    @Transactional
    @DisplayName("Verify real MySQL connectivity and seeded reference data queries")
    void testRealMysqlDataQueries() {
        // 1. Role by name
        Optional<Role> adminRole = roleRepository.findByName(RoleType.ADMIN);
        assertTrue(adminRole.isPresent(), "ADMIN role should exist in MySQL");

        // 2. Department by code
        Optional<Department> erDept = departmentRepository.findByCode("ER");
        assertTrue(erDept.isPresent(), "ER department should exist in MySQL");
        assertEquals("Emergency", erDept.get().getName());

        // 3. User by email
        Optional<User> staffUser = userRepository.findByEmail("staff@medtrack.local");
        assertTrue(staffUser.isPresent(), "staff@medtrack.local should exist in MySQL");
        assertNotNull(staffUser.get().getPasswordHash());
        assertTrue(staffUser.get().getPasswordHash().startsWith("$2a$") || staffUser.get().getPasswordHash().startsWith("$2b$") || staffUser.get().getPasswordHash().startsWith("$2y$"),
                "staff user password must be BCrypt hashed");

        // 4. Asset by assetCode
        Optional<Asset> pumpAsset = assetRepository.findByAssetCode("PUMP-001");
        assertTrue(pumpAsset.isPresent(), "PUMP-001 should exist in MySQL");
        assertEquals("Infusion Pump", pumpAsset.get().getAssetName());

        // 5. Assets by department
        List<Asset> icuAssets = assetRepository.findByDepartmentId(
                departmentRepository.findByCode("ICU").orElseThrow().getId()
        );
        assertFalse(icuAssets.isEmpty(), "ICU department should have assigned assets");

        // 6. Assets by status
        List<Asset> availableAssets = assetRepository.findByStatus(AssetStatus.AVAILABLE);
        assertFalse(availableAssets.isEmpty(), "Available assets should exist in MySQL");

        // 7. Active assignment
        Asset ecg = assetRepository.findByAssetCode("ECG-001").orElseThrow();
        Optional<AssetAssignment> assignment = assetAssignmentRepository.findByAssetIdAndActiveTrue(ecg.getId());
        assertTrue(assignment.isPresent(), "Active assignment for ECG-001 should exist in MySQL");
        assertEquals("staff@medtrack.local", assignment.get().getAssignedUser().getEmail());
    }

    @Test
    @DisplayName("Verify seed idempotency against real MySQL on port 3306")
    void testRealMysqlIdempotency() {
        long rolesCount = roleRepository.count();
        long deptsCount = departmentRepository.count();
        long usersCount = userRepository.count();
        long assetsCount = assetRepository.count();
        long assignsCount = assetAssignmentRepository.count();

        // Trigger seed execution again
        dataInitializer.run();

        assertEquals(rolesCount, roleRepository.count(), "MySQL roles count must not change");
        assertEquals(deptsCount, departmentRepository.count(), "MySQL departments count must not change");
        assertEquals(usersCount, userRepository.count(), "MySQL users count must not change");
        assertEquals(assetsCount, assetRepository.count(), "MySQL assets count must not change");
        assertEquals(assignsCount, assetAssignmentRepository.count(), "MySQL assignments count must not change");
    }
}
