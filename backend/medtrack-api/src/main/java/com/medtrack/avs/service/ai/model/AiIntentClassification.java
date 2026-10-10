package com.medtrack.avs.service.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Result of intent classification and entity extraction for a user's question (Phase 20.2).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiIntentClassification {

    private AiIntent intent;

    @Builder.Default
    private List<AiToolName> targetTools = Collections.emptyList();

    // Extracted and validated parameters
    private String assetCode;
    private Long departmentId;
    private String departmentNameOrCode;
    private String metricName; // "AMC", "HFI", "GUB"
    private Boolean resolved;
    private String severity;
    private Boolean staleOnly;
    private Integer limit;
    private Instant from;
    private Instant to;

    // Direct response if action request or prompt injection detected
    private String directResponse;
    private boolean requiresDirectRefusal;
}
