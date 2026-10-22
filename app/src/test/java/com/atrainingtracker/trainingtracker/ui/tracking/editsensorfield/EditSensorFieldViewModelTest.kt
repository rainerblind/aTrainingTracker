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
import com.atrainingtracker.banalservice.filters.FilterData
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldConfig
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
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
import org.junit.Assert.assertNull
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

    @Test
    fun testAvailableSensorTypes_reactivelyUpdatesOnDelayedTuningConfigEmission() = runTest {
        every { ActivityType.getSensorTypeArray(any(), any()) } returns arrayOf(
            SensorType.SPEED_mps,
            SensorType.POWER,
            SensorType.NORMALIZED_POWER,
            SensorType.INTENSITY_FACTOR,
            SensorType.TSS,
            SensorType.W_PRIME_BAL
        )

        val delayedTuningFlow = kotlinx.coroutines.flow.MutableSharedFlow<com.atrainingtracker.trainingtracker.settings.TuningConfig>()
        val viewModel = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = -1L,
            tabViewId = 1L,
            rowNr = 0,
            colNr = 0,
            tuningConfigFlow = delayedTuningFlow
        )

        // Immediately before delayed flow emits, initial state uses default TuningConfig (Coggan=true, CP=false)
        // and never shows an empty list
        val initialAvailable = viewModel.uiState.value.availableSensorTypesForCurrentActivityType
        assertTrue(initialAvailable.isNotEmpty())
        assertTrue(initialAvailable.contains(SensorType.NORMALIZED_POWER))
        assertFalse(initialAvailable.contains(SensorType.W_PRIME_BAL))

        // Simulate delayed TuningConfig emission disabling Coggan and enabling Critical Power
        testDispatcher.scheduler.advanceUntilIdle()
        delayedTuningFlow.emit(
            com.atrainingtracker.trainingtracker.settings.TuningConfig(
                enableCogganPowerMetrics = false,
                enableCriticalPowerModel = true
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedAvailable = viewModel.uiState.value.availableSensorTypesForCurrentActivityType
        assertFalse(updatedAvailable.contains(SensorType.NORMALIZED_POWER))
        assertFalse(updatedAvailable.contains(SensorType.INTENSITY_FACTOR))
        assertFalse(updatedAvailable.contains(SensorType.TSS))
        assertTrue(updatedAvailable.contains(SensorType.W_PRIME_BAL))
        assertTrue(updatedAvailable.contains(SensorType.POWER))
    }

    /**
     * TST-FIL-014.1: Creating a new field with sentinel "Best Sensor" (selectedDeviceId = -1)
     * must normalize FilterData.deviceName = null and repository sourceDeviceId/Name = null
     * (Rule 50 / REQ-FIL-014.1).
     */
    @Test
    fun testSaveChanges_newField_bestSensor_normalizesDeviceNameToNull() = runTest {
        val viewModel = createViewModel(sensorFieldId = -1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveChanges()
        testDispatcher.scheduler.advanceUntilIdle()

        val filterSlot = slot<FilterData>()
        verify { banalServiceRepo.createFilter(capture(filterSlot)) }
        assertNull("Best sensor must normalize FilterData.deviceName to null", filterSlot.captured.deviceName)

        coVerify {
            trackingViewsRepo.insertSensorFieldConfig(
                tabViewId = 1L,
                rowNr = 0,
                colNr = 0,
                newSensorType = SensorType.SPEED_mps,
                newViewSize = ViewSize.NORMAL,
                newSourceDeviceId = null,
                newSourceDeviceName = null,
                newFilterType = any(),
                newFilterConstant = any()
            )
        }
    }

    /**
     * TST-FIL-014.2: Updating an existing field with sentinel "Best Sensor" (selectedDeviceId = -1)
     * must normalize FilterData.deviceName = null and repository update sourceDeviceId/Name = null
     * (Rule 50 / REQ-FIL-014.1).
     */
    @Test
    fun testSaveChanges_editField_bestSensor_normalizesDeviceNameToNull() = runTest {
        val existingConfig = SensorFieldConfig(
            sensorFieldId = 42L,
            rowNr = 0,
            colNr = 0,
            sensorType = SensorType.POWER,
            sourceDeviceId = -1L,
            sourceDeviceName = "Bester Sensor",
            viewSize = ViewSize.NORMAL,
            filterType = FilterType.MOVING_AVERAGE_TIME,
            filterConstant = 3.0
        )
        every { trackingViewsRepo.getSensorFieldConfig(42L) } returns flowOf(existingConfig)

        val viewModel = createViewModel(sensorFieldId = 42L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveChanges()
        testDispatcher.scheduler.advanceUntilIdle()

        val filterSlot = slot<FilterData>()
        verify { banalServiceRepo.createFilter(capture(filterSlot)) }
        assertNull("Best sensor update must normalize FilterData.deviceName to null", filterSlot.captured.deviceName)

        coVerify {
            trackingViewsRepo.updateSensorFieldConfig(
                sensorFieldId = 42L,
                newSensorType = SensorType.POWER,
                newViewSize = ViewSize.NORMAL,
                newSourceDeviceId = null,
                newSourceDeviceName = null,
                newFilterType = FilterType.MOVING_AVERAGE_TIME,
                newFilterConstant = 3.0
            )
        }
    }

    /**
     * TST-FIL-014.3: Selecting a specific paired device (selectedDeviceId > 0) must preserve
     * the explicit deviceName and deviceId across FilterData and repository persistence
     * (REQ-FIL-014.1, REQ-FIL-014.4).
     */
    @Test
    fun testSaveChanges_specificDevice_preservesDeviceNameAndId() = runTest {
        val viewModel = createViewModel(sensorFieldId = -1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeviceChanged(123L, "Stages Power")
        viewModel.saveChanges()
        testDispatcher.scheduler.advanceUntilIdle()

        val filterSlot = slot<FilterData>()
        verify { banalServiceRepo.createFilter(capture(filterSlot)) }
        assertEquals("Stages Power", filterSlot.captured.deviceName)

        coVerify {
            trackingViewsRepo.insertSensorFieldConfig(
                tabViewId = 1L,
                rowNr = 0,
                colNr = 0,
                newSensorType = SensorType.SPEED_mps,
                newViewSize = ViewSize.NORMAL,
                newSourceDeviceId = 123L,
                newSourceDeviceName = "Stages Power",
                newFilterType = any(),
                newFilterConstant = any()
            )
        }
    }
}
