package com.medtrack.avs.service;

import com.medtrack.avs.dto.RoomCreateRequest;
import com.medtrack.avs.dto.RoomResponse;
import com.medtrack.avs.dto.RoomUpdateRequest;
import com.medtrack.avs.entity.Building;
import com.medtrack.avs.entity.Department;
import com.medtrack.avs.entity.Floor;
import com.medtrack.avs.entity.Room;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.repository.DepartmentRepository;
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
public class RoomService {

    private final RoomRepository roomRepository;
    private final FloorRepository floorRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RoomResponse> getRoomsByFloorId(Long floorId, boolean activeOnly) {
        if (!floorRepository.existsById(floorId)) {
            throw new ResourceNotFoundException("Floor not found with id: " + floorId);
        }

        List<Room> rooms = activeOnly
                ? roomRepository.findByFloorIdAndActiveTrue(floorId)
                : roomRepository.findByFloorId(floorId);

        return rooms.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));
        return mapToResponse(room);
    }

    @Transactional
    public RoomResponse createRoom(Long floorId, RoomCreateRequest request, User currentUser) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found with id: " + floorId));

        if (!floor.isActive()) {
            throw new BadRequestException("Cannot add room to an inactive floor (id: " + floorId + ")");
        }

        String normalizedRoomNumber = request.getRoomNumber().trim().toUpperCase();
        if (roomRepository.existsByFloorIdAndRoomNumber(floorId, normalizedRoomNumber)) {
            throw new BadRequestException("Room number " + request.getRoomNumber() + " already exists on floor: " + floor.getDisplayName());
        }

        validateCanvasBounds(request.getXPosition(), request.getYPosition(), request.getWidth(), request.getHeight());

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
            if (!department.isActive()) {
                throw new BadRequestException("Cannot associate room with inactive department: " + department.getName());
            }
        }

        Room room = Room.builder()
                .floor(floor)
                .department(department)
                .roomNumber(normalizedRoomNumber)
                .name(request.getName().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .xPosition(request.getXPosition())
                .yPosition(request.getYPosition())
                .width(request.getWidth())
                .height(request.getHeight())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Room saved = roomRepository.save(room);
        log.info("Room created: id={}, floorId={}, roomNumber={}, name={}",
                saved.getId(), floorId, saved.getRoomNumber(), saved.getName());

        auditService.logFacilityEvent(
                currentUser,
                "CREATE_ROOM",
                "ROOM",
                saved.getId(),
                "Created room " + saved.getName() + " (" + saved.getRoomNumber() + ") on floor " + floor.getDisplayName()
        );

        return mapToResponse(saved);
    }

    @Transactional
    public RoomResponse updateRoom(Long roomId, RoomUpdateRequest request, User currentUser) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));

        Double newX = request.getXPosition() != null ? request.getXPosition() : room.getXPosition();
        Double newY = request.getYPosition() != null ? request.getYPosition() : room.getYPosition();
        Double newW = request.getWidth() != null ? request.getWidth() : room.getWidth();
        Double newH = request.getHeight() != null ? request.getHeight() : room.getHeight();
        validateCanvasBounds(newX, newY, newW, newH);

        room.setXPosition(newX);
        room.setYPosition(newY);
        room.setWidth(newW);
        room.setHeight(newH);

        if (request.getName() != null && !request.getName().isBlank()) {
            room.setName(request.getName().trim());
        }

        if (request.getDescription() != null) {
            room.setDescription(request.getDescription().trim());
        }

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
            if (!department.isActive()) {
                throw new BadRequestException("Cannot associate room with inactive department: " + department.getName());
            }
            room.setDepartment(department);
        }

        if (request.getActive() != null) {
            room.setActive(request.getActive());
        }

        Room updated = roomRepository.save(room);
        log.info("Room updated: id={}, roomNumber={}, name={}",
                updated.getId(), updated.getRoomNumber(), updated.getName());

        auditService.logFacilityEvent(
                currentUser,
                "UPDATE_ROOM",
                "ROOM",
                updated.getId(),
                "Updated room " + updated.getName() + " (" + updated.getRoomNumber() + ")"
        );

        return mapToResponse(updated);
    }

    @Transactional
    public void deleteRoom(Long roomId, User currentUser) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));

        // Soft deactivation
        room.setActive(false);
        roomRepository.save(room);
        log.info("Room deactivated: id={}, roomNumber={}", room.getId(), room.getRoomNumber());

        auditService.logFacilityEvent(
                currentUser,
                "DEACTIVATE_ROOM",
                "ROOM",
                room.getId(),
                "Deactivated room " + room.getName() + " (" + room.getRoomNumber() + ")"
        );
    }

    private void validateCanvasBounds(Double x, Double y, Double width, Double height) {
        if (x == null || x < 0.0 || x > 100.0) {
            throw new BadRequestException("xPosition must be between 0.0% and 100.0%");
        }
        if (y == null || y < 0.0 || y > 100.0) {
            throw new BadRequestException("yPosition must be between 0.0% and 100.0%");
        }
        if (width == null || width <= 0.0 || width > 100.0) {
            throw new BadRequestException("width must be between 0.1% and 100.0%");
        }
        if (height == null || height <= 0.0 || height > 100.0) {
            throw new BadRequestException("height must be between 0.1% and 100.0%");
        }
        if (x + width > 100.0001) {
            throw new BadRequestException("Room horizontal bound (x + width = " + String.format("%.2f", x + width) + "%) exceeds canvas 100.0%");
        }
        if (y + height > 100.0001) {
            throw new BadRequestException("Room vertical bound (y + height = " + String.format("%.2f", y + height) + "%) exceeds canvas 100.0%");
        }
    }

    public RoomResponse mapToResponse(Room room) {
        Floor floor = room.getFloor();
        Building building = floor.getBuilding();
        Department dept = room.getDepartment();

        return RoomResponse.builder()
                .id(room.getId())
                .floorId(floor.getId())
                .floorDisplayName(floor.getDisplayName())
                .buildingId(building.getId())
                .buildingName(building.getName())
                .hospitalId(building.getHospital().getId())
                .departmentId(dept != null ? dept.getId() : null)
                .departmentName(dept != null ? dept.getName() : null)
                .departmentCode(dept != null ? dept.getCode() : null)
                .roomNumber(room.getRoomNumber())
                .name(room.getName())
                .description(room.getDescription())
                .xPosition(room.getXPosition())
                .yPosition(room.getYPosition())
                .width(room.getWidth())
                .height(room.getHeight())
                .active(room.isActive())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }
}
