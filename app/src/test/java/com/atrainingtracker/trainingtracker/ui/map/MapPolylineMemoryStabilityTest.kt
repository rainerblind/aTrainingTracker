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

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropWorkoutPreviewMath
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and architectural contract tests verifying Google Maps polyline memory stability,
 * referential identity (`===`) of coordinate/pattern lists, static layer flow decoupling,
 * keyed layer composition, and bounded preview downsampling (ATT-3690 / REQ-MAP-051 / TST-MAP-053).
 */
class MapPolylineMemoryStabilityTest {

    private fun resolveProjectFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Cannot locate $relativePath from working directory ${File(".").absolutePath}")
    }

    @Test
    fun patternItemLists_areReferentiallyIdenticalSingletons() {
        val samplePath = listOf(
            PathPoint(0.0, LatLng(48.0, 11.0), 500.0),
            PathPoint(100.0, LatLng(48.001, 11.001), 505.0)
        )

        val track1 = MapTrack(id = 1L, type = TrackType.BEST, bSportType = BSportType.BIKE, path = samplePath)
        val track2 = MapTrack(id = 2L, type = TrackType.BEST, bSportType = BSportType.RUN, path = samplePath)
        assertNotNull(track1.pattern)
        assertSame(
            "MapTrack(BEST).pattern must return a referentially identical singleton list",
            track1.pattern,
            track2.pattern
        )

        val gpsTrack = MapTrack(id = 3L, type = TrackType.GPS, bSportType = BSportType.BIKE, path = samplePath)
        assertNull(gpsTrack.pattern)

        val seg1 = MapSegment(stravaId = 10L, name = "Seg 1", bSportType = BSportType.BIKE, path = samplePath, isDashed = true)
        val seg2 = MapSegment(stravaId = 11L, name = "Seg 2", bSportType = BSportType.BIKE, path = samplePath, isDashed = true)
        assertNotNull(seg1.pattern)
        assertSame(
            "MapSegment(isDashed=true).pattern must return a referentially identical singleton list",
            seg1.pattern,
            seg2.pattern
        )

        val route1 = MapRoute(id = 20L, name = "R1", isSelected = true, bSportType = BSportType.BIKE, path = samplePath, isActiveNavigation = false)
        val route2 = MapRoute(id = 21L, name = "R2", isSelected = false, bSportType = BSportType.BIKE, path = samplePath, isActiveNavigation = false)
        assertNotNull(route1.pattern)
        assertSame(
            "MapRoute(isActiveNavigation=false).pattern must return a referentially identical singleton list",
            route1.pattern,
            route2.pattern
        )
    }

    @Test
    fun routeWithPathAndSegmentWithPath_shareReferentiallyIdenticalLatLngsAcrossConversions() {
        val samplePath = listOf(
            PathPoint(0.0, LatLng(48.1, 11.1), 500.0),
            PathPoint(250.0, LatLng(48.2, 11.2), 510.0),
            PathPoint(500.0, LatLng(48.3, 11.3), 520.0)
        )

        val routeSummary = RouteSummary(
            id = 42L,
            externalId = "ext-42",
            name = "Test Route",
            description = "",
            isSelected = true,
            distance = 500.0,
            elevationGain = 20.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        val routeWithPath = RouteWithPath(summary = routeSummary, path = samplePath)

        val mapRouteInactive = routeWithPath.toMapRoute(isActiveNavigation = false)
        val mapRouteActive = routeWithPath.toMapRoute(isActiveNavigation = true)

        assertSame(
            "RouteWithPath.toMapRoute() must share the exact precomputed latLngs list reference",
            routeWithPath.latLngs,
            mapRouteInactive.latLngs
        )
        assertSame(
            "Active and inactive MapRoute conversions from the same RouteWithPath must share identical latLngs",
            mapRouteInactive.latLngs,
            mapRouteActive.latLngs
        )

        val segmentSummary = SegmentSummary(
            stravaId = 99L,
            name = "Climb Segment",
            bSportType = BSportType.BIKE,
            climbCategory_raw = 3,
            climbCategory = "3",
            prTime_raw = 180,
            prTime = "03:00",
            city = "Munich",
            distance = "500 m",
            distance_raw = 500.0,
            averageGrade_raw = 4.0,
            averageGrade = "4.0%",
            maxGrade = "8.0%",
            elevationGain_raw = 20.0,
            elevationGain = "20 m",
            elevationMin = "500 m",
            elevationMax = "520 m",
            map_polyline = ""
        )
        val segmentWithPath = SegmentWithPath(summary = segmentSummary, path = samplePath)
        val mapSegmentSolid = segmentWithPath.toMapSegment(isDashed = false)
        val mapSegmentDashed = segmentWithPath.toMapSegment(isDashed = true)

        assertSame(
            "SegmentWithPath.toMapSegment() must share the exact precomputed latLngs list reference",
            segmentWithPath.latLngs,
            mapSegmentSolid.latLngs
        )
        assertSame(
            "Solid and dashed MapSegment conversions from the same SegmentWithPath must share identical latLngs",
            mapSegmentSolid.latLngs,
            mapSegmentDashed.latLngs
        )
    }

    @Test
    fun cropWorkoutPreviewDownsampling_capsLargePathsAndPreservesExactEndpoints() {
        val largePoints = (0 until 10_000).map { idx ->
            PathPoint(
                distance = idx.toDouble(),
                latLng = LatLng(48.0 + idx * 0.0001, 11.0 + idx * 0.0001),
                altitude = 500.0 + (idx % 50),
                timeSec = idx.toLong()
            )
        }

        val downsampled = CropWorkoutPreviewMath.downsamplePathPoints(largePoints, maxPoints = 500)
        assertEquals(500, downsampled.size)
        assertSame("First point must be preserved identically", largePoints.first(), downsampled.first())
        assertSame("Last point must be preserved identically", largePoints.last(), downsampled.last())

        val smallPoints = largePoints.subList(0, 120)
        val unchanged = CropWorkoutPreviewMath.downsamplePathPoints(smallPoints, maxPoints = 500)
        assertSame("Paths <= maxPoints must be returned without reallocation", smallPoints, unchanged)
    }

    @Test
    fun architecturalContracts_verifyKeyedLayersStableScopeAndDecoupledFlows() {
        val mapContentScopeSource = resolveProjectFile(
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt"
        ).readText()
        val keyOccurrences = Regex("""key\(data\.path\.id\)""").findAll(mapContentScopeSource).count()
        assertTrue(
            "MapContentScope.Render must wrap contextualPaths, segmentData, routeData, and trackData in key(data.path.id) (found $keyOccurrences)",
            keyOccurrences >= 4
        )

        val mapSource = resolveProjectFile(
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt"
        ).readText()
        assertFalse(
            "ATrainingTrackerMap must not include `content` in the remember keys of MapContentScopeImpl",
            mapSource.contains("remember(content, zoomFocus")
        )
        assertTrue(
            "ATrainingTrackerMap must remember MapContentScopeImpl without `content` key",
            mapSource.contains("remember(zoomFocus, primaryColor, context, directionIcons, bSportType)")
        )

        val trackingViewModelSource = resolveProjectFile(
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt"
        ).readText()
        assertTrue(
            "TrackingViewModel must decouple staticMapSegmentsFlow with distinctUntilChanged()",
            trackingViewModelSource.contains("val staticMapSegmentsFlow = liveSegmentsRepository.liveSegments")
        )
        assertTrue(
            "TrackingViewModel must decouple mapRoutesFlow with distinctUntilChanged()",
            trackingViewModelSource.contains("val mapRoutesFlow = combine(")
        )
    }
}
