package com.medtrack.avs.controller;

import com.medtrack.avs.dto.*;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.service.TrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;
    private final UserRepository userRepository;

    /**
     * POST /api/tracking/location
     * Ingest and persist real mobile GPS telemetry.
     */
    @PostMapping("/location")
    public ResponseEntity<LocationTelemetryResponse> recordLocation(
            @Valid @RequestBody LocationTelemetryRequest request) {
        User currentUser = getCurrentUser();
        LocationTelemetryResponse response = trackingService.recordLocation(request, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/tracking/sessions/start
     * Start a new GPS tracking session.
     */
    @PostMapping("/sessions/start")
    public ResponseEntity<GpsSessionResponse> startSession(
            @Valid @RequestBody GpsSessionStartRequest request) {
        User currentUser = getCurrentUser();
        GpsSessionResponse response = trackingService.startSession(request, currentUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * POST /api/tracking/sessions/{id}/stop
     * Stop an active GPS tracking session by session ID.
     */
    @PostMapping("/sessions/{id}/stop")
    public ResponseEntity<GpsSessionResponse> stopSession(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        GpsSessionResponse response = trackingService.stopSession(id, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/tracking/sessions/stop?assetId=1
     * Stop an active GPS tracking session by asset ID.
     */
    @PostMapping("/sessions/stop")
    public ResponseEntity<GpsSessionResponse> stopSessionByAssetId(@RequestParam Long assetId) {
        User currentUser = getCurrentUser();
        GpsSessionResponse response = trackingService.stopSessionByAssetId(assetId, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/tracking/assets/{assetId}/history?limit=50
     * Retrieve recent location history records.
     */
    @GetMapping("/assets/{assetId}/history")
    public ResponseEntity<List<LocationHistoryResponse>> getHistory(
            @PathVariable Long assetId,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false, defaultValue = "desc") String direction) {
        User currentUser = getCurrentUser();
        List<LocationHistoryResponse> history = trackingService.getHistory(assetId, limit, direction, currentUser);
        return ResponseEntity.ok(history);
    }

    /**
     * GET /api/tracking/assets/{assetId}/latest
     * Retrieve the latest known GPS coordinates for an asset.
     */
    @GetMapping("/assets/{assetId}/latest")
    public ResponseEntity<LatestLocationResponse> getLatestLocation(@PathVariable Long assetId) {
        User currentUser = getCurrentUser();
        LatestLocationResponse response = trackingService.getLatestLocation(assetId, currentUser);
        return ResponseEntity.ok(response);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Full authentication is required to access tracking endpoints");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("User record not found for principal: " + auth.getName()));
    }
}
