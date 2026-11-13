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
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Objects

/**
 * Unit tests for [TrackingViewModel] HRV telemetry integration conforming to REQ-TRK-061.3 and TST-TRK-061.3:
 * - ZoneType resolution for HRV_DFA_A1 mapping to HR_RUN and HR_BIKE
 * - HR ZoneDisplayOptions parity inheritance
 * - Dynamic artifact suppression threshold gating via TuningConfig (REQ-TRK-061.2)
 */
class TrackingViewModelHrvTest {

    private lateinit var viewModel: TrackingViewModel

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.getColor(any(), any()) } returns 0xFF000000.toInt()

        val app = mockk<Application>(relaxed = true)
        val trackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
        val banalServiceRepo = mockk<BANALServiceRepository>(relaxed = true)
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

    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
    }

    private fun callGetZoneType(sensorType: SensorType, sportType: BSportType): SettingsDataStore.ZoneType? {
        val method = TrackingViewModel::class.java.declaredMethods.first { it.name.startsWith("getZoneType") }
        method.isAccessible = true
        return method.invoke(viewModel, sensorType, sportType) as? SettingsDataStore.ZoneType
    }

    private fun callResolveDisplayOptions(sensorType: SensorType, sportType: BSportType): ZoneDisplayOptions {
        val method = TrackingViewModel::class.java.declaredMethods.first { it.name.startsWith("resolveDisplayOptions") }
        method.isAccessible = true
        return method.invoke(viewModel, sensorType, sportType) as ZoneDisplayOptions
    }

    private fun setHrvDisplayOptions(options: ZoneDisplayOptions) {
        val field = TrackingViewModel::class.java.getDeclaredField("hrvDisplayOptions")
        field.isAccessible = true
        field.set(viewModel, options)
    }

    @Suppress("UNCHECKED_CAST")
    private fun callApplySensorData(
        currentFields: List<SensorFieldState>,
        allSensorData: List<FilteredSensorData<*>>,
        activityType: ActivityType
    ): List<SensorFieldState> {
        val method = TrackingViewModel::class.java.getDeclaredMethod(
            "applySensorData",
            List::class.java,
            List::class.java,
            ActivityType::class.java
        )
        method.isAccessible = true
        return method.invoke(viewModel, currentFields, allSensorData, activityType) as List<SensorFieldState>
    }

    @Suppress("UNCHECKED_CAST")
    private fun setTuningConfig(config: TuningConfig) {
        val field = TrackingViewModel::class.java.getDeclaredField("_tuningConfig")
        field.isAccessible = true
        val flow = field.get(viewModel) as MutableStateFlow<TuningConfig>
        flow.value = config
    }

    @Test
    fun testGetZoneType_hrvDfaA1_returnsNullForIndependentResolution() {
        // REQ-TRK-061.3: HRV_DFA_A1 does not map to HR ZoneType enum (uses dedicated HRV options)
        assertNull("HRV_DFA_A1 in RUN must return null from getZoneType", callGetZoneType(SensorType.HRV_DFA_A1, BSportType.RUN))
        assertNull("HRV_DFA_A1 in BIKE must return null from getZoneType", callGetZoneType(SensorType.HRV_DFA_A1, BSportType.BIKE))
        assertNull("HRV_DFA_A1 in UNKNOWN must return null from getZoneType", callGetZoneType(SensorType.HRV_DFA_A1, BSportType.UNKNOWN))
    }

    @Test
    fun testResolveDisplayOptions_hrvDfaA1_resolvesDedicatedOptionsAcrossAllSports() {
        // REQ-TRK-061.3: HRV_DFA_A1 resolves dedicated hrvDisplayOptions across all sport types
        val customHrvOptions = ZoneDisplayOptions(
            showBackground = false,
            showLeftBar = true,
            showRightBar = false,
            showTextColor = true,
            showGauge = true
        )
        setHrvDisplayOptions(customHrvOptions)

        val runOptions = callResolveDisplayOptions(SensorType.HRV_DFA_A1, BSportType.RUN)
        assertEquals(customHrvOptions, runOptions)

        val bikeOptions = callResolveDisplayOptions(SensorType.HRV_DFA_A1, BSportType.BIKE)
        assertEquals(customHrvOptions, bikeOptions)

        val otherOptions = callResolveDisplayOptions(SensorType.HRV_DFA_A1, BSportType.UNKNOWN)
        assertEquals(customHrvOptions, otherOptions)
    }

    @Test
    fun applySensorData_dynamicArtifactThreshold_gatesHrvDfaA1() {
        // Set a custom threshold of 3.0% instead of hardcoded 5.0%
        setTuningConfig(TuningConfig(hrvArtifactGateThresholdPercent = 3.0f))

        val dfaHash = Objects.hash(SensorType.HRV_DFA_A1, FilterType.INSTANTANEOUS, 1.0, null)
        val dfaField = SensorFieldState(
            configHash = dfaHash,
            sensorFieldId = 1L,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "DFA a1",
            filterDescription = "",
            value = "--.--",
            units = "",
            zoneColor = Color.Transparent,
            sensorType = SensorType.HRV_DFA_A1,
            isArtifactGated = false
        )

        // Case 1: Artifact rate is 2.5% (<= 3.0%) -> isArtifactGated should remain false
        val lowArtifactData = FilteredSensorData<Double>(
            SensorType.HRV_ARTIFACT_RATE,
            2.5,
            "2.5%",
            null,
            FilterType.INSTANTANEOUS,
            1.0
        )
        val resultLow = callApplySensorData(
            listOf(dfaField),
            listOf(lowArtifactData),
            ActivityType.RUN_SPEED_AND_CADENCE
        )
        assertFalse("Rate 2.5% <= 3.0% must not trigger artifact gate", resultLow.first().isArtifactGated)

        // Case 2: Artifact rate is 3.5% (> 3.0%) -> isArtifactGated must become true
        val highArtifactData = FilteredSensorData<Double>(
            SensorType.HRV_ARTIFACT_RATE,
            3.5,
            "3.5%",
            null,
            FilterType.INSTANTANEOUS,
            1.0
        )
        val resultHigh = callApplySensorData(
            resultLow,
            listOf(highArtifactData),
            ActivityType.RUN_SPEED_AND_CADENCE
        )
        assertTrue("Rate 3.5% > 3.0% must trigger artifact gate", resultHigh.first().isArtifactGated)

        // Case 3: Artifact rate drops back to 1.0% (<= 3.0%) -> isArtifactGated un-gates
        val droppedArtifactData = FilteredSensorData<Double>(
            SensorType.HRV_ARTIFACT_RATE,
            1.0,
            "1.0%",
            null,
            FilterType.INSTANTANEOUS,
            1.0
        )
        val resultDropped = callApplySensorData(
            resultHigh,
            listOf(droppedArtifactData),
            ActivityType.RUN_SPEED_AND_CADENCE
        )
        assertFalse("Rate 1.0% <= 3.0% must clear artifact gate", resultDropped.first().isArtifactGated)
    }
}
