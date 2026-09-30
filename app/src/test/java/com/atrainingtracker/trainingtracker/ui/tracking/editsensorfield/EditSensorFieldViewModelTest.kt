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

package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import android.app.Application
import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Unit tests verifying ViewSize exposure and selection in EditSensorFieldViewModel (REQ-UI-181 / TST-UI-133).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditSensorFieldViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val application = mockk<Application>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val trackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
    private val banalServiceRepo = mockk<BANALServiceRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0

        mockkStatic(ActivityType::class)
        every { ActivityType.getSensorTypeArray(any(), any()) } returns arrayOf(
            SensorType.SPEED_mps,
            SensorType.HR,
            SensorType.POWER,
            SensorType.CADENCE
        )

        every { application.applicationContext } returns context
        every { context.getString(any()) } returns "mock_string"
        every { context.getString(any(), *anyVararg()) } returns "mock_formatted_string"
        every { trackingViewsRepo.getSensorFieldConfig(any()) } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(ActivityType::class)
        unmockkStatic(Log::class)
    }

    private fun createViewModel(sensorFieldId: Long = -1L): EditSensorFieldViewModel {
        return EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = sensorFieldId,
            tabViewId = 1L,
            rowNr = 0,
            colNr = 0
        )
    }

    @Test
    fun testAvailableViewSizes_containsAllNineSizesIncludingXXHugeAndXXXHuge() = runTest {
        val viewModel = createViewModel()
        val availableSizes = viewModel.uiState.value.availableViewSizes

        assertEquals(9, availableSizes.size)
        assertTrue("availableViewSizes must contain XXHUGE", availableSizes.contains(ViewSize.XXHUGE))
        assertTrue("availableViewSizes must contain XXXHUGE", availableSizes.contains(ViewSize.XXXHUGE))
        assertEquals(
            listOf(
                ViewSize.XSMALL,
                ViewSize.SMALL,
                ViewSize.NORMAL,
                ViewSize.LARGE,
                ViewSize.XLARGE,
                ViewSize.HUGE,
                ViewSize.XHUGE,
                ViewSize.XXHUGE,
                ViewSize.XXXHUGE
            ),
            availableSizes
        )
    }

    @Test
    fun testOnViewSizeChanged_updatesSelectedViewSizeToXXHugeAndXXXHuge() = runTest {
        val viewModel = createViewModel()
        assertEquals(ViewSize.NORMAL, viewModel.uiState.value.selectedViewSize)

        viewModel.onViewSizeChanged(ViewSize.XXHUGE)
        assertEquals(ViewSize.XXHUGE, viewModel.uiState.value.selectedViewSize)

        viewModel.onViewSizeChanged(ViewSize.XXXHUGE)
        assertEquals(ViewSize.XXXHUGE, viewModel.uiState.value.selectedViewSize)
    }

    @Test
    fun testViewSizeSerialization_namesAndValueOf() {
        assertEquals("XXHUGE", ViewSize.XXHUGE.name)
        assertEquals("XXXHUGE", ViewSize.XXXHUGE.name)
        assertEquals(ViewSize.XXHUGE, ViewSize.valueOf("XXHUGE"))
        assertEquals(ViewSize.XXXHUGE, ViewSize.valueOf("XXXHUGE"))
    }

    @Test
    fun testOnSensorTypeChanged_power_defaultsToThreeSecondsMovingAverage() = runTest {
        val viewModel = createViewModel()
        viewModel.onSensorTypeChanged(SensorType.POWER)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SensorType.POWER, state.selectedSensorType)
        assertEquals(FilterType.MOVING_AVERAGE_TIME, state.selectedFilterType)
        assertEquals(3.0, state.filterConstant, 0.001)
        assertEquals("sec", state.movingAverageUnit)
        assertFalse(state.isCustomFilterExpanded)
        assertEquals(FilterPreset.SMOOTH_3S, state.activePreset)
    }

    @Test
    fun testOnSensorTypeChanged_pace_defaultsToFiveSecondsMovingAverage() = runTest {
        val viewModel = createViewModel()
        viewModel.onSensorTypeChanged(SensorType.PACE_spm)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SensorType.PACE_spm, state.selectedSensorType)
        assertEquals(FilterType.MOVING_AVERAGE_TIME, state.selectedFilterType)
        assertEquals(5.0, state.filterConstant, 0.001)
        assertEquals("sec", state.movingAverageUnit)
        assertTrue(state.isCustomFilterExpanded)
        assertEquals(FilterPreset.CUSTOM, state.activePreset)
    }

    @Test
    fun testOnSensorTypeChanged_verticalSpeed_defaultsToFifteenSecondsMovingAverage() = runTest {
        val viewModel = createViewModel()
        viewModel.onSensorTypeChanged(SensorType.VERTICAL_SPEED)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SensorType.VERTICAL_SPEED, state.selectedSensorType)
        assertEquals(FilterType.MOVING_AVERAGE_TIME, state.selectedFilterType)
        assertEquals(15.0, state.filterConstant, 0.001)
        assertEquals("sec", state.movingAverageUnit)
        assertTrue(state.isCustomFilterExpanded)
        assertEquals(FilterPreset.CUSTOM, state.activePreset)
    }

    @Test
    fun testOnSensorTypeChanged_slope_defaultsToFiveSecondsMovingAverage() = runTest {
        val viewModel = createViewModel()
        viewModel.onSensorTypeChanged(SensorType.SLOPE)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SensorType.SLOPE, state.selectedSensorType)
        assertEquals(FilterType.MOVING_AVERAGE_TIME, state.selectedFilterType)
        assertEquals(5.0, state.filterConstant, 0.001)
        assertEquals("sec", state.movingAverageUnit)
        assertTrue(state.isCustomFilterExpanded)
        assertEquals(FilterPreset.CUSTOM, state.activePreset)
    }

    @Test
    fun testOnSensorTypeChanged_accumulator_defaultsToInstantaneous() = runTest {
        val viewModel = createViewModel()
        viewModel.onSensorTypeChanged(SensorType.DISTANCE_m)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SensorType.DISTANCE_m, state.selectedSensorType)
        assertEquals(FilterType.INSTANTANEOUS, state.selectedFilterType)
        assertEquals(1.0, state.filterConstant, 0.001)
        assertEquals("sec", state.movingAverageUnit)
        assertFalse(state.isCustomFilterExpanded)
        assertEquals(FilterPreset.DIRECT, state.activePreset)
    }

    @Test
    fun testOnFilterConfigDismissed_restoresDomainDefaultWhenSensorTypeChanged() = runTest {
        val viewModel = createViewModel()
        // Switch to PACE
        viewModel.onSensorTypeChanged(SensorType.PACE_spm)
        testDispatcher.scheduler.advanceUntilIdle()

        // User temporarily alters filter in dialog
        viewModel.onPresetSelected(FilterPreset.SMOOTH_30S)
        assertEquals(FilterType.MOVING_AVERAGE_TIME, viewModel.uiState.value.selectedFilterType)
        assertEquals(30.0, viewModel.uiState.value.filterConstant, 0.001)

        // Dismiss dialog without saving
        viewModel.onFilterConfigDismissed()

        val state = viewModel.uiState.value
        assertEquals(SensorType.PACE_spm, state.selectedSensorType)
        assertEquals(FilterType.MOVING_AVERAGE_TIME, state.selectedFilterType)
        assertEquals(5.0, state.filterConstant, 0.001)
        assertEquals("sec", state.movingAverageUnit)
        assertTrue(state.isCustomFilterExpanded)
    }
}
