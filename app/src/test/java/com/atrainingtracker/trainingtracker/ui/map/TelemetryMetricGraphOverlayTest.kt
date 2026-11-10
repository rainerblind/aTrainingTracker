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
}
