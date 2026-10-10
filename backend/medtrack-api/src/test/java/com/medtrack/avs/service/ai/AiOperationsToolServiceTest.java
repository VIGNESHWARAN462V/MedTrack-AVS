package com.medtrack.avs.service.ai;

import com.medtrack.avs.dto.AlertResponse;
import com.medtrack.avs.dto.LatestLocationResponse;
import com.medtrack.avs.dto.TransferResponse;
import com.medtrack.avs.dto.analytics.AmcMetricResponse;
import com.medtrack.avs.dto.analytics.DepartmentGubDto;
import com.medtrack.avs.dto.analytics.GubMetricResponse;
import com.medtrack.avs.dto.analytics.HfiMetricResponse;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.exception.ResourceNotFoundException;
import com.medtrack.avs.repository.*;
import com.medtrack.avs.service.*;
import com.medtrack.avs.service.ai.model.AiIntentClassification;
import com.medtrack.avs.service.ai.model.AiToolExecutionResult;
import com.medtrack.avs.service.ai.model.AiToolName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiOperationsToolServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private AssetAssignmentRepository assetAssignmentRepository;
    @Mock private GpsSessionRepository gpsSessionRepository;
    @Mock private LocationHistoryRepository locationHistoryRepository;
    @Mock private TrackingService trackingService;
    @Mock private AlertService alertService;
    @Mock private TransferService transferService;
    @Mock private AnalyticsService analyticsService;

    private AiOperationsToolService toolService;

    private User adminUser;
    private User staffUser;
    private Department cardioDept;
    private Asset ecgAsset;

    @BeforeEach
    void setUp() {
        toolService = new AiOperationsToolService(
                assetRepository, departmentRepository, assetAssignmentRepository,
                gpsSessionRepository, locationHistoryRepository,
                trackingService, alertService, transferService, analyticsService
        );

        adminUser = User.builder()
                .id(1L)
                .email("admin@medtrack.org")
                .role(Role.builder().name(RoleType.ADMIN).build())
                .build();

        staffUser = User.builder()
                .id(2L)
                .email("staff@medtrack.org")
                .role(Role.builder().name(RoleType.STAFF).build())
                .build();

        cardioDept = Department.builder()
                .id(10L)
                .name("Cardiology")
                .code("CARD")
                .build();

        ecgAsset = Asset.builder()
                .id(100L)
                .assetCode("ECG-001")
                .assetName("Portable ECG")
                .category("Diagnostic")
                .status(AssetStatus.AVAILABLE)
                .department(cardioDept)
                .lastSeen(Instant.now().minus(5, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
    }

    @Test
    @DisplayName("1. lookupAsset allows ADMIN to retrieve any asset with source reference")
    void testLookupAsset_adminSuccess() {
        when(assetRepository.findByAssetCode("ECG-001")).thenReturn(Optional.of(ecgAsset));
        when(assetAssignmentRepository.findByAssetIdAndActiveTrue(100L)).thenReturn(Optional.empty());

        List<com.medtrack.avs.dto.AiSourceReference> sources = new ArrayList<>();
        Map<String, Object> result = toolService.lookupAsset("ECG-001", adminUser, sources);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals("ECG-001", data.get("assetCode"));
        assertEquals("Cardiology", data.get("department"));
        assertEquals("Unassigned", data.get("assignedUser"));
        assertEquals(1, sources.size());
        assertEquals("Asset inventory", sources.get(0).getLabel());
        assertEquals("LIVE", sources.get(0).getFreshness());
    }

    @Test
    @DisplayName("2. lookupAsset allows STAFF to retrieve assigned asset")
    void testLookupAsset_staffAssignedSuccess() {
        when(assetRepository.findByAssetCode("ECG-001")).thenReturn(Optional.of(ecgAsset));
        when(assetAssignmentRepository.existsByAssetIdAndAssignedUserIdAndActiveTrue(100L, 2L)).thenReturn(true);
        when(assetAssignmentRepository.findByAssetIdAndActiveTrue(100L)).thenReturn(Optional.empty());

        List<com.medtrack.avs.dto.AiSourceReference> sources = new ArrayList<>();
        Map<String, Object> result = toolService.lookupAsset("ECG-001", staffUser, sources);

        assertNotNull(result);
        assertEquals(1, sources.size());
    }

    @Test
    @DisplayName("3. lookupAsset denies STAFF access to unassigned asset")
    void testLookupAsset_staffUnassignedDenied() {
        when(assetRepository.findByAssetCode("ECG-001")).thenReturn(Optional.of(ecgAsset));
        when(assetAssignmentRepository.existsByAssetIdAndAssignedUserIdAndActiveTrue(100L, 2L)).thenReturn(false);
        when(gpsSessionRepository.findByAssetIdAndActiveTrue(100L)).thenReturn(Optional.empty());

        List<com.medtrack.avs.dto.AiSourceReference> sources = new ArrayList<>();
        assertThrows(AccessDeniedException.class, () ->
                toolService.lookupAsset("ECG-001", staffUser, sources));
    }

    @Test
    @DisplayName("4. lookupAsset throws ResourceNotFoundException for unknown asset code")
    void testLookupAsset_unknownAsset() {
        when(assetRepository.findByAssetCode("FAKE-999")).thenReturn(Optional.empty());

        List<com.medtrack.avs.dto.AiSourceReference> sources = new ArrayList<>();
        assertThrows(ResourceNotFoundException.class, () ->
                toolService.lookupAsset("FAKE-999", adminUser, sources));
    }

    @Test
    @DisplayName("5. getLatestLocation returns LIVE location when update is fresh (< 15 mins)")
    void testGetLatestLocation_freshLive() {
        when(assetRepository.findByAssetCode("ECG-001")).thenReturn(Optional.of(ecgAsset));
        Instant recordedAt = Instant.now().minus(3, ChronoUnit.MINUTES);
        LatestLocationResponse loc = LatestLocationResponse.builder()
                .assetId(100L)
                .assetCode("ECG-001")
                .latitude(11.4961)
                .longitude(77.2785)
                .accuracy(4.2)
                .recordedAt(recordedAt)
                .hasLocation(true)
                .build();
        when(trackingService.getLatestLocation(100L, adminUser)).thenReturn(loc);

        List<com.medtrack.avs.dto.AiSourceReference> sources = new ArrayList<>();
        Map<String, Object> result = toolService.getLatestLocation("ECG-001", adminUser, sources);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(true, data.get("hasLocation"));
        assertEquals(false, data.get("isStale"));
        assertEquals("LIVE", data.get("freshness"));
        assertEquals(1, sources.size());
        assertEquals("LIVE", sources.get(0).getFreshness());
    }

    @Test
    @DisplayName("6. getLatestLocation flags STALE update when older than 15 mins")
    void testGetLatestLocation_staleUpdate() {
        when(assetRepository.findByAssetCode("ECG-001")).thenReturn(Optional.of(ecgAsset));
        Instant recordedAt = Instant.now().minus(45, ChronoUnit.MINUTES);
        LatestLocationResponse loc = LatestLocationResponse.builder()
                .assetId(100L)
                .assetCode("ECG-001")
                .latitude(11.4961)
                .longitude(77.2785)
                .accuracy(4.2)
                .recordedAt(recordedAt)
                .hasLocation(true)
                .build();
        when(trackingService.getLatestLocation(100L, adminUser)).thenReturn(loc);

        List<com.medtrack.avs.dto.AiSourceReference> sources = new ArrayList<>();
        Map<String, Object> result = toolService.getLatestLocation("ECG-001", adminUser, sources);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(true, data.get("isStale"));
        assertTrue(((String) data.get("freshness")).contains("STALE"));
        assertTrue(sources.get(0).getFreshness().contains("STALE"));
    }

    @Test
    @DisplayName("7. getLatestLocation preserves NO_DATA when no coordinates exist (not invented)")
    void testGetLatestLocation_missingCoordinatesPreserved() {
        when(assetRepository.findByAssetCode("ECG-001")).thenReturn(Optional.of(ecgAsset));
        LatestLocationResponse loc = LatestLocationResponse.builder()
                .assetId(100L)
                .assetCode("ECG-001")
                .hasLocation(false)
                .build();
        when(trackingService.getLatestLocation(100L, adminUser)).thenReturn(loc);

        List<com.medtrack.avs.dto.AiSourceReference> sources = new ArrayList<>();
        Map<String, Object> result = toolService.getLatestLocation("ECG-001", adminUser, sources);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(false, data.get("hasLocation"));
        assertEquals("NO_DATA", data.get("freshness"));
        assertEquals("NO_DATA", sources.get(0).getFreshness());
        assertTrue(((String) result.get("summary")).contains("NO GPS location reported"));
    }

    @Test
    @DisplayName("8. getRecentAlerts bounds limit to max 20")
    void testGetRecentAlerts_boundsLimit() {
        List<AlertResponse> alertList = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            alertList.add(AlertResponse.builder()
                    .id((long) i)
                    .assetCode("ECG-001")
                    .occurredAt(Instant.now().minus(i, ChronoUnit.MINUTES))
                    .resolved(false)
                    .build());
        }
        when(alertService.getAlerts(any(), any(), any(), eq(adminUser))).thenReturn(alertList);

        List<com.medtrack.avs.dto.AiSourceReference> sources = new ArrayList<>();
        Map<String, Object> result = toolService.getRecentAlerts(false, 50, adminUser, sources);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        @SuppressWarnings("unchecked")
        List<AlertResponse> returned = (List<AlertResponse>) data.get("alerts");
        assertEquals(20, returned.size(), "Result must be capped to max 20");
    }

    @Test
    @DisplayName("9. getActiveTransfers filters only active in-progress statuses")
    void testGetActiveTransfers_activeOnly() {
        TransferResponse t1 = TransferResponse.builder().id(1L).status(TransferStatus.REQUESTED).assetCode("ECG-001").build();
        TransferResponse t2 = TransferResponse.builder().id(2L).status(TransferStatus.IN_TRANSIT).assetCode("PUMP-001").build();
        TransferResponse t3 = TransferResponse.builder().id(3L).status(TransferStatus.COMPLETED).assetCode("MON-001").build();

        when(transferService.getTransfers(isNull(), isNull(), isNull(), isNull(), eq(adminUser)))
                .thenReturn(List.of(t1, t2, t3));

        List<com.medtrack.avs.dto.AiSourceReference> sources = new ArrayList<>();
        Map<String, Object> result = toolService.getActiveTransfers(null, null, 10, adminUser, sources);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        @SuppressWarnings("unchecked")
        List<TransferResponse> active = (List<TransferResponse>) data.get("transfers");
        assertEquals(2, active.size());
        assertEquals(TransferStatus.REQUESTED, active.get(0).getStatus());
        assertEquals(TransferStatus.IN_TRANSIT, active.get(1).getStatus());
    }

    @Test
    @DisplayName("10. getAnalyticsMetric preserves NO_DATA status for HFI when transfers are empty")
    void testGetAnalyticsMetric_hfiNoData() {
        HfiMetricResponse hfi = HfiMetricResponse.builder()
                .metricName("Handoff Friction Index (HFI)")
                .status("NO_DATA")
                .completedTransferCount(0)
                .periodEnd(Instant.now())
                .build();
        when(analyticsService.calculateHfi(any(), any(), any(), any(), eq(adminUser))).thenReturn(hfi);

        List<com.medtrack.avs.dto.AiSourceReference> sources = new ArrayList<>();
        Map<String, Object> result = toolService.getAnalyticsMetric("HFI", null, null, null, null, adminUser, sources);

        assertEquals("NO_DATA", sources.get(0).getFreshness());
        assertTrue(((String) result.get("summary")).contains("NO_DATA"));
    }

    @Test
    @DisplayName("11. getDepartmentBalance identifies department with greatest supply deficit")
    void testGetDepartmentBalance_identifiesDeficit() {
        DepartmentGubDto d1 = DepartmentGubDto.builder()
                .departmentName("Cardiology")
                .departmentCode("CARD")
                .balanceScore(15.0)
                .status("SURPLUS")
                .build();
        DepartmentGubDto d2 = DepartmentGubDto.builder()
                .departmentName("Emergency")
                .departmentCode("ER")
                .balanceScore(-32.5)
                .status("DEFICIT")
                .usableAssetCount(2L)
                .demandCount(6L)
                .build();

        GubMetricResponse gub = GubMetricResponse.builder()
                .hospitalEquilibriumScore(72.5)
                .departments(List.of(d1, d2))
                .periodEnd(Instant.now())
                .build();

        when(analyticsService.calculateGub(isNull(), isNull(), isNull(), eq(adminUser))).thenReturn(gub);

        List<com.medtrack.avs.dto.AiSourceReference> sources = new ArrayList<>();
        Map<String, Object> result = toolService.getDepartmentBalance(null, adminUser, sources);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        DepartmentGubDto greatestDeficit = (DepartmentGubDto) data.get("greatestDeficitDepartment");
        assertNotNull(greatestDeficit);
        assertEquals("Emergency", greatestDeficit.getDepartmentName());
        assertEquals(-32.5, greatestDeficit.getBalanceScore());
        assertTrue(((String) result.get("summary")).contains("Emergency"));
    }

    @Test
    @DisplayName("12. execute caps tool invocations to maximum 3")
    void testExecute_capsMax3Tools() {
        when(assetRepository.findByAssetCode("ECG-001")).thenReturn(Optional.of(ecgAsset));
        when(assetAssignmentRepository.findByAssetIdAndActiveTrue(100L)).thenReturn(Optional.empty());
        when(trackingService.getLatestLocation(100L, adminUser)).thenReturn(LatestLocationResponse.builder().hasLocation(false).build());
        when(alertService.getAlerts(any(), any(), any(), eq(adminUser))).thenReturn(List.of());

        AiIntentClassification classification = AiIntentClassification.builder()
                .assetCode("ECG-001")
                .targetTools(List.of(
                        AiToolName.LOOKUP_ASSET,
                        AiToolName.GET_LATEST_LOCATION,
                        AiToolName.GET_RECENT_ALERTS,
                        AiToolName.GET_ACTIVE_TRANSFERS // 4th tool should not be run
                ))
                .build();

        AiToolExecutionResult executionResult = toolService.execute(classification, adminUser);

        assertEquals(3, executionResult.getExecutedTools().size(), "Strictly max 3 tools allowed");
        verify(transferService, never()).getTransfers(any(), any(), any(), any(), any());
    }
}
