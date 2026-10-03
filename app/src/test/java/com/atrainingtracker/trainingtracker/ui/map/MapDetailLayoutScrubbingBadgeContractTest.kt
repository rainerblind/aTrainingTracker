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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and architectural contract tests for [MapDetailLayout] and [ElevationProfile]
 * verifying the persistent floating [ScrubbingTelemetryBadge] overlay pinned above scrollable graphs
 * (TST-UI-200.1 / REQ-UI-241 / ATT-2016).
 */
class MapDetailLayoutScrubbingBadgeContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val elevationProfileFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
    }

    private val mapDetailLayoutFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
    }

    @Test
    fun testElevationProfile_declaresShowScrubbingBadgeParameterWithDefaultTrue() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        val count = Regex("""showScrubbingBadge:\s*Boolean\s*=\s*true""").findAll(content).count()
        assertEquals("Both ElevationProfile composable overloads must declare showScrubbingBadge: Boolean = true", 2, count)
    }

    @Test
    fun testElevationProfile_internalBadgeGatedByShowScrubbingBadge() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        assertTrue(
            "ElevationProfile must gate internal ScrubbingTelemetryBadge rendering with showScrubbingBadge",
            content.contains("if (showZoomControls && showScrubbingBadge && currentDistance != null)")
        )
    }

    @Test
    fun testMapDetailLayout_suppressesInternalBadgeInElevationProfile() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must pass showScrubbingBadge = false when invoking ElevationProfile to avoid duplicate badges",
            content.contains("showScrubbingBadge = false")
        )
    }

    @Test
    fun testMapDetailLayout_anchorsScrubbingTelemetryBadgeAtTopEnd() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must invoke ScrubbingTelemetryBadge",
            content.contains("ScrubbingTelemetryBadge(")
        )

        assertTrue(
            "MapDetailLayout must anchor ScrubbingTelemetryBadge at Alignment.TopEnd with 2.dp top and 8.dp end padding (REQ-UI-246 / TST-UI-205.1)",
            content.contains(".align(Alignment.TopEnd)") &&
                    content.contains(".padding(top = 2.dp, end = 8.dp)")
        )

        assertTrue(
            "MapDetailLayout must gate overlay badge with showZoomControls, selectedDistance != null, and activeScrubPoint != null",
            content.contains("if (showZoomControls && selectedDistance != null && activeScrubPoint != null)")
        )
    }

    @Test
    fun testMapDetailLayout_unifiesToolbarAndLowerColumnInSharedBox() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must encapsulate GlobalTelemetryZoomToolbar and lowerColumn in a shared Box with scrubbingOverlay (REQ-UI-246 / TST-UI-205.2)",
            content.contains("GlobalTelemetryZoomToolbar(") &&
                    content.contains("lowerColumn(") &&
                    content.contains("scrubbingOverlay()")
        )

        val countSharedBox = Regex("""Box\(\s*modifier\s*=\s*Modifier\s*\.weight\([^\)]+\)\s*\.fillMaxWidth\(\)\s*\)\s*\{\s*Column\(modifier\s*=\s*Modifier\.fillMaxSize\(\)\)""").findAll(content).count()
        assertTrue(
            "MapDetailLayout must instantiate shared Box container with internal fillMaxSize Column for both split-pane and non-map layouts (found $countSharedBox)",
            countSharedBox >= 2
        )
    }

    @Test
    fun testMapDetailLayout_hoistsScrubbingPointAndAltitudeWithTracklessSupport() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must hoist activeScrubPoint remembering across selectedDistance, activeScrubPath, and isTrackless",
            content.contains("val activeScrubPoint = remember(selectedDistance, activeScrubPath, isTrackless)")
        )

        assertTrue(
            "MapDetailLayout must hoist activeScrubAltitude with interpolation",
            content.contains("val activeScrubAltitude = remember(selectedDistance, activeScrubPath, isTrackless, activeScrubPoint)")
        )
    }

    @Test
    fun testMapDetailLayout_memoizesHeartRateAndPowerThresholds() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must memoize hrThresholds with remember(bSportType, context)",
            content.contains("val hrThresholds = remember(bSportType, context)")
        )

        assertTrue(
            "MapDetailLayout must memoize powerThresholds with remember(context)",
            content.contains("val powerThresholds = remember(context)")
        )

        assertTrue(
            "MapDetailLayout must wire hrThresholds and powerThresholds to ScrubbingTelemetryBadge",
            content.contains("hrZoneThresholds = hrThresholds") &&
                    content.contains("powerZoneThresholds = powerThresholds")
        )
    }
}
