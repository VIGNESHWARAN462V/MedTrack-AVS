package com.medtrack.avs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Server-side configuration for Cloud AI Operations Assistant (Phase 20.1).
 * Credentials are read exclusively from environment variables or secure server properties.
 * Never expose secrets to frontend or clients.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "medtrack.ai")
public class AiProperties {

    /**
     * Provider type: GEMINI, OPENAI, etc. Defaults to GEMINI.
     */
    private String provider = "GEMINI";

    /**
     * Secret API key for the cloud AI provider.
     * Never log, print, or expose this value.
     */
    private String apiKey = "";

    /**
     * Model identifier (e.g. gemini-3.5-flash-lite, gpt-4o-mini).
     */
    private String model = "gemini-3.5-flash-lite";

    /**
     * Optional comma-separated list of fallback models to try if the primary model
     * experiences high demand (HTTP 503), rate limits (HTTP 429), or is unavailable (HTTP 404).
     */
    private String fallbackModels = "";

    /**
     * Maximum retry attempts for transient errors (HTTP 503 / 429).
     * Defaults to 0 to preserve deterministic single-call behavior in unit tests.
     */
    private int maxRetries = 0;

    /**
     * Backoff duration between retries in milliseconds.
     */
    private long retryBackoffMs = 500;

    /**
     * Optional custom base URL for the AI provider.
     */
    private String apiBaseUrl = "";

    /**
     * HTTP connect timeout in milliseconds. Defaults to 5000ms.
     */
    private int connectTimeoutMs = 5000;

    /**
     * HTTP read/response timeout in milliseconds. Defaults to 30000ms for reasoning/thinking models.
     */
    private int readTimeoutMs = 30000;

    /**
     * Returns the ordered candidate models to attempt (primary model followed by fallbacks).
     */
    public java.util.List<String> getCandidateModels() {
        java.util.List<String> candidates = new java.util.ArrayList<>();
        if (StringUtils.hasText(model)) {
            candidates.add(model.trim());
        }
        if (StringUtils.hasText(fallbackModels)) {
            for (String fallback : fallbackModels.split(",")) {
                String trimmed = fallback.trim();
                if (StringUtils.hasText(trimmed) && !candidates.contains(trimmed)) {
                    candidates.add(trimmed);
                }
            }
        }
        if (candidates.isEmpty()) {
            candidates.add("gemini-3.5-flash-lite");
        }
        return candidates;
    }

    /**
     * Determines whether the AI provider is properly configured on the server.
     * Blank keys, empty strings, and template placeholders (e.g. "CHANGE_ME") return false.
     */
    public boolean isConfigured() {
        if (!StringUtils.hasText(apiKey)) {
            return false;
        }
        String trimmed = apiKey.trim();
        return !trimmed.equalsIgnoreCase("placeholder")
                && !trimmed.equalsIgnoreCase("CHANGE_ME")
                && !trimmed.startsWith("CHANGE_ME_")
                && !trimmed.equalsIgnoreCase("your-api-key");
    }
}
