package com.medtrack.avs.repository;

import com.medtrack.avs.entity.AssetGeofenceState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetGeofenceStateRepository extends JpaRepository<AssetGeofenceState, Long> {
    Optional<AssetGeofenceState> findByAssetIdAndGeofenceId(Long assetId, Long geofenceId);
    List<AssetGeofenceState> findByAssetId(Long assetId);
    void deleteByGeofenceId(Long geofenceId);
    void deleteByAssetId(Long assetId);
}
