package com.medtrack.avs;

import com.medtrack.avs.service.GeofenceCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeofenceCalculatorTest {

    private final GeofenceCalculator calculator = new GeofenceCalculator();

    @Test
    @DisplayName("Haversine: Exact same point returns 0.0 meters")
    void testExactSamePoint() {
        double dist = calculator.calculateDistanceMeters(11.496155, 77.278545, 11.496155, 77.278545);
        assertEquals(0.0, dist, 0.0001);
        assertTrue(calculator.isInside(dist, 50.0));
    }

    @Test
    @DisplayName("Haversine: Point clearly inside geofence radius")
    void testPointClearlyInside() {
        // ~10 meters north: 11.496155 + 0.000090
        double dist = calculator.calculateDistanceMeters(11.496155, 77.278545, 11.496245, 77.278545);
        assertTrue(dist > 5.0 && dist < 15.0, "Expected distance around ~10m, got " + dist);
        assertTrue(calculator.isInside(dist, 50.0));
    }

    @Test
    @DisplayName("Haversine: Point clearly outside geofence radius")
    void testPointClearlyOutside() {
        // ~150 meters away
        double dist = calculator.calculateDistanceMeters(11.496155, 77.278545, 11.497500, 77.278545);
        assertTrue(dist > 100.0, "Expected distance > 100m, got " + dist);
        assertFalse(calculator.isInside(dist, 50.0));
    }

    @Test
    @DisplayName("Haversine: Boundary condition test")
    void testBoundaryCondition() {
        // Exact boundary distance
        double radius = 50.0;
        assertTrue(calculator.isInside(50.0, radius));
        assertTrue(calculator.isInside(49.99, radius));
        assertFalse(calculator.isInside(50.01, radius));
    }

    @Test
    @DisplayName("Haversine: Known reference coordinates pair")
    void testKnownCoordinatesPair() {
        // Distance between London (51.5074, -0.1278) and Paris (48.8566, 2.3522) is approx 343 km
        double dist = calculator.calculateDistanceMeters(51.5074, -0.1278, 48.8566, 2.3522);
        assertTrue(dist > 340000 && dist < 346000, "Expected ~343km between London and Paris, got " + dist);
    }
}
