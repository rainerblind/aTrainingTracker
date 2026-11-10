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
import java.io.File

class ZoneDistributionCardTest {

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }

    private fun resolveSourceFile(relativePath: String): File {
        val root = findProjectRoot()
        val target = File(root, relativePath)
        assertTrue("Source file must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testZoneDistributionCard_contractAndLayoutArchitecture() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCard.kt")
        val content = file.readText()

        // 1. Header Row Architecture
        assertTrue("Header row comment must exist", content.contains("// Header Row:"))
        assertTrue("Header must format duration via ZoneDistributionChartMath", content.contains("ZoneDistributionChartMath.formatZoneDuration(distribution.totalActiveTimeSec)"))
        assertTrue("Header must render 18.dp icon", content.contains("modifier = Modifier.size(18.dp)"))

        // 2. Body Display Modes
        assertTrue("Body comment must exist", content.contains("// Body:"))
        assertTrue("Must render ZoneDistributionStackedBar with showLegend = false",
            content.contains("ZoneDistributionStackedBar(") && content.contains("showLegend = false"))
        assertTrue("Must render ZoneDistributionColumnChart in COLUMNS mode", content.contains("ZoneDistributionColumnChart("))
        assertTrue("Must render TelemetryHistogramChart in HISTOGRAM mode", content.contains("TelemetryHistogramChart("))
        assertTrue("Must pass parameterized unit to TelemetryHistogramChart", content.contains("unit = unit"))

        // 3. Bottom Mode Switcher
        assertTrue("Bottom Mode Switcher comment must exist", content.contains("// Bottom Mode Switcher:"))
        assertTrue("Must contain SingleChoiceSegmentedButtonRow", content.contains("SingleChoiceSegmentedButtonRow"))
        assertTrue("Switcher row must be 28.dp height", content.contains("height(28.dp)"))
        assertTrue("Must reference zone_mode_stacked_bar", content.contains("R.string.zone_mode_stacked_bar"))
        assertTrue("Must reference zone_mode_columns", content.contains("R.string.zone_mode_columns"))
        assertTrue("Must reference zone_mode_histogram", content.contains("R.string.zone_mode_histogram"))
        assertTrue("Must gate on showModeSelector && distribution.entries.isNotEmpty()",
            content.contains("if (showModeSelector && distribution.entries.isNotEmpty())"))

        // 4. Fallback button count
        assertTrue("Must calculate buttonCount based on histogram presence",
            content.contains("val buttonCount = if (hasHistogram) 3 else 2"))
    }

    @Test
    fun testZoneDistributionCard_wrapperDelegationAndIsolation() {
        val wrappers = listOf(
            Triple("PowerZoneDistributionCard.kt", "R.drawable.ic_power", "\"W\""),
            Triple("HeartRateZoneDistributionCard.kt", "R.drawable.ic_heart_rate", "\"bpm\""),
            Triple("GradeZoneDistributionCard.kt", "R.drawable.ic_grade", "\"%\"")
        )

        for ((fileName, iconRes, unit) in wrappers) {
            val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/$fileName")
            val content = file.readText()

            assertTrue("$fileName must delegate to ZoneDistributionCard", content.contains("ZoneDistributionCard("))
            assertTrue("$fileName must pass iconRes = $iconRes", content.contains(iconRes))
            assertTrue("$fileName must pass unit = $unit", content.contains(unit))
            assertTrue("$fileName must declare showModeSelector: Boolean = true", content.contains("showModeSelector: Boolean = true"))
            assertTrue("$fileName must declare displayMode: ZoneCardDisplayMode? = null", content.contains("displayMode: ZoneCardDisplayMode? = null"))
            assertTrue("$fileName must declare onDisplayModeChange", content.contains("onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null"))
            assertTrue("$fileName must maintain isolated rememberSaveable state", content.contains("rememberSaveable { mutableStateOf(ZoneCardDisplayMode.COLUMNS) }"))
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
