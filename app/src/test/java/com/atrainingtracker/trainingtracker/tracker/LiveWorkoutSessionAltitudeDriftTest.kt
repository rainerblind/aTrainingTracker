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

package com.atrainingtracker.trainingtracker.tracker

import com.atrainingtracker.banalservice.sensor.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for [LiveWorkoutSession] retroactive altitude drift correction (REQ-TRK-047, TST-TRK-047.3, TST-TRK-047.4).
 */
class LiveWorkoutSessionAltitudeDriftTest {

    @Test
    fun testCaseA_uniformShift_whenNoPreviousCalibration() {
        val session = LiveWorkoutSession(1L, setOf(SensorType.ALTITUDE))

        // Record 3 samples: 500m at t=1000ms, 520m at t=2000ms, 510m at t=3000ms
        session.addSample(SensorType.ALTITUDE, 500.0, null)
        session.addSampledAltitudeWithTimestamp(500.0, 1000L)

        session.addSample(SensorType.ALTITUDE, 520.0, null)
        session.addSampledAltitudeWithTimestamp(520.0, 2000L)

        session.addSample(SensorType.ALTITUDE, 510.0, null)
        session.addSampledAltitudeWithTimestamp(510.0, 3000L)

        // Case A: deltaOffset = +30m, t0 = -1 (no previous calibration), t1 = 3000ms
        session.applyRetroactiveAltitudeCorrection(30.0, -1L, 3000L)

        val updatedAltitudes = session.sampledAltitudes
        assertEquals(3, updatedAltitudes.size)
        assertEquals(530.0, updatedAltitudes[0], 1e-4)
        assertEquals(550.0, updatedAltitudes[1], 1e-4)
        assertEquals(540.0, updatedAltitudes[2], 1e-4)

        // Verify RunningStats recomputed
        val stats = session.sensorStats[SensorType.ALTITUDE]
        assertNotNull(stats)
        assertEquals(530.0, stats!!.min, 1e-4)
        assertEquals(550.0, stats.max, 1e-4)
        assertEquals(1620.0, stats.sum, 1e-4)
        assertEquals(3, stats.count)

        // Verify recomputed ascent / descent
        assertEquals(20.0, session.sampledAscent, 1e-4)
        assertEquals(10.0, session.sampledDescent, 1e-4)
    }

    @Test
    fun testCaseB_piecewiseLinearInterpolation_withPreviousCalibration() {
        val session = LiveWorkoutSession(2L, setOf(SensorType.ALTITUDE))

        // Sample 0: Before t0 (e.g., recorded before landmark A) -> t=500ms, alt=400m
        session.addSample(SensorType.ALTITUDE, 400.0, null)
        session.addSampledAltitudeWithTimestamp(400.0, 500L)

        // Sample 1: At landmark A (first calibration) -> t0=1000ms, alt=450m
        session.addSample(SensorType.ALTITUDE, 450.0, null)
        session.addSampledAltitudeWithTimestamp(450.0, 1000L)

        // Sample 2: Mid-way to landmark B -> t=2000ms (halfway between 1000 and 3000), alt=480m
        session.addSample(SensorType.ALTITUDE, 480.0, null)
        session.addSampledAltitudeWithTimestamp(480.0, 2000L)

        // Sample 3: At landmark B (second calibration) -> t1=3000ms, alt=500m
        session.addSample(SensorType.ALTITUDE, 500.0, null)
        session.addSampledAltitudeWithTimestamp(500.0, 3000L)

        // Sample 4: After t1 -> t=4000ms, alt=510m
        session.addSample(SensorType.ALTITUDE, 510.0, null)
        session.addSampledAltitudeWithTimestamp(510.0, 4000L)

        // Case B: deltaOffset = +40m, t0 = 1000ms, t1 = 3000ms
        session.applyRetroactiveAltitudeCorrection(40.0, 1000L, 3000L)

        val updatedAltitudes = session.sampledAltitudes
        assertEquals(5, updatedAltitudes.size)

        // Sample 0: t < t0 -> unchanged
        assertEquals(400.0, updatedAltitudes[0], 1e-4)

        // Sample 1: t == t0 -> fraction = 0 -> correction = 0 -> 450.0
        assertEquals(450.0, updatedAltitudes[1], 1e-4)

        // Sample 2: t == 2000ms -> fraction = (2000 - 1000) / (3000 - 1000) = 0.5 -> correction = +20.0 -> 500.0
        assertEquals(500.0, updatedAltitudes[2], 1e-4)

        // Sample 3: t == 3000ms -> fraction = 1.0 -> correction = +40.0 -> 540.0
        assertEquals(540.0, updatedAltitudes[3], 1e-4)

        // Sample 4: t > t1 -> correction = +40.0 -> 550.0
        assertEquals(550.0, updatedAltitudes[4], 1e-4)

        // RunningStats should be recomputed
        val stats = session.sensorStats[SensorType.ALTITUDE]
        assertNotNull(stats)
        assertEquals(400.0, stats!!.min, 1e-4)
        assertEquals(550.0, stats.max, 1e-4)
        assertEquals(400.0 + 450.0 + 500.0 + 540.0 + 550.0, stats.sum, 1e-4)
        assertEquals(5, stats.count)

        // Verify recomputed ascent / descent for Case B
        assertEquals(150.0, session.sampledAscent, 1e-4)
        assertEquals(0.0, session.sampledDescent, 1e-4)
    }

    @Test
    fun testLegacyApplyAltitudeCorrection_delegatesToCaseA() {
        val session = LiveWorkoutSession(3L, setOf(SensorType.ALTITUDE))
        session.addSample(SensorType.ALTITUDE, 100.0, null)
        session.addSampledAltitudeWithTimestamp(100.0, 1000L)

        session.applyAltitudeCorrection(25.0)

        val updated = session.sampledAltitudes
        assertEquals(1, updated.size)
        assertEquals(125.0, updated[0], 1e-4)
        assertEquals(125.0, session.sensorStats[SensorType.ALTITUDE]!!.min, 1e-4)
    }
}
