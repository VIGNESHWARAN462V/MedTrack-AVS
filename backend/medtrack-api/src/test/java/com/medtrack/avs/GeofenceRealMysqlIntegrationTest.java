package com.medtrack.avs;

import com.medtrack.avs.dto.*;
import com.medtrack.avs.entity.*;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ConflictException;
import com.medtrack.avs.repository.*;
import com.medtrack.avs.service.AlertService;
import com.medtrack.avs.service.GeofenceService;
import com.medtrack.avs.service.TrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
class GeofenceRealMysqlIntegrationTest {

    @Autowired
    private GeofenceService geofenceService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private TrackingService trackingService;

    @Autowired
    private GeofenceRepository geofenceRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AssetGeofenceStateRepository stateRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GpsSessionRepository gpsSessionRepository;

    private User adminUser;
    private User supervisorUser;
    private User staffUser;
    private Asset testAsset;
    private Department cardDept;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.findByEmail("admin@medtrack.local")
                .or(() -> userRepository.findByEmail("admin@medtrack.org"))
                .orElseThrow();
        supervisorUser = userRepository.findByEmail("supervisor@medtrack.local")
                .orElseThrow();
        staffUser = userRepository.findByEmail("staff@medtrack.local")
                .orElseThrow();

        testAsset = assetRepository.findByAssetCode("ECG-001").orElseThrow();
        cardDept = departmentRepository.findByCode("CARD").orElseThrow();

        // Close any active session for test asset
        gpsSessionRepository.findByAssetIdAndActiveTrue(testAsset.getId()).ifPresent(s -> {
            s.setActive(false);
            gpsSessionRepository.save(s);
        });
    }

    @Test
    @DisplayName("Geofence CRUD: Create, read, update, delete with validation and authorization")
    void testGeofenceCrudLifecycle() {
        // 1. Create Geofence as ADMIN
        GeofenceCreateRequest createReq = GeofenceCreateRequest.builder()
                .name("Test Ward Zone")
                .departmentId(cardDept.getId())
                .centerLatitude(11.496155)
                .centerLongitude(77.278545)
                .radiusMeters(60.0)
                .active(true)
                .build();

        GeofenceResponse created = geofenceService.createGeofence(createReq, adminUser);
        assertNotNull(created.getId());
        assertEquals("Test Ward Zone", created.getName());
        assertEquals(60.0, created.getRadiusMeters());
        assertTrue(created.isActive());

        // 2. Read by ID
        GeofenceResponse fetched = geofenceService.getGeofenceById(created.getId());
        assertEquals(created.getId(), fetched.getId());
        assertEquals(created.getName(), fetched.getName());

        // 3. Update as SUPERVISOR
        GeofenceUpdateRequest updateReq = GeofenceUpdateRequest.builder()
                .name("Updated Ward Zone")
                .radiusMeters(75.0)
                .build();
        GeofenceResponse updated = geofenceService.updateGeofence(created.getId(), updateReq, supervisorUser);
        assertEquals("Updated Ward Zone", updated.getName());
        assertEquals(75.0, updated.getRadiusMeters());

        // 4. Delete as STAFF should be rejected
        assertThrows(AccessDeniedException.class, () -> geofenceService.deleteGeofence(created.getId(), staffUser));

        // 5. Delete as ADMIN succeeds
        geofenceService.deleteGeofence(created.getId(), adminUser);
        assertFalse(geofenceRepository.existsById(created.getId()));
    }

    @Test
    @DisplayName("Geofence Validation: Reject invalid latitude, longitude, and radius")
    void testGeofenceCoordinateAndRadiusValidation() {
        // Invalid latitude > 90
        GeofenceCreateRequest invalidLat = GeofenceCreateRequest.builder()
                .name("Invalid Lat Zone")
                .centerLatitude(95.0)
                .centerLongitude(77.278545)
                .radiusMeters(50.0)
                .build();
        assertThrows(BadRequestException.class, () -> geofenceService.createGeofence(invalidLat, adminUser));

        // Invalid longitude > 180
        GeofenceCreateRequest invalidLon = GeofenceCreateRequest.builder()
                .name("Invalid Lon Zone")
                .centerLatitude(11.496155)
                .centerLongitude(195.0)
                .radiusMeters(50.0)
                .build();
        assertThrows(BadRequestException.class, () -> geofenceService.createGeofence(invalidLon, adminUser));

        // Invalid radius <= 0
        GeofenceCreateRequest invalidRadius = GeofenceCreateRequest.builder()
                .name("Zero Radius Zone")
                .centerLatitude(11.496155)
                .centerLongitude(77.278545)
                .radiusMeters(0.0)
                .build();
        assertThrows(BadRequestException.class, () -> geofenceService.createGeofence(invalidRadius, adminUser));
    }

    @Test
    @DisplayName("Active vs Inactive Geofences: Inactive geofences are excluded from evaluation")
    void testInactiveGeofenceExcluded() {
        Geofence inactiveGeofence = Geofence.builder()
                .name("Inactive Zone")
                .department(cardDept)
                .centerLatitude(11.496155)
                .centerLongitude(77.278545)
                .radiusMeters(100.0)
                .active(false)
                .build();
        inactiveGeofence = geofenceRepository.save(inactiveGeofence);

        List<GeofenceResponse> activeList = geofenceService.getActiveGeofences();
        Long inactiveId = inactiveGeofence.getId();
        assertTrue(activeList.stream().noneMatch(g -> g.getId().equals(inactiveId)));
    }

    @Test
    @DisplayName("End-to-End Transition: Telemetry moves OUTSIDE -> INSIDE (ENTER) and INSIDE -> OUTSIDE (EXIT) with duplicate suppression")
    void testEndToEndGeofenceTransitionsAndAlerts() {
        // Create an isolated test geofence for deterministic evaluation far from seeded geofences
        Geofence testGeo = geofenceRepository.save(Geofence.builder()
                .name("Integration Isolated Test Zone")
                .department(cardDept)
                .centerLatitude(12.500000)
                .centerLongitude(78.500000)
                .radiusMeters(50.0)
                .active(true)
                .build());

        // Start GPS session
        trackingService.startSession(new GpsSessionStartRequest(testAsset.getId()), adminUser);

        long initialAlertCount = alertRepository.count();

        // 1. Initial fix far OUTSIDE (~11km north)
        // State transition: UNKNOWN -> OUTSIDE (No alert should be created)
        LocationTelemetryRequest fix1 = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(12.600000)
                .longitude(78.500000)
                .accuracy(10.0)
                .recordedAt(Instant.now().toString())
                .build();
        trackingService.recordLocation(fix1, adminUser);

        AssetGeofenceState state1 = stateRepository.findByAssetIdAndGeofenceId(testAsset.getId(), testGeo.getId())
                .orElseThrow();
        assertEquals(GeofenceStatus.OUTSIDE, state1.getState());
        assertEquals(0, alertRepository.findByGeofenceIdOrderByOccurredAtDesc(testGeo.getId()).size(), "UNKNOWN -> OUTSIDE must NOT trigger false EXIT alert");

        // 2. Second fix also OUTSIDE
        // State transition: OUTSIDE -> OUTSIDE (No alert)
        LocationTelemetryRequest fix2 = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(12.600100)
                .longitude(78.500000)
                .accuracy(8.0)
                .recordedAt(Instant.now().toString())
                .build();
        trackingService.recordLocation(fix2, adminUser);
        assertEquals(0, alertRepository.findByGeofenceIdOrderByOccurredAtDesc(testGeo.getId()).size(), "OUTSIDE -> OUTSIDE must suppress duplicates");

        // 3. Third fix moves INSIDE (exact center)
        // State transition: OUTSIDE -> INSIDE (ENTER alert)
        LocationTelemetryRequest fix3 = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(12.500000)
                .longitude(78.500000)
                .accuracy(5.0)
                .recordedAt(Instant.now().toString())
                .build();
        LocationTelemetryResponse res3 = trackingService.recordLocation(fix3, adminUser);

        List<Alert> alerts = alertRepository.findByGeofenceIdOrderByOccurredAtDesc(testGeo.getId());
        assertEquals(1, alerts.size(), "OUTSIDE -> INSIDE must trigger exactly 1 ENTER alert");
        Alert enterAlert = alerts.get(0);
        assertEquals(AlertType.GEOFENCE_ENTER, enterAlert.getType());
        assertEquals(AlertSeverity.INFO, enterAlert.getSeverity());
        assertEquals(testGeo.getId(), enterAlert.getGeofence().getId());
        assertEquals(res3.getRecordedAt(), enterAlert.getOccurredAt(), "Alert occurredAt must equal authoritative telemetry Instant");

        AssetGeofenceState state3 = stateRepository.findByAssetIdAndGeofenceId(testAsset.getId(), testGeo.getId())
                .orElseThrow();
        assertEquals(GeofenceStatus.INSIDE, state3.getState());

        // 4. Fourth fix remains INSIDE
        // State transition: INSIDE -> INSIDE (No alert)
        LocationTelemetryRequest fix4 = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(12.500005)
                .longitude(78.500000)
                .accuracy(5.0)
                .recordedAt(Instant.now().toString())
                .build();
        trackingService.recordLocation(fix4, adminUser);
        assertEquals(1, alertRepository.findByGeofenceIdOrderByOccurredAtDesc(testGeo.getId()).size(), "INSIDE -> INSIDE must suppress duplicates");

        // 5. Fifth fix moves OUTSIDE (~11km north)
        // State transition: INSIDE -> OUTSIDE (EXIT alert)
        LocationTelemetryRequest fix5 = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(12.600000)
                .longitude(78.500000)
                .accuracy(10.0)
                .recordedAt(Instant.now().toString())
                .build();
        LocationTelemetryResponse res5 = trackingService.recordLocation(fix5, adminUser);

        List<Alert> updatedAlerts = alertRepository.findByGeofenceIdOrderByOccurredAtDesc(testGeo.getId());
        assertEquals(2, updatedAlerts.size(), "INSIDE -> OUTSIDE must trigger exactly 1 EXIT alert");
        Alert exitAlert = updatedAlerts.get(0);
        assertEquals(AlertType.GEOFENCE_EXIT, exitAlert.getType());
        assertEquals(AlertSeverity.WARNING, exitAlert.getSeverity());
        assertEquals(testGeo.getId(), exitAlert.getGeofence().getId());
        assertEquals(res5.getRecordedAt(), exitAlert.getOccurredAt(), "Alert occurredAt must equal authoritative telemetry Instant");

        AssetGeofenceState state5 = stateRepository.findByAssetIdAndGeofenceId(testAsset.getId(), testGeo.getId())
                .orElseThrow();
        assertEquals(GeofenceStatus.OUTSIDE, state5.getState());
    }

    @Test
    @DisplayName("Rejected Telemetry: Orphan and post-stop telemetry are rejected and generate NO geofence alerts")
    void testRejectedTelemetryGeneratesNoAlerts() {
        long initialAlertCount = alertRepository.count();

        // 1. Orphan telemetry without session
        LocationTelemetryRequest orphanReq = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.496155)
                .longitude(77.278545)
                .accuracy(10.0)
                .recordedAt(Instant.now().toString())
                .build();

        assertThrows(ConflictException.class, () -> trackingService.recordLocation(orphanReq, adminUser));
        assertEquals(initialAlertCount, alertRepository.count());

        // 2. Start and stop session
        var session = trackingService.startSession(new GpsSessionStartRequest(testAsset.getId()), adminUser);
        trackingService.stopSession(session.getId(), adminUser);

        // 3. Post-stop telemetry
        assertThrows(ConflictException.class, () -> trackingService.recordLocation(orphanReq, adminUser));
        assertEquals(initialAlertCount, alertRepository.count());
    }

    @Test
    @DisplayName("Alert Resolution: Alerts can be queried and resolved")
    void testAlertResolution() {
        // Start session and create an alert
        trackingService.startSession(new GpsSessionStartRequest(testAsset.getId()), adminUser);
        LocationTelemetryRequest fix = LocationTelemetryRequest.builder()
                .assetId(testAsset.getId())
                .latitude(11.496155)
                .longitude(77.278545)
                .accuracy(5.0)
                .recordedAt(Instant.now().toString())
                .build();
        trackingService.recordLocation(fix, adminUser);

        List<AlertResponse> unresolved = alertService.getAlerts(false, testAsset.getId(), null, adminUser);
        assertFalse(unresolved.isEmpty());

        Long alertId = unresolved.get(0).getId();
        AlertResponse resolved = alertService.resolveAlert(alertId, adminUser);
        assertTrue(resolved.isResolved());
        assertNotNull(resolved.getResolvedAt());
    }
}
