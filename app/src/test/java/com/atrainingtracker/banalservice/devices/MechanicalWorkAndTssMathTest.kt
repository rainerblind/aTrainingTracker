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

package com.atrainingtracker.banalservice.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import kotlin.math.pow

/**
 * Pure mathematical algorithmic tests for Mechanical Work (kJ) and Training Stress Score (TSS)
 * calculations conforming to Dr. Andrew Coggan's formulations and Welford's online running mean (REQ-TRK-021, TST-TRK-013).
 */
class MechanicalWorkAndTssMathTest {

    private class TestTelemetryEngine(var ftp: Int? = 250) {
        val window = ArrayDeque<Double>(30)
        var mean4thPower = 0.0
        var count4thPower = 0L
        var workJoules = 0.0
        var activePowerDurationSec = 0L
        var lastCalculatedNp: Double? = null

        fun ingestSample(power: Double?, isPaused: Boolean = false, pauseDurationMs: Long = 0L) {
            if (isPaused) {
                return
            }

            if (pauseDurationMs > 30000L) {
                window.clear()
            }

            if (power == null || power.isNaN() || power.isInfinite() || power < 0.0) {
                return
            }

            // 1. Mechanical Work Integration (1Hz)
            workJoules += power * 1.0
            activePowerDurationSec++

            // 2. Rolling 30-sample buffer for NP (growing 1..29s during warm-up / refill per Rule 40)
            if (window.size == 30) {
                window.pollFirst()
            }
            window.addLast(power)

            // 3. Rolling average over current window (1..30 samples)
            var sum = 0.0
            for (p in window) {
                sum += p
            }
            val pRolling = sum / window.size.toDouble()
            val pRollingPow4 = pRolling.pow(4.0)

            // Welford's online running mean recurrence:
            // Y_N = Y_{N-1} + (y_N - Y_{N-1}) / N
            count4thPower++
            mean4thPower += (pRollingPow4 - mean4thPower) / count4thPower.toDouble()

            lastCalculatedNp = mean4thPower.pow(0.25)
        }

        fun getWorkKj(): Double {
            return workJoules / 1000.0
        }

        fun getNormalizedPower(): Double? {
            return lastCalculatedNp
        }

        fun getIntensityFactor(): Double? {
            val np = getNormalizedPower() ?: return null
            val currentFtp = ftp ?: return null
            if (currentFtp <= 0) return null
            return np / currentFtp.toDouble()
        }

        fun getTss(): Double? {
            val np = getNormalizedPower() ?: return null
            val ifVal = getIntensityFactor() ?: return null
            val currentFtp = ftp ?: return null
            if (currentFtp <= 0) return null

            // TSS = [(s * NP * IF) / (FTP * 3600)] * 100
            //     = (s * NP * IF) / (FTP * 36)
            return (activePowerDurationSec * np * ifVal) / (currentFtp.toDouble() * 36.0)
        }
    }

    @Test
    fun testWorkIntegration_constantPower_accumulatesCorrectKilojoules() {
        val engine = TestTelemetryEngine()
        // 10 seconds at 200 Watts = 2000 Joules = 2.0 kJ
        repeat(10) {
            engine.ingestSample(200.0)
        }
        assertEquals(2.0, engine.getWorkKj(), 1e-6)
        assertEquals(10L, engine.activePowerDurationSec)
    }

    @Test
    fun testWorkIntegration_coastingZeroWatts_doesNotAddEnergy() {
        val engine = TestTelemetryEngine()
        // 5 seconds at 200W = 1.0 kJ
        repeat(5) {
            engine.ingestSample(200.0)
        }
        assertEquals(1.0, engine.getWorkKj(), 1e-6)
        assertEquals(5L, engine.activePowerDurationSec)

        // 10 seconds coasting at 0W -> work stays at 1.0 kJ, active duration reaches 15s
        repeat(10) {
            engine.ingestSample(0.0)
        }
        assertEquals(1.0, engine.getWorkKj(), 1e-6)
        assertEquals(15L, engine.activePowerDurationSec)
    }

    @Test
    fun testWorkIntegration_negativeOrInvalidPower_isIgnored() {
        val engine = TestTelemetryEngine()
        engine.ingestSample(250.0)
        assertEquals(0.25, engine.getWorkKj(), 1e-6)

        // Invalid power packets
        engine.ingestSample(-10.0)
        engine.ingestSample(Double.NaN)
        engine.ingestSample(Double.POSITIVE_INFINITY)
        engine.ingestSample(null)

        // Work remains exactly 0.25 kJ and active duration remains 1
        assertEquals(0.25, engine.getWorkKj(), 1e-6)
        assertEquals(1L, engine.activePowerDurationSec)
    }

    @Test
    fun testWorkIntegration_pauseGating_haltsAccumulation() {
        val engine = TestTelemetryEngine()
        repeat(5) {
            engine.ingestSample(200.0)
        }
        assertEquals(1.0, engine.getWorkKj(), 1e-6)

        // While paused, ticks are ignored
        repeat(10) {
            engine.ingestSample(200.0, isPaused = true)
        }
        assertEquals(1.0, engine.getWorkKj(), 1e-6)
        assertEquals(5L, engine.activePowerDurationSec)

        // Resumed
        engine.ingestSample(200.0, isPaused = false)
        assertEquals(1.2, engine.getWorkKj(), 1e-6)
        assertEquals(6L, engine.activePowerDurationSec)
    }

    @Test
    fun testTss_growingWarmupWindow_emitsValidTssFromFirstSecond() {
        val engine = TestTelemetryEngine(ftp = 250)
        // During the first 29 seconds, growing rolling window emits valid NP, IF, and TSS from tick 1
        for (t in 1..29) {
            engine.ingestSample(250.0)
            assertNotNull("TSS must be emitted during warmup tick $t", engine.getTss())
            assertEquals(t.toDouble() / 36.0, engine.getTss()!!, 1e-4)
        }

        // On tick 30, window is complete, NP is 250W, IF is 1.0, TSS continues smoothly
        engine.ingestSample(250.0)
        assertNotNull(engine.getTss())
        assertEquals(30.0 / 36.0, engine.getTss()!!, 1e-4)
    }

    @Test
    fun testTss_canonicalOneHourAtFtp_equals100() {
        val engine = TestTelemetryEngine(ftp = 250)
        // 3600 seconds at FTP (250 W)
        repeat(3600) {
            engine.ingestSample(250.0)
        }

        assertEquals(250.0, engine.getNormalizedPower()!!, 1e-3)
        assertEquals(1.0, engine.getIntensityFactor()!!, 1e-4)
        // TSS = (3600 * 250 * 1.0) / (250 * 36) = 100.0
        assertEquals(100.0, engine.getTss()!!, 1e-2)
        // Mechanical work: 3600 * 250 J = 900,000 J = 900 kJ
        assertEquals(900.0, engine.getWorkKj(), 1e-4)
    }

    @Test
    fun testTss_canonicalHalfHourAtFtp_equals50() {
        val engine = TestTelemetryEngine(ftp = 250)
        // 1800 seconds at FTP (250 W)
        repeat(1800) {
            engine.ingestSample(250.0)
        }

        assertEquals(250.0, engine.getNormalizedPower()!!, 1e-3)
        assertEquals(1.0, engine.getIntensityFactor()!!, 1e-4)
        // TSS = (1800 * 250 * 1.0) / (250 * 36) = 50.0
        assertEquals(50.0, engine.getTss()!!, 1e-2)
        assertEquals(450.0, engine.getWorkKj(), 1e-4)
    }

    @Test
    fun testTss_twoHoursSweetSpot() {
        val engine = TestTelemetryEngine(ftp = 250)
        // 2 hours (7200 seconds) at 225 W (90% of FTP, SweetSpot)
        repeat(7200) {
            engine.ingestSample(225.0)
        }

        assertEquals(225.0, engine.getNormalizedPower()!!, 1e-3)
        assertEquals(0.90, engine.getIntensityFactor()!!, 1e-4)
        // TSS = (7200 * 225 * 0.90) / (250 * 36) = 1458000 / 9000 = 162.0
        assertEquals(162.0, engine.getTss()!!, 1e-2)
        assertEquals(1620.0, engine.getWorkKj(), 1e-4)
    }

    @Test
    fun testTss_unconfiguredFtp_returnsNull() {
        val engine = TestTelemetryEngine(ftp = null)
        repeat(60) {
            engine.ingestSample(200.0)
        }
        assertNotNull(engine.getNormalizedPower())
        assertNull(engine.getIntensityFactor())
        assertNull(engine.getTss())

        // Zero FTP also returns null
        engine.ftp = 0
        assertNull(engine.getIntensityFactor())
        assertNull(engine.getTss())

        // Negative FTP also returns null
        engine.ftp = -50
        assertNull(engine.getIntensityFactor())
        assertNull(engine.getTss())
    }

    @Test
    fun testTss_midRideFtpUpdate_recalculatesTssImmediately() {
        val engine = TestTelemetryEngine(ftp = 200)
        // 1 hour at 200W with FTP = 200 -> TSS = 100.0
        repeat(3600) {
            engine.ingestSample(200.0)
        }
        assertEquals(100.0, engine.getTss()!!, 1e-2)

        // Athlete realizes FTP was set too low and updates to 250W
        engine.ftp = 250
        // New IF = 200 / 250 = 0.80
        // New TSS = (3600 * 200 * 0.80) / (250 * 36) = 576000 / 9000 = 64.0
        assertEquals(64.0, engine.getTss()!!, 1e-2)
        // Work is completely unaffected by FTP change: 720 kJ
        assertEquals(720.0, engine.getWorkKj(), 1e-4)
    }

    @Test
    fun testLongRideStability_welfordAlgorithm24Hours() {
        val engine = TestTelemetryEngine(ftp = 400)
        // Simulate a 24-hour ultra ride at 400W: 86,400 ticks
        repeat(86400) {
            engine.ingestSample(400.0)
        }

        // Bounded Welford mean: exactly 400^4 = 2.56e10
        assertEquals(400.0, engine.getNormalizedPower()!!, 1e-6)
        assertEquals(1.0, engine.getIntensityFactor()!!, 1e-6)
        // TSS = (86400 * 400 * 1.0) / (400 * 36) = 2400.0
        assertEquals(2400.0, engine.getTss()!!, 1e-4)
        // Work = 86400 * 400 J = 34,560,000 J = 34,560 kJ
        assertEquals(34560.0, engine.getWorkKj(), 1e-6)
    }

    @Test
    fun testLongPause_windowFlushed_staleSamplesPrevented() {
        val engine = TestTelemetryEngine(ftp = 250)
        // Ride for 60 seconds at 300W
        repeat(60) {
            engine.ingestSample(300.0)
        }
        val prePauseNp = engine.getNormalizedPower()!!
        assertEquals(300.0, prePauseNp, 1e-2)

        // Long pause: 15 minutes (900,000 ms > 30,000 ms)
        // Ingest next sample with pauseDurationMs = 900_000L
        engine.ingestSample(100.0, pauseDurationMs = 900_000L)

        // Window size should have reset to 1 fresh sample (100W uncontaminated by pre-pause 300W)
        assertEquals(1, engine.window.size)
        // Growing post-pause window immediately blends the fresh 100W sample into cumulative NP
        val firstPostPauseNp = engine.getNormalizedPower()!!
        assertTrue(firstPostPauseNp < 300.0 && firstPostPauseNp > 290.0)

        // Ingest remaining 29 samples of 100W
        repeat(29) {
            engine.ingestSample(100.0)
        }
        assertEquals(30, engine.window.size)
        // NP has updated using fresh 100W rolling samples, blending into cumulative mean
        assertTrue(engine.getNormalizedPower()!! < firstPostPauseNp)
    }
}
