package com.medtrack.avs.controller;

import com.medtrack.avs.dto.FloorAssetResponse;
import com.medtrack.avs.dto.IndoorLocationUpdateRequest;
import com.medtrack.avs.dto.QrCheckInRequest;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.service.IndoorTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class IndoorTrackingController {

    private final IndoorTrackingService indoorTrackingService;
    private final UserRepository userRepository;

    /**
     * GET /api/floors/{floorId}/assets
     * Retrieve all assets whose recorded indoor positions belong to this floor.
     */
    @GetMapping("/api/floors/{floorId}/assets")
    public ResponseEntity<List<FloorAssetResponse>> getAssetsByFloor(@PathVariable Long floorId) {
        List<FloorAssetResponse> assets = indoorTrackingService.getAssetsByFloorId(floorId);
        return ResponseEntity.ok(assets);
    }

    /**
     * PATCH /api/assets/{assetId}/indoor-location
     * Manually configure or update an asset's indoor position (ADMIN only).
     */
    @PatchMapping({"/api/admin/assets/{assetId}/indoor-location", "/api/assets/{assetId}/indoor-location"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FloorAssetResponse> updateIndoorLocation(
            @PathVariable Long assetId,
            @Valid @RequestBody IndoorLocationUpdateRequest request) {
        User currentUser = getCurrentUser();
        FloorAssetResponse response = indoorTrackingService.updateIndoorLocation(assetId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/assets/{assetId}/qr-checkin
     * Check in an asset to an explicitly selected hospital, building, floor and optional room.
     */
    @PostMapping("/api/assets/{assetId}/qr-checkin")
    public ResponseEntity<FloorAssetResponse> checkInAssetViaQr(
            @PathVariable Long assetId,
            @Valid @RequestBody QrCheckInRequest request) {
        User currentUser = getCurrentUser();
        FloorAssetResponse response = indoorTrackingService.checkInAssetViaQr(assetId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Full authentication is required to access indoor tracking endpoints");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("User record not found for principal: " + auth.getName()));
    }
}
