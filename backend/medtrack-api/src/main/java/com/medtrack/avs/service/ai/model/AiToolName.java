package com.medtrack.avs.service.ai.model;

/**
 * Strict allowlist of read-only AI tools supported by MedTrack - AVS (Phase 20.2).
 */
public enum AiToolName {
    LOOKUP_ASSET("lookup_asset"),
    GET_LATEST_LOCATION("get_latest_location"),
    GET_TRACKING_STATUS("get_tracking_status"),
    GET_RECENT_ALERTS("get_recent_alerts"),
    GET_ACTIVE_TRANSFERS("get_active_transfers"),
    GET_ANALYTICS_METRIC("get_analytics_metric"),
    GET_DEPARTMENT_BALANCE("get_department_balance");

    private final String toolKey;

    AiToolName(String toolKey) {
        this.toolKey = toolKey;
    }

    public String getToolKey() {
        return toolKey;
    }
}
