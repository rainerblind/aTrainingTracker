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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.odometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Pure-JVM unit tests for [BarometricBucketDispersion] (`REQ-FIL-040.1`, `TST-FIL-040.1`, `ATT-9602`).
 */
class BarometricBucketDispersionTest {

    @Test
    fun calmHandlebarAndSteepAlpineDescent_keepZeroDispersionAndEmitExactBucketMedian() {
        val dispersion = BarometricBucketDispersion()
        var tMs = 1_000L

        // 1. Calm 10% climb at 5 m/s (+0.5 m/s vertical rate, ~10 Hz baro)
        for (sec in 0 until 5) {
            val expectedSamples = mutableListOf<Double>()
            for (sub in 0 until 10) {
                val alt = 500.0 + (sec + sub * 0.1) * 0.5
                expectedSamples.add(alt)
                dispersion.addSample(alt, tMs)
                tMs += 100L
            }
            val result = dispersion.flushBucket(tMs - 100L, speedMps = 5.0)
            assertNotNull(result)
            val sorted = expectedSamples.sorted()
            val expectedMedian = 0.5 * (sorted[4] + sorted[5])
            assertEquals("Calm climb must keep dispersionVarM2 == 0.0", 0.0, result!!.dispersionVarM2, 1e-9)
            assertEquals("Calm climb must emit exact 1-Hz bucket median", expectedMedian, result.representativeAltitudeM, 1e-9)
        }

        // 2. Fast -12% alpine descent at 18 m/s (-2.16 m/s vertical rate -> IQR ~1.08 m << physicalSpan = 3.6 m)
        for (sec in 0 until 5) {
            val expectedSamples = mutableListOf<Double>()
            for (sub in 0 until 10) {
                val alt = 502.5 - (sec + sub * 0.1) * 2.16
                expectedSamples.add(alt)
                dispersion.addSample(alt, tMs)
                tMs += 100L
            }
            val result = dispersion.flushBucket(tMs - 100L, speedMps = 18.0)
            assertNotNull(result)
            val sorted = expectedSamples.sorted()
            val expectedMedian = 0.5 * (sorted[4] + sorted[5])
            assertEquals("Fast alpine descent must keep dispersionVarM2 == 0.0", 0.0, result!!.dispersionVarM2, 1e-9)
            assertEquals("Fast alpine descent must emit exact 1-Hz median without lag", expectedMedian, result.representativeAltitudeM, 1e-9)
        }
    }

    @Test
    fun pocketCompression092HzWave_inflatesDispersionAndSuppressesBeatAliasing() {
        val dispersion = BarometricBucketDispersion()
        val baselineAlt = 438.0
        var maxRepBiasM = 0.0
        var minDispVarAfterOnset = Double.MAX_VALUE

        // Simulate 25 s of ~13 Hz (77 ms) asymmetric 0.92 Hz 80 m p-p pocket-compression wave
        var tMs = 0L
        for (sec in 1..25) {
            val secEndMs = sec * 1_000L
            while (tMs < secEndMs) {
                val tSec = tMs / 1000.0
                val phase = 2.0 * Math.PI * 0.92 * tSec
                // Asymmetric wave: sharper compression trough, broader decompression peak
                val wave = 40.0 * sin(phase) - 8.0 * sin(2.0 * phase)
                dispersion.addSample(baselineAlt + wave, tMs)
                tMs += 77L
            }
            val result = dispersion.flushBucket(tMs - 77L, speedMps = 6.0)
            assertNotNull(result)
            minDispVarAfterOnset = minOf(minDispVarAfterOnset, result!!.dispersionVarM2)
            if (sec >= 3) {
                maxRepBiasM = maxOf(maxRepBiasM, abs(result.representativeAltitudeM - baselineAlt))
            }
        }

        assertTrue(
            "Pocket wave must trigger immediate high dispersion variance (>= 500 m^2, was $minDispVarAfterOnset)",
            minDispVarAfterOnset >= 500.0
        )
        assertTrue(
            "Dispersion variance must remain bounded by MAX_DISPERSION_VAR_M2",
            dispersion.dispersionVarM2 <= BarometricBucketDispersion.MAX_DISPERSION_VAR_M2
        )
        assertTrue(
            "Multi-second trimmed mean must suppress beat aliasing bias (max bias was $maxRepBiasM m)",
            maxRepBiasM <= 6.0
        )
    }

    @Test
    fun dispersionVarianceDecaysSmoothlyWhenPocketWaveStops() {
        val dispersion = BarometricBucketDispersion()
        // Inject 1 s of strong pocket wave
        for (i in 0 until 12) {
            val alt = if (i % 2 == 0) 400.0 else 480.0
            dispersion.addSample(alt, i * 80L)
        }
        val spike = dispersion.flushBucket(11 * 80L, speedMps = 5.0)!!
        assertEquals(BarometricBucketDispersion.MAX_DISPERSION_VAR_M2, spike.dispersionVarM2, 1e-6)

        // Follow with calm samples -> exponential 0.65 retention decay back to 0.0
        var prevVar = spike.dispersionVarM2
        var tMs = 1_000L
        for (sec in 1..30) {
            for (i in 0 until 5) {
                dispersion.addSample(440.0, tMs)
                tMs += 200L
            }
            val calm = dispersion.flushBucket(tMs - 200L, speedMps = 5.0)!!
            assertTrue("Dispersion variance must decay monotonically", calm.dispersionVarM2 <= prevVar)
            prevVar = calm.dispersionVarM2
        }
        assertEquals("Dispersion variance must decay cleanly to 0.0 after sustained calm", 0.0, dispersion.dispersionVarM2, 1e-9)
    }

    @Test
    fun guardsNonFiniteBackwardTimestampPauseGapSmallBucketsAndDelayedFlush() {
        val dispersion = BarometricBucketDispersion()
        assertNull("Empty bucket flush must return null", dispersion.flushBucket(1_000L, 5.0))

        // Non-finite samples ignored
        dispersion.addSample(Double.NaN, 1_000L)
        dispersion.addSample(Double.POSITIVE_INFINITY, 1_100L)
        assertTrue(dispersion.isBucketEmpty)

        // Small bucket (size 1 -> iqr 0.0; size 2..3 -> 0.5 * span)
        dispersion.addSample(440.0, 1_000L)
        val single = dispersion.flushBucket(1_000L, 5.0)!!
        assertEquals(0.0, single.bucketIqrM, 1e-9)
        assertEquals(440.0, single.representativeAltitudeM, 1e-9)

        dispersion.addSample(400.0, 2_000L)
        dispersion.addSample(440.0, 2_200L)
        val pair = dispersion.flushBucket(2_200L, 5.0)!!
        assertEquals(20.0, pair.bucketIqrM, 1e-9)
        assertTrue("2-sample 40 m jump must inflate dispersionVarM2", pair.dispersionVarM2 > 100.0)

        // Pause gap (> 3,000 ms) resets stale dispersion variance & rolling window
        dispersion.addSample(442.0, 10_000L)
        val postPause = dispersion.flushBucket(10_000L, 5.0)!!
        assertEquals("Pause gap must clear stale dispersionVarM2", 0.0, postPause.dispersionVarM2, 1e-9)
        assertEquals(442.0, postPause.representativeAltitudeM, 1e-9)

        // Backward timestamp resets cleanly
        dispersion.addSample(400.0, 11_000L)
        dispersion.addSample(480.0, 11_100L)
        dispersion.flushBucket(11_100L, 5.0)
        assertTrue(dispersion.dispersionVarM2 > 100.0)
        dispersion.addSample(445.0, 5_000L)
        val postBackward = dispersion.flushBucket(5_000L, 5.0)!!
        assertEquals("Backward timestamp must reset dispersionVarM2", 0.0, postBackward.dispersionVarM2, 1e-9)
        assertEquals(445.0, postBackward.representativeAltitudeM, 1e-9)

        // Delayed flush (flushTimestampMs >> sample timestamp) falls back to bucket median instead of NaN
        dispersion.addSample(400.0, 20_000L)
        dispersion.addSample(480.0, 20_100L)
        val delayed = dispersion.flushBucket(30_000L, 5.0)!!
        assertTrue("Delayed flush representative altitude must be finite", delayed.representativeAltitudeM.isFinite())
        assertEquals(440.0, delayed.representativeAltitudeM, 1e-9)
    }
}
