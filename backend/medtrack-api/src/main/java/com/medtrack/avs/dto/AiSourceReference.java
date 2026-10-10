package com.medtrack.avs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Metadata identifying authoritative operational records utilized by the AI assistant (Phase 20.2).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSourceReference {

    /**
     * User-facing label, e.g. "Asset inventory", "Latest GPS telemetry", "Geofence alerts".
     */
    private String label;

    /**
     * Standardized resource type, e.g. "ASSET", "GPS_LOCATION", "GPS_SESSION", "ALERT", "TRANSFER", "ANALYTICS".
     */
    private String resourceType;

    /**
     * Safe identifier or reference, e.g. "ECG-001", "Alert #4", "Cardiology GUB".
     */
    private String identifier;

    /**
     * Authoritative record timestamp in UTC with microsecond precision.
     */
    private Instant timestamp;

    /**
     * Freshness evaluation: "LIVE", "STALE", "MEASURED", "NO_DATA", "HISTORICAL", "UNRESOLVED".
     */
    private String freshness;
}
