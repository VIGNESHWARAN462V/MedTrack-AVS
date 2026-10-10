package com.medtrack.avs.repository;

import com.medtrack.avs.entity.Alert;
import com.medtrack.avs.entity.AlertSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    @Query("SELECT DISTINCT a FROM Alert a LEFT JOIN FETCH a.asset LEFT JOIN FETCH a.geofence ORDER BY a.occurredAt DESC")
    List<Alert> findAllByOrderByOccurredAtDesc();

    @Query("SELECT DISTINCT a FROM Alert a LEFT JOIN FETCH a.asset LEFT JOIN FETCH a.geofence WHERE a.resolved = false ORDER BY a.occurredAt DESC")
    List<Alert> findByResolvedFalseOrderByOccurredAtDesc();
    List<Alert> findByAssetIdOrderByOccurredAtDesc(Long assetId);
    List<Alert> findByGeofenceIdOrderByOccurredAtDesc(Long geofenceId);
    List<Alert> findBySeverityOrderByOccurredAtDesc(AlertSeverity severity);
    long countByResolvedFalse();
}
