package com.medtrack.avs.service;

import com.medtrack.avs.dto.FloorCreateRequest;
import com.medtrack.avs.dto.FloorResponse;
import com.medtrack.avs.dto.FloorUpdateRequest;
import com.medtrack.avs.entity.Building;
import com.medtrack.avs.entity.Floor;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.repository.BuildingRepository;
import com.medtrack.avs.repository.FloorRepository;
import com.medtrack.avs.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FloorService {

    private final FloorRepository floorRepository;
    private final BuildingRepository buildingRepository;
    private final RoomRepository roomRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<FloorResponse> getFloorsByBuildingId(Long buildingId, boolean activeOnly) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new ResourceNotFoundException("Building not found with id: " + buildingId);
        }

        List<Floor> floors = activeOnly
                ? floorRepository.findByBuildingIdAndActiveTrueOrderByFloorNumberAsc(buildingId)
                : floorRepository.findByBuildingIdOrderByFloorNumberAsc(buildingId);

        return floors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FloorResponse getFloorById(Long floorId) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found with id: " + floorId));
        return mapToResponse(floor);
    }

    @Transactional
    public FloorResponse createFloor(Long buildingId, FloorCreateRequest request, User currentUser) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + buildingId));

        if (!building.isActive()) {
            throw new BadRequestException("Cannot add floor to an inactive building (id: " + buildingId + ")");
        }

        if (floorRepository.existsByBuildingIdAndFloorNumber(buildingId, request.getFloorNumber())) {
            throw new BadRequestException("Floor number " + request.getFloorNumber() + " already exists in building: " + building.getName());
        }

        validateDimensions(request.getFloorPlanWidth(), request.getFloorPlanHeight());

        Floor floor = Floor.builder()
                .building(building)
                .floorNumber(request.getFloorNumber())
                .displayName(request.getDisplayName().trim())
                .floorPlanImageUrl(request.getFloorPlanImageUrl() != null ? request.getFloorPlanImageUrl().trim() : null)
                .floorPlanWidth(request.getFloorPlanWidth())
                .floorPlanHeight(request.getFloorPlanHeight())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Floor saved = floorRepository.save(floor);
        log.info("Floor created: id={}, buildingId={}, floorNumber={}, displayName={}",
                saved.getId(), buildingId, saved.getFloorNumber(), saved.getDisplayName());

        auditService.logFacilityEvent(
                currentUser,
                "CREATE_FLOOR",
                "FLOOR",
                saved.getId(),
                "Created floor " + saved.getDisplayName() + " (Level " + saved.getFloorNumber() + ") in building " + building.getName()
        );

        return mapToResponse(saved);
    }

    @Transactional
    public FloorResponse updateFloor(Long floorId, FloorUpdateRequest request, User currentUser) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found with id: " + floorId));

        validateDimensions(request.getFloorPlanWidth(), request.getFloorPlanHeight());

        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            floor.setDisplayName(request.getDisplayName().trim());
        }

        if (request.getFloorPlanImageUrl() != null) {
            floor.setFloorPlanImageUrl(request.getFloorPlanImageUrl().trim());
        }

        if (request.getFloorPlanWidth() != null) {
            floor.setFloorPlanWidth(request.getFloorPlanWidth());
        }

        if (request.getFloorPlanHeight() != null) {
            floor.setFloorPlanHeight(request.getFloorPlanHeight());
        }

        if (request.getActive() != null) {
            floor.setActive(request.getActive());
        }

        Floor updated = floorRepository.save(floor);
        log.info("Floor updated: id={}, floorNumber={}, displayName={}",
                updated.getId(), updated.getFloorNumber(), updated.getDisplayName());

        auditService.logFacilityEvent(
                currentUser,
                "UPDATE_FLOOR",
                "FLOOR",
                updated.getId(),
                "Updated floor " + updated.getDisplayName() + " (Level " + updated.getFloorNumber() + ")"
        );

        return mapToResponse(updated);
    }

    @Transactional
    public void deleteFloor(Long floorId, User currentUser) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found with id: " + floorId));

        // Soft deactivation
        floor.setActive(false);
        floorRepository.save(floor);
        log.info("Floor deactivated: id={}, displayName={}", floor.getId(), floor.getDisplayName());

        auditService.logFacilityEvent(
                currentUser,
                "DEACTIVATE_FLOOR",
                "FLOOR",
                floor.getId(),
                "Deactivated floor " + floor.getDisplayName() + " (Level " + floor.getFloorNumber() + ")"
        );
    }

    private void validateDimensions(Integer width, Integer height) {
        if (width != null && width <= 0) {
            throw new BadRequestException("Floor plan width must be a positive integer");
        }
        if (height != null && height <= 0) {
            throw new BadRequestException("Floor plan height must be a positive integer");
        }
    }

    public FloorResponse mapToResponse(Floor floor) {
        long roomCount = roomRepository.countByFloorIdAndActiveTrue(floor.getId());
        return FloorResponse.builder()
                .id(floor.getId())
                .buildingId(floor.getBuilding().getId())
                .buildingName(floor.getBuilding().getName())
                .floorNumber(floor.getFloorNumber())
                .displayName(floor.getDisplayName())
                .floorPlanImageUrl(floor.getFloorPlanImageUrl())
                .floorPlanWidth(floor.getFloorPlanWidth())
                .floorPlanHeight(floor.getFloorPlanHeight())
                .active(floor.isActive())
                .roomCount(roomCount)
                .createdAt(floor.getCreatedAt())
                .updatedAt(floor.getUpdatedAt())
                .build();
    }
}
