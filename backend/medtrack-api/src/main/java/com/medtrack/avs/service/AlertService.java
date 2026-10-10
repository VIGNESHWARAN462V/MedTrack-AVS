package com.medtrack.avs.service;

import com.medtrack.avs.dto.AlertResponse;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.repository.AlertRepository;
import com.medtrack.avs.repository.AssetAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(Boolean resolved, Long assetId, AlertSeverity severity, User currentUser) {
        List<Alert> alerts;

        if (assetId != null) {
            checkAssetAccess(assetId, currentUser);
            alerts = alertRepository.findByAssetIdOrderByOccurredAtDesc(assetId);
        } else if (Boolean.FALSE.equals(resolved)) {
            alerts = alertRepository.findByResolvedFalseOrderByOccurredAtDesc();
        } else if (severity != null) {
            alerts = alertRepository.findBySeverityOrderByOccurredAtDesc(severity);
        } else {
            alerts = alertRepository.findAllByOrderByOccurredAtDesc();
        }

        // Filter for STAFF: only show alerts for assigned assets if not admin/supervisor
        if (currentUser.getRole().getName() == RoleType.STAFF) {
            List<Long> assignedAssetIds = assetAssignmentRepository.findByAssignedUserId(currentUser.getId()).stream()
                    .map(a -> a.getAsset().getId())
                    .collect(Collectors.toList());
            alerts = alerts.stream()
                    .filter(a -> assignedAssetIds.contains(a.getAsset().getId()))
                    .collect(Collectors.toList());
        }

        return alerts.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlertById(Long id, User currentUser) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + id));

        checkAssetAccess(alert.getAsset().getId(), currentUser);
        return mapToResponse(alert);
    }

    @Transactional
    public AlertResponse resolveAlert(Long id, User currentUser) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + id));

        checkAssetAccess(alert.getAsset().getId(), currentUser);

        alert.setResolved(true);
        alert.setResolvedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        Alert saved = alertRepository.save(alert);
        log.info("Alert id={} resolved by user {}", id, currentUser.getEmail());

        return mapToResponse(saved);
    }

    private void checkAssetAccess(Long assetId, User currentUser) {
        if (currentUser.getRole().getName() == RoleType.ADMIN || currentUser.getRole().getName() == RoleType.SUPERVISOR) {
            return;
        }
        boolean isAssigned = assetAssignmentRepository.existsByAssetIdAndAssignedUserIdAndActiveTrue(assetId, currentUser.getId());
        if (!isAssigned) {
            throw new AccessDeniedException("Access denied: you are not assigned to this asset's alerts");
        }
    }

    public AlertResponse mapToResponse(Alert a) {
        String deptName = null;
        Long deptId = null;
        if (a.getGeofence() != null && a.getGeofence().getDepartment() != null) {
            deptName = a.getGeofence().getDepartment().getName();
            deptId = a.getGeofence().getDepartment().getId();
        } else if (a.getAsset() != null && a.getAsset().getDepartment() != null) {
            deptName = a.getAsset().getDepartment().getName();
            deptId = a.getAsset().getDepartment().getId();
        }

        return AlertResponse.builder()
                .id(a.getId())
                .assetId(a.getAsset().getId())
                .assetCode(a.getAsset().getAssetCode())
                .assetName(a.getAsset().getAssetName())
                .geofenceId(a.getGeofence() != null ? a.getGeofence().getId() : null)
                .geofenceName(a.getGeofence() != null ? a.getGeofence().getName() : null)
                .departmentId(deptId)
                .departmentName(deptName)
                .type(a.getType())
                .severity(a.getSeverity())
                .message(a.getMessage())
                .latitude(a.getLatitude())
                .longitude(a.getLongitude())
                .distanceMeters(a.getDistanceMeters())
                .resolved(a.isResolved())
                .occurredAt(a.getOccurredAt())
                .createdAt(a.getCreatedAt())
                .resolvedAt(a.getResolvedAt())
                .build();
    }
}
