package com.medtrack.avs.service.ai.model;

/**
 * Classifies the caller's operational intent (Phase 20.2).
 */
public enum AiIntent {
    LOOKUP_ASSET,
    GET_LATEST_LOCATION,
    GET_TRACKING_STATUS,
    GET_RECENT_ALERTS,
    GET_ACTIVE_TRANSFERS,
    GET_ANALYTICS_METRIC,
    GET_DEPARTMENT_BALANCE,
    ACTION_REQUEST,      // Mutation attempt (delete, update, start tracking, approve, etc.)
    PROMPT_INJECTION,    // Attempt to bypass instructions or dump secrets
    UNSUPPORTED          // General question unrelated to hospital operations
}
