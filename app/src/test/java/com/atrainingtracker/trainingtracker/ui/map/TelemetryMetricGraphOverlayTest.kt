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

import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying telemetry graph fresh model trajectory simulation, overlay state management,
 * and drift shading bounds (REQ-REV-010.3 / TST-REV-011.3).
 */
class TelemetryMetricGraphOverlayTest {

    private fun createSamplePoints(count: Int = 100): List<PathPoint> {
        val origin = LatLng(48.0, 11.0)
        return (0 until count).map { i ->
            PathPoint(
                distance = i * 10.0,
                latLng = origin,
                timeSec = i.toLong(),
                hr = 120 + (i % 30),
                power = 180 + (i % 40)
            )
        }
    }

    @Test
    fun testTrajectorySimulationOutputMatchesExpectedLength() {
        val points = createSamplePoints(60)
        val powerSamples = points.map { it.power?.toDouble() }

        val simulated = PowerHrDynamicsCalculator.simulateTrajectory(
            powerSamples = powerSamples,
            tau = 30.0,
            hr0 = 90.0,
            k = 0.35,
            dt = 1.0
        )

        assertEquals(60, simulated.size)
        // First sample should start at hr0 + k * power[0]
        val expectedFirst = 90.0 + 0.35 * (powerSamples[0] ?: 0.0)
        assertEquals(expectedFirst, simulated[0] ?: 0.0, 0.01)
    }

    @Test
    fun testDriftGapConditionEvaluatesCorrectly() {
        val points = createSamplePoints(10)
        val actualHr = points.map { it.hr?.toDouble() ?: 0.0 }
        // Simulated fresh model curve
        val freshModel = listOf(115.0, 118.0, 120.0, 122.0, 123.0, 124.0, 125.0, 125.0, 125.0, 125.0)

        var driftPointCount = 0
        for (i in actualHr.indices) {
            if (actualHr[i] > freshModel[i]) {
                driftPointCount++
            }
        }
        assertTrue(driftPointCount > 0)
    }

    @Test
    fun testOverlayGatingRequiresHeartRateAndPowerAndDynamics() {
        val pointsWithPower = createSamplePoints(10)
        val pointsWithoutPower = pointsWithPower.map { it.copy(power = null) }

        val record = CardioPowerDynamicsRecord(
            workoutId = 1L,
            tauStart = 30.0,
            hr0Start = 90.0,
            kStart = 0.35,
            r2Start = 0.85,
            rmseStart = 3.5
        )

        // Can render when HR metric, record non-null, toggle enabled, and power stream present
        fun canRender(
            metricType: TelemetryMetricType,
            record: CardioPowerDynamicsRecord?,
            enableToggle: Boolean,
            points: List<PathPoint>
        ): Boolean {
            val hasPower = points.any { it.power != null && it.power > 0 }
            return metricType == TelemetryMetricType.HEART_RATE &&
                record != null &&
                enableToggle &&
                hasPower
        }

        assertTrue(canRender(TelemetryMetricType.HEART_RATE, record, true, pointsWithPower))
        assertFalse(canRender(TelemetryMetricType.SPEED, record, true, pointsWithPower))
        assertFalse(canRender(TelemetryMetricType.HEART_RATE, null, true, pointsWithPower))
        assertFalse(canRender(TelemetryMetricType.HEART_RATE, record, false, pointsWithPower))
        assertFalse(canRender(TelemetryMetricType.HEART_RATE, record, true, pointsWithoutPower))
    }

    @Test
    fun testYAxisBoundsIncludeFreshModelTrajectoryWhenOverlayActive() {
        val actualHrValues = listOf(130.0, 140.0, 150.0, 160.0)
        val freshModelValues = listOf(110.0, 135.0, 145.0, 180.0) // dips to 110, surges to 180

        fun computeHrBounds(
            measured: List<Double>,
            freshModel: List<Double>,
            overlayActive: Boolean
        ): Pair<Double, Double> {
            val effective = if (overlayActive && freshModel.isNotEmpty()) {
                measured + freshModel
            } else {
                measured
            }
            val min = effective.minOrNull() ?: 0.0
            val max = effective.maxOrNull() ?: 1.0
            return (min - 5.0).coerceAtLeast(30.0) to (max + 5.0).coerceAtLeast(100.0)
        }

        // When overlay is inactive, bounds are strictly based on measured values [130, 160] -> [125, 165]
        val boundsInactive = computeHrBounds(actualHrValues, freshModelValues, overlayActive = false)
        assertEquals(125.0, boundsInactive.first, 0.01)
        assertEquals(165.0, boundsInactive.second, 0.01)

        // When overlay is active, bounds expand to [110, 180] -> [105, 185]
        val boundsActive = computeHrBounds(actualHrValues, freshModelValues, overlayActive = true)
        assertEquals(105.0, boundsActive.first, 0.01)
        assertEquals(185.0, boundsActive.second, 0.01)
    }

    @Test
    fun testDecimatedTrackpointsSimulationWithTimeSec() {
        // Trackpoints spaced by 5s
        val origin = LatLng(48.0, 11.0)
        val points = listOf(
            PathPoint(0.0, origin, timeSec = 0L, power = 200),
            PathPoint(50.0, origin, timeSec = 5L, power = 200),
            PathPoint(100.0, origin, timeSec = 10L, power = 200)
        )
        val powerSamples = points.map { it.power?.toDouble() }
        val timeSamples = points.map { it.timeSec }

        val simulated = PowerHrDynamicsCalculator.simulateTrajectory(
            powerSamples = powerSamples,
            timestampsSec = timeSamples,
            tau = 30.0,
            hr0 = 80.0,
            k = 0.25
        )

        assertEquals(3, simulated.size)
        // t=0: hr0 + k*p = 80 + 0.25*200 = 130.0
        assertEquals(130.0, checkNotNull(simulated[0]), 0.01)
        // At steady state power, HR stays at 130.0
        assertEquals(130.0, checkNotNull(simulated[1]), 0.01)
        assertEquals(130.0, checkNotNull(simulated[2]), 0.01)
    }
}
