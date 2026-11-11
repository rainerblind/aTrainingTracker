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
        // Harmonic sinusoids with box scales [4..16] with 50% overlap (step = boxSize / 2)
        val expectedGoldenAlpha1 = 1.784
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

    @Test
    fun testNeuroKit2GoldenVectorsParity() {
        val testCases = listOf(
            Triple("White noise", WHITE_NOISE_SERIES, 0.5144),
            Triple("Threshold", THRESHOLD_SERIES, 0.7550),
            Triple("Pink noise", PINK_NOISE_SERIES, 0.9622),
            Triple("Aerobic", AEROBIC_SERIES, 1.4379)
        )

        for ((name, series, expectedAlpha1) in testCases) {
            val alpha1 = DfaCalculator.computeDfaAlpha1(series)
            val delta = abs(alpha1 - expectedAlpha1)
            assertTrue(
                "Expected $name |Delta alpha1| <= 0.02 (was $delta, actual=$alpha1, expected=$expectedAlpha1)",
                delta <= 0.02
            )
        }
    }

    companion object {
        val WHITE_NOISE_SERIES = doubleArrayOf(
            819.87, 794.47, 825.91, 860.92, 790.63, 790.63, 863.17, 830.70, 781.22, 821.70,
            781.46, 781.37, 809.68, 723.47, 731.00, 777.51, 759.49, 812.57, 763.68, 743.51,
            858.63, 790.97, 802.70, 743.01, 778.22, 804.44, 753.96, 815.03, 775.97, 788.33,
            775.93, 874.09, 799.46, 757.69, 832.90, 751.17, 808.35, 721.61, 746.87, 807.87,
            829.54, 806.85, 795.37, 787.96, 740.86, 771.21, 781.57, 842.28, 813.74, 729.48,
            812.96, 784.60, 772.92, 824.47, 841.24, 837.25, 766.43, 787.63, 813.25, 839.02,
            780.83, 792.57, 755.75, 752.15, 832.50, 854.25, 797.12, 840.14, 814.47, 774.20,
            814.46, 861.52, 798.57, 862.59, 695.21, 832.88, 803.48, 788.04, 803.67, 720.50,
            791.21, 814.28, 859.12, 779.27, 767.66, 779.93, 836.62, 813.15, 778.81, 820.53,
            803.88, 838.75, 771.92, 786.89, 784.32, 741.46, 811.84, 810.44, 800.20, 790.62,
            743.39, 783.17, 786.29, 767.91, 793.55, 816.16, 875.45, 806.98, 810.30, 797.02,
            723.25, 798.94, 802.41, 898.53, 792.31, 812.06, 798.61, 753.25, 845.71, 830.08
        )

        val THRESHOLD_SERIES = doubleArrayOf(
            795.49, 804.80, 833.08, 836.63, 821.57, 792.45, 821.37, 810.91, 789.05, 797.35,
            780.43, 794.28, 819.08, 768.22, 753.24, 809.60, 796.01, 796.89, 817.53, 765.58,
            861.70, 815.68, 834.86, 806.06, 785.16, 771.75, 751.32, 805.50, 798.79, 806.10,
            792.08, 853.73, 800.93, 787.43, 823.84, 790.27, 809.43, 768.37, 780.39, 827.72,
            833.76, 810.54, 798.01, 800.99, 739.80, 755.37, 781.63, 829.42, 827.73, 775.48,
            842.05, 769.19, 755.63, 803.94, 820.77, 831.40, 789.21, 812.93, 809.98, 827.05,
            790.14, 789.25, 764.12, 759.65, 797.28, 810.69, 812.64, 855.86, 806.99, 788.86,
            800.86, 840.11, 788.29, 810.29, 743.21, 823.35, 813.64, 834.74, 838.31, 786.23,
            820.07, 825.36, 847.25, 782.58, 794.48, 795.54, 809.71, 789.16, 739.70, 805.44,
            786.15, 786.54, 751.20, 759.91, 760.47, 750.38, 803.57, 780.85, 756.26, 764.60,
            737.86, 764.98, 776.12, 770.81, 763.11, 796.41, 839.58, 792.52, 808.42, 794.78,
            728.56, 780.80, 785.39, 846.88, 815.20, 840.01, 832.96, 810.07, 854.22, 818.06
        )

        val PINK_NOISE_SERIES = doubleArrayOf(
            771.12, 815.12, 840.24, 812.33, 852.51, 794.27, 779.57, 791.13, 796.88, 772.99,
            779.40, 807.19, 828.47, 812.98, 775.47, 841.70, 832.53, 781.20, 871.38, 787.65,
            864.77, 840.40, 867.02, 869.10, 792.09, 739.06, 748.68, 795.96, 821.60, 823.87,
            808.23, 833.37, 802.39, 817.17, 814.79, 829.37, 810.51, 815.14, 813.91, 847.56,
            837.98, 814.23, 800.64, 814.02, 738.74, 739.53, 781.68, 816.56, 841.72, 821.48,
            871.14, 753.78, 738.34, 783.41, 800.31, 825.55, 812.00, 838.23, 806.70, 815.08,
            799.44, 785.93, 772.50, 767.14, 762.07, 767.14, 828.16, 871.58, 799.51, 803.52,
            787.26, 818.71, 778.02, 758.00, 791.21, 813.82, 823.80, 881.44, 872.95, 851.95,
            848.92, 836.43, 835.39, 785.90, 821.30, 811.14, 782.80, 765.17, 700.59, 790.35,
            768.41, 734.34, 730.48, 732.92, 736.62, 759.30, 795.30, 751.26, 712.32, 738.58,
            732.34, 746.79, 765.95, 773.71, 732.67, 776.67, 803.72, 778.06, 806.54, 792.54,
            733.88, 762.65, 768.36, 795.23, 838.09, 867.95, 867.31, 866.88, 862.73, 806.04
        )

        val AEROBIC_SERIES = doubleArrayOf(
            800.00, 783.72, 801.12, 805.20, 781.82, 775.87, 804.26, 767.22, 765.70, 789.84,
            778.36, 771.42, 774.29, 800.52, 790.86, 785.57, 781.22, 817.12, 847.36, 855.31,
            852.81, 855.95, 869.92, 845.39, 856.22, 828.98, 815.07, 826.41, 801.02, 798.77,
            786.03, 784.29, 744.67, 726.39, 726.94, 751.81, 756.43, 763.01, 778.88, 768.86,
            777.78, 769.03, 747.76, 749.74, 765.88, 776.08, 779.49, 818.45, 821.88, 833.28,
            861.86, 833.17, 812.61, 836.87, 819.37, 816.91, 830.41, 839.21, 859.65, 873.14,
            878.21, 854.89, 858.58, 854.51, 826.44, 843.73, 849.28, 842.57, 832.69, 809.81,
            811.33, 816.66, 801.69, 818.87, 799.58, 767.80, 788.22, 783.94, 784.46, 774.23,
            754.00, 779.73, 772.44, 801.49, 813.37, 806.65, 789.36, 779.97, 764.79, 801.38,
            803.64, 820.34, 798.28, 801.25, 818.73, 810.90, 824.73, 804.75, 783.59, 791.74,
            787.29, 798.83, 769.34, 784.62, 825.90, 821.65, 818.91, 818.77, 788.02, 796.21,
            772.70, 770.38, 793.47, 783.42, 793.42, 809.60, 812.34, 789.93, 786.45, 817.87
        )
    }
}
