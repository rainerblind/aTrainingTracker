package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying [ElevationProfileZoomMath] coordinate transformations and canvas
 * mapping with non-zero start distance from sliced rolling windows (REQ-UI-355 / TST-UI-315 / ATT-3317).
 */
class ElevationProfileCoordinateTest {

    @Test
    fun testDistanceToCanvasX_withNonZeroStartDist() {
        val startDist = 9647.0588
        val visibleDist = 2352.9412
        val canvasWidth = 1000f

        // Left edge: exactly at startDist -> 0f
        val xLeft = ElevationProfileZoomMath.distanceToCanvasX(startDist, startDist, visibleDist, canvasWidth)
        assertEquals(0f, xLeft, 0.001f)

        // Athlete position at 10000.0m -> exactly 15% (150f on 1000f width)
        val xAthlete = ElevationProfileZoomMath.distanceToCanvasX(10000.0, startDist, visibleDist, canvasWidth)
        assertEquals(150.0f, xAthlete, 0.5f)

        // Right edge: at startDist + visibleDist (12000.0m) -> 1000f
        val xRight = ElevationProfileZoomMath.distanceToCanvasX(startDist + visibleDist, startDist, visibleDist, canvasWidth)
        assertEquals(1000f, xRight, 0.001f)
    }

    @Test
    fun testCanvasXToDistance_withNonZeroStartDist() {
        val startDist = 9647.0588
        val visibleDist = 2352.9412
        val canvasWidth = 1000f

        // At canvasX = 0f -> startDist
        val distLeft = ElevationProfileZoomMath.canvasXToDistance(0f, startDist, visibleDist, canvasWidth)
        assertEquals(startDist, distLeft, 0.001)

        // At canvasX = 150f -> 10000.0m
        val distAthlete = ElevationProfileZoomMath.canvasXToDistance(150f, startDist, visibleDist, canvasWidth)
        assertEquals(10000.0, distAthlete, 0.5)

        // At canvasX = 1000f -> 12000.0m
        val distRight = ElevationProfileZoomMath.canvasXToDistance(1000f, startDist, visibleDist, canvasWidth)
        assertEquals(startDist + visibleDist, distRight, 0.001)
    }

    @Test
    fun testCanvasXToDistance_clampingInvariants() {
        val startDist = 5000.0
        val visibleDist = 2000.0
        val canvasWidth = 1000f

        // Negative X should clamp to startDist
        val clampedNeg = ElevationProfileZoomMath.canvasXToDistance(-50f, startDist, visibleDist, canvasWidth)
        assertEquals(5000.0, clampedNeg, 0.001)

        // Beyond width X should clamp to startDist + visibleDist
        val clampedOver = ElevationProfileZoomMath.canvasXToDistance(1200f, startDist, visibleDist, canvasWidth)
        assertEquals(7000.0, clampedOver, 0.001)
    }
}
