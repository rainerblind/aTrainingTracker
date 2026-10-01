/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.map

import com.atrainingtracker.trainingtracker.MyUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying viewport mathematics, coordinate conversions, centroid anchoring,
 * and boundary clamping for [ElevationProfileZoomMath] (TST-UI-146).
 */
class ElevationProfileZoomMathTest {

    private val totalDist = 20_000.0 // 20km
    private val canvasWidth = 1000f

    @Test
    fun calculateVisibleDistance_clampsScaleAndComputesWindow() {
        // Default 1.0x
        assertEquals(20_000.0, ElevationProfileZoomMath.calculateVisibleDistance(totalDist, 1.0f), 0.001)

        // 2.0x zoom
        assertEquals(10_000.0, ElevationProfileZoomMath.calculateVisibleDistance(totalDist, 2.0f), 0.001)

        // 5.0x zoom
        assertEquals(4_000.0, ElevationProfileZoomMath.calculateVisibleDistance(totalDist, 5.0f), 0.001)

        // 10.0x zoom (max)
        assertEquals(2_000.0, ElevationProfileZoomMath.calculateVisibleDistance(totalDist, 10.0f), 0.001)

        // Underflow zoom < 1.0f clamps to 1.0f
        assertEquals(20_000.0, ElevationProfileZoomMath.calculateVisibleDistance(totalDist, 0.5f), 0.001)

        // Overflow zoom > 10.0f clamps to 10.0f
        assertEquals(2_000.0, ElevationProfileZoomMath.calculateVisibleDistance(totalDist, 25.0f), 0.001)

        // Short track safeguard (<= 10m)
        assertEquals(8.0, ElevationProfileZoomMath.calculateVisibleDistance(8.0, 5.0f), 0.001)
    }

    @Test
    fun clampStartDistance_enforcesRouteBoundaries() {
        val visibleDist = 10_000.0 // 2.0x zoom on 20km route (maxStart = 10_000.0)

        // In-bounds
        assertEquals(3_000.0, ElevationProfileZoomMath.clampStartDistance(totalDist, visibleDist, 3_000.0), 0.001)
        assertEquals(0.0, ElevationProfileZoomMath.clampStartDistance(totalDist, visibleDist, 0.0), 0.001)
        assertEquals(10_000.0, ElevationProfileZoomMath.clampStartDistance(totalDist, visibleDist, 10_000.0), 0.001)

        // Negative start clamps to 0.0
        assertEquals(0.0, ElevationProfileZoomMath.clampStartDistance(totalDist, visibleDist, -500.0), 0.001)

        // Exceeding start clamps to maxStart
        assertEquals(10_000.0, ElevationProfileZoomMath.clampStartDistance(totalDist, visibleDist, 15_000.0), 0.001)
    }

    @Test
    fun distanceAndCanvasX_areMutuallyInvertible() {
        val startDist = 4_000.0
        val visibleDist = 8_000.0 // window is [4km, 12km]

        val testDistances = listOf(4_000.0, 6_000.0, 8_000.0, 10_000.0, 12_000.0)
        for (dist in testDistances) {
            val x = ElevationProfileZoomMath.distanceToCanvasX(dist, startDist, visibleDist, canvasWidth)
            val recoveredDist = ElevationProfileZoomMath.canvasXToDistance(x, startDist, visibleDist, canvasWidth, totalDist)
            assertEquals("Invertibility failed for dist $dist", dist, recoveredDist, 0.01)
        }
    }

    @Test
    fun canvasXToDistance_clampsToBounds() {
        val startDist = 5_000.0
        val visibleDist = 10_000.0

        // Tap left of canvas (< 0) clamps to startDist
        val distLeft = ElevationProfileZoomMath.canvasXToDistance(-50f, startDist, visibleDist, canvasWidth, totalDist)
        assertEquals(5_000.0, distLeft, 0.001)

        // Tap right of canvas (> canvasWidth) clamps to end of visible window
        val distRight = ElevationProfileZoomMath.canvasXToDistance(1200f, startDist, visibleDist, canvasWidth, totalDist)
        assertEquals(15_000.0, distRight, 0.001)
    }

    @Test
    fun applyZoomAtCentroid_anchorsAtTouchPoint() {
        // Initially 1.0x (full 20km route, startDist = 0)
        val initialZoom = 1.0f
        val initialStart = 0.0

        // Athlete pinches to 2.0x centered at midpoint of screen (x = 500px, which is 10km)
        val (newZoom, newStart) = ElevationProfileZoomMath.applyZoomAtCentroid(
            totalDist = totalDist,
            currentZoom = initialZoom,
            targetZoom = 2.0f,
            centroidX = 500f,
            canvasWidth = canvasWidth,
            currentStartDist = initialStart
        )

        assertEquals(2.0f, newZoom, 0.001f)
        // With 2.0x zoom, visible distance is 10km. Centered at 10km, start must be 5km.
        assertEquals(5_000.0, newStart, 0.001)

        // Verify distance at centroid remains 10km
        val distAtCentroid = ElevationProfileZoomMath.canvasXToDistance(500f, newStart, 10_000.0, canvasWidth, totalDist)
        assertEquals(10_000.0, distAtCentroid, 0.001)
    }

    @Test
    fun applyZoomAtCentroid_clampsAtEdges() {
        // Zooming at left edge (centroidX = 0)
        val (_, startLeft) = ElevationProfileZoomMath.applyZoomAtCentroid(
            totalDist = totalDist,
            currentZoom = 1.0f,
            targetZoom = 2.0f,
            centroidX = 0f,
            canvasWidth = canvasWidth,
            currentStartDist = 0.0
        )
        assertEquals(0.0, startLeft, 0.001)

        // Zooming at right edge (centroidX = canvasWidth)
        val (_, startRight) = ElevationProfileZoomMath.applyZoomAtCentroid(
            totalDist = totalDist,
            currentZoom = 1.0f,
            targetZoom = 2.0f,
            centroidX = canvasWidth,
            canvasWidth = canvasWidth,
            currentStartDist = 0.0
        )
        // Visible is 10km, so start must clamp to 10km (showing 10km..20km)
        assertEquals(10_000.0, startRight, 0.001)
    }

    @Test
    fun calculateAdaptiveDistanceStep_adaptsToVisibleRange() {
        // Metric steps
        assertEquals(10_000f, ElevationProfileZoomMath.calculateAdaptiveDistanceStep(80_000.0, MyUnits.METRIC), 0.1f)
        assertEquals(5_000f, ElevationProfileZoomMath.calculateAdaptiveDistanceStep(30_000.0, MyUnits.METRIC), 0.1f)
        assertEquals(2_000f, ElevationProfileZoomMath.calculateAdaptiveDistanceStep(17_820.0, MyUnits.METRIC), 0.1f)
        assertEquals(2_000f, ElevationProfileZoomMath.calculateAdaptiveDistanceStep(12_000.0, MyUnits.METRIC), 0.1f)
        assertEquals(1_000f, ElevationProfileZoomMath.calculateAdaptiveDistanceStep(8_000.0, MyUnits.METRIC), 0.1f)
        assertEquals(500f, ElevationProfileZoomMath.calculateAdaptiveDistanceStep(2_500.0, MyUnits.METRIC), 0.1f)
        assertEquals(100f, ElevationProfileZoomMath.calculateAdaptiveDistanceStep(800.0, MyUnits.METRIC), 0.1f)
        assertEquals(50f, ElevationProfileZoomMath.calculateAdaptiveDistanceStep(200.0, MyUnits.METRIC), 0.1f)
    }

    @Test
    fun isDistanceVisible_correctlyFiltersCoordinates() {
        val startDist = 5_000.0
        val visibleDist = 4_000.0 // visible range [5km .. 9km]

        assertTrue(ElevationProfileZoomMath.isDistanceVisible(5_000.0, startDist, visibleDist))
        assertTrue(ElevationProfileZoomMath.isDistanceVisible(7_000.0, startDist, visibleDist))
        assertTrue(ElevationProfileZoomMath.isDistanceVisible(9_000.0, startDist, visibleDist))

        assertFalse(ElevationProfileZoomMath.isDistanceVisible(4_999.0, startDist, visibleDist))
        assertFalse(ElevationProfileZoomMath.isDistanceVisible(9_001.0, startDist, visibleDist))
        assertFalse(ElevationProfileZoomMath.isDistanceVisible(0.0, startDist, visibleDist))
    }

    @Test
    fun applyPan_correctlyShiftsStartDistanceAndClamps() {
        val visibleDist = 10_000.0 // 2x zoom on 20km route (maxStart = 10_000.0)
        val initialStart = 5_000.0 // viewport is [5km, 15km]

        // Drag finger right by 100px (panDeltaX = +100f) -> shifts viewport left by 1km -> start becomes 4km
        val pannedRight = ElevationProfileZoomMath.applyPan(
            currentStartDist = initialStart,
            visibleDist = visibleDist,
            panDeltaX = 100f,
            canvasWidth = canvasWidth,
            totalDist = totalDist
        )
        assertEquals(4_000.0, pannedRight, 0.001)

        // Drag finger left by 200px (panDeltaX = -200f) -> shifts viewport right by 2km -> start becomes 7km
        val pannedLeft = ElevationProfileZoomMath.applyPan(
            currentStartDist = initialStart,
            visibleDist = visibleDist,
            panDeltaX = -200f,
            canvasWidth = canvasWidth,
            totalDist = totalDist
        )
        assertEquals(7_000.0, pannedLeft, 0.001)

        // Dragging past left boundary clamps to 0.0
        val clampedStart = ElevationProfileZoomMath.applyPan(
            currentStartDist = initialStart,
            visibleDist = visibleDist,
            panDeltaX = 800f,
            canvasWidth = canvasWidth,
            totalDist = totalDist
        )
        assertEquals(0.0, clampedStart, 0.001)

        // Dragging past right boundary clamps to 10_000.0
        val clampedEnd = ElevationProfileZoomMath.applyPan(
            currentStartDist = initialStart,
            visibleDist = visibleDist,
            panDeltaX = -800f,
            canvasWidth = canvasWidth,
            totalDist = totalDist
        )
        assertEquals(10_000.0, clampedEnd, 0.001)
    }
}
