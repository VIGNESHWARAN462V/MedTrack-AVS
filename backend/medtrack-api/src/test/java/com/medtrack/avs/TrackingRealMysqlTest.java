package com.medtrack.avs;

import com.medtrack.avs.dto.*;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ConflictException;
import com.medtrack.avs.repository.*;
import com.medtrack.avs.service.TrackingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mariadb://localhost:3306/medtrack_db?createDatabaseIfNotExist=true&useSSL=false&timezone=UTC",
        "spring.datasource.driverClassName=org.mariadb.jdbc.Driver",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
@Transactional
class TrackingRealMysqlTest {

    @Autowired
    private TrackingService trackingService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LocationHistoryRepository locationHistoryRepository;

    @Autowired
    private GpsSessionRepository gpsSessionRepository;

    @Test
    @DisplayName("Real MySQL: Complete GPS session lifecycle, strict timestamp ordering, and rejection rules")
    void testRealMysqlGpsSessionLifecycleAndTimestampIntegrity() {
        Asset asset = assetRepository.findByAssetCode("ECG-001")
                .orElseGet(() -> assetRepository.findAll().stream().findFirst().orElseThrow());

        User adminUser = userRepository.findByEmail("admin@medtrack.local")
                .or(() -> userRepository.findByEmail("admin@medtrack.org"))
                .orElseThrow(() -> new IllegalStateException("Admin user not found in MySQL"));

        // Close any lingering active session from previous runs
        gpsSessionRepository.findByAssetIdAndActiveTrue(asset.getId()).ifPresent(s -> {
            s.setActive(false);
            s.setEndedAt(Instant.now());
            gpsSessionRepository.save(s);
        });

        // 1. REJECTION RULE: Telemetry before active session MUST be rejected
        LocationTelemetryRequest orphanReq = LocationTelemetryRequest.builder()
                .assetId(asset.getId())
                .latitude(11.341234)
                .longitude(77.717865)
                .accuracy(6.8)
                .speed(1.2)
                .heading(180.0)
                .recordedAt(Instant.now().toString())
                .build();

        assertThrows(ConflictException.class, () -> trackingService.recordLocation(orphanReq, adminUser),
                "Telemetry before session start must be rejected with ConflictException");

        // 2. START SESSION: Create active session
        GpsSessionResponse startRes = trackingService.startSession(new GpsSessionStartRequest(asset.getId()), adminUser);
        assertNotNull(startRes);
        assertTrue(startRes.isActive());
        assertNotNull(startRes.getStartedAt());
        Instant sessionStartTime = startRes.getStartedAt();

        // 3. DUPLICATE SESSION CHECK: Rapid second start must be rejected
        assertThrows(ConflictException.class, () -> trackingService.startSession(new GpsSessionStartRequest(asset.getId()), adminUser),
                "Duplicate active session start must be rejected");

        // 4. INGEST MULTIPLE TELEMETRY UPDATES INSIDE SESSION
        LocationTelemetryRequest fix1 = LocationTelemetryRequest.builder()
                .assetId(asset.getId())
                .latitude(11.496150)
                .longitude(77.278544)
                .accuracy(15.2)
                .speed(0.8)
                .heading(90.0)
                .recordedAt(Instant.now().toString())
                .build();
        LocationTelemetryResponse res1 = trackingService.recordLocation(fix1, adminUser);
        assertNotNull(res1);

        LocationTelemetryRequest fix2 = LocationTelemetryRequest.builder()
                .assetId(asset.getId())
                .latitude(11.496180)
                .longitude(77.278590)
                .accuracy(12.0)
                .speed(1.1)
                .heading(95.0)
                .recordedAt(Instant.now().toString())
                .build();
        LocationTelemetryResponse res2 = trackingService.recordLocation(fix2, adminUser);
        assertNotNull(res2);

        LocationTelemetryRequest fix3 = LocationTelemetryRequest.builder()
                .assetId(asset.getId())
                .latitude(11.496220)
                .longitude(77.278630)
                .accuracy(9.5)
                .speed(1.3)
                .heading(100.0)
                .recordedAt(Instant.now().toString())
                .build();
        LocationTelemetryResponse res3 = trackingService.recordLocation(fix3, adminUser);
        assertNotNull(res3);

        // 5. VERIFY CHRONOLOGICAL ORDER AND INVARIANTS: session.startedAt <= telemetry1 <= telemetry2 <= telemetry3
        List<LocationHistory> ascHistory = locationHistoryRepository.findByAssetIdOrderByRecordedAtAsc(asset.getId());
        assertFalse(ascHistory.isEmpty());
        assertTrue(ascHistory.size() >= 3);

        LocationHistory h1 = ascHistory.get(ascHistory.size() - 3);
        LocationHistory h2 = ascHistory.get(ascHistory.size() - 2);
        LocationHistory h3 = ascHistory.get(ascHistory.size() - 1);

        assertFalse(h1.getRecordedAt().isBefore(sessionStartTime), "telemetry 1 must be >= session start");
        assertFalse(h2.getRecordedAt().isBefore(h1.getRecordedAt()), "telemetry 2 must be >= telemetry 1");
        assertFalse(h3.getRecordedAt().isBefore(h2.getRecordedAt()), "telemetry 3 must be >= telemetry 2");

        // 6. VERIFY ASSET LAST_SEEN corresponds to latest telemetry
        Asset updatedAsset = assetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(h3.getRecordedAt(), updatedAsset.getLastSeen());
        assertEquals(AssetStatus.MOVING, updatedAsset.getStatus());

        // 7. STOP TRACKING
        GpsSession activeSession = gpsSessionRepository.findByAssetIdAndActiveTrue(asset.getId()).orElseThrow();
        GpsSessionResponse stopRes = trackingService.stopSession(activeSession.getId(), adminUser);
        assertFalse(stopRes.isActive());
        assertNotNull(stopRes.getEndedAt());
        Instant sessionEndTime = stopRes.getEndedAt();

        // 8. VERIFY INVARIANT: telemetry <= session.endedAt
        assertFalse(sessionEndTime.isBefore(h3.getRecordedAt()), "session endedAt must be >= last telemetry recordedAt");

        // 9. REJECTION RULE: Post-stop telemetry submission MUST be rejected
        LocationTelemetryRequest postStopReq = LocationTelemetryRequest.builder()
                .assetId(asset.getId())
                .latitude(11.496300)
                .longitude(77.278700)
                .accuracy(10.0)
                .speed(0.0)
                .heading(0.0)
                .recordedAt(Instant.now().toString())
                .build();
        assertThrows(ConflictException.class, () -> trackingService.recordLocation(postStopReq, adminUser),
                "Telemetry after session closure must be rejected");

        // 10. POST-STOP ASSET STATUS: active assignment preserves ASSIGNED status
        Asset postStopAsset = assetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(AssetStatus.ASSIGNED, postStopAsset.getStatus());
    }

    @Test
    @DisplayName("Real MySQL: Validate defensive coordinates and validation failure rules")
    void testDefensiveValidation() {
        Asset asset = assetRepository.findByAssetCode("ECG-001")
                .orElseGet(() -> assetRepository.findAll().stream().findFirst().orElseThrow());

        User adminUser = userRepository.findByEmail("admin@medtrack.local")
                .or(() -> userRepository.findByEmail("admin@medtrack.org"))
                .orElseThrow(() -> new IllegalStateException("Admin user not found in MySQL"));

        // Latitude out of bounds
        LocationTelemetryRequest invalidLat = LocationTelemetryRequest.builder()
                .assetId(asset.getId())
                .latitude(195.0)
                .longitude(77.0)
                .accuracy(5.0)
                .recordedAt(Instant.now().toString())
                .build();
        assertThrows(BadRequestException.class, () -> trackingService.recordLocation(invalidLat, adminUser));

        // Negative accuracy
        LocationTelemetryRequest invalidAcc = LocationTelemetryRequest.builder()
                .assetId(asset.getId())
                .latitude(11.0)
                .longitude(77.0)
                .accuracy(-5.0)
                .recordedAt(Instant.now().toString())
                .build();
        assertThrows(BadRequestException.class, () -> trackingService.recordLocation(invalidAcc, adminUser));
    }

    @Test
    @DisplayName("Real MySQL: Verify unauthorized user tracking rejection")
    void testUnauthorizedUserTrackingRejection() {
        Asset asset = assetRepository.findByAssetCode("ECG-001")
                .orElseGet(() -> assetRepository.findAll().stream().findFirst().orElseThrow());

        Role staffRole = roleRepository.findByName(RoleType.STAFF).orElseGet(() ->
                roleRepository.save(Role.builder().name(RoleType.STAFF).description("Staff").build()));

        User unauthorizedStaff = User.builder()
                .id(99999L)
                .email("unauthorized.staff@medtrack.local")
                .name("Unauthorized Staff")
                .role(staffRole)
                .enabled(true)
                .build();

        assertThrows(AccessDeniedException.class, () ->
                trackingService.startSession(new GpsSessionStartRequest(asset.getId()), unauthorizedStaff));
    }

    @Test
    @DisplayName("Real MySQL: Verify directional history queries (ASC vs DESC)")
    void testDirectionalHistoryQuery() {
        Asset asset = assetRepository.findByAssetCode("ECG-001")
                .orElseGet(() -> assetRepository.findAll().stream().findFirst().orElseThrow());

        User adminUser = userRepository.findByEmail("admin@medtrack.local")
                .or(() -> userRepository.findByEmail("admin@medtrack.org"))
                .orElseThrow(() -> new IllegalStateException("Admin user not found in MySQL"));

        List<LocationHistoryResponse> ascList = trackingService.getHistory(asset.getId(), 10, "asc", adminUser);
        List<LocationHistoryResponse> descList = trackingService.getHistory(asset.getId(), 10, "desc", adminUser);

        assertNotNull(ascList);
        assertNotNull(descList);
        if (ascList.size() >= 2) {
            assertFalse(ascList.get(0).getRecordedAt().isAfter(ascList.get(ascList.size() - 1).getRecordedAt()),
                    "ASC order must have oldest first");
            assertFalse(descList.get(0).getRecordedAt().isBefore(descList.get(descList.size() - 1).getRecordedAt()),
                    "DESC order must have newest first");
        }
    }
}
