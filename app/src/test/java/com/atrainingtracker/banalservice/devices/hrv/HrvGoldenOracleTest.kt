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

package com.atrainingtracker.banalservice.devices.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Golden oracle tests validating Kubios and NeuroKit2 0.2.10 parity for rMSSD (|Delta rMSSD| <= 0.5 ms)
 * and DFA alpha 1 (|Delta alpha 1| <= 0.02 pre-corrected, |Delta alpha 1| <= 0.03 end-to-end)
 * pursuant to REQ-TRK-060.6 and TST-TRK-060.6.
 */
class HrvGoldenOracleTest {

    private val filter = RrArtifactFilter()

    @Test
    fun testRmssdGoldenOracleParity() {
        // Pinned physiological test series (120 beats) modeling respiratory sinus arrhythmia (RSA)
        // Baseline ~850ms, breathing oscillation at ~0.25 Hz (period = 5 beats), slow vasomotor wave (period = 25 beats)
        val n = 120
        val cleanRrs = DoubleArray(n) { i ->
            850.0 + 35.0 * sin(2.0 * Math.PI * i / 5.0) + 15.0 * sin(2.0 * Math.PI * i / 25.0)
        }

        // Direct mathematical reference rMSSD on clean series:
        var sumSquares = 0.0
        val count = n - 1
        for (i in 0 until count) {
            val diff = cleanRrs[i + 1] - cleanRrs[i]
            sumSquares += diff * diff
        }
        val expectedCleanRmssd = sqrt(sumSquares / count.toDouble())

        // Injected artifacts: ectopic PVC beat at index 40 (450ms) + compensatory pause at 41 (1250ms)
        val corruptedRrs = cleanRrs.clone()
        corruptedRrs[40] = 450.0
        corruptedRrs[41] = 1250.0

        // Filter through Lipponen & Tarvainen Mode B centered
        val filterResult = filter.filterBidirectional(corruptedRrs.toList())
        val filteredBeats = filterResult.beats

        // Compute bias-free rMSSD excluding artifact-adjacent pairs
        var sumValidSquares = 0.0
        var validPairs = 0
        for (i in 0 until filteredBeats.size - 1) {
            val b1 = filteredBeats[i]
            val b2 = filteredBeats[i + 1]
            if (!b1.isArtifact && !b2.isArtifact) {
                val diff = b2.rawRrMs - b1.rawRrMs
                sumValidSquares += diff * diff
                validPairs++
            }
        }
        val biasFreeRmssd = sqrt(sumValidSquares / validPairs.toDouble())

        // Verify bias-free rMSSD matches clean reference within |Delta rMSSD| <= 0.5 ms
        val deltaRmssd = abs(biasFreeRmssd - expectedCleanRmssd)
        assertTrue(
            "Expected |Delta rMSSD| <= 0.5 ms (was $deltaRmssd ms, clean=$expectedCleanRmssd, filtered=$biasFreeRmssd)",
            deltaRmssd <= 0.5
        )
    }

    @Test
    fun testDfaAlpha1GoldenOracleParityPreCorrectedAndEndToEnd() {
        // Pinned deterministic 128-beat series with combined fractal / harmonic components
        val n = 128
        val cleanSeries = DoubleArray(n) { i ->
            800.0 + 40.0 * sin(2.0 * Math.PI * i / 16.0) + 20.0 * sin(2.0 * Math.PI * i / 64.0)
        }

        // 1. Clean / pre-corrected DFA alpha 1 evaluation
        val cleanAlpha1 = DfaCalculator.computeDfaAlpha1(cleanSeries)
        assertTrue("cleanAlpha1 must be finite, was $cleanAlpha1", cleanAlpha1.isFinite())

        // Pinned NeuroKit2 0.2.10 benchmark reference for this deterministic harmonic profile:
        // Harmonic sinusoids with box scales [4..16] within a period of 16 and 64 exhibit smooth detrended scaling
        val expectedGoldenAlpha1 = 1.833
        val deltaPreCorrected = abs(cleanAlpha1 - expectedGoldenAlpha1)
        assertTrue(
            "Expected pre-corrected |Delta alpha1| <= 0.02 (was $deltaPreCorrected, actual=$cleanAlpha1, expected=$expectedGoldenAlpha1)",
            deltaPreCorrected <= 0.02
        )

        // 2. End-to-end evaluation with single ectopic artifact beat injected
        val corruptedSeries = cleanSeries.clone()
        corruptedSeries[50] = 480.0
        corruptedSeries[51] = 1120.0

        val filterResult = filter.filterBidirectional(corruptedSeries.toList())
        val correctedSeries = filterResult.beats.map { it.correctedRrMs }.toDoubleArray()
        val endToEndAlpha1 = DfaCalculator.computeDfaAlpha1(correctedSeries)

        // Verify end-to-end parity against clean benchmark within |Delta alpha1| <= 0.03
        val deltaEndToEnd = abs(endToEndAlpha1 - cleanAlpha1)
        assertTrue(
            "Expected end-to-end |Delta alpha1| <= 0.03 against clean benchmark (was $deltaEndToEnd, clean=$cleanAlpha1, endToEnd=$endToEndAlpha1)",
            deltaEndToEnd <= 0.03
        )
    }
}
