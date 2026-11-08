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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.ui.routes

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural contract test for Route Details polish, summit badge clearance,
 * climb iconography disambiguation, and section header deduplication
 * (REQ-UI-489, TST-UI-489, ATT-5853).
 */
class RouteDetailsContractTest {

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

    private val routeSummaryHeaderFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSummaryHeader.kt")
    }

    private val routeOnMapScreenFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt")
    }

    private val routeSegmentsBreakdownFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSegmentsBreakdownSection.kt")
    }

    private val climbDetectorFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/climbs/ClimbDetector.kt")
    }

    @Test
    fun testElevationProfile_topPaddingAndCanvasHeightCalibration_REQ_UI_489_1() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        assertTrue(
            "ElevationProfile must set topPadding = 24.dp when showZoomControls == true (REQ-UI-489.1)",
            content.contains("val topPadding = if (showZoomControls) 24.dp else 16.dp")
        )

        assertTrue(
            "ElevationProfile must adjust totalCanvasHeight to + 8.dp when showZoomControls == true (REQ-UI-489.1)",
            content.contains("val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 8.dp else cachedData.adaptiveHeight")
        )
    }

    @Test
    fun testRouteSummaryHeader_climbCountIconDisambiguation_REQ_UI_489_2() {
        assertTrue("RouteSummaryHeader.kt must exist", routeSummaryHeaderFile.exists())
        val content = routeSummaryHeaderFile.readText()

        assertTrue(
            "RouteSummaryHeader must use R.drawable.ic_altitude for climb count (REQ-UI-489.2)",
            content.contains("iconRes = R.drawable.ic_altitude,") &&
                    content.contains("value = stringResource(R.string.routes_climb_count, climbs.size)")
        )

        assertTrue(
            "RouteSummaryHeader must continue using R.drawable.ic_ascent for elevation gain",
            content.contains("iconRes = R.drawable.ic_ascent,") &&
                    content.contains("value = formatters.altitude.format_with_units(summary.elevationGain)")
        )
    }

    @Test
    fun testSectionHeaderDeduplication_parameterAndWiring_REQ_UI_489_3() {
        assertTrue("RouteOnMapScreen.kt must exist", routeOnMapScreenFile.exists())
        val mapContent = routeOnMapScreenFile.readText()

        assertTrue("RouteSegmentsBreakdownSection.kt must exist", routeSegmentsBreakdownFile.exists())
        val segContent = routeSegmentsBreakdownFile.readText()

        // 1. Parameter presence in RouteClimbsBreakdownSection
        assertTrue(
            "RouteClimbsBreakdownSection must declare showHeader: Boolean = true (REQ-UI-489.3)",
            mapContent.contains("showHeader: Boolean = true")
        )

        // 2. Parameter presence in RouteSegmentsBreakdownSection
        assertTrue(
            "RouteSegmentsBreakdownSection must declare showHeader: Boolean = true (REQ-UI-489.3)",
            segContent.contains("showHeader: Boolean = true")
        )

        // 3. Conditional header wrapping in both breakdown sections
        assertTrue(
            "RouteClimbsBreakdownSection must wrap header in if (showHeader)",
            mapContent.contains("if (showHeader) {\n            Text(\n                text = stringResource(R.string.routes_climbs_section_title") ||
                    mapContent.contains("if (showHeader) {")
        )

        assertTrue(
            "RouteSegmentsBreakdownSection must wrap header in if (showHeader)",
            segContent.contains("if (showHeader) {\n            Text(\n                text = stringResource(R.string.routes_segments_section_title") ||
                    segContent.contains("if (showHeader) {")
        )

        // 4. Invocation suppresses header under dual filter tabs in RouteOnMapScreen
        assertTrue(
            "RouteOnMapScreen must pass showHeader = !hasDualTabs to both breakdown sections",
            mapContent.contains("showHeader = !hasDualTabs")
        )
    }

    @Test
    fun testClimbDetector_localeAwareGradeFormatting_REQ_UI_489_4() {
        assertTrue("ClimbDetector.kt must exist", climbDetectorFile.exists())
        val content = climbDetectorFile.readText()

        assertTrue(
            "ClimbDetector must use Locale.getDefault() for average grade percentage (REQ-UI-489.4)",
            content.contains("String.format(Locale.getDefault(), \"%.1f%%\", avgGrade)")
        )
    }
}
