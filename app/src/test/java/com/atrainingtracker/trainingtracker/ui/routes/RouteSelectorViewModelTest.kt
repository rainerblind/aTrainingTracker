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

package com.atrainingtracker.trainingtracker.ui.routes

import android.location.Location
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.routes.RouteAutoDetector
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying RouteSelectorViewModel behavior (REQ-MAP-024 / TST-MAP-026 / ATT-1835).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteSelectorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockRepository: RoutesRepository
    private lateinit var autoDetector: RouteAutoDetector
    private lateinit var mockTuningDataStore: TuningPreferencesDataStore
    private val allRoutesFlow = MutableStateFlow<List<RouteWithPath>>(emptyList())
    private val activeNavigatedRouteIdFlow = MutableStateFlow<Long?>(null)
    private val tuningConfigFlow = MutableStateFlow(TuningConfig())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockRepository = mockk(relaxed = true)
        every { mockRepository.allRoutes } returns allRoutesFlow
        every { mockRepository.activeNavigatedRouteId } returns activeNavigatedRouteIdFlow

        mockTuningDataStore = mockk(relaxed = true)
        every { mockTuningDataStore.tuningConfigFlow } returns tuningConfigFlow

        autoDetector = RouteAutoDetector()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleRoutes(): List<RouteWithPath> {
        return listOf(
            RouteWithPath(
                summary = RouteSummary(
                    id = 1L,
                    externalId = "ext_1",
                    name = "Short Trail",
                    description = "Nice short trail",
                    isSelected = false,
                    distance = 2000.0,
                    elevationGain = 20.0,
                    bSportType = BSportType.RUN,
                    source = RouteSource.LOCAL_GPX,
                    syncedAt = 1000L
                ),
                path = listOf(
                    PathPoint(0.0, LatLng(48.137, 11.576), 500.0),
                    PathPoint(100.0, LatLng(48.138, 11.577), 505.0)
                )
            ),
            RouteWithPath(
                summary = RouteSummary(
                    id = 2L,
                    externalId = "ext_2",
                    name = "Long Mountain Pass",
                    description = "Hard mountain pass",
                    isSelected = false,
                    distance = 25000.0,
                    elevationGain = 800.0,
                    bSportType = BSportType.BIKE,
                    source = RouteSource.STRAVA,
                    syncedAt = 5000L
                ),
                path = listOf(
                    PathPoint(0.0, LatLng(48.200, 11.600), 600.0),
                    PathPoint(500.0, LatLng(48.300, 11.700), 750.0)
                )
            )
        )
    }

    @Test
    fun testInitialStateLoadsRoutesAndObservesActiveRoute() = runTest {
        allRoutesFlow.value = sampleRoutes()
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Initial routes should be empty when location is null", 0, state.routes.size)
        assertEquals("Total route count should reflect all imported routes", 2, state.totalRouteCount)
        assertNull(state.activeRoute)
        assertNull(state.autoDetectedCandidate)
    }

    @Test
    fun testSelectRouteDelegatesToRepository() = runTest {
        allRoutesFlow.value = sampleRoutes()
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        viewModel.selectRoute(1L)
        verify { mockRepository.setActiveNavigatedRoute(1L) }
    }

    @Test
    fun testStopRouteClearsActiveNavigation() = runTest {
        allRoutesFlow.value = sampleRoutes()
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        viewModel.stopRoute()
        verify { mockRepository.setActiveNavigatedRoute(null) }
    }

    @Test
    fun testClearRouteClearsActiveNavigation() = runTest {
        allRoutesFlow.value = sampleRoutes()
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        viewModel.clearRoute()
        verify { mockRepository.setActiveNavigatedRoute(null) }
    }

    @Test
    fun testInitialStateHasEmptyFilteredRoutesWhenLocationIsNull() = runTest {
        allRoutesFlow.value = sampleRoutes()
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.routes.size)
        assertEquals(2, state.totalRouteCount)
    }

    @Test
    fun testLocationChangeTriggersProximityFiltering() = runTest {
        allRoutesFlow.value = sampleRoutes()
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        // Athlete near Route 1 start point (48.137, 11.576)
        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns 48.13705
        every { loc.longitude } returns 11.57605
        every { loc.bearing } returns 38f
        every { loc.hasBearing() } returns true
        every { loc.speed } returns 4f

        viewModel.onLocationChanged(loc)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Route 1 is ~7m away (<= default 1.0 km radius). Route 2 is ~7.2 km away (> 1.0 km radius).
        assertEquals(1, state.routes.size)
        assertEquals(1L, state.routes[0].summary.id)
    }

    @Test
    fun testMultipleRoutesInRadiusSortedByRecencyDescending() = runTest {
        val routeOlder = RouteWithPath(
            summary = RouteSummary(
                id = 10L, externalId = "ext_10", name = "Older", description = "", isSelected = false,
                distance = 5000.0, elevationGain = 50.0, bSportType = BSportType.BIKE,
                source = RouteSource.LOCAL_GPX, syncedAt = 1000L
            ),
            path = listOf(PathPoint(0.0, LatLng(48.1372, 11.5762), 500.0))
        )
        val routeNewer = RouteWithPath(
            summary = RouteSummary(
                id = 20L, externalId = "ext_20", name = "Newer", description = "", isSelected = false,
                distance = 6000.0, elevationGain = 60.0, bSportType = BSportType.BIKE,
                source = RouteSource.LOCAL_GPX, syncedAt = 9000L
            ),
            path = listOf(PathPoint(0.0, LatLng(48.1375, 11.5765), 500.0))
        )
        allRoutesFlow.value = listOf(routeOlder, routeNewer)
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns 48.137
        every { loc.longitude } returns 11.576
        viewModel.onLocationChanged(loc)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.routes.size)
        assertEquals("Most recently ridden route (syncedAt 9000L) must rank first", 20L, state.routes[0].summary.id)
        assertEquals(10L, state.routes[1].summary.id)
    }

    @Test
    fun testDynamicRadiusPreferenceChangeReFiltersRoutes() = runTest {
        allRoutesFlow.value = sampleRoutes()
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns 48.13705
        every { loc.longitude } returns 11.57605
        viewModel.onLocationChanged(loc)
        advanceUntilIdle()

        // Default radius 1.0 km -> only Route 1
        assertEquals(1, viewModel.uiState.value.routes.size)

        // Expand radius to 10.0 km
        tuningConfigFlow.value = TuningConfig(routeSelectionRadiusKm = 10.0f)
        advanceUntilIdle()

        // Now both Route 1 and Route 2 (~7.2 km away) qualify!
        val expandedState = viewModel.uiState.value
        assertEquals(2, expandedState.routes.size)
        // Sorted by recency: Route 2 (5000L) > Route 1 (1000L)
        assertEquals(2L, expandedState.routes[0].summary.id)
        assertEquals(1L, expandedState.routes[1].summary.id)
    }

    @Test
    fun testLocationUpdateTriggersAutoDetection() = runTest {
        val routes = sampleRoutes()
        allRoutesFlow.value = routes
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns 48.13705
        every { loc.longitude } returns 11.57605
        every { loc.bearing } returns 38f
        every { loc.hasBearing() } returns true
        every { loc.speed } returns 4f

        viewModel.onLocationChanged(loc)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("Should detect candidate near start", state.autoDetectedCandidate)
        assertEquals(1L, state.autoDetectedCandidate?.summary?.id)
    }

    @Test
    fun testDismissAutoDetectedCandidateClearsState() = runTest {
        val routes = sampleRoutes()
        allRoutesFlow.value = routes
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns 48.13705
        every { loc.longitude } returns 11.57605
        every { loc.bearing } returns 38f
        every { loc.hasBearing() } returns true
        every { loc.speed } returns 4f

        viewModel.onLocationChanged(loc)
        advanceUntilIdle()

        viewModel.dismissCandidate(1L)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.autoDetectedCandidate)
    }

    @Test
    fun testActivateAutoDetectedCandidateSetsRoute() = runTest {
        val routes = sampleRoutes()
        allRoutesFlow.value = routes
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns 48.13705
        every { loc.longitude } returns 11.57605
        every { loc.bearing } returns 38f
        every { loc.hasBearing() } returns true
        every { loc.speed } returns 4f

        viewModel.onLocationChanged(loc)
        advanceUntilIdle()

        viewModel.activateCandidate(1L)
        advanceUntilIdle()

        verify { mockRepository.setActiveNavigatedRoute(1L) }
        assertNull(viewModel.uiState.value.autoDetectedCandidate)
    }

    @Test
    fun testUiStateExposesTotalRouteCountWithoutFilterTabs() = runTest {
        val fiveRoutes = (1..5).map { id ->
            RouteWithPath(
                summary = RouteSummary(
                    id = id.toLong(),
                    externalId = "ext_$id",
                    name = "Route $id",
                    description = "Description $id",
                    isSelected = false,
                    distance = (id * 1000).toDouble(),
                    elevationGain = 50.0,
                    bSportType = BSportType.BIKE,
                    source = RouteSource.LOCAL_GPX
                ),
                path = emptyList()
            )
        }
        allRoutesFlow.value = fiveRoutes
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.totalRouteCount)
        assertEquals(0, state.routes.size)
    }

    @Test
    fun testPreTracking_ranksByStartProximity_andSetsContextEmptyHintRes() = runTest {
        allRoutesFlow.value = sampleRoutes()
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        // By default, isTrackingActive is false
        assertEquals(false, viewModel.uiState.value.isTrackingActive)
        assertEquals(com.atrainingtracker.R.string.route_no_routes_nearby, viewModel.uiState.value.contextEmptyHintRes)

        // Set location near Route 1 start (48.137154, 11.576124)
        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns 48.137154
        every { loc.longitude } returns 11.576124
        viewModel.onLocationChanged(loc)
        advanceUntilIdle()

        // Within 1km, Route 1 qualifies
        val routes = viewModel.uiState.value.routes
        assertEquals(1, routes.size)
        assertEquals(1L, routes[0].summary.id)
    }

    @Test
    fun testInRide_evaluatesMatchingCorridorRoutes_andSetsContextEmptyHintRes() = runTest {
        allRoutesFlow.value = sampleRoutes()
        val viewModel = RouteSelectorViewModel(mockRepository, autoDetector, mockTuningDataStore, SharingStarted.Eagerly)
        advanceUntilIdle()

        // Switch to in-ride
        viewModel.setTrackingActive(true)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isTrackingActive)
        assertEquals(com.atrainingtracker.R.string.route_no_matching_route_detected, viewModel.uiState.value.contextEmptyHintRes)

        // Location along Route 1 segment (48.138000, 11.577000)
        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns 48.138000
        every { loc.longitude } returns 11.577000
        every { loc.bearing } returns 38f
        every { loc.hasBearing() } returns true
        every { loc.speed } returns 5f
        viewModel.onLocationChanged(loc)
        advanceUntilIdle()

        val routes = viewModel.uiState.value.routes
        assertEquals(1, routes.size)
        assertEquals(1L, routes[0].summary.id)
    }
}
