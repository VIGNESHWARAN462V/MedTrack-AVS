package com.medtrack.avs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.dto.RoomCreateRequest;
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

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IndoorFloorMapIntegrationTest {

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
    private Building testBuilding;
    private Floor testFloorGround;
    private Floor testFloorEmpty;
    private Department testDeptER;
    private Room testRoom1;
    private Room testRoom2;

    @BeforeEach
    void setUp() {
        if (rateLimiterService != null) {
            rateLimiterService.reset();
        }

        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.ADMIN).description("Admin").build()));
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.STAFF).description("Staff").build()));

        User adminUser = userRepository.findByEmail("admin-indoor-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Admin Indoor Test")
                        .email("admin-indoor-test@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Admin@12345"))
                        .role(adminRole)
                        .enabled(true)
                        .build()));

        User staffUser = userRepository.findByEmail("staff-indoor-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Staff Indoor Test")
                        .email("staff-indoor-test@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Staff@12345"))
                        .role(staffRole)
                        .enabled(true)
                        .build()));

        adminToken = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        adminUser.getEmail(), adminUser.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        staffToken = jwtService.generateToken(
                new org.springframework.security.core.userdetails.User(
                        staffUser.getEmail(), staffUser.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
                )
        );

        // Seed Department
        testDeptER = departmentRepository.findByCode("ER").orElseGet(() ->
                departmentRepository.save(Department.builder()
                        .name("Emergency Department")
                        .code("ER")
                        .description("Acute emergency and trauma services")
                        .build()));

        // Seed Hospital
        testHospital = hospitalRepository.save(Hospital.builder()
                .name("Apex Valley General Hospital")
                .code("AVS-GEN-01")
                .address("500 Medical Center Drive")
                .latitude(11.496155)
                .longitude(77.278545)
                .geofenceRadiusMeters(160.0)
                .active(true)
                .build());

        // Seed Building
        testBuilding = buildingRepository.save(Building.builder()
                .hospital(testHospital)
                .name("Clinical Surgery Pavilion")
                .code("BLD-SURG-01")
                .latitude(11.496200)
                .longitude(77.278600)
                .active(true)
                .build());

        // Seed Floor 1 (With Floor Plan CAD Dimensions)
        testFloorGround = floorRepository.save(Floor.builder()
                .building(testBuilding)
                .floorNumber(0)
                .displayName("Ground Floor - Triage & Resuscitation")
                .floorPlanImageUrl("https://cdn.medtrack.org/plans/surg-ground.svg")
                .floorPlanWidth(1200)
                .floorPlanHeight(800)
                .active(true)
                .build());

        // Seed Floor 2 (Schematic Mode - No Image)
        testFloorEmpty = floorRepository.save(Floor.builder()
                .building(testBuilding)
                .floorNumber(1)
                .displayName("Floor 1 - Surgical Suites")
                .floorPlanImageUrl(null)
                .floorPlanWidth(null)
                .floorPlanHeight(null)
                .active(true)
                .build());

        // Seed Rooms with percentage geometry
        testRoom1 = roomRepository.save(Room.builder()
                .floor(testFloorGround)
                .department(testDeptER)
                .roomNumber("RESUS-BAY-1")
                .name("Emergency Resuscitation Bay 1")
                .description("Acute stabilization bay with telemetry and defib")
                .xPosition(10.0)
                .yPosition(15.0)
                .width(35.0)
                .height(30.0)
                .active(true)
                .build());

        testRoom2 = roomRepository.save(Room.builder()
                .floor(testFloorGround)
                .department(null)
                .roomNumber("STORAGE-CLN-1")
                .name("Clean Utility & Sterile Storage")
                .description("Clean linens and infusion consumable store")
                .xPosition(55.0)
                .yPosition(50.0)
                .width(30.0)
                .height(25.0)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Phase 5: Query rooms for floor returns percentage geometries, department zoning and room numbers")
    void testGetRoomsForFloorReturnsCorrectGeometries() throws Exception {
        mockMvc.perform(get("/api/floors/" + testFloorGround.getId() + "/rooms")
                        .param("activeOnly", "true")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.roomNumber == 'RESUS-BAY-1')].name", contains("Emergency Resuscitation Bay 1")))
                .andExpect(jsonPath("$[?(@.roomNumber == 'RESUS-BAY-1')].departmentCode", contains("ER")))
                .andExpect(jsonPath("$[?(@.roomNumber == 'RESUS-BAY-1')].xPosition", contains(10.0)))
                .andExpect(jsonPath("$[?(@.roomNumber == 'RESUS-BAY-1')].yPosition", contains(15.0)))
                .andExpect(jsonPath("$[?(@.roomNumber == 'RESUS-BAY-1')].width", contains(35.0)))
                .andExpect(jsonPath("$[?(@.roomNumber == 'RESUS-BAY-1')].height", contains(30.0)))
                .andExpect(jsonPath("$[?(@.roomNumber == 'STORAGE-CLN-1')].departmentCode", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.roomNumber == 'STORAGE-CLN-1')].xPosition", contains(55.0)))
                .andExpect(jsonPath("$[?(@.roomNumber == 'STORAGE-CLN-1')].yPosition", contains(50.0)));
    }

    @Test
    @DisplayName("Phase 5: Floor endpoint preserves floorPlanImageUrl and dimensions for CAD overlay")
    void testFloorPlanMetadataPreservation() throws Exception {
        mockMvc.perform(get("/api/floors/" + testFloorGround.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ground Floor - Triage & Resuscitation"))
                .andExpect(jsonPath("$.floorPlanImageUrl").value("https://cdn.medtrack.org/plans/surg-ground.svg"))
                .andExpect(jsonPath("$.floorPlanWidth").value(1200))
                .andExpect(jsonPath("$.floorPlanHeight").value(800))
                .andExpect(jsonPath("$.roomCount").value(2));
    }

    @Test
    @DisplayName("Phase 5: Floors without configured rooms return clean empty list")
    void testEmptyRoomsOnUnconfiguredFloor() throws Exception {
        mockMvc.perform(get("/api/floors/" + testFloorEmpty.getId() + "/rooms")
                        .param("activeOnly", "true")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Phase 5: Backend rejects room coordinate boundaries exceeding 100% canvas limit")
    void testRoomCanvasBoundaryValidation() throws Exception {
        // Room whose xPosition (80%) + width (30%) exceeds 100%
        RoomCreateRequest invalidXRequest = RoomCreateRequest.builder()
                .roomNumber("ROOM-OVERFLOW-X")
                .name("Overflow Width Room")
                .xPosition(80.0)
                .yPosition(20.0)
                .width(30.0)
                .height(20.0)
                .build();

        mockMvc.perform(post("/api/admin/floors/" + testFloorEmpty.getId() + "/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidXRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("exceeds canvas 100.0%")));

        // Room whose yPosition (70%) + height (40%) exceeds 100%
        RoomCreateRequest invalidYRequest = RoomCreateRequest.builder()
                .roomNumber("ROOM-OVERFLOW-Y")
                .name("Overflow Height Room")
                .xPosition(20.0)
                .yPosition(70.0)
                .width(20.0)
                .height(40.0)
                .build();

        mockMvc.perform(post("/api/admin/floors/" + testFloorEmpty.getId() + "/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidYRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("exceeds canvas 100.0%")));
    }

    @Test
    @DisplayName("Phase 5: Unauthenticated access to rooms is rejected with 401 Unauthorized")
    void testUnauthorizedRoomAccess() throws Exception {
        mockMvc.perform(get("/api/floors/" + testFloorGround.getId() + "/rooms"))
                .andExpect(status().isUnauthorized());

        RoomCreateRequest request = RoomCreateRequest.builder()
                .roomNumber("ROOM-SEC-1")
                .name("Secure Room")
                .xPosition(10.0)
                .yPosition(10.0)
                .width(20.0)
                .height(20.0)
                .build();

        // Staff cannot create room
        mockMvc.perform(post("/api/admin/floors/" + testFloorGround.getId() + "/rooms")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
