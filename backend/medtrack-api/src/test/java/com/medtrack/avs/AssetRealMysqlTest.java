package com.medtrack.avs;

import com.medtrack.avs.dto.AssetCreateRequest;
import com.medtrack.avs.dto.AssetResponse;
import com.medtrack.avs.dto.AssetUpdateRequest;
import com.medtrack.avs.entity.Asset;
import com.medtrack.avs.entity.AssetStatus;
import com.medtrack.avs.entity.Department;
import com.medtrack.avs.exception.ConflictException;
import com.medtrack.avs.repository.AssetRepository;
import com.medtrack.avs.repository.DepartmentRepository;
import com.medtrack.avs.service.AssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/medtrack_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC",
        "spring.datasource.driverClassName=com.mysql.cj.jdbc.Driver",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
class AssetRealMysqlTest {

    @Autowired
    private AssetService assetService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Test
    @DisplayName("Real MySQL: Verify reading seeded assets and filtering/searching")
    void testRealMysqlReadAndFilters() {
        // Verify seeded assets are present in MySQL
        List<AssetResponse> allAssets = assetService.getAssets(null, null, null, null);
        assertNotNull(allAssets);
        assertTrue(allAssets.size() >= 10, "MySQL should contain seeded demo assets");

        // Verify lookup by code on seeded record
        AssetResponse ecgAsset = assetService.getAssetByCode("ECG-001");
        assertNotNull(ecgAsset);
        assertEquals("ECG-001", ecgAsset.getAssetCode());
        assertEquals("Cardiology", ecgAsset.getCategory());

        // Verify search for ECG in MySQL
        List<AssetResponse> searchResults = assetService.getAssets("ECG", null, null, null);
        assertFalse(searchResults.isEmpty(), "Search 'ECG' should return results in MySQL");
        assertTrue(searchResults.stream().anyMatch(a -> a.getAssetCode().contains("ECG")));

        // Verify status filter
        List<AssetResponse> availableList = assetService.getAssets(null, AssetStatus.AVAILABLE, null, null);
        assertFalse(availableList.isEmpty());
        assertTrue(availableList.stream().allMatch(a -> a.getStatus() == AssetStatus.AVAILABLE));

        // Verify category filter
        List<AssetResponse> cardioList = assetService.getAssets(null, null, null, "Cardiology");
        assertFalse(cardioList.isEmpty());
        assertTrue(cardioList.stream().allMatch(a -> a.getCategory().equalsIgnoreCase("Cardiology")));
    }

    @Test
    @DisplayName("Real MySQL: Dedicated test asset CRUD, duplicate prevention, and clean deletion")
    void testRealMysqlDedicatedAssetCrud() {
        long initialCount = assetRepository.count();
        Department department = departmentRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No departments found in MySQL"));

        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        String testAssetCode = "MYSQL-TEST-" + uniqueSuffix;
        String testSerial = "SN-MYSQL-" + uniqueSuffix;
        String testQr = "MEDTRACK:ASSET:" + testAssetCode;

        // 1. CREATE test asset in real MySQL
        AssetCreateRequest createReq = AssetCreateRequest.builder()
                .assetCode(testAssetCode)
                .assetName("Automated MySQL Test Device")
                .category("Diagnostics")
                .serialNumber(testSerial)
                .status(AssetStatus.AVAILABLE)
                .departmentId(department.getId())
                .batteryLevel(88)
                .qrCode(testQr)
                .build();

        AssetResponse created = assetService.createAsset(createReq);
        assertNotNull(created.getId());
        assertEquals(testAssetCode, created.getAssetCode());
        assertEquals(88, created.getBatteryLevel());
        assertNull(created.getLatitude(), "Ordinary CRUD must not set latitude");

        // Verify real database record exists
        Optional<Asset> persisted = assetRepository.findById(created.getId());
        assertTrue(persisted.isPresent());
        assertEquals(testAssetCode, persisted.get().getAssetCode());

        // 2. DUPLICATE CHECK: attempt duplicate assetCode in MySQL
        AssetCreateRequest duplicateCodeReq = AssetCreateRequest.builder()
                .assetCode(testAssetCode)
                .assetName("Duplicate Code Device")
                .category("Diagnostics")
                .serialNumber("SN-OTHER-" + uniqueSuffix)
                .departmentId(department.getId())
                .build();
        assertThrows(ConflictException.class, () -> assetService.createAsset(duplicateCodeReq));

        // 3. DUPLICATE CHECK: attempt duplicate serialNumber in MySQL
        AssetCreateRequest duplicateSerialReq = AssetCreateRequest.builder()
                .assetCode("CODE-OTHER-" + uniqueSuffix)
                .assetName("Duplicate Serial Device")
                .category("Diagnostics")
                .serialNumber(testSerial)
                .departmentId(department.getId())
                .build();
        assertThrows(ConflictException.class, () -> assetService.createAsset(duplicateSerialReq));

        // 4. UPDATE test asset in real MySQL
        AssetUpdateRequest updateReq = AssetUpdateRequest.builder()
                .assetName("Automated MySQL Test Device (Updated)")
                .batteryLevel(95)
                .status(AssetStatus.IN_USE)
                .build();
        AssetResponse updated = assetService.updateAsset(created.getId(), updateReq);
        assertEquals("Automated MySQL Test Device (Updated)", updated.getAssetName());
        assertEquals(95, updated.getBatteryLevel());
        assertEquals(AssetStatus.IN_USE, updated.getStatus());

        // 5. DELETE test asset from real MySQL
        assetService.deleteAsset(created.getId());
        assertFalse(assetRepository.existsById(created.getId()), "Test asset must be deleted from MySQL");

        // Verify count returned to initial count (preserving seeded assets)
        assertEquals(initialCount, assetRepository.count(), "Total asset count must match initial seed count after cleanup");
    }

    @Test
    @DisplayName("Real MySQL: Verify QR lookup for seeded asset (MEDTRACK:ASSET:ECG-001)")
    void testRealMysqlQrLookupSeededAsset() {
        assetRepository.findByAssetCode("ECG-001").ifPresent(a -> {
            Department card = departmentRepository.findByCode("CARD").orElse(null);
            if (card != null && (a.getDepartment() == null || !a.getDepartment().getId().equals(card.getId()))) {
                a.setDepartment(card);
                assetRepository.save(a);
            }
        });

        AssetResponse response = assetService.getAssetByQr("MEDTRACK:ASSET:ECG-001");
        assertNotNull(response);
        assertEquals("ECG-001", response.getAssetCode());
        assertEquals("Portable ECG Machine", response.getAssetName());
        assertEquals("Cardiology", response.getDepartmentName());
        assertEquals("MEDTRACK:ASSET:ECG-001", response.getQrCode());
        if (response.getLatitude() != null) {
            assertTrue(response.getLatitude() >= -90.0 && response.getLatitude() <= 90.0);
            assertTrue(response.getLongitude() >= -180.0 && response.getLongitude() <= 180.0);
        }
    }

    @Test
    @DisplayName("Real MySQL: Verify QR lookup with unknown code throws ResourceNotFoundException (404)")
    void testRealMysqlQrLookupUnknownCode() {
        assertThrows(com.medtrack.avs.exception.ResourceNotFoundException.class, () ->
                assetService.getAssetByQr("MEDTRACK:ASSET:NONEXISTENT-XYZ"));
    }

    @Test
    @DisplayName("Real MySQL: Verify QR lookup with empty/overlength code throws BadRequestException (400)")
    void testRealMysqlQrLookupValidation() {
        assertThrows(com.medtrack.avs.exception.BadRequestException.class, () ->
                assetService.getAssetByQr(""));
        assertThrows(com.medtrack.avs.exception.BadRequestException.class, () ->
                assetService.getAssetByQr("   "));
        assertThrows(com.medtrack.avs.exception.BadRequestException.class, () ->
                assetService.getAssetByQr("MEDTRACK:ASSET:" + "X".repeat(300)));
    }

    @Test
    @DisplayName("Real MySQL: Verify repeated QR lookups are idempotent and do not create assets")
    void testRealMysqlDuplicateQrLookupsIdempotent() {
        long countBefore = assetRepository.count();

        AssetResponse scan1 = assetService.getAssetByQr("MEDTRACK:ASSET:PUMP-001");
        AssetResponse scan2 = assetService.getAssetByQr("MEDTRACK:ASSET:PUMP-001");

        assertNotNull(scan1);
        assertNotNull(scan2);
        assertEquals(scan1.getId(), scan2.getId());
        assertEquals("PUMP-001", scan1.getAssetCode());
        assertEquals(countBefore, assetRepository.count(), "Asset count must not change on repeated QR lookup");
    }
}
