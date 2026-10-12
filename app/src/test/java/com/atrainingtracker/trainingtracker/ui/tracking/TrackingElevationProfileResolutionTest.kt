package com.atrainingtracker.trainingtracker.ui.tracking

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingElevationProfileResolver
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating contextual elevation profile data source resolution,
 * categorized climbs overlay, and orthogonal map-matched progress projection
 * during live tracking (REQ-UI-355 / TST-UI-315 / ATT-3199).
 */
class TrackingElevationProfileResolutionTest {

    private val sampleRecordedPath = listOf(
        PathPoint(distance = 0.0, altitude = 100.0, latLng = LatLng(48.0, 11.0)),
        PathPoint(distance = 500.0, altitude = 110.0, latLng = LatLng(48.005, 11.005)),
        PathPoint(distance = 1000.0, altitude = 125.0, latLng = LatLng(48.010, 11.010))
    )

    private val sampleRouteClimb = Climb(
        id = 1L,
        routeId = 100L,
        name = "Test Climb",
        startLat = 48.002,
        startLng = 11.002,
        endLat = 48.008,
        endLng = 11.008,
        distanceMeters = 600.0,
        elevationGainMeters = 50.0,
        avgGradePercent = 8.3,
        maxGradePercent = 12.0,
        category = ClimbCategory.CAT_3
    )

    private val sampleRoutePath = listOf(
        PathPoint(distance = 0.0, altitude = 100.0, latLng = LatLng(48.0, 11.0)),
        PathPoint(distance = 500.0, altitude = 130.0, latLng = LatLng(48.005, 11.0)),
        PathPoint(distance = 1000.0, altitude = 160.0, latLng = LatLng(48.010, 11.0)),
        PathPoint(distance = 2000.0, altitude = 120.0, latLng = LatLng(48.020, 11.0))
    )

    private fun createMapRoute(
        id: Long = 100L,
        isActiveNavigation: Boolean = true,
        path: List<PathPoint> = sampleRoutePath,
        climbs: List<Climb> = listOf(sampleRouteClimb)
    ): MapRoute {
        return MapRoute(
            id = id,
            name = "Scenic Hill Route",
            isSelected = true,
            bSportType = BSportType.BIKE,
            path = path,
            isActiveNavigation = isActiveNavigation,
            climbs = climbs
        )
    }

    @Test
    fun testResolve_whenActiveRouteWithValidElevation_resolvesRoutePathClimbsAndMapMatchedProgress() {
        val activeRoute = createMapRoute()
        // Midpoint of first segment: LatLng(48.0025, 11.0) -> approx 250m along track
        val currentLocation = LatLng(48.0025, 11.0)

        val result = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = activeRoute,
            recordedPathPoints = sampleRecordedPath,
            currentLocation = currentLocation
        )

        assertTrue("Must resolve as active route elevation", result.isRouteElevation)
        assertEquals("Must use full route path points", activeRoute.path, result.pathPoints)
        assertEquals("Must provide active route climbs", activeRoute.climbs, result.climbs)
        // Along track distance should be roughly 250-280m
        assertTrue("Marker distance must be around 250m-300m", (result.markerDistance ?: 0.0) in 200.0..350.0)
    }

    @Test
    fun testResolve_whenActiveRouteWithZeroElevation_fallsBackToRecordedPath() {
        val flatRoutePath = listOf(
            PathPoint(distance = 0.0, altitude = 0.0, latLng = LatLng(48.0, 11.0)),
            PathPoint(distance = 1000.0, altitude = 0.0, latLng = LatLng(48.01, 11.0))
        )
        val activeRoute = createMapRoute(path = flatRoutePath)

        val result = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = activeRoute,
            recordedPathPoints = sampleRecordedPath,
            currentLocation = LatLng(48.005, 11.0)
        )

        assertFalse("Must fall back to recorded path when route has zero altitude data", result.isRouteElevation)
        assertEquals("Must use recorded path points", sampleRecordedPath, result.pathPoints)
        assertTrue("Climbs must be empty in fallback mode", result.climbs.isEmpty())
        assertEquals("Marker distance must be leading edge of recorded points", 1000.0, result.markerDistance)
    }

    @Test
    fun testResolve_whenActiveRouteHasFewerThanTwoPoints_fallsBackToRecordedPath() {
        val singlePointRoutePath = listOf(
            PathPoint(distance = 0.0, altitude = 150.0, latLng = LatLng(48.0, 11.0))
        )
        val activeRoute = createMapRoute(path = singlePointRoutePath)

        val result = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = activeRoute,
            recordedPathPoints = sampleRecordedPath,
            currentLocation = LatLng(48.0, 11.0)
        )

        assertFalse("Must fall back when route path has fewer than 2 points", result.isRouteElevation)
        assertEquals("Must use recorded path points", sampleRecordedPath, result.pathPoints)
        assertTrue("Climbs must be empty in fallback mode", result.climbs.isEmpty())
        assertEquals(1000.0, result.markerDistance)
    }

    @Test
    fun testResolve_whenNoActiveNavigatedRoute_fallsBackToRecordedPath() {
        // Route is selected but not in active navigation mode
        val unnavigatedRoute = createMapRoute(isActiveNavigation = false)

        val result = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = if (unnavigatedRoute.isActiveNavigation) unnavigatedRoute else null,
            recordedPathPoints = sampleRecordedPath,
            currentLocation = LatLng(48.0, 11.0)
        )

        assertFalse("Must fall back when no active navigation route", result.isRouteElevation)
        assertEquals("Must use recorded path points", sampleRecordedPath, result.pathPoints)
        assertTrue("Climbs must be empty", result.climbs.isEmpty())
        assertEquals(1000.0, result.markerDistance)
    }

    @Test
    fun testResolve_whenCurrentLocationIsNull_defaultsMarkerDistanceToZero() {
        val activeRoute = createMapRoute()

        val result = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = activeRoute,
            recordedPathPoints = sampleRecordedPath,
            currentLocation = null
        )

        assertTrue(result.isRouteElevation)
        assertEquals(activeRoute.path, result.pathPoints)
        assertEquals(0.0, result.markerDistance ?: -1.0, 0.001)
    }

    @Test
    fun testResolve_whenRecordedPointsEmptyAndNoActiveRoute_returnsEmptyAndNullDistance() {
        val result = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = null,
            recordedPathPoints = emptyList(),
            currentLocation = LatLng(48.0, 11.0)
        )

        assertFalse(result.isRouteElevation)
        assertTrue(result.pathPoints.isEmpty())
        assertTrue(result.climbs.isEmpty())
        assertNull(result.markerDistance)
    }
}
