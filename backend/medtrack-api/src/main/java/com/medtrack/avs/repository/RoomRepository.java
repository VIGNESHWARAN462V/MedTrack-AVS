package com.medtrack.avs.repository;

import com.medtrack.avs.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByFloorId(Long floorId);
    List<Room> findByFloorIdAndActiveTrue(Long floorId);
    Optional<Room> findByFloorIdAndRoomNumber(Long floorId, String roomNumber);
    Optional<Room> findFirstByRoomNumber(String roomNumber);
    boolean existsByFloorIdAndRoomNumber(Long floorId, String roomNumber);
    long countByFloorIdAndActiveTrue(Long floorId);
}
