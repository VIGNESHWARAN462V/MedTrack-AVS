package com.medtrack.avs.controller;

import com.medtrack.avs.dto.*;
import com.medtrack.avs.entity.TransferStatus;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Slf4j
public class TransferController {

    private final TransferService transferService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<TransferResponse> requestTransfer(@Valid @RequestBody TransferCreateRequest request) {
        User currentUser = getCurrentUser();
        TransferResponse response = transferService.requestTransfer(request, currentUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TransferResponse>> getTransfers(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long requestedBy) {
        User currentUser = getCurrentUser();
        List<TransferResponse> transfers = transferService.getTransfers(status, assetId, departmentId, requestedBy, currentUser);
        return ResponseEntity.ok(transfers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransferById(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        TransferResponse transfer = transferService.getTransferById(id, currentUser);
        return ResponseEntity.ok(transfer);
    }

    @GetMapping("/asset/{assetId}/active")
    public ResponseEntity<TransferResponse> getActiveTransferForAsset(@PathVariable Long assetId) {
        Optional<TransferResponse> active = transferService.getActiveTransferForAsset(assetId);
        return active.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<TransferResponse> acceptTransfer(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        TransferResponse response = transferService.acceptTransfer(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<TransferResponse> rejectTransfer(
            @PathVariable Long id,
            @RequestBody(required = false) TransferRejectRequest request) {
        User currentUser = getCurrentUser();
        TransferResponse response = transferService.rejectTransfer(id, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<TransferResponse> startTransfer(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        TransferResponse response = transferService.startTransfer(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<TransferResponse> completeTransfer(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        TransferResponse response = transferService.completeTransfer(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<TransferResponse> cancelTransfer(
            @PathVariable Long id,
            @RequestBody(required = false) TransferCancelRequest request) {
        User currentUser = getCurrentUser();
        TransferResponse response = transferService.cancelTransfer(id, request, currentUser);
        return ResponseEntity.ok(response);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Full authentication is required to access transfer endpoints");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found: " + auth.getName()));
    }
}
