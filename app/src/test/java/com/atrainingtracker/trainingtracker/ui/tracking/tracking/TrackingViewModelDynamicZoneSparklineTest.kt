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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityStatus
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Objects

/**
 * Unit tests verifying Dynamic Zone sparkline threshold projection, POWER_TO_WEIGHT exclusion,
 * and HRV DFA alpha 1 stream-quality suppression in [TrackingViewModel]
 * (TST-TRK-082.3, REQ-TRK-082.2, ATT-7761).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingViewModelDynamicZoneSparklineTest {

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
        activityType: ActivityType
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
    fun resolveSparklineZoneThresholds_hrRunAndBike_projectsFourCutoffsAndFiveZones() {
        // TST-TRK-082
        val runThresholds = viewModel.resolveSparklineZoneThresholds(
            sensorType = SensorType.HR,
            sportType = BSportType.RUN,
            graphColor = "DYNAMIC_ZONE"
        )
        assertNotNull(runThresholds)
        assertEquals(listOf(132f, 145f, 158f, 171f), runThresholds!!.cutoffs)
        assertEquals(
            listOf(TTColor.Zone1, TTColor.Zone2, TTColor.Zone3, TTColor.Zone4, TTColor.Zone5),
            runThresholds.zoneColors
        )
        assertFalse(runThresholds.invertedPolarity)

        val bikeThresholds = viewModel.resolveSparklineZoneThresholds(
            sensorType = SensorType.HR,
            sportType = BSportType.BIKE,
            graphColor = "DYNAMIC_ZONE"
        )
        assertNotNull(bikeThresholds)
        assertEquals(listOf(127f, 140f, 153f, 166f), bikeThresholds!!.cutoffs)
    }

    @Test
    fun resolveSparklineZoneThresholds_powerFamilyAndPowerToWeightGuard() {
        // TST-TRK-082
        val wattTypes = listOf(
            SensorType.POWER,
            SensorType.NORMALIZED_POWER,
            SensorType.POWER_5S,
            SensorType.POWER_1M,
            SensorType.POWER_5M,
            SensorType.POWER_20M,
            SensorType.POWER_PEAK_5S,
            SensorType.POWER_PEAK_1M,
            SensorType.POWER_PEAK_5M,
            SensorType.POWER_PEAK_20M
        )
        for (sensorType in wattTypes) {
            val thresholds = viewModel.resolveSparklineZoneThresholds(
                sensorType = sensorType,
                sportType = BSportType.BIKE,
                graphColor = "DYNAMIC_ZONE"
            )
            assertNotNull("Expected 7-zone thresholds for $sensorType", thresholds)
            assertEquals(listOf(149f, 203f, 243f, 284f, 324f, 405f), thresholds!!.cutoffs)
            assertEquals(7, thresholds.zoneColors.size)
            assertEquals(TTColor.Zone7, thresholds.zoneColors.last())
        }

        // POWER_TO_WEIGHT (W/kg) MUST NEVER receive raw Watt cutoffs
        val wkgThresholds = viewModel.resolveSparklineZoneThresholds(
            sensorType = SensorType.POWER_TO_WEIGHT,
            sportType = BSportType.BIKE,
            graphColor = "DYNAMIC_ZONE"
        )
        assertNull("POWER_TO_WEIGHT must be excluded from Watt cutoffs", wkgThresholds)
    }

    @Test
    fun resolveSparklineZoneThresholds_dfaAlpha1PolarityAndStreamSuppression() {
        // TST-TRK-082
        val tuning = TuningConfig(dfaA1ThresholdT1 = 0.78f, dfaA1ThresholdT2 = 0.52f)

        val valid = viewModel.resolveSparklineZoneThresholds(
            sensorType = SensorType.HRV_DFA_A1,
            sportType = BSportType.RUN,
            graphColor = "DYNAMIC_ZONE",
            tuningConfig = tuning,
            signalQuality = HrvSignalQualityStatus.VALID
        )
        assertNotNull(valid)
        assertEquals(listOf(0.52f, 0.78f), valid!!.cutoffs)
        assertEquals(listOf(TTColor.Zone4, TTColor.Zone3, TTColor.Zone2), valid.zoneColors)
        assertTrue(valid.invertedPolarity)

        // Suppressed for SMOOTHED_STREAM and QUANTIZED_SYNTHETIC
        assertNull(
            viewModel.resolveSparklineZoneThresholds(
                sensorType = SensorType.HRV_DFA_A1,
                sportType = BSportType.RUN,
                graphColor = "DYNAMIC_ZONE",
                tuningConfig = tuning,
                signalQuality = HrvSignalQualityStatus.SMOOTHED_STREAM
            )
        )
        assertNull(
            viewModel.resolveSparklineZoneThresholds(
                sensorType = SensorType.HRV_DFA_A1,
                sportType = BSportType.RUN,
                graphColor = "DYNAMIC_ZONE",
                tuningConfig = tuning,
                signalQuality = HrvSignalQualityStatus.QUANTIZED_SYNTHETIC
            )
        )
    }

    @Test
    fun resolveSparklineZoneThresholds_nonDynamicModeReturnsNull() {
        // TST-TRK-082
        assertNull(
            viewModel.resolveSparklineZoneThresholds(
                sensorType = SensorType.HR,
                sportType = BSportType.RUN,
                graphColor = "CRIMSON"
            )
        )
        assertNull(
            viewModel.resolveSparklineZoneThresholds(
                sensorType = SensorType.HR,
                sportType = BSportType.RUN,
                graphColor = "GLOBAL_DEFAULT"
            )
        )
    }

    @Test
    fun applySensorData_projectsThresholdsForDynamicZoneTilesOnly() {
        // TST-TRK-082
        val hrFieldId = 2001L
        val wkgFieldId = 2002L
        val fixedFieldId = 2003L

        every { banalServiceRepo.getSparklineSamples(hrFieldId) } returns listOf(128f, 150f, 174f)
        every { banalServiceRepo.getSparklineSamples(wkgFieldId) } returns listOf(2.5f, 3.8f, 4.5f)
        every { banalServiceRepo.getSparklineSamples(fixedFieldId) } returns listOf(200f, 250f, 300f)

        val hrField = SensorFieldState(
            configHash = Objects.hash(SensorType.HR, FilterType.INSTANTANEOUS, 0.0, null),
            sensorFieldId = hrFieldId,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "HR",
            filterDescription = "Inst.",
            value = "174",
            units = "bpm",
            zoneColor = TTColor.Zone5,
            sensorType = SensorType.HR,
            showGraph = true,
            graphColor = "DYNAMIC_ZONE"
        )
        val wkgField = SensorFieldState(
            configHash = Objects.hash(SensorType.POWER_TO_WEIGHT, FilterType.INSTANTANEOUS, 0.0, null),
            sensorFieldId = wkgFieldId,
            rowNr = 1,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "W/kg",
            filterDescription = "Inst.",
            value = "4.5",
            units = "W/kg",
            zoneColor = Color.Transparent,
            sensorType = SensorType.POWER_TO_WEIGHT,
            showGraph = true,
            graphColor = "DYNAMIC_ZONE"
        )
        val fixedPowerField = SensorFieldState(
            configHash = Objects.hash(SensorType.POWER, FilterType.INSTANTANEOUS, 0.0, null),
            sensorFieldId = fixedFieldId,
            rowNr = 2,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Power",
            filterDescription = "Inst.",
            value = "300",
            units = "W",
            zoneColor = TTColor.Zone4,
            sensorType = SensorType.POWER,
            showGraph = true,
            graphColor = "CRIMSON"
        )

        val result = invokeApplySensorData(
            currentFields = listOf(hrField, wkgField, fixedPowerField),
            allSensorData = emptyList(),
            activityType = ActivityType.BIKE_POWER
        )

        assertNotNull("HR DYNAMIC_ZONE must project thresholds", result[0].sparklineZoneThresholds)
        assertEquals(4, result[0].sparklineZoneThresholds!!.cutoffs.size)
        assertNull("POWER_TO_WEIGHT must stay null", result[1].sparklineZoneThresholds)
        assertNull("Fixed color tile must not attach zone thresholds", result[2].sparklineZoneThresholds)
    }
}
