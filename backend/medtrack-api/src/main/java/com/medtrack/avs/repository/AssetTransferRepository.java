package com.medtrack.avs.repository;

import com.medtrack.avs.entity.AssetTransfer;
import com.medtrack.avs.entity.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetTransferRepository extends JpaRepository<AssetTransfer, Long> {

    List<AssetTransfer> findByAssetId(Long assetId);

    List<AssetTransfer> findByStatus(TransferStatus status);

    List<AssetTransfer> findByToDepartmentId(Long departmentId);

    List<AssetTransfer> findByFromDepartmentId(Long departmentId);

    List<AssetTransfer> findByRequestedById(Long userId);

    boolean existsByAssetIdAndStatusIn(Long assetId, Collection<TransferStatus> statuses);

    Optional<AssetTransfer> findFirstByAssetIdAndStatusInOrderByCreatedAtDesc(Long assetId, Collection<TransferStatus> statuses);

    List<AssetTransfer> findAllByOrderByCreatedAtDesc();

    @Query("SELECT DISTINCT t FROM AssetTransfer t " +
           "LEFT JOIN FETCH t.asset " +
           "LEFT JOIN FETCH t.fromDepartment " +
           "LEFT JOIN FETCH t.toDepartment " +
           "LEFT JOIN FETCH t.requestedBy WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:assetId IS NULL OR t.asset.id = :assetId) AND " +
           "(:departmentId IS NULL OR t.fromDepartment.id = :departmentId OR t.toDepartment.id = :departmentId) AND " +
           "(:requestedBy IS NULL OR t.requestedBy.id = :requestedBy) " +
           "ORDER BY t.createdAt DESC")
    List<AssetTransfer> findWithFilters(
            @Param("status") TransferStatus status,
            @Param("assetId") Long assetId,
            @Param("departmentId") Long departmentId,
            @Param("requestedBy") Long requestedBy
    );

    @Query("SELECT t FROM AssetTransfer t WHERE " +
           "t.status = com.medtrack.avs.entity.TransferStatus.COMPLETED AND " +
           "(:assetId IS NULL OR t.asset.id = :assetId) AND " +
           "(:departmentId IS NULL OR t.fromDepartment.id = :departmentId OR t.toDepartment.id = :departmentId) AND " +
           "t.completedAt BETWEEN :from AND :to " +
           "ORDER BY t.completedAt DESC")
    List<AssetTransfer> findCompletedTransfersInPeriod(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("departmentId") Long departmentId,
            @Param("assetId") Long assetId
    );

    @Query("SELECT COUNT(t) FROM AssetTransfer t WHERE " +
           "t.toDepartment.id = :departmentId AND " +
           "t.createdAt BETWEEN :from AND :to")
    Long countInboundTransfersInPeriod(
            @Param("departmentId") Long departmentId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("SELECT COUNT(t) FROM AssetTransfer t WHERE " +
           "t.fromDepartment.id = :departmentId AND " +
           "t.createdAt BETWEEN :from AND :to")
    Long countOutboundTransfersInPeriod(
            @Param("departmentId") Long departmentId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
