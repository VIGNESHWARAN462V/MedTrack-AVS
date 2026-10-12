package com.medtrack.avs.repository;

import com.medtrack.avs.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findByHospitalId(Long hospitalId);
    List<Building> findByHospitalIdAndActiveTrue(Long hospitalId);
    Optional<Building> findByHospitalIdAndCode(Long hospitalId, String code);
    boolean existsByHospitalIdAndCode(Long hospitalId, String code);
    long countByHospitalIdAndActiveTrue(Long hospitalId);
}
