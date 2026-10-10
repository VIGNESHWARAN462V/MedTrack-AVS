package com.medtrack.avs.mapper;

import com.medtrack.avs.dto.AssetCreateRequest;
import com.medtrack.avs.dto.AssetResponse;
import com.medtrack.avs.entity.Asset;
import com.medtrack.avs.entity.AssetStatus;
import org.springframework.stereotype.Component;

@Component
public class AssetMapper {

    public AssetResponse toResponse(Asset entity) {
        if (entity == null) {
            return null;
        }

        return AssetResponse.builder()
                .id(entity.getId())
                .assetCode(entity.getAssetCode())
                .assetName(entity.getAssetName())
                .category(entity.getCategory())
                .serialNumber(entity.getSerialNumber())
                .status(entity.getStatus())
                .departmentId(entity.getDepartment() != null ? entity.getDepartment().getId() : null)
                .departmentName(entity.getDepartment() != null ? entity.getDepartment().getName() : null)
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .gpsAccuracy(entity.getGpsAccuracy())
                .batteryLevel(entity.getBatteryLevel())
                .qrCode(entity.getQrCode())
                .lastSeen(entity.getLastSeen())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public Asset toEntity(AssetCreateRequest request) {
        if (request == null) {
            return null;
        }

        return Asset.builder()
                .assetCode(request.getAssetCode())
                .assetName(request.getAssetName())
                .category(request.getCategory())
                .serialNumber(request.getSerialNumber())
                .status(request.getStatus() != null ? request.getStatus() : AssetStatus.AVAILABLE)
                .batteryLevel(request.getBatteryLevel() != null ? request.getBatteryLevel() : 100)
                .qrCode(request.getQrCode())
                .build();
    }
}

