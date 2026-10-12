package com.medtrack.avs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.dto.HospitalCreateRequest;
import com.medtrack.avs.dto.HospitalUpdateRequest;
import com.medtrack.avs.entity.Hospital;
import com.medtrack.avs.entity.Role;
import com.medtrack.avs.entity.RoleType;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.HospitalRepository;
import com.medtrack.avs.repository.RoleRepository;
import com.medtrack.avs.repository.UserRepository;
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
class HospitalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HospitalRepository hospitalRepository;

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

    private Hospital sampleHospital;

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

        User adminUser = userRepository.findByEmail("admin-hosp-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Admin Hospital Test")
                        .email("admin-hosp-test@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Admin@12345"))
                        .role(adminRole)
                        .enabled(true)
                        .build()));

        User supervisorUser = userRepository.findByEmail("supervisor-hosp-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Supervisor Hospital Test")
                        .email("supervisor-hosp-test@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Supervisor@12345"))
                        .role(supervisorRole)
                        .enabled(true)
                        .build()));

        User staffUser = userRepository.findByEmail("staff-hosp-test@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Staff Hospital Test")
                        .email("staff-hosp-test@medtrack.org")
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

        sampleHospital = hospitalRepository.save(Hospital.builder()
                .name("General Test Hospital")
                .code("GTH-" + System.currentTimeMillis())
                .address("101 Medical Center Way")
                .latitude(11.496000)
                .longitude(77.278000)
                .geofenceRadiusMeters(175.0)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("GET /api/hospitals - Authenticated user can list hospitals")
    void getAllHospitals_Authenticated_Success() throws Exception {
        mockMvc.perform(get("/api/hospitals")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[*].id", hasItem(sampleHospital.getId().intValue())));
    }

    @Test
    @DisplayName("GET /api/hospitals/{id} - Authenticated user can fetch hospital by ID")
    void getHospitalById_Success() throws Exception {
        mockMvc.perform(get("/api/hospitals/" + sampleHospital.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sampleHospital.getId().intValue())))
                .andExpect(jsonPath("$.name", is("General Test Hospital")))
                .andExpect(jsonPath("$.latitude", is(11.496000)))
                .andExpect(jsonPath("$.longitude", is(77.278000)));
    }

    @Test
    @DisplayName("GET /api/hospitals/{id} - Non-existent ID returns 404")
    void getHospitalById_NotFound() throws Exception {
        mockMvc.perform(get("/api/hospitals/999999")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/hospitals - ADMIN creates hospital with valid coordinates")
    void createHospital_Admin_Success() throws Exception {
        HospitalCreateRequest request = HospitalCreateRequest.builder()
                .name("New Specialty Hospital")
                .code("NSH-" + System.currentTimeMillis())
                .address("456 Care Center Lane")
                .latitude(11.498500)
                .longitude(77.281000)
                .geofenceRadiusMeters(180.0)
                .active(true)
                .build();

        mockMvc.perform(post("/api/hospitals")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("New Specialty Hospital")))
                .andExpect(jsonPath("$.latitude", is(11.498500)))
                .andExpect(jsonPath("$.longitude", is(77.281000)))
                .andExpect(jsonPath("$.geofenceRadiusMeters", is(180.0)));
    }

    @Test
    @DisplayName("POST /api/hospitals - STAFF is forbidden (403)")
    void createHospital_Staff_Forbidden() throws Exception {
        HospitalCreateRequest request = HospitalCreateRequest.builder()
                .name("Unauthorized Hospital")
                .code("UNAUTH-" + System.currentTimeMillis())
                .latitude(11.498500)
                .longitude(77.281000)
                .build();

        mockMvc.perform(post("/api/hospitals")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/hospitals - Invalid latitude/longitude returns 400 Bad Request")
    void createHospital_InvalidCoordinates_BadRequest() throws Exception {
        HospitalCreateRequest request = HospitalCreateRequest.builder()
                .name("Invalid Coords Hospital")
                .code("INV-" + System.currentTimeMillis())
                .latitude(95.0) // Invalid: > 90
                .longitude(77.281000)
                .build();

        mockMvc.perform(post("/api/hospitals")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/hospitals - Duplicate code returns 400 Bad Request")
    void createHospital_DuplicateCode_BadRequest() throws Exception {
        HospitalCreateRequest request = HospitalCreateRequest.builder()
                .name("Duplicate Hospital")
                .code(sampleHospital.getCode())
                .latitude(11.498500)
                .longitude(77.281000)
                .build();

        mockMvc.perform(post("/api/hospitals")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/hospitals/{id} - ADMIN updates hospital coordinates and radius")
    void updateHospital_Admin_Success() throws Exception {
        HospitalUpdateRequest request = HospitalUpdateRequest.builder()
                .name("Updated General Test Hospital")
                .latitude(11.499000)
                .longitude(77.282000)
                .geofenceRadiusMeters(220.0)
                .build();

        mockMvc.perform(put("/api/hospitals/" + sampleHospital.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated General Test Hospital")))
                .andExpect(jsonPath("$.latitude", is(11.499000)))
                .andExpect(jsonPath("$.longitude", is(77.282000)))
                .andExpect(jsonPath("$.geofenceRadiusMeters", is(220.0)));
    }

    @Test
    @DisplayName("DELETE /api/hospitals/{id} - ADMIN deactivates hospital (204 No Content)")
    void deleteHospital_Admin_Success() throws Exception {
        mockMvc.perform(delete("/api/hospitals/" + sampleHospital.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Hospital deactivated = hospitalRepository.findById(sampleHospital.getId()).orElseThrow();
        assertFalse(deactivated.isActive());
    }

    @Test
    @DisplayName("DELETE /api/hospitals/{id} - SUPERVISOR is forbidden (403)")
    void deleteHospital_Supervisor_Forbidden() throws Exception {
        mockMvc.perform(delete("/api/hospitals/" + sampleHospital.getId())
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isForbidden());
    }
}
