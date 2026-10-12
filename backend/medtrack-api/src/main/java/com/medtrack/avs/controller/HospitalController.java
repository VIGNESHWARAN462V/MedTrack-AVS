package com.medtrack.avs.controller;

import com.medtrack.avs.dto.HospitalCreateRequest;
import com.medtrack.avs.dto.HospitalResponse;
import com.medtrack.avs.dto.HospitalUpdateRequest;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.service.HospitalService;
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
@RequestMapping("/api/hospitals")
@RequiredArgsConstructor
@Slf4j
public class HospitalController {

    private final HospitalService hospitalService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<HospitalResponse>> getAllHospitals(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<HospitalResponse> responses = hospitalService.getAllHospitals(activeOnly);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HospitalResponse> getHospitalById(@PathVariable Long id) {
        return ResponseEntity.ok(hospitalService.getHospitalById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HospitalResponse> createHospital(
            @Valid @RequestBody HospitalCreateRequest request) {
        User currentUser = getCurrentUser();
        HospitalResponse response = hospitalService.createHospital(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HospitalResponse> updateHospital(
            @PathVariable Long id,
            @Valid @RequestBody HospitalUpdateRequest request) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(hospitalService.updateHospital(id, request, currentUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteHospital(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        hospitalService.deleteHospital(id, currentUser);
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
