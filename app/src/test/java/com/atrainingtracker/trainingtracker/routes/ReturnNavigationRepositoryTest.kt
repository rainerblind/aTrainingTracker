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

package com.atrainingtracker.trainingtracker.routes

import android.content.Context
import android.location.Location
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.MyLocation
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit test suite verifying strict "Take Me Home" activation decoupling and lifecycle reset invariants
 * in [ReturnNavigationRepository] (REQ-MAP-039, TST-MAP-041, ATT-2938).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnNavigationRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockRoutesRepository: RoutesRepository
    private lateinit var mockBanalRepository: BANALServiceRepository
    private lateinit var mockKnownLocationsManager: KnownLocationsDatabaseManager

    private val fakeActiveRouteIdFlow = MutableStateFlow<Long?>(null)
    private val fakeAllRoutesFlow = MutableStateFlow<List<RouteWithPath>>(emptyList())
    private val fakeCurrentLocationFlow = MutableStateFlow<LatLng?>(null)
    private val fakeCurrentSpeedFlow = MutableStateFlow<Double?>(5.5) // ~20 km/h
    private val fakeSportTypeFlow = MutableStateFlow(BSportType.BIKE)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val startLat = arg<Double>(0)
            val startLng = arg<Double>(1)
            val endLat = arg<Double>(2)
            val endLng = arg<Double>(3)
            val results = arg<FloatArray>(4)

            val dLat = Math.toRadians(endLat - startLat)
            val dLng = Math.toRadians(endLng - startLng)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) *
                    sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val dist = (6371000 * c).toFloat()
            results[0] = dist
        }

        mockContext = mockk(relaxed = true)
        mockRoutesRepository = mockk(relaxed = true)
        mockBanalRepository = mockk(relaxed = true)
        mockKnownLocationsManager = mockk(relaxed = true)

        every { mockRoutesRepository.activeNavigatedRouteId } returns fakeActiveRouteIdFlow
        every { mockRoutesRepository.allRoutes } returns fakeAllRoutesFlow
        every { mockBanalRepository.currentLocation } returns fakeCurrentLocationFlow
        every { mockBanalRepository.currentSpeed } returns fakeCurrentSpeedFlow
        every { mockBanalRepository.bSportType } returns fakeSportTypeFlow

        val homeLocation = MyLocation(
            42L, 48.0, 9.0, "Zu Hause", 300.0, 50, 10, true, ElevationSource.MANUAL_USER, true
        )
        every { mockKnownLocationsManager.allLocations } returns listOf(homeLocation)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Location::class)
        clearAllMocks()
    }

    private fun createRepository(scope: CoroutineScope): ReturnNavigationRepository {
        return ReturnNavigationRepository(
            context = mockContext,
            routesRepository = mockRoutesRepository,
            banalRepository = mockBanalRepository,
            knownLocationsManager = mockKnownLocationsManager,
            scope = scope
        )
    }

    private fun createTestRoute(id: Long, name: String): RouteWithPath {
        // Route starts at 48.05, 9.0 and ends at 48.0001, 9.0001 (~15m from Home at 48.0, 9.0)
        val p1 = PathPoint(0.0, LatLng(48.05, 9.0), 320.0)
        val p2 = PathPoint(5000.0, LatLng(48.02, 9.0), 310.0)
        val p3 = PathPoint(10000.0, LatLng(48.0001, 9.0001), 300.0)
        val summary = RouteSummary(
            id = id,
            externalId = "ext_$id",
            name = name,
            description = "Loop ending near home",
            isSelected = true,
            distance = 10000.0,
            elevationGain = 50.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        return RouteWithPath(summary = summary, path = listOf(p1, p2, p3))
    }

    @Test
    fun testRegularRouteNavigation_doesNotActivateReturnNavigationState() = runTest {
        val repo = createRepository(backgroundScope)
        val route = createTestRoute(1L, "Alpenblick Runde")

        // Given athlete selects an active route terminating near home without tapping Take Me Home
        fakeAllRoutesFlow.value = listOf(route)
        fakeActiveRouteIdFlow.value = 1L
        fakeCurrentLocationFlow.value = LatLng(48.03, 9.0)
        testScheduler.advanceUntilIdle()

        // Then ReturnNavigationState must remain completely inactive (REQ-MAP-039.1)
        val state = repo.navigationState.value
        assertFalse("ReturnNavigationState must be inactive during regular route navigation", state.isActive)
        assertFalse("hasRemainingMetrics must be false when isTakeMeHomeMode is false", state.hasRemainingMetrics)
    }

    @Test
    fun testStartTakeMeHome_activatesReturnMetricsWithHomeDestination() = runTest {
        val repo = createRepository(backgroundScope)
        val route = createTestRoute(1L, "Alpenblick Runde")

        fakeAllRoutesFlow.value = listOf(route)
        fakeActiveRouteIdFlow.value = 1L
        fakeCurrentLocationFlow.value = LatLng(48.02, 9.0)
        testScheduler.advanceUntilIdle()

        // When athlete explicitly activates Take Me Home
        repo.startTakeMeHome()
        testScheduler.advanceUntilIdle()

        // Then ReturnNavigationState becomes active and resolves Home Destination (REQ-MAP-039.1, REQ-MAP-039.2)
        val state = repo.navigationState.value
        assertTrue("ReturnNavigationState must be active after startTakeMeHome()", state.isActive)
        assertTrue("hasRemainingMetrics must be true", state.hasRemainingMetrics)
        assertTrue("isHomeDestination must be true for route ending near home", state.isHomeDestination)
        assertEquals("Zu Hause", state.destinationName)
        assertTrue("Remaining distance must be positive", state.remainingDistanceMeters > 0.0)
    }

    @Test
    fun testStopTakeMeHome_resetsLifecycleStateToInactive() = runTest {
        val repo = createRepository(backgroundScope)
        val route = createTestRoute(1L, "Alpenblick Runde")

        fakeAllRoutesFlow.value = listOf(route)
        fakeActiveRouteIdFlow.value = 1L
        fakeCurrentLocationFlow.value = LatLng(48.02, 9.0)
        repo.startTakeMeHome()
        testScheduler.advanceUntilIdle()
        assertTrue(repo.navigationState.value.isActive)

        // When athlete stops Take Me Home
        repo.stopTakeMeHome()
        testScheduler.advanceUntilIdle()

        // Then state is immediately reset to inactive default (REQ-MAP-039.3)
        val state = repo.navigationState.value
        assertFalse("ReturnNavigationState must be inactive after stopTakeMeHome()", state.isActive)
        assertFalse("hasRemainingMetrics must be false", state.hasRemainingMetrics)
    }

    @Test
    fun testRouteDeselection_resetsReverseReturn() = runTest {
        val repo = createRepository(backgroundScope)
        val route = createTestRoute(1L, "Alpenblick Runde")

        fakeAllRoutesFlow.value = listOf(route)
        fakeActiveRouteIdFlow.value = 1L
        repo.setReverseReturn(true)
        testScheduler.advanceUntilIdle()

        // When route is deselected / cleared
        fakeActiveRouteIdFlow.value = null
        testScheduler.advanceUntilIdle()

        // Then state is inactive and reverse return is reset
        val state = repo.navigationState.value
        assertFalse("ReturnNavigationState must be inactive when route is cleared and not in Take Me Home", state.isActive)
    }
}
