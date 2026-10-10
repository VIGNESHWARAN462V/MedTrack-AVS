package com.medtrack.avs.service.ai.model;

import com.medtrack.avs.dto.AiSourceReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Structured outcome of allowlisted tool execution containing sanitized data and source citations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolExecutionResult {

    @Builder.Default
    private List<AiToolName> executedTools = Collections.emptyList();

    /**
     * Sanitized operational data map or structured summary.
     */
    private Object data;

    /**
     * Deterministic textual operational summary grounded in the real retrieved records.
     */
    private String groundedSummary;

    /**
     * List of authoritative citations and timestamps.
     */
    @Builder.Default
    private List<AiSourceReference> sources = Collections.emptyList();

    /**
     * Authoritative reference timestamp.
     */
    private Instant dataAsOf;

    /**
     * Whether operational data was found or empty/missing.
     */
    private boolean hasData;
}
