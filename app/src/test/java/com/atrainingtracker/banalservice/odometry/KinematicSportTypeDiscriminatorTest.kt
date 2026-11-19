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

package com.atrainingtracker.banalservice.odometry

import com.atrainingtracker.banalservice.BSportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pure-JVM unit tests for [MovingSpeedHistogram] and [KinematicSportTypeDiscriminator]
 * (`ATT-8891`, `REQ-FIL-030.1`, `TST-FIL-030.1`).
 */
// Linked test specs: TST-FIL-030
class KinematicSportTypeDiscriminatorTest {

    @Test
    fun movingSpeedHistogram_throttlesHighRateAndBoundsQuantileError() {
        val hist = MovingSpeedHistogram()
        // 50-Hz burst at 6.0 m/s over 1 second must collapse to 1 moving second
        for (i in 0 until 50) {
            hist.recordThrottled(6.0, 1_000L + i * 20L)
        }
        assertEquals(1, hist.sampleCount())

        // Stationary samples (<= 0.8 m/s) are ignored
        assertFalse(hist.recordThrottled(0.0, 2_000L))
        assertFalse(hist.recordThrottled(0.8, 3_000L))
        assertEquals(1, hist.sampleCount())

        // Record 19 more 1-Hz moving samples (total 20): 10 at 4.0 m/s, 10 at 6.5 m/s
        for (sec in 1..10) {
            assertTrue(hist.recordThrottled(4.0, 2_000L + sec * 1_000L))
        }
        for (sec in 1..9) {
            assertTrue(hist.recordThrottled(6.5, 13_000L + sec * 1_000L))
        }
        val summary = hist.snapshot(coastingSeconds = 42.0)
        assertNotNull(summary)
        assertEquals(20, summary!!.movingSampleCount)
        assertEquals(42.0, summary.coastingSeconds, 1e-6)
        // Quantile midpoint error bounded to <= 0.05 m/s
        assertEquals(4.05, summary.p50Mps, 0.05)
        assertEquals(6.55, summary.p90Mps, 0.05)

        // Unthrottled 1-Hz entrypoint for LiveWorkoutSession loops without timestamps
        val oneHzHist = MovingSpeedHistogram()
        repeat(15) { oneHzHist.recordOneHzSample(5.23) }
        assertEquals(15, oneHzHist.sampleCount())
        assertEquals(5.23, oneHzHist.movingMeanMps(), 1e-6)
        assertEquals(5.25, oneHzHist.quantileMps(0.50), 0.05)

        oneHzHist.reset()
        assertEquals(0, oneHzHist.sampleCount())
        assertNull(oneHzHist.snapshot())
    }

    @Test
    fun errandBikeRide_metzgerProfileWithUnpausedStops_classifiesBikeAndPreservesMovingSpeed() {
        val disc = KinematicSportTypeDiscriminator()
        var ts = 1_000L

        // 20 s cycling at ~6.3 m/s with coasting (no steps, quiet gyro/acc)
        for (sec in 0 until 20) {
            feedQuietImuSecond(disc, ts, gyroMag = 0.05)
            disc.onCarriageMode(PhoneCarriageMode.BIKE_HANDLEBAR, ts)
            disc.onSpeedSample(if (sec < 10) 3.88 else 6.34, ts)
            ts += 1_000L
        }
        // 1200 s (20 min) unpaused bakery/butcher indoor stop at 0.0 m/s
        for (sec in 0 until 1200) {
            disc.onCarriageMode(PhoneCarriageMode.STATIONARY, ts)
            disc.onSpeedSample(0.0, ts)
            ts += 1_000L
        }
        // 15 s return ride with coasting at 6.65 m/s
        for (sec in 0 until 15) {
            feedQuietImuSecond(disc, ts, gyroMag = 0.04)
            disc.onCarriageMode(PhoneCarriageMode.BIKE_FRONT_POCKET, ts)
            disc.onSpeedSample(6.65, ts)
            ts += 1_000L
        }

        assertEquals(BSportType.BIKE, disc.inferredBSportType)
        assertTrue("Expected confirmed bicycle coasting seconds", disc.coastingSeconds >= 25.0)
        val summary = disc.getMovingSpeedSummary()
        assertNotNull(summary)
        assertEquals(35, summary!!.movingSampleCount)
        assertTrue("p90 must stay >= 6.3 m/s despite 20-min stop", summary.p90Mps >= 6.3)
        assertTrue("movingMean must stay >= 5.0 m/s despite 20-min stop", summary.movingMeanMps >= 5.0)
    }

    @Test
    fun runningKurzeRunde_withStepDetector_classifiesRunWithin30sAndZeroCoasting() {
        val disc = KinematicSportTypeDiscriminator()
        var ts = 1_000L
        var nextStepMs = 1_000L
        val stepPeriodMs = 370L // 2.70 Hz (162 steps/min), speed 2.32 m/s -> L_step = 0.86 m

        for (sec in 0 until 20) {
            while (nextStepMs <= ts + 1_000L) {
                disc.onStepDetected(nextStepMs)
                nextStepMs += stepPeriodMs
            }
            feedRunningAccImpactsSecond(disc, ts, stepFreqHz = 2.70, amplitudeMps2 = 6.0)
            disc.onCarriageMode(PhoneCarriageMode.RUN_WAIST_HIP, ts)
            disc.onSpeedSample(2.32, ts)
            ts += 1_000L
        }

        assertEquals(BSportType.RUN, disc.inferredBSportType)
        assertEquals(0.0, disc.coastingSeconds, 1e-6)
        val summary = disc.getMovingSpeedSummary()
        assertNotNull(summary)
        assertEquals(20, summary!!.movingSampleCount)
        assertEquals(2.32, summary.movingMeanMps, 0.05)
    }

    @Test
    fun budgetPhoneDegraded_noBaroNoGyroNoActivityRecognition_fast56MpsRunnerClassifiesRun() {
        // Simulates a budget phone lacking gyro/baro/rotVec with ACTIVITY_RECOGNITION denied:
        // zero TYPE_STEP_DETECTOR events, PhoneCarriageClassifier falls back to BIKE_HANDLEBAR at 5.6 m/s!
        val disc = KinematicSportTypeDiscriminator()
        var ts = 1_000L

        for (sec in 0 until 20) {
            feedRunningAccImpactsSecond(disc, ts, stepFreqHz = 3.20, amplitudeMps2 = 8.5)
            disc.onCarriageMode(PhoneCarriageMode.BIKE_HANDLEBAR, ts) // worst-case degraded fallback!
            disc.onSpeedSample(5.60, ts) // L_step = 5.60 / 3.20 = 1.75 m
            ts += 1_000L
        }

        assertEquals(
            "Permission-free 50-Hz ACC footstrike coherence must classify 5.6 m/s runner as RUN within 30 s",
            BSportType.RUN,
            disc.inferredBSportType
        )
        assertEquals(0.0, disc.coastingSeconds, 1e-6)
    }

    @Test
    fun slow20MpsUphillMtb_bumpyBroadbandChatter_doesNotMisclassifyAsRun() {
        val disc = KinematicSportTypeDiscriminator()
        val rng = Random(42)
        var ts = 1_000L

        // Slow 2.0 m/s steep MTB climb with bumpy gravel chatter (non-periodic) & worst-case RUN_WAIST_HIP fallback
        for (sec in 0 until 25) {
            for (k in 0 until 50) {
                val sampleTs = ts + k * 20L
                val crankPhase = 2.0 * PI * 1.15 * (sampleTs / 1000.0) // 69 RPM crank
                val noise = (rng.nextDouble() - 0.5) * 3.0
                val az = 9.81 + 0.5 * sin(crankPhase) + noise
                disc.onAccSample(0.2 * sin(crankPhase), 0.2, az, sampleTs)
            }
            disc.onCarriageMode(PhoneCarriageMode.RUN_WAIST_HIP, ts)
            disc.onSpeedSample(2.0, ts)
            ts += 1_000L
        }
        assertNotEquals(
            "Slow 2.0 m/s uphill MTB with broadband chatter must never lock BSportType.RUN",
            BSportType.RUN,
            disc.inferredBSportType
        )

        // When carriage detects BIKE_HANDLEBAR at 2.0 m/s, discriminator classifies BIKE
        for (sec in 0 until 10) {
            feedQuietImuSecond(disc, ts, gyroMag = 0.05)
            disc.onCarriageMode(PhoneCarriageMode.BIKE_HANDLEBAR, ts)
            disc.onSpeedSample(2.0, ts)
            ts += 1_000L
        }
        assertEquals(BSportType.BIKE, disc.inferredBSportType)
    }

    @Test
    fun midSessionRunToBikeTransition_recoversCleanlyToBike() {
        val disc = KinematicSportTypeDiscriminator()
        var ts = 1_000L
        var nextStepMs = 1_000L

        // 15 s initial run -> locks RUN
        for (sec in 0 until 15) {
            while (nextStepMs <= ts + 1_000L) {
                disc.onStepDetected(nextStepMs)
                nextStepMs += 350L // ~2.86 Hz
            }
            feedRunningAccImpactsSecond(disc, ts, stepFreqHz = 2.86, amplitudeMps2 = 6.5)
            disc.onCarriageMode(PhoneCarriageMode.RUN_WAIST_HIP, ts)
            disc.onSpeedSample(2.8, ts)
            ts += 1_000L
        }
        assertEquals(BSportType.RUN, disc.inferredBSportType)

        // Mount bicycle: 15 s riding/coasting at 6.2 m/s with zero steps
        for (sec in 0 until 15) {
            feedQuietImuSecond(disc, ts, gyroMag = 0.08)
            disc.onCarriageMode(PhoneCarriageMode.BIKE_HANDLEBAR, ts)
            disc.onSpeedSample(6.2, ts)
            ts += 1_000L
        }
        assertEquals(
            "Sustained bicycle coasting & carriage must recover RUN -> BIKE",
            BSportType.BIKE,
            disc.inferredBSportType
        )

        disc.reset()
        assertEquals(BSportType.UNKNOWN, disc.inferredBSportType)
        assertEquals(0.0, disc.coastingSeconds, 1e-6)
        assertNull(disc.getMovingSpeedSummary())
    }

    private fun feedQuietImuSecond(disc: KinematicSportTypeDiscriminator, startMs: Long, gyroMag: Double) {
        for (k in 0 until 50) {
            val t = startMs + k * 20L
            disc.onAccSample(0.05, 0.05, 9.81, t)
            disc.onGyroSample(gyroMag, t)
        }
    }

    private fun feedRunningAccImpactsSecond(
        disc: KinematicSportTypeDiscriminator,
        startMs: Long,
        stepFreqHz: Double,
        amplitudeMps2: Double
    ) {
        for (k in 0 until 50) {
            val t = startMs + k * 20L
            val phase = 2.0 * PI * stepFreqHz * (t / 1000.0)
            // Sharp vertical footstrike impact train at stepFreqHz
            val az = 9.81 + amplitudeMps2 * sin(phase)
            disc.onAccSample(0.3, 0.2, az, t)
        }
    }
}
