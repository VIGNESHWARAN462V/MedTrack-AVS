package com.medtrack.avs.repository;

import com.medtrack.avs.entity.FloorPlanImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FloorPlanImageRepository extends JpaRepository<FloorPlanImage, Long> {

    Optional<FloorPlanImage> findByFloorId(Long floorId);

    boolean existsByFloorId(Long floorId);

    void deleteByFloorId(Long floorId);
}
