package com.medtrack.avs.repository;

import com.medtrack.avs.entity.LocationHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface LocationHistoryRepository extends JpaRepository<LocationHistory, Long> {
    List<LocationHistory> findByAssetId(Long assetId);
    List<LocationHistory> findByAssetIdOrderByRecordedAtDesc(Long assetId);
    List<LocationHistory> findByAssetIdOrderByRecordedAtDesc(Long assetId, Pageable pageable);
    List<LocationHistory> findByAssetIdOrderByRecordedAtAsc(Long assetId);
    List<LocationHistory> findByAssetIdOrderByRecordedAtAsc(Long assetId, Pageable pageable);
    Optional<LocationHistory> findFirstByAssetIdOrderByRecordedAtDesc(Long assetId);

    List<LocationHistory> findByAssetIdAndRecordedAtBetweenOrderByRecordedAtAsc(Long assetId, Instant start, Instant end);

    @Query("SELECT l FROM LocationHistory l WHERE " +
           "(:assetId IS NULL OR l.asset.id = :assetId) AND " +
           "(:departmentId IS NULL OR l.asset.department.id = :departmentId) AND " +
           "l.recordedAt BETWEEN :from AND :to " +
           "ORDER BY l.recordedAt ASC")
    List<LocationHistory> findUpdatesInPeriod(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("departmentId") Long departmentId,
            @Param("assetId") Long assetId
    );

    @Query("SELECT COUNT(l) FROM LocationHistory l WHERE " +
           "(:assetId IS NULL OR l.asset.id = :assetId) AND " +
           "(:departmentId IS NULL OR l.asset.department.id = :departmentId) AND " +
           "l.recordedAt BETWEEN :from AND :to")
    Long countUpdatesInPeriod(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("departmentId") Long departmentId,
            @Param("assetId") Long assetId
    );

    @Query("SELECT COUNT(DISTINCT l.asset.id) FROM LocationHistory l WHERE " +
           "(:assetId IS NULL OR l.asset.id = :assetId) AND " +
           "(:departmentId IS NULL OR l.asset.department.id = :departmentId) AND " +
           "l.recordedAt BETWEEN :from AND :to")
    Integer countDistinctAssetsInPeriod(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("departmentId") Long departmentId,
            @Param("assetId") Long assetId
    );
}

