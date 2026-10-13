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
 * Architectural contract test for RouteSegmentsBreakdownSection and RouteOnMapScreen tab coexistence
 * (REQ-UI-302, TST-UI-262, ATT-2583).
 */
class RouteSegmentsBreakdownContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val routeOnMapScreenFile: File by lazy {
        val file1 = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt")
        if (file1.exists()) file1 else File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt")
    }

    private val routeSegmentsBreakdownFile: File by lazy {
        val file1 = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSegmentsBreakdownSection.kt")
        if (file1.exists()) file1 else File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSegmentsBreakdownSection.kt")
    }

    @Test
    fun testRouteSegmentsBreakdownSection_isAccessible() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteSegmentsBreakdownSectionKt")
        assertNotNull("RouteSegmentsBreakdownSectionKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val sectionMethod = methods.find { it.name.startsWith("RouteSegmentsBreakdownSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteSegmentsBreakdownSection composable must exist and be public", sectionMethod)
    }

    @Test
    fun testRouteSegmentsBreakdownSection_declaresRequiredParametersAndCard() {
        assertTrue("RouteSegmentsBreakdownSection.kt must exist", routeSegmentsBreakdownFile.exists())
        val content = routeSegmentsBreakdownFile.readText()

        assertTrue(
            "RouteSegmentsBreakdownSection must declare segments: List<MatchedRouteSegment>",
            content.contains("segments: List<MatchedRouteSegment>")
        )
        assertTrue(
            "RouteSegmentsBreakdownSection must declare onSegmentClick callback parameter",
            content.contains("onSegmentClick: ((MatchedRouteSegment) -> Unit)? = null")
        )
        assertTrue(
            "RouteSegmentsBreakdownSection must render ElevatedCard for segment breakdown items",
            content.contains("ElevatedCard(")
        )
        assertTrue(
            "RouteSegmentsBreakdownSection must wire onSegmentClick callback",
            content.contains("onClick = { onSegmentClick?.invoke(matched) }")
        )
        assertTrue(
            "RouteSegmentsBreakdownSection must render PR badge when prTime is available",
            content.contains("R.string.routes_segment_pr")
        )
        assertTrue(
            "RouteSegmentsBreakdownSection must render start kilometer using routes_segment_start_at",
            content.contains("R.string.routes_segment_start_at")
        )
    }

    @Test
    fun testRouteOnMapScreen_integratesSegmentsAndTabCoexistence() {
        assertTrue("RouteOnMapScreen.kt must exist", routeOnMapScreenFile.exists())
        val content = routeOnMapScreenFile.readText()

        // 1. Verify allSegments parameter in RouteOnMapScreen
        assertTrue(
            "RouteOnMapScreen must declare allSegments parameter",
            content.contains("allSegments: List<SegmentWithPath> = emptyList()")
        )

        // 2. Verify RouteBreakdownTab enum definition
        assertTrue(
            "RouteOnMapScreen must define RouteBreakdownTab enum for coexistence",
            content.contains("enum class RouteBreakdownTab") &&
                    content.contains("CLIMBS") &&
                    content.contains("SEGMENTS")
        )

        // 3. Verify selectedBreakdownTab state persistence via rememberSaveable
        assertTrue(
            "RouteOnMapScreen must persist selectedBreakdownTab via rememberSaveable",
            content.contains("var selectedBreakdownTab by rememberSaveable { mutableStateOf(RouteBreakdownTab.CLIMBS) }")
        )

        // 4. Verify asynchronous segment matching via produceState
        assertTrue(
            "RouteOnMapScreen must evaluate matchedSegments using produceState",
            content.contains("val matchedSegments by produceState<List<MatchedRouteSegment>>")
        )

        // 5. Verify FilterChip toggle row rendered when both climbs and segments exist
        assertTrue(
            "RouteOnMapScreen must render FilterChip toggle for coexistence",
            content.contains("FilterChip(") &&
                    content.contains("selectedBreakdownTab == RouteBreakdownTab.CLIMBS") &&
                    content.contains("selectedBreakdownTab == RouteBreakdownTab.SEGMENTS")
        )

        // 6. Verify MapDetailLayout receives externalScrubDistance
        assertTrue(
            "RouteOnMapScreen must wire externalScrubDistance to MapDetailLayout",
            content.contains("externalScrubDistance = externalScrubDistance")
        )

        // 7. Verify segments rendered in mapContent
        assertTrue(
            "RouteOnMapScreen must render matched segments in mapContent",
            content.contains("segments(")
        )

        // 8. Verify selectedSegmentForDetail state declaration and SegmentDetailSheet integration (REQ-UI-303, ATT-2774)
        assertTrue(
            "RouteOnMapScreen must declare selectedSegmentForDetail state",
            content.contains("var selectedSegmentForDetail by remember { mutableStateOf<MatchedRouteSegment?>(null) }")
        )
        assertTrue(
            "RouteOnMapScreen must set selectedSegmentForDetail on segment click",
            content.contains("selectedSegmentForDetail = matched")
        )
        assertTrue(
            "RouteOnMapScreen must render SegmentDetailSheet when selectedSegmentForDetail is non-null",
            content.contains("SegmentDetailSheet(") &&
                    content.contains("matchedSegment = matched")
        )
    }
}

