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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.*
import org.junit.Test

class TelemetryHistogramCalculatorTest {

    private val hrThresholds = HeartRateZoneThresholds(
        z1Max = 130,
        z2Max = 150,
        z3Max = 165,
        z4Max = 180
    )

    private val pwrThresholds = PowerZoneThresholds(
        z1Max = 150,
        z2Max = 200,
        z3Max = 250,
        z4Max = 300,
        z5Max = 350,
        z6Max = 450
    )

    @Test
    fun testCalculateHeartRateHistogram_binsCorrectlyAt2Bpm() {
        // Samples at 140 bpm for 4 seconds, 142 bpm for 6 seconds
        val samples = listOf(
            ZoneSample(timeActiveSec = 0L, value = 140),
            ZoneSample(timeActiveSec = 4L, value = 142),
            ZoneSample(timeActiveSec = 9L, value = 142) // dt=5s, plus last sample default 1s = 6s
        )

        val result = TelemetryHistogramCalculator.calculateHeartRateHistogram(samples, hrThresholds)
        assertNotNull(result)
        val data = result!!

        assertEquals(2, data.binWidth)
        assertEquals(140, data.dataMin)
        assertEquals(144, data.dataMax)
        assertEquals(2, data.bins.size)
        assertEquals(10L, data.totalActiveTimeSec) // 4s + 5s + 1s = 10s

        val bin0 = data.bins[0]
        assertEquals(140, bin0.rangeMin)
        assertEquals(142, bin0.rangeMax)
        assertEquals(4L, bin0.durationSec)
        assertEquals(2, bin0.zoneIndex) // Midpoint 141 -> Z2 (131..150)
        assertEquals(TTColor.Zone2, bin0.color)

        val bin1 = data.bins[1]
        assertEquals(142, bin1.rangeMin)
        assertEquals(144, bin1.rangeMax)
        assertEquals(6L, bin1.durationSec)
        assertEquals(2, bin1.zoneIndex) // Midpoint 143 -> Z2 (131..150)
        assertEquals(TTColor.Zone2, bin1.color)

        // Percentage sum
        val totalPct = data.bins.sumOf { it.percentage.toDouble() }
        assertEquals(100.0, totalPct, 0.1)
    }

    @Test
    fun testCalculatePowerHistogram_binsCorrectlyAt10W() {
        val samples = listOf(
            ZoneSample(timeActiveSec = 0L, value = 185), // bin 180..190 (Z2)
            ZoneSample(timeActiveSec = 5L, value = 265), // bin 260..270 (Z4)
            ZoneSample(timeActiveSec = 8L, value = 265)  // 3s + 1s = 4s
        )

        val result = TelemetryHistogramCalculator.calculatePowerHistogram(samples, pwrThresholds)
        assertNotNull(result)
        val data = result!!

        assertEquals(10, data.binWidth)
        assertEquals(180, data.dataMin)
        assertEquals(270, data.dataMax)
        assertEquals(9, data.bins.size) // 180..190, 190..200, ... 260..270
        assertEquals(9L, data.totalActiveTimeSec)

        val firstBin = data.bins[0]
        assertEquals(180, firstBin.rangeMin)
        assertEquals(190, firstBin.rangeMax)
        assertEquals(5L, firstBin.durationSec)
        assertEquals(2, firstBin.zoneIndex) // Midpoint 185 -> Z2 (151..200)
        assertEquals(TTColor.Zone2, firstBin.color)

        val lastBin = data.bins.last()
        assertEquals(260, lastBin.rangeMin)
        assertEquals(270, lastBin.rangeMax)
        assertEquals(4L, lastBin.durationSec)
        assertEquals(4, lastBin.zoneIndex) // Midpoint 265 -> Z4 (251..300)
        assertEquals(TTColor.Zone4, lastBin.color)
    }

    @Test
    fun testDurationAccumulation_capsIntervalAt5Seconds() {
        val samples = listOf(
            ZoneSample(timeActiveSec = 0L, value = 150),
            ZoneSample(timeActiveSec = 20L, value = 150), // gap of 20s -> capped at 5s
            ZoneSample(timeActiveSec = 22L, value = 150)  // 2s + 1s = 3s
        )

        val result = TelemetryHistogramCalculator.calculateHeartRateHistogram(samples, hrThresholds)
        assertNotNull(result)
        // total should be 5s + 2s + 1s = 8s
        assertEquals(8L, result!!.totalActiveTimeSec)
    }

    @Test
    fun testDegenerateInputs_emptyOrSingleSample() {
        // Empty
        val emptyResult = TelemetryHistogramCalculator.calculateHeartRateHistogram(emptyList(), hrThresholds)
        assertNull(emptyResult)

        // Non-positive values
        val zeroResult = TelemetryHistogramCalculator.calculateHeartRateHistogram(
            listOf(ZoneSample(0L, 0), ZoneSample(1L, -5)),
            hrThresholds
        )
        assertNull(zeroResult)

        // Single sample
        val singleSampleResult = TelemetryHistogramCalculator.calculateHeartRateHistogram(
            listOf(ZoneSample(10L, 160)),
            hrThresholds
        )
        assertNotNull(singleSampleResult)
        assertEquals(1, singleSampleResult!!.bins.size)
        assertEquals(1L, singleSampleResult.totalActiveTimeSec)
        assertEquals(1L, singleSampleResult.bins[0].durationSec)
        assertEquals(100f, singleSampleResult.bins[0].percentage, 0.01f)
    }

    @Test
    fun testBinZoneColorAssignment_evaluatesBinMidpoint() {
        // Thresholds: Z1 <= 130, Z2 <= 150, Z3 <= 165, Z4 <= 180, Z5 > 180
        val samples = listOf(
            ZoneSample(timeActiveSec = 0L, value = 129), // bin 128..130 -> midpoint 129 <= 130 -> Z1
            ZoneSample(timeActiveSec = 2L, value = 131), // bin 130..132 -> midpoint 131 > 130 -> Z2
            ZoneSample(timeActiveSec = 4L, value = 165), // bin 164..166 -> midpoint 165 <= 165 -> Z3
            ZoneSample(timeActiveSec = 6L, value = 181), // bin 180..182 -> midpoint 181 > 180 -> Z5
            ZoneSample(timeActiveSec = 8L, value = 181)
        )

        val result = TelemetryHistogramCalculator.calculateHeartRateHistogram(samples, hrThresholds)
        assertNotNull(result)
        val data = result!!

        val z1Bin = data.bins.first { it.rangeMin == 128 }
        assertEquals(1, z1Bin.zoneIndex)
        assertEquals(TTColor.Zone1, z1Bin.color)

        val z2Bin = data.bins.first { it.rangeMin == 130 }
        assertEquals(2, z2Bin.zoneIndex)
        assertEquals(TTColor.Zone2, z2Bin.color)

        val z3Bin = data.bins.first { it.rangeMin == 164 }
        assertEquals(3, z3Bin.zoneIndex)
        assertEquals(TTColor.Zone3, z3Bin.color)

        val z5Bin = data.bins.first { it.rangeMin == 180 }
        assertEquals(5, z5Bin.zoneIndex)
        assertEquals(TTColor.Zone5, z5Bin.color)
    }

    @Test
    fun testCalculatePowerHistogram_p99CutoffAndRounding_tst_ui_382_1() {
        // Dataset A: 99% of duration spent <= 380 W, spikes up to 940 W
        val samplesA = mutableListOf<ZoneSample>()
        var t = 0L
        // 180 samples at 200 W (900s)
        repeat(180) {
            samplesA.add(ZoneSample(timeActiveSec = t, value = 200))
            t += 5L
        }
        // 18 samples at 375 W (bin 370..380 -> rangeMax 380 W, 90s -> reaches 990s = 99.0%)
        repeat(18) {
            samplesA.add(ZoneSample(timeActiveSec = t, value = 375))
            t += 5L
        }
        // Spikes at 940 W (10s -> total 1000s)
        samplesA.add(ZoneSample(timeActiveSec = t, value = 940))
        t += 5L
        samplesA.add(ZoneSample(timeActiveSec = t, value = 940))
        t += 5L
        samplesA.add(ZoneSample(timeActiveSec = t, value = 940))

        val dataA = TelemetryHistogramCalculator.calculatePowerHistogram(samplesA, pwrThresholds)
        assertNotNull(dataA)
        assertEquals(400, dataA!!.tailCutoff)

        // Dataset B: 99% of duration spent <= 220 W, spikes up to 800 W (floored at z4Max = 300)
        val samplesB = mutableListOf<ZoneSample>()
        t = 0L
        repeat(180) {
            samplesB.add(ZoneSample(timeActiveSec = t, value = 150))
            t += 5L
        }
        repeat(18) {
            samplesB.add(ZoneSample(timeActiveSec = t, value = 215)) // bin 210..220 -> rangeMax 220
            t += 5L
        }
        samplesB.add(ZoneSample(timeActiveSec = t, value = 800))
        t += 5L
        samplesB.add(ZoneSample(timeActiveSec = t, value = 800))
        t += 5L
        samplesB.add(ZoneSample(timeActiveSec = t, value = 800))

        val dataB = TelemetryHistogramCalculator.calculatePowerHistogram(samplesB, pwrThresholds)
        assertNotNull(dataB)
        assertEquals(300, dataB!!.tailCutoff)

        // Clean 50 W Rounding Check
        fun roundTo50W(cutoff: Int) = ((cutoff + 49) / 50) * 50
        assertEquals(350, roundTo50W(312))
        assertEquals(350, roundTo50W(350))
        assertEquals(400, roundTo50W(351))
    }

    @Test
    fun testCalculatePowerHistogram_gatingAndHeartRateParity_tst_ui_382_2() {
        // Gating 1 Violation: < 100 W in tail (P99 = 300, dataMax = 370 -> tailWidth = 70 < 100)
        val samplesGating1 = mutableListOf<ZoneSample>()
        var t = 0L
        repeat(180) {
            samplesGating1.add(ZoneSample(timeActiveSec = t, value = 200))
            t += 5L
        }
        repeat(18) {
            samplesGating1.add(ZoneSample(timeActiveSec = t, value = 295)) // bin 290..300 -> rangeMax 300
            t += 5L
        }
        samplesGating1.add(ZoneSample(timeActiveSec = t, value = 370))
        t += 5L
        samplesGating1.add(ZoneSample(timeActiveSec = t, value = 370))

        val dataGating1 = TelemetryHistogramCalculator.calculatePowerHistogram(samplesGating1, pwrThresholds)
        assertNotNull(dataGating1)
        assertNull(dataGating1!!.tailCutoff)
        assertNull(dataGating1.sprintTail)

        // Gating 2 Violation: < 25% tail excess (tailCutoff = 400, dataMax = 480 -> 480 / 400 = 1.20 < 1.25)
        val samplesGating2 = mutableListOf<ZoneSample>()
        t = 0L
        repeat(150) {
            samplesGating2.add(ZoneSample(timeActiveSec = t, value = 250))
            t += 5L
        }
        repeat(47) {
            samplesGating2.add(ZoneSample(timeActiveSec = t, value = 375)) // bin 370..380 -> rangeMax 380 -> tailCutoff = 400
            t += 5L
        }
        samplesGating2.add(ZoneSample(timeActiveSec = t, value = 480))
        t += 5L
        samplesGating2.add(ZoneSample(timeActiveSec = t, value = 480))

        val dataGating2 = TelemetryHistogramCalculator.calculatePowerHistogram(samplesGating2, pwrThresholds)
        assertNotNull(dataGating2)
        assertNull(dataGating2!!.tailCutoff)
        assertNull(dataGating2.sprintTail)

        // Heart Rate Parity: Heart Rate telemetry never folds into sprint tail
        val hrSamples = listOf(
            ZoneSample(timeActiveSec = 0L, value = 90),
            ZoneSample(timeActiveSec = 100L, value = 140),
            ZoneSample(timeActiveSec = 200L, value = 180)
        )
        val hrData = TelemetryHistogramCalculator.calculateHeartRateHistogram(hrSamples, hrThresholds)
        assertNotNull(hrData)
        assertNull(hrData!!.tailCutoff)
        assertNull(hrData.sprintTail)
    }

    @Test
    fun testCalculatePowerHistogram_mainAndSprintTailAggregation_tst_ui_382_3() {
        val samples = mutableListOf<ZoneSample>()
        var t = 0L

        // Sample 0 at 5 W (so dataMin = 0 W)
        samples.add(ZoneSample(timeActiveSec = 0L, value = 5))
        t = 5L

        // 679 samples at 200 W (3395s) + 20 samples at 375 W (100s) = 3500s in 0..400 W
        // P99 will fall in bin 370..380 (rangeMax 380 -> rawCutoff 380 -> tailCutoff 400)
        repeat(679) {
            samples.add(ZoneSample(timeActiveSec = t, value = 200))
            t += 5L
        }
        repeat(20) {
            samples.add(ZoneSample(timeActiveSec = t, value = 375))
            t += 5L
        }

        // Tail: 400..940 W for 50s
        // 9 samples at 850 W with dt = 5s (45s)
        repeat(9) {
            samples.add(ZoneSample(timeActiveSec = t, value = 850))
            t += 5L
        }
        // 1 sample at 935 W with dt = 4s
        samples.add(ZoneSample(timeActiveSec = t, value = 935))
        t += 4L
        // 1 last sample at 935 W with default 1s
        samples.add(ZoneSample(timeActiveSec = t, value = 935))

        val result = TelemetryHistogramCalculator.calculatePowerHistogram(samples, pwrThresholds)
        assertNotNull(result)
        val data = result!!

        assertEquals(3550L, data.totalActiveTimeSec)
        assertEquals(400, data.tailCutoff)

        // 1. Inspect Main View Dataset
        // Regular bins span 0..400 W (40 bins) + 1 overflow bin = 41 bins
        assertEquals(41, data.bins.size)
        val overflowBin = data.bins.last()
        assertTrue(overflowBin.isOverflow)
        assertFalse(overflowBin.isReturn)
        assertEquals(40, overflowBin.binIndex)
        assertEquals(400, overflowBin.rangeMin)
        assertEquals(940, overflowBin.rangeMax)
        assertEquals(50L, overflowBin.durationSec)
        assertEquals((50.0 / 3550.0) * 100.0, overflowBin.percentage.toDouble(), 0.1)
        assertEquals(TTColor.Zone5, overflowBin.color)

        // Conservation of duration & percentage in Main View
        val mainDurationSum = data.bins.sumOf { it.durationSec }
        assertEquals(3550L, mainDurationSum)
        val mainPctSum = data.bins.sumOf { it.percentage.toDouble() }
        assertEquals(100.0, mainPctSum, 0.1)

        // 2. Inspect Sprint Tail Dataset
        val sprintTail = data.sprintTail
        assertNotNull(sprintTail)
        assertEquals(400, sprintTail!!.dataMin)
        assertEquals(940, sprintTail.dataMax)

        // Bin 0 is the Return Bin
        val returnBin = sprintTail.bins.first()
        assertTrue(returnBin.isReturn)
        assertFalse(returnBin.isOverflow)
        assertEquals(0, returnBin.binIndex)
        assertEquals(0, returnBin.rangeMin)
        assertEquals(400, returnBin.rangeMax)
        assertEquals(3500L, returnBin.durationSec)
        assertEquals((3500.0 / 3550.0) * 100.0, returnBin.percentage.toDouble(), 0.1)

        // Remaining bins are 10 W sprint bins from 400 to 940 W (54 bins) -> total 55 bins
        assertEquals(55, sprintTail.bins.size)
        val sprintBins = sprintTail.bins.drop(1)
        assertEquals(54, sprintBins.size)
        assertEquals(400, sprintBins.first().rangeMin)
        assertEquals(940, sprintBins.last().rangeMax)

        // Conservation of duration & percentage in Sprint Tail View
        val tailDurationSum = sprintTail.bins.sumOf { it.durationSec }
        assertEquals(3550L, tailDurationSum)
        val tailPctSum = sprintTail.bins.sumOf { it.percentage.toDouble() }
        assertEquals(100.0, tailPctSum, 0.1)
    }
}
