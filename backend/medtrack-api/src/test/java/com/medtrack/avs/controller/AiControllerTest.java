package com.medtrack.avs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.dto.AiChatRequest;
import com.medtrack.avs.entity.Role;
import com.medtrack.avs.entity.RoleType;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.RoleRepository;
import com.medtrack.avs.repository.UserRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RateLimiterService rateLimiterService;

    private String staffToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        if (rateLimiterService != null) {
            rateLimiterService.reset();
        }

        Role adminRole = roleRepository.findByName(RoleType.ADMIN).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.ADMIN).description("Admin").build()));
        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.STAFF).description("Staff").build()));

        User adminUser = userRepository.findByEmail("ai-admin@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("AI Admin")
                        .email("ai-admin@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Admin@12345"))
                        .role(adminRole)
                        .enabled(true)
                        .build()));

        User staffUser = userRepository.findByEmail("ai-staff@medtrack.org").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("AI Staff")
                        .email("ai-staff@medtrack.org")
                        .passwordHash(passwordEncoder.encode("Staff@12345"))
                        .role(staffRole)
                        .enabled(true)
                        .build()));

        adminToken = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        staffToken = jwtService.generateToken(new org.springframework.security.core.userdetails.User(
                staffUser.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
    }

    @Test
    @DisplayName("1. Unauthenticated POST /api/ai/chat is rejected with HTTP 401 Unauthorized")
    void testUnauthenticatedRequestIsRejected() throws Exception {
        AiChatRequest request = new AiChatRequest("Where is asset ECG-001?");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2. Blank question is rejected with HTTP 400 Bad Request")
    void testBlankQuestionIsRejected() throws Exception {
        AiChatRequest request = new AiChatRequest("   ");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message", containsString("blank")));
    }

    @Test
    @DisplayName("3. Null question is rejected with HTTP 400 Bad Request")
    void testNullQuestionIsRejected() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("4. Oversized question (> 2000 chars) is rejected with HTTP 400 Bad Request")
    void testOversizedQuestionIsRejected() throws Exception {
        String oversized = "x".repeat(2001);
        AiChatRequest request = new AiChatRequest(oversized);

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message", containsString("2000 characters")));
    }

    @Test
    @DisplayName("5. Unconfigured AI provider returns HTTP 503 Service Unavailable with AI_NOT_CONFIGURED")
    void testUnconfiguredAiProviderReturns503() throws Exception {
        AiChatRequest request = new AiChatRequest("Where is asset ECG-001 currently reported?");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("AI_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.message", containsString("not configured")));
    }

    @Test
    @DisplayName("6. Admin user can also reach endpoint and receives consistent safe response")
    void testAdminUserCanAccessEndpoint() throws Exception {
        AiChatRequest request = new AiChatRequest("Summarize recent geofence alerts.");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("AI_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("7. Error responses do not leak sensitive credentials or internal stack traces")
    void testErrorResponseDoesNotLeakSecrets() throws Exception {
        AiChatRequest request = new AiChatRequest("Explain the current AMC and HFI result.");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", not(containsString("Bearer"))))
                .andExpect(jsonPath("$.message", not(containsString("key"))));
    }

    @Test
    @DisplayName("8. Rapid repeated requests to /api/ai/chat trigger HTTP 429 Too Many Requests rate limit")
    void testAiChatRateLimitTriggered() throws Exception {
        AiChatRequest request = new AiChatRequest("Where is asset ECG-001?");
        boolean got429 = false;

        // Exhaust the 12-token capacity bucket
        for (int i = 0; i < 15; i++) {
            var result = mockMvc.perform(post("/api/ai/chat")
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            if (result.getResponse().getStatus() == 429) {
                got429 = true;
                assertNotNull(result.getResponse().getHeader("Retry-After"));
                assertTrue(result.getResponse().getContentAsString().contains("Rate limit exceeded"));
                break;
            }
        }

        assertTrue(got429, "Expected rate limiter to reject rapid repeated AI requests with HTTP 429");
    }
}
