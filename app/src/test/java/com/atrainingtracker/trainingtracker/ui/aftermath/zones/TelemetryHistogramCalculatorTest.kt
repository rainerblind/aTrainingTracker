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
        z4Max = 300
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
}
