package com.medtrack.avs.repository;

import com.medtrack.avs.entity.Asset;
import com.medtrack.avs.entity.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByAssetCode(String assetCode);

    Optional<Asset> findBySerialNumber(String serialNumber);

    Optional<Asset> findByQrCode(String qrCode);

    List<Asset> findByDepartmentId(Long departmentId);

    List<Asset> findByStatus(AssetStatus status);

    List<Asset> findByCategoryIgnoreCase(String category);

    boolean existsByAssetCode(String assetCode);

    boolean existsBySerialNumber(String serialNumber);

    boolean existsByQrCode(String qrCode);

    boolean existsByAssetCodeAndIdNot(String assetCode, Long id);

    boolean existsBySerialNumberAndIdNot(String serialNumber, Long id);

    boolean existsByQrCodeAndIdNot(String qrCode, Long id);

    @Query("SELECT DISTINCT a FROM Asset a LEFT JOIN FETCH a.department WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            " LOWER(a.assetCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            " LOWER(a.assetName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            " LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            " LOWER(a.category) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:departmentId IS NULL OR a.department.id = :departmentId) AND " +
            "(:category IS NULL OR :category = '' OR LOWER(a.category) = LOWER(:category))")
    List<Asset> searchAndFilter(
            @Param("search") String search,
            @Param("status") AssetStatus status,
            @Param("departmentId") Long departmentId,
            @Param("category") String category);

    @Query("SELECT DISTINCT a FROM Asset a " +
            "LEFT JOIN FETCH a.department " +
            "LEFT JOIN FETCH a.hospital " +
            "LEFT JOIN FETCH a.building " +
            "LEFT JOIN FETCH a.floor " +
            "LEFT JOIN FETCH a.room " +
            "WHERE a.floor.id = :floorId")
    List<Asset> findByFloorIdWithHierarchy(@Param("floorId") Long floorId);

    List<Asset> findByFloorId(Long floorId);

    long countByFloorId(Long floorId);

    long countByRoomId(Long roomId);
}

