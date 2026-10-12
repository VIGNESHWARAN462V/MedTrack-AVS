package com.medtrack.avs.controller;

import com.medtrack.avs.dto.RoomCreateRequest;
import com.medtrack.avs.dto.RoomResponse;
import com.medtrack.avs.dto.RoomUpdateRequest;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.service.RoomService;
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
public class RoomController {

    private final RoomService roomService;
    private final UserRepository userRepository;

    @GetMapping("/api/floors/{floorId}/rooms")
    public ResponseEntity<List<RoomResponse>> getRoomsByFloor(
            @PathVariable Long floorId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<RoomResponse> responses = roomService.getRoomsByFloorId(floorId, activeOnly);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/api/rooms/{roomId}")
    public ResponseEntity<RoomResponse> getRoomById(@PathVariable Long roomId) {
        return ResponseEntity.ok(roomService.getRoomById(roomId));
    }

    @PostMapping({"/api/admin/floors/{floorId}/rooms", "/api/floors/{floorId}/rooms"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> createRoom(
            @PathVariable Long floorId,
            @Valid @RequestBody RoomCreateRequest request) {
        User currentUser = getCurrentUser();
        RoomResponse response = roomService.createRoom(floorId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping({"/api/admin/rooms/{roomId}", "/api/rooms/{roomId}"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable Long roomId,
            @Valid @RequestBody RoomUpdateRequest request) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(roomService.updateRoom(roomId, request, currentUser));
    }

    @DeleteMapping({"/api/admin/rooms/{roomId}", "/api/rooms/{roomId}"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long roomId) {
        User currentUser = getCurrentUser();
        roomService.deleteRoom(roomId, currentUser);
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
