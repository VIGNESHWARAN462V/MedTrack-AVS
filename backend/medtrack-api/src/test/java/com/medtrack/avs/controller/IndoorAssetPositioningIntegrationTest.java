package com.medtrack.avs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.dto.IndoorLocationUpdateRequest;
import com.medtrack.avs.dto.QrCheckInRequest;
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
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IndoorAssetPositioningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RateLimiterService rateLimiterService;

    private String adminToken;
    private String staffToken;

    private Hospital testHospital;
    private Hospital inactiveHospital;
    private Building testBuilding;
    private Floor testFloorGround;
    private Floor testFloorOne;
    private Room testRoomBay1; // x=10, y=10, w=30, h=30
    private Asset testAsset1;
    private Asset testAssetGps;

    @BeforeEach
    void setUp() {
        if (rateLimiterService != null) {
            rateLimiterService.reset();
        }

        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.ADMIN).description("Admin").build()));
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.STAFF).description("Staff").build()));

        User adminUser = userRepository.findByEmail("admin-pos-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Admin Pos Test")
                        .email("admin-pos-test@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Admin@12345"))
                        .role(adminRole)
                        .enabled(true)
                        .build()));

        User staffUser = userRepository.findByEmail("staff-pos-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Staff Pos Test")
                        .email("staff-pos-test@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Staff@12345"))
                        .role(staffRole)
                        .enabled(true)
                        .build()));

        adminToken = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        adminUser.getEmail(), adminUser.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        staffToken = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        staffUser.getEmail(), staffUser.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority("ROLE_STAFF")))
        );

        // Test hospital & hierarchy
        testHospital = hospitalRepository.save(Hospital.builder()
                .code("HOSP-POS-TEST")
                .name("Positioning Test Hospital")
                .address("100 Campus Road, Metro NY")
                .latitude(40.7128)
                .longitude(-74.0060)
                .geofenceRadiusMeters(250.0)
                .active(true)
                .build());

        inactiveHospital = hospitalRepository.save(Hospital.builder()
                .code("HOSP-INACT")
                .name("Inactive Hospital")
                .address("101 Inactive Way, Metro NY")
                .latitude(40.7100)
                .longitude(-74.0000)
                .geofenceRadiusMeters(100.0)
                .active(false)
                .build());

        testBuilding = buildingRepository.save(Building.builder()
                .hospital(testHospital)
                .code("BLD-POS")
                .name("Clinical Wing")
                .active(true)
                .build());

        testFloorGround = floorRepository.save(Floor.builder()
                .building(testBuilding)
                .floorNumber(0)
                .displayName("Ground Floor")
                .active(true)
                .build());

        testFloorOne = floorRepository.save(Floor.builder()
                .building(testBuilding)
                .floorNumber(1)
                .displayName("Floor 1")
                .active(true)
                .build());

        Department deptER = departmentRepository.findByCode("ER").orElseGet(() ->
                departmentRepository.save(Department.builder().code("ER").name("Emergency").active(true).build()));

        // Room from x=10 to 40, y=10 to 40
        testRoomBay1 = roomRepository.save(Room.builder()
                .floor(testFloorGround)
                .department(deptER)
                .roomNumber("ROOM-POS-101")
                .name("Trauma Bay 101")
                .xPosition(10.0)
                .yPosition(10.0)
                .width(30.0)
                .height(30.0)
                .active(true)
                .build());

        // Test assets
        testAsset1 = assetRepository.save(Asset.builder()
                .assetCode("ASSET-POS-001")
                .assetName("Infusion Pump Alpha")
                .category("Infusion")
                .serialNumber("SN-PUMP-ALPHA")
                .status(AssetStatus.AVAILABLE)
                .department(deptER)
                .qrCode("MEDTRACK:ASSET:ASSET-POS-001")
                .build());

        testAssetGps = assetRepository.save(Asset.builder()
                .assetCode("ASSET-POS-GPS")
                .assetName("GPS Ambulance Monitor")
                .category("Telemetry")
                .serialNumber("SN-GPS-99")
                .status(AssetStatus.IN_USE)
                .latitude(40.7130)
                .longitude(-74.0055)
                .gpsAccuracy(5.0)
                .lastSeen(Instant.now())
                .qrCode("MEDTRACK:ASSET:ASSET-POS-GPS")
                .build());
    }

    @Test
    @DisplayName("PATCH /api/assets/{id}/indoor-location: Admin can configure indoor location inside room geometry")
    void testUpdateIndoorLocation_SuccessWithinRoom() throws Exception {
        IndoorLocationUpdateRequest request = IndoorLocationUpdateRequest.builder()
                .hospitalId(testHospital.getId())
                .buildingId(testBuilding.getId())
                .floorId(testFloorGround.getId())
                .roomId(testRoomBay1.getId())
                .indoorX(25.0)
                .indoorY(25.0)
                .indoorLocationSource(IndoorLocationSource.MANUAL_CONFIG)
                .build();

        mockMvc.perform(patch("/api/assets/" + testAsset1.getId() + "/indoor-location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testAsset1.getId().intValue())))
                .andExpect(jsonPath("$.assetCode", is("ASSET-POS-001")))
                .andExpect(jsonPath("$.floorId", is(testFloorGround.getId().intValue())))
                .andExpect(jsonPath("$.roomId", is(testRoomBay1.getId().intValue())))
                .andExpect(jsonPath("$.roomNumber", is("ROOM-POS-101")))
                .andExpect(jsonPath("$.indoorX", is(25.0)))
                .andExpect(jsonPath("$.indoorY", is(25.0)))
                .andExpect(jsonPath("$.indoorLocationSource", is("MANUAL_CONFIG")))
                .andExpect(jsonPath("$.locationStatus", is("CONFIGURED_INDOOR")));

        // Verify entity persisted in DB
        Asset updated = assetRepository.findById(testAsset1.getId()).orElseThrow();
        assertEquals(testFloorGround.getId(), updated.getFloor().getId());
        assertEquals(testRoomBay1.getId(), updated.getRoom().getId());
        assertEquals(25.0, updated.getIndoorX());
        assertEquals(25.0, updated.getIndoorY());
        assertEquals(IndoorLocationSource.MANUAL_CONFIG, updated.getIndoorLocationSource());
    }

    @Test
    @DisplayName("PATCH /api/assets/{id}/indoor-location: Preserves outdoor GPS telemetry coordinates")
    void testUpdateIndoorLocation_PreservesOutdoorGps() throws Exception {
        Double origLat = testAssetGps.getLatitude();
        Double origLon = testAssetGps.getLongitude();
        Double origAcc = testAssetGps.getGpsAccuracy();
        Instant origLastSeen = testAssetGps.getLastSeen();

        IndoorLocationUpdateRequest request = IndoorLocationUpdateRequest.builder()
                .hospitalId(testHospital.getId())
                .buildingId(testBuilding.getId())
                .floorId(testFloorGround.getId())
                .roomId(testRoomBay1.getId())
                .indoorX(20.0)
                .indoorY(20.0)
                .build();

        mockMvc.perform(patch("/api/assets/" + testAssetGps.getId() + "/indoor-location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outdoorLatitude", is(origLat)))
                .andExpect(jsonPath("$.outdoorLongitude", is(origLon)))
                .andExpect(jsonPath("$.outdoorGpsAccuracy", is(origAcc)));

        Asset reloaded = assetRepository.findById(testAssetGps.getId()).orElseThrow();
        assertEquals(origLat, reloaded.getLatitude());
        assertEquals(origLon, reloaded.getLongitude());
        assertEquals(origAcc, reloaded.getGpsAccuracy());
        assertEquals(origLastSeen, reloaded.getLastSeen());
    }

    @Test
    @DisplayName("PATCH /api/assets/{id}/indoor-location: Reject coordinates outside assigned room bounds")
    void testUpdateIndoorLocation_RejectsOutsideRoomGeometry() throws Exception {
        // Room bounds: X in [10, 40], Y in [10, 40]
        // Supply point at (50.0, 50.0) which is outside
        IndoorLocationUpdateRequest request = IndoorLocationUpdateRequest.builder()
                .hospitalId(testHospital.getId())
                .buildingId(testBuilding.getId())
                .floorId(testFloorGround.getId())
                .roomId(testRoomBay1.getId())
                .indoorX(50.0)
                .indoorY(50.0)
                .build();

        mockMvc.perform(patch("/api/assets/" + testAsset1.getId() + "/indoor-location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("falls outside assigned room")));
    }

    @Test
    @DisplayName("PATCH /api/assets/{id}/indoor-location: Reject coordinates on inactive or mismatched hierarchy")
    void testUpdateIndoorLocation_RejectsMismatchedHierarchy() throws Exception {
        // Floor belongs to testBuilding, but request uses inactiveHospital
        IndoorLocationUpdateRequest request = IndoorLocationUpdateRequest.builder()
                .hospitalId(inactiveHospital.getId())
                .buildingId(testBuilding.getId())
                .floorId(testFloorGround.getId())
                .roomId(testRoomBay1.getId())
                .indoorX(20.0)
                .indoorY(20.0)
                .build();

        mockMvc.perform(patch("/api/assets/" + testAsset1.getId() + "/indoor-location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/assets/{id}/indoor-location: Reject coordinates out of 0-100 range")
    void testUpdateIndoorLocation_RejectsOutOfBoundsPercentages() throws Exception {
        IndoorLocationUpdateRequest request = IndoorLocationUpdateRequest.builder()
                .hospitalId(testHospital.getId())
                .buildingId(testBuilding.getId())
                .floorId(testFloorGround.getId())
                .indoorX(105.0) // Invalid percentage
                .indoorY(50.0)
                .build();

        mockMvc.perform(patch("/api/assets/" + testAsset1.getId() + "/indoor-location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/assets/{id}/indoor-location: Staff cannot update manual indoor location (403 Forbidden)")
    void testUpdateIndoorLocation_ForbiddenForStaff() throws Exception {
        IndoorLocationUpdateRequest request = IndoorLocationUpdateRequest.builder()
                .hospitalId(testHospital.getId())
                .buildingId(testBuilding.getId())
                .floorId(testFloorGround.getId())
                .indoorX(20.0)
                .indoorY(20.0)
                .build();

        mockMvc.perform(patch("/api/assets/" + testAsset1.getId() + "/indoor-location")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/assets/{id}/qr-checkin: Authenticated user can check in asset to floor/room")
    void testQrCheckIn_Success() throws Exception {
        QrCheckInRequest request = QrCheckInRequest.builder()
                .hospitalId(testHospital.getId())
                .buildingId(testBuilding.getId())
                .floorId(testFloorGround.getId())
                .roomId(testRoomBay1.getId())
                // No X/Y specified: should default to room center (25.0, 25.0)
                .build();

        mockMvc.perform(post("/api/assets/" + testAsset1.getId() + "/qr-checkin")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testAsset1.getId().intValue())))
                .andExpect(jsonPath("$.indoorLocationSource", is("QR_CHECKIN")))
                .andExpect(jsonPath("$.locationStatus", is("RECENT_CHECK_IN")))
                .andExpect(jsonPath("$.indoorX", is(25.0)))
                .andExpect(jsonPath("$.indoorY", is(25.0)));
    }

    @Test
    @DisplayName("GET /api/floors/{floorId}/assets: Retrieves assets on floor and handles movement between floors")
    void testGetAssetsByFloorAndMovement() throws Exception {
        // Initially no assets on Ground Floor
        mockMvc.perform(get("/api/floors/" + testFloorGround.getId() + "/assets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Place testAsset1 on Ground Floor
        IndoorLocationUpdateRequest requestGround = IndoorLocationUpdateRequest.builder()
                .hospitalId(testHospital.getId())
                .buildingId(testBuilding.getId())
                .floorId(testFloorGround.getId())
                .roomId(testRoomBay1.getId())
                .indoorX(25.0)
                .indoorY(25.0)
                .build();

        mockMvc.perform(patch("/api/assets/" + testAsset1.getId() + "/indoor-location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestGround)))
                .andExpect(status().isOk());

        // Ground Floor now has 1 asset
        mockMvc.perform(get("/api/floors/" + testFloorGround.getId() + "/assets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].assetCode", is("ASSET-POS-001")));

        // Floor 1 has 0 assets
        mockMvc.perform(get("/api/floors/" + testFloorOne.getId() + "/assets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Move asset to Floor 1
        IndoorLocationUpdateRequest requestFloor1 = IndoorLocationUpdateRequest.builder()
                .hospitalId(testHospital.getId())
                .buildingId(testBuilding.getId())
                .floorId(testFloorOne.getId())
                .indoorX(50.0)
                .indoorY(50.0)
                .build();

        mockMvc.perform(patch("/api/assets/" + testAsset1.getId() + "/indoor-location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestFloor1)))
                .andExpect(status().isOk());

        // Now Ground Floor has 0 assets
        mockMvc.perform(get("/api/floors/" + testFloorGround.getId() + "/assets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // And Floor 1 has 1 asset
        mockMvc.perform(get("/api/floors/" + testFloorOne.getId() + "/assets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].assetCode", is("ASSET-POS-001")));
    }
}
