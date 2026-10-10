package com.medtrack.avs.controller;

import com.medtrack.avs.dto.AssetCreateRequest;
import com.medtrack.avs.dto.AssetResponse;
import com.medtrack.avs.dto.AssetUpdateRequest;
import com.medtrack.avs.entity.AssetStatus;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.service.AssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    public ResponseEntity<List<AssetResponse>> getAssets(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String category
    ) {
        return ResponseEntity.ok(assetService.getAssets(search, status, departmentId, category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetResponse> getAssetById(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.getAssetById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<AssetResponse> getAssetByCode(@PathVariable String code) {
        return ResponseEntity.ok(assetService.getAssetByCode(code));
    }
    @GetMapping({"/qr/{qrCode:.+}", "/qr"})
    public ResponseEntity<AssetResponse> getAssetByQr(
            @PathVariable(name = "qrCode", required = false) String qrCode,
            @RequestParam(name = "code", required = false) String qrParam
    ) {
        String effectiveQr = (qrCode != null && !qrCode.isEmpty()) ? qrCode : qrParam;
        if (effectiveQr == null || effectiveQr.trim().isEmpty()) {
            throw new BadRequestException("QR code parameter is required and cannot be empty");
        }
        return ResponseEntity.ok(assetService.getAssetByQr(effectiveQr));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<AssetResponse> createAsset(@Valid @RequestBody AssetCreateRequest request) {
        AssetResponse response = assetService.createAsset(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<AssetResponse> updateAsset(
            @PathVariable Long id,
            @Valid @RequestBody AssetUpdateRequest request
    ) {
        return ResponseEntity.ok(assetService.updateAsset(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ResponseEntity.ok(Map.of("message", "Asset deleted successfully"));
    }
}
