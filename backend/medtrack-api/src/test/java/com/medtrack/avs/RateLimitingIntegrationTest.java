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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                            .header("X-Real-IP", "192.168.1.100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json));
        }

        // 11th request must be rejected with HTTP 429
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Real-IP", "192.168.1.100")
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
                            .header("X-Real-IP", testIp)
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
                    .header("X-Real-IP", testIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json));
        }

        // Must be rate limited
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Real-IP", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isTooManyRequests());

        // Reset
        rateLimiterService.reset();

        // Must now be allowed through (e.g. 401 Unauthorized for bad password, NOT 429)
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Real-IP", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(not(429)));
    }

    @Test
    @DisplayName("GET /api/health allows up to 10 requests and returns 429 with Retry-After on 11th request")
    void testHealthCheckRateLimitingReturns429WithRetryAfter() throws Exception {
        String testIp = "192.168.1.50";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/health")
                            .header("X-Real-IP", testIp)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        // 11th request must be rate limited by HEALTH_CHECK policy (capacity 10)
        mockMvc.perform(get("/api/health")
                        .header("X-Real-IP", testIp)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.path").value("/api/health"))
                .andExpect(jsonPath("$.message", containsString("Rate limit exceeded")));
    }

    @Test
    @DisplayName("Rapid health checks do not starve normal API requests sharing the same client IP")
    void testRapidHealthChecksDoNotStarveNormalApiRequests() throws Exception {
        String testIp = "192.168.1.60";

        // Exhaust all 10 HEALTH_CHECK tokens
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/health")
                            .header("X-Real-IP", testIp)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        // 11th health request is blocked with 429
        mockMvc.perform(get("/api/health")
                        .header("X-Real-IP", testIp)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());

        // Normal API request from the same IP (e.g. /api/assets) reaches security filter and returns 401 Unauthorized, NOT 429
        mockMvc.perform(get("/api/assets")
                        .header("X-Real-IP", testIp)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(status().is(not(429)));

        // Unrelated sub-route /api/health/admin also reaches security filter and returns 401 Unauthorized, NOT 429
        mockMvc.perform(get("/api/health/admin")
                        .header("X-Real-IP", testIp)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(status().is(not(429)));
    }

    @Test
    @DisplayName("Non-GET methods on /api/health do not use HEALTH_CHECK bucket")
    void testNonGetOnHealthEndpointDoesNotUseHealthCheckPolicy() throws Exception {
        String testIp = "192.168.1.70";

        // Exhaust all 10 HEALTH_CHECK tokens
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/health")
                            .header("X-Real-IP", testIp)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        // GET is 429
        mockMvc.perform(get("/api/health")
                        .header("X-Real-IP", testIp)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());

        // POST /api/health is routed to DEFAULT_API (not HEALTH_CHECK), so it is NOT 429 (returns 401 Unauthorized from Spring Security)
        mockMvc.perform(post("/api/health")
                        .header("X-Real-IP", testIp)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(status().is(not(429)));
    }

    @Test
    @DisplayName("Forged X-Forwarded-For header does not override valid X-Real-IP")
    void testForgedXForwardedForDoesNotOverrideXRealIp() throws Exception {
        String edgeIp = "192.168.1.80";
        String spoofedForwarded = "10.0.0.99, 172.16.0.1";

        // Exhaust all 10 HEALTH_CHECK tokens using edge IP
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/health")
                            .header("X-Real-IP", edgeIp)
                            .header("X-Forwarded-For", spoofedForwarded)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        // 11th request with same X-Real-IP is 429 rejected even though X-Forwarded-For is present
        mockMvc.perform(get("/api/health")
                        .header("X-Real-IP", edgeIp)
                        .header("X-Forwarded-For", spoofedForwarded)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("Rotating X-Forwarded-For headers alone cannot allocate a new bucket to evade rate limits")
    void testChangingXForwardedForAloneCannotAllocateNewBucket() throws Exception {
        String edgeIp = "192.168.1.85";

        // Send 10 requests with continuously rotating X-Forwarded-For headers
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/health")
                            .header("X-Real-IP", edgeIp)
                            .header("X-Forwarded-For", "203.0.113." + (i + 1))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        // 11th request with yet another different X-Forwarded-For MUST be rejected with 429
        mockMvc.perform(get("/api/health")
                        .header("X-Real-IP", edgeIp)
                        .header("X-Forwarded-For", "203.0.113.250")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Missing or malformed X-Real-IP follows safe fallback to remote socket address")
    void testMissingOrMalformedXRealIpFallsBackToSafeRemoteAddr() throws Exception {
        // Test malformed X-Real-IP (e.g. hostname, injection attempt)
        // In MockMvc, fallback getRemoteAddr() defaults to 127.0.0.1
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/health")
                            .header("X-Real-IP", "malicious-host.example.com; drop table")
                            .header("X-Forwarded-For", "1.2.3.4")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        // 11th request with malformed header is tracked under fallback bucket (127.0.0.1) and rejected
        mockMvc.perform(get("/api/health")
                        .header("X-Real-IP", "malicious-host.example.com; drop table")
                        .header("X-Forwarded-For", "5.6.7.8")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Valid IPv6 address in X-Real-IP is handled correctly and bounded by rate limits")
    void testIpv6HandlingWithXRealIp() throws Exception {
        String ipv6 = "2001:db8::1";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/health")
                            .header("X-Real-IP", ipv6)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/health")
                        .header("X-Real-IP", ipv6)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
