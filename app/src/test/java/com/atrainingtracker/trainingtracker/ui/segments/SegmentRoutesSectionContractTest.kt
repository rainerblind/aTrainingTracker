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

package com.atrainingtracker.trainingtracker.ui.segments

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural contract test for SegmentRoutesSection, SegmentOnMapScreen, and StarredSegmentsScreen
 * (REQ-UI-305, TST-UI-265, ATT-2585).
 */
class SegmentRoutesSectionContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val segmentRoutesSectionFile: File by lazy {
        val file = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentRoutesSection.kt")
        if (file.exists()) file else File("src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentRoutesSection.kt")
    }

    private val segmentOnMapScreenFile: File by lazy {
        val file = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentOnMapScreen.kt")
        if (file.exists()) file else File("src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentOnMapScreen.kt")
    }

    private val starredSegmentsScreenFile: File by lazy {
        val file = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/StarredSegmentsScreen.kt")
        if (file.exists()) file else File("src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/StarredSegmentsScreen.kt")
    }

    @Test
    fun testSegmentRoutesSection_isAccessible() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.segments.SegmentRoutesSectionKt")
        assertNotNull("SegmentRoutesSectionKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val sectionMethod = methods.find { it.name.startsWith("SegmentRoutesSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SegmentRoutesSection composable must exist and be public", sectionMethod)
    }

    @Test
    fun testSegmentRoutesSection_cardStructureAndTokens() {
        assertTrue("SegmentRoutesSection.kt file must exist", segmentRoutesSectionFile.exists())
        val content = segmentRoutesSectionFile.readText()

        assertTrue("Must use ElevatedCard for route cards", content.contains("ElevatedCard"))
        assertTrue("Must enforce RoundedCornerShape(12.dp) card styling", content.contains("RoundedCornerShape(12.dp)"))
        assertTrue("Must use segment_routes_containing_title string resource", content.contains("R.string.segment_routes_containing_title"))
        assertTrue("Must use routes_segment_start_at string resource", content.contains("R.string.routes_segment_start_at"))
        assertTrue("Must support onRouteClick callback", content.contains("onRouteClick"))
    }

    @Test
    fun testSegmentOnMapScreen_integratesCandidateRoutesAndAnalyticsContent() {
        assertTrue("SegmentOnMapScreen.kt file must exist", segmentOnMapScreenFile.exists())
        val content = segmentOnMapScreenFile.readText()

        assertTrue("SegmentOnMapScreen must declare candidateRoutes parameter", content.contains("candidateRoutes: List<RouteWithPath>"))
        assertTrue("SegmentOnMapScreen must declare onRouteClick parameter", content.contains("onRouteClick: ((Long) -> Unit)?"))
        assertTrue("SegmentOnMapScreen must invoke RouteSegmentMatcher.findRoutesContainingSegment", content.contains("RouteSegmentMatcher.findRoutesContainingSegment"))
        assertTrue("SegmentOnMapScreen must pass SegmentRoutesSection into analyticsContent", content.contains("SegmentRoutesSection("))
    }

    @Test
    fun testStarredSegmentsScreen_routeDetailSheetOverlay() {
        assertTrue("StarredSegmentsScreen.kt file must exist", starredSegmentsScreenFile.exists())
        val content = starredSegmentsScreenFile.readText()

        assertTrue("StarredSegmentsScreen must collect allRoutes from ViewModel", content.contains("val allRoutes by viewModel.allRoutes.collectAsStateWithLifecycle()"))
        assertTrue("StarredSegmentsScreen must maintain inspectedRouteId state", content.contains("inspectedRouteId"))
        assertTrue("StarredSegmentsScreen must render RouteDetailSheet on route tap (REQ-UI-316)", content.contains("RouteDetailSheet("))
        assertTrue("StarredSegmentsScreen must forward candidateRoutes to SegmentOnMapScreen", content.contains("candidateRoutes = allRoutes"))
    }
}
