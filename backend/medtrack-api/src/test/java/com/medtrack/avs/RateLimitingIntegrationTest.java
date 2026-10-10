package com.medtrack.avs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.dto.LoginRequest;
import com.medtrack.avs.security.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
    }

    @Test
    @DisplayName("POST /api/auth/login returns 429 Too Many Requests with Retry-After when rate limit is exceeded")
    void testRateLimitExceededOnLoginReturns429() throws Exception {
        LoginRequest loginRequest = new LoginRequest("test@hospital.org", "wrongPassword!");
        String json = objectMapper.writeValueAsString(loginRequest);

        // Consume all 10 tokens allowed by AUTH_LOGIN policy
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", "192.168.1.100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json));
        }

        // 11th request must be rejected with HTTP 429
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "192.168.1.100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.message", containsString("Rate limit exceeded")));
    }

    @Test
    @DisplayName("POST /api/tracking/location supports high-throughput 5s GPS telemetry without 429 rejection")
    void testGpsTelemetryHighAllowance() throws Exception {
        String testIp = "192.168.1.150";

        // Simulate 20 rapid telemetry packets from multiple devices on the same gateway
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/tracking/location")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(429)));
        }
    }

    @Test
    @DisplayName("Resetting rate limiter restores access immediately")
    void testResetRestoresAccess() throws Exception {
        LoginRequest loginRequest = new LoginRequest("test@hospital.org", "wrongPassword!");
        String json = objectMapper.writeValueAsString(loginRequest);
        String testIp = "192.168.1.200";

        // Exhaust capacity
        for (int i = 0; i < 11; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", testIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json));
        }

        // Must be rate limited
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isTooManyRequests());

        // Reset
        rateLimiterService.reset();

        // Must now be allowed through (e.g. 401 Unauthorized for bad password, NOT 429)
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(not(429)));
    }
}
