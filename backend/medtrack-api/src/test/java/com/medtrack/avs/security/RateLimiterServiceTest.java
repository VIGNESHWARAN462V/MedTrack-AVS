package com.medtrack.avs.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
        rateLimiterService.reset();
    }

    @Test
    @DisplayName("GET /api/health resolves to HEALTH_CHECK policy")
    void testResolvePolicy_getHealth_resolvesToHealthCheck() {
        RateLimiterService.Policy policy = rateLimiterService.resolvePolicy("GET", "/api/health");
        assertEquals(RateLimiterService.Policy.HEALTH_CHECK, policy);
        assertEquals(10.0, policy.getCapacity(), 0.001);
        assertEquals(1.0, policy.getRefillRatePerSecond(), 0.001);
    }

    @Test
    @DisplayName("Non-GET methods on /api/health fall back to DEFAULT_API")
    void testResolvePolicy_nonGetHealth_resolvesToDefaultApi() {
        assertEquals(RateLimiterService.Policy.DEFAULT_API, rateLimiterService.resolvePolicy("POST", "/api/health"));
        assertEquals(RateLimiterService.Policy.DEFAULT_API, rateLimiterService.resolvePolicy("PUT", "/api/health"));
        assertEquals(RateLimiterService.Policy.DEFAULT_API, rateLimiterService.resolvePolicy("DELETE", "/api/health"));
        assertEquals(RateLimiterService.Policy.DEFAULT_API, rateLimiterService.resolvePolicy("PATCH", "/api/health"));
    }

    @Test
    @DisplayName("Sub-paths or prefixes like /api/health/admin or /api/healthcheck do not match HEALTH_CHECK")
    void testResolvePolicy_unrelatedEndpoints_doNotMatchHealthCheck() {
        assertEquals(RateLimiterService.Policy.DEFAULT_API, rateLimiterService.resolvePolicy("GET", "/api/health/admin"));
        assertEquals(RateLimiterService.Policy.DEFAULT_API, rateLimiterService.resolvePolicy("GET", "/api/healthcheck"));
        assertEquals(RateLimiterService.Policy.DEFAULT_API, rateLimiterService.resolvePolicy("GET", "/api/hospitals"));
        assertEquals(RateLimiterService.Policy.DEFAULT_API, rateLimiterService.resolvePolicy("GET", "/api/buildings/1/floors"));
    }

    @Test
    @DisplayName("Existing policy routes remain untouched")
    void testResolvePolicy_existingPoliciesUnchanged() {
        assertEquals(RateLimiterService.Policy.AUTH_LOGIN, rateLimiterService.resolvePolicy("POST", "/api/auth/login"));
        assertEquals(RateLimiterService.Policy.AUTH_REGISTER, rateLimiterService.resolvePolicy("POST", "/api/auth/register"));
        assertEquals(RateLimiterService.Policy.AUTH_OAUTH_EXCHANGE, rateLimiterService.resolvePolicy("POST", "/api/auth/oauth2/exchange"));
        assertEquals(RateLimiterService.Policy.GPS_TELEMETRY, rateLimiterService.resolvePolicy("POST", "/api/tracking/location"));
        assertEquals(RateLimiterService.Policy.AI_CHAT, rateLimiterService.resolvePolicy("POST", "/api/ai/chat"));
        assertEquals(RateLimiterService.Policy.TRANSFER_MUTATION, rateLimiterService.resolvePolicy("POST", "/api/transfers/request"));
        assertEquals(RateLimiterService.Policy.QR_LOOKUP, rateLimiterService.resolvePolicy("GET", "/api/assets/qr/MED-100"));
    }

    @Test
    @DisplayName("Exceeding HEALTH_CHECK limit returns allowed=false with Retry-After")
    void testHealthCheck_exceedingCapacityRejects() {
        String testIp = "10.0.0.1";

        // First 10 requests should succeed
        for (int i = 0; i < 10; i++) {
            RateLimiterService.RateLimitResult result = rateLimiterService.checkLimit(testIp, "GET", "/api/health");
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
        }

        // 11th request must be rejected
        RateLimiterService.RateLimitResult rejected = rateLimiterService.checkLimit(testIp, "GET", "/api/health");
        assertFalse(rejected.isAllowed());
        assertTrue(rejected.getRetryAfterSeconds() >= 1, "Retry-After should be at least 1 second");
    }

    @Test
    @DisplayName("Rapid health checks do not starve normal API requests sharing the same client IP")
    void testHealthCheck_doesNotStarveDefaultApi() {
        String sharedIp = "10.0.0.2";

        // Exhaust all 10 HEALTH_CHECK tokens
        for (int i = 0; i < 10; i++) {
            rateLimiterService.checkLimit(sharedIp, "GET", "/api/health");
        }

        // 11th health request is blocked
        RateLimiterService.RateLimitResult healthCheck11 = rateLimiterService.checkLimit(sharedIp, "GET", "/api/health");
        assertFalse(healthCheck11.isAllowed());

        // DEFAULT_API request from same IP has independent bucket and must succeed
        RateLimiterService.RateLimitResult defaultApiResult = rateLimiterService.checkLimit(sharedIp, "GET", "/api/hospitals");
        assertTrue(defaultApiResult.isAllowed(), "DEFAULT_API should not be starved by health-check activity");

        // Verify DEFAULT_API has its full quota intact (39 more requests should succeed)
        for (int i = 0; i < 39; i++) {
            assertTrue(rateLimiterService.checkLimit(sharedIp, "GET", "/api/hospitals").isAllowed(),
                    "DEFAULT_API request " + (i + 2) + " should be allowed");
        }
        // 41st request to DEFAULT_API should be rejected
        assertFalse(rateLimiterService.checkLimit(sharedIp, "GET", "/api/hospitals").isAllowed(),
                "41st request to DEFAULT_API should exceed its capacity of 40");
    }

    @Test
    @DisplayName("IPv4 validation accepts valid IPv4 literals and rejects invalid/malformed strings")
    void testIpValidation_ipv4() {
        assertTrue(RateLimitingFilter.isValidIpv4("192.168.1.1"));
        assertTrue(RateLimitingFilter.isValidIpv4("10.0.0.1"));
        assertTrue(RateLimitingFilter.isValidIpv4("127.0.0.1"));
        assertTrue(RateLimitingFilter.isValidIpv4("0.0.0.0"));
        assertTrue(RateLimitingFilter.isValidIpv4("255.255.255.255"));

        assertFalse(RateLimitingFilter.isValidIpv4("256.0.0.1"));
        assertFalse(RateLimitingFilter.isValidIpv4("192.168.1"));
        assertFalse(RateLimitingFilter.isValidIpv4("192.168.1.1.1"));
        assertFalse(RateLimitingFilter.isValidIpv4("192.168.01.1"));
        assertFalse(RateLimitingFilter.isValidIpv4("192.168.1.1:8080"));
        assertFalse(RateLimitingFilter.isValidIpv4("192.168.1.1/24"));
        assertFalse(RateLimitingFilter.isValidIpv4("abc"));
        assertFalse(RateLimitingFilter.isValidIpv4(""));
        assertFalse(RateLimitingFilter.isValidIpv4(null));
    }

    @Test
    @DisplayName("IPv6 validation accepts valid IPv6 literals and rejects invalid strings")
    void testIpValidation_ipv6() {
        assertTrue(RateLimitingFilter.isValidIpv6("::1"));
        assertTrue(RateLimitingFilter.isValidIpv6("::"));
        assertTrue(RateLimitingFilter.isValidIpv6("2001:db8::1"));
        assertTrue(RateLimitingFilter.isValidIpv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
        assertTrue(RateLimitingFilter.isValidIpv6("fe80::1ff:fe23:4567:890a"));
        assertTrue(RateLimitingFilter.isValidIpv6("0:0:0:0:0:0:0:1"));
        assertTrue(RateLimitingFilter.isValidIpv6("[::1]"));
        assertTrue(RateLimitingFilter.isValidIpv6("[2001:db8::1]"));
        assertTrue(RateLimitingFilter.isValidIpv6("::ffff:192.168.1.1"));

        assertFalse(RateLimitingFilter.isValidIpv6(":::"));
        assertFalse(RateLimitingFilter.isValidIpv6("2001::db8::1"));
        assertFalse(RateLimitingFilter.isValidIpv6("12345::1"));
        assertFalse(RateLimitingFilter.isValidIpv6("2001:db8:1"));
        assertFalse(RateLimitingFilter.isValidIpv6(":2001:db8::1"));
        assertFalse(RateLimitingFilter.isValidIpv6("2001:db8::1:"));
        assertFalse(RateLimitingFilter.isValidIpv6("localhost"));
        assertFalse(RateLimitingFilter.isValidIpv6("attacker.com"));
        assertFalse(RateLimitingFilter.isValidIpv6("fe80::1%eth0"));
        assertFalse(RateLimitingFilter.isValidIpv6(""));
        assertFalse(RateLimitingFilter.isValidIpv6(null));
    }

    @Test
    @DisplayName("resolveClientIp prefers valid X-Real-IP and ignores forged X-Forwarded-For")
    void testResolveClientIp_prefersXRealIp() {
        RateLimitingFilter filter = new RateLimitingFilter(rateLimiterService, null);
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("X-Real-IP", "203.0.113.195");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.50");
        request.setRemoteAddr("127.0.0.1");

        assertEquals("203.0.113.195", filter.resolveClientIp(request));
    }

    @Test
    @DisplayName("resolveClientIp ignores X-Forwarded-For and falls back to remoteAddr when X-Real-IP is absent")
    void testResolveClientIp_ignoresXForwardedForWhenXRealIpMissing() {
        RateLimitingFilter filter = new RateLimitingFilter(rateLimiterService, null);
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.50");
        request.setRemoteAddr("192.168.1.20");

        assertEquals("192.168.1.20", filter.resolveClientIp(request));
    }

    @Test
    @DisplayName("resolveClientIp falls back to remoteAddr when X-Real-IP is malformed")
    void testResolveClientIp_fallsBackWhenXRealIpMalformed() {
        RateLimitingFilter filter = new RateLimitingFilter(rateLimiterService, null);
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("X-Real-IP", "malicious-hostname.com");
        request.addHeader("X-Forwarded-For", "10.0.0.1");
        request.setRemoteAddr("192.168.1.30");

        assertEquals("192.168.1.30", filter.resolveClientIp(request));
    }

    @Test
    @DisplayName("resolveClientIp returns UNKNOWN when both X-Real-IP and remoteAddr are missing or invalid")
    void testResolveClientIp_returnsUnknownWhenNoValidIpFound() {
        RateLimitingFilter filter = new RateLimitingFilter(rateLimiterService, null);
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("X-Real-IP", "not-an-ip");
        request.setRemoteAddr("also-invalid");

        assertEquals("UNKNOWN", filter.resolveClientIp(request));
    }
}
