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
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldConfig
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
 * Unit test suite verifying intuitive smoothing presets, bidirectional preset resolution,
 * smart athletic defaults, and filter configuration state transitions according to TST-UI-119 and REQ-UI-167.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfigureFilterDialogTest {

    private val testDispatcher = StandardTestDispatcher()
    private val application = mockk<Application>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val trackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
    private val banalServiceRepo = mockk<BANALServiceRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

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
    }

    @After
    fun tearDown() {
        unmockkStatic(ActivityType::class)
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun testResolveFilterPreset_mapsStandardPresetsAccurately() {
        // Direct / Instantaneous (1s)
        assertEquals(FilterPreset.DIRECT, resolveFilterPreset(FilterType.INSTANTANEOUS, 1.0, "sec"))
        assertEquals(FilterPreset.DIRECT, resolveFilterPreset(FilterType.MOVING_AVERAGE_TIME, 1.0, "sec"))

        // Standard athletic moving average presets
        assertEquals(FilterPreset.SMOOTH_3S, resolveFilterPreset(FilterType.MOVING_AVERAGE_TIME, 3.0, "sec"))
        assertEquals(FilterPreset.SMOOTH_10S, resolveFilterPreset(FilterType.MOVING_AVERAGE_TIME, 10.0, "sec"))
        assertEquals(FilterPreset.SMOOTH_30S, resolveFilterPreset(FilterType.MOVING_AVERAGE_TIME, 30.0, "sec"))

        // Session metrics
        assertEquals(FilterPreset.SESSION_AVG, resolveFilterPreset(FilterType.AVERAGE, 1.0, "sec"))
        assertEquals(FilterPreset.SESSION_MAX, resolveFilterPreset(FilterType.MAX_VALUE, 1.0, "sec"))
    }

    @Test
    fun testResolveFilterPreset_fallsBackToCustomForArbitraryInputs() {
        // Non-standard seconds
        assertEquals(FilterPreset.CUSTOM, resolveFilterPreset(FilterType.MOVING_AVERAGE_TIME, 5.0, "sec"))
        assertEquals(FilterPreset.CUSTOM, resolveFilterPreset(FilterType.MOVING_AVERAGE_TIME, 15.0, "sec"))

        // Minute units (e.g. 1 min = 60s)
        assertEquals(FilterPreset.CUSTOM, resolveFilterPreset(FilterType.MOVING_AVERAGE_TIME, 1.0, "min"))

        // Sample-based moving average
        assertEquals(FilterPreset.CUSTOM, resolveFilterPreset(FilterType.MOVING_AVERAGE_NUMBER, 5.0, "samples"))

        // Exponential smoothing
        assertEquals(FilterPreset.CUSTOM, resolveFilterPreset(FilterType.EXPONENTIAL_SMOOTHING, 0.2, "sec"))
    }

    @Test
    fun testSmartAthleticDefaults_defaultsPowerSensorTo3sMovingAverage() = runTest(testDispatcher) {
        val viewModel = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = -1L,
            tabViewId = 1L,
            rowNr = 1,
            colNr = 1
        )

        viewModel.onSensorTypeChanged(SensorType.POWER)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SensorType.POWER, state.selectedSensorType)
        assertEquals(FilterType.MOVING_AVERAGE_TIME, state.selectedFilterType)
        assertEquals(3.0, state.filterConstant, 0.001)
        assertEquals("sec", state.movingAverageUnit)
        assertEquals(FilterPreset.SMOOTH_3S, state.activePreset)
        assertFalse(state.isCustomFilterExpanded)
    }

    @Test
    fun testSmartAthleticDefaults_defaultsOtherSensorsToInstantaneous() = runTest(testDispatcher) {
        val viewModel = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = -1L,
            tabViewId = 1L,
            rowNr = 1,
            colNr = 1
        )

        // Heart rate
        viewModel.onSensorTypeChanged(SensorType.HR)
        testDispatcher.scheduler.advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals(FilterType.INSTANTANEOUS, state.selectedFilterType)
        assertEquals(1.0, state.filterConstant, 0.001)
        assertEquals(FilterPreset.DIRECT, state.activePreset)

        // Speed
        viewModel.onSensorTypeChanged(SensorType.SPEED_mps)
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(FilterType.INSTANTANEOUS, state.selectedFilterType)
        assertEquals(1.0, state.filterConstant, 0.001)
        assertEquals(FilterPreset.DIRECT, state.activePreset)
    }

    @Test
    fun testPresetSelection_transitionsFilterStateCleanly() = runTest(testDispatcher) {
        val viewModel = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = -1L,
            tabViewId = 1L,
            rowNr = 1,
            colNr = 1
        )

        // Select 10s Pacing preset
        viewModel.onPresetSelected(FilterPreset.SMOOTH_10S)
        var state = viewModel.uiState.value
        assertEquals(FilterType.MOVING_AVERAGE_TIME, state.selectedFilterType)
        assertEquals(10.0, state.filterConstant, 0.001)
        assertEquals("sec", state.movingAverageUnit)
        assertEquals(FilterPreset.SMOOTH_10S, state.activePreset)
        assertFalse(state.isCustomFilterExpanded)

        // Select 30s Endurance preset
        viewModel.onPresetSelected(FilterPreset.SMOOTH_30S)
        state = viewModel.uiState.value
        assertEquals(FilterType.MOVING_AVERAGE_TIME, state.selectedFilterType)
        assertEquals(30.0, state.filterConstant, 0.001)
        assertEquals(FilterPreset.SMOOTH_30S, state.activePreset)

        // Select Session Average preset
        viewModel.onPresetSelected(FilterPreset.SESSION_AVG)
        state = viewModel.uiState.value
        assertEquals(FilterType.AVERAGE, state.selectedFilterType)
        assertEquals(FilterPreset.SESSION_AVG, state.activePreset)

        // Select Custom preset expands custom inputs
        viewModel.onPresetSelected(FilterPreset.CUSTOM)
        state = viewModel.uiState.value
        assertTrue(state.isCustomFilterExpanded)
        assertEquals(FilterPreset.CUSTOM, state.activePreset)
    }

    @Test
    fun testModeSwitching_togglesCustomFilterExpanded() = runTest(testDispatcher) {
        val viewModel = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = -1L,
            tabViewId = 1L,
            rowNr = 1,
            colNr = 1
        )

        // Initially in Schnellauswahl (presets) mode
        assertFalse(viewModel.uiState.value.isCustomFilterExpanded)

        // Switch to Manual / Expert mode
        viewModel.onCustomFilterExpandedChanged(true)
        assertTrue(viewModel.uiState.value.isCustomFilterExpanded)

        // Switch back to Presets mode
        viewModel.onCustomFilterExpandedChanged(false)
        assertFalse(viewModel.uiState.value.isCustomFilterExpanded)
    }

    @Test
    fun testFilterConfigDismissed_restoresInitialConfiguration() = runTest(testDispatcher) {
        val existingConfig = SensorFieldConfig(
            sensorFieldId = 42L,
            rowNr = 1,
            colNr = 1,
            sensorType = SensorType.POWER,
            sourceDeviceId = 10L,
            sourceDeviceName = "Power Meter",
            viewSize = ViewSize.HUGE,
            filterType = FilterType.MOVING_AVERAGE_TIME,
            filterConstant = 3.0
        )

        every { trackingViewsRepo.getSensorFieldConfig(42L) } returns flowOf(existingConfig)

        val viewModel = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = 42L,
            tabViewId = 1L,
            rowNr = 1,
            colNr = 1
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // Verify loaded state
        var state = viewModel.uiState.value
        assertEquals(FilterPreset.SMOOTH_3S, state.activePreset)

        // User opens dialog and modifies preset to SESSION_MAX
        viewModel.onConfigureFilterClicked()
        viewModel.onPresetSelected(FilterPreset.SESSION_MAX)
        state = viewModel.uiState.value
        assertEquals(FilterPreset.SESSION_MAX, state.activePreset)

        // User dismisses without saving
        viewModel.onFilterConfigDismissed()
        state = viewModel.uiState.value

        // Should revert back to 3s power smoothing
        assertEquals(FilterType.MOVING_AVERAGE_TIME, state.selectedFilterType)
        assertEquals(3.0, state.filterConstant, 0.001)
        assertEquals(FilterPreset.SMOOTH_3S, state.activePreset)
        assertFalse(state.showFilterConfigDialog)
    }

    @Test
    fun testExponentialSmoothing_adjustsAndConstrainsAlpha() = runTest(testDispatcher) {
        val viewModel = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = -1L,
            tabViewId = 1L,
            rowNr = 1,
            colNr = 1
        )

        // Default power sensor has 3s moving average (constant = 3.0)
        viewModel.onSensorTypeChanged(SensorType.POWER)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3.0, viewModel.uiState.value.filterConstant, 0.001)

        // Switching to EXPONENTIAL_SMOOTHING must adjust constant from 3.0 to 0.8 (within (0, 1])
        viewModel.onFilterTypeChanged(FilterType.EXPONENTIAL_SMOOTHING)
        var state = viewModel.uiState.value
        assertEquals(FilterType.EXPONENTIAL_SMOOTHING, state.selectedFilterType)
        assertEquals(0.8, state.filterConstant, 0.001)
        assertTrue(state.filterConstant > 0.0 && state.filterConstant <= 1.0)

        // Changing constant while in EXPONENTIAL_SMOOTHING must clamp to (0, 1]
        viewModel.onFilterConstantChanged(3.0)
        assertEquals(1.0, viewModel.uiState.value.filterConstant, 0.001)

        viewModel.onFilterConstantChanged(0.0)
        assertEquals(0.01, viewModel.uiState.value.filterConstant, 0.001)

        viewModel.onFilterConstantChanged(0.5)
        assertEquals(0.5, viewModel.uiState.value.filterConstant, 0.001)

        // Switching back to MOVING_AVERAGE_TIME adjusts constant from <= 1.0 to 3.0
        viewModel.onFilterTypeChanged(FilterType.MOVING_AVERAGE_TIME)
        assertEquals(3.0, viewModel.uiState.value.filterConstant, 0.001)
    }
}

