package com.medtrack.avs.controller;

import com.medtrack.avs.dto.GeofenceCreateRequest;
import com.medtrack.avs.dto.GeofenceResponse;
import com.medtrack.avs.dto.GeofenceUpdateRequest;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.service.GeofenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geofences")
@RequiredArgsConstructor
@Slf4j
public class GeofenceController {

    private final GeofenceService geofenceService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<GeofenceResponse>> getAllGeofences(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<GeofenceResponse> responses = activeOnly
                ? geofenceService.getActiveGeofences()
                : geofenceService.getAllGeofences();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GeofenceResponse> getGeofenceById(@PathVariable Long id) {
        return ResponseEntity.ok(geofenceService.getGeofenceById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<GeofenceResponse> createGeofence(
            @Valid @RequestBody GeofenceCreateRequest request) {
        User currentUser = getCurrentUser();
        GeofenceResponse response = geofenceService.createGeofence(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<GeofenceResponse> updateGeofence(
            @PathVariable Long id,
            @Valid @RequestBody GeofenceUpdateRequest request) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(geofenceService.updateGeofence(id, request, currentUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteGeofence(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        geofenceService.deleteGeofence(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Full authentication is required to access geofence endpoints");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("User record not found for principal: " + auth.getName()));
    }
}
