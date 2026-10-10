package com.medtrack.avs;

import com.medtrack.avs.dto.GpsSessionStartRequest;
import com.medtrack.avs.dto.LocationTelemetryRequest;
import com.medtrack.avs.dto.LocationTelemetryResponse;
import com.medtrack.avs.dto.TrackingTelemetryEvent;
import com.medtrack.avs.entity.Asset;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.event.TelemetryPersistedEvent;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.exception.ConflictException;
import com.medtrack.avs.repository.AssetAssignmentRepository;
import com.medtrack.avs.repository.AssetRepository;
import com.medtrack.avs.repository.GpsSessionRepository;
import com.medtrack.avs.repository.LocationHistoryRepository;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.security.CustomUserDetailsService;
import com.medtrack.avs.security.JwtService;
import com.medtrack.avs.service.TrackingService;
import com.medtrack.avs.websocket.TrackingWebSocketBroadcaster;
import com.medtrack.avs.websocket.WebSocketAuthChannelInterceptor;
import com.medtrack.avs.websocket.WebSocketConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
class WebSocketTrackingIntegrationTest {

    @Autowired
    private WebSocketConfig webSocketConfig;

    @Autowired
    private WebSocketAuthChannelInterceptor authInterceptor;

    @Autowired
    private TrackingWebSocketBroadcaster trackingWebSocketBroadcaster;

    @Autowired
    private TrackingService trackingService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GpsSessionRepository gpsSessionRepository;

    @Autowired
    private LocationHistoryRepository locationHistoryRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    private final MessageChannel dummyChannel = mock(MessageChannel.class);

    private User adminUser;
    private User supervisorUser;
    private User staffUser;
    private Asset testAsset1;
    private Asset testAsset2;
    private String adminToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.findByEmail("admin@medtrack.local")
                .or(() -> userRepository.findByEmail("admin@medtrack.org"))
                .orElseThrow();
        supervisorUser = userRepository.findByEmail("supervisor@medtrack.local")
                .orElseThrow();
        staffUser = userRepository.findByEmail("staff@medtrack.local")
                .orElseThrow();

        testAsset1 = assetRepository.findByAssetCode("ECG-001").orElseThrow();
        testAsset2 = assetRepository.findByAssetCode("DEF-001")
                .or(() -> assetRepository.findAll().stream().filter(a -> !a.getId().equals(testAsset1.getId())).findFirst())
                .orElseThrow();

        // Close any lingering active sessions for test assets
        gpsSessionRepository.findByAssetIdAndActiveTrue(testAsset1.getId()).ifPresent(s -> {
            s.setActive(false);
            gpsSessionRepository.save(s);
        });
        gpsSessionRepository.findByAssetIdAndActiveTrue(testAsset2.getId()).ifPresent(s -> {
            s.setActive(false);
            gpsSessionRepository.save(s);
        });

        UserDetails adminDetails = userDetailsService.loadUserByUsername(adminUser.getEmail());
        UserDetails staffDetails = userDetailsService.loadUserByUsername(staffUser.getEmail());

        adminToken = jwtService.generateToken(adminDetails);
        staffToken = jwtService.generateToken(staffDetails);

        reset(messagingTemplate);
    }

    @AfterEach
    void tearDown() {
        gpsSessionRepository.findByAssetIdAndActiveTrue(testAsset1.getId()).ifPresent(s -> {
            s.setActive(false);
            gpsSessionRepository.save(s);
        });
        gpsSessionRepository.findByAssetIdAndActiveTrue(testAsset2.getId()).ifPresent(s -> {
            s.setActive(false);
            gpsSessionRepository.save(s);
        });
    }

    @Test
    @DisplayName("WebSocket: Configuration bean exists")
    void testWebSocketEndpointConfigured() {
        assertNotNull(webSocketConfig);
        assertNotNull(authInterceptor);
        assertNotNull(trackingWebSocketBroadcaster);
    }

    @Test
    @DisplayName("STOMP CONNECT: Valid JWT authenticates successfully")
    void testStompConnectValidJwt() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer " + adminToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result);

        StompHeaderAccessor resAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resAccessor.getUser());
        assertEquals(adminUser.getEmail(), resAccessor.getUser().getName());
    }

    @Test
    @DisplayName("STOMP CONNECT: Missing Authorization header is rejected")
    void testStompConnectMissingAuthHeader() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> authInterceptor.preSend(message, dummyChannel));
    }

    @Test
    @DisplayName("STOMP CONNECT: Invalid JWT is rejected")
    void testStompConnectInvalidJwt() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer invalid-tampered-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(BadCredentialsException.class,
                () -> authInterceptor.preSend(message, dummyChannel));
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: ADMIN can subscribe to global tracking /topic/tracking/assets")
    void testStompSubscribeAdminGlobal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/tracking/assets");
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                adminUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: SUPERVISOR can subscribe to global tracking /topic/tracking/assets")
    void testStompSubscribeSupervisorGlobal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/tracking/assets");
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                supervisorUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: STAFF subscription to global /topic/tracking/assets is rejected")
    void testStompSubscribeStaffGlobalRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/tracking/assets");
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                staffUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class,
                () -> authInterceptor.preSend(message, dummyChannel));
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: STAFF subscription to assigned asset /topic/tracking/assets/{assetId} succeeds")
    void testStompSubscribeStaffAssignedAsset() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/tracking/assets/" + testAsset1.getId());
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                staffUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result);
    }

    @Autowired
    private AssetAssignmentRepository assetAssignmentRepository;

    @Test
    @DisplayName("STOMP SUBSCRIBE: STAFF subscription to unassigned asset is rejected")
    void testStompSubscribeStaffUnassignedAssetRejected() {
        // MON-001 is assigned to supervisor, not staff
        Asset unassignedAsset = assetRepository.findByAssetCode("MON-001").orElseThrow();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/tracking/assets/" + unassignedAsset.getId());
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                staffUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class,
                () -> authInterceptor.preSend(message, dummyChannel));
    }

    @Test
    @DisplayName("Accepted telemetry publishes TrackingTelemetryEvent with exact persisted timestamp")
    void testAcceptedTelemetryPublishesEventWithPersistedTimestamp() {
        trackingService.startSession(new GpsSessionStartRequest(testAsset1.getId()), adminUser);

        Instant testTime = Instant.now();
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset1.getId())
                .latitude(11.496155)
                .longitude(77.278545)
                .accuracy(12.0)
                .speed(1.2)
                .heading(90.0)
                .recordedAt(testTime.toString())
                .build();

        LocationTelemetryResponse response = trackingService.recordLocation(request, adminUser);
        assertNotNull(response);
        assertNotNull(response.getRecordedAt());

        // Verify broadcaster sends event
        ArgumentCaptor<TrackingTelemetryEvent> captor = ArgumentCaptor.forClass(TrackingTelemetryEvent.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/tracking/assets"), captor.capture());

        TrackingTelemetryEvent lastEvent = captor.getValue();
        assertEquals(testAsset1.getId(), lastEvent.getAssetId());
        assertEquals(11.496155, lastEvent.getLatitude());
        assertEquals(77.278545, lastEvent.getLongitude());
        assertEquals(response.getRecordedAt(), lastEvent.getRecordedAt());

        // Also verified sent to asset-specific topic
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/tracking/assets/" + testAsset1.getId()), any(TrackingTelemetryEvent.class));
    }

    @Test
    @DisplayName("Multiple assets telemetry broadcasts to respective topics")
    void testMultipleAssetEvents() {
        trackingService.startSession(new GpsSessionStartRequest(testAsset1.getId()), adminUser);
        trackingService.startSession(new GpsSessionStartRequest(testAsset2.getId()), adminUser);

        LocationTelemetryRequest req1 = LocationTelemetryRequest.builder()
                .assetId(testAsset1.getId())
                .latitude(11.496155)
                .longitude(77.278545)
                .accuracy(10.0)
                .recordedAt(Instant.now().toString())
                .build();
        LocationTelemetryRequest req2 = LocationTelemetryRequest.builder()
                .assetId(testAsset2.getId())
                .latitude(11.497000)
                .longitude(77.279000)
                .accuracy(15.0)
                .recordedAt(Instant.now().toString())
                .build();

        trackingService.recordLocation(req1, adminUser);
        trackingService.recordLocation(req2, adminUser);

        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/tracking/assets/" + testAsset1.getId()), any(TrackingTelemetryEvent.class));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/tracking/assets/" + testAsset2.getId()), any(TrackingTelemetryEvent.class));
    }

    @Test
    @DisplayName("Event ordering: consecutive telemetry events preserve chronological order")
    void testEventOrdering() throws InterruptedException {
        trackingService.startSession(new GpsSessionStartRequest(testAsset1.getId()), adminUser);

        LocationTelemetryRequest req1 = LocationTelemetryRequest.builder()
                .assetId(testAsset1.getId())
                .latitude(11.496100)
                .longitude(77.278500)
                .accuracy(10.0)
                .recordedAt(Instant.now().toString())
                .build();
        LocationTelemetryResponse res1 = trackingService.recordLocation(req1, adminUser);

        Thread.sleep(10);

        LocationTelemetryRequest req2 = LocationTelemetryRequest.builder()
                .assetId(testAsset1.getId())
                .latitude(11.496200)
                .longitude(77.278600)
                .accuracy(8.0)
                .recordedAt(Instant.now().toString())
                .build();
        LocationTelemetryResponse res2 = trackingService.recordLocation(req2, adminUser);

        assertTrue(res2.getRecordedAt().isAfter(res1.getRecordedAt()));
    }

    @Test
    @DisplayName("Rejected orphan telemetry throws ConflictException and does not broadcast")
    void testRejectedOrphanTelemetryNoBroadcast() {
        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset1.getId())
                .latitude(11.496155)
                .longitude(77.278545)
                .accuracy(12.0)
                .recordedAt(Instant.now().toString())
                .build();

        assertThrows(ConflictException.class, () -> trackingService.recordLocation(request, adminUser));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/tracking/assets"), any(Object.class));
    }

    @Test
    @DisplayName("Rejected post-stop telemetry throws ConflictException and does not broadcast")
    void testRejectedPostStopTelemetryNoBroadcast() {
        var startRes = trackingService.startSession(new GpsSessionStartRequest(testAsset1.getId()), adminUser);
        trackingService.stopSession(startRes.getId(), adminUser);
        reset(messagingTemplate);

        LocationTelemetryRequest request = LocationTelemetryRequest.builder()
                .assetId(testAsset1.getId())
                .latitude(11.496155)
                .longitude(77.278545)
                .accuracy(12.0)
                .recordedAt(Instant.now().toString())
                .build();

        assertThrows(ConflictException.class, () -> trackingService.recordLocation(request, adminUser));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/tracking/assets"), any(Object.class));
    }

    @Test
    @DisplayName("Rejected invalid coordinates throw BadRequestException and do not broadcast")
    void testRejectedInvalidCoordinatesNoBroadcast() {
        trackingService.startSession(new GpsSessionStartRequest(testAsset1.getId()), adminUser);
        reset(messagingTemplate);

        LocationTelemetryRequest invalidLat = LocationTelemetryRequest.builder()
                .assetId(testAsset1.getId())
                .latitude(999.0) // Invalid: > 90
                .longitude(77.278545)
                .accuracy(12.0)
                .recordedAt(Instant.now().toString())
                .build();

        assertThrows(BadRequestException.class, () -> trackingService.recordLocation(invalidLat, adminUser));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/tracking/assets"), any(Object.class));
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: ADMIN and SUPERVISOR can subscribe to /topic/alerts")
    void testStompSubscribeAlertsAdminAndSupervisor() {
        StompHeaderAccessor accessorAdmin = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessorAdmin.setDestination("/topic/alerts");
        accessorAdmin.setUser(new UsernamePasswordAuthenticationToken(
                adminUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Message<byte[]> msgAdmin = MessageBuilder.createMessage(new byte[0], accessorAdmin.getMessageHeaders());
        assertNotNull(authInterceptor.preSend(msgAdmin, dummyChannel));

        StompHeaderAccessor accessorSuper = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessorSuper.setDestination("/topic/alerts");
        accessorSuper.setUser(new UsernamePasswordAuthenticationToken(
                supervisorUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))));
        Message<byte[]> msgSuper = MessageBuilder.createMessage(new byte[0], accessorSuper.getMessageHeaders());
        assertNotNull(authInterceptor.preSend(msgSuper, dummyChannel));
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: STAFF subscription to global /topic/alerts is rejected")
    void testStompSubscribeStaffGlobalAlertsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/alerts");
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                staffUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> authInterceptor.preSend(message, dummyChannel));
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: STAFF subscription to assigned asset /topic/alerts/{assetId} succeeds")
    void testStompSubscribeStaffAssignedAlertsSucceeds() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/alerts/" + testAsset1.getId());
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                staffUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertNotNull(authInterceptor.preSend(message, dummyChannel));
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: STAFF subscription to unassigned asset /topic/alerts/{assetId} is rejected")
    void testStompSubscribeStaffUnassignedAlertsRejected() {
        Asset unassignedAsset = assetRepository.findByAssetCode("MON-001").orElseThrow();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/alerts/" + unassignedAsset.getId());
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                staffUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> authInterceptor.preSend(message, dummyChannel));
    }

    @Test
    @DisplayName("STOMP BROADCAST: AlertPersistedEvent broadcasts to /topic/alerts and /topic/alerts/{assetId}")
    void testAlertPersistedEventBroadcastsOverStomp() {
        Instant testTime = Instant.now();
        com.medtrack.avs.dto.AlertWebSocketEvent alertEvent = com.medtrack.avs.dto.AlertWebSocketEvent.builder()
                .eventType("GEOFENCE_ALERT")
                .alertId(777L)
                .assetId(testAsset1.getId())
                .assetCode(testAsset1.getAssetCode())
                .geofenceId(99L)
                .geofenceName("Cardio Ward")
                .department("Cardiology")
                .alertType("GEOFENCE_ENTER")
                .severity("INFO")
                .message("Asset entered Cardio Ward")
                .latitude(11.496155)
                .longitude(77.278545)
                .distanceMeters(10.5)
                .occurredAt(testTime)
                .build();

        trackingWebSocketBroadcaster.onAlertPersisted(
                new com.medtrack.avs.event.AlertPersistedEvent(this, alertEvent, null));

        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/alerts"), eq(alertEvent));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/alerts/" + testAsset1.getId()), eq(alertEvent));
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: ADMIN can subscribe to global transfers /topic/transfers")
    void testStompSubscribeAdminTransfersGlobal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/transfers");
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                adminUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertNotNull(authInterceptor.preSend(message, dummyChannel));
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: STAFF subscription to global /topic/transfers is rejected")
    void testStompSubscribeStaffTransfersGlobalRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/transfers");
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                staffUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> authInterceptor.preSend(message, dummyChannel));
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: STAFF subscription to assigned asset /topic/transfers/{assetId} succeeds")
    void testStompSubscribeStaffAssignedAssetTransfers() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/transfers/" + testAsset1.getId());
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                staffUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertNotNull(authInterceptor.preSend(message, dummyChannel));
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE: STAFF subscription to unassigned asset /topic/transfers/{assetId} is rejected")
    void testStompSubscribeStaffUnassignedAssetTransfersRejected() {
        Asset unassignedAsset = assetRepository.findByAssetCode("MON-001").orElseThrow();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/transfers/" + unassignedAsset.getId());
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                staffUser.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> authInterceptor.preSend(message, dummyChannel));
    }

    @Test
    @DisplayName("STOMP BROADCAST: TransferPersistedEvent broadcasts to /topic/transfers and /topic/transfers/{assetId}")
    void testTransferPersistedEventBroadcastsOverStomp() {
        Instant testTime = Instant.now();
        com.medtrack.avs.dto.TransferWebSocketEvent transferEvent = com.medtrack.avs.dto.TransferWebSocketEvent.builder()
                .eventType("TRANSFER_COMPLETED")
                .transferId(888L)
                .assetId(testAsset1.getId())
                .assetCode(testAsset1.getAssetCode())
                .sourceDepartmentId(6L)
                .sourceDepartmentName("Cardiology")
                .destinationDepartmentId(4L)
                .destinationDepartmentName("Emergency")
                .sourceDepartment("Cardiology")
                .destinationDepartment("Emergency")
                .sourceUserId(3L)
                .sourceUserName("Staff Member")
                .destinationUserId(2L)
                .destinationUserName("ER Nurse")
                .sourceUser("Staff Member")
                .destinationUser("ER Nurse")
                .requestedByUserId(1L)
                .requestedByUserName("Admin")
                .status(com.medtrack.avs.entity.TransferStatus.COMPLETED)
                .requestedBy("Admin")
                .occurredAt(testTime)
                .build();

        trackingWebSocketBroadcaster.onTransferPersisted(
                new com.medtrack.avs.event.TransferPersistedEvent(this, transferEvent, null));

        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/transfers"), eq(transferEvent));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/transfers/" + testAsset1.getId()), eq(transferEvent));
    }
}
