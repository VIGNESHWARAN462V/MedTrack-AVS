package com.medtrack.avs.service;

import com.medtrack.avs.dto.FloorPlanUploadResponse;
import com.medtrack.avs.entity.Floor;
import com.medtrack.avs.entity.FloorPlanImage;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.repository.FloorPlanImageRepository;
import com.medtrack.avs.repository.FloorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FloorPlanImageService {

    public static final long MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024L; // 15 MB
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg"
    );

    // PNG magic signature (8 bytes)
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    // JPEG SOI marker (3 bytes)
    private static final byte[] JPEG_SIGNATURE = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    };

    private final FloorPlanImageRepository floorPlanImageRepository;
    private final FloorRepository floorRepository;
    private final AuditService auditService;

    /**
     * Securely upload, validate, and persist an architectural floor plan image.
     */
    @Transactional
    public FloorPlanUploadResponse uploadFloorPlanImage(Long floorId, MultipartFile file, User currentUser) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found with id: " + floorId));

        if (!floor.isActive()) {
            throw new BadRequestException("Cannot upload floor plan for an inactive floor (id: " + floorId + ")");
        }

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Floor plan file is missing or empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File size exceeds maximum allowed limit of 15 MB (Actual: "
                    + (file.getSize() / (1024 * 1024)) + " MB)");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("Original filename cannot be empty");
        }

        String extension = extractExtension(originalFilename).toLowerCase();
        if ("svg".equalsIgnoreCase(extension)) {
            throw new BadRequestException("SVG uploads are not permitted for security reasons. Architectural floor plans must be uploaded as PNG or JPEG images.");
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported image format '." + extension
                    + "'. Supported formats for architectural floor plans: PNG and JPEG (.jpg, .jpeg).");
        }

        String rawContentType = file.getContentType();
        if (rawContentType != null && !rawContentType.isBlank()) {
            String lowerType = rawContentType.toLowerCase();
            if (lowerType.contains("svg") || lowerType.contains("html") || lowerType.contains("xml") || lowerType.contains("javascript")) {
                throw new BadRequestException("MIME type '" + rawContentType + "' is not permitted. Only PNG and JPEG images are allowed.");
            }
            if (!ALLOWED_CONTENT_TYPES.contains(lowerType) && !lowerType.equals("application/octet-stream")) {
                throw new BadRequestException("Unsupported MIME type '" + rawContentType + "'. Only image/png and image/jpeg are supported.");
            }
        }
        String normalizedContentType = normalizeContentType(rawContentType, extension);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes for floor id: {}", floorId, e);
            throw new BadRequestException("Failed to read image stream: " + e.getMessage());
        }

        // Validate content against magic bytes and security policies
        validateContentSecurity(bytes, extension, normalizedContentType);

        // Detect or extract dimensions
        int[] dimensions = extractDimensions(bytes, extension);
        int detectedWidth = dimensions[0];
        int detectedHeight = dimensions[1];

        // Safe server-controlled storage filename
        String safeStorageName = "floor_" + floorId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "." + extension;
        String checksum = computeSha256(bytes);

        // Save or update existing FloorPlanImage entity
        FloorPlanImage floorPlanImage = floorPlanImageRepository.findByFloorId(floorId)
                .orElseGet(() -> FloorPlanImage.builder().floor(floor).build());

        floorPlanImage.setFileName(safeStorageName);
        floorPlanImage.setOriginalFileName(sanitizeOriginalFilename(originalFilename));
        floorPlanImage.setContentType(normalizedContentType);
        floorPlanImage.setFileSize((long) bytes.length);
        floorPlanImage.setWidth(detectedWidth);
        floorPlanImage.setHeight(detectedHeight);
        floorPlanImage.setSha256Checksum(checksum);
        floorPlanImage.setImageData(bytes);

        FloorPlanImage savedImage = floorPlanImageRepository.save(floorPlanImage);

        // Update Floor record with managed API URL reference and verified dimensions
        String managedImageUrl = "/api/floors/" + floorId + "/floor-plan-image";
        floor.setFloorPlanImageUrl(managedImageUrl);
        floor.setFloorPlanWidth(detectedWidth);
        floor.setFloorPlanHeight(detectedHeight);
        floorRepository.save(floor);

        log.info("Floor plan uploaded successfully: floorId={}, dimensions={}x{}, size={} bytes, checksum={}",
                floorId, detectedWidth, detectedHeight, bytes.length, checksum);

        auditService.logFacilityEvent(
                currentUser,
                "UPLOAD_FLOOR_PLAN_IMAGE",
                "FLOOR",
                floorId,
                String.format("Uploaded architectural floor plan '%s' (%dx%d, %d bytes) for floor %s",
                        originalFilename, detectedWidth, detectedHeight, bytes.length, floor.getDisplayName())
        );

        return FloorPlanUploadResponse.builder()
                .floorId(floorId)
                .fileName(safeStorageName)
                .originalFileName(originalFilename)
                .contentType(normalizedContentType)
                .fileSize((long) bytes.length)
                .width(detectedWidth)
                .height(detectedHeight)
                .imageUrl(managedImageUrl)
                .message("Floor plan image uploaded and validated successfully")
                .uploadedAt(savedImage.getUpdatedAt() != null ? savedImage.getUpdatedAt() : Instant.now())
                .build();
    }

    /**
     * Retrieve the persistent floor plan image binary for a floor.
     */
    @Transactional(readOnly = true)
    public FloorPlanImage getFloorPlanImage(Long floorId) {
        if (!floorRepository.existsById(floorId)) {
            throw new ResourceNotFoundException("Floor not found with id: " + floorId);
        }

        return floorPlanImageRepository.findByFloorId(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("No floor plan image found for floor id: " + floorId));
    }

    /**
     * Delete an architectural floor plan image and reset floor metadata cleanly.
     */
    @Transactional
    public void deleteFloorPlanImage(Long floorId, User currentUser) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found with id: " + floorId));

        if (floorPlanImageRepository.existsByFloorId(floorId)) {
            floorPlanImageRepository.deleteByFloorId(floorId);
        }

        floor.setFloorPlanImageUrl(null);
        floorRepository.save(floor);

        log.info("Floor plan image removed for floorId: {}", floorId);

        auditService.logFacilityEvent(
                currentUser,
                "DELETE_FLOOR_PLAN_IMAGE",
                "FLOOR",
                floorId,
                "Removed floor plan image reference from floor " + floor.getDisplayName()
        );
    }

    private void validateContentSecurity(byte[] bytes, String extension, String contentType) {
        if (bytes.length < 12) {
            throw new BadRequestException("Uploaded file is too small to be a valid image");
        }

        switch (extension) {
            case "png":
                if (!matchesSignature(bytes, PNG_SIGNATURE)) {
                    throw new BadRequestException("File header does not match valid PNG magic bytes");
                }
                break;

            case "jpg":
            case "jpeg":
                if (!matchesSignature(bytes, JPEG_SIGNATURE)) {
                    throw new BadRequestException("File header does not match valid JPEG magic bytes");
                }
                break;

            default:
                throw new BadRequestException("Unsupported image format '." + extension + "'");
        }
    }

    private int[] extractDimensions(byte[] bytes, String extension) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new BadRequestException("Unable to decode raster image dimensions. Corrupted or invalid image file.");
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException e) {
            log.error("Failed to decode image dimensions for extension {}: {}", extension, e.getMessage());
            throw new BadRequestException("Failed to decode image dimensions: " + e.getMessage());
        }
    }

    private boolean matchesSignature(byte[] data, byte[] signature) {
        if (data.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) return false;
        }
        return true;
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }

    private String normalizeContentType(String rawContentType, String extension) {
        if (rawContentType != null && ALLOWED_CONTENT_TYPES.contains(rawContentType.toLowerCase())) {
            return rawContentType.toLowerCase();
        }
        switch (extension) {
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            default: return "application/octet-stream";
        }
    }

    private String sanitizeOriginalFilename(String originalFilename) {
        if (originalFilename == null) return "floor-plan";
        return originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
