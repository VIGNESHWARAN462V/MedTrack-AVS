package com.medtrack.avs.controller;

import com.medtrack.avs.dto.AiChatRequest;
import com.medtrack.avs.dto.AiChatResponse;
import com.medtrack.avs.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Controller providing authenticated endpoints for the AI Operations Assistant (Phase 20.1).
 * Strictly requires authentication and rejects unauthenticated callers.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiService aiService;

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            Principal principal) {

        String username = userDetails != null ? userDetails.getUsername()
                : (principal != null ? principal.getName() : "anonymous");

        log.info("[AI] Received operational question from user '{}', question length={}",
                username, request.getQuestion().length());

        AiChatResponse response = aiService.chat(request, username);
        return ResponseEntity.ok(response);
    }
}
