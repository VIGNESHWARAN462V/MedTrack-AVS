package com.medtrack.avs.service;

import com.medtrack.avs.dto.HospitalCreateRequest;
import com.medtrack.avs.dto.HospitalResponse;
import com.medtrack.avs.dto.HospitalUpdateRequest;
import com.medtrack.avs.entity.Hospital;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ResourceNotFoundException;
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
public class HospitalService {

    private final HospitalRepository hospitalRepository;
    private final com.medtrack.avs.repository.BuildingRepository buildingRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<HospitalResponse> getAllHospitals(boolean activeOnly) {
        List<Hospital> hospitals = activeOnly
                ? hospitalRepository.findByActiveTrue()
                : hospitalRepository.findAll();

        return hospitals.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HospitalResponse getHospitalById(Long id) {
        Hospital hospital = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found with id: " + id));
        return mapToResponse(hospital);
    }

    @Transactional
    public HospitalResponse createHospital(HospitalCreateRequest request, User currentUser) {
        validateCoordinates(request.getLatitude(), request.getLongitude());

        if (hospitalRepository.existsByCode(request.getCode().trim().toUpperCase())) {
            throw new BadRequestException("Hospital code already exists: " + request.getCode());
        }

        Hospital hospital = Hospital.builder()
                .name(request.getName().trim())
                .code(request.getCode().trim().toUpperCase())
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .geofenceRadiusMeters(request.getGeofenceRadiusMeters() != null ? request.getGeofenceRadiusMeters() : 150.0)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Hospital saved = hospitalRepository.save(hospital);
        log.info("Hospital created: id={}, code={}, name={}", saved.getId(), saved.getCode(), saved.getName());

        auditService.logFacilityEvent(
                currentUser,
                "CREATE_HOSPITAL",
                "HOSPITAL",
                saved.getId(),
                "Created hospital " + saved.getName() + " (" + saved.getCode() + ") at [" + saved.getLatitude() + ", " + saved.getLongitude() + "]"
        );

        return mapToResponse(saved);
    }

    @Transactional
    public HospitalResponse updateHospital(Long id, HospitalUpdateRequest request, User currentUser) {
        Hospital hospital = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found with id: " + id));

        if (request.getLatitude() != null || request.getLongitude() != null) {
            Double lat = request.getLatitude() != null ? request.getLatitude() : hospital.getLatitude();
            Double lon = request.getLongitude() != null ? request.getLongitude() : hospital.getLongitude();
            validateCoordinates(lat, lon);
            hospital.setLatitude(lat);
            hospital.setLongitude(lon);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            hospital.setName(request.getName().trim());
        }

        if (request.getAddress() != null) {
            hospital.setAddress(request.getAddress().trim());
        }

        if (request.getGeofenceRadiusMeters() != null) {
            if (request.getGeofenceRadiusMeters() < 1.0 || request.getGeofenceRadiusMeters() > 10000.0) {
                throw new BadRequestException("Geofence radius must be between 1.0 and 10000.0 meters");
            }
            hospital.setGeofenceRadiusMeters(request.getGeofenceRadiusMeters());
        }

        if (request.getActive() != null) {
            hospital.setActive(request.getActive());
        }

        Hospital updated = hospitalRepository.save(hospital);
        log.info("Hospital updated: id={}, code={}, name={}", updated.getId(), updated.getCode(), updated.getName());

        auditService.logFacilityEvent(
                currentUser,
                "UPDATE_HOSPITAL",
                "HOSPITAL",
                updated.getId(),
                "Updated hospital " + updated.getName() + " (" + updated.getCode() + ")"
        );

        return mapToResponse(updated);
    }

    @Transactional
    public void deleteHospital(Long id, User currentUser) {
        Hospital hospital = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found with id: " + id));

        // Soft delete / deactivate to preserve historical referential integrity
        hospital.setActive(false);
        hospitalRepository.save(hospital);
        log.info("Hospital deactivated: id={}, code={}", hospital.getId(), hospital.getCode());

        auditService.logFacilityEvent(
                currentUser,
                "DEACTIVATE_HOSPITAL",
                "HOSPITAL",
                hospital.getId(),
                "Deactivated hospital " + hospital.getName() + " (" + hospital.getCode() + ")"
        );
    }

    public void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || latitude < -90.0 || latitude > 90.0) {
            throw new BadRequestException("Latitude must be between -90.0 and +90.0 degrees");
        }
        if (longitude == null || longitude < -180.0 || longitude > 180.0) {
            throw new BadRequestException("Longitude must be between -180.0 and +180.0 degrees");
        }
    }

    public HospitalResponse mapToResponse(Hospital hospital) {
        return HospitalResponse.builder()
                .id(hospital.getId())
                .name(hospital.getName())
                .code(hospital.getCode())
                .address(hospital.getAddress())
                .latitude(hospital.getLatitude())
                .longitude(hospital.getLongitude())
                .geofenceRadiusMeters(hospital.getGeofenceRadiusMeters())
                .active(hospital.isActive())
                .buildingCount(buildingRepository != null ? buildingRepository.countByHospitalIdAndActiveTrue(hospital.getId()) : 0L)
                .createdAt(hospital.getCreatedAt())
                .updatedAt(hospital.getUpdatedAt())
                .build();
    }
}
