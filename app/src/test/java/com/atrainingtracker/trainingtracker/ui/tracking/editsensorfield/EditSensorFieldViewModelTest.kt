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

    private fun createViewModel(
        sensorFieldId: Long = -1L,
        activityType: ActivityType = ActivityType.BIKE_POWER,
        tabViewId: Long = 1L,
        rowNr: Int = 0,
        colNr: Int = 0
    ): EditSensorFieldViewModel {
        return EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            activityType = activityType,
            sensorFieldId = sensorFieldId,
            tabViewId = tabViewId,
            rowNr = rowNr,
            colNr = colNr
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
                newFilterConstant = any(),
                showValue = any(),
                showBar = any()
            )
        }
    }

    /**
     * TST-TRK-046.1: Bar-supported sensors (HR, POWER, PEDAL_POWER_BALANCE, W_PRIME_BAL)
     * default to showValue = true and showBar = true, and enforce mutual exclusion
     * so athlete cannot deselect both simultaneously (REQ-TRK-046).
     */
    @Test
    fun testDisplayElements_barSupportedSensors_defaultsAndMutualExclusion() = runTest {
        val viewModel = createViewModel(sensorFieldId = -1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSensorTypeChanged(SensorType.POWER)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("POWER must support horizontal bar", state.isBarSupported)
        assertTrue("POWER must default showValue = true", state.showValue)
        assertTrue("POWER must default showBar = true", state.showBar)

        // Deselect value -> bar remains active, showValue becomes false
        viewModel.onShowValueChanged(false)
        assertFalse("showValue must become false", viewModel.uiState.value.showValue)
        assertTrue("showBar must remain true", viewModel.uiState.value.showBar)

        // Try to deselect bar while value is inactive -> invariant prevents both false
        viewModel.onShowBarChanged(false)
        assertFalse("showValue must remain false", viewModel.uiState.value.showValue)
        assertTrue("showBar must stay true due to mutual exclusion", viewModel.uiState.value.showBar)

        // Re-enable value
        viewModel.onShowValueChanged(true)
        assertTrue("showValue must become true", viewModel.uiState.value.showValue)

        // Deselect bar -> showBar becomes false
        viewModel.onShowBarChanged(false)
        assertTrue("showValue must remain true", viewModel.uiState.value.showValue)
        assertFalse("showBar must become false", viewModel.uiState.value.showBar)

        // Try to deselect value while bar is inactive -> invariant prevents both false
        viewModel.onShowValueChanged(false)
        assertTrue("showValue must stay true due to mutual exclusion", viewModel.uiState.value.showValue)
        assertFalse("showBar must remain false", viewModel.uiState.value.showBar)
    }

    /**
     * TST-TRK-046.2: Non-bar sensors (e.g. SPEED_mps) do not support bar and cannot disable value.
     */
    @Test
    fun testDisplayElements_unsupportedSensor_barDisabled() = runTest {
        val viewModel = createViewModel(sensorFieldId = -1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSensorTypeChanged(SensorType.SPEED_mps)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("SPEED_mps must not support bar", state.isBarSupported)
        assertTrue("SPEED_mps must default showValue = true", state.showValue)
        assertFalse("SPEED_mps must default showBar = false", state.showBar)

        // Athlete cannot deselect value when bar is unsupported
        viewModel.onShowValueChanged(false)
        assertTrue("showValue must remain true for non-bar sensors", viewModel.uiState.value.showValue)
    }

    /**
     * TST-TRK-046.3: saveChanges persists showValue and showBar flags into repository.
     */
    @Test
    fun testDisplayElements_saveChanges_persistsFlags() = runTest {
        val viewModel = createViewModel(sensorFieldId = -1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSensorTypeChanged(SensorType.HR)
        testDispatcher.scheduler.advanceUntilIdle()

        // Configure bar-only tile: showValue = false, showBar = true
        viewModel.onShowValueChanged(false)
        viewModel.saveChanges()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            trackingViewsRepo.insertSensorFieldConfig(
                tabViewId = 1L,
                rowNr = 0,
                colNr = 0,
                newSensorType = SensorType.HR,
                newViewSize = ViewSize.NORMAL,
                newSourceDeviceId = null,
                newSourceDeviceName = null,
                newFilterType = any(),
                newFilterConstant = any(),
                showValue = false,
                showBar = true,
                colWeight = any()
            )
        }
    }

    /**
     * TST-TRK-046.3: Sport-dependent bar support in EditDialogUiState:
     * HR supports bar in RUN and BIKE, but not GENERIC.
     * POWER, BALANCE, and W'BAL support bar only in BIKE, not RUN.
     */
    @Test
    fun testDisplayElements_sportDependentBarFeasibility() = runTest {
        // Run layout
        val runViewModel = createViewModel(activityType = ActivityType.RUN_SPEED_AND_CADENCE)
        testDispatcher.scheduler.advanceUntilIdle()

        runViewModel.onSensorTypeChanged(SensorType.HR)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("HR in RUN must support bar", runViewModel.uiState.value.isBarSupported)

        runViewModel.onSensorTypeChanged(SensorType.POWER)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("POWER in RUN must NOT support bar", runViewModel.uiState.value.isBarSupported)
        assertTrue("showValue must default to true when bar unsupported", runViewModel.uiState.value.showValue)
        assertFalse("showBar must default to false when bar unsupported", runViewModel.uiState.value.showBar)

        runViewModel.onSensorTypeChanged(SensorType.PEDAL_POWER_BALANCE)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("PEDAL_POWER_BALANCE in RUN must NOT support bar", runViewModel.uiState.value.isBarSupported)

        runViewModel.onSensorTypeChanged(SensorType.W_PRIME_BAL)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("W_PRIME_BAL in RUN must NOT support bar", runViewModel.uiState.value.isBarSupported)

        // Generic layout
        val genericViewModel = createViewModel(activityType = ActivityType.GENERIC_HR)
        testDispatcher.scheduler.advanceUntilIdle()

        genericViewModel.onSensorTypeChanged(SensorType.HR)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("HR in GENERIC must NOT support bar", genericViewModel.uiState.value.isBarSupported)
    }

    /**
     * TST-TRK-046.3: Saving unsupported bar sensor enforces showValue = true, showBar = false.
     */
    @Test
    fun testDisplayElements_saveChanges_unsupportedBarEnforcesShowValueTrueAndShowBarFalse() = runTest {
        val runViewModel = createViewModel(activityType = ActivityType.RUN_SPEED_AND_CADENCE)
        testDispatcher.scheduler.advanceUntilIdle()

        runViewModel.onSensorTypeChanged(SensorType.POWER)
        testDispatcher.scheduler.advanceUntilIdle()

        runViewModel.saveChanges()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            trackingViewsRepo.insertSensorFieldConfig(
                tabViewId = 1L,
                rowNr = 0,
                colNr = 0,
                newSensorType = SensorType.POWER,
                newViewSize = ViewSize.NORMAL,
                newSourceDeviceId = null,
                newSourceDeviceName = null,
                newFilterType = any(),
                newFilterConstant = any(),
                showValue = true,
                showBar = false,
                colWeight = 1.0f
            )
        }
    }

    /**
     * TST-UI-437.3: Individual relative width weight updates uiState and persists on save.
     */
    @Test
    fun testColWeight_individualWeightSelection_updatesStateAndPersists() = runTest {
        val viewModel = createViewModel(sensorFieldId = -1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1.0f, viewModel.uiState.value.colWeight, 0.001f)

        viewModel.onColWeightChanged(2.0f)
        assertEquals(2.0f, viewModel.uiState.value.colWeight, 0.001f)

        viewModel.onColWeightChanged(3.0f)
        assertEquals(3.0f, viewModel.uiState.value.colWeight, 0.001f)

        // Invalid weight <= 0 is safely clamped to 1.0f
        viewModel.onColWeightChanged(0f)
        assertEquals(1.0f, viewModel.uiState.value.colWeight, 0.001f)

        viewModel.onColWeightChanged(3.0f)
        viewModel.saveChanges()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            trackingViewsRepo.insertSensorFieldConfig(
                tabViewId = 1L,
                rowNr = 0,
                colNr = 0,
                newSensorType = any(),
                newViewSize = any(),
                newSourceDeviceId = any(),
                newSourceDeviceName = any(),
                newFilterType = any(),
                newFilterConstant = any(),
                showValue = any(),
                showBar = any(),
                colWeight = 3.0f
            )
        }
    }

    /**
     * TST-UI-437.3: Editing existing field updates relative colWeight and persists without sibling update (ATT-5077).
     */
    @Test
    fun testColWeight_editField_updatesColWeightAndPersistsWithoutSiblingUpdate() = runTest {
        val field1 = SensorFieldConfig(
            sensorFieldId = 10L,
            rowNr = 1,
            colNr = 1,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.POWER,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = -1,
            colWeight = 1.0f
        )
        every { trackingViewsRepo.getSensorFieldConfig(10L) } returns flowOf(field1)

        val viewModel = createViewModel(sensorFieldId = 10L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1.0f, viewModel.uiState.value.colWeight, 0.001f)

        // Select 2x relative weight
        viewModel.onColWeightChanged(2.0f)
        assertEquals(2.0f, viewModel.uiState.value.colWeight, 0.001f)

        viewModel.saveChanges()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            trackingViewsRepo.updateSensorFieldConfig(
                sensorFieldId = 10L,
                newSensorType = any(),
                newViewSize = any(),
                newSourceDeviceId = any(),
                newSourceDeviceName = any(),
                newFilterType = any(),
                newFilterConstant = any(),
                showValue = any(),
                showBar = any(),
                colWeight = 2.0f
            )
        }
        coVerify(exactly = 0) {
            trackingViewsRepo.updateColWeight(any(), any())
        }
    }

    // --- TST-UI-437.6: Relative Width Cardinality Gating (ATT-5192) ---

    @Test
    fun testCalculateIsMultiFieldRow_pureLogic() {
        // Adding new field into a row: multi-field if row already has >= 1 field
        assertFalse(calculateIsMultiFieldRow(existingFieldsInTargetRow = 0, isNewField = true))
        assertTrue(calculateIsMultiFieldRow(existingFieldsInTargetRow = 1, isNewField = true))
        assertTrue(calculateIsMultiFieldRow(existingFieldsInTargetRow = 2, isNewField = true))

        // Editing existing field: multi-field only if row currently has > 1 field
        assertFalse(calculateIsMultiFieldRow(existingFieldsInTargetRow = 0, isNewField = false))
        assertFalse(calculateIsMultiFieldRow(existingFieldsInTargetRow = 1, isNewField = false))
        assertTrue(calculateIsMultiFieldRow(existingFieldsInTargetRow = 2, isNewField = false))
        assertTrue(calculateIsMultiFieldRow(existingFieldsInTargetRow = 3, isNewField = false))
    }

    @Test
    fun testIsMultiFieldRow_editingFieldInSingleFieldRow_isFalse() = runTest {
        val field = SensorFieldConfig(
            sensorFieldId = 10L,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.SPEED_mps,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = -1,
            colWeight = 1.0f
        )
        every { trackingViewsRepo.getSensorFieldConfig(10L) } returns flowOf(field)
        every { trackingViewsRepo.getSensorFieldConfigsForView(1L) } returns flowOf(listOf(field))

        val viewModel = createViewModel(sensorFieldId = 10L, rowNr = 0, colNr = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Single-field row must not be marked as multi-field", viewModel.uiState.value.isMultiFieldRow)
    }

    @Test
    fun testIsMultiFieldRow_editingFieldInMultiFieldRow_isTrue() = runTest {
        val field1 = SensorFieldConfig(
            sensorFieldId = 10L,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.SPEED_mps,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = -1,
            colWeight = 1.0f
        )
        val field2 = SensorFieldConfig(
            sensorFieldId = 11L,
            rowNr = 0,
            colNr = 1,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.HR,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = -1,
            colWeight = 1.0f
        )
        every { trackingViewsRepo.getSensorFieldConfig(10L) } returns flowOf(field1)
        every { trackingViewsRepo.getSensorFieldConfigsForView(1L) } returns flowOf(listOf(field1, field2))

        val viewModel = createViewModel(sensorFieldId = 10L, rowNr = 0, colNr = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Row with 2 fields must be marked as multi-field", viewModel.uiState.value.isMultiFieldRow)
    }

    @Test
    fun testIsMultiFieldRow_addingFieldToExistingRow_isTrue() = runTest {
        val existingField = SensorFieldConfig(
            sensorFieldId = 10L,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.SPEED_mps,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = -1,
            colWeight = 1.0f
        )
        every { trackingViewsRepo.getSensorFieldConfigsForView(1L) } returns flowOf(listOf(existingField))

        // Adding into row 0 where 1 field already exists
        val viewModel = createViewModel(sensorFieldId = -1L, rowNr = 0, colNr = 1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Adding field into row with 1 existing field must result in multi-field row", viewModel.uiState.value.isMultiFieldRow)
    }

    @Test
    fun testIsMultiFieldRow_addingFieldToNewRow_isFalse() = runTest {
        val existingField = SensorFieldConfig(
            sensorFieldId = 10L,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.SPEED_mps,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = -1,
            colWeight = 1.0f
        )
        every { trackingViewsRepo.getSensorFieldConfigsForView(1L) } returns flowOf(listOf(existingField))

        // colNr = -1 indicates creating a new row
        val viewModel = createViewModel(sensorFieldId = -1L, rowNr = 1, colNr = -1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Adding field to a new row must result in single-field row", viewModel.uiState.value.isMultiFieldRow)
    }
}
