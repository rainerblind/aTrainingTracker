/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.map

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests verifying TelemetryMetricUtils binary search scrubbing point lookup,
 * numerical parity with linear search, and metric extraction helpers (REQ-UI-260, TST-UI-219, ATT-2031).
 */
class TelemetryMetricUtilsTest {

    private fun createPoint(distance: Double, timeSec: Long, hr: Int? = null, power: Int? = null, speedMps: Double? = null): PathPoint {
        return PathPoint(
            distance = distance,
            latLng = LatLng(48.0, 11.0),
            altitude = 500.0,
            timeSec = timeSec,
            hr = hr,
            power = power,
            speedMps = speedMps
        )
    }

    @Test
    fun testFindNearestPoint_emptyList_returnsNull() {
        assertNull(TelemetryMetricUtils.findNearestPoint(emptyList(), 100.0))
        assertNull(TelemetryMetricUtils.findNearestPoint(emptyList(), 100.0, isTimeDomain = true))
    }

    @Test
    fun testFindNearestPoint_singleElementList_returnsElement() {
        val p = createPoint(50.0, 10L)
        val list = listOf(p)

        assertEquals(p, TelemetryMetricUtils.findNearestPoint(list, 0.0))
        assertEquals(p, TelemetryMetricUtils.findNearestPoint(list, 50.0))
        assertEquals(p, TelemetryMetricUtils.findNearestPoint(list, 100.0))
        assertEquals(p, TelemetryMetricUtils.findNearestPoint(list, 5.0, isTimeDomain = true))
        assertEquals(p, TelemetryMetricUtils.findNearestPoint(list, 15.0, isTimeDomain = true))
    }

    @Test
    fun testFindNearestPoint_boundaryTargets_returnsFirstOrLast() {
        val points = listOf(
            createPoint(100.0, 10L),
            createPoint(200.0, 20L),
            createPoint(300.0, 30L)
        )

        // Below first
        assertEquals(points[0], TelemetryMetricUtils.findNearestPoint(points, 0.0))
        assertEquals(points[0], TelemetryMetricUtils.findNearestPoint(points, 5.0, isTimeDomain = true))

        // Above last
        assertEquals(points[2], TelemetryMetricUtils.findNearestPoint(points, 500.0))
        assertEquals(points[2], TelemetryMetricUtils.findNearestPoint(points, 50.0, isTimeDomain = true))

        // Exact boundaries
        assertEquals(points[0], TelemetryMetricUtils.findNearestPoint(points, 100.0))
        assertEquals(points[2], TelemetryMetricUtils.findNearestPoint(points, 300.0))
    }

    @Test
    fun testFindNearestPoint_distanceDomain_100PercentNumericalParityWithMinByOrNull() {
        // Monotonically increasing synthetic track with 1000 points
        val points = (0 until 1000).map { i ->
            createPoint(
                distance = i * 25.5,
                timeSec = i * 3L
            )
        }

        // Test across 500 probe queries (exact match, between points, beyond ends)
        val probeTargets = (0..500).map { it * 51.3 }

        for (target in probeTargets) {
            val linearResult = points.minByOrNull { abs(it.distance - target) }
            val binaryResult = TelemetryMetricUtils.findNearestPoint(points, target, isTimeDomain = false)

            assertNotNull("Binary search returned null for target $target", binaryResult)
            assertEquals(
                "Mismatch for distance target $target: linear=${linearResult?.distance}, binary=${binaryResult?.distance}",
                linearResult,
                binaryResult
            )
        }
    }

    @Test
    fun testFindNearestPoint_timeDomain_100PercentNumericalParityWithMinByOrNull() {
        val points = (0 until 1000).map { i ->
            createPoint(
                distance = i * 15.0,
                timeSec = i * 2L
            )
        }

        val probeTimes = (0..500).map { (it * 3.7) }

        for (target in probeTimes) {
            val linearResult = points.minByOrNull { abs(it.timeSec - target) }
            val binaryResult = TelemetryMetricUtils.findNearestPoint(points, target, isTimeDomain = true)

            assertNotNull("Binary search returned null for time target $target", binaryResult)
            assertEquals(
                "Mismatch for time target $target: linear=${linearResult?.timeSec}, binary=${binaryResult?.timeSec}",
                linearResult,
                binaryResult
            )
        }
    }

    @Test
    fun testFindNearestPoint_tieBreaking_matchesMinByOrNull() {
        // Given points at 10.0 and 20.0, target is 15.0 (equidistant)
        val points = listOf(
            createPoint(10.0, 10L),
            createPoint(20.0, 20L)
        )

        val linearResult = points.minByOrNull { abs(it.distance - 15.0) }
        val binaryResult = TelemetryMetricUtils.findNearestPoint(points, 15.0, isTimeDomain = false)

        assertEquals(linearResult, binaryResult)
        assertEquals(points[0], binaryResult)
    }

    @Test
    fun testDataAvailabilityChecks() {
        val emptyList = emptyList<PathPoint>()
        assertFalse(TelemetryMetricUtils.hasHeartRateData(null))
        assertFalse(TelemetryMetricUtils.hasHeartRateData(emptyList))
        assertFalse(TelemetryMetricUtils.hasSpeedData(null))
        assertFalse(TelemetryMetricUtils.hasPowerData(null))

        val points = listOf(
            createPoint(10.0, 10L, hr = 145, power = 220, speedMps = 8.5)
        )
        assertTrue(TelemetryMetricUtils.hasHeartRateData(points))
        assertTrue(TelemetryMetricUtils.hasSpeedData(points))
        assertTrue(TelemetryMetricUtils.hasPowerData(points))

        val noDataPoints = listOf(
            createPoint(10.0, 10L, hr = 0, power = 0, speedMps = 0.0)
        )
        assertFalse(TelemetryMetricUtils.hasHeartRateData(noDataPoints))
        assertFalse(TelemetryMetricUtils.hasSpeedData(noDataPoints))
        assertFalse(TelemetryMetricUtils.hasPowerData(noDataPoints))
    }

    @Test
    fun testFormatPaceMinutes() {
        assertEquals("4:30", TelemetryMetricUtils.formatPaceMinutes(4.5))
        assertEquals("5:00", TelemetryMetricUtils.formatPaceMinutes(5.0))
        assertEquals("3:45", TelemetryMetricUtils.formatPaceMinutes(3.75))
    }
}
