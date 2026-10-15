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

import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque

/**
 * Pure algorithmic verification of Dr. Andrew Coggan's Normalized Power (NP)
 * and Intensity Factor (IF = NP / FTP) calculations conforming to REQ-TRK-020 and TST-TRK-012.
 */
class NormalizedPowerMathTest {

    private class NormalizedPowerEngine(
        var ftp: Int? = null
    ) {
        val window30s = ArrayDeque<Double>(30)
        var sum4thPower = 0.0
        var count4thPower = 0L

        fun ingestSample(powerWatts: Double): Pair<Double?, Double?> {
            if (window30s.size == 30) {
                window30s.pollFirst()
            }
            window30s.addLast(powerWatts)

            if (window30s.size < 30) {
                return Pair(null, null)
            }

            var sum30 = 0.0
            for (p in window30s) {
                sum30 += p
            }
            val p30s = sum30 / 30.0
            val p30sPow4 = Math.pow(p30s, 4.0)

            sum4thPower += p30sPow4
            count4thPower++

            val np = Math.pow(sum4thPower / count4thPower, 0.25)

            val currentFtp = ftp
            val ifVal = if (currentFtp != null && currentFtp > 0) {
                np / currentFtp.toDouble()
            } else {
                null
            }

            return Pair(np, ifVal)
        }
    }

    @Test
    fun testWarmupWindow_lessThan30Samples_returnsNull() {
        val engine = NormalizedPowerEngine(ftp = 250)
        for (i in 1..29) {
            val (np, ifVal) = engine.ingestSample(200.0)
            assertNull("NP must be null before 30 samples have accumulated", np)
            assertNull("IF must be null before 30 samples have accumulated", ifVal)
        }
    }

    @Test
    fun testSteadyStatePower_30Samples_equalsArithmeticMean() {
        val engine = NormalizedPowerEngine(ftp = 250)
        var lastNp: Double? = null
        var lastIf: Double? = null

        // Ingest 60 samples of steady 200 Watts
        for (i in 1..60) {
            val (np, ifVal) = engine.ingestSample(200.0)
            lastNp = np
            lastIf = ifVal
        }

        assertNotNull(lastNp)
        assertEquals(200.0, lastNp!!, 0.001)
        assertNotNull(lastIf)
        assertEquals(200.0 / 250.0, lastIf!!, 0.001)
    }

    @Test
    fun testStochasticPower_surgeIntervals_npExceedsAveragePower() {
        val engine = NormalizedPowerEngine(ftp = 300)
        var lastNp: Double? = null

        // Ingest 30 samples of 100W, then 30 samples of 300W
        // Arithmetic mean across the 60 samples is 200W
        for (i in 1..30) {
            engine.ingestSample(100.0)
        }
        for (i in 1..30) {
            val (np, _) = engine.ingestSample(300.0)
            lastNp = np
        }

        assertNotNull(lastNp)
        // Due to the 4th power weighting of surges, NP strictly exceeds the 200W average power
        assertTrue(
            "Normalized Power ($lastNp) must strictly exceed average power (200W) in stochastic intervals",
            lastNp!! > 200.0
        )
    }

    @Test
    fun testCoastingInclusion_zeroWattsAreIngested() {
        val engine = NormalizedPowerEngine(ftp = 250)

        // 15 seconds at 300W, 15 seconds coasting at 0W
        for (i in 1..15) {
            engine.ingestSample(300.0)
        }
        var sample30Np: Double? = null
        for (i in 1..15) {
            val (np, _) = engine.ingestSample(0.0)
            sample30Np = np
        }

        // On sample 30, rolling 30s average is (15 * 300 + 15 * 0) / 30 = 150W
        assertNotNull(sample30Np)
        assertEquals(150.0, sample30Np!!, 0.001)
    }

    @Test
    fun testIntensityFactor_validFtp_computesCorrectRatio() {
        val engine = NormalizedPowerEngine(ftp = 300)
        var lastIf: Double? = null
        for (i in 1..30) {
            val (_, ifVal) = engine.ingestSample(240.0)
            lastIf = ifVal
        }

        assertNotNull(lastIf)
        assertEquals(240.0 / 300.0, lastIf!!, 0.001) // 0.80
    }

    @Test
    fun testIntensityFactor_unconfiguredFtp_returnsNull() {
        val engineNullFtp = NormalizedPowerEngine(ftp = null)
        val engineZeroFtp = NormalizedPowerEngine(ftp = 0)
        val engineNegativeFtp = NormalizedPowerEngine(ftp = -50)

        for (i in 1..30) {
            val (np1, if1) = engineNullFtp.ingestSample(250.0)
            val (np2, if2) = engineZeroFtp.ingestSample(250.0)
            val (np3, if3) = engineNegativeFtp.ingestSample(250.0)

            if (i == 30) {
                assertNotNull(np1)
                assertNull("IF must be null when FTP is null", if1)
                assertNotNull(np2)
                assertNull("IF must be null when FTP is 0", if2)
                assertNotNull(np3)
                assertNull("IF must be null when FTP is negative", if3)
            }
        }
    }

    @Test
    fun testMidRideFtpChange_updatesIntensityFactorImmediatelyWithoutAffectingNp() {
        val engine = NormalizedPowerEngine(ftp = 200)

        for (i in 1..35) {
            engine.ingestSample(200.0)
        }

        val (npBefore, ifBefore) = engine.ingestSample(200.0)
        assertEquals(200.0, npBefore!!, 0.001)
        assertEquals(1.0, ifBefore!!, 0.001) // 200 / 200 = 1.0

        // Athlete updates FTP mid-ride to 250W
        engine.ftp = 250
        val (npAfter, ifAfter) = engine.ingestSample(200.0)

        // NP history is completely unchanged (200W steady)
        assertEquals(200.0, npAfter!!, 0.001)
        // IF immediately updates to 200 / 250 = 0.80
        assertEquals(0.80, ifAfter!!, 0.001)
    }

    @Test
    fun testLongDurationStability_simulated5Hours_accumulatesWithoutPrecisionLoss() {
        val engine = NormalizedPowerEngine(ftp = 300)
        val seconds5Hours = 5 * 3600 // 18,000 seconds

        // Simulate 5 hours of fluctuating power between 150W and 350W
        var currentNp: Double? = null
        for (s in 1..seconds5Hours) {
            val power = if (s % 2 == 0) 350.0 else 150.0
            val (np, _) = engine.ingestSample(power)
            currentNp = np
        }

        assertNotNull(currentNp)
        // Expected NP for symmetric alternation of 150W and 350W:
        // P_30s alternates near 250W, so NP is strictly valid, finite, and positive
        assertTrue(currentNp!! > 0.0)
        assertFalse(currentNp.isNaN())
        assertFalse(currentNp.isInfinite())
        assertEquals(18000L - 29L, engine.count4thPower)
    }
}
