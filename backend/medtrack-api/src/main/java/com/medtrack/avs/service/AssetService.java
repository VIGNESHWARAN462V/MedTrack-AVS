package com.medtrack.avs.service;

import com.medtrack.avs.dto.AssetCreateRequest;
import com.medtrack.avs.dto.AssetResponse;
import com.medtrack.avs.dto.AssetUpdateRequest;
import com.medtrack.avs.entity.Asset;
import com.medtrack.avs.entity.AssetStatus;
import com.medtrack.avs.entity.Department;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ConflictException;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.mapper.AssetMapper;
import com.medtrack.avs.repository.AssetAssignmentRepository;
import com.medtrack.avs.repository.AssetRepository;
import com.medtrack.avs.repository.AssetTransferRepository;
import com.medtrack.avs.repository.DepartmentRepository;
import com.medtrack.avs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final DepartmentRepository departmentRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final AssetTransferRepository assetTransferRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AssetMapper assetMapper;

    @Transactional(readOnly = true)
    public List<AssetResponse> getAssets(String search, AssetStatus status, Long departmentId, String category) {
        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String cleanCategory = (category != null && !category.trim().isEmpty()) ? category.trim() : null;

        return assetRepository.searchAndFilter(cleanSearch, status, departmentId, cleanCategory).stream()
                .map(assetMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AssetResponse getAssetById(Long id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "id", id));
        return assetMapper.toResponse(asset);
    }

    @Transactional(readOnly = true)
    public AssetResponse getAssetByCode(String assetCode) {
        Asset asset = assetRepository.findByAssetCode(assetCode)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "assetCode", assetCode));
        return assetMapper.toResponse(asset);
    }

    @Transactional
    public AssetResponse getAssetByQr(String qrCode) {
        if (qrCode == null || qrCode.trim().isEmpty()) {
            throw new BadRequestException("QR code must not be empty");
        }
        String cleanQr = qrCode.trim();
        if (cleanQr.contains("%")) {
            try {
                cleanQr = java.net.URLDecoder.decode(cleanQr, java.nio.charset.StandardCharsets.UTF_8).trim();
            } catch (Exception ignored) {
            }
        }
        if (cleanQr.length() > 255) {
            throw new BadRequestException("QR code length exceeds maximum allowable limit of 255 characters");
        }

        // Direct lookup by QR code string
        Optional<Asset> assetOpt = assetRepository.findByQrCode(cleanQr);

        // Fallback: If formatted as MEDTRACK:ASSET:<CODE>, lookup by assetCode
        if (assetOpt.isEmpty() && cleanQr.startsWith("MEDTRACK:ASSET:")) {
            String extractedCode = cleanQr.substring("MEDTRACK:ASSET:".length()).trim();
            if (!extractedCode.isEmpty()) {
                assetOpt = assetRepository.findByAssetCode(extractedCode);
            }
        }

        // Fallback: Direct lookup by assetCode if plain code entered
        if (assetOpt.isEmpty()) {
            assetOpt = assetRepository.findByAssetCode(cleanQr);
        }

        if (assetOpt.isEmpty()) {
            throw new ResourceNotFoundException("Asset not found for QR code: " + cleanQr);
        }

        Asset asset = assetOpt.get();

        // Audit log the QR scan event without interrupting transaction
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = null;
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                currentUser = userRepository.findByEmail(auth.getName()).orElse(null);
            }
            auditService.logAssetEvent(currentUser, "QR_ASSET_SCAN", asset.getId(), "Asset identified via QR scan: " + asset.getAssetCode());
        } catch (Exception ignored) {
        }

        return assetMapper.toResponse(asset);
    }

    @Transactional
    public AssetResponse createAsset(AssetCreateRequest request) {
        if (assetRepository.existsByAssetCode(request.getAssetCode())) {
            throw new ConflictException("Asset with code '" + request.getAssetCode() + "' already exists");
        }
        if (assetRepository.existsBySerialNumber(request.getSerialNumber())) {
            throw new ConflictException("Asset with serial number '" + request.getSerialNumber() + "' already exists");
        }
        if (request.getQrCode() != null && !request.getQrCode().trim().isEmpty() && assetRepository.existsByQrCode(request.getQrCode().trim())) {
            throw new ConflictException("Asset with QR code '" + request.getQrCode() + "' already exists");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.getDepartmentId()));

        Asset asset = assetMapper.toEntity(request);
        asset.setDepartment(department);

        Asset saved = assetRepository.save(asset);
        return assetMapper.toResponse(saved);
    }

    @Transactional
    public AssetResponse updateAsset(Long id, AssetUpdateRequest request) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "id", id));

        if (request.getSerialNumber() != null && !request.getSerialNumber().trim().isEmpty()) {
            String trimmedSerial = request.getSerialNumber().trim();
            if (assetRepository.existsBySerialNumberAndIdNot(trimmedSerial, id)) {
                throw new ConflictException("Asset with serial number '" + trimmedSerial + "' already exists");
            }
            asset.setSerialNumber(trimmedSerial);
        }

        if (request.getQrCode() != null && !request.getQrCode().trim().isEmpty()) {
            String trimmedQr = request.getQrCode().trim();
            if (assetRepository.existsByQrCodeAndIdNot(trimmedQr, id)) {
                throw new ConflictException("Asset with QR code '" + trimmedQr + "' already exists");
            }
            asset.setQrCode(trimmedQr);
        }

        if (request.getAssetName() != null && !request.getAssetName().trim().isEmpty()) {
            asset.setAssetName(request.getAssetName().trim());
        }
        if (request.getCategory() != null && !request.getCategory().trim().isEmpty()) {
            asset.setCategory(request.getCategory().trim());
        }
        if (request.getStatus() != null) {
            asset.setStatus(request.getStatus());
        }
        if (request.getBatteryLevel() != null) {
            asset.setBatteryLevel(request.getBatteryLevel());
        }
        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.getDepartmentId()));
            asset.setDepartment(department);
        }

        // Note: latitude, longitude, gpsAccuracy, lastSeen, id, createdAt are intentionally untouched
        Asset updated = assetRepository.save(asset);
        return assetMapper.toResponse(updated);
    }

    @Transactional
    public void deleteAsset(Long id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "id", id));

        if (assetAssignmentRepository.existsByAssetIdAndActiveTrue(id)) {
            throw new ConflictException("Cannot delete asset '" + asset.getAssetCode() + "' because it currently has an active assignment");
        }

        if (!assetAssignmentRepository.findByAssetId(id).isEmpty() || !assetTransferRepository.findByAssetId(id).isEmpty()) {
            throw new ConflictException("Cannot delete asset '" + asset.getAssetCode() + "' because it is associated with assignment or transfer history");
        }

        assetRepository.delete(asset);
    }
}

