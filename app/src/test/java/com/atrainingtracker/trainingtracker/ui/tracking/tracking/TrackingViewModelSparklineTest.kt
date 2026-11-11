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
import androidx.compose.ui.graphics.Color
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Objects

/**
 * Unit and contract tests for TrackingViewModel live sparkline buffer sample preservation
 * and non-emitting tick retention (REQ-TRK-059.7, TST-TRK-059.7, ATT-6710).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingViewModelSparklineTest {

    private lateinit var viewModel: TrackingViewModel
    private val banalServiceRepo = mockk<BANALServiceRepository>(relaxed = true)

    @Before
    fun setUp() {
        val app = mockk<Application>(relaxed = true)
        val trackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
        val liveSegmentsRepo = mockk<LiveSegmentsRepository>(relaxed = true)
        val routesRepo = mockk<RoutesRepository>(relaxed = true)

        viewModel = TrackingViewModel(
            application = app,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            liveSegmentsRepository = liveSegmentsRepo,
            routesRepository = routesRepo,
            viewId = 1L
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeApplySensorData(
        currentFields: List<SensorFieldState>,
        allSensorData: List<FilteredSensorData<*>>,
        activityType: ActivityType = ActivityType.RUN_SPEED_AND_CADENCE
    ): List<SensorFieldState> {
        val applyMethod = TrackingViewModel::class.java.getDeclaredMethod(
            "applySensorData",
            List::class.java,
            List::class.java,
            ActivityType::class.java
        )
        applyMethod.isAccessible = true
        return applyMethod.invoke(viewModel, currentFields, allSensorData, activityType) as List<SensorFieldState>
    }

    @Test
    fun applySensorData_showGraphTrue_populatesSparklineSamplesFromRepository() {
        val fieldId = 1001L
        val configHash = Objects.hash(SensorType.HR, FilterType.INSTANTANEOUS, 0.0, null)
        val expectedSamples = listOf(130.0f, 135.0f, 140.0f)
        every { banalServiceRepo.getSparklineSamples(fieldId) } returns expectedSamples

        val field = SensorFieldState(
            configHash = configHash,
            sensorFieldId = fieldId,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Heart Rate",
            filterDescription = "None",
            value = "140",
            units = "bpm",
            zoneColor = Color.Red,
            showGraph = true,
            graphHorizonSeconds = 300,
            sparklineSamples = emptyList() // initially empty
        )

        val result = invokeApplySensorData(listOf(field), emptyList())
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("Must populate sparkline samples from repository", expectedSamples, result[0].sparklineSamples)
    }

    @Test
    fun applySensorData_emptySensorData_preservesAccumulatedSparklineSamplesWithoutEmptyListOverwrite() {
        val fieldId = 1002L
        val configHash = Objects.hash(SensorType.POWER, FilterType.INSTANTANEOUS, 0.0, null)
        val accumulatedSamples = listOf(200.0f, 210.0f, 220.0f)
        every { banalServiceRepo.getSparklineSamples(fieldId) } returns accumulatedSamples

        // Simulating cachedBaseFields where sparklineSamples was initially emptyList()
        val cachedBaseField = SensorFieldState(
            configHash = configHash,
            sensorFieldId = fieldId,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Power",
            filterDescription = "None",
            value = "220",
            units = "W",
            zoneColor = Color.Green,
            showGraph = true,
            graphHorizonSeconds = 300,
            sparklineSamples = emptyList()
        )

        // Tick with empty sensor data
        val result = invokeApplySensorData(listOf(cachedBaseField), emptyList())
        assertEquals("Empty sensor data must not overwrite accumulated samples with emptyList()", accumulatedSamples, result[0].sparklineSamples)
    }

    @Test
    fun applySensorData_partialSensorUpdate_unmatchedGraphFieldRetainsSamples() {
        val hrFieldId = 2001L
        val powerFieldId = 2002L
        val hrHash = Objects.hash(SensorType.HR, FilterType.INSTANTANEOUS, 0.0, null)
        val powerHash = Objects.hash(SensorType.POWER, FilterType.INSTANTANEOUS, 0.0, null)

        val powerSamples = listOf(250.0f, 255.0f, 260.0f)
        every { banalServiceRepo.getSparklineSamples(powerFieldId) } returns powerSamples
        every { banalServiceRepo.getSparklineSamples(hrFieldId) } returns emptyList()

        val hrField = SensorFieldState(
            configHash = hrHash,
            sensorFieldId = hrFieldId,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Heart Rate",
            filterDescription = "None",
            value = "150",
            units = "bpm",
            zoneColor = Color.Red,
            showGraph = false
        )
        val powerField = SensorFieldState(
            configHash = powerHash,
            sensorFieldId = powerFieldId,
            rowNr = 0,
            colNr = 1,
            viewSize = ViewSize.NORMAL,
            label = "Power",
            filterDescription = "None",
            value = "260",
            units = "W",
            zoneColor = Color.Blue,
            showGraph = true,
            sparklineSamples = powerSamples
        )

        // Live sensor data arrives ONLY for Heart Rate; Power does not emit on this tick
        val hrSensorData = FilteredSensorData<Double>(
            SensorType.HR,
            155.0,
            "155",
            null,
            FilterType.INSTANTANEOUS,
            0.0
        )

        val result = invokeApplySensorData(listOf(hrField, powerField), listOf(hrSensorData))
        val updatedPowerField = result.first { it.sensorFieldId == powerFieldId }
        assertEquals("Unmatched field on partial tick must retain accumulated sparkline samples", powerSamples, updatedPowerField.sparklineSamples)
    }

    @Test
    fun testClearSparklineBuffers_delegatesToRepositoryAndClearsUiState() {
        val fieldId = 3001L
        val configHash = Objects.hash(SensorType.HR, FilterType.INSTANTANEOUS, 0.0, null)
        val field = SensorFieldState(
            configHash = configHash,
            sensorFieldId = fieldId,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Heart Rate",
            filterDescription = "None",
            value = "140",
            units = "bpm",
            zoneColor = Color.Red,
            showGraph = true,
            graphHorizonSeconds = 300,
            sparklineSamples = listOf(140f, 142f, 145f),
            sparklineSpanSeconds = 45
        )

        // Inject initial field into uiState via reflection
        val uiStateField = TrackingViewModel::class.java.getDeclaredField("_uiState")
        uiStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mutableUiState = uiStateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<TrackingScreenState>
        mutableUiState.value = TrackingScreenState(fields = listOf(field))

        viewModel.clearSparklineBuffers()

        verify { banalServiceRepo.clearSparklineBuffers() }
        val clearedField = viewModel.uiState.value.fields.first { it.sensorFieldId == fieldId }
        assertTrue("Sparkline samples must be empty after clearSparklineBuffers()", clearedField.sparklineSamples.isEmpty())
        assertEquals("Sparkline span seconds must be 0 after clearSparklineBuffers()", 0, clearedField.sparklineSpanSeconds)
    }
}
