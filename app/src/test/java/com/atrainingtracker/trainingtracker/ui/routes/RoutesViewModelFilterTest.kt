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

import android.app.Application
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Automated unit test suite verifying reactive route filtering and sorting interplay
 * in [RoutesViewModel] (REQ-UI-133, TST-UI-086).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutesViewModelFilterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)

    private val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockSegmentsRepo = mockk<SegmentsRepository>(relaxed = true)

    private val allRoutesFlow = MutableStateFlow<List<RouteWithPath>>(emptyList())
    private val currentLocationFlow = MutableStateFlow<LatLng?>(null)
    private val allSegmentsFlow = MutableStateFlow<List<SegmentWithPath>>(emptyList())
    private val routeFilterCriteriaFlow = MutableStateFlow(RouteFilterCriteria())

    private fun createRoute(
        id: Long,
        name: String,
        source: RouteSource = RouteSource.LOCAL_GPX,
        distance: Double = 25000.0,
        elevationGain: Double = 400.0,
        isSelected: Boolean = false,
        bSportType: BSportType = BSportType.BIKE,
        description: String = "Test description $id"
    ): RouteWithPath {
        val summary = RouteSummary(
            id = id,
            externalId = "ext_$id",
            name = name,
            description = description,
            isSelected = isSelected,
            distance = distance,
            elevationGain = elevationGain,
            bSportType = bSportType,
            source = source
        )
        return RouteWithPath(summary = summary, path = emptyList())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkObject(RoutesRepository.Companion)
        mockkObject(BANALServiceRepository.Companion)
        mockkObject(SegmentsRepository.Companion)
        mockkConstructor(MyPreferenceManager::class)

        every { RoutesRepository.getInstance(any()) } returns mockRoutesRepo
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo
        every { SegmentsRepository.getInstance(any()) } returns mockSegmentsRepo

        every { mockRoutesRepo.allRoutes } returns allRoutesFlow
        every { mockBanalRepo.currentLocation } returns currentLocationFlow
        every { mockSegmentsRepo.allSegmentsWithPath } returns allSegmentsFlow

        every { anyConstructed<MyPreferenceManager>().routeFilterCriteriaFlow } returns routeFilterCriteriaFlow
        coEvery { anyConstructed<MyPreferenceManager>().setRouteFilterCriteria(any()) } answers {
            routeFilterCriteriaFlow.value = firstArg()
        }
        coEvery { anyConstructed<MyPreferenceManager>().clearRouteFilterCriteria() } answers {
            routeFilterCriteriaFlow.value = RouteFilterCriteria()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    @Test
    fun testRoutesFlow_UnfilteredCombinesWithSortOrder() = runTest(testDispatcher) {
        val r1 = createRoute(1L, "Route Zeta", distance = 10000.0)
        val r2 = createRoute(2L, "Route Alpha", distance = 30000.0)
        allRoutesFlow.value = listOf(r1, r2)

        val viewModel = RoutesViewModel(mockApplication)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.routes.collect()
        }
        advanceUntilIdle()

        // Sort by NAME
        viewModel.setSortOrder(RouteSortOrder.NAME)
        advanceUntilIdle()

        val sortedByName = viewModel.routes.value
        assertEquals(2, sortedByName.size)
        assertEquals("Route Alpha", sortedByName[0].summary.name)
        assertEquals("Route Zeta", sortedByName[1].summary.name)

        // Sort by ROUTE_DISTANCE (descending: 30000.0 first, then 10000.0)
        viewModel.setSortOrder(RouteSortOrder.ROUTE_DISTANCE)
        advanceUntilIdle()

        val sortedByDistance = viewModel.routes.value
        assertEquals(2, sortedByDistance.size)
        assertEquals(2L, sortedByDistance[0].summary.id)
        assertEquals(1L, sortedByDistance[1].summary.id)
    }

    @Test
    fun testRoutesFlow_FilteredBySearchQuery() = runTest(testDispatcher) {
        val r1 = createRoute(1L, "Lake Constance Loop", description = "Easy lakeside path")
        val r2 = createRoute(2L, "Feldberg Peak Climb", description = "Steep mountain ascent")
        allRoutesFlow.value = listOf(r1, r2)

        val viewModel = RoutesViewModel(mockApplication)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.routes.collect()
        }
        advanceUntilIdle()

        viewModel.setFilterCriteria(RouteFilterCriteria(query = "peak"))
        advanceUntilIdle()

        val filtered = viewModel.routes.value
        assertEquals(1, filtered.size)
        assertEquals(2L, filtered[0].summary.id)
    }

    @Test
    fun testRoutesFlow_FilteredBySource() = runTest(testDispatcher) {
        val r1 = createRoute(1L, "GPX Route", source = RouteSource.LOCAL_GPX)
        val r2 = createRoute(2L, "Strava Route", source = RouteSource.STRAVA)
        allRoutesFlow.value = listOf(r1, r2)

        val viewModel = RoutesViewModel(mockApplication)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.routes.collect()
        }
        advanceUntilIdle()

        viewModel.setFilterCriteria(RouteFilterCriteria(source = RouteSource.STRAVA))
        advanceUntilIdle()

        val filtered = viewModel.routes.value
        assertEquals(1, filtered.size)
        assertEquals(2L, filtered[0].summary.id)
    }

    @Test
    fun testRoutesFlow_FilteredBySelection() = runTest(testDispatcher) {
        val r1 = createRoute(1L, "Selected Route", isSelected = true)
        val r2 = createRoute(2L, "Unselected Route", isSelected = false)
        allRoutesFlow.value = listOf(r1, r2)

        val viewModel = RoutesViewModel(mockApplication)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.routes.collect()
        }
        advanceUntilIdle()

        viewModel.setFilterCriteria(RouteFilterCriteria(isSelected = true))
        advanceUntilIdle()

        val filtered = viewModel.routes.value
        assertEquals(1, filtered.size)
        assertEquals(1L, filtered[0].summary.id)
    }

    @Test
    fun testRoutesFlow_FilteredByDistanceAndElevation() = runTest(testDispatcher) {
        val r1 = createRoute(1L, "Short Flat", distance = 15000.0, elevationGain = 100.0)
        val r2 = createRoute(2L, "Long Flat", distance = 60000.0, elevationGain = 150.0)
        val r3 = createRoute(3L, "Long Mountain", distance = 70000.0, elevationGain = 1200.0)
        allRoutesFlow.value = listOf(r1, r2, r3)

        val viewModel = RoutesViewModel(mockApplication)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.routes.collect()
        }
        advanceUntilIdle()

        // Filter: min 50km (50,000m) and min 500m elevation
        viewModel.setFilterCriteria(RouteFilterCriteria(minDistanceMeters = 50_000.0, minElevationGainMeters = 500.0))
        advanceUntilIdle()

        val filtered = viewModel.routes.value
        assertEquals(1, filtered.size)
        assertEquals(3L, filtered[0].summary.id)
    }

    @Test
    fun testViewModel_FilterCriteriaLifecycleMethods() = runTest(testDispatcher) {
        val viewModel = RoutesViewModel(mockApplication)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.filterCriteria.collect()
        }
        advanceUntilIdle()

        assertTrue("Initially empty", viewModel.filterCriteria.value.isEmpty)

        // Set filter criteria
        viewModel.setFilterCriteria(RouteFilterCriteria(query = "test", isSelected = true))
        advanceUntilIdle()
        assertEquals("test", viewModel.filterCriteria.value.query)
        assertEquals(true, viewModel.filterCriteria.value.isSelected)
        assertEquals(2, viewModel.filterCriteria.value.activeFilterCount)

        // Update filter criteria
        viewModel.updateFilterCriteria { it.copy(query = "") }
        advanceUntilIdle()
        assertEquals("", viewModel.filterCriteria.value.query)
        assertEquals(true, viewModel.filterCriteria.value.isSelected)
        assertEquals(1, viewModel.filterCriteria.value.activeFilterCount)

        // Clear filter criteria
        viewModel.clearFilterCriteria()
        advanceUntilIdle()
        assertTrue("Cleared criteria should be empty", viewModel.filterCriteria.value.isEmpty)
        assertEquals(0, viewModel.filterCriteria.value.activeFilterCount)
    }
}
