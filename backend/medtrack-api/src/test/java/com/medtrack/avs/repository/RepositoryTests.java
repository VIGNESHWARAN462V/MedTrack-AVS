package com.medtrack.avs.repository;

import com.medtrack.avs.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RepositoryTests {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Test
    @DisplayName("Should persist and find Role and User")
    void testUserAndRolePersistence() {
        Role adminRole = roleRepository.findByName(RoleType.ADMIN)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(RoleType.ADMIN)
                        .description("System Administrator")
                        .build()));

        User user = User.builder()
                .name("Dr. Jonathan Davis")
                .email("jdavis@medtrack.org")
                .passwordHash("hashed_pass_xyz")
                .role(adminRole)
                .authProvider(AuthProvider.LOCAL)
                .enabled(true)
                .build();
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("jdavis@medtrack.org");
        assertTrue(found.isPresent());
        assertEquals("Dr. Jonathan Davis", found.get().getName());
        assertEquals(RoleType.ADMIN, found.get().getRole().getName());
    }

    @Test
    @DisplayName("Should persist and find Department and Asset")
    void testDepartmentAndAssetPersistence() {
        Department cardio = Department.builder()
                .name("Cardiology Test")
                .code("CARD-TEST-01")
                .description("Cardiovascular and telemetry ward")
                .active(true)
                .build();
        departmentRepository.save(cardio);

        Asset asset = Asset.builder()
                .assetCode("ECG-TEST-023")
                .assetName("CardioScan 500")
                .category("Monitoring Equipment")
                .serialNumber("SN-CARD-99281-TEST")
                .status(AssetStatus.IN_USE)
                .department(cardio)
                .batteryLevel(84)
                .latitude(40.7128)
                .longitude(-74.0060)
                .lastSeen(java.time.Instant.now())
                .build();
        assetRepository.save(asset);

        Optional<Asset> found = assetRepository.findByAssetCode("ECG-TEST-023");
        assertTrue(found.isPresent());
        assertEquals("CardioScan 500", found.get().getAssetName());
        assertEquals("CARD-TEST-01", found.get().getDepartment().getCode());
    }

    @Test
    @DisplayName("Should persist Alert and query unresolved alerts")
    void testAlertPersistence() {
        Alert alert = Alert.builder()
                .type(AlertType.LOW_BATTERY)
                .message("Battery level critical (4%) in ER Bay 3")
                .severity(AlertSeverity.CRITICAL)
                .resolved(false)
                .build();
        alertRepository.save(alert);

        long unresolvedCount = alertRepository.countByResolvedFalse();
        assertTrue(unresolvedCount >= 1);
    }
}
