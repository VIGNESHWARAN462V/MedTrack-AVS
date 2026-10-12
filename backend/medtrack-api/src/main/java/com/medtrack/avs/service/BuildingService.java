package com.medtrack.avs.service;

import com.medtrack.avs.dto.BuildingCreateRequest;
import com.medtrack.avs.dto.BuildingResponse;
import com.medtrack.avs.dto.BuildingUpdateRequest;
import com.medtrack.avs.entity.Building;
import com.medtrack.avs.entity.Hospital;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.repository.BuildingRepository;
import com.medtrack.avs.repository.FloorRepository;
import com.medtrack.avs.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final HospitalRepository hospitalRepository;
    private final FloorRepository floorRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<BuildingResponse> getBuildingsByHospitalId(Long hospitalId, boolean activeOnly) {
        if (!hospitalRepository.existsById(hospitalId)) {
            throw new ResourceNotFoundException("Hospital not found with id: " + hospitalId);
        }

        List<Building> buildings = activeOnly
                ? buildingRepository.findByHospitalIdAndActiveTrue(hospitalId)
                : buildingRepository.findByHospitalId(hospitalId);

        return buildings.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BuildingResponse getBuildingById(Long buildingId) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + buildingId));
        return mapToResponse(building);
    }

    @Transactional
    public BuildingResponse createBuilding(Long hospitalId, BuildingCreateRequest request, User currentUser) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found with id: " + hospitalId));

        if (!hospital.isActive()) {
            throw new BadRequestException("Cannot add building to an inactive hospital (id: " + hospitalId + ")");
        }

        String normalizedCode = request.getCode().trim().toUpperCase();
        if (buildingRepository.existsByHospitalIdAndCode(hospitalId, normalizedCode)) {
            throw new BadRequestException("Building code already exists in hospital: " + request.getCode());
        }

        if (request.getLatitude() != null || request.getLongitude() != null) {
            validateCoordinates(request.getLatitude(), request.getLongitude());
        }

        Building building = Building.builder()
                .hospital(hospital)
                .name(request.getName().trim())
                .code(normalizedCode)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Building saved = buildingRepository.save(building);
        log.info("Building created: id={}, hospitalId={}, code={}, name={}",
                saved.getId(), hospitalId, saved.getCode(), saved.getName());

        auditService.logFacilityEvent(
                currentUser,
                "CREATE_BUILDING",
                "BUILDING",
                saved.getId(),
                "Created building " + saved.getName() + " (" + saved.getCode() + ") in hospital " + hospital.getName()
        );

        return mapToResponse(saved);
    }

    @Transactional
    public BuildingResponse updateBuilding(Long buildingId, BuildingUpdateRequest request, User currentUser) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + buildingId));

        if (request.getLatitude() != null || request.getLongitude() != null) {
            Double lat = request.getLatitude() != null ? request.getLatitude() : building.getLatitude();
            Double lon = request.getLongitude() != null ? request.getLongitude() : building.getLongitude();
            validateCoordinates(lat, lon);
            building.setLatitude(lat);
            building.setLongitude(lon);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            building.setName(request.getName().trim());
        }

        if (request.getDescription() != null) {
            building.setDescription(request.getDescription().trim());
        }

        if (request.getActive() != null) {
            building.setActive(request.getActive());
        }

        Building updated = buildingRepository.save(building);
        log.info("Building updated: id={}, code={}, name={}", updated.getId(), updated.getCode(), updated.getName());

        auditService.logFacilityEvent(
                currentUser,
                "UPDATE_BUILDING",
                "BUILDING",
                updated.getId(),
                "Updated building " + updated.getName() + " (" + updated.getCode() + ")"
        );

        return mapToResponse(updated);
    }

    @Transactional
    public void deleteBuilding(Long buildingId, User currentUser) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + buildingId));

        // Soft deactivation to preserve historical records and relationships
        building.setActive(false);
        buildingRepository.save(building);
        log.info("Building deactivated: id={}, code={}", building.getId(), building.getCode());

        auditService.logFacilityEvent(
                currentUser,
                "DEACTIVATE_BUILDING",
                "BUILDING",
                building.getId(),
                "Deactivated building " + building.getName() + " (" + building.getCode() + ")"
        );
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || latitude < -90.0 || latitude > 90.0) {
            throw new BadRequestException("Building latitude must be between -90.0 and +90.0 degrees");
        }
        if (longitude == null || longitude < -180.0 || longitude > 180.0) {
            throw new BadRequestException("Building longitude must be between -180.0 and +180.0 degrees");
        }
    }

    public BuildingResponse mapToResponse(Building building) {
        long floorCount = floorRepository.countByBuildingIdAndActiveTrue(building.getId());
        return BuildingResponse.builder()
                .id(building.getId())
                .hospitalId(building.getHospital().getId())
                .hospitalName(building.getHospital().getName())
                .name(building.getName())
                .code(building.getCode())
                .description(building.getDescription())
                .latitude(building.getLatitude())
                .longitude(building.getLongitude())
                .active(building.isActive())
                .floorCount(floorCount)
                .createdAt(building.getCreatedAt())
                .updatedAt(building.getUpdatedAt())
                .build();
    }
}
