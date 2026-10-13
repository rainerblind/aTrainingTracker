package com.atrainingtracker.trainingtracker.routes

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure geodesic calculation utilities for great-circle distance and initial azimuth bearing.
 *
 * Implements standard spherical trigonometry algorithms (WGS-84 radius 6371 km) with zero
 * Android framework dependencies, ensuring fast deterministic execution in background navigation
 * algorithms and headless unit tests.
 */
object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle geodesic distance in meters between two geographic coordinates
     * using the Haversine formula.
     *
     * @param lat1 Latitude of first point in degrees
     * @param lon1 Longitude of first point in degrees
     * @param lat2 Latitude of second point in degrees
     * @param lon2 Longitude of second point in degrees
     * @return Distance in meters
     */
    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Computes the initial forward azimuth bearing in degrees clockwise from True North (0°..360°)
     * along the great-circle path from (lat1, lng1) to (lat2, lng2).
     *
     * @param lat1 Latitude of source point in degrees
     * @param lng1 Longitude of source point in degrees
     * @param lat2 Latitude of target point in degrees
     * @param lng2 Longitude of target point in degrees
     * @return Normalized azimuth bearing in degrees [0.0, 360.0)
     */
    fun calculateInitialBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lng2 - lng1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)

        return (Math.toDegrees(theta) + 360.0) % 360.0
    }
}
