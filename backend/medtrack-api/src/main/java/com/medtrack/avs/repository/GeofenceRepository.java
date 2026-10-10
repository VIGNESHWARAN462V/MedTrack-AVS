package com.medtrack.avs.repository;

import com.medtrack.avs.entity.Geofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GeofenceRepository extends JpaRepository<Geofence, Long> {
    List<Geofence> findByDepartmentId(Long departmentId);
    List<Geofence> findByActiveTrue();
    boolean existsByName(String name);
    Optional<Geofence> findByName(String name);
}
