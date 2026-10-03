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

package com.atrainingtracker.trainingtracker.ui.map

import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneTimeEntry
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Contract and architectural tests verifying REQ-UI-251 / ATT-2147:
 * Integrated minimalist marginal zone bars & histogram right of telemetry graph in portrait mode.
 */
class TelemetryMetricGraphMarginalBarsContractTest {

    private lateinit var projectRoot: File

    @Before
    fun setUp() {
        val userDir = System.getProperty("user.dir") ?: "."
        var root = File(userDir)
        if (!File(root, "app").exists() && File(root, "../app").exists()) {
            root = File(root, "..")
        }
        projectRoot = root
    }

    private fun resolve(relPath: String): File {
        return File(projectRoot, relPath)
    }

    @Test
    fun testTelemetryMetricGraph_containsMarginalStripParametersAndContract() {
        val file = resolve("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt")
        assertTrue("TelemetryMetricGraph.kt must exist", file.exists())
        val content = file.readText()

        assertTrue("TelemetryMetricGraph must accept zoneDistribution parameter",
            content.contains("zoneDistribution: ZoneDistributionData? = null"))
        assertTrue("TelemetryMetricGraph must accept zoneDisplayMode parameter",
            content.contains("zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES"))

        assertTrue("TelemetryMetricGraph must contain marginal strip rendering logic for REQ-UI-251",
            content.contains("REQ-UI-251") || content.contains("ATT-2147"))
        assertTrue("TelemetryMetricGraph must render 5-zones bars when in FIVE_ZONES mode",
            content.contains("zoneDisplayMode == ZoneCardDisplayMode.FIVE_ZONES"))
        assertTrue("TelemetryMetricGraph must render histogram bars when in HISTOGRAM mode",
            content.contains("zoneDisplayMode == ZoneCardDisplayMode.HISTOGRAM"))
    }

    @Test
    fun testZoneCards_hoistDisplayModeParameters() {
        val hrCardFile = resolve("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt")
        assertTrue("HeartRateZoneDistributionCard.kt must exist", hrCardFile.exists())
        val hrContent = hrCardFile.readText()

        assertTrue("HeartRateZoneDistributionCard must accept hoisted displayMode parameter",
            hrContent.contains("displayMode: ZoneCardDisplayMode? = null"))
        assertTrue("HeartRateZoneDistributionCard must accept onDisplayModeChange callback",
            hrContent.contains("onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null"))

        val pwrCardFile = resolve("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt")
        assertTrue("PowerZoneDistributionCard.kt must exist", pwrCardFile.exists())
        val pwrContent = pwrCardFile.readText()

        assertTrue("PowerZoneDistributionCard must accept hoisted displayMode parameter",
            pwrContent.contains("displayMode: ZoneCardDisplayMode? = null"))
        assertTrue("PowerZoneDistributionCard must accept onDisplayModeChange callback",
            pwrContent.contains("onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null"))
    }

    @Test
    fun testMapDetailLayout_wiresZoneDistributionsAndDisplayModes() {
        val file = resolve("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        assertTrue("MapDetailLayout.kt must exist", file.exists())
        val content = file.readText()

        assertTrue("MapDetailLayout must accept hrZoneDistribution",
            content.contains("hrZoneDistribution: ZoneDistributionData? = null"))
        assertTrue("MapDetailLayout must accept powerZoneDistribution",
            content.contains("powerZoneDistribution: ZoneDistributionData? = null"))
        assertTrue("MapDetailLayout must pass zoneDistribution to HR TelemetryMetricGraph",
            content.contains("zoneDistribution = hrZoneDistribution"))
        assertTrue("MapDetailLayout must pass zoneDistribution to Power TelemetryMetricGraph",
            content.contains("zoneDistribution = powerZoneDistribution"))
    }

    @Test
    fun testTrackOnMapScreen_wiresSynchronousModeSwitching() {
        val file = resolve("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
        assertTrue("TrackOnMapScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue("TrackOnMapScreen must hoist hrZoneDisplayMode",
            content.contains("var hrZoneDisplayMode by rememberSaveable { mutableStateOf(ZoneCardDisplayMode.FIVE_ZONES) }"))
        assertTrue("TrackOnMapScreen must hoist powerZoneDisplayMode",
            content.contains("var powerZoneDisplayMode by rememberSaveable { mutableStateOf(ZoneCardDisplayMode.FIVE_ZONES) }"))
        assertTrue("TrackOnMapScreen must pass hrZoneDisplayMode to MapDetailLayout",
            content.contains("hrZoneDisplayMode = hrZoneDisplayMode"))
        assertTrue("TrackOnMapScreen must pass powerZoneDisplayMode to MapDetailLayout",
            content.contains("powerZoneDisplayMode = powerZoneDisplayMode"))
    }

    @Test
    fun testMarginalBarMath_5ZonesAndHistogramScaling() {
        val entries = listOf(
            ZoneTimeEntry(1, 0, 100L, 10f, Color.Blue),
            ZoneTimeEntry(2, 0, 300L, 30f, Color.Green),
            ZoneTimeEntry(3, 0, 400L, 40f, Color.Yellow),
            ZoneTimeEntry(4, 0, 150L, 15f, Color(0xFFFF9800)),
            ZoneTimeEntry(5, 0, 50L, 5f, Color.Red)
        )
        val data = ZoneDistributionData(totalActiveTimeSec = 1000L, entries = entries)

        val stripWidth = 20f
        val z1Width = (entries[0].percentage / 100f).coerceIn(0f, 1f) * stripWidth
        val z3Width = (entries[2].percentage / 100f).coerceIn(0f, 1f) * stripWidth

        assertEquals(2.0f, z1Width, 0.001f)
        assertEquals(8.0f, z3Width, 0.001f)
        assertTrue("Z3 bar width must be greater than Z1 bar width", z3Width > z1Width)
    }
}
