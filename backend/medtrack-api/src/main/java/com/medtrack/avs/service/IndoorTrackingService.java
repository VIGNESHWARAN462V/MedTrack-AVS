package com.medtrack.avs.service;

import com.medtrack.avs.dto.FloorAssetResponse;
import com.medtrack.avs.dto.IndoorLocationUpdateRequest;
import com.medtrack.avs.dto.IndoorTrackingEvent;
import com.medtrack.avs.dto.QrCheckInRequest;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.event.IndoorTrackingPersistedEvent;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.mapper.AssetMapper;
import com.medtrack.avs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndoorTrackingService {

    private final AssetRepository assetRepository;
    private final HospitalRepository hospitalRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Retrieve all assets assigned to a specific building floor.
     */
    @Transactional(readOnly = true)
    public List<FloorAssetResponse> getAssetsByFloorId(Long floorId) {
        if (!floorRepository.existsById(floorId)) {
            throw new ResourceNotFoundException("Floor not found with id: " + floorId);
        }

        List<Asset> assets = assetRepository.findByFloorIdWithHierarchy(floorId);
        return assets.stream()
                .map(this::mapToFloorAssetResponse)
                .collect(Collectors.toList());
    }

    /**
     * Configure or update an asset's indoor location (Admin/Authorized).
     */
    @Transactional
    public FloorAssetResponse updateIndoorLocation(Long assetId, IndoorLocationUpdateRequest request, User currentUser) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + assetId));

        Hospital hospital = validateHospital(request.getHospitalId());
        Building building = validateBuilding(request.getBuildingId(), hospital);
        Floor floor = validateFloor(request.getFloorId(), building);
        Room room = null;
        if (request.getRoomId() != null) {
            room = validateRoom(request.getRoomId(), floor);
            validatePointInsideRoom(request.getIndoorX(), request.getIndoorY(), room);
        }

        IndoorLocationSource source = request.getIndoorLocationSource() != null
                ? request.getIndoorLocationSource()
                : IndoorLocationSource.MANUAL_CONFIG;

        Instant now = Instant.now();

        // Update asset indoor location properties without modifying outdoor GPS coordinates
        asset.setHospital(hospital);
        asset.setBuilding(building);
        asset.setFloor(floor);
        asset.setRoom(room);
        asset.setIndoorX(request.getIndoorX());
        asset.setIndoorY(request.getIndoorY());
        asset.setIndoorLocationSource(source);
        asset.setIndoorUpdatedAt(now);

        Asset savedAsset = assetRepository.save(asset);

        // Audit log
        String desc = String.format("Asset %s configured at (%.2f%%, %.2f%%) on Floor %s%s via %s",
                asset.getAssetCode(),
                request.getIndoorX(), request.getIndoorY(),
                floor.getDisplayName(),
                room != null ? " in Room " + room.getRoomNumber() : "",
                source);
        auditService.logAssetEvent(currentUser, "INDOOR_LOCATION_CONFIGURED", asset.getId(), desc);

        // Publish event for WebSocket broadcast
        FloorAssetResponse response = mapToFloorAssetResponse(savedAsset);
        publishIndoorTrackingEvent("INDOOR_LOCATION_UPDATED", savedAsset, response.getLocationStatus());

        return response;
    }

    /**
     * Check in an asset to an indoor hospital, building, floor, and optional room via QR code scan.
     */
    @Transactional
    public FloorAssetResponse checkInAssetViaQr(Long assetId, QrCheckInRequest request, User currentUser) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + assetId));

        Hospital hospital = validateHospital(request.getHospitalId());
        Building building = validateBuilding(request.getBuildingId(), hospital);
        Floor floor = validateFloor(request.getFloorId(), building);
        Room room = null;

        Double targetX = request.getIndoorX();
        Double targetY = request.getIndoorY();

        if (request.getRoomId() != null) {
            room = validateRoom(request.getRoomId(), floor);

            // If coordinates not specified in QR check-in, place at room center
            if (targetX == null || targetY == null) {
                targetX = room.getXPosition() + (room.getWidth() / 2.0);
                targetY = room.getYPosition() + (room.getHeight() / 2.0);
            } else {
                validatePointInsideRoom(targetX, targetY, room);
            }
        } else {
            // Room is optional, but if no room then coordinates must be provided
            if (targetX == null || targetY == null) {
                targetX = 50.0; // Default canvas center
                targetY = 50.0;
            }
        }

        Instant now = Instant.now();

        asset.setHospital(hospital);
        asset.setBuilding(building);
        asset.setFloor(floor);
        asset.setRoom(room);
        asset.setIndoorX(targetX);
        asset.setIndoorY(targetY);
        asset.setIndoorLocationSource(IndoorLocationSource.QR_CHECKIN);
        asset.setIndoorUpdatedAt(now);

        Asset savedAsset = assetRepository.save(asset);

        String desc = String.format("Asset %s checked in via QR to %s > %s > %s%s at (%.2f%%, %.2f%%)",
                asset.getAssetCode(),
                hospital.getName(), building.getName(), floor.getDisplayName(),
                room != null ? " (" + room.getRoomNumber() + ")" : "",
                targetX, targetY);
        auditService.logAssetEvent(currentUser, "QR_CHECKIN_POSITIONED", asset.getId(), desc);

        FloorAssetResponse response = mapToFloorAssetResponse(savedAsset);
        publishIndoorTrackingEvent("QR_CHECKIN_UPDATED", savedAsset, response.getLocationStatus());

        return response;
    }

    // --- Validation Helpers ---

    private Hospital validateHospital(Long hospitalId) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found with id: " + hospitalId));
        if (!hospital.isActive()) {
            throw new BadRequestException("Hospital " + hospital.getName() + " is currently inactive");
        }
        return hospital;
    }

    private Building validateBuilding(Long buildingId, Hospital hospital) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + buildingId));
        if (!building.getHospital().getId().equals(hospital.getId())) {
            throw new BadRequestException("Building " + building.getCode() + " does not belong to hospital " + hospital.getCode());
        }
        if (!building.isActive()) {
            throw new BadRequestException("Building " + building.getName() + " is currently inactive");
        }
        return building;
    }

    private Floor validateFloor(Long floorId, Building building) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found with id: " + floorId));
        if (!floor.getBuilding().getId().equals(building.getId())) {
            throw new BadRequestException("Floor " + floor.getDisplayName() + " does not belong to building " + building.getCode());
        }
        if (!floor.isActive()) {
            throw new BadRequestException("Floor " + floor.getDisplayName() + " is currently inactive");
        }
        return floor;
    }

    private Room validateRoom(Long roomId, Floor floor) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));
        if (!room.getFloor().getId().equals(floor.getId())) {
            throw new BadRequestException("Room " + room.getRoomNumber() + " does not belong to floor " + floor.getDisplayName());
        }
        if (!room.isActive()) {
            throw new BadRequestException("Room " + room.getRoomNumber() + " is currently inactive");
        }
        return room;
    }

    private void validatePointInsideRoom(Double x, Double y, Room room) {
        if (x == null || y == null) {
            throw new BadRequestException("Indoor X and Y coordinates are required when room is specified");
        }

        Double rx = room.getXPosition();
        Double ry = room.getYPosition();
        Double rw = room.getWidth();
        Double rh = room.getHeight();

        if (rx != null && ry != null && rw != null && rh != null) {
            // Tolerant boundary check (inclusive borders)
            boolean inside = (x >= rx - 0.001) && (x <= rx + rw + 0.001) &&
                             (y >= ry - 0.001) && (y <= ry + rh + 0.001);

            if (!inside) {
                throw new BadRequestException(String.format(
                        "Indoor point (%.2f%%, %.2f%%) falls outside assigned room %s bounds [X: %.2f%%-%.2f%%, Y: %.2f%%-%.2f%%]",
                        x, y, room.getRoomNumber(), rx, rx + rw, ry, ry + rh));
            }
        }
    }

    private void publishIndoorTrackingEvent(String eventType, Asset asset, String locationStatus) {
        IndoorTrackingEvent event = IndoorTrackingEvent.builder()
                .eventType(eventType)
                .assetId(asset.getId())
                .assetCode(asset.getAssetCode())
                .assetName(asset.getAssetName())
                .category(asset.getCategory())
                .status(asset.getStatus())
                .hospitalId(asset.getHospital() != null ? asset.getHospital().getId() : null)
                .hospitalName(asset.getHospital() != null ? asset.getHospital().getName() : null)
                .buildingId(asset.getBuilding() != null ? asset.getBuilding().getId() : null)
                .buildingName(asset.getBuilding() != null ? asset.getBuilding().getName() : null)
                .floorId(asset.getFloor() != null ? asset.getFloor().getId() : null)
                .floorDisplayName(asset.getFloor() != null ? asset.getFloor().getDisplayName() : null)
                .floorNumber(asset.getFloor() != null ? asset.getFloor().getFloorNumber() : null)
                .roomId(asset.getRoom() != null ? asset.getRoom().getId() : null)
                .roomName(asset.getRoom() != null ? asset.getRoom().getName() : null)
                .roomNumber(asset.getRoom() != null ? asset.getRoom().getRoomNumber() : null)
                .indoorX(asset.getIndoorX())
                .indoorY(asset.getIndoorY())
                .indoorLocationSource(asset.getIndoorLocationSource())
                .indoorUpdatedAt(asset.getIndoorUpdatedAt())
                .locationStatus(locationStatus)
                .departmentId(asset.getDepartment() != null ? asset.getDepartment().getId() : null)
                .departmentName(asset.getDepartment() != null ? asset.getDepartment().getName() : null)
                .departmentCode(asset.getDepartment() != null ? asset.getDepartment().getCode() : null)
                .build();

        eventPublisher.publishEvent(new IndoorTrackingPersistedEvent(this, event));
    }

    public FloorAssetResponse mapToFloorAssetResponse(Asset asset) {
        String locationStatus = AssetMapper.resolveLocationStatus(asset);

        return FloorAssetResponse.builder()
                .id(asset.getId())
                .assetCode(asset.getAssetCode())
                .assetName(asset.getAssetName())
                .category(asset.getCategory())
                .serialNumber(asset.getSerialNumber())
                .status(asset.getStatus())
                .hospitalId(asset.getHospital() != null ? asset.getHospital().getId() : null)
                .hospitalName(asset.getHospital() != null ? asset.getHospital().getName() : null)
                .buildingId(asset.getBuilding() != null ? asset.getBuilding().getId() : null)
                .buildingName(asset.getBuilding() != null ? asset.getBuilding().getName() : null)
                .floorId(asset.getFloor() != null ? asset.getFloor().getId() : null)
                .floorDisplayName(asset.getFloor() != null ? asset.getFloor().getDisplayName() : null)
                .floorNumber(asset.getFloor() != null ? asset.getFloor().getFloorNumber() : null)
                .roomId(asset.getRoom() != null ? asset.getRoom().getId() : null)
                .roomName(asset.getRoom() != null ? asset.getRoom().getName() : null)
                .roomNumber(asset.getRoom() != null ? asset.getRoom().getRoomNumber() : null)
                .indoorX(asset.getIndoorX())
                .indoorY(asset.getIndoorY())
                .indoorLocationSource(asset.getIndoorLocationSource())
                .indoorUpdatedAt(asset.getIndoorUpdatedAt())
                .locationStatus(locationStatus)
                .departmentId(asset.getDepartment() != null ? asset.getDepartment().getId() : null)
                .departmentName(asset.getDepartment() != null ? asset.getDepartment().getName() : null)
                .departmentCode(asset.getDepartment() != null ? asset.getDepartment().getCode() : null)
                .outdoorLatitude(asset.getLatitude())
                .outdoorLongitude(asset.getLongitude())
                .outdoorGpsAccuracy(asset.getGpsAccuracy())
                .outdoorLastSeen(asset.getLastSeen())
                .batteryLevel(asset.getBatteryLevel())
                .qrCode(asset.getQrCode())
                .build();
    }
}
