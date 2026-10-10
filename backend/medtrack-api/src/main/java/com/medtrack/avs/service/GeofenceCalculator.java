package com.medtrack.avs.service;

import org.springframework.stereotype.Component;

/**
 * Geographic distance calculation engine implementing the Haversine spherical formula.
 *
 * Formula:
 * a = sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2)
 * c = 2 * atan2(√a, √(1−a))
 * d = R * c
 *
 * where R = 6,371,000 meters (mean volumetric radius of Earth).
 */
@Component
public class GeofenceCalculator {

    public static final double EARTH_RADIUS_METERS = 6371000.0;

    /**
     * Calculates the great-circle distance between two geographic coordinates in meters.
     *
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return Distance in meters
     */
    public double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        // Handle exact same coordinates fast without trigonometric overhead
        if (Double.compare(lat1, lat2) == 0 && Double.compare(lon1, lon2) == 0) {
            return 0.0;
        }

        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double sinDeltaPhi2 = Math.sin(deltaPhi / 2.0);
        double sinDeltaLambda2 = Math.sin(deltaLambda / 2.0);

        double a = sinDeltaPhi2 * sinDeltaPhi2
                + Math.cos(phi1) * Math.cos(phi2) * sinDeltaLambda2 * sinDeltaLambda2;

        // Clamp 'a' to [0, 1] to avoid NaN caused by floating point precision
        a = Math.min(1.0, Math.max(0.0, a));

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Determines whether a distance falls inside the specified radius.
     *
     * @param distanceMeters Distance in meters
     * @param radiusMeters   Geofence radius in meters
     * @return true if distance <= radius
     */
    public boolean isInside(double distanceMeters, double radiusMeters) {
        return distanceMeters <= radiusMeters;
    }
}
