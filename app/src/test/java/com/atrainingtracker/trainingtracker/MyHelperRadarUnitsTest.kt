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

package com.atrainingtracker.trainingtracker

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.SimpleSensorData
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.banalservice.ui.devices.GetMergedDevicesUseCase
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldConfig
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModel
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit and caller-integration tests verifying [MyHelper.getUnitsId] and
 * [MyHelper.getShortUnitsId] unit resolution for [SensorType.RADAR_APPROACH_SPEED_mps]
 * across Metric and Imperial modes while preserving storage/export invariants
 * (ATT-8676, REQ-TRK-088, TST-TRK-088).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyHelperRadarUnitsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        TrainingApplication.setUnitForTesting(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun getUnitsId_radarApproachSpeed_metric_matchesSpeedAndResolvesKmh() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)

        val unitId = MyHelper.getUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)
        assertEquals(R.string.units_speed_metric, unitId)
        assertEquals(MyHelper.getUnitsId(SensorType.SPEED_mps), unitId)
        assertNotEquals(R.string.units_speed_basic, unitId)
        assertEquals("km/h", application.getString(unitId))
    }

    @Test
    fun getUnitsId_radarApproachSpeed_imperial_matchesSpeedAndResolvesMilePerHour() {
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)

        val unitId = MyHelper.getUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)
        assertEquals(R.string.units_speed_imperial, unitId)
        assertEquals(MyHelper.getUnitsId(SensorType.SPEED_mps), unitId)
        assertNotEquals(R.string.units_speed_basic, unitId)
        assertEquals("mile/h", application.getString(unitId))
    }

    @Test
    fun getShortUnitsId_radarApproachSpeed_metric_matchesSpeedAndResolvesKmh() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)

        val shortUnitId = MyHelper.getShortUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)
        assertEquals(R.string.units_speed_short_metric, shortUnitId)
        assertEquals(MyHelper.getShortUnitsId(SensorType.SPEED_mps), shortUnitId)
        assertNotEquals(R.string.units_speed_basic, shortUnitId)
        assertEquals("km/h", application.getString(shortUnitId))
    }

    @Test
    fun getShortUnitsId_radarApproachSpeed_imperial_matchesSpeedAndResolvesMiPerHour() {
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)

        val shortUnitId = MyHelper.getShortUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)
        assertEquals(R.string.units_speed_short_imperial, shortUnitId)
        assertEquals(MyHelper.getShortUnitsId(SensorType.SPEED_mps), shortUnitId)
        assertNotEquals(R.string.units_speed_basic, shortUnitId)
        assertEquals("mi/h", application.getString(shortUnitId))
    }

    @Test
    @Suppress("DEPRECATION")
    fun radarApproachSpeed_preservesInternalUnitDeprecatedUnitIdAndDistanceFallback() {
        assertEquals("m/s", SensorType.RADAR_APPROACH_SPEED_mps.internalUnit)
        assertEquals(R.string.units_speed_basic, SensorType.RADAR_APPROACH_SPEED_mps.unitId)
        assertEquals("m/s", application.getString(SensorType.RADAR_APPROACH_SPEED_mps.unitId))

        // Out-of-scope radar distance sensors preserve units_distance_basic ("m") fallback
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        assertEquals(R.string.units_distance_basic, MyHelper.getUnitsId(SensorType.RADAR_CLOSEST_DISTANCE_m))
        assertEquals(R.string.units_distance_basic, MyHelper.getShortUnitsId(SensorType.RADAR_CLOSEST_DISTANCE_m))
        assertEquals(R.string.units_distance_basic, MyHelper.getUnitsId(SensorType.RADAR_PASSING_DISTANCE_m))
        assertEquals(R.string.units_distance_basic, MyHelper.getShortUnitsId(SensorType.RADAR_PASSING_DISTANCE_m))

        // SpeedFormatter display units match MyHelper.getUnitsId() in both unit systems
        val formatter = SpeedFormatter()
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        assertTrue(
            formatter.format_with_units(10.0)
                .endsWith(application.getString(MyHelper.getUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)))
        )
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        assertTrue(
            formatter.format_with_units(10.0)
                .endsWith(application.getString(MyHelper.getUnitsId(SensorType.RADAR_APPROACH_SPEED_mps)))
        )
    }

    @Test
    fun trackingViewModel_radarApproachSpeedTile_resolvesMetricAndImperialShortUnits() = runTest {
        val radarConfig = SensorFieldConfig(
            sensorFieldId = 8676L,
            rowNr = 1,
            colNr = 1,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.RADAR_APPROACH_SPEED_mps,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = -1L
        )
        val mockTrackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
        val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
        val mockLiveSegmentsRepo = mockk<LiveSegmentsRepository>(relaxed = true)
        val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)

        every { mockLiveSegmentsRepo.liveSegments } returns MutableStateFlow(emptyList<LiveSegment>())
        every { mockBanalRepo.bSportType } returns MutableStateFlow(BSportType.BIKE)
        every { mockBanalRepo.activityType } returns MutableStateFlow(ActivityType.getDefaultActivityType())
        every { mockBanalRepo.currentTrack } returns MutableStateFlow(emptyList<LatLng>())
        every { mockBanalRepo.currentSpeed } returns MutableStateFlow<Double?>(null)
        every { mockBanalRepo.currentBearing } returns MutableStateFlow<Double?>(null)
        every { mockBanalRepo.allFilteredSensorData } returns MutableStateFlow(emptyList<FilteredSensorData<*>>())
        every { mockBanalRepo.currentPathPoints } returns MutableStateFlow(emptyList<PathPoint>())
        every { mockRoutesRepo.allRoutes } returns MutableStateFlow(emptyList<RouteWithPath>())
        coEvery { mockTrackingViewsRepo.getActivityTypeForView(any()) } returns ActivityType.getDefaultActivityType()
        every { mockTrackingViewsRepo.getTrackingViewInfoFlow(any()) } returns flowOf(null)
        every { mockTrackingViewsRepo.getSectionDividersFlow(any()) } returns flowOf(emptyList())
        every { mockTrackingViewsRepo.getSensorFieldConfigsForView(any()) } returns flowOf(listOf(radarConfig))

        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        val metricVm = TrackingViewModel(
            application = application,
            trackingViewsRepository = mockTrackingViewsRepo,
            banalServiceRepository = mockBanalRepo,
            liveSegmentsRepository = mockLiveSegmentsRepo,
            routesRepository = mockRoutesRepo,
            viewId = 1L,
            defaultDispatcher = testDispatcher,
            workoutRepository = mockWorkoutRepo
        )
        advanceUntilIdle()
        assertEquals("km/h", metricVm.uiState.value.fields.single().units)

        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        val imperialVm = TrackingViewModel(
            application = application,
            trackingViewsRepository = mockTrackingViewsRepo,
            banalServiceRepository = mockBanalRepo,
            liveSegmentsRepository = mockLiveSegmentsRepo,
            routesRepository = mockRoutesRepo,
            viewId = 1L,
            defaultDispatcher = testDispatcher,
            workoutRepository = mockWorkoutRepo
        )
        advanceUntilIdle()
        assertEquals("mi/h", imperialVm.uiState.value.fields.single().units)
    }

    @Test
    fun getMergedDevicesUseCase_radarApproachSpeed_resolvesMetricAndImperialUnits() = runTest {
        val dbRepo = mockk<DeviceDataRepository>()
        val serviceRepo = mockk<BANALServiceRepository>()

        val radarDevice = DeviceUiData(
            id = 42L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.BIKE_RADAR,
            lastSeen = "now",
            manufacturer = "Garmin",
            deviceName = "Varia",
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 90,
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = true,
            mainValue = null,
            allValues = null
        )
        val telemetry = DeviceTelemetry(
            deviceId = 42L,
            mainValue = SimpleSensorData("36.0", SensorType.RADAR_APPROACH_SPEED_mps),
            allValues = emptyList(),
            batteryPercentage = 90
        )

        every { dbRepo.allDevices } returns MutableStateFlow(listOf(radarDevice))
        every { serviceRepo.allActiveDevicesTelemetry } returns MutableStateFlow(listOf(telemetry))
        every { serviceRepo.newlyFoundDevicesIds } returns MutableStateFlow(emptyList<Long>())

        val useCase = GetMergedDevicesUseCase(dbRepo, serviceRepo, application)

        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        val metricMerged = useCase.mergedDevices.first().single()
        assertEquals("36.0 km/h", metricMerged.mainValue)

        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        val imperialMerged = useCase.mergedDevices.first().single()
        assertEquals("36.0 mile/h", imperialMerged.mainValue)
    }
}
