package com.medtrack.avs.service;

import com.medtrack.avs.dto.analytics.AmcMetricResponse;
import com.medtrack.avs.dto.analytics.DepartmentGubDto;
import com.medtrack.avs.dto.analytics.GubMetricResponse;
import com.medtrack.avs.dto.analytics.HfiMetricResponse;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AnalyticsCalculationTest {

    @Mock
    private LocationHistoryRepository locationHistoryRepository;

    @Mock
    private GpsSessionRepository gpsSessionRepository;

    @Mock
    private AssetTransferRepository assetTransferRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetAssignmentRepository assetAssignmentRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private Department deptEr;
    private Department deptCard;
    private Asset assetEcg;
    private User testUser;

    @BeforeEach
    void setUp() {
        deptEr = Department.builder()
                .id(1L)
                .name("Emergency Room")
                .code("ER")
                .active(true)
                .build();

        deptCard = Department.builder()
                .id(2L)
                .name("Cardiology")
                .code("CARD")
                .active(true)
                .build();

        assetEcg = Asset.builder()
                .id(10L)
                .assetCode("ECG-001")
                .assetName("Portable ECG Monitor")
                .department(deptEr)
                .status(AssetStatus.AVAILABLE)
                .build();

        Role adminRole = Role.builder().id(1L).name(RoleType.ADMIN).build();
        testUser = User.builder()
                .id(1L)
                .name("Admin System")
                .email("admin@medtrack.local")
                .role(adminRole)
                .build();
    }

    // ==========================================
    // 1. AMC CALCULATION TESTS
    // ==========================================

    @Test
    @DisplayName("AMC: exact expected == successful -> 100.00%")
    void testAmcExactExpectedEqualsSuccessful() {
        Instant from = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant to = Instant.now();

        // 1 active session of 100 seconds (started at from, ended at from + 100s)
        // TELEMETRY_INTERVAL_SECONDS = 5 -> expected = 100 / 5 = 20
        GpsSession session = GpsSession.builder()
                .id(101L)
                .asset(assetEcg)
                .active(false)
                .startedAt(from)
                .endedAt(from.plus(100, ChronoUnit.SECONDS))
                .build();

        when(gpsSessionRepository.findSessionsInPeriod(any(), any(), any(), any()))
                .thenReturn(List.of(session));

        java.util.List<LocationHistory> updates = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            updates.add(LocationHistory.builder().asset(assetEcg).recordedAt(from.plusSeconds(i * 5)).build());
        }
        when(locationHistoryRepository.findUpdatesInPeriod(any(), any(), any(), any()))
                .thenReturn(updates);

        AmcMetricResponse response = analyticsService.calculateAmc(from, to, null, null, testUser);

        assertNotNull(response);
        assertEquals(20L, response.getExpectedGpsUpdates());
        assertEquals(20L, response.getSuccessfulGpsUpdates());
        assertEquals(100.00, response.getContinuityPercentage());
        assertEquals("MEASURED", response.getStatus());
    }

    @Test
    @DisplayName("AMC: half successful -> 50.00%")
    void testAmcHalfSuccessful() {
        Instant from = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant to = Instant.now();

        // 1 session of 100 seconds -> 20 expected updates
        GpsSession session = GpsSession.builder()
                .id(101L)
                .asset(assetEcg)
                .active(false)
                .startedAt(from)
                .endedAt(from.plus(100, ChronoUnit.SECONDS))
                .build();

        when(gpsSessionRepository.findSessionsInPeriod(any(), any(), any(), any()))
                .thenReturn(List.of(session));

        java.util.List<LocationHistory> updates = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            updates.add(LocationHistory.builder().asset(assetEcg).recordedAt(from.plusSeconds(i * 5)).build());
        }
        when(locationHistoryRepository.findUpdatesInPeriod(any(), any(), any(), any()))
                .thenReturn(updates);

        AmcMetricResponse response = analyticsService.calculateAmc(from, to, null, null, testUser);

        assertNotNull(response);
        assertEquals(20L, response.getExpectedGpsUpdates());
        assertEquals(10L, response.getSuccessfulGpsUpdates());
        assertEquals(50.00, response.getContinuityPercentage());
        assertEquals("MEASURED", response.getStatus());
    }

    @Test
    @DisplayName("AMC: zero expected updates -> null value and NO_DATA status")
    void testAmcZeroExpectedUpdates() {
        Instant from = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant to = Instant.now();

        // No sessions
        when(gpsSessionRepository.findSessionsInPeriod(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(locationHistoryRepository.findUpdatesInPeriod(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        AmcMetricResponse response = analyticsService.calculateAmc(from, to, null, null, testUser);

        assertNotNull(response);
        assertEquals(0L, response.getExpectedGpsUpdates());
        assertEquals(0L, response.getSuccessfulGpsUpdates());
        assertNull(response.getContinuityPercentage());
        assertEquals("NO_DATA", response.getStatus());
    }

    @Test
    @DisplayName("AMC: zero successful updates with non-zero expected -> 0.00%")
    void testAmcZeroSuccessfulUpdates() {
        Instant from = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant to = Instant.now();

        GpsSession session = GpsSession.builder()
                .id(101L)
                .asset(assetEcg)
                .active(false)
                .startedAt(from)
                .endedAt(from.plus(50, ChronoUnit.SECONDS)) // 10 expected
                .build();

        when(gpsSessionRepository.findSessionsInPeriod(any(), any(), any(), any()))
                .thenReturn(List.of(session));

        when(locationHistoryRepository.findUpdatesInPeriod(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        AmcMetricResponse response = analyticsService.calculateAmc(from, to, null, null, testUser);

        assertNotNull(response);
        assertEquals(10L, response.getExpectedGpsUpdates());
        assertEquals(0L, response.getSuccessfulGpsUpdates());
        assertEquals(0.00, response.getContinuityPercentage());
        assertEquals("MEASURED", response.getStatus());
    }

    @Test
    @DisplayName("AMC: tracking gap detection between consecutive updates")
    void testAmcTrackingGapDetection() {
        Instant t0 = Instant.parse("2026-10-01T10:00:00Z");
        Instant t1 = t0.plusSeconds(5);
        Instant t2 = t1.plusSeconds(30); // 30s gap (> 15s threshold, excess gap = 25s)

        LocationHistory lh0 = LocationHistory.builder()
                .asset(assetEcg)
                .latitude(11.497100)
                .longitude(77.278900)
                .accuracy(3.5)
                .recordedAt(t0)
                .build();

        LocationHistory lh1 = LocationHistory.builder()
                .asset(assetEcg)
                .latitude(11.497120)
                .longitude(77.278920)
                .accuracy(4.0)
                .recordedAt(t1)
                .build();

        LocationHistory lh2 = LocationHistory.builder()
                .asset(assetEcg)
                .latitude(11.497150)
                .longitude(77.278950)
                .accuracy(2.5)
                .recordedAt(t2)
                .build();

        GpsSession session = GpsSession.builder()
                .id(1L)
                .asset(assetEcg)
                .active(false)
                .startedAt(t0)
                .endedAt(t2)
                .build();

        when(gpsSessionRepository.findSessionsInPeriod(any(), any(), any(), any()))
                .thenReturn(List.of(session));
        when(locationHistoryRepository.findUpdatesInPeriod(any(), any(), any(), any()))
                .thenReturn(List.of(lh0, lh1, lh2));

        AmcMetricResponse response = analyticsService.calculateAmc(t0, t2.plusSeconds(1), null, null, testUser);

        assertNotNull(response);
        assertEquals(1, response.getTrackingGapsCount());
        assertEquals(25L, response.getTotalGapDurationSeconds());
        assertEquals(2.5, response.getBestAccuracyMeters());
        assertEquals(4.0, response.getWorstAccuracyMeters());
    }

    @Test
    @DisplayName("AMC: completed historical session (active=false) + historical telemetry is included in AMC")
    void testCompletedHistoricalSessionIncludedInAmc() {
        // Historical window from 3 days ago
        Instant tPastStart = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant tPastEnd = tPastStart.plusSeconds(300); // 5-minute session -> 60 expected updates (300/5)
        Instant queryFrom = tPastStart.minusSeconds(60);
        Instant queryTo = tPastEnd.plusSeconds(60);

        // Historical completed session with active=false and endedAt set
        GpsSession historicalSession = GpsSession.builder()
                .id(999L)
                .asset(assetEcg)
                .active(false)
                .startedAt(tPastStart)
                .endedAt(tPastEnd)
                .build();

        when(gpsSessionRepository.findSessionsInPeriod(any(), any(), any(), any()))
                .thenReturn(List.of(historicalSession));

        // 60 historical telemetry records within the window
        java.util.List<LocationHistory> historicalUpdates = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            historicalUpdates.add(LocationHistory.builder()
                    .asset(assetEcg)
                    .latitude(11.497100)
                    .longitude(77.278900)
                    .accuracy(3.0)
                    .recordedAt(tPastStart.plusSeconds(i * 5))
                    .build());
        }
        when(locationHistoryRepository.findUpdatesInPeriod(any(), any(), any(), any()))
                .thenReturn(historicalUpdates);

        AmcMetricResponse response = analyticsService.calculateAmc(queryFrom, queryTo, null, null, testUser);

        assertNotNull(response);
        assertEquals(60L, response.getExpectedGpsUpdates(), "Expected 60 updates from 300s historical session");
        assertEquals(60L, response.getSuccessfulGpsUpdates(), "Expected 60 historical telemetry updates");
        assertEquals(100.00, response.getContinuityPercentage());
        assertEquals("MEASURED", response.getStatus());
        assertEquals(1, response.getSessionCount(), "Historical session count must be 1");
    }

    // ==========================================
    // 2. HFI CALCULATION TESTS
    // ==========================================

    @Test
    @DisplayName("HFI: zero gap / minimum delay baseline produces near-zero friction score")
    void testHfiBaselineLowFriction() {
        Instant tReq = Instant.parse("2026-10-01T10:00:00Z");
        Instant tAcc = tReq.plusSeconds(60);
        Instant tStart = tAcc.plusSeconds(10); // 10s handoff delay
        Instant tComp = tStart.plusSeconds(120); // 2 min transit (120s / 3600 reference = ~3.33)

        AssetTransfer transfer = AssetTransfer.builder()
                .id(1001L)
                .asset(assetEcg)
                .fromDepartment(deptEr)
                .toDepartment(deptCard)
                .status(TransferStatus.COMPLETED)
                .requestedAt(tReq)
                .acceptedAt(tAcc)
                .startedAt(tStart)
                .completedAt(tComp)
                .build();

        when(assetTransferRepository.findCompletedTransfersInPeriod(any(), any(), any(), any()))
                .thenReturn(List.of(transfer));

        // Seamless telemetry during transit: updates every 5 seconds for full 120s
        List<LocationHistory> transitUpdates = new java.util.ArrayList<>();
        for (int sec = 5; sec <= 115; sec += 5) {
            transitUpdates.add(LocationHistory.builder().recordedAt(tStart.plusSeconds(sec)).build());
        }
        when(locationHistoryRepository.findByAssetIdAndRecordedAtBetweenOrderByRecordedAtAsc(eq(assetEcg.getId()), eq(tStart), eq(tComp)))
                .thenReturn(transitUpdates);

        HfiMetricResponse response = analyticsService.calculateHfi(tReq, tComp.plusSeconds(10), null, null, testUser);

        assertNotNull(response);
        assertEquals(1, response.getCompletedTransferCount());
        assertNotNull(response.getOverallHfi());
        assertTrue(response.getOverallHfi() < 10.0, "Expected low friction, got: " + response.getOverallHfi());
        assertEquals("MEASURED", response.getStatus());
    }

    @Test
    @DisplayName("HFI: long transfer duration and large handoff delay produces high friction score")
    void testHfiHighFriction() {
        Instant tReq = Instant.parse("2026-10-01T10:00:00Z");
        Instant tAcc = tReq.plusSeconds(60);
        Instant tStart = tAcc.plusSeconds(3000); // 50 min delay (> 45 min reference -> S_delay = 100)
        Instant tComp = tStart.plusSeconds(4000); // > 1 hour transit (> 60 min reference -> S_dur = 100)

        AssetTransfer transfer = AssetTransfer.builder()
                .id(1002L)
                .asset(assetEcg)
                .fromDepartment(deptEr)
                .toDepartment(deptCard)
                .status(TransferStatus.COMPLETED)
                .requestedAt(tReq)
                .acceptedAt(tAcc)
                .startedAt(tStart)
                .completedAt(tComp)
                .build();

        when(assetTransferRepository.findCompletedTransfersInPeriod(any(), any(), any(), any()))
                .thenReturn(List.of(transfer));

        // No telemetry during transit -> tracking gap ratio = 1.0 (S_gap = 100)
        when(locationHistoryRepository.findByAssetIdAndRecordedAtBetweenOrderByRecordedAtAsc(eq(assetEcg.getId()), eq(tStart), eq(tComp)))
                .thenReturn(Collections.emptyList());

        HfiMetricResponse response = analyticsService.calculateHfi(tReq, tComp.plusSeconds(10), null, null, testUser);

        assertNotNull(response);
        assertEquals(1, response.getCompletedTransferCount());
        assertEquals(100.0, response.getOverallHfi());
        assertEquals("MEASURED", response.getStatus());
    }

    @Test
    @DisplayName("HFI: zero completed transfers returns NO_DATA")
    void testHfiZeroCompletedTransfers() {
        Instant tReq = Instant.parse("2026-10-01T10:00:00Z");
        Instant tComp = Instant.parse("2026-10-01T12:00:00Z");

        when(assetTransferRepository.findCompletedTransfersInPeriod(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        HfiMetricResponse response = analyticsService.calculateHfi(tReq, tComp, null, null, testUser);

        assertNotNull(response);
        assertEquals(0, response.getCompletedTransferCount());
        assertNull(response.getOverallHfi());
        assertEquals("NO_DATA", response.getStatus());
    }

    // ==========================================
    // 3. GUB CALCULATION TESTS
    // ==========================================

    @Test
    @DisplayName("GUB: balanced supply and demand -> balanceScore = 0.0, status = BALANCED")
    void testGubBalanced() {
        Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant to = Instant.now();

        when(departmentRepository.findAll()).thenReturn(List.of(deptEr));
        // Department ER: 5 available assets -> Supply = 5
        List<Asset> assets = List.of(
                Asset.builder().id(1L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(2L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(3L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(4L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(5L).status(AssetStatus.AVAILABLE).build()
        );
        when(assetRepository.findByDepartmentId(deptEr.getId())).thenReturn(assets);

        // Demand: 5 inbound transfers + 0 active assigned = 5
        when(assetTransferRepository.countInboundTransfersInPeriod(eq(deptEr.getId()), any(), any())).thenReturn(5L);
        when(assetTransferRepository.countOutboundTransfersInPeriod(eq(deptEr.getId()), any(), any())).thenReturn(2L);

        GubMetricResponse response = analyticsService.calculateGub(from, to, null, testUser);

        assertNotNull(response);
        assertEquals(1, response.getDepartments().size());
        DepartmentGubDto erDto = response.getDepartments().get(0);
        assertEquals(5L, erDto.getUsableAssetCount());
        assertEquals(5L, erDto.getDemandCount());
        assertEquals(0.00, erDto.getBalanceScore());
        assertEquals("BALANCED", erDto.getStatus());
    }

    @Test
    @DisplayName("GUB: supply deficit -> negative balanceScore, status = DEFICIT")
    void testGubSupplyDeficit() {
        Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant to = Instant.now();

        when(departmentRepository.findAll()).thenReturn(List.of(deptEr));
        // Supply = 2 (2 available)
        List<Asset> assets = List.of(
                Asset.builder().id(1L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(2L).status(AssetStatus.AVAILABLE).build()
        );
        when(assetRepository.findByDepartmentId(deptEr.getId())).thenReturn(assets);

        // Demand = 8 (8 inbound transfers + 0 assigned = 8)
        when(assetTransferRepository.countInboundTransfersInPeriod(eq(deptEr.getId()), any(), any())).thenReturn(8L);
        when(assetTransferRepository.countOutboundTransfersInPeriod(eq(deptEr.getId()), any(), any())).thenReturn(0L);

        // BalanceScore = ((2 - 8) / (2 + 8)) * 100 = (-6 / 10) * 100 = -60.00
        GubMetricResponse response = analyticsService.calculateGub(from, to, null, testUser);

        assertNotNull(response);
        assertEquals(1, response.getDepartments().size());
        DepartmentGubDto erDto = response.getDepartments().get(0);
        assertEquals(2L, erDto.getUsableAssetCount());
        assertEquals(8L, erDto.getDemandCount());
        assertEquals(-60.00, erDto.getBalanceScore());
        assertEquals("DEFICIT", erDto.getStatus());
    }

    @Test
    @DisplayName("GUB: supply surplus -> positive balanceScore, status = SURPLUS")
    void testGubSupplySurplus() {
        Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant to = Instant.now();

        when(departmentRepository.findAll()).thenReturn(List.of(deptEr));
        // Supply = 9
        List<Asset> assets = List.of(
                Asset.builder().id(1L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(2L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(3L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(4L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(5L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(6L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(7L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(8L).status(AssetStatus.AVAILABLE).build(),
                Asset.builder().id(9L).status(AssetStatus.AVAILABLE).build()
        );
        when(assetRepository.findByDepartmentId(deptEr.getId())).thenReturn(assets);

        // Demand = 1
        when(assetTransferRepository.countInboundTransfersInPeriod(eq(deptEr.getId()), any(), any())).thenReturn(1L);
        when(assetTransferRepository.countOutboundTransfersInPeriod(eq(deptEr.getId()), any(), any())).thenReturn(0L);

        // BalanceScore = ((9 - 1) / (9 + 1)) * 100 = (8 / 10) * 100 = +80.00
        GubMetricResponse response = analyticsService.calculateGub(from, to, null, testUser);

        assertNotNull(response);
        assertEquals(1, response.getDepartments().size());
        DepartmentGubDto erDto = response.getDepartments().get(0);
        assertEquals(9L, erDto.getUsableAssetCount());
        assertEquals(1L, erDto.getDemandCount());
        assertEquals(80.00, erDto.getBalanceScore());
        assertEquals("SURPLUS", erDto.getStatus());
    }

    @Test
    @DisplayName("GUB: zero demand and zero supply -> balanceScore = 0.0, status = BALANCED")
    void testGubZeroSupplyAndZeroDemand() {
        Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant to = Instant.now();

        when(departmentRepository.findAll()).thenReturn(List.of(deptEr));
        when(assetRepository.findByDepartmentId(deptEr.getId())).thenReturn(Collections.emptyList());

        when(assetTransferRepository.countInboundTransfersInPeriod(eq(deptEr.getId()), any(), any())).thenReturn(0L);
        when(assetTransferRepository.countOutboundTransfersInPeriod(eq(deptEr.getId()), any(), any())).thenReturn(0L);

        GubMetricResponse response = analyticsService.calculateGub(from, to, null, testUser);

        assertNotNull(response);
        assertEquals(1, response.getDepartments().size());
        DepartmentGubDto erDto = response.getDepartments().get(0);
        assertEquals(0L, erDto.getUsableAssetCount());
        assertEquals(0L, erDto.getDemandCount());
        assertEquals(0.00, erDto.getBalanceScore());
        assertEquals("BALANCED", erDto.getStatus());
    }
}
