package com.medtrack.avs.repository;

import com.medtrack.avs.entity.GpsSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface GpsSessionRepository extends JpaRepository<GpsSession, Long> {
    List<GpsSession> findByAssetId(Long assetId);
    Optional<GpsSession> findByAssetIdAndActiveTrue(Long assetId);
    boolean existsByAssetIdAndActiveTrue(Long assetId);

    @Query("SELECT s FROM GpsSession s WHERE " +
           "(:assetId IS NULL OR s.asset.id = :assetId) AND " +
           "(:departmentId IS NULL OR s.asset.department.id = :departmentId) AND " +
           "s.startedAt <= :to AND (s.endedAt IS NULL OR s.endedAt >= :from) " +
           "ORDER BY s.startedAt ASC")
    List<GpsSession> findSessionsInPeriod(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("departmentId") Long departmentId,
            @Param("assetId") Long assetId
    );
}

