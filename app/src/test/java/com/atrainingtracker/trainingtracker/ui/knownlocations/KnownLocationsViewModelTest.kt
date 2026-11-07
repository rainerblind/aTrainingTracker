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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import android.app.Application
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.google.android.gms.maps.model.LatLng
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for streamlined [KnownLocationsViewModel].
 *
 * Traceability:
 * - TST-UI-117.3: updateLocation atomically persists name, altitude, source=MANUAL_USER, is_locked=1.
 * - TST-UI-131.3: updateLocation with radius propagates radius to repository and dismisses edit dialog.
 * - TST-UI-132.4: Streamlined ViewModel retains list sorting, search filtering, and editing.
 * - TST-DAT-011.3: Reactive starts count synchronization and dynamic sorting by actual workout starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KnownLocationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockRepository: KnownLocationsRepository
    private lateinit var mockClusterRepository: WorkoutClusterRepository
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private val fakeLocationsFlow = MutableStateFlow<List<KnownLocationItem>>(emptyList())
    private val fakeClustersFlow = MutableStateFlow<List<WorkoutCluster>>(emptyList())
    private val fakeWorkoutsFlow = MutableStateFlow<List<WorkoutData>>(emptyList())

    private lateinit var viewModel: KnownLocationsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockClusterRepository = mockk(relaxed = true)
        mockWorkoutRepository = mockk(relaxed = true)

        every { mockRepository.locationsFlow } returns fakeLocationsFlow
        every { mockClusterRepository.allClusters } returns fakeClustersFlow
        every { mockWorkoutRepository.allWorkouts } returns fakeWorkoutsFlow

        viewModel = KnownLocationsViewModel(
            application = mockApplication,
            repository = mockRepository,
            initialIsMetric = true,
            clusterRepository = mockClusterRepository,
            workoutRepository = mockWorkoutRepository,
            defaultDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun testInitialUiState() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.isMetric)
        assertEquals("", state.searchQuery)
        assertEquals(5, state.filterCriteria.minStarts)
        assertTrue(state.isFilterActive)
        assertEquals(1, state.activeFilterCount)
        assertTrue(state.locations.isEmpty())
        assertNull(state.selectedLocationForEdit)
    }

    @Test
    fun testFlowCollection_updatesUiStateLocations() = runTest {
        val testLocations = listOf(
            KnownLocationItem(1L, "Olympiazentrum", 515.0, 200, LatLng(48.175, 11.554), 10, true, ElevationSource.MANUAL_USER),
            KnownLocationItem(2L, "Englischer Garten", 505.0, 200, LatLng(48.155, 11.590), 5, false, ElevationSource.INTERNET_DEM)
        )
        fakeLocationsFlow.value = testLocations
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.locations.size)
        assertEquals(2, state.filteredLocations.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun testSearchFiltering_filtersByNameAndCoordinates() = runTest {
        val testLocations = listOf(
            KnownLocationItem(1L, "Olympiazentrum", 515.0, 200, LatLng(48.175, 11.554), 10, true, ElevationSource.MANUAL_USER),
            KnownLocationItem(2L, "Englischer Garten", 505.0, 200, LatLng(48.155, 11.590), 5, false, ElevationSource.INTERNET_DEM)
        )
        fakeLocationsFlow.value = testLocations
        testDispatcher.scheduler.advanceUntilIdle()

        // Filter by name
        viewModel.setSearchQuery("Olymp")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredLocations.size)
        assertEquals("Olympiazentrum", viewModel.uiState.value.filteredLocations[0].name)

        // Filter by latitude substring
        viewModel.setSearchQuery("48.155")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredLocations.size)
        assertEquals("Englischer Garten", viewModel.uiState.value.filteredLocations[0].name)

        // Clear filter
        viewModel.setSearchQuery("")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredLocations.size)
    }

    /**
     * TST-UI-117.3: updateLocation atomically persists name, altitude, source=MANUAL_USER.
     */
    @Test
    fun testUpdateLocation_callsRepositoryAndDismissesDialog() = runTest {
        viewModel.updateLocation(
            id = 1L,
            name = "Updated Base",
            altitude = 530.0,
            source = ElevationSource.MANUAL_USER
        )
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            mockRepository.updateLocation(1L, "Updated Base", 530.0, ElevationSource.MANUAL_USER)
        }
        assertNull(viewModel.uiState.value.selectedLocationForEdit)
    }

    /**
     * TST-UI-131.3: updateLocation with radius propagates radius to repository and dismisses edit dialog.
     */
    @Test
    fun testUpdateLocation_withRadius_propagatesRadiusAndDismissesDialog() = runTest {
        val sampleItem = KnownLocationItem(1L, "Home Spot", 520.0, 200, LatLng(48.1, 11.5), 5, false, ElevationSource.INTERNET_DEM)
        viewModel.openEditDialog(sampleItem)
        assertNotNull(viewModel.uiState.value.selectedLocationForEdit)

        viewModel.updateLocation(
            id = 1L,
            name = "Home Spot",
            altitude = 520.0,
            radius = 75,
            source = ElevationSource.MANUAL_USER
        )
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            mockRepository.updateLocation(1L, "Home Spot", 520.0, 75, ElevationSource.MANUAL_USER)
        }
        assertNull(viewModel.uiState.value.selectedLocationForEdit)
    }

    @Test
    fun testDeleteLocation_callsRepository() = runTest {
        viewModel.deleteLocation(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            mockRepository.deleteLocation(42L)
        }
    }

    @Test
    fun testEditDialogTransitions() = runTest {
        val item = KnownLocationItem(1L, "Test", 500.0, 200, LatLng(48.0, 11.0), 1, false, ElevationSource.INTERNET_DEM)

        viewModel.openEditDialog(item)
        assertNotNull(viewModel.uiState.value.selectedLocationForEdit)
        assertEquals(1L, viewModel.uiState.value.selectedLocationForEdit?.id)

        viewModel.dismissEditDialog()
        assertNull(viewModel.uiState.value.selectedLocationForEdit)
    }

    /**
     * TST-DAT-011.3: Verifies that startsByLocationId dynamically computes start occurrences
     * by evaluating workout start coordinates against location radius.
     */
    @Test
    fun testStartsByLocationId_computesExactCountsFromWorkoutsFlow() = runTest {
        val loc1 = KnownLocationItem(1L, "Olympiazentrum", 515.0, 200, LatLng(48.175, 11.554), 0, true, ElevationSource.MANUAL_USER)
        val loc2 = KnownLocationItem(2L, "Englischer Garten", 505.0, 200, LatLng(48.155, 11.590), 0, false, ElevationSource.INTERNET_DEM)

        val w1 = mockk<WorkoutData>(relaxed = true) { every { startLatLng } returns LatLng(48.1751, 11.5541) } // Inside loc1 (<20m)
        val w2 = mockk<WorkoutData>(relaxed = true) { every { startLatLng } returns LatLng(48.1752, 11.5542) } // Inside loc1 (<30m)
        val w3 = mockk<WorkoutData>(relaxed = true) { every { startLatLng } returns LatLng(48.1551, 11.5901) } // Inside loc2 (<20m)
        val w4 = mockk<WorkoutData>(relaxed = true) { every { startLatLng } returns LatLng(48.2000, 11.6000) } // Far away
        val w5 = mockk<WorkoutData>(relaxed = true) { every { startLatLng } returns null } // No GPS

        fakeLocationsFlow.value = listOf(loc1, loc2)
        fakeWorkoutsFlow.value = listOf(w1, w2, w3, w4, w5)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.startsByLocationId[1L])
        assertEquals(1, state.startsByLocationId[2L])
        assertNull(state.startsByLocationId[999L])
    }

    /**
     * TST-DAT-011.3: Verifies that applySort with STARTS orders by dynamic startsByLocationId
     * rather than obsolete/bloated hitCount.
     */
    @Test
    fun testSortByStarts_ordersByDynamicStartsCountRatherThanBloatedHitCount() = runTest {
        // Bloated spot has hitCount=999 in DB, but only 1 real workout
        val bloatedSpot = KnownLocationItem(10L, "Bloated Spot", 520.0, 200, LatLng(48.175, 11.554), 999, false, ElevationSource.INTERNET_DEM)
        // Active spot has hitCount=1 in DB, but 5 real workouts
        val activeSpot = KnownLocationItem(20L, "Active Spot", 510.0, 200, LatLng(48.155, 11.590), 1, false, ElevationSource.INTERNET_DEM)

        val workouts = listOf(
            mockk<WorkoutData>(relaxed = true) { every { startLatLng } returns LatLng(48.1751, 11.5541) }, // 1 near bloatedSpot
            mockk<WorkoutData>(relaxed = true) { every { startLatLng } returns LatLng(48.1551, 11.5901) }, // 5 near activeSpot
            mockk<WorkoutData>(relaxed = true) { every { startLatLng } returns LatLng(48.1552, 11.5902) },
            mockk<WorkoutData>(relaxed = true) { every { startLatLng } returns LatLng(48.1553, 11.5903) },
            mockk<WorkoutData>(relaxed = true) { every { startLatLng } returns LatLng(48.1554, 11.5904) },
            mockk<WorkoutData>(relaxed = true) { every { startLatLng } returns LatLng(48.1550, 11.5900) }
        )

        fakeLocationsFlow.value = listOf(bloatedSpot, activeSpot)
        fakeWorkoutsFlow.value = workouts
        viewModel.setSortOrder(KnownLocationSortOrder.STARTS)
        viewModel.setMinStarts(null)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.filteredLocations.size)
        // Active Spot should be first because startsCount is 5 > 1
        assertEquals(20L, state.filteredLocations[0].id)
        assertEquals("Active Spot", state.filteredLocations[0].name)
        assertEquals(10L, state.filteredLocations[1].id)
        assertEquals("Bloated Spot", state.filteredLocations[1].name)
    }

    /**
     * TST-UI-466.1: Verifies that default minStarts=5 filter excludes locations with < 5 starts.
     */
    @Test
    fun testDefaultStartsFilter_excludesLocationsWithLessThanFiveStarts() = runTest {
        val locLow1 = KnownLocationItem(1L, "Vacation Spot", 500.0, 200, LatLng(48.1, 11.1), 1, false, ElevationSource.INTERNET_DEM)
        val locLow4 = KnownLocationItem(2L, "Single Tour", 510.0, 200, LatLng(48.2, 11.2), 4, false, ElevationSource.INTERNET_DEM)
        val locMin5 = KnownLocationItem(3L, "Local Loop", 520.0, 200, LatLng(48.3, 11.3), 5, false, ElevationSource.INTERNET_DEM)
        val locHigh12 = KnownLocationItem(4L, "Home Base", 530.0, 200, LatLng(48.4, 11.4), 12, true, ElevationSource.MANUAL_USER)

        fakeLocationsFlow.value = listOf(locLow1, locLow4, locMin5, locHigh12)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(4, state.locations.size)
        // Default filter minStarts = 5
        assertEquals(5, state.filterCriteria.minStarts)
        assertTrue(state.isFilterActive)
        assertEquals(1, state.activeFilterCount)
        assertEquals(2, state.filteredLocations.size)
        assertEquals(listOf(4L, 3L), state.filteredLocations.map { it.id })
    }

    /**
     * TST-UI-466.1: Verifies that clearing filter sets minStarts = null and reveals all locations.
     */
    @Test
    fun testClearFilter_revealsAllLocations() = runTest {
        val locLow1 = KnownLocationItem(1L, "Vacation Spot", 500.0, 200, LatLng(48.1, 11.1), 1, false, ElevationSource.INTERNET_DEM)
        val locMin5 = KnownLocationItem(2L, "Local Loop", 520.0, 200, LatLng(48.3, 11.3), 5, false, ElevationSource.INTERNET_DEM)

        fakeLocationsFlow.value = listOf(locLow1, locMin5)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.filteredLocations.size)

        // Clear all filters
        viewModel.clearFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        val clearedState = viewModel.uiState.value
        assertNull(clearedState.filterCriteria.minStarts)
        assertEquals("", clearedState.filterCriteria.query)
        assertFalse(clearedState.isFilterActive)
        assertEquals(0, clearedState.activeFilterCount)
        assertEquals(2, clearedState.filteredLocations.size)
    }

    /**
     * TST-UI-466.1: Verifies that setting custom minStarts thresholds (e.g. 3, 10, 25) filters correctly.
     */
    @Test
    fun testSetMinStarts_filtersByCustomThreshold() = runTest {
        val loc1 = KnownLocationItem(1L, "Spot 1", 500.0, 200, LatLng(48.1, 11.1), 1, false, ElevationSource.INTERNET_DEM)
        val loc3 = KnownLocationItem(2L, "Spot 3", 510.0, 200, LatLng(48.2, 11.2), 3, false, ElevationSource.INTERNET_DEM)
        val loc5 = KnownLocationItem(3L, "Spot 5", 520.0, 200, LatLng(48.3, 11.3), 5, false, ElevationSource.INTERNET_DEM)
        val loc10 = KnownLocationItem(4L, "Spot 10", 530.0, 200, LatLng(48.4, 11.4), 10, false, ElevationSource.INTERNET_DEM)
        val loc25 = KnownLocationItem(5L, "Spot 25", 540.0, 200, LatLng(48.5, 11.5), 25, false, ElevationSource.INTERNET_DEM)

        fakeLocationsFlow.value = listOf(loc1, loc3, loc5, loc10, loc25)
        testDispatcher.scheduler.advanceUntilIdle()

        // Threshold >= 3
        viewModel.setMinStarts(3)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.filteredLocations.size)

        // Threshold >= 10
        viewModel.setMinStarts(10)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredLocations.size)
        assertEquals(listOf(5L, 4L), viewModel.uiState.value.filteredLocations.map { it.id })

        // Threshold >= 25
        viewModel.setMinStarts(25)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredLocations.size)
        assertEquals(5L, viewModel.uiState.value.filteredLocations[0].id)
    }

    /**
     * TST-UI-466.1: Verifies multi-dimensional combination of search query and minStarts threshold.
     */
    @Test
    fun testSearchQueryCombinedWithMinStarts() = runTest {
        val locOlympia10 = KnownLocationItem(1L, "Olympiazentrum", 515.0, 200, LatLng(48.175, 11.554), 10, true, ElevationSource.MANUAL_USER)
        val locOlympia3 = KnownLocationItem(2L, "Olympiapark Trail", 515.0, 200, LatLng(48.176, 11.555), 3, false, ElevationSource.INTERNET_DEM)
        val locGarten15 = KnownLocationItem(3L, "Englischer Garten", 505.0, 200, LatLng(48.155, 11.590), 15, false, ElevationSource.INTERNET_DEM)

        fakeLocationsFlow.value = listOf(locOlympia10, locOlympia3, locGarten15)
        testDispatcher.scheduler.advanceUntilIdle()

        // Set criteria: query = "Olymp", minStarts = 5
        viewModel.setFilterCriteria(KnownLocationsFilterCriteria(query = "Olymp", minStarts = 5))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.activeFilterCount)
        assertTrue(state.isFilterActive)
        assertEquals(1, state.filteredLocations.size)
        assertEquals("Olympiazentrum", state.filteredLocations[0].name)
    }

    @Test
    fun testConstructor_supportsSingleApplicationArgumentForAndroidViewModelFactory() {
        val constructor = KnownLocationsViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull(constructor)
    }

    /**
     * TST-SET-095.3: Verifies candidate locations for merge are filtered to <= 2,000m
     * and sorted ascending by raw numeric distance primitive (ATT-3324).
     */
    @Test
    fun testGetCandidateLocationsForMerge_filtersTo2000mAndSortsAscending() = runTest {
        val source = KnownLocationItem(1L, "Home Base", 500.0, 200, LatLng(48.0, 11.0), 10, true, ElevationSource.MANUAL_USER, isHome = true)
        val candidateNear = KnownLocationItem(2L, "Driveway", 500.0, 200, LatLng(48.001, 11.0), 5, false, ElevationSource.AUTO_LEARNED)
        val candidateMid = KnownLocationItem(3L, "Park Entrance", 502.0, 200, LatLng(48.010, 11.0), 8, false, ElevationSource.AUTO_LEARNED)
        val candidateFar = KnownLocationItem(4L, "Distant Peak", 650.0, 200, LatLng(48.030, 11.0), 2, false, ElevationSource.AUTO_LEARNED)

        fakeLocationsFlow.value = listOf(source, candidateNear, candidateMid, candidateFar)
        testDispatcher.scheduler.advanceUntilIdle()

        val candidates = viewModel.getCandidateLocationsForMerge(source)
        assertEquals(2, candidates.size)
        assertFalse(candidates.any { it.first.id == source.id })
        assertFalse(candidates.any { it.first.id == candidateFar.id })
        assertEquals(candidateNear.id, candidates[0].first.id)
        assertEquals(candidateMid.id, candidates[1].first.id)
        assertTrue(candidates[0].second < candidates[1].second)
    }

    /**
     * TST-SET-095.3: Verifies getCandidateLocationsForMerge returns empty list when no locations are within 2,000m.
     */
    @Test
    fun testGetCandidateLocationsForMerge_noNearbyCandidates_returnsEmptyList() = runTest {
        val source = KnownLocationItem(1L, "Isolated Cabin", 1200.0, 200, LatLng(47.5, 11.0), 1, false, ElevationSource.AUTO_LEARNED)
        val farLocation = KnownLocationItem(2L, "Valley Town", 600.0, 200, LatLng(47.7, 11.0), 5, false, ElevationSource.AUTO_LEARNED)

        fakeLocationsFlow.value = listOf(source, farLocation)
        testDispatcher.scheduler.advanceUntilIdle()

        val candidates = viewModel.getCandidateLocationsForMerge(source)
        assertTrue(candidates.isEmpty())
    }

    /**
     * TST-SET-095.5: Verifies mergeLocations calls repository.mergeLocations and refreshes clusters.
     */
    @Test
    fun testMergeLocations_delegatesToRepositoryAndRefreshesClusters() = runTest {
        val source = KnownLocationItem(1L, "Source", 500.0, 200, LatLng(48.0, 11.0), 3, false, ElevationSource.AUTO_LEARNED)
        val target = KnownLocationItem(2L, "Target", 500.0, 200, LatLng(48.001, 11.0), 10, true, ElevationSource.MANUAL_USER)

        viewModel.mergeLocations(source, target)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.mergeLocations(source, target) }
        coVerify { mockClusterRepository.refreshClusters() }
    }
}
