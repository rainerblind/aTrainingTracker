package com.atrainingtracker.trainingtracker.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GeoUtils] pure geodesic math calculations (TST-MAP-037 / REQ-MAP-035).
 */
class GeoUtilsTest {

    @Test
    fun haversineDistanceMeters_coincidentPoints_returnsZero() {
        val dist = GeoUtils.haversineDistanceMeters(48.8566, 2.3522, 48.8566, 2.3522)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun haversineDistanceMeters_oneDegreeAlongEquator_matchesTheoreticalArc() {
        // 1 degree along equator: 2 * pi * 6371000 / 360 = ~111195 meters
        val dist = GeoUtils.haversineDistanceMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111195.0, dist, 100.0)
    }

    @Test
    fun haversineDistanceMeters_parisToLondon_accurateWithinTolerance() {
        // Paris (48.8566, 2.3522) to London (51.5074, -0.1278) is approx 343 km
        val dist = GeoUtils.haversineDistanceMeters(48.8566, 2.3522, 51.5074, -0.1278)
        assertEquals(343500.0, dist, 2000.0)
    }

    @Test
    fun haversineDistanceMeters_poleToEquator_matchesQuarterMeridian() {
        // Pole to equator: pi/2 * 6371000 = ~10007543 meters
        val dist = GeoUtils.haversineDistanceMeters(90.0, 0.0, 0.0, 0.0)
        assertEquals(10007543.0, dist, 100.0)
    }

    @Test
    fun calculateInitialBearing_cardinalDirections_computesExactAzimuth() {
        // North
        val north = GeoUtils.calculateInitialBearing(0.0, 0.0, 1.0, 0.0)
        assertEquals(0.0, north, 0.01)

        // East
        val east = GeoUtils.calculateInitialBearing(0.0, 0.0, 0.0, 1.0)
        assertEquals(90.0, east, 0.01)

        // South
        val south = GeoUtils.calculateInitialBearing(1.0, 0.0, 0.0, 0.0)
        assertEquals(180.0, south, 0.01)

        // West
        val west = GeoUtils.calculateInitialBearing(0.0, 1.0, 0.0, 0.0)
        assertEquals(270.0, west, 0.01)
    }

    @Test
    fun calculateInitialBearing_intercardinalDirections_accurateAzimuth() {
        val northeast = GeoUtils.calculateInitialBearing(0.0, 0.0, 1.0, 1.0)
        assertEquals(45.0, northeast, 0.5)

        val southeast = GeoUtils.calculateInitialBearing(0.0, 0.0, -1.0, 1.0)
        assertEquals(135.0, southeast, 0.5)

        val southwest = GeoUtils.calculateInitialBearing(0.0, 0.0, -1.0, -1.0)
        assertEquals(225.0, southwest, 0.5)

        val northwest = GeoUtils.calculateInitialBearing(0.0, 0.0, 1.0, -1.0)
        assertEquals(315.0, northwest, 0.5)
    }

    @Test
    fun calculateInitialBearing_isAlwaysNormalizedBetween0And360() {
        val testCoords = listOf(
            Pair(Pair(48.0, 9.0), Pair(48.5, 9.5)),
            Pair(Pair(-20.0, 150.0), Pair(-25.0, 140.0)),
            Pair(Pair(60.0, -170.0), Pair(61.0, 175.0)),
            Pair(Pair(0.0, 0.0), Pair(-1.0, 0.0))
        )
        for ((p1, p2) in testCoords) {
            val bearing = GeoUtils.calculateInitialBearing(p1.first, p1.second, p2.first, p2.second)
            assertTrue("Bearing $bearing should be in [0, 360)", bearing in 0.0..360.0)
        }
    }

    @Test
    fun isValidGpsCoordinate_nullCoordinates_returnsFalse() {
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(null, null))
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(48.65, null))
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(null, 9.05))
    }

    @Test
    fun isValidGpsCoordinate_nullIsland_returnsFalse() {
        // Exact 0.0, 0.0
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(0.0, 0.0))
        // Coordinates within epsilon 1e-5
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(1e-6, 1e-6))
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(-1e-6, 0.0))
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(0.0, -1e-6))
    }

    @Test
    fun isValidGpsCoordinate_nonFiniteValues_returnsFalse() {
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(Double.NaN, 9.05))
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(48.65, Double.NaN))
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(Double.POSITIVE_INFINITY, 9.05))
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(48.65, Double.NEGATIVE_INFINITY))
    }

    @Test
    fun isValidGpsCoordinate_outOfBoundsLatitudeOrLongitude_returnsFalse() {
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(90.1, 9.05))
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(-90.1, 9.05))
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(48.65, 180.1))
        org.junit.Assert.assertFalse(GeoUtils.isValidGpsCoordinate(48.65, -180.1))
    }

    @Test
    fun isValidGpsCoordinate_validAuthenticCoordinates_returnsTrue() {
        org.junit.Assert.assertTrue(GeoUtils.isValidGpsCoordinate(48.65, 9.05))
        org.junit.Assert.assertTrue(GeoUtils.isValidGpsCoordinate(-33.8688, 151.2093))
        // Edge boundaries
        org.junit.Assert.assertTrue(GeoUtils.isValidGpsCoordinate(90.0, 180.0))
        org.junit.Assert.assertTrue(GeoUtils.isValidGpsCoordinate(-90.0, -180.0))
        // Non-zero point on prime meridian or equator
        org.junit.Assert.assertTrue(GeoUtils.isValidGpsCoordinate(1.0, 0.0))
        org.junit.Assert.assertTrue(GeoUtils.isValidGpsCoordinate(0.0, 1.0))
    }

    @Test
    fun haversineDistanceMeters_goldenValues_withinSubMicrometerTolerance_TST_PRO_137_1() {
        // Equator 1 degree arc at R = 6,371,000.0 m: 2 * pi * R / 360
        val expectedEquatorDeg = 2.0 * Math.PI * 6371000.0 / 360.0
        assertEquals(
            expectedEquatorDeg,
            GeoUtils.haversineDistanceMeters(0.0, 0.0, 0.0, 1.0),
            1e-6
        )

        // Pole to equator quarter-meridian: pi/2 * R
        val expectedQuarterMeridian = Math.PI / 2.0 * 6371000.0
        assertEquals(
            expectedQuarterMeridian,
            GeoUtils.haversineDistanceMeters(90.0, 0.0, 0.0, 0.0),
            1e-6
        )

        // Paris (48.8566, 2.3522) -> London (51.5074, -0.1278) analytical spherical value (R = 6,371,000.0 m)
        val parisLondonGolden = 343556.06034101645
        assertEquals(
            parisLondonGolden,
            GeoUtils.haversineDistanceMeters(48.8566, 2.3522, 51.5074, -0.1278),
            1e-6
        )

        // Custom radius R = 6,371,009.0 m (used by KnownLocationsGeometryMath)
        val munichToGarmisch = GeoUtils.haversineDistanceMeters(
            48.137154, 11.576124,
            47.491694, 11.095498,
            earthRadiusMeters = 6371009.0
        )
        val expectedMunichGarmisch6371009 = 80244.42048614829
        assertEquals(expectedMunichGarmisch6371009, munichToGarmisch, 1e-6)
    }
}
