package com.medtrack.avs.service;

import com.medtrack.avs.entity.*;
import com.medtrack.avs.mapper.AssetMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class IndoorLocationFreshnessTest {

    @Test
    @DisplayName("Fresh QR check-in within 12 hours resolves to RECENT_CHECK_IN")
    void testFreshQrCheckInResolvesToRecentCheckIn() {
        Instant now = Instant.now();
        Instant checkInTime = now.minus(Duration.ofHours(2)); // 2 hours ago (within 12h policy)

        Floor floor = Floor.builder().id(1L).displayName("Ground Floor").build();
        Room room = Room.builder().id(10L).roomNumber("ER-101").build();

        Asset asset = Asset.builder()
                .assetCode("ECG-FRESH")
                .floor(floor)
                .room(room)
                .indoorX(20.0)
                .indoorY(25.0)
                .indoorLocationSource(IndoorLocationSource.QR_CHECKIN)
                .indoorUpdatedAt(checkInTime)
                .build();

        String status = AssetMapper.resolveLocationStatus(asset, now);
        assertEquals("RECENT_CHECK_IN", status, "Fresh QR check-in must resolve to RECENT_CHECK_IN, not VERIFIED_INDOOR");
    }

    @Test
    @DisplayName("Expired QR check-in older than 12 hours transitions to LAST_KNOWN")
    void testExpiredQrCheckInTransitionsToLastKnown() {
        Instant now = Instant.now();
        Instant checkInTime = now.minus(Duration.ofHours(14)); // 14 hours ago (exceeds 12h policy)

        Floor floor = Floor.builder().id(1L).displayName("Ground Floor").build();

        Asset asset = Asset.builder()
                .assetCode("ECG-EXPIRED-QR")
                .floor(floor)
                .indoorX(30.0)
                .indoorY(40.0)
                .indoorLocationSource(IndoorLocationSource.QR_CHECKIN)
                .indoorUpdatedAt(checkInTime)
                .build();

        String status = AssetMapper.resolveLocationStatus(asset, now);
        assertEquals("LAST_KNOWN", status, "Expired QR check-in must transition to LAST_KNOWN");
    }

    @Test
    @DisplayName("Manually configured position within 30 days resolves to CONFIGURED_INDOOR")
    void testManuallyConfiguredIndoorPosition() {
        Instant now = Instant.now();
        Instant configTime = now.minus(Duration.ofDays(5)); // 5 days ago

        Floor floor = Floor.builder().id(2L).displayName("Floor 2").build();

        Asset asset = Asset.builder()
                .assetCode("PUMP-MANUAL")
                .floor(floor)
                .indoorX(55.0)
                .indoorY(60.0)
                .indoorLocationSource(IndoorLocationSource.MANUAL_CONFIG)
                .indoorUpdatedAt(configTime)
                .build();

        String status = AssetMapper.resolveLocationStatus(asset, now);
        assertEquals("CONFIGURED_INDOOR", status, "Manual configuration must resolve to CONFIGURED_INDOOR, never verified telemetry");
    }

    @Test
    @DisplayName("Manually configured position older than 30 days transitions to LAST_KNOWN")
    void testExpiredManualConfiguration() {
        Instant now = Instant.now();
        Instant configTime = now.minus(Duration.ofDays(35)); // 35 days ago (exceeds 30d window)

        Floor floor = Floor.builder().id(2L).displayName("Floor 2").build();

        Asset asset = Asset.builder()
                .assetCode("PUMP-OLD-MANUAL")
                .floor(floor)
                .indoorX(55.0)
                .indoorY(60.0)
                .indoorLocationSource(IndoorLocationSource.MANUAL_CONFIG)
                .indoorUpdatedAt(configTime)
                .build();

        String status = AssetMapper.resolveLocationStatus(asset, now);
        assertEquals("LAST_KNOWN", status, "Manual configuration exceeding freshness policy must transition to LAST_KNOWN");
    }

    @Test
    @DisplayName("BLE_BEACON or WIFI_POSITIONING without genuine hardware integration cannot claim VERIFIED_INDOOR")
    void testBleBeaconWithoutHardwareIntegrationReturnsLastKnown() {
        Instant now = Instant.now();
        Instant recentTime = now.minus(Duration.ofMinutes(5));

        Floor floor = Floor.builder().id(3L).displayName("Floor 3").build();

        Asset assetBle = Asset.builder()
                .assetCode("DEF-BLE")
                .floor(floor)
                .indoorX(40.0)
                .indoorY(50.0)
                .indoorLocationSource(IndoorLocationSource.BLE_BEACON)
                .indoorUpdatedAt(recentTime)
                .build();

        String statusBle = AssetMapper.resolveLocationStatus(assetBle, now);
        assertNotEquals("VERIFIED_INDOOR", statusBle, "Unconnected BLE source must not falsely claim VERIFIED_INDOOR");
        assertEquals("LAST_KNOWN", statusBle);

        Asset assetWifi = Asset.builder()
                .assetCode("DEF-WIFI")
                .floor(floor)
                .indoorX(40.0)
                .indoorY(50.0)
                .indoorLocationSource(IndoorLocationSource.WIFI_POSITIONING)
                .indoorUpdatedAt(recentTime)
                .build();

        String statusWifi = AssetMapper.resolveLocationStatus(assetWifi, now);
        assertNotEquals("VERIFIED_INDOOR", statusWifi, "Unconnected Wi-Fi source must not falsely claim VERIFIED_INDOOR");
        assertEquals("LAST_KNOWN", statusWifi);
    }

    @Test
    @DisplayName("Fresh outdoor GPS within 2 hours resolves to OUTDOOR_GPS")
    void testFreshOutdoorGps() {
        Instant now = Instant.now();
        Instant fixTime = now.minus(Duration.ofMinutes(30)); // 30 minutes ago

        Asset asset = Asset.builder()
                .assetCode("AMB-GPS")
                .latitude(40.7128)
                .longitude(-74.0060)
                .gpsAccuracy(5.0)
                .lastSeen(fixTime)
                .build();

        String status = AssetMapper.resolveLocationStatus(asset, now);
        assertEquals("OUTDOOR_GPS", status);
    }

    @Test
    @DisplayName("Expired outdoor GPS older than 2 hours transitions to LAST_KNOWN")
    void testExpiredOutdoorGps() {
        Instant now = Instant.now();
        Instant fixTime = now.minus(Duration.ofHours(3)); // 3 hours ago (exceeds 2h GPS window)

        Asset asset = Asset.builder()
                .assetCode("AMB-GPS-OLD")
                .latitude(40.7128)
                .longitude(-74.0060)
                .gpsAccuracy(5.0)
                .lastSeen(fixTime)
                .build();

        String status = AssetMapper.resolveLocationStatus(asset, now);
        assertEquals("LAST_KNOWN", status);
    }

    @Test
    @DisplayName("Fresh outdoor GPS takes precedence when newer than stale indoor position")
    void testFreshGpsOverridesStaleIndoor() {
        Instant now = Instant.now();
        Instant indoorStaleTime = now.minus(Duration.ofDays(2)); // Indoor check-in from 2 days ago
        Instant freshGpsTime = now.minus(Duration.ofMinutes(10)); // Live GPS fix from 10 minutes ago

        Floor floor = Floor.builder().id(1L).displayName("Ground Floor").build();

        Asset asset = Asset.builder()
                .assetCode("TRANSIT-ASSET")
                .floor(floor)
                .indoorX(15.0)
                .indoorY(25.0)
                .indoorLocationSource(IndoorLocationSource.QR_CHECKIN)
                .indoorUpdatedAt(indoorStaleTime)
                .latitude(40.7135)
                .longitude(-74.0070)
                .lastSeen(freshGpsTime)
                .build();

        String status = AssetMapper.resolveLocationStatus(asset, now);
        assertEquals("OUTDOOR_GPS", status, "Fresh live GPS must take precedence when asset is actively transmitting outside");
    }

    @Test
    @DisplayName("Asset without any coordinates resolves to LOCATION_UNAVAILABLE")
    void testMissingCoordinatesResolvesToLocationUnavailable() {
        Asset asset = Asset.builder()
                .assetCode("NO-LOC-01")
                .latitude(null)
                .longitude(null)
                .floor(null)
                .indoorX(null)
                .indoorY(null)
                .build();

        String status = AssetMapper.resolveLocationStatus(asset, Instant.now());
        assertEquals("LOCATION_UNAVAILABLE", status);

        // Also test null asset
        assertEquals("LOCATION_UNAVAILABLE", AssetMapper.resolveLocationStatus(null));
    }
}
