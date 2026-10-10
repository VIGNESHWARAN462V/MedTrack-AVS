package com.medtrack.avs;

import com.medtrack.avs.entity.*;
import com.medtrack.avs.event.AlertPersistedEvent;
import com.medtrack.avs.repository.AlertRepository;
import com.medtrack.avs.repository.AssetGeofenceStateRepository;
import com.medtrack.avs.repository.GeofenceRepository;
import com.medtrack.avs.service.GeofenceCalculator;
import com.medtrack.avs.service.GeofenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeofenceTransitionTest {

    private GeofenceRepository geofenceRepository;
    private AlertRepository alertRepository;
    private AssetGeofenceStateRepository stateRepository;
    private GeofenceCalculator calculator;
    private ApplicationEventPublisher eventPublisher;
    private GeofenceService geofenceService;

    private Asset testAsset;
    private Geofence testGeofence;

    @BeforeEach
    void setUp() {
        geofenceRepository = mock(GeofenceRepository.class);
        alertRepository = mock(AlertRepository.class);
        stateRepository = mock(AssetGeofenceStateRepository.class);
        calculator = new GeofenceCalculator();
        eventPublisher = mock(ApplicationEventPublisher.class);

        geofenceService = new GeofenceService(
                geofenceRepository, null, alertRepository, stateRepository, calculator, eventPublisher);

        testAsset = Asset.builder()
                .id(100L)
                .assetCode("ECG-001")
                .assetName("Portable ECG")
                .build();

        testGeofence = Geofence.builder()
                .id(200L)
                .name("Cardiology Ward")
                .centerLatitude(11.496155)
                .centerLongitude(77.278545)
                .radiusMeters(50.0)
                .active(true)
                .build();

        when(geofenceRepository.findByActiveTrue()).thenReturn(List.of(testGeofence));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId(999L);
            return a;
        });
    }

    @Test
    @DisplayName("Transition: UNKNOWN -> INSIDE triggers GEOFENCE_ENTER alert")
    void testUnknownToInsideTriggersEnter() {
        AssetGeofenceState state = AssetGeofenceState.builder()
                .asset(testAsset)
                .geofence(testGeofence)
                .state(GeofenceStatus.UNKNOWN)
                .build();

        when(stateRepository.findByAssetIdAndGeofenceId(100L, 200L)).thenReturn(Optional.of(state));

        Instant now = Instant.now();
        // Point inside (exact center)
        geofenceService.evaluateGeofences(testAsset, 11.496155, 77.278545, now);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(1)).save(alertCaptor.capture());
        assertEquals(AlertType.GEOFENCE_ENTER, alertCaptor.getValue().getType());
        assertEquals(AlertSeverity.INFO, alertCaptor.getValue().getSeverity());
        assertEquals(GeofenceStatus.INSIDE, state.getState());
        verify(eventPublisher, times(1)).publishEvent(any(AlertPersistedEvent.class));
    }

    @Test
    @DisplayName("Transition: UNKNOWN -> OUTSIDE establishes OUTSIDE without false EXIT alert")
    void testUnknownToOutsideNoAlert() {
        AssetGeofenceState state = AssetGeofenceState.builder()
                .asset(testAsset)
                .geofence(testGeofence)
                .state(GeofenceStatus.UNKNOWN)
                .build();

        when(stateRepository.findByAssetIdAndGeofenceId(100L, 200L)).thenReturn(Optional.of(state));

        Instant now = Instant.now();
        // Point far outside (~1km away)
        geofenceService.evaluateGeofences(testAsset, 11.506155, 77.278545, now);

        verify(alertRepository, never()).save(any(Alert.class));
        verify(eventPublisher, never()).publishEvent(any(AlertPersistedEvent.class));
        assertEquals(GeofenceStatus.OUTSIDE, state.getState());
    }

    @Test
    @DisplayName("Transition: OUTSIDE -> INSIDE triggers GEOFENCE_ENTER alert")
    void testOutsideToInsideTriggersEnter() {
        AssetGeofenceState state = AssetGeofenceState.builder()
                .asset(testAsset)
                .geofence(testGeofence)
                .state(GeofenceStatus.OUTSIDE)
                .build();

        when(stateRepository.findByAssetIdAndGeofenceId(100L, 200L)).thenReturn(Optional.of(state));

        Instant now = Instant.now();
        // Inside
        geofenceService.evaluateGeofences(testAsset, 11.496155, 77.278545, now);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(1)).save(alertCaptor.capture());
        assertEquals(AlertType.GEOFENCE_ENTER, alertCaptor.getValue().getType());
        assertEquals(GeofenceStatus.INSIDE, state.getState());
        verify(eventPublisher, times(1)).publishEvent(any(AlertPersistedEvent.class));
    }

    @Test
    @DisplayName("Transition: INSIDE -> INSIDE suppresses duplicate alert")
    void testInsideToInsideSuppressesDuplicate() {
        AssetGeofenceState state = AssetGeofenceState.builder()
                .asset(testAsset)
                .geofence(testGeofence)
                .state(GeofenceStatus.INSIDE)
                .build();

        when(stateRepository.findByAssetIdAndGeofenceId(100L, 200L)).thenReturn(Optional.of(state));

        Instant now = Instant.now();
        // Inside again
        geofenceService.evaluateGeofences(testAsset, 11.496155, 77.278545, now);

        verify(alertRepository, never()).save(any(Alert.class));
        verify(eventPublisher, never()).publishEvent(any(AlertPersistedEvent.class));
        assertEquals(GeofenceStatus.INSIDE, state.getState());
    }

    @Test
    @DisplayName("Transition: INSIDE -> OUTSIDE triggers GEOFENCE_EXIT alert")
    void testInsideToOutsideTriggersExit() {
        AssetGeofenceState state = AssetGeofenceState.builder()
                .asset(testAsset)
                .geofence(testGeofence)
                .state(GeofenceStatus.INSIDE)
                .build();

        when(stateRepository.findByAssetIdAndGeofenceId(100L, 200L)).thenReturn(Optional.of(state));

        Instant now = Instant.now();
        // Point far outside (~1km away)
        geofenceService.evaluateGeofences(testAsset, 11.506155, 77.278545, now);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(1)).save(alertCaptor.capture());
        assertEquals(AlertType.GEOFENCE_EXIT, alertCaptor.getValue().getType());
        assertEquals(AlertSeverity.WARNING, alertCaptor.getValue().getSeverity());
        assertEquals(GeofenceStatus.OUTSIDE, state.getState());
        verify(eventPublisher, times(1)).publishEvent(any(AlertPersistedEvent.class));
    }

    @Test
    @DisplayName("Transition: OUTSIDE -> OUTSIDE suppresses duplicate alert")
    void testOutsideToOutsideSuppressesDuplicate() {
        AssetGeofenceState state = AssetGeofenceState.builder()
                .asset(testAsset)
                .geofence(testGeofence)
                .state(GeofenceStatus.OUTSIDE)
                .build();

        when(stateRepository.findByAssetIdAndGeofenceId(100L, 200L)).thenReturn(Optional.of(state));

        Instant now = Instant.now();
        // Still outside
        geofenceService.evaluateGeofences(testAsset, 11.506155, 77.278545, now);

        verify(alertRepository, never()).save(any(Alert.class));
        verify(eventPublisher, never()).publishEvent(any(AlertPersistedEvent.class));
        assertEquals(GeofenceStatus.OUTSIDE, state.getState());
    }
}
