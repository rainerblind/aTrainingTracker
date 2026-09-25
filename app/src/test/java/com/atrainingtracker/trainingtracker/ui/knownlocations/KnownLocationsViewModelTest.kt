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
import com.google.android.gms.maps.model.LatLngBounds
import io.mockk.clearAllMocks
import io.mockk.coEvery
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
 * Unit test suite for [KnownLocationsViewModel].
 *
 * Traceability:
 * - TST-UI-117.3: updateLocation atomically persists name, altitude, source=MANUAL_USER, is_locked=1.
 * - TST-UI-117.7: Viewport culling filters locations intersecting active visibleRegion.latLngBounds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KnownLocationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockRepository: KnownLocationsRepository
    private val fakeLocationsFlow = MutableStateFlow<List<KnownLocationItem>>(emptyList())

    private lateinit var viewModel: KnownLocationsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)

        every { mockRepository.locationsFlow } returns fakeLocationsFlow

        viewModel = KnownLocationsViewModel(
            application = mockApplication,
            repository = mockRepository,
            initialIsMetric = true
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
        assertEquals(KnownLocationsTab.LIST, state.selectedTab)
        assertTrue(state.isMetric)
        assertEquals("", state.searchQuery)
        assertTrue(state.locations.isEmpty())
        assertNull(state.selectedLocationForEdit)
        assertNull(state.selectedLocationForMapPeek)
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
        assertEquals(2, state.visibleMapLocations.size)
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
     * TST-UI-117.7: Viewport culling filters locations intersecting active bounds.
     */
    @Test
    fun testViewportCulling_filtersStrictlyWithinBounds() = runTest {
        val testLocations = listOf(
            // Inside Munich City center bounds (48.13 to 48.15, 11.56 to 11.59)
            KnownLocationItem(1L, "Marienplatz", 520.0, 200, LatLng(48.137, 11.576), 12, true, ElevationSource.MANUAL_USER),
            // Outside bounds (Berlin)
            KnownLocationItem(2L, "Brandenburger Tor", 34.0, 200, LatLng(52.516, 13.377), 1, false, ElevationSource.INTERNET_DEM)
        )
        fakeLocationsFlow.value = testLocations
        testDispatcher.scheduler.advanceUntilIdle()

        val munichBounds = LatLngBounds(
            LatLng(48.12, 11.55), // Southwest
            LatLng(48.16, 11.60)  // Northeast
        )

        viewModel.onViewportBoundsChanged(munichBounds)
        testDispatcher.scheduler.advanceUntilIdle()

        val visible = viewModel.uiState.value.visibleMapLocations
        assertEquals(1, visible.size)
        assertEquals("Marienplatz", visible[0].name)
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

    @Test
    fun testDeleteLocation_callsRepository() = runTest {
        viewModel.deleteLocation(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            mockRepository.deleteLocation(42L)
        }
    }

    @Test
    fun testTabSelection() = runTest {
        viewModel.selectTab(KnownLocationsTab.MAP)
        assertEquals(KnownLocationsTab.MAP, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(KnownLocationsTab.LIST)
        assertEquals(KnownLocationsTab.LIST, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testEditAndPeekDialogTransitions() = runTest {
        val item = KnownLocationItem(1L, "Test", 500.0, 200, LatLng(48.0, 11.0), 1, false, ElevationSource.INTERNET_DEM)

        viewModel.openEditDialog(item)
        assertNotNull(viewModel.uiState.value.selectedLocationForEdit)
        assertEquals(1L, viewModel.uiState.value.selectedLocationForEdit?.id)

        viewModel.dismissEditDialog()
        assertNull(viewModel.uiState.value.selectedLocationForEdit)

        viewModel.openMapPeek(item)
        assertNotNull(viewModel.uiState.value.selectedLocationForMapPeek)
        assertEquals(1L, viewModel.uiState.value.selectedLocationForMapPeek?.id)

        viewModel.dismissMapPeek()
        assertNull(viewModel.uiState.value.selectedLocationForMapPeek)
    }
}
