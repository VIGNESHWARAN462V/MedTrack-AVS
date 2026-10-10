package com.medtrack.avs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.dto.GpsSessionResponse;
import com.medtrack.avs.dto.GpsSessionStartRequest;
import com.medtrack.avs.dto.LocationTelemetryRequest;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.repository.*;
import com.medtrack.avs.security.JwtService;
import com.medtrack.avs.security.RateLimiterService;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private GpsSessionRepository gpsSessionRepository;

    @Autowired
    private LocationHistoryRepository locationHistoryRepository;

    @Autowired
    private AssetAssignmentRepository assetAssignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RateLimiterService rateLimiterService;

    private String adminToken;
    private String supervisorToken;
    private String staffToken;

    private User adminUser;
    private User staffUser;
    private Asset testAsset;
    private Department testDept;

    @BeforeEach
    void setUp() {
        if (rateLimiterService != null) {
            rateLimiterService.reset();
        }
        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.ADMIN).description("Admin").build()));
        Role supervisorRole = roleRepository.findByName(RoleType.SUPERVISOR).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.SUPERVISOR).description("Supervisor").build()));
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.STAFF).description("Staff").build()));

        adminUser = userRepository.findByEmail("admin@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Admin System")
                        .email("admin@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Admin@12345"))
                        .role(adminRole)
                        .enabled(true)
                        .build()));

        User supervisorUser = userRepository.findByEmail("supervisor@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Supervisor One")
                        .email("supervisor@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Supervisor@12345"))
                        .role(supervisorRole)
                        .enabled(true)
                        .build()));

        staffUser = userRepository.findByEmail("nurse@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Staff Nurse")
                        .email("nurse@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Staff@12345"))
                        .role(staffRole)
                        .enabled(true)
                        .build()));

        adminToken = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        supervisorToken = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                supervisorUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))));
        staffToken = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                staffUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));

        testDept = departmentRepository.findAll().stream().findFirst().orElseGet(() ->
                departmentRepository.save(Department.builder()
                        .name("Telemetry Test Dept")
                        .code("TEL-TEST-" + System.currentTimeMillis())
                        .description("Telemetry Department")
                        .active(true)
                        .build()));

        testAsset = assetRepository.save(Asset.builder()
                .assetCode("GPS-TEST-" + System.currentTimeMillis())
                .assetName("GPS Telemetry Monitor")
                .category("Telemetry")
                .serialNumber("SN-GPS-" + System.currentTimeMillis())
                .status(AssetStatus.AVAILABLE)
                .department(testDept)
                .batteryLevel(95)
                .qrCode("MEDTRACK:ASSET:GPS-" + System.currentTimeMillis())
                .build());
    }

    private GpsSession createActiveSession(Asset asset) {
        return gpsSessionRepository.save(GpsSession.builder()
                .asset(asset)
                .startedBy(adminUser)
                .startedAt(Instant.now())
                .active(true)
                .build());
    }

    @Test
    @DisplayName("1. Valid GPS ingestion: Returns HTTP 200 with saved telemetry details")
    void testValidGpsIngestion() throws Exception {
        createActiveSession(testAsset);

        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.341234)
                .longitude(77.717865)
                .accuracy(7.2)
                .speed(0.8)
                .heading(90.0)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId", is(testAsset.getId().intValue())))
                .andExpect(jsonPath("$.assetCode", is(testAsset.getAssetCode())))
                .andExpect(jsonPath("$.latitude", closeTo(11.341234, 0.00001)))
                .andExpect(jsonPath("$.longitude", closeTo(77.717865, 0.00001)))
                .andExpect(jsonPath("$.accuracy", closeTo(7.2, 0.01)))
                .andExpect(jsonPath("$.status", is("MOVING")));
    }

    @Test
    @DisplayName("2. Invalid latitude (> 90 or < -90): Returns HTTP 400 Bad Request")
    void testInvalidLatitude() throws Exception {
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(91.5)
                .longitude(77.717865)
                .accuracy(5.0)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("3. Invalid longitude (> 180 or < -180): Returns HTTP 400 Bad Request")
    void testInvalidLongitude() throws Exception {
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.341234)
                .longitude(181.5)
                .accuracy(5.0)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("4. Invalid accuracy (< 0): Returns HTTP 400 Bad Request")
    void testInvalidAccuracy() throws Exception {
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.341234)
                .longitude(77.717865)
                .accuracy(-2.5)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("5. Missing asset: Returns HTTP 404 Not Found")
    void testMissingAsset() throws Exception {
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(999999L)
                .latitude(11.341234)
                .longitude(77.717865)
                .accuracy(5.0)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("6. Unauthorized request (no token): Returns HTTP 401 or 403")
    void testUnauthorizedRequest() throws Exception {
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.341234)
                .longitude(77.717865)
                .accuracy(5.0)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. Forbidden tracking attempt: Staff without active assignment returns 403 Forbidden")
    void testForbiddenTrackingAttempt() throws Exception {
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.341234)
                .longitude(77.717865)
                .accuracy(5.0)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. LocationHistory persistence: Verifies append-only record creation in DB")
    void testLocationHistoryPersistence() throws Exception {
        createActiveSession(testAsset);
        long initialCount = locationHistoryRepository.count();

        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.341234)
                .longitude(77.717865)
                .accuracy(6.5)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertEquals(initialCount + 1, locationHistoryRepository.count());
        List<LocationHistory> histories = locationHistoryRepository.findByAssetId(testAsset.getId());
        assertFalse(histories.isEmpty());
        LocationHistory latest = histories.get(histories.size() - 1);
        assertEquals(11.341234, latest.getLatitude(), 0.00001);
        assertEquals("BROWSER_GPS", latest.getSource());
    }

    @Test
    @DisplayName("8b. Orphan telemetry rejection: Returns HTTP 409 Conflict when no active session exists")
    void testOrphanTelemetryRejection() throws Exception {
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.341234)
                .longitude(77.717865)
                .accuracy(7.2)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("9. Asset current-location update: Asset entity is updated with telemetry")
    void testAssetCurrentLocationUpdate() throws Exception {
        createActiveSession(testAsset);

        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(12.971598)
                .longitude(77.594562)
                .accuracy(4.2)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Asset updated = assetRepository.findById(testAsset.getId()).orElseThrow();
        assertEquals(12.971598, updated.getLatitude(), 0.00001);
        assertEquals(77.594562, updated.getLongitude(), 0.00001);
        assertEquals(4.2, updated.getGpsAccuracy(), 0.01);
        assertEquals(AssetStatus.MOVING, updated.getStatus());
        assertNotNull(updated.getLastSeen());
    }

    @Test
    @DisplayName("10. Start session: Returns HTTP 201 Created with active=true")
    void testStartSession() throws Exception {
        GpsSessionStartRequest request = new GpsSessionStartRequest(testAsset.getId());

        mockMvc.perform(post("/api/tracking/sessions/start")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetId", is(testAsset.getId().intValue())))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.startedAt", notNullValue()));
    }

    @Test
    @DisplayName("11. Stop session: Updates session to active=false and sets endedAt")
    void testStopSession() throws Exception {
        // First start a session
        GpsSession session = gpsSessionRepository.save(GpsSession.builder()
                .asset(testAsset)
                .startedBy(adminUser)
                .startedAt(Instant.now())
                .active(true)
                .build());

        mockMvc.perform(post("/api/tracking/sessions/" + session.getId() + "/stop")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.endedAt", notNullValue()));

        GpsSession updated = gpsSessionRepository.findById(session.getId()).orElseThrow();
        assertFalse(updated.isActive());
        assertNotNull(updated.getEndedAt());
    }

    @Test
    @DisplayName("12. Duplicate active session prevention: Returns HTTP 409 Conflict")
    void testDuplicateActiveSessionPrevention() throws Exception {
        // Start a session
        gpsSessionRepository.save(GpsSession.builder()
                .asset(testAsset)
                .startedBy(adminUser)
                .startedAt(Instant.now())
                .active(true)
                .build());

        GpsSessionStartRequest request = new GpsSessionStartRequest(testAsset.getId());

        mockMvc.perform(post("/api/tracking/sessions/start")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("13. Latest location: Returns hasLocation=true when telemetry exists")
    void testLatestLocation() throws Exception {
        createActiveSession(testAsset);

        // Record telemetry first
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.341234)
                .longitude(77.717865)
                .accuracy(5.0)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tracking/assets/" + testAsset.getId() + "/latest")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasLocation", is(true)))
                .andExpect(jsonPath("$.latitude", closeTo(11.341234, 0.00001)))
                .andExpect(jsonPath("$.longitude", closeTo(77.717865, 0.00001)));
    }

    @Test
    @DisplayName("14. Location history limit: Returns at most limit records")
    void testLocationHistoryLimit() throws Exception {
        createActiveSession(testAsset);

        // Ingest 3 telemetry records
        for (int i = 1; i <= 3; i++) {
            LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                    .assetId(testAsset.getId())
                    .latitude(11.340000 + (i * 0.001))
                    .longitude(77.710000 + (i * 0.001))
                    .accuracy(5.0)
                    .recordedAt(Instant.now().toString())
                    .build();

            mockMvc.perform(post("/api/tracking/location")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/tracking/assets/" + testAsset.getId() + "/history?limit=2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("15. Invalid GPS values (missing fields): Returns HTTP 400")
    void testMissingRequiredFields() throws Exception {
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(null)
                .latitude(11.341234)
                .longitude(77.717865)
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("16. Time handling: Parses UTC ISO-8601 string successfully")
    void testTimeHandlingUtc() throws Exception {
        createActiveSession(testAsset);

        String utcTimestamp = "2026-09-28T12:30:00Z";
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.341234)
                .longitude(77.717865)
                .accuracy(5.0)
                .recordedAt(utcTimestamp)
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordedAt", notNullValue()));
    }

    @Test
    @DisplayName("17. Browser source value: Persisted source is BROWSER_GPS")
    void testBrowserSourceValue() throws Exception {
        createActiveSession(testAsset);

        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.341234)
                .longitude(77.717865)
                .accuracy(5.0)
                .recordedAt(Instant.now().toString())
                .build();

        mockMvc.perform(post("/api/tracking/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<LocationHistory> histories = locationHistoryRepository.findByAssetId(testAsset.getId());
        assertEquals("BROWSER_GPS", histories.get(histories.size() - 1).getSource());
    }

    @Test
    @DisplayName("18. Stop status rule: Asset with active assignment transitions to ASSIGNED upon stop")
    void testStopStatusRule_WhenAssetHasActiveAssignment_StatusBecomesAssigned() throws Exception {
        // Create active assignment for testAsset
        assetAssignmentRepository.save(AssetAssignment.builder()
                .asset(testAsset)
                .assignedUser(staffUser)
                .department(testDept)
                .assignedAt(LocalDateTime.now(ZoneOffset.UTC))
                .active(true)
                .build());

        // Start session (asset status -> MOVING)
        GpsSession session = gpsSessionRepository.save(GpsSession.builder()
                .asset(testAsset)
                .startedBy(adminUser)
                .startedAt(Instant.now())
                .active(true)
                .build());
        testAsset.setStatus(AssetStatus.MOVING);
        assetRepository.save(testAsset);

        // Stop session
        mockMvc.perform(post("/api/tracking/sessions/" + session.getId() + "/stop")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        Asset updatedAsset = assetRepository.findById(testAsset.getId()).orElseThrow();
        assertEquals(AssetStatus.ASSIGNED, updatedAsset.getStatus(),
                "Asset with active assignment must transition to ASSIGNED when tracking is stopped");
    }

    @Test
    @DisplayName("19. Stop status rule: Asset without active assignment transitions to AVAILABLE upon stop")
    void testStopStatusRule_WhenAssetHasNoActiveAssignment_StatusBecomesAvailable() throws Exception {
        // Ensure no active assignment exists for testAsset
        List<AssetAssignment> assignments = assetAssignmentRepository.findByAssetId(testAsset.getId());
        for (AssetAssignment a : assignments) {
            a.setActive(false);
            assetAssignmentRepository.save(a);
        }

        // Start session (asset status -> MOVING)
        GpsSession session = gpsSessionRepository.save(GpsSession.builder()
                .asset(testAsset)
                .startedBy(adminUser)
                .startedAt(Instant.now())
                .active(true)
                .build());
        testAsset.setStatus(AssetStatus.MOVING);
        assetRepository.save(testAsset);

        // Stop session
        mockMvc.perform(post("/api/tracking/sessions/" + session.getId() + "/stop")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        Asset updatedAsset = assetRepository.findById(testAsset.getId()).orElseThrow();
        assertEquals(AssetStatus.AVAILABLE, updatedAsset.getStatus(),
                "Asset without active assignment must transition to AVAILABLE when tracking is stopped");
    }

    @Test
    @DisplayName("20. UTC timestamp consistency: Session startedAt and endedAt are UTC timestamps")
    void testUtcTimestampConsistency() throws Exception {
        Instant beforeUtc = Instant.now().minusSeconds(2);

        GpsSessionStartRequest startReq = new GpsSessionStartRequest(testAsset.getId());
        String resContent = mockMvc.perform(post("/api/tracking/sessions/start")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active", is(true)))
                .andReturn().getResponse().getContentAsString();

        GpsSessionResponse startRes = objectMapper.readValue(resContent, GpsSessionResponse.class);
        Instant sessionStartedAt = startRes.getStartedAt();
        assertNotNull(sessionStartedAt);
        Instant afterStartUtc = Instant.now().plusSeconds(2);
        assertTrue(!sessionStartedAt.isBefore(beforeUtc) && !sessionStartedAt.isAfter(afterStartUtc),
                "startedAt should align with UTC clock");

        // Stop session
        String stopContent = mockMvc.perform(post("/api/tracking/sessions/" + startRes.getId() + "/stop")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)))
                .andReturn().getResponse().getContentAsString();

        GpsSessionResponse stopRes = objectMapper.readValue(stopContent, GpsSessionResponse.class);
        Instant sessionEndedAt = stopRes.getEndedAt();
        assertNotNull(sessionEndedAt);
        Instant afterStopUtc = Instant.now().plusSeconds(2);
        assertTrue(!sessionEndedAt.isBefore(beforeUtc) && !sessionEndedAt.isAfter(afterStopUtc),
                "endedAt should align with UTC clock");
    }
}
