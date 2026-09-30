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

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ZoneDistributionChartTest {

    @Test
    fun testCalculateHeightFraction_scalingAccuracy() {
        val maxDuration = 1200L

        // Max zone reaches 1.0
        assertEquals(1.0f, ZoneDistributionChartMath.calculateHeightFraction(1200L, maxDuration), 0.001f)

        // Half duration reaches 0.5
        assertEquals(0.5f, ZoneDistributionChartMath.calculateHeightFraction(600L, maxDuration), 0.001f)

        // Small non-zero duration
        assertEquals(0.05f, ZoneDistributionChartMath.calculateHeightFraction(60L, maxDuration), 0.001f)

        // Zero duration returns 0.0
        assertEquals(0.0f, ZoneDistributionChartMath.calculateHeightFraction(0L, maxDuration), 0.001f)
    }

    @Test
    fun testCalculateHeightFraction_edgeCases() {
        // Zero max duration returns 0
        assertEquals(0.0f, ZoneDistributionChartMath.calculateHeightFraction(100L, 0L), 0.001f)

        // Negative duration returns 0
        assertEquals(0.0f, ZoneDistributionChartMath.calculateHeightFraction(-50L, 100L), 0.001f)

        // Duration exceeding max clamped to 1.0
        assertEquals(1.0f, ZoneDistributionChartMath.calculateHeightFraction(1500L, 1000L), 0.001f)
    }

    @Test
    fun testFormatZoneDuration_formattingAccuracy() {
        assertEquals("0:00", ZoneDistributionChartMath.formatZoneDuration(0L))
        assertEquals("0:05", ZoneDistributionChartMath.formatZoneDuration(5L))
        assertEquals("0:45", ZoneDistributionChartMath.formatZoneDuration(45L))
        assertEquals("10:00", ZoneDistributionChartMath.formatZoneDuration(600L))
        assertEquals("1:00:00", ZoneDistributionChartMath.formatZoneDuration(3600L))
        assertEquals("1:01:05", ZoneDistributionChartMath.formatZoneDuration(3665L))
        assertEquals("2:30:15", ZoneDistributionChartMath.formatZoneDuration(9015L))
    }

    @Test
    fun testZoneDistributionData_fiveColumnHistogramOrdering() {
        val entries = listOf(
            ZoneTimeEntry(zoneIndex = 1, zoneLabelResId = 1, durationSec = 120L, percentage = 10f, color = Color.Gray),
            ZoneTimeEntry(zoneIndex = 2, zoneLabelResId = 2, durationSec = 600L, percentage = 50f, color = Color.Blue),
            ZoneTimeEntry(zoneIndex = 3, zoneLabelResId = 3, durationSec = 240L, percentage = 20f, color = Color.Green),
            ZoneTimeEntry(zoneIndex = 4, zoneLabelResId = 4, durationSec = 180L, percentage = 15f, color = Color.Yellow),
            ZoneTimeEntry(zoneIndex = 5, zoneLabelResId = 5, durationSec = 60L, percentage = 5f, color = Color.Red)
        )
        val data = ZoneDistributionData(totalActiveTimeSec = 1200L, entries = entries)

        assertEquals(5, data.entries.size)
        assertEquals(1, data.entries[0].zoneIndex)
        assertEquals(5, data.entries[4].zoneIndex)

        val maxDuration = data.entries.maxOf { it.durationSec }
        assertEquals(600L, maxDuration)

        val z2Fraction = ZoneDistributionChartMath.calculateHeightFraction(data.entries[1].durationSec, maxDuration)
        assertEquals(1.0f, z2Fraction, 0.001f)

        val z1Fraction = ZoneDistributionChartMath.calculateHeightFraction(data.entries[0].durationSec, maxDuration)
        assertEquals(0.2f, z1Fraction, 0.001f)
    }
}
