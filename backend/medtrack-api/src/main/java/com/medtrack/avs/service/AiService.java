package com.medtrack.avs.service;

import com.medtrack.avs.dto.AiChatRequest;
import com.medtrack.avs.dto.AiChatResponse;

/**
 * Service interface for the AI Operations Assistant (Phase 20.1).
 */
public interface AiService {

    /**
     * Checks if the cloud AI provider has been configured with a valid API key.
     *
     * @return true if configured, false otherwise
     */
    boolean isConfigured();

    /**
     * Processes an operational question from an authenticated user.
     *
     * @param request validated chat request
     * @param authenticatedUsername username of the authenticated caller
     * @return structured AI answer response
     */
    AiChatResponse chat(AiChatRequest request, String authenticatedUsername);
}
