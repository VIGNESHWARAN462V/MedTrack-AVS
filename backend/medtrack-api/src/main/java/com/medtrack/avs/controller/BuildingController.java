package com.medtrack.avs.controller;

import com.medtrack.avs.dto.BuildingCreateRequest;
import com.medtrack.avs.dto.BuildingResponse;
import com.medtrack.avs.dto.BuildingUpdateRequest;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.service.BuildingService;
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
@RequiredArgsConstructor
@Slf4j
public class BuildingController {

    private final BuildingService buildingService;
    private final UserRepository userRepository;

    @GetMapping("/api/hospitals/{hospitalId}/buildings")
    public ResponseEntity<List<BuildingResponse>> getBuildingsByHospital(
            @PathVariable Long hospitalId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<BuildingResponse> responses = buildingService.getBuildingsByHospitalId(hospitalId, activeOnly);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/api/buildings/{buildingId}")
    public ResponseEntity<BuildingResponse> getBuildingById(@PathVariable Long buildingId) {
        return ResponseEntity.ok(buildingService.getBuildingById(buildingId));
    }

    @PostMapping({"/api/admin/hospitals/{hospitalId}/buildings", "/api/hospitals/{hospitalId}/buildings"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BuildingResponse> createBuilding(
            @PathVariable Long hospitalId,
            @Valid @RequestBody BuildingCreateRequest request) {
        User currentUser = getCurrentUser();
        BuildingResponse response = buildingService.createBuilding(hospitalId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping({"/api/admin/buildings/{buildingId}", "/api/buildings/{buildingId}"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BuildingResponse> updateBuilding(
            @PathVariable Long buildingId,
            @Valid @RequestBody BuildingUpdateRequest request) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(buildingService.updateBuilding(buildingId, request, currentUser));
    }

    @DeleteMapping({"/api/admin/buildings/{buildingId}", "/api/buildings/{buildingId}"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBuilding(@PathVariable Long buildingId) {
        User currentUser = getCurrentUser();
        buildingService.deleteBuilding(buildingId, currentUser);
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("User not found with email: " + email));
    }
}
