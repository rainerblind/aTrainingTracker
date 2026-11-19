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
 * Unit tests for [LiveWorkoutSession] retroactive altitude drift correction
 * (REQ-TRK-047, TST-TRK-047.3, TST-TRK-047.4; REQ-FIL-004, TST-FIL-003.1).
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

    /**
     * TST-FIL-018.3 (Criterion 3): Case B (t0Ms > 0) keeps 1-Hz altStats.count and altStats.sum
     * dimensionally consistent instead of summing 20-second sampledAltitudes over 1-Hz count.
     */
    @Test
    fun testCaseB_1HzRunningStatsWith20sSampledAltitudes_preservesAverageAltitude() {
        val session = LiveWorkoutSession(4L, setOf(SensorType.ALTITUDE))

        // Record 100 1-Hz samples around 445.0m
        repeat(100) {
            session.addSample(SensorType.ALTITUDE, 445.0, null)
        }
        // Record 5 corresponding 20-second stream samples
        session.addSampledAltitudeWithTimestamp(445.0, 500L)
        session.addSampledAltitudeWithTimestamp(445.0, 1500L)
        session.addSampledAltitudeWithTimestamp(445.0, 2500L)
        session.addSampledAltitudeWithTimestamp(445.0, 3500L)
        session.addSampledAltitudeWithTimestamp(445.0, 4500L)

        // Case B interpolation: [1500ms, 3500ms] -> corrections = [0, 0, +20, +40, +40], mean = +20.0m
        session.applyRetroactiveAltitudeCorrection(40.0, 1500L, 3500L)

        val stats = session.sensorStats[SensorType.ALTITUDE]!!
        assertEquals("1-Hz count must remain untouched", 100, stats.count)
        assertEquals("AVG ALTITUDE must equal 445.0 + 20.0 = 465.0m (never divided by ~20)", 465.0, stats.average, 1e-4)
    }

    /**
     * TST-FIL-018.2 (Criterion 2): On post-crash rehydration, Case A protects pre-crash sampledAltitudes
     * and pre-crash RunningStats while shifting only uncalibrated post-restart samples.
     */
    @Test
    fun testCaseA_rehydratedPreCrashSession_epochBoundProtectsPreCrashSamplesAndStats() {
        val session = LiveWorkoutSession(5L, setOf(SensorType.ALTITUDE))
        session.rehydrateStreams(null, listOf(440.0, 445.0, 448.0), null)
        session.rehydrateSensorStats(SensorType.ALTITUDE, 438.0, 448.0, 444.0, 640)
        session.setSessionEpochStartTimeMs(10_000L)

        // Post-restart uncalibrated samples
        repeat(10) {
            session.addSample(SensorType.ALTITUDE, 380.0, null)
        }
        session.addSampledAltitudeWithTimestamp(380.0, 10_500L)
        // Post-calibration sample (after t1Ms = 20_000L)
        session.addSampledAltitudeWithTimestamp(448.0, 25_000L)

        session.applyRetroactiveAltitudeCorrection(62.0, -1L, 20_000L)

        val alts = session.sampledAltitudes
        assertEquals(5, alts.size)
        assertEquals(440.0, alts[0], 1e-4)
        assertEquals(445.0, alts[1], 1e-4)
        assertEquals(448.0, alts[2], 1e-4)
        assertEquals("Post-restart uncalibrated sample shifts by +62m", 442.0, alts[3], 1e-4)
        assertEquals("Post-calibration sample (> t1Ms) stays untouched", 448.0, alts[4], 1e-4)

        val stats = session.sensorStats[SensorType.ALTITUDE]!!
        assertEquals(650, stats.count)
        assertEquals(438.0, stats.min, 1e-4)
        assertEquals(448.0, stats.max, 1e-4)
        val expectedSum = 444.0 * 640 + 442.0 * 10
        assertEquals(expectedSum, stats.sum, 1e-4)
    }
}
