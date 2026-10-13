package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.tracking.ElevationProfileScope
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [TrackingElevationProfileResolver] full route and rolling lookahead
 * window resolution, boundary clamping, 15% athlete positioning, and climb filtering
 * (REQ-UI-355 / TST-UI-315 / ATT-3317).
 */
class TrackingElevationProfileResolutionTest {

    private fun createTestRoute(totalLengthMeters: Double = 20000.0, stepMeters: Double = 100.0): MapRoute {
        val points = mutableListOf<PathPoint>()
        var dist = 0.0
        var time = 0L
        while (dist <= totalLengthMeters) {
            val alt = 100.0 + 100.0 * Math.sin(dist / 1000.0)
            val lat = 48.0 + (dist * 0.00001)
            val lng = 11.0
            points.add(PathPoint(distance = dist, latLng = LatLng(lat, lng), altitude = alt, timeSec = time))
            dist += stepMeters
            time += 10L
        }

        val climb1Pts = points.filter { it.distance in 2000.0..4000.0 }
        val climb2Pts = points.filter { it.distance in 9800.0..11200.0 }
        val climb3Pts = points.filter { it.distance in 15000.0..18000.0 }

        val climbs = listOf(
            Climb(
                id = 1L,
                name = "Climb 1",
                startLat = climb1Pts.first().latLng.latitude,
                startLng = climb1Pts.first().latLng.longitude,
                endLat = climb1Pts.last().latLng.latitude,
                endLng = climb1Pts.last().latLng.longitude,
                distanceMeters = 2000.0,
                elevationGainMeters = 100.0,
                avgGradePercent = 5.0,
                maxGradePercent = 8.0,
                category = ClimbCategory.CAT_4,
                pathPoints = climb1Pts
            ),
            Climb(
                id = 2L,
                name = "Climb 2",
                startLat = climb2Pts.first().latLng.latitude,
                startLng = climb2Pts.first().latLng.longitude,
                endLat = climb2Pts.last().latLng.latitude,
                endLng = climb2Pts.last().latLng.longitude,
                distanceMeters = 1400.0,
                elevationGainMeters = 150.0,
                avgGradePercent = 6.0,
                maxGradePercent = 10.0,
                category = ClimbCategory.CAT_3,
                pathPoints = climb2Pts
            ),
            Climb(
                id = 3L,
                name = "Climb 3",
                startLat = climb3Pts.first().latLng.latitude,
                startLng = climb3Pts.first().latLng.longitude,
                endLat = climb3Pts.last().latLng.latitude,
                endLng = climb3Pts.last().latLng.longitude,
                distanceMeters = 3000.0,
                elevationGainMeters = 250.0,
                avgGradePercent = 7.0,
                maxGradePercent = 12.0,
                category = ClimbCategory.CAT_2,
                pathPoints = climb3Pts
            )
        )

        return MapRoute(
            id = 42L,
            name = "Test Route",
            isSelected = true,
            bSportType = com.atrainingtracker.banalservice.BSportType.BIKE,
            path = points,
            climbs = climbs,
            isActiveNavigation = true
        )
    }

    @Test
    fun testResolve_fullRouteScope_returnsEntireRoute() {
        val route = createTestRoute(20000.0)
        // Athlete at km 10.0 (lat 48.1, lng 11.0)
        val location = LatLng(48.0 + (10000.0 * 0.00001), 11.0)

        val resolved = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = route,
            recordedPathPoints = emptyList(),
            currentLocation = location,
            scope = ElevationProfileScope.FULL_ROUTE,
            lookaheadDistanceMeters = 2000
        )

        assertTrue(resolved.isRouteElevation)
        assertEquals(route.path.size, resolved.pathPoints.size)
        assertEquals(0.0, resolved.pathPoints.first().distance, 0.001)
        assertEquals(20000.0, resolved.pathPoints.last().distance, 0.001)
        assertEquals(3, resolved.climbs.size)
        assertEquals(10000.0, resolved.markerDistance!!, 50.0)
    }

    @Test
    fun testResolve_rollingWindowScope_2000m_centersAthleteAt15Percent() {
        val route = createTestRoute(20000.0)
        val targetDist = 10000.0
        val location = LatLng(48.0 + (targetDist * 0.00001), 11.0)
        val lookahead = 2000

        val resolved = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = route,
            recordedPathPoints = emptyList(),
            currentLocation = location,
            scope = ElevationProfileScope.ROLLING_WINDOW,
            lookaheadDistanceMeters = lookahead
        )

        assertTrue(resolved.isRouteElevation)
        val points = resolved.pathPoints
        assertTrue("Must contain sliced points", points.size >= 2)

        val wStart = points.first().distance
        val wEnd = points.last().distance
        val wSpan = wEnd - wStart

        // Expected D_forward = 2000.0, D_behind = 2000.0 * 3/17 ≈ 352.941
        val expectedBehind = (3.0 / 17.0) * 2000.0
        val expectedSpan = expectedBehind + 2000.0 // ≈ 2352.941

        assertEquals("Window start distance must match alongTrack - D_behind", targetDist - expectedBehind, wStart, 1.0)
        assertEquals("Window end distance must match alongTrack + D_forward", targetDist + 2000.0, wEnd, 1.0)
        assertEquals("Total window span must equal D_behind + D_forward", expectedSpan, wSpan, 1.0)

        // Verify athlete centering ratio: (alongTrack - wStart) / wSpan == 0.1500 (15%)
        val actualRatio = (targetDist - wStart) / wSpan
        assertEquals("Athlete marker must be positioned at 15% from left edge", 0.15, actualRatio, 0.005)
    }

    @Test
    fun testResolve_rollingWindowScope_presets() {
        val route = createTestRoute(50000.0)
        val targetDist = 25000.0
        val location = LatLng(48.0 + (targetDist * 0.00001), 11.0)

        val presets = listOf(500, 1000, 2000, 5000, 10000)
        for (preset in presets) {
            val resolved = TrackingElevationProfileResolver.resolve(
                activeNavigatedRoute = route,
                recordedPathPoints = emptyList(),
                currentLocation = location,
                scope = ElevationProfileScope.ROLLING_WINDOW,
                lookaheadDistanceMeters = preset
            )

            val wStart = resolved.pathPoints.first().distance
            val wEnd = resolved.pathPoints.last().distance
            val wSpan = wEnd - wStart
            val ratio = (targetDist - wStart) / wSpan

            assertEquals("Preset $preset must center athlete at 15%", 0.15, ratio, 0.005)
        }
    }

    @Test
    fun testResolve_rollingWindowScope_startBoundaryClamping() {
        val route = createTestRoute(20000.0)
        // Athlete near start at 100m
        val targetDist = 100.0
        val location = LatLng(48.0 + (targetDist * 0.00001), 11.0)
        val lookahead = 2000

        val resolved = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = route,
            recordedPathPoints = emptyList(),
            currentLocation = location,
            scope = ElevationProfileScope.ROLLING_WINDOW,
            lookaheadDistanceMeters = lookahead
        )

        val wStart = resolved.pathPoints.first().distance
        val wEnd = resolved.pathPoints.last().distance

        val expectedSpan = (20.0 / 17.0) * 2000.0 // ≈ 2352.941
        assertEquals("Window start must be clamped to 0.0m", 0.0, wStart, 0.001)
        assertEquals("Window end must extend to full window span", expectedSpan, wEnd, 1.0)

        // Marker advances naturally from 0% towards 15%
        val ratio = (targetDist - wStart) / (wEnd - wStart)
        assertTrue("Marker ratio near start should be less than 15%", ratio < 0.15)
        assertTrue("Marker ratio must be positive", ratio > 0.0)
    }

    @Test
    fun testResolve_rollingWindowScope_finishBoundaryClamping() {
        val route = createTestRoute(20000.0)
        // Athlete near finish at 19500m on 20000m route
        val targetDist = 19500.0
        val location = LatLng(48.0 + (targetDist * 0.00001), 11.0)
        val lookahead = 2000

        val resolved = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = route,
            recordedPathPoints = emptyList(),
            currentLocation = location,
            scope = ElevationProfileScope.ROLLING_WINDOW,
            lookaheadDistanceMeters = lookahead
        )

        val wStart = resolved.pathPoints.first().distance
        val wEnd = resolved.pathPoints.last().distance

        val expectedSpan = (20.0 / 17.0) * 2000.0 // ≈ 2352.941
        assertEquals("Window end must be clamped to route finish (20000m)", 20000.0, wEnd, 0.001)
        assertEquals("Window start must be clamped to routeLength - windowSpan", 20000.0 - expectedSpan, wStart, 1.0)

        // Marker advances towards 100% as finish line is crossed
        val ratio = (targetDist - wStart) / (wEnd - wStart)
        assertTrue("Marker ratio near finish should exceed 15%", ratio > 0.15)
    }

    @Test
    fun testResolve_rollingWindowScope_climbsFiltering() {
        val route = createTestRoute(20000.0)
        // Athlete at km 10.0 (window: [9647m .. 12000m])
        // Climb 1 is at 2000..4000m (outside)
        // Climb 2 is at 9800..11200m (inside window!)
        // Climb 3 is at 15000..18000m (outside)
        val location = LatLng(48.0 + (10000.0 * 0.00001), 11.0)

        val resolved = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = route,
            recordedPathPoints = emptyList(),
            currentLocation = location,
            scope = ElevationProfileScope.ROLLING_WINDOW,
            lookaheadDistanceMeters = 2000
        )

        assertEquals("Must filter climbs to only those intersecting visible window", 1, resolved.climbs.size)
        assertEquals("Climb 2", resolved.climbs.first().name)
    }

    @Test
    fun testResolve_fallbackWhenNoRoute() {
        val recordedPoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 100.0, timeSec = 0L),
            PathPoint(distance = 500.0, latLng = LatLng(48.01, 11.01), altitude = 110.0, timeSec = 100L)
        )

        val resolved = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = null,
            recordedPathPoints = recordedPoints,
            currentLocation = LatLng(48.01, 11.01),
            scope = ElevationProfileScope.ROLLING_WINDOW,
            lookaheadDistanceMeters = 2000
        )

        assertFalse(resolved.isRouteElevation)
        assertEquals(recordedPoints.size, resolved.pathPoints.size)
        assertTrue(resolved.climbs.isEmpty())
        assertEquals(500.0, resolved.markerDistance)
    }

    @Test
    fun testSlicePath_interpolatesExactBoundaryAltitudes() {
        val path = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 100.0, timeSec = 0L),
            PathPoint(distance = 1000.0, latLng = LatLng(48.1, 11.1), altitude = 200.0, timeSec = 100L)
        )

        val sliced = TrackingElevationProfileResolver.slicePath(path, 250.0, 750.0)
        assertEquals(2, sliced.size)

        // At 250m: ratio 0.25 -> alt 125m
        assertEquals(250.0, sliced.first().distance, 0.001)
        assertEquals(125.0, sliced.first().altitude, 0.001)

        // At 750m: ratio 0.75 -> alt 175m
        assertEquals(750.0, sliced.last().distance, 0.001)
        assertEquals(175.0, sliced.last().altitude, 0.001)
    }
}
