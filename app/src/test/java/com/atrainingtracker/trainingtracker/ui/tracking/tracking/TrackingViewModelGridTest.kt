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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying TrackingViewModel's Pick & Place move state management,
 * swap and move repository dispatching, and screen-mode boundary invariants (REQ-UI-200, TST-UI-154.3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelGridTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockTrackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockLiveSegmentsRepo = mockk<LiveSegmentsRepository>(relaxed = true)
    private val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
    private val mockPrefs = mockk<SharedPreferences>(relaxed = true)

    private val liveSegmentsFlow = MutableStateFlow(emptyList<LiveSegment>())
    private val bSportTypeFlow = MutableStateFlow(BSportType.RUN)

    private fun sampleField(id: Long, row: Int, col: Int): SensorFieldState {
        return SensorFieldState(
            configHash = id.toInt(),
            sensorFieldId = id,
            rowNr = row,
            colNr = col,
            viewSize = ViewSize.NORMAL,
            label = "Metric $id",
            filterDescription = "",
            value = "100",
            units = "bpm",
            zoneColor = Color.Transparent
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

        mockkStatic(ContextCompat::class)
        every { ContextCompat.getColor(any(), any()) } returns 0xFF00FF

        mockkConstructor(SettingsDataStore::class)
        every { anyConstructed<SettingsDataStore>().getZoneDisplayOptionsFlow(any()) } returns flowOf(ZoneDisplayOptions())

        every { mockApplication.applicationContext } returns mockContext
        every { mockLiveSegmentsRepo.liveSegments } returns liveSegmentsFlow
        every { mockBanalRepo.bSportType } returns bSportTypeFlow
        every { mockBanalRepo.activityType } returns MutableStateFlow(ActivityType.getDefaultActivityType())
        every { mockBanalRepo.currentTrack } returns MutableStateFlow(emptyList<LatLng>())
        every { mockBanalRepo.currentSpeed } returns MutableStateFlow<Double?>(null)
        every { mockBanalRepo.currentBearing } returns MutableStateFlow<Double?>(null)
        every { mockBanalRepo.allFilteredSensorData } returns MutableStateFlow(emptyList<FilteredSensorData<*>>())
        every { mockBanalRepo.currentPathPoints } returns MutableStateFlow(emptyList<PathPoint>())
        every { mockRoutesRepo.allRoutes } returns MutableStateFlow(emptyList<RouteWithPath>())
        coEvery { mockTrackingViewsRepo.getActivityTypeForView(any()) } returns ActivityType.getDefaultActivityType()
        every { mockTrackingViewsRepo.getTrackingViewInfoFlow(any()) } returns flowOf(null)
        every { mockTrackingViewsRepo.getSensorFieldConfigsForView(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): TrackingViewModel {
        return TrackingViewModel(
            application = mockApplication,
            trackingViewsRepository = mockTrackingViewsRepo,
            banalServiceRepository = mockBanalRepo,
            liveSegmentsRepository = mockLiveSegmentsRepo,
            routesRepository = mockRoutesRepo,
            viewId = 1L
        )
    }

    @Test
    fun testSelectFieldForMove_andCancelMove() = runTest {
        val viewModel = createViewModel()
        assertNull(viewModel.selectedFieldForMove.value)

        val field = sampleField(10L, 1, 1)
        viewModel.onSelectFieldForMove(field)
        assertEquals(field, viewModel.selectedFieldForMove.value)

        viewModel.onCancelMove()
        assertNull(viewModel.selectedFieldForMove.value)
    }

    @Test
    fun testSwapFields_dispatchesToRepositoryAndClearsSelection() = runTest {
        val viewModel = createViewModel()
        val fieldA = sampleField(10L, 1, 1)
        viewModel.onSelectFieldForMove(fieldA)
        assertEquals(fieldA, viewModel.selectedFieldForMove.value)

        viewModel.onSwapFields(10L, 20L)

        // Selection must be cleared immediately
        assertNull(viewModel.selectedFieldForMove.value)

        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockTrackingViewsRepo.swapSensorFields(10L, 20L) }
    }

    @Test
    fun testMoveField_dispatchesToRepositoryAndClearsSelection() = runTest {
        val viewModel = createViewModel()
        val field = sampleField(15L, 2, 1)
        viewModel.onSelectFieldForMove(field)
        assertEquals(field, viewModel.selectedFieldForMove.value)

        viewModel.onMoveField(15L, 1, 2)

        assertNull(viewModel.selectedFieldForMove.value)

        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockTrackingViewsRepo.moveSensorField(15L, 1, 2) }
    }

    @Test
    fun testScreenModeChangeToTracking_cancelsActiveMoveSelection() = runTest {
        val viewModel = createViewModel()
        viewModel.updateScreenMode(ScreenMode.CONFIGURATION)

        val field = sampleField(30L, 1, 1)
        viewModel.onSelectFieldForMove(field)
        assertEquals(field, viewModel.selectedFieldForMove.value)

        // Exiting configuration mode must cancel move selection
        viewModel.updateScreenMode(ScreenMode.TRACKING)
        assertNull(viewModel.selectedFieldForMove.value)
    }

    @Test
    fun testDeleteSensorField_clearsMoveSelectionIfSameFieldDeleted() = runTest {
        val viewModel = createViewModel()
        val field = sampleField(40L, 1, 1)
        viewModel.onSelectFieldForMove(field)
        assertEquals(field, viewModel.selectedFieldForMove.value)

        viewModel.onDeleteSensorField(40L)
        assertNull(viewModel.selectedFieldForMove.value)

        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockTrackingViewsRepo.deleteSensorField(40L) }
    }
}
