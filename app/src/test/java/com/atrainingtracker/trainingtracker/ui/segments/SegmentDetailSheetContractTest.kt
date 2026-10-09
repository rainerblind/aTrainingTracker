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

package com.atrainingtracker.trainingtracker.ui.segments

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.routes.MatchedRouteSegment
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural and contract tests for [SegmentDetailSheet] and [calculateSegmentBounds]
 * (REQ-UI-303, TST-UI-263, ATT-2774).
 */
class SegmentDetailSheetContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val segmentDetailSheetFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentDetailSheet.kt")
    }

    @Test
    fun testSegmentDetailSheet_composablesExistAndArePublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.segments.SegmentDetailSheetKt")
        assertNotNull("SegmentDetailSheetKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val sheetMethod = methods.find { it.name.startsWith("SegmentDetailSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SegmentDetailSheet composable function must exist and be public", sheetMethod)
    }

    @Test
    fun testSegmentDetailSheet_structuralTokensAndContracts() {
        assertTrue("SegmentDetailSheet.kt must exist", segmentDetailSheetFile.exists())
        val content = segmentDetailSheetFile.readText()

        // 1. Material 3 AppModalBottomSheet usage
        assertTrue("Must use AppModalBottomSheet", content.contains("AppModalBottomSheet"))

        // 2. Header actions: counter, climb category, PR badge
        assertTrue("Must display routes_segment_counter in header", content.contains("routes_segment_counter"))
        assertTrue("Must support ClimbCategoryChip in header", content.contains("ClimbCategoryChip"))
        assertTrue("Must display athlete PR badge in header", content.contains("routes_segment_pr"))

        // 3. Telemetry metrics HUD
        assertTrue("Must render start at metric", content.contains("routes_segment_start_at"))
        assertTrue("Must reuse SegmentDetails", content.contains("SegmentDetails("))

        // 4. Embedded map with EXPLICIT_BOUNDS and StravaOrange MapSegment (REQ-UI-315.2)
        assertTrue("Must use ATrainingTrackerMap", content.contains("ATrainingTrackerMap"))
        assertTrue("Must use MapZoomFocus.EXPLICIT_BOUNDS", content.contains("MapZoomFocus.EXPLICIT_BOUNDS"))
        assertTrue("Must invoke calculateSegmentBounds", content.contains("calculateSegmentBounds(matchedSegment)"))
        assertTrue("Must render MapSegment for authentic StravaOrange polyline (REQ-UI-315.2)", content.contains("MapSegment("))

        // Material 3 16.dp card corners (REQ-UI-315.4)
        assertTrue("Must use 16.dp corner radius for cards (REQ-UI-315.4)", content.contains("RoundedCornerShape(16.dp)"))

        // 5. Isolated zoomed elevation profile with slope grade visualization (REQ-UI-315.3)
        assertTrue("Must render SegmentDetailElevationProfile", content.contains("SegmentDetailElevationProfile("))
        assertTrue("Must render SegmentDetailElevationProfileCard", content.contains("SegmentDetailElevationProfileCard"))
    }

    @Test
    fun testCalculateSegmentBounds_validSegment_computesCorrectBounds() {
        val p1 = PathPoint(0.0, LatLng(48.1351, 11.5820), 500.0)
        val p2 = PathPoint(1000.0, LatLng(48.1400, 11.5900), 580.0)
        val summary = SegmentSummary(
            stravaId = 12345L,
            name = "Test Segment",
            bSportType = BSportType.BIKE,
            climbCategory_raw = 0,
            climbCategory = "",
            prTime_raw = 180,
            prTime = "03:00",
            city = "Munich",
            distance = "1.0 km",
            distance_raw = 1000.0,
            averageGrade_raw = 8.0,
            averageGrade = "8.0%",
            maxGrade = "12.0%",
            elevationGain_raw = 80.0,
            elevationGain = "80 m",
            elevationMin = "500 m",
            elevationMax = "580 m",
            map_polyline = "",
            minLat = 48.1351,
            minLng = 11.5820,
            maxLat = 48.1400,
            maxLng = 11.5900
        )
        val segmentWithPath = SegmentWithPath(summary = summary, path = listOf(p1, p2))
        val matched = MatchedRouteSegment(
            segment = segmentWithPath,
            startDistanceMeters = 5000.0,
            endDistanceMeters = 6000.0,
            startPathIndex = 50,
            endPathIndex = 60
        )

        val bounds = calculateSegmentBounds(matched)
        assertNotNull("Bounds must not be null", bounds)
        assertEquals(48.1351, bounds!!.southwest.latitude, 0.0001)
        assertEquals(11.5820, bounds.southwest.longitude, 0.0001)
        assertEquals(48.1400, bounds.northeast.latitude, 0.0001)
        assertEquals(11.5900, bounds.northeast.longitude, 0.0001)
    }

    @Test
    fun testCalculateSegmentBounds_singlePoint_expandsBounds() {
        val p1 = PathPoint(0.0, LatLng(48.1351, 11.5820), 500.0)
        val summary = SegmentSummary(
            stravaId = 12345L,
            name = "Single Point Segment",
            bSportType = BSportType.BIKE,
            climbCategory_raw = 0,
            climbCategory = "",
            prTime_raw = 0,
            prTime = "",
            city = "Munich",
            distance = "0 m",
            distance_raw = 0.0,
            averageGrade_raw = 0.0,
            averageGrade = "0%",
            maxGrade = "0%",
            elevationGain_raw = 0.0,
            elevationGain = "0 m",
            elevationMin = "500 m",
            elevationMax = "500 m",
            map_polyline = ""
        )
        val segmentWithPath = SegmentWithPath(summary = summary, path = listOf(p1))
        val matched = MatchedRouteSegment(
            segment = segmentWithPath,
            startDistanceMeters = 0.0,
            endDistanceMeters = 0.0,
            startPathIndex = 0,
            endPathIndex = 0
        )

        val bounds = calculateSegmentBounds(matched)
        assertNotNull("Bounds must not be null", bounds)
        assertTrue("Bounds northeast must exceed southwest", bounds!!.northeast.latitude > bounds.southwest.latitude)
        assertTrue("Bounds northeast must exceed southwest", bounds.northeast.longitude > bounds.southwest.longitude)
    }
}
