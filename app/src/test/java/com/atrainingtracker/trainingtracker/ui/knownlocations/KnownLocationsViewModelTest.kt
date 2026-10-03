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
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private val fakeLocationsFlow = MutableStateFlow<List<KnownLocationItem>>(emptyList())
    private val fakeWorkoutsFlow = MutableStateFlow<List<WorkoutData>>(emptyList())

    private lateinit var viewModel: KnownLocationsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockWorkoutRepository = mockk(relaxed = true)

        every { mockRepository.locationsFlow } returns fakeLocationsFlow
        every { mockWorkoutRepository.allWorkouts } returns fakeWorkoutsFlow

        viewModel = KnownLocationsViewModel(
            application = mockApplication,
            repository = mockRepository,
            initialIsMetric = true,
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
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.filteredLocations.size)
        // Active Spot should be first because startsCount is 5 > 1
        assertEquals(20L, state.filteredLocations[0].id)
        assertEquals("Active Spot", state.filteredLocations[0].name)
        assertEquals(10L, state.filteredLocations[1].id)
        assertEquals("Bloated Spot", state.filteredLocations[1].name)
    }

    @Test
    fun testConstructor_supportsSingleApplicationArgumentForAndroidViewModelFactory() {
        val constructor = KnownLocationsViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull(constructor)
    }
}
