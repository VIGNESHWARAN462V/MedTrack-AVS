package com.medtrack.avs.controller;

import com.medtrack.avs.dto.AlertResponse;
import com.medtrack.avs.entity.AlertSeverity;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Slf4j
public class AlertController {

    private final AlertService alertService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAlerts(
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) AlertSeverity severity) {
        User currentUser = getCurrentUser();
        List<AlertResponse> alerts = alertService.getAlerts(resolved, assetId, severity, currentUser);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> getAlertById(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(alertService.getAlertById(id, currentUser));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<AlertResponse> resolveAlertPut(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(alertService.resolveAlert(id, currentUser));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<AlertResponse> resolveAlertPost(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(alertService.resolveAlert(id, currentUser));
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Full authentication is required to access alert endpoints");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("User record not found for principal: " + auth.getName()));
    }
}
