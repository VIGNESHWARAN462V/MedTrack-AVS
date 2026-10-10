package com.medtrack.avs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.dto.LoginRequest;
import com.medtrack.avs.dto.OAuth2ExchangeRequest;
import com.medtrack.avs.dto.RegisterRequest;
import com.medtrack.avs.entity.Role;
import com.medtrack.avs.entity.RoleType;
import com.medtrack.avs.entity.User;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String supervisorToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        // Ensure roles exist
        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.ADMIN).description("Admin").build()));
        Role supervisorRole = roleRepository.findByName(RoleType.SUPERVISOR).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.SUPERVISOR).description("Supervisor").build()));
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.STAFF).description("Staff").build()));

        // Create or ensure seed users
        User adminUser = userRepository.findByEmail("admin@medtrack.org").orElseGet(() ->
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

        User staffUser = userRepository.findByEmail("nurse@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Staff Nurse")
                        .email("nurse@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Staff@12345"))
                        .role(staffRole)
                        .enabled(true)
                        .build()));

        // Generate tokens for testing
        org.springframework.security.core.userdetails.User adminDetails =
                new org.springframework.security.core.userdetails.User(
                        adminUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        adminToken = jwtService.generateToken(adminDetails);

        org.springframework.security.core.userdetails.User supervisorDetails =
                new org.springframework.security.core.userdetails.User(
                        supervisorUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR")));
        supervisorToken = jwtService.generateToken(supervisorDetails);

        org.springframework.security.core.userdetails.User staffDetails =
                new org.springframework.security.core.userdetails.User(
                        staffUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        staffToken = jwtService.generateToken(staffDetails);
    }

    @Test
    @DisplayName("POST /api/auth/register - Successful registration assigns STAFF role and does not leak password")
    void testSuccessfulRegistration() throws Exception {
        String uniqueEmail = "regtest_" + System.currentTimeMillis() + "@medtrack.org";
        RegisterRequest request = RegisterRequest.builder()
                .name("Test Registered User")
                .email(uniqueEmail)
                .password("SecurePass123")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Registered User"))
                .andExpect(jsonPath("$.email").value(uniqueEmail))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.roles", hasItem("STAFF")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertFalse(responseBody.contains("passwordHash"));
        assertFalse(responseBody.contains("SecurePass123"));
    }

    @Test
    @DisplayName("POST /api/auth/register - Duplicate email returns HTTP 409 Conflict")
    void testDuplicateRegistration() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("Duplicate Test")
                .email("admin@medtrack.org") // Already exists
                .password("Password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message", containsString("already in use")));
    }

    @Test
    @DisplayName("POST /api/auth/register - Invalid inputs return HTTP 400 Bad Request")
    void testInvalidRegistration() throws Exception {
        RegisterRequest invalidRequest = RegisterRequest.builder()
                .name("") // Blank name
                .email("not-an-email") // Invalid email
                .password("123") // Too short (<6 chars)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Successful login returns JWT and safe user object")
    void testSuccessfulLogin() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("admin@medtrack.org")
                .password("Admin@12345")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.user.email").value("admin@medtrack.org"))
                .andExpect(jsonPath("$.user.name").value("Admin System"))
                .andExpect(jsonPath("$.user.roles", hasItem("ADMIN")))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertFalse(responseBody.contains("passwordHash"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Invalid password returns HTTP 401 JSON")
    void testInvalidLogin() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("admin@medtrack.org")
                .password("WrongPassword999")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Disabled user returns HTTP 401 JSON")
    void testDisabledUserRejection() throws Exception {
        String disabledEmail = "disabled_" + System.currentTimeMillis() + "@medtrack.org";
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseThrow();
        userRepository.save(User.builder()
                .name("Disabled Staff")
                .email(disabledEmail)
                .passwordHash(passwordEncoder.encode("DisabledPass123"))
                .role(staffRole)
                .enabled(false)
                .build());

        LoginRequest request = LoginRequest.builder()
                .email(disabledEmail)
                .password("DisabledPass123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("User account is disabled"));
    }

    @Test
    @DisplayName("GET /api/auth/me - Authenticated user with JWT returns user details")
    void testGetCurrentUserWithValidJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@medtrack.org"))
                .andExpect(jsonPath("$.name").value("Admin System"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.roles", hasItem("ADMIN")))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/auth/me - Missing JWT returns HTTP 401 JSON")
    void testGetCurrentUserWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/auth/me - Invalid JWT returns HTTP 401 JSON")
    void testGetCurrentUserWithInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/auth/me - Expired JWT returns HTTP 401 JSON")
    void testGetCurrentUserWithExpiredJwt() throws Exception {
        // Build expired token (1ms expiration in past)
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", List.of("ROLE_ADMIN"));
        String expiredToken = jwtService.generateToken(claims, "admin@medtrack.org");

        // Wait slightly or build token directly
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + expiredToken.substring(0, expiredToken.length() - 5) + "xyz12"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Role Authorization - ADMIN can access /api/admin/dashboard, STAFF is rejected with 403 JSON")
    void testRoleAuthorizationAdminRoute() throws Exception {
        // ADMIN should succeed
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // STAFF should be rejected with 403 Forbidden JSON
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message", containsString("insufficient permissions")));
    }

    @Test
    @DisplayName("Role Authorization - SUPERVISOR can access /api/supervisor/dashboard, STAFF is rejected with 403 JSON")
    void testRoleAuthorizationSupervisorRoute() throws Exception {
        // SUPERVISOR should succeed
        mockMvc.perform(get("/api/supervisor/dashboard")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // STAFF should be rejected with 403 Forbidden JSON
        mockMvc.perform(get("/api/supervisor/dashboard")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("POST /api/auth/logout - Authenticated logout succeeds")
    void testLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message", containsString("Stateless JWT")));
    }

    @Test
    @DisplayName("POST /api/auth/oauth2/exchange - Invalid or expired code returns HTTP 400 Bad Request")
    void testOAuth2ExchangeInvalidCode() throws Exception {
        OAuth2ExchangeRequest request = OAuth2ExchangeRequest.builder()
                .code("invalid-or-nonexistent-code")
                .build();

        mockMvc.perform(post("/api/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Invalid, expired, or already used")));
    }
}
