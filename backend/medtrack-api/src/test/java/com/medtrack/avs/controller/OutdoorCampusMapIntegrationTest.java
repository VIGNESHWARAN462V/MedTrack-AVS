package com.medtrack.avs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.dto.BuildingCreateRequest;
import com.medtrack.avs.dto.HospitalCreateRequest;
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
class OutdoorCampusMapIntegrationTest {

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

    private Hospital hospitalMain;
    private Hospital hospitalAnnex;
    private Building buildingWithCoords;
    private Building buildingWithoutCoords;

    @BeforeEach
    void setUp() {
        if (rateLimiterService != null) {
            rateLimiterService.reset();
        }

        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.ADMIN).description("Admin").build()));
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.STAFF).description("Staff").build()));

        User adminUser = userRepository.findByEmail("admin-campus-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Admin Campus Test")
                        .email("admin-campus-test@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Admin@12345"))
                        .role(adminRole)
                        .enabled(true)
                        .build()));

        User staffUser = userRepository.findByEmail("staff-campus-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Staff Campus Test")
                        .email("staff-campus-test@medtrack.org")
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

        // Seed Hospital 1 (Main Campus)
        hospitalMain = hospitalRepository.save(Hospital.builder()
                .name("Apex Valley Central Campus")
                .code("AVS-CAMPUS-01")
                .address("100 Healthcare Parkway, Valley District")
                .latitude(11.496155)
                .longitude(77.278545)
                .geofenceRadiusMeters(180.0)
                .active(true)
                .build());

        // Seed Hospital 2 (South Annex Campus)
        hospitalAnnex = hospitalRepository.save(Hospital.builder()
                .name("Apex South Annex Campus")
                .code("AVS-ANNEX-02")
                .address("45 South Medical Center Blvd")
                .latitude(11.482000)
                .longitude(77.265000)
                .geofenceRadiusMeters(250.0)
                .active(true)
                .build());

        // Seed Building with valid coordinates in Main Campus
        buildingWithCoords = buildingRepository.save(Building.builder()
                .hospital(hospitalMain)
                .name("Emergency Trauma Pavilion")
                .code("BLD-ETP-01")
                .description("Level 1 Trauma and Acute Center")
                .latitude(11.496300)
                .longitude(77.278700)
                .active(true)
                .build());

        // Seed Building lacking coordinates in Main Campus
        buildingWithoutCoords = buildingRepository.save(Building.builder()
                .hospital(hospitalMain)
                .name("Logistics & Warehouse Unit")
                .code("BLD-LOG-UNMAPPED")
                .description("Supply depot without GPS fix")
                .latitude(null)
                .longitude(null)
                .active(true)
                .build());

        // Seed Floors for buildingWithCoords in ascending floorNumber order
        floorRepository.save(Floor.builder()
                .building(buildingWithCoords)
                .displayName("Basement Central Storage")
                .floorNumber(-1)
                .active(true)
                .build());

        floorRepository.save(Floor.builder()
                .building(buildingWithCoords)
                .displayName("Ground Floor - Triage & Admitting")
                .floorNumber(0)
                .active(true)
                .build());

        floorRepository.save(Floor.builder()
                .building(buildingWithCoords)
                .displayName("Floor 1 - Intensive Care Unit")
                .floorNumber(1)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Phase 4: Hospital selector queries active hospitals with coordinates and campus geofence radius")
    void testHospitalSelectorRetrievesActiveCampuses() throws Exception {
        mockMvc.perform(get("/api/hospitals")
                        .param("activeOnly", "true")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[?(@.code == 'AVS-CAMPUS-01')].name", contains("Apex Valley Central Campus")))
                .andExpect(jsonPath("$[?(@.code == 'AVS-CAMPUS-01')].latitude", contains(11.496155)))
                .andExpect(jsonPath("$[?(@.code == 'AVS-CAMPUS-01')].longitude", contains(77.278545)))
                .andExpect(jsonPath("$[?(@.code == 'AVS-CAMPUS-01')].geofenceRadiusMeters", contains(180.0)))
                .andExpect(jsonPath("$[?(@.code == 'AVS-CAMPUS-01')].active", contains(true)));
    }

    @Test
    @DisplayName("Phase 4: Buildings query returns both valid and unmapped buildings with distinct coordinates")
    void testBuildingsWithValidAndMissingCoordinates() throws Exception {
        mockMvc.perform(get("/api/hospitals/" + hospitalMain.getId() + "/buildings")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                // Building with valid coordinates
                .andExpect(jsonPath("$[?(@.code == 'BLD-ETP-01')].latitude", contains(11.496300)))
                .andExpect(jsonPath("$[?(@.code == 'BLD-ETP-01')].longitude", contains(77.278700)))
                // Building without coordinates
                .andExpect(jsonPath("$[?(@.code == 'BLD-LOG-UNMAPPED')].latitude", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.code == 'BLD-LOG-UNMAPPED')].longitude", contains(nullValue())));
    }

    @Test
    @DisplayName("Phase 4: Switching hospitals isolates buildings and updates campus center")
    void testSwitchingBetweenHospitals() throws Exception {
        // Query Hospital 1
        mockMvc.perform(get("/api/hospitals/" + hospitalMain.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AVS-CAMPUS-01"))
                .andExpect(jsonPath("$.geofenceRadiusMeters").value(180.0))
                .andExpect(jsonPath("$.latitude").value(11.496155))
                .andExpect(jsonPath("$.longitude").value(77.278545));

        // Query Hospital 2
        mockMvc.perform(get("/api/hospitals/" + hospitalAnnex.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AVS-ANNEX-02"))
                .andExpect(jsonPath("$.geofenceRadiusMeters").value(250.0))
                .andExpect(jsonPath("$.latitude").value(11.482000))
                .andExpect(jsonPath("$.longitude").value(77.265000));

        // Buildings in Hospital 2 should be empty initially
        mockMvc.perform(get("/api/hospitals/" + hospitalAnnex.getId() + "/buildings")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Phase 4: Building detail panel returns configured floors ordered by floorNumber ascending")
    void testFloorHierarchyOrderingAscending() throws Exception {
        mockMvc.perform(get("/api/buildings/" + buildingWithCoords.getId() + "/floors")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].floorNumber").value(-1))
                .andExpect(jsonPath("$[0].displayName").value("Basement Central Storage"))
                .andExpect(jsonPath("$[1].floorNumber").value(0))
                .andExpect(jsonPath("$[1].displayName").value("Ground Floor - Triage & Admitting"))
                .andExpect(jsonPath("$[2].floorNumber").value(1))
                .andExpect(jsonPath("$[2].displayName").value("Floor 1 - Intensive Care Unit"));
    }

    @Test
    @DisplayName("Phase 4: Unauthorized access is rejected and mutations require ADMIN authority")
    void testUnauthorizedAndMutationSecurity() throws Exception {
        // 1. Unauthenticated request rejected
        mockMvc.perform(get("/api/hospitals"))
                .andExpect(status().isUnauthorized());

        // 2. Staff user forbidden from creating building
        BuildingCreateRequest buildingRequest = BuildingCreateRequest.builder()
                .name("Unauthorized Wing")
                .code("BLD-UNAUTH")
                .latitude(11.500000)
                .longitude(77.280000)
                .build();

        mockMvc.perform(post("/api/hospitals/" + hospitalMain.getId() + "/buildings")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildingRequest)))
                .andExpect(status().isForbidden());

        // 3. Admin user successfully creates building
        mockMvc.perform(post("/api/hospitals/" + hospitalMain.getId() + "/buildings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildingRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BLD-UNAUTH"));
    }
}
