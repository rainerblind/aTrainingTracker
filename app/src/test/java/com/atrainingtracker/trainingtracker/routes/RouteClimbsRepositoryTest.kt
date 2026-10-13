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

package com.atrainingtracker.trainingtracker.routes

import android.content.Context
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManager
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.toMapRoute
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TST-UI-234.1: Unit & Model tests for Route Climbs propagation and repository enrichment (REQ-UI-274).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteClimbsRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockContext = mockk<Context>(relaxed = true) {
        every { applicationContext } returns this
    }
    private val mockRoutesDb = mockk<RoutesDatabaseManager>(relaxed = true)
    private val mockClimbsDb = mockk<ClimbsDatabaseManager>(relaxed = true)

    private fun createSummary(id: Long, name: String = "Test Route") = RouteSummary(
        id = id,
        externalId = "ext_$id",
        name = name,
        description = "Test description",
        distance = 15000.0,
        elevationGain = 350.0,
        bSportType = BSportType.BIKE,
        isSelected = true,
        source = RouteSource.LOCAL_GPX
    )

    private fun createPath(): List<PathPoint> = listOf(
        PathPoint(distance = 0.0, latLng = LatLng(48.0, 9.0), altitude = 400.0),
        PathPoint(distance = 1000.0, latLng = LatLng(48.005, 9.005), altitude = 470.0),
        PathPoint(distance = 2000.0, latLng = LatLng(48.01, 9.01), altitude = 550.0)
    )

    private fun createTestClimb(routeId: Long): Climb = Climb(
        id = 101L,
        name = "Test Ascent",
        routeId = routeId,
        startLat = 48.0,
        startLng = 9.0,
        endLat = 48.01,
        endLng = 9.01,
        distanceMeters = 2000.0,
        elevationGainMeters = 150.0,
        avgGradePercent = 7.5,
        maxGradePercent = 10.0,
        category = ClimbCategory.CAT_3,
        pathPoints = createPath()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getStravaAccessToken() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(TrainingApplication::class)
    }

    @Test
    fun testRouteWithPath_defaultClimbsIsEmpty() {
        val summary = createSummary(1L)
        val route = RouteWithPath(summary = summary, path = createPath())
        assertTrue("Default climbs list should be empty", route.climbs.isEmpty())
    }

    @Test
    fun testToMapRoute_propagatesClimbsToMapRoute() {
        val summary = createSummary(1L)
        val climb = createTestClimb(1L)
        val route = RouteWithPath(summary = summary, path = createPath(), climbs = listOf(climb))

        val mapRoute = route.toMapRoute(isActiveNavigation = true)
        assertEquals(1, mapRoute.climbs.size)
        assertEquals(climb.name, mapRoute.climbs[0].name)
        assertEquals(ClimbCategory.CAT_3, mapRoute.climbs[0].category)
        assertTrue(mapRoute.isActiveNavigation)
    }

    @Test
    fun testRoutesRepository_enrichesRouteFromClimbsDatabase() = runTest {
        val summary = createSummary(42L)
        val path = createPath()
        val rawRoute = RouteWithPath(summary = summary, path = path)
        val storedClimb = createTestClimb(42L)

        every { mockRoutesDb.getAllRoutes() } returns listOf(rawRoute)
        coEvery { mockClimbsDb.getClimbsForRoute(42L) } returns listOf(storedClimb)

        val repository = RoutesRepository(mockContext, mockRoutesDb, mockClimbsDb, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        val enrichedRoutes = repository.allRoutes.value
        assertEquals(1, enrichedRoutes.size)
        assertEquals(1, enrichedRoutes[0].climbs.size)
        assertEquals(storedClimb.id, enrichedRoutes[0].climbs[0].id)
        assertEquals("Test Ascent", enrichedRoutes[0].climbs[0].name)
    }

    @Test
    fun testRoutesRepository_fallbackOnTheFlyDetectionWhenDatabaseReturnsEmpty() = runTest {
        val summary = createSummary(99L)
        // Sustained climb: 2000m length, 150m gain = 7.5% grade (exceeds min 500m, 20m, 3%)
        val climbPath = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.5, 9.5), altitude = 400.0),
            PathPoint(distance = 500.0, latLng = LatLng(48.504, 9.504), altitude = 440.0),
            PathPoint(distance = 1000.0, latLng = LatLng(48.508, 9.508), altitude = 480.0),
            PathPoint(distance = 1500.0, latLng = LatLng(48.512, 9.512), altitude = 520.0),
            PathPoint(distance = 2000.0, latLng = LatLng(48.516, 9.516), altitude = 550.0)
        )
        val rawRoute = RouteWithPath(summary = summary, path = climbPath)

        every { mockRoutesDb.getAllRoutes() } returns listOf(rawRoute)
        // First returns empty (simulating legacy route prior to ATT-1281)
        coEvery { mockClimbsDb.getClimbsForRoute(99L) } returns emptyList()
        coEvery { mockClimbsDb.insertClimbsWithDeduplicationBatch(any()) } returns listOf(1L)

        val repository = RoutesRepository(mockContext, mockRoutesDb, mockClimbsDb, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        val enrichedRoutes = repository.allRoutes.value
        assertEquals(1, enrichedRoutes.size)
        assertTrue("On-the-fly detection should detect the climb", enrichedRoutes[0].climbs.isNotEmpty())
        coVerify(atLeast = 1) { mockClimbsDb.insertClimbsWithDeduplicationBatch(any()) }
    }
}
