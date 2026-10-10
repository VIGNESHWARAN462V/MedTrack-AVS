package com.medtrack.avs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.repository.*;
import com.medtrack.avs.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private GpsSessionRepository gpsSessionRepository;

    @Autowired
    private LocationHistoryRepository locationHistoryRepository;

    @Autowired
    private AssetTransferRepository assetTransferRepository;

    @Autowired
    private AssetAssignmentRepository assetAssignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String supervisorToken;
    private String staffToken;

    private Department erDept;
    private Department cardDept;
    private Asset testAsset;
    private User adminUser;
    private User staffUser;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.ADMIN).description("Admin").build()));
        Role supervisorRole = roleRepository.findByName(RoleType.SUPERVISOR).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.SUPERVISOR).description("Supervisor").build()));
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.STAFF).description("Staff").build()));

        erDept = departmentRepository.findByCode("ER").orElseGet(() ->
                departmentRepository.save(Department.builder().name("Emergency Department").code("ER").active(true).build()));

        cardDept = departmentRepository.findByCode("CARD").orElseGet(() ->
                departmentRepository.save(Department.builder().name("Cardiology Department").code("CARD").active(true).build()));

        adminUser = userRepository.findByEmail("admin_analytics@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Admin Analytics")
                        .email("admin_analytics@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Admin@12345"))
                        .role(adminRole)
                        .enabled(true)
                        .build()));

        User supervisorUser = userRepository.findByEmail("super_analytics@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Super Analytics")
                        .email("super_analytics@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Supervisor@12345"))
                        .role(supervisorRole)
                        .enabled(true)
                        .build()));

        staffUser = userRepository.findByEmail("staff_analytics@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Staff Analytics")
                        .email("staff_analytics@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Staff@12345"))
                        .role(staffRole)
                        .enabled(true)
                        .build()));

        testAsset = assetRepository.findByAssetCode("ECG-001").orElseGet(() ->
                assetRepository.save(Asset.builder()
                        .assetCode("ECG-001")
                        .assetName("Electrocardiogram 1")
                        .category("Cardiology")
                        .department(erDept)
                        .status(AssetStatus.AVAILABLE)
                        .build()));

        if (assetAssignmentRepository.findByAssignedUserId(staffUser.getId()).isEmpty()) {
            assetAssignmentRepository.save(AssetAssignment.builder()
                    .asset(testAsset)
                    .assignedUser(staffUser)
                    .department(erDept)
                    .assignedAt(java.time.LocalDateTime.now().minusDays(2))
                    .active(true)
                    .build());
        }

        adminToken = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        supervisorToken = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                supervisorUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))));
        staffToken = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                staffUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
    }

    @Test
    @DisplayName("GET /api/analytics/overview: 200 OK for ADMIN with default range")
    void testOverviewDefaultRangeAdmin() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amcStatus").exists())
                .andExpect(jsonPath("$.hfiStatus").exists())
                .andExpect(jsonPath("$.gubStatus").exists())
                .andExpect(jsonPath("$.periodStart").exists())
                .andExpect(jsonPath("$.periodEnd").exists());
    }

    @Test
    @DisplayName("GET /api/analytics/amc: 200 OK with deterministic data")
    void testAmcEndpoint() throws Exception {
        Instant now = Instant.now();
        Instant from = now.minus(1, ChronoUnit.HOURS);

        // Seed a GPS session and telemetry
        GpsSession session = gpsSessionRepository.save(GpsSession.builder()
                .asset(testAsset)
                .startedBy(adminUser)
                .active(false)
                .startedAt(from)
                .endedAt(from.plus(50, ChronoUnit.SECONDS)) // 10 intervals
                .build());

        locationHistoryRepository.save(LocationHistory.builder()
                .asset(testAsset)
                .latitude(11.497100)
                .longitude(77.278900)
                .accuracy(4.0)
                .recordedAt(from.plusSeconds(5))
                .build());

        mockMvc.perform(get("/api/analytics/amc")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", from.toString())
                        .param("to", now.toString())
                        .param("assetId", testAsset.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricName").value("Asset Movement Continuity (AMC)"))
                .andExpect(jsonPath("$.expectedGpsUpdates", greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$.successfulGpsUpdates", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.continuityPercentage").isNumber());
    }

    @Test
    @DisplayName("GET /api/analytics/hfi: 200 OK structure and weights")
    void testHfiEndpoint() throws Exception {
        mockMvc.perform(get("/api/analytics/hfi")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricName").value("Handoff Friction Index (HFI)"))
                .andExpect(jsonPath("$.durationWeight").value(0.40))
                .andExpect(jsonPath("$.delayWeight").value(0.35))
                .andExpect(jsonPath("$.gapWeight").value(0.25))
                .andExpect(jsonPath("$.departmentBreakdown").isArray());
    }

    @Test
    @DisplayName("GET /api/analytics/gub: 200 OK returns departmental balances")
    void testGubEndpoint() throws Exception {
        mockMvc.perform(get("/api/analytics/gub")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricName").value("Geo-Utilization Balance (GUB)"))
                .andExpect(jsonPath("$.departments").isArray())
                .andExpect(jsonPath("$.departments", not(empty())))
                .andExpect(jsonPath("$.supplyDefinition").isNotEmpty())
                .andExpect(jsonPath("$.demandDefinition").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/analytics/overview: 400 Bad Request when from is after to")
    void testInvalidDateRange() throws Exception {
        Instant now = Instant.now();
        Instant past = now.minus(2, ChronoUnit.DAYS);

        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", now.toString())
                        .param("to", past.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/analytics/overview: 401 Unauthorized when missing token")
    void testUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/analytics/overview: 403 Forbidden when STAFF accesses other department")
    void testStaffIdorForbidden() throws Exception {
        // staff belongs to ER (erDept.getId()), attempts to query CARD (cardDept.getId())
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + staffToken)
                        .param("departmentId", cardDept.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/analytics/overview: 200 OK for STAFF accessing own department")
    void testStaffOwnDepartmentSuccess() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + staffToken)
                        .param("departmentId", erDept.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amcStatus").exists());
    }

    @Test
    @DisplayName("GET /api/analytics/amc: 200 OK with NO_DATA status for period in the far past")
    void testNoDataPeriodGraceful() throws Exception {
        Instant pastFrom = Instant.parse("2020-01-01T00:00:00Z");
        Instant pastTo = Instant.parse("2020-01-02T00:00:00Z");

        mockMvc.perform(get("/api/analytics/amc")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", pastFrom.toString())
                        .param("to", pastTo.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_DATA"))
                .andExpect(jsonPath("$.expectedGpsUpdates").value(0))
                .andExpect(jsonPath("$.successfulGpsUpdates").value(0))
                .andExpect(jsonPath("$.continuityPercentage").doesNotExist());
    }
}
