package com.medtrack.avs.security;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Token Bucket rate limiter for abuse prevention and traffic control (Phase 20).
 * Protects authentication, OAuth exchange, transfers, and QR lookups while providing safe,
 * high-capacity throughput for 5-second GPS telemetry.
 */
@Service
@Slf4j
public class RateLimiterService {

    public enum Policy {
        AUTH_LOGIN(10.0, 10.0 / 60.0),            // 10 requests / min
        AUTH_REGISTER(5.0, 5.0 / 60.0),           // 5 requests / min
        AUTH_OAUTH_EXCHANGE(10.0, 10.0 / 60.0),   // 10 requests / min
        GPS_TELEMETRY(30.0, 120.0 / 60.0),        // 120 requests / min (2.0/s, supports 10 concurrent 5s GPS devices)
        QR_LOOKUP(20.0, 60.0 / 60.0),             // 60 requests / min (1.0/s)
        TRANSFER_MUTATION(15.0, 30.0 / 60.0),     // 30 requests / min (0.5/s)
        AI_CHAT(12.0, 20.0 / 60.0),               // 20 requests / min (upstream cost & abuse protection)
        DEFAULT_API(40.0, 120.0 / 60.0);          // 120 requests / min fallback

        @Getter
        private final double capacity;
        @Getter
        private final double refillRatePerSecond;

        Policy(double capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
        }
    }

    @Getter
    public static class RateLimitResult {
        private final boolean allowed;
        private final long retryAfterSeconds;

        private RateLimitResult(boolean allowed, long retryAfterSeconds) {
            this.allowed = allowed;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public static RateLimitResult allowed() {
            return new RateLimitResult(true, 0);
        }

        public static RateLimitResult rejected(long retryAfterSeconds) {
            return new RateLimitResult(false, Math.max(1, retryAfterSeconds));
        }
    }

    private static class TokenBucket {
        private double tokens;
        private long lastRefillTimestampMs;
        private final double capacity;
        private final double refillRatePerSecond;

        public TokenBucket(Policy policy) {
            this.capacity = policy.getCapacity();
            this.refillRatePerSecond = policy.getRefillRatePerSecond();
            this.tokens = this.capacity;
            this.lastRefillTimestampMs = System.currentTimeMillis();
        }

        public synchronized RateLimitResult tryConsume(double tokensRequested) {
            refill();
            if (tokens >= tokensRequested) {
                tokens -= tokensRequested;
                return RateLimitResult.allowed();
            } else {
                double deficit = tokensRequested - tokens;
                long retryAfter = (long) Math.ceil(deficit / refillRatePerSecond);
                return RateLimitResult.rejected(retryAfter);
            }
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double elapsedSeconds = (now - lastRefillTimestampMs) / 1000.0;
            if (elapsedSeconds > 0) {
                tokens = Math.min(capacity, tokens + (elapsedSeconds * refillRatePerSecond));
                lastRefillTimestampMs = now;
            }
        }
    }

    // Key format: "POLICY:CLIENT_IP"
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Resolve traffic policy based on HTTP method and path.
     */
    public Policy resolvePolicy(String method, String uri) {
        if ("POST".equalsIgnoreCase(method)) {
            if ("/api/auth/login".equalsIgnoreCase(uri)) {
                return Policy.AUTH_LOGIN;
            }
            if ("/api/auth/register".equalsIgnoreCase(uri)) {
                return Policy.AUTH_REGISTER;
            }
            if ("/api/auth/oauth2/exchange".equalsIgnoreCase(uri)) {
                return Policy.AUTH_OAUTH_EXCHANGE;
            }
            if ("/api/tracking/location".equalsIgnoreCase(uri)) {
                return Policy.GPS_TELEMETRY;
            }
            if ("/api/ai/chat".equalsIgnoreCase(uri)) {
                return Policy.AI_CHAT;
            }
            if (uri != null && uri.startsWith("/api/transfers")) {
                return Policy.TRANSFER_MUTATION;
            }
        } else if ("GET".equalsIgnoreCase(method)) {
            if (uri != null && uri.startsWith("/api/assets/qr/")) {
                return Policy.QR_LOOKUP;
            }
        }
        return Policy.DEFAULT_API;
    }

    /**
     * Check rate limit for a client IP and request endpoint.
     */
    public RateLimitResult checkLimit(String clientIp, String method, String uri) {
        Policy policy = resolvePolicy(method, uri);
        String bucketKey = policy.name() + ":" + (clientIp != null ? clientIp : "UNKNOWN");

        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k -> new TokenBucket(policy));
        RateLimitResult result = bucket.tryConsume(1.0);

        if (!result.isAllowed()) {
            log.warn("[RateLimit Exceeded] ip={} method={} uri={} policy={} retryAfter={}s",
                    clientIp, method, uri, policy.name(), result.getRetryAfterSeconds());
        }

        return result;
    }

    /**
     * Reset all rate limit buckets (useful for test isolation).
     */
    public void reset() {
        buckets.clear();
    }
}
