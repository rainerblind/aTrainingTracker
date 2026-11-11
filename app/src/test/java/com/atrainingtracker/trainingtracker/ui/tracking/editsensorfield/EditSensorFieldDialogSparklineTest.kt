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
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldConfig
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Unit and contract tests verifying live sparkline graph chip toggling, 7 duration horizon choices,
 * mutual exclusion invariants, and persistence in [EditSensorFieldViewModel] (TST-TRK-059.4, REQ-TRK-059.3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditSensorFieldDialogSparklineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val application = mockk<Application>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val trackingViewsRepository = mockk<TrackingViewsRepository>(relaxed = true)
    private val banalServiceRepository = mockk<BANALServiceRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0

        mockkStatic(ActivityType::class)
        every { ActivityType.getSensorTypeArray(any(), any()) } returns arrayOf(
            SensorType.SPEED_mps,
            SensorType.HR,
            SensorType.POWER,
            SensorType.TIME_ACTIVE,
            SensorType.CADENCE
        )

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns context

        every { application.applicationContext } returns context
        every { context.getString(any()) } returns "Mock String"
        every { context.getString(any(), *anyVararg()) } returns "Mock Formatted"
        every { trackingViewsRepository.getSensorFieldConfigsForView(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(TrainingApplication::class)
        unmockkStatic(ActivityType::class)
        unmockkStatic(Log::class)
    }

    @Test
    fun testIsGraphSupported_trueForPower_falseForTimeActive() = runTest {
        val initialConfigPower = SensorFieldConfig(
            sensorFieldId = 1L,
            rowNr = 1,
            colNr = 1,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.POWER,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = -1L,
            showValue = true,
            showBar = false,
            showGraph = false,
            graphHorizonSeconds = 300
        )
        every { trackingViewsRepository.getSensorFieldConfig(1L) } returns flowOf(initialConfigPower)

        val viewModelPower = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepository,
            banalServiceRepository = banalServiceRepository,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = 1L,
            tabViewId = 10L,
            rowNr = 1,
            colNr = 1
        )
        viewModelPower.loadInitialState()
        advanceUntilIdle()

        assertTrue("POWER must support graph", viewModelPower.uiState.value.isGraphSupported)

        // Now test non-numeric TIME_ACTIVE
        val initialConfigTime = initialConfigPower.copy(sensorType = SensorType.TIME_ACTIVE)
        every { trackingViewsRepository.getSensorFieldConfig(2L) } returns flowOf(initialConfigTime)

        val viewModelTime = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepository,
            banalServiceRepository = banalServiceRepository,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = 2L,
            tabViewId = 10L,
            rowNr = 1,
            colNr = 1
        )
        viewModelTime.loadInitialState()
        advanceUntilIdle()

        assertFalse("TIME_ACTIVE must NOT support graph", viewModelTime.uiState.value.isGraphSupported)
    }

    @Test
    fun testSparklineMutualExclusion_andGraphHorizonSelection() = runTest {
        val initialConfig = SensorFieldConfig(
            sensorFieldId = 1L,
            rowNr = 1,
            colNr = 1,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.POWER,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = -1L,
            showValue = true,
            showBar = false,
            showGraph = false,
            graphHorizonSeconds = 300
        )
        every { trackingViewsRepository.getSensorFieldConfig(1L) } returns flowOf(initialConfig)

        val viewModel = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepository,
            banalServiceRepository = banalServiceRepository,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = 1L,
            tabViewId = 10L,
            rowNr = 1,
            colNr = 1
        )
        viewModel.loadInitialState()
        advanceUntilIdle()

        // 1. Cannot disable value when it's the only active element
        viewModel.onShowValueChanged(false)
        assertTrue("Value must remain true when no other visual element is active", viewModel.uiState.value.showValue)

        // 2. Enable Graph
        viewModel.onShowGraphChanged(true)
        assertTrue(viewModel.uiState.value.showGraph)
        assertEquals(300, viewModel.uiState.value.graphHorizonSeconds)

        // 3. Select 7 different duration horizons
        val horizons = listOf(60, 120, 300, 600, 1200, 3600, 0)
        for (h in horizons) {
            viewModel.onGraphHorizonChanged(h)
            assertEquals(h, viewModel.uiState.value.graphHorizonSeconds)
        }

        // 4. Now with Graph enabled, we CAN disable Value (entering Graph-only mode)
        viewModel.onShowValueChanged(false)
        assertFalse(viewModel.uiState.value.showValue)

        // 5. In Graph-only mode, attempting to disable Graph is rejected (at least 1 element must remain)
        viewModel.onShowGraphChanged(false)
        assertTrue("Graph must remain active when it is the sole active display element", viewModel.uiState.value.showGraph)
    }

    @Test
    fun testSaveChanges_persistsShowGraphAndHorizon() = runTest {
        val initialConfig = SensorFieldConfig(
            sensorFieldId = 1L,
            rowNr = 1,
            colNr = 1,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.POWER,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = -1L,
            showValue = true,
            showBar = false,
            showGraph = false,
            graphHorizonSeconds = 300
        )
        every { trackingViewsRepository.getSensorFieldConfig(1L) } returns flowOf(initialConfig)

        val viewModel = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepository,
            banalServiceRepository = banalServiceRepository,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = 1L,
            tabViewId = 10L,
            rowNr = 1,
            colNr = 1
        )
        viewModel.loadInitialState()
        advanceUntilIdle()

        viewModel.onShowGraphChanged(true)
        viewModel.onGraphHorizonChanged(600) // 10 minutes

        viewModel.saveChanges()
        advanceUntilIdle()

        coVerify {
            trackingViewsRepository.updateSensorFieldConfig(
                sensorFieldId = 1L,
                newSensorType = SensorType.POWER,
                newViewSize = ViewSize.NORMAL,
                newSourceDeviceId = null,
                newSourceDeviceName = null,
                newFilterType = FilterType.INSTANTANEOUS,
                newFilterConstant = 1.0,
                showValue = true,
                showBar = false,
                colWeight = 1.0f,
                showGraph = true,
                graphHorizonSeconds = 600
            )
        }
    }

    @Test
    fun testSliderIndexToDurationSeconds_all7Positions() {
        assertEquals(60, sliderIndexToDurationSeconds(0))
        assertEquals(120, sliderIndexToDurationSeconds(1))
        assertEquals(300, sliderIndexToDurationSeconds(2))
        assertEquals(600, sliderIndexToDurationSeconds(3))
        assertEquals(1200, sliderIndexToDurationSeconds(4))
        assertEquals(3600, sliderIndexToDurationSeconds(5))
        assertEquals(0, sliderIndexToDurationSeconds(6))

        // Clamping out of bounds
        assertEquals(60, sliderIndexToDurationSeconds(-1))
        assertEquals(0, sliderIndexToDurationSeconds(10))
    }

    @Test
    fun testDurationSecondsToSliderIndex_all7DurationsAndFallback() {
        assertEquals(0, durationSecondsToSliderIndex(60))
        assertEquals(1, durationSecondsToSliderIndex(120))
        assertEquals(2, durationSecondsToSliderIndex(300))
        assertEquals(3, durationSecondsToSliderIndex(600))
        assertEquals(4, durationSecondsToSliderIndex(1200))
        assertEquals(5, durationSecondsToSliderIndex(3600))
        assertEquals(6, durationSecondsToSliderIndex(0))

        // Unknown fallback defaults to 5m (index 2)
        assertEquals(2, durationSecondsToSliderIndex(999))
    }

    @Test
    fun testAll7Horizons_updateViewModelState() = runTest {
        val viewModel = EditSensorFieldViewModel(
            application = application,
            trackingViewsRepository = trackingViewsRepository,
            banalServiceRepository = banalServiceRepository,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = 1L,
            tabViewId = 10L,
            rowNr = 1,
            colNr = 1
        )
        viewModel.onShowGraphChanged(true)

        val expectedDurations = listOf(60, 120, 300, 600, 1200, 3600, 0)
        for (duration in expectedDurations) {
            viewModel.onGraphHorizonChanged(duration)
            assertEquals(duration, viewModel.uiState.value.graphHorizonSeconds)
        }
    }
}
