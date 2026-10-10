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
     * Model identifier (e.g. gemini-3.8-flash, gpt-4o-mini).
     */
    private String model = "gemini-3.8-flash";

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
