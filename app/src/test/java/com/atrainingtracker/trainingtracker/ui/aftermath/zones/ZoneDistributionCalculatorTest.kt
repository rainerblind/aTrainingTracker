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

import org.junit.Assert.*
import org.junit.Test

class ZoneDistributionCalculatorTest {

    private val thresholds = HeartRateZoneThresholds(
        z1Max = 130,
        z2Max = 150,
        z3Max = 165,
        z4Max = 180
    )

    @Test
    fun testCalculateHeartRateDistribution_thresholdClassification() {
        val samples = listOf(
            ZoneSample(timeActiveSec = 0L, value = 120),  // Z1: <= 130
            ZoneSample(timeActiveSec = 10L, value = 130), // Z1: <= 130
            ZoneSample(timeActiveSec = 20L, value = 131), // Z2: 131..150
            ZoneSample(timeActiveSec = 30L, value = 150), // Z2: 131..150
            ZoneSample(timeActiveSec = 40L, value = 151), // Z3: 151..165
            ZoneSample(timeActiveSec = 50L, value = 165), // Z3: 151..165
            ZoneSample(timeActiveSec = 60L, value = 166), // Z4: 166..180
            ZoneSample(timeActiveSec = 70L, value = 180), // Z4: 166..180
            ZoneSample(timeActiveSec = 80L, value = 181), // Z5: > 180
            ZoneSample(timeActiveSec = 90L, value = 200), // Z5: > 180
            ZoneSample(timeActiveSec = 91L, value = 200)
        )

        val result = ZoneDistributionCalculator.calculateHeartRateDistribution(samples, thresholds)
        assertNotNull(result)
        assertEquals(5, result!!.entries.size)

        // All 5 zones should have duration > 0
        assertTrue("Zone 1 should have duration > 0", result.entries[0].durationSec > 0)
        assertTrue("Zone 2 should have duration > 0", result.entries[1].durationSec > 0)
        assertTrue("Zone 3 should have duration > 0", result.entries[2].durationSec > 0)
        assertTrue("Zone 4 should have duration > 0", result.entries[3].durationSec > 0)
        assertTrue("Zone 5 should have duration > 0", result.entries[4].durationSec > 0)

        // Verify zone indices 1..5
        for (i in 0..4) {
            assertEquals(i + 1, result.entries[i].zoneIndex)
        }
    }

    @Test
    fun testCalculateHeartRateDistribution_durationAccumulationAndClamping() {
        // Sample at 0s (Z2), sample at 600s (Z2 pause gap > 5s), sample at 602s (Z2)
        val samples = listOf(
            ZoneSample(timeActiveSec = 0L, value = 140),
            ZoneSample(timeActiveSec = 600L, value = 140),
            ZoneSample(timeActiveSec = 602L, value = 140)
        )

        val result = ZoneDistributionCalculator.calculateHeartRateDistribution(samples, thresholds)
        assertNotNull(result)

        // Delta between sample 0 and sample 1 is 600s, should be clamped to 5s.
        // Delta between sample 1 and sample 2 is 2s.
        // Last sample gets default 1s.
        // Total active time = 5 + 2 + 1 = 8s
        val z2Entry = result!!.entries[1]
        assertEquals(8L, z2Entry.durationSec)
        assertEquals(8L, result.totalActiveTimeSec)
        assertEquals(100f, z2Entry.percentage, 0.01f)

        // Other zones should have 0s duration and 0%
        for (i in listOf(0, 2, 3, 4)) {
            assertEquals(0L, result.entries[i].durationSec)
            assertEquals(0f, result.entries[i].percentage, 0.01f)
        }
    }

    @Test
    fun testCalculateHeartRateDistribution_zeroDurationOrNullSamples_returnsNull() {
        val emptyResult = ZoneDistributionCalculator.calculateHeartRateDistribution(emptyList(), thresholds)
        assertNull(emptyResult)

        val zeroHrSamples = listOf(
            ZoneSample(timeActiveSec = 0L, value = 0),
            ZoneSample(timeActiveSec = 1L, value = 0)
        )
        val zeroResult = ZoneDistributionCalculator.calculateHeartRateDistribution(zeroHrSamples, thresholds)
        assertNull(zeroResult)
    }

    @Test
    fun testCalculateHeartRateDistribution_percentageSum() {
        val samples = listOf(
            ZoneSample(timeActiveSec = 0L, value = 120), // Z1: 1s
            ZoneSample(timeActiveSec = 1L, value = 140), // Z2: 1s
            ZoneSample(timeActiveSec = 2L, value = 160), // Z3: 1s
            ZoneSample(timeActiveSec = 3L, value = 175), // Z4: 1s
            ZoneSample(timeActiveSec = 4L, value = 190), // Z5: 1s
            ZoneSample(timeActiveSec = 5L, value = 190)  // terminal: 1s
        )

        val result = ZoneDistributionCalculator.calculateHeartRateDistribution(samples, thresholds)
        assertNotNull(result)

        val totalPercentage = result!!.entries.sumOf { it.percentage.toDouble() }
        assertEquals(100.0, totalPercentage, 0.5)
        assertEquals(result.totalActiveTimeSec, result.entries.sumOf { it.durationSec })
    }

    @Test
    fun testCalculateHeartRateDistribution_degenerateIdenticalTimestamps_fallsBackToUnitDuration() {
        // 100 samples all having timeActiveSec = 0L (e.g. historical workout missing TIME_ACTIVE)
        // 50 samples in Z2 (140 bpm), 50 samples in Z3 (160 bpm)
        val samples = (1..50).map { ZoneSample(timeActiveSec = 0L, value = 140) } +
                (1..50).map { ZoneSample(timeActiveSec = 0L, value = 160) }

        val result = ZoneDistributionCalculator.calculateHeartRateDistribution(samples, thresholds)
        assertNotNull(result)
        assertEquals(100L, result!!.totalActiveTimeSec)
        assertEquals(50L, result.entries[1].durationSec) // Z2
        assertEquals(50.0f, result.entries[1].percentage, 0.01f)
        assertEquals(50L, result.entries[2].durationSec) // Z3
        assertEquals(50.0f, result.entries[2].percentage, 0.01f)
        assertEquals(0L, result.entries[0].durationSec)  // Z1
        assertEquals(0L, result.entries[3].durationSec)  // Z4
        assertEquals(0L, result.entries[4].durationSec)  // Z5
    }
}

