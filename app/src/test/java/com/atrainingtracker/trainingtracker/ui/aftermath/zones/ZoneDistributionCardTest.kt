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
import com.atrainingtracker.R
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test verifying ZoneDistributionCard architecture, wrapper delegation, and fallback resolution.
 * Refactored to JVM reflection and behavioral tests per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class ZoneDistributionCardTest {

    @Test
    fun testZoneDistributionCard_contractAndLayoutArchitecture() {
        val genericClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionCardKt")
        val methods = genericClass.declaredMethods.filter { it.name == "ZoneDistributionCard" }
        assertTrue("ZoneDistributionCard composable must exist", methods.isNotEmpty())

        val formattedDuration = ZoneDistributionChartMath.formatZoneDuration(3600L)
        assertEquals("1:00:00", formattedDuration)
    }

    @Test
    fun testZoneDistributionCard_wrapperDelegationAndIsolation() {
        val wrappers = listOf(
            Pair("PowerZoneDistributionCardKt", "PowerZoneDistributionCard"),
            Pair("HeartRateZoneDistributionCardKt", "HeartRateZoneDistributionCard"),
            Pair("GradeZoneDistributionCardKt", "GradeZoneDistributionCard")
        )

        for ((className, methodName) in wrappers) {
            val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.$className")
            val methods = clazz.declaredMethods.filter { it.name == methodName }
            assertTrue("$className must declare $methodName composable", methods.isNotEmpty())
        }
    }

    @Test
    fun testZoneDistributionCard_displayModeFallbackResolution() {
        val entry = ZoneTimeEntry(
            zoneIndex = 1,
            zoneLabelResId = R.string.zone_1_label,
            durationSec = 100L,
            percentage = 100f,
            color = Color.Blue
        )

        val withoutHistogram = ZoneDistributionData(
            totalActiveTimeSec = 100L,
            entries = listOf(entry),
            histogram = null
        )
        assertEquals(
            ZoneCardDisplayMode.COLUMNS,
            ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(withoutHistogram.histogram != null)
        )

        val bin = TelemetryHistogramBin(
            binIndex = 0,
            rangeMin = 100,
            rangeMax = 110,
            durationSec = 100L,
            percentage = 100f,
            zoneIndex = 1,
            color = Color.Blue
        )
        val withHistogram = ZoneDistributionData(
            totalActiveTimeSec = 100L,
            entries = listOf(entry),
            histogram = TelemetryHistogramData(
                binWidth = 10,
                dataMin = 100,
                dataMax = 200,
                totalActiveTimeSec = 100L,
                bins = listOf(bin)
            )
        )
        assertEquals(
            ZoneCardDisplayMode.HISTOGRAM,
            ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(withHistogram.histogram != null)
        )
    }
}
