package com.medtrack.avs.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Structured response DTO for the AI Operations Assistant (Phase 20.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    private String answer;
    private String provider;
    private String model;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant answeredAt;

    private boolean cached;

    @Builder.Default
    private java.util.List<AiSourceReference> sources = java.util.Collections.emptyList();

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant dataAsOf;
}
