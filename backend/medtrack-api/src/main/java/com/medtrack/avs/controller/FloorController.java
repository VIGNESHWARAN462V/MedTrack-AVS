package com.medtrack.avs.controller;

import com.medtrack.avs.dto.FloorCreateRequest;
import com.medtrack.avs.dto.FloorResponse;
import com.medtrack.avs.dto.FloorUpdateRequest;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.service.FloorService;
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
public class FloorController {

    private final FloorService floorService;
    private final com.medtrack.avs.service.FloorPlanImageService floorPlanImageService;
    private final UserRepository userRepository;

    @GetMapping("/api/buildings/{buildingId}/floors")
    public ResponseEntity<List<FloorResponse>> getFloorsByBuilding(
            @PathVariable Long buildingId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<FloorResponse> responses = floorService.getFloorsByBuildingId(buildingId, activeOnly);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/api/floors/{floorId}")
    public ResponseEntity<FloorResponse> getFloorById(@PathVariable Long floorId) {
        return ResponseEntity.ok(floorService.getFloorById(floorId));
    }

    @PostMapping({"/api/admin/buildings/{buildingId}/floors", "/api/buildings/{buildingId}/floors"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FloorResponse> createFloor(
            @PathVariable Long buildingId,
            @Valid @RequestBody FloorCreateRequest request) {
        User currentUser = getCurrentUser();
        FloorResponse response = floorService.createFloor(buildingId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping({"/api/admin/floors/{floorId}", "/api/floors/{floorId}"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FloorResponse> updateFloor(
            @PathVariable Long floorId,
            @Valid @RequestBody FloorUpdateRequest request) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(floorService.updateFloor(floorId, request, currentUser));
    }

    @DeleteMapping({"/api/admin/floors/{floorId}", "/api/floors/{floorId}"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteFloor(@PathVariable Long floorId) {
        User currentUser = getCurrentUser();
        floorService.deleteFloor(floorId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = {"/api/admin/floors/{floorId}/floor-plan-image", "/api/floors/{floorId}/floor-plan-image"},
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.medtrack.avs.dto.FloorPlanUploadResponse> uploadFloorPlanImage(
            @PathVariable Long floorId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        User currentUser = getCurrentUser();
        com.medtrack.avs.dto.FloorPlanUploadResponse response = floorPlanImageService.uploadFloorPlanImage(floorId, file, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/floors/{floorId}/floor-plan-image")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getFloorPlanImage(@PathVariable Long floorId) {
        com.medtrack.avs.entity.FloorPlanImage image = floorPlanImageService.getFloorPlanImage(floorId);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(image.getContentType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + image.getFileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .cacheControl(org.springframework.http.CacheControl.noCache().cachePrivate().mustRevalidate())
                .body(image.getImageData());
    }

    @DeleteMapping({"/api/admin/floors/{floorId}/floor-plan-image", "/api/floors/{floorId}/floor-plan-image"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteFloorPlanImage(@PathVariable Long floorId) {
        User currentUser = getCurrentUser();
        floorPlanImageService.deleteFloorPlanImage(floorId, currentUser);
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
