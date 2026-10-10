package com.medtrack.avs.repository;

import com.medtrack.avs.entity.AssetAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, Long> {
    List<AssetAssignment> findByAssetId(Long assetId);
    List<AssetAssignment> findByAssignedUserId(Long userId);
    Optional<AssetAssignment> findByAssetIdAndActiveTrue(Long assetId);
    boolean existsByAssetIdAndActiveTrue(Long assetId);
    boolean existsByAssetIdAndAssignedUserIdAndActiveTrue(Long assetId, Long userId);
}

