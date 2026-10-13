package com.medtrack.avs.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medtrack.avs.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Filter that applies rate limiting and traffic control to API requests (Phase 20).
 * Rejects requests exceeding policy limits with HTTP 429 Too Many Requests,
 * Retry-After header, and a structured ErrorResponse JSON payload.
 *
 * Client IP Resolution Security (Harden Phase):
 * - For Railway public ingress, prefers the edge-managed 'X-Real-IP' header, which is
 *   sanitized and set by Railway's public edge router.
 * - Disregards untrusted caller-supplied 'X-Forwarded-For' headers to eliminate rate-limit
 *   evasion and victim bucket exhaustion via header spoofing.
 * - Validates chosen IP strictly against literal IPv4 and IPv6 syntax (rejecting hostnames
 *   without triggering blocking DNS resolutions).
 * - Implements a deterministic fallback to request.getRemoteAddr() (or 'UNKNOWN') if
 *   X-Real-IP is absent or malformed (e.g. during local development or internal mesh calls).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])\\." +
            "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])\\." +
            "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])\\." +
            "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])$"
    );

    private static final Pattern IPV6_HEX4 = Pattern.compile("^[0-9a-fA-F]{1,4}$");

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Rate limiting applies to /api/** endpoints
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        String method = request.getMethod();
        String uri = request.getRequestURI();

        RateLimiterService.RateLimitResult result = rateLimiterService.checkLimit(clientIp, method, uri);

        if (result.isAllowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Rate limit exceeded: return HTTP 429 JSON response
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message("Rate limit exceeded. Please retry after " + result.getRetryAfterSeconds() + " seconds.")
                .path(uri)
                .build();

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    /**
     * Resolves client IP securely:
     * 1. Prefers Railway edge-managed X-Real-IP.
     * 2. Rejects caller-controlled X-Forwarded-For to prevent spoofing bypass.
     * 3. Validates chosen IP as a literal IPv4 or IPv6 address.
     * 4. Safely falls back to socket peer remoteAddr (or "UNKNOWN").
     */
    public String resolveClientIp(HttpServletRequest request) {
        // 1. Prefer Railway-provided X-Real-IP header from public edge
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            String normalizedRealIp = normalizeIp(realIp);
            if (isValidIp(normalizedRealIp)) {
                return normalizedRealIp;
            }
            log.warn("[RateLimit Security] Malformed X-Real-IP header ignored: '{}'", realIp);
        }

        // 2. Safe fallback: peer socket remote address (never trust client-controlled X-Forwarded-For)
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.trim().isEmpty()) {
            String normalizedRemote = normalizeIp(remoteAddr);
            if (isValidIp(normalizedRemote)) {
                return normalizedRemote;
            }
        }

        // 3. Fallback constant for unresolvable client IP
        return "UNKNOWN";
    }

    static String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String trimmed = ip.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() > 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return isValidIpv4(ip) || isValidIpv6(ip);
    }

    static boolean isValidIpv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }

    static boolean isValidIpv6(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        if (ip.startsWith("[") && ip.endsWith("]") && ip.length() > 2) {
            ip = ip.substring(1, ip.length() - 1).trim();
        }
        // Characters must be hex digits, colon, or dot (for ipv4-mapped)
        for (int i = 0; i < ip.length(); i++) {
            char c = ip.charAt(i);
            if (!((c >= '0' && c <= '9') ||
                  (c >= 'a' && c <= 'f') ||
                  (c >= 'A' && c <= 'F') ||
                  c == ':' || c == '.')) {
                return false;
            }
        }
        // Must contain at least two colons
        int colonCount = 0;
        for (int i = 0; i < ip.length(); i++) {
            if (ip.charAt(i) == ':') {
                colonCount++;
            }
        }
        if (colonCount < 2) {
            return false;
        }

        // Check for double colon "::"
        int doubleColonIndex = ip.indexOf("::");
        if (doubleColonIndex != -1) {
            // Cannot have multiple "::"
            if (ip.indexOf("::", doubleColonIndex + 2) != -1) {
                return false;
            }
            // Cannot have ":::"
            if (ip.contains(":::")) {
                return false;
            }
        }

        // Cannot start or end with a single ":" (only allowed if part of "::")
        if ((ip.startsWith(":") && !ip.startsWith("::")) ||
            (ip.endsWith(":") && !ip.endsWith("::"))) {
            return false;
        }

        // Handle IPv4-mapped IPv6 suffix like "::ffff:192.168.1.1"
        int lastColon = ip.lastIndexOf(':');
        String trailing = ip.substring(lastColon + 1);
        if (trailing.contains(".")) {
            if (!isValidIpv4(trailing)) {
                return false;
            }
            // Replace trailing IPv4 with two 16-bit hex words for group counting
            ip = ip.substring(0, lastColon) + ":0:0";
            doubleColonIndex = ip.indexOf("::");
        }

        String[] parts = ip.split(":", -1);
        int nonEmptyParts = 0;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!IPV6_HEX4.matcher(part).matches()) {
                return false;
            }
            nonEmptyParts++;
        }

        if (doubleColonIndex != -1) {
            return nonEmptyParts <= 7;
        } else {
            return nonEmptyParts == 8 && parts.length == 8;
        }
    }
}
