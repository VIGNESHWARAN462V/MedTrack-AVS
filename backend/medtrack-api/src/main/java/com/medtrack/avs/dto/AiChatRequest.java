package com.medtrack.avs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Validated request DTO for the AI Operations Assistant (Phase 20.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    @NotBlank(message = "Question is required and cannot be blank")
    @Size(max = 2000, message = "Question cannot exceed 2000 characters")
    private String question;
}
