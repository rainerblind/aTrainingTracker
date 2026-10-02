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

class TelemetryHistogramCardTest {

    @Test
    fun testZoneDistributionData_backwardCompatibilityWithoutHistogram() {
        val entry = ZoneTimeEntry(
            zoneIndex = 1,
            zoneLabelResId = 1,
            durationSec = 100L,
            percentage = 100f,
            color = Color.Blue
        )
        // Construction without histogram parameter defaults to null
        val data = ZoneDistributionData(
            totalActiveTimeSec = 100L,
            entries = listOf(entry)
        )
        assertNull(data.histogram)
    }

    @Test
    fun testZoneDistributionData_withHistogram() {
        val bin = TelemetryHistogramBin(
            binIndex = 0,
            rangeMin = 140,
            rangeMax = 142,
            durationSec = 60L,
            percentage = 100f,
            zoneIndex = 2,
            color = Color.Green
        )
        val histogram = TelemetryHistogramData(
            binWidth = 2,
            dataMin = 140,
            dataMax = 142,
            totalActiveTimeSec = 60L,
            bins = listOf(bin)
        )
        val entry = ZoneTimeEntry(
            zoneIndex = 2,
            zoneLabelResId = 2,
            durationSec = 60L,
            percentage = 100f,
            color = Color.Green
        )
        val data = ZoneDistributionData(
            totalActiveTimeSec = 60L,
            entries = listOf(entry),
            histogram = histogram
        )

        val hist = data.histogram
        assertNotNull(hist)
        if (hist != null) {
            assertEquals(2, hist.binWidth)
            assertEquals(1, hist.bins.size)
            assertEquals(140, hist.bins[0].rangeMin)
            assertEquals(142, hist.bins[0].rangeMax)
        }
    }

    @Test
    fun testZoneCardDisplayMode_values() {
        val modes = ZoneCardDisplayMode.values()
        assertEquals(2, modes.size)
        assertTrue(modes.contains(ZoneCardDisplayMode.FIVE_ZONES))
        assertTrue(modes.contains(ZoneCardDisplayMode.HISTOGRAM))
    }

    @Test
    fun testHistogramReadoutFormat_calculations() {
        val bin = TelemetryHistogramBin(
            binIndex = 4,
            rangeMin = 160,
            rangeMax = 162,
            durationSec = 245L,
            percentage = 24.5f,
            zoneIndex = 3,
            color = Color.Yellow
        )

        val durationFormatted = ZoneDistributionChartMath.formatZoneDuration(bin.durationSec)
        assertEquals("4:05", durationFormatted)

        val readout = "${bin.rangeMin}–${bin.rangeMax} bpm • $durationFormatted (${String.format(java.util.Locale.US, "%.1f", bin.percentage)}%) • Z${bin.zoneIndex}"
        assertEquals("160–162 bpm • 4:05 (24.5%) • Z3", readout)
    }
}
