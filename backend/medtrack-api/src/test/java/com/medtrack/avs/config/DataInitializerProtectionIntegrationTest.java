package com.medtrack.avs.config;

import com.medtrack.avs.dto.LoginRequest;
import com.medtrack.avs.dto.RegisterRequest;
import com.medtrack.avs.dto.UserResponse;
import com.medtrack.avs.entity.AuthProvider;
import com.medtrack.avs.entity.RoleType;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.*;
import com.medtrack.avs.service.AuthService;
import com.medtrack.avs.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Phase 9.2: DataInitializer Safe Demonstration Seeding and Production Protection Tests")
class DataInitializerProtectionIntegrationTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Transactional
    @TestPropertySource(properties = {"medtrack.seed.demo-data=false"})
    @DisplayName("When medtrack.seed.demo-data=false (Production Safe Baseline)")
    class DemoSeedingDisabledTests {

        @Autowired
        private RoleRepository roleRepository;

        @Autowired
        private HospitalRepository hospitalRepository;

        @Autowired
        private BuildingRepository buildingRepository;

        @Autowired
        private FloorRepository floorRepository;

        @Autowired
        private RoomRepository roomRepository;

        @Autowired
        private DepartmentRepository departmentRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private AssetRepository assetRepository;

        @Autowired
        private AssetAssignmentRepository assetAssignmentRepository;

        @Autowired
        private GeofenceRepository geofenceRepository;

        @Autowired
        private DataInitializer dataInitializer;

        @Autowired
        private AuthService authService;

        @Autowired
        private JwtService jwtService;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("1. RBAC roles (ADMIN, SUPERVISOR, STAFF) must ALWAYS be seeded as mandatory baseline")
        void testRbacRolesAlwaysSeeded() {
            assertTrue(roleRepository.existsByName(RoleType.ADMIN), "ADMIN role must be seeded");
            assertTrue(roleRepository.existsByName(RoleType.SUPERVISOR), "SUPERVISOR role must be seeded");
            assertTrue(roleRepository.existsByName(RoleType.STAFF), "STAFF role must be seeded");
            assertEquals(3, roleRepository.count(), "Exactly 3 system RBAC roles must exist");
        }

        @Test
        @DisplayName("2. No demonstration hospital, buildings, floors, or rooms are inserted")
        void testNoDemoFacilitiesInserted() {
            assertFalse(hospitalRepository.existsByCode("AVS-MAIN"), "Demonstration hospital AVS-MAIN must NOT be seeded");
            assertEquals(0, hospitalRepository.count(), "Hospital registry must remain empty");
            assertEquals(0, buildingRepository.count(), "Building registry must remain empty");
            assertEquals(0, floorRepository.count(), "Floor registry must remain empty");
            assertEquals(0, roomRepository.count(), "Room registry must remain empty");
        }

        @Test
        @DisplayName("3. No demonstration departments, demo assets, assignments, or geofences are inserted")
        void testNoDemoAssetsOrDepartmentsInserted() {
            assertEquals(0, departmentRepository.count(), "Departments must not be seeded when demo data is disabled");
            assertEquals(0, assetRepository.count(), "Assets must not be seeded when demo data is disabled");
            assertEquals(0, assetAssignmentRepository.count(), "Asset assignments must not be seeded when demo data is disabled");
            assertEquals(0, geofenceRepository.count(), "Geofences must not be seeded when demo data is disabled");
        }

        @Test
        @DisplayName("4. No demonstration test accounts (.local) are inserted")
        void testNoDemoUsersInserted() {
            assertTrue(userRepository.findByEmail("admin@medtrack.local").isEmpty(), "admin@medtrack.local must NOT be seeded");
            assertTrue(userRepository.findByEmail("supervisor@medtrack.local").isEmpty(), "supervisor@medtrack.local must NOT be seeded");
            assertTrue(userRepository.findByEmail("staff@medtrack.local").isEmpty(), "staff@medtrack.local must NOT be seeded");
        }

        @Test
        @DisplayName("5. User registration and authentication function cleanly using baseline STAFF role")
        void testUserRegistrationAndAuthWorksWithBaselineRoles() {
            RegisterRequest registerReq = new RegisterRequest();
            registerReq.setName("Clinical Nurse");
            registerReq.setEmail("nurse.unit@hospital.org");
            registerReq.setPassword("SecureClinicPass123!");

            UserResponse userResponse = authService.register(registerReq);
            assertNotNull(userResponse);
            assertEquals("nurse.unit@hospital.org", userResponse.getEmail());
            assertEquals(RoleType.STAFF, userResponse.getRole());

            // Verify login succeeds
            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail("nurse.unit@hospital.org");
            loginReq.setPassword("SecureClinicPass123!");

            var authResponse = authService.login(loginReq);
            assertNotNull(authResponse);
            assertNotNull(authResponse.getAccessToken());
            assertFalse(authResponse.getAccessToken().isBlank());
        }

        @Test
        @DisplayName("6. Missing demo facilities do not cause ordinary APIs to crash or fail unexpectedly")
        void testApisHandleEmptyFacilityRegistryGracefully() throws Exception {
            // Create and save an authorized admin user for testing API access
            var adminRole = roleRepository.findByName(RoleType.ADMIN).orElseThrow();
            User admin = User.builder()
                    .email("prod.admin@hospital.org")
                    .name("Production Administrator")
                    .passwordHash(passwordEncoder.encode("AdminPass123!"))
                    .role(adminRole)
                    .authProvider(AuthProvider.LOCAL)
                    .enabled(true)
                    .build();
            userRepository.save(admin);

            String token = jwtService.generateToken(
                    new org.springframework.security.core.userdetails.User(
                            admin.getEmail(),
                            admin.getPasswordHash(),
                            java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                    )
            );

            // GET /api/hospitals returns HTTP 200 with empty array []
            mockMvc.perform(get("/api/hospitals")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            // GET /api/health returns UP
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));

            // GET /api/floors/1/floor-plan-image without auth returns 401 Unauthorized
            mockMvc.perform(get("/api/floors/1/floor-plan-image"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("7. Re-running DataInitializer does not insert demo records when disabled")
        void testRepeatedRunRemainsEmpty() {
            dataInitializer.run();
            assertEquals(3, roleRepository.count(), "Role count must remain 3");
            assertEquals(0, hospitalRepository.count(), "Hospital count must remain 0");
            assertEquals(0, assetRepository.count(), "Asset count must remain 0");
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Transactional
    @TestPropertySource(properties = {"medtrack.seed.demo-data=true"})
    @DisplayName("When medtrack.seed.demo-data=true (Development / Test Opt-in)")
    class DemoSeedingEnabledTests {

        @Autowired
        private RoleRepository roleRepository;

        @Autowired
        private HospitalRepository hospitalRepository;

        @Autowired
        private BuildingRepository buildingRepository;

        @Autowired
        private FloorRepository floorRepository;

        @Autowired
        private RoomRepository roomRepository;

        @Autowired
        private DepartmentRepository departmentRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private AssetRepository assetRepository;

        @Autowired
        private AssetAssignmentRepository assetAssignmentRepository;

        @Autowired
        private GeofenceRepository geofenceRepository;

        @Autowired
        private DataInitializer dataInitializer;

        @Test
        @DisplayName("1. Expected demonstration hospital and hierarchy are seeded")
        void testDemoFacilitiesSeeded() {
            assertTrue(hospitalRepository.existsByCode("AVS-MAIN"), "AVS-MAIN must be seeded");
            var hospital = hospitalRepository.findByCode("AVS-MAIN").orElseThrow();
            assertEquals("Apex Valley Central Hospital", hospital.getName());

            assertTrue(buildingRepository.findByHospitalIdAndCode(hospital.getId(), "BLD-ER-ACUTE").isPresent());
            assertTrue(buildingRepository.findByHospitalIdAndCode(hospital.getId(), "BLD-CARD-PAV").isPresent());

            assertTrue(floorRepository.count() >= 4, "At least 4 demo floors should exist");
            assertTrue(roomRepository.count() >= 4, "At least 4 demo rooms should exist");
        }

        @Test
        @DisplayName("2. Expected demonstration departments, assets, users, and geofences are seeded")
        void testDemoDataSeeded() {
            assertTrue(departmentRepository.existsByCode("ER"));
            assertTrue(departmentRepository.existsByCode("ICU"));
            assertTrue(departmentRepository.existsByCode("CARD"));

            assertTrue(userRepository.findByEmail("admin@medtrack.local").isPresent());
            assertTrue(userRepository.findByEmail("supervisor@medtrack.local").isPresent());
            assertTrue(userRepository.findByEmail("staff@medtrack.local").isPresent());

            assertTrue(assetRepository.findByAssetCode("ECG-001").isPresent());
            assertTrue(assetRepository.findByAssetCode("PUMP-001").isPresent());

            assertTrue(assetAssignmentRepository.count() >= 3);
            assertTrue(geofenceRepository.count() >= 3);
        }

        @Test
        @DisplayName("3. Repeated DataInitializer execution is idempotent and causes zero duplicate records")
        void testSeedIdempotencyWhenEnabled() {
            long rolesCount = roleRepository.count();
            long hospitalsCount = hospitalRepository.count();
            long buildingsCount = buildingRepository.count();
            long floorsCount = floorRepository.count();
            long roomsCount = roomRepository.count();
            long deptsCount = departmentRepository.count();
            long usersCount = userRepository.count();
            long assetsCount = assetRepository.count();
            long assignsCount = assetAssignmentRepository.count();
            long geofencesCount = geofenceRepository.count();

            // Run dataInitializer again
            dataInitializer.run();

            assertEquals(rolesCount, roleRepository.count(), "Roles must not duplicate");
            assertEquals(hospitalsCount, hospitalRepository.count(), "Hospitals must not duplicate");
            assertEquals(buildingsCount, buildingRepository.count(), "Buildings must not duplicate");
            assertEquals(floorsCount, floorRepository.count(), "Floors must not duplicate");
            assertEquals(roomsCount, roomRepository.count(), "Rooms must not duplicate");
            assertEquals(deptsCount, departmentRepository.count(), "Departments must not duplicate");
            assertEquals(usersCount, userRepository.count(), "Users must not duplicate");
            assertEquals(assetsCount, assetRepository.count(), "Assets must not duplicate");
            assertEquals(assignsCount, assetAssignmentRepository.count(), "Assignments must not duplicate");
            assertEquals(geofencesCount, geofenceRepository.count(), "Geofences must not duplicate");
        }
    }
}
