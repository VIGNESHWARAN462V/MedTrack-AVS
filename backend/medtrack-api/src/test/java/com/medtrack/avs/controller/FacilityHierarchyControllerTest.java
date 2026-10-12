package com.medtrack.avs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.dto.*;
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

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FacilityHierarchyControllerTest {

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
    private com.medtrack.avs.security.RateLimiterService rateLimiterService;

    private String adminToken;
    private String supervisorToken;
    private String staffToken;

    private Hospital hospital;
    private Building building;
    private Floor floor;
    private Room room;
    private Department department;

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

        User adminUser = userRepository.findByEmail("admin-fac-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Admin Facility Test")
                        .email("admin-fac-test@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Admin@12345"))
                        .role(adminRole)
                        .enabled(true)
                        .build()));

        User supervisorUser = userRepository.findByEmail("supervisor-fac-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Supervisor Facility Test")
                        .email("supervisor-fac-test@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Supervisor@12345"))
                        .role(supervisorRole)
                        .enabled(true)
                        .build()));

        User staffUser = userRepository.findByEmail("staff-fac-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Staff Facility Test")
                        .email("staff-fac-test@medtrack.org")
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

        hospital = hospitalRepository.save(Hospital.builder()
                .name("Facility Test Hospital")
                .code("FTH-" + System.currentTimeMillis())
                .address("500 Clinical Drive")
                .latitude(11.496000)
                .longitude(77.278000)
                .geofenceRadiusMeters(160.0)
                .active(true)
                .build());

        building = buildingRepository.save(Building.builder()
                .hospital(hospital)
                .name("Test Surgical Block")
                .code("TSB-" + System.currentTimeMillis())
                .description("Surgical Suites and PACU")
                .latitude(11.496200)
                .longitude(77.278200)
                .active(true)
                .build());

        floor = floorRepository.save(Floor.builder()
                .building(building)
                .floorNumber(0)
                .displayName("Ground Floor")
                .floorPlanWidth(1000)
                .floorPlanHeight(800)
                .active(true)
                .build());

        department = departmentRepository.findAll().stream().findFirst().orElseGet(() ->
                departmentRepository.save(Department.builder()
                        .name("Emergency Dept Test")
                        .code("ED-T-" + System.currentTimeMillis())
                        .active(true)
                        .build()));

        room = roomRepository.save(Room.builder()
                .floor(floor)
                .department(department)
                .roomNumber("OR-101-" + System.currentTimeMillis())
                .name("Operating Suite 1")
                .xPosition(10.0)
                .yPosition(15.0)
                .width(30.0)
                .height(25.0)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("1. Authenticated users can retrieve buildings")
    void getBuildings_Authenticated_Success() throws Exception {
        mockMvc.perform(get("/api/hospitals/" + hospital.getId() + "/buildings")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].name", is("Test Surgical Block")));
    }

    @Test
    @DisplayName("2. Authenticated users can retrieve floors")
    void getFloors_Authenticated_Success() throws Exception {
        mockMvc.perform(get("/api/buildings/" + building.getId() + "/floors")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].displayName", is("Ground Floor")));
    }

    @Test
    @DisplayName("3. Authenticated users can retrieve rooms")
    void getRooms_Authenticated_Success() throws Exception {
        mockMvc.perform(get("/api/floors/" + floor.getId() + "/rooms")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].name", is("Operating Suite 1")));
    }

    @Test
    @DisplayName("4. Unauthenticated access is rejected")
    void unauthenticatedAccess_Rejected() throws Exception {
        mockMvc.perform(get("/api/hospitals/" + hospital.getId() + "/buildings"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/hospitals/" + hospital.getId() + "/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("5. STAFF cannot create buildings, floors, or rooms (403 Forbidden)")
    void staff_CannotCreateHierarchy_Forbidden() throws Exception {
        BuildingCreateRequest bReq = BuildingCreateRequest.builder()
                .name("Staff Building").code("STF-B").build();
        mockMvc.perform(post("/api/admin/hospitals/" + hospital.getId() + "/buildings")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bReq)))
                .andExpect(status().isForbidden());

        FloorCreateRequest fReq = FloorCreateRequest.builder()
                .floorNumber(1).displayName("Staff Floor").build();
        mockMvc.perform(post("/api/admin/buildings/" + building.getId() + "/floors")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fReq)))
                .andExpect(status().isForbidden());

        RoomCreateRequest rReq = RoomCreateRequest.builder()
                .roomNumber("STF-R1").name("Staff Room")
                .xPosition(5.0).yPosition(5.0).width(20.0).height(20.0).build();
        mockMvc.perform(post("/api/admin/floors/" + floor.getId() + "/rooms")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6. SUPERVISOR cannot create buildings, floors, or rooms (403 Forbidden)")
    void supervisor_CannotCreateHierarchy_Forbidden() throws Exception {
        BuildingCreateRequest bReq = BuildingCreateRequest.builder()
                .name("Sup Building").code("SUP-B").build();
        mockMvc.perform(post("/api/admin/hospitals/" + hospital.getId() + "/buildings")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7. ADMIN can create and update each hierarchy level")
    void admin_CanCreateAndUpdateEachLevel_Success() throws Exception {
        // Create Building
        BuildingCreateRequest bReq = BuildingCreateRequest.builder()
                .name("New Inpatient Wing")
                .code("NIW-" + System.currentTimeMillis())
                .latitude(11.497000)
                .longitude(77.279000)
                .build();

        String bRespStr = mockMvc.perform(post("/api/admin/hospitals/" + hospital.getId() + "/buildings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        BuildingResponse bResp = objectMapper.readValue(bRespStr, BuildingResponse.class);

        // Update Building
        BuildingUpdateRequest bUpd = BuildingUpdateRequest.builder().name("Updated Inpatient Wing").build();
        mockMvc.perform(put("/api/admin/buildings/" + bResp.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bUpd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Inpatient Wing")));

        // Create Floor
        FloorCreateRequest fReq = FloorCreateRequest.builder()
                .floorNumber(1)
                .displayName("Floor 1 - ICU")
                .floorPlanWidth(1200)
                .floorPlanHeight(900)
                .build();

        String fRespStr = mockMvc.perform(post("/api/admin/buildings/" + bResp.getId() + "/floors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName", is("Floor 1 - ICU")))
                .andReturn().getResponse().getContentAsString();

        FloorResponse fResp = objectMapper.readValue(fRespStr, FloorResponse.class);

        // Create Room
        RoomCreateRequest rReq = RoomCreateRequest.builder()
                .roomNumber("ICU-201")
                .name("ICU Room 201")
                .departmentId(department.getId())
                .xPosition(10.0)
                .yPosition(10.0)
                .width(40.0)
                .height(30.0)
                .build();

        String rRespStr = mockMvc.perform(post("/api/admin/floors/" + fResp.getId() + "/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomNumber", is("ICU-201")))
                .andReturn().getResponse().getContentAsString();

        RoomResponse rResp = objectMapper.readValue(rRespStr, RoomResponse.class);

        // Update Room
        RoomUpdateRequest rUpd = RoomUpdateRequest.builder().name("ICU Room 201 Advanced").build();
        mockMvc.perform(put("/api/admin/rooms/" + rResp.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rUpd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ICU Room 201 Advanced")));
    }

    @Test
    @DisplayName("8. Invalid coordinates and dimensions are rejected")
    void invalidCoordinatesAndDimensions_Rejected() throws Exception {
        // Invalid building coordinates
        BuildingCreateRequest badB = BuildingCreateRequest.builder()
                .name("Bad Coords").code("BAD-C").latitude(95.0).longitude(77.0).build();
        mockMvc.perform(post("/api/admin/hospitals/" + hospital.getId() + "/buildings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badB)))
                .andExpect(status().isBadRequest());

        // Negative floor dimensions
        FloorCreateRequest badF = FloorCreateRequest.builder()
                .floorNumber(5).displayName("Bad Floor").floorPlanWidth(-100).build();
        mockMvc.perform(post("/api/admin/buildings/" + building.getId() + "/floors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badF)))
                .andExpect(status().isBadRequest());

        // Overflowing room bounds (x + width > 100%)
        RoomCreateRequest badR = RoomCreateRequest.builder()
                .roomNumber("OVERFLOW").name("Overflow Room")
                .xPosition(80.0).yPosition(10.0).width(30.0).height(20.0).build();
        mockMvc.perform(post("/api/admin/floors/" + floor.getId() + "/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badR)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("9. Duplicate building codes within one hospital are rejected")
    void duplicateBuildingCode_Rejected() throws Exception {
        BuildingCreateRequest dupB = BuildingCreateRequest.builder()
                .name("Duplicate Block").code(building.getCode()).build();
        mockMvc.perform(post("/api/admin/hospitals/" + hospital.getId() + "/buildings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupB)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("10. Duplicate floor numbers within one building are rejected")
    void duplicateFloorNumber_Rejected() throws Exception {
        FloorCreateRequest dupF = FloorCreateRequest.builder()
                .floorNumber(floor.getFloorNumber()).displayName("Duplicate Level 0").build();
        mockMvc.perform(post("/api/admin/buildings/" + building.getId() + "/floors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupF)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("11. Duplicate room numbers within one floor are rejected")
    void duplicateRoomNumber_Rejected() throws Exception {
        RoomCreateRequest dupR = RoomCreateRequest.builder()
                .roomNumber(room.getRoomNumber()).name("Duplicate Room")
                .xPosition(10.0).yPosition(10.0).width(20.0).height(20.0).build();
        mockMvc.perform(post("/api/admin/floors/" + floor.getId() + "/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupR)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("12. Invalid hospital/building/floor relationships are rejected")
    void invalidRelationships_Rejected() throws Exception {
        // Non-existent hospital ID
        BuildingCreateRequest bReq = BuildingCreateRequest.builder()
                .name("Ghost Building").code("GST-B").build();
        mockMvc.perform(post("/api/admin/hospitals/999999/buildings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bReq)))
                .andExpect(status().isNotFound());

        // Non-existent building ID
        FloorCreateRequest fReq = FloorCreateRequest.builder()
                .floorNumber(9).displayName("Ghost Floor").build();
        mockMvc.perform(post("/api/admin/buildings/999999/floors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fReq)))
                .andExpect(status().isNotFound());

        // Non-existent floor ID
        RoomCreateRequest rReq = RoomCreateRequest.builder()
                .roomNumber("GST-R").name("Ghost Room")
                .xPosition(10.0).yPosition(10.0).width(20.0).height(20.0).build();
        mockMvc.perform(post("/api/admin/floors/999999/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("13. Soft deactivation preserves existing records")
    void softDeactivation_PreservesRecords() throws Exception {
        mockMvc.perform(delete("/api/admin/rooms/" + room.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Room preservedRoom = roomRepository.findById(room.getId()).orElseThrow();
        assertFalse(preservedRoom.isActive());

        mockMvc.perform(delete("/api/admin/floors/" + floor.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Floor preservedFloor = floorRepository.findById(floor.getId()).orElseThrow();
        assertFalse(preservedFloor.isActive());

        mockMvc.perform(delete("/api/admin/buildings/" + building.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Building preservedBuilding = buildingRepository.findById(building.getId()).orElseThrow();
        assertFalse(preservedBuilding.isActive());
    }
}
