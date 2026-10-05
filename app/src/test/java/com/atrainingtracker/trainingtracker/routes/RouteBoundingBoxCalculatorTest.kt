package com.atrainingtracker.trainingtracker.routes

import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteBoundingBoxCalculatorTest {

    private fun pt(lat: Double, lng: Double, alt: Double = 400.0, dist: Double = 0.0) =
        PathPoint(dist, LatLng(lat, lng), alt)

    @Test
    fun calculateGlobalBounds_computesMinMaxLatAndLng() {
        val path = listOf(
            pt(48.0, 9.0),
            pt(48.5, 9.2),
            pt(47.9, 8.8)
        )

        val bounds = RouteBoundingBoxCalculator.calculateGlobalBounds(path)

        assertEquals(47.9, bounds.minLat, 1e-6)
        assertEquals(48.5, bounds.maxLat, 1e-6)
        assertEquals(8.8, bounds.minLng, 1e-6)
        assertEquals(9.2, bounds.maxLng, 1e-6)
        assertEquals(48.2, bounds.centerLat, 1e-6)
        assertEquals(9.0, bounds.centerLng, 1e-6)
    }

    @Test
    fun calculateDifferentiatingBounds_cropsCommonEntryAndExitCorridors() {
        // Create a 10 km route:
        // First 2 km: goes North (48.0 -> 48.018) along fixed longitude 9.0
        // Middle 6 km: makes a wide loop out to the East (longitude 9.0 -> 9.06 -> 9.0)
        // Last 2 km: returns South (48.018 -> 48.0) along fixed longitude 9.0
        val points = mutableListOf<PathPoint>()

        // 0 to 2 km (outbound corridor)
        for (i in 0..20) {
            val lat = 48.0 + (0.018 * i / 20.0)
            points.add(pt(lat, 9.0))
        }

        // 2 to 8 km (middle loop venturing far east to 9.06)
        for (i in 1..60) {
            val lat = 48.018 + (0.03 * (i / 60.0))
            val lng = 9.0 + (0.06 * Math.sin(Math.PI * i / 60.0))
            points.add(pt(lat, lng))
        }

        // 8 to 10 km (inbound corridor returning south)
        for (i in 1..20) {
            val lat = 48.048 - (0.048 * i / 20.0)
            points.add(pt(lat, 9.0))
        }

        val globalBounds = RouteBoundingBoxCalculator.calculateGlobalBounds(points)
        val diffBounds = RouteBoundingBoxCalculator.calculateDifferentiatingBounds(
            points,
            departureCorridorDistMeters = 2000.0,
            returnCorridorDistMeters = 2000.0,
            paddingPercent = 0.10
        )

        // Global bounds includes the south base (48.0)
        assertEquals(48.0, globalBounds.minLat, 1e-3)

        // Differentiating bounds focuses on the north loop, so minLat is elevated well above 48.0
        assertTrue("Differentiating minLat (${diffBounds.minLat}) should be higher than global minLat (${globalBounds.minLat})",
            diffBounds.minLat > globalBounds.minLat)

        // Longitude span remains focused around the eastern loop
        assertTrue(diffBounds.maxLng >= 9.05)
    }

    @Test
    fun calculateDifferentiatingBounds_whenRouteTooShort_fallsBackToGlobal() {
        // Short 1 km route
        val points = listOf(
            pt(48.0, 9.0),
            pt(48.005, 9.0),
            pt(48.009, 9.0)
        )

        val globalBounds = RouteBoundingBoxCalculator.calculateGlobalBounds(points)
        val diffBounds = RouteBoundingBoxCalculator.calculateDifferentiatingBounds(
            points,
            departureCorridorDistMeters = 1500.0,
            returnCorridorDistMeters = 1500.0
        )

        assertEquals(globalBounds.minLat, diffBounds.minLat, 1e-6)
        assertEquals(globalBounds.maxLat, diffBounds.maxLat, 1e-6)
        assertEquals(globalBounds.minLng, diffBounds.minLng, 1e-6)
        assertEquals(globalBounds.maxLng, diffBounds.maxLng, 1e-6)
    }
}
