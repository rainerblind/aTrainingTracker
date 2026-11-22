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

package com.atrainingtracker.banalservice.filters

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.ProxySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldConfig
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral verification suite for `ATT-8126` (`REQ-UI-2800.3`, `TST-UI-2800.3`):
 * - `ProxySensor.addSensorListener` delivers strictly ONE initial value (never leading null) & handles concurrent source swaps
 * - `FilterManager.rebindFilter` ingests current value strictly ONCE into moving-average / smoothing filters
 * - End-to-end `deviceId` filter keying survives device renames across `FilterData`, `FilterManager`, `MyFilter`, `TrackingViewModel` & sparklines
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProxySensorAndFilterManagerIdKeyTest {

    private lateinit var context: Context
    private lateinit var deviceManager: DeviceManager
    private lateinit var sensorManager: MySensorManager
    private lateinit var filterManager: FilterManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        deviceManager = mockk(relaxed = true)
        sensorManager = mockk(relaxed = true)
        filterManager = FilterManager(context, deviceManager, sensorManager)
        BANALServiceRepository.resetForTesting(null)
    }

    @After
    fun tearDown() {
        filterManager.shutDown()
        BANALServiceRepository.resetForTesting(null)
        Dispatchers.resetMain()
    }

    @Test
    fun testProxySensor_addSensorListener_deliversSingleInitialValueWithoutLeadingNull_tstUi2800_3() {
        val mockDevice = mockk<MyDevice>(relaxed = true) { every { name } returns "HR_Device" }
        val source = MySensor<Double>(mockDevice, SensorType.HR)
        source.newValue(150.0)

        val proxy = ProxySensor<Double>(mockDevice, SensorType.HR, source)

        val received = mutableListOf<Double?>()
        proxy.addSensorListener { value -> received.add(value as? Double) }

        assertEquals("Bound ProxySensor must deliver current value strictly once without leading null", listOf(150.0), received)

        // Unbound ProxySensor delivers null strictly once
        val unboundProxy = ProxySensor<Double>(mockDevice, SensorType.HR, null)
        val unboundReceived = mutableListOf<Double?>()
        unboundProxy.addSensorListener { value -> unboundReceived.add(value as? Double) }
        assertEquals(listOf<Double?>(null), unboundReceived)
    }

    @Test
    fun testProxySensor_addSensorListener_concurrentSwapDuringAttachReattachesCleanly_tstUi2800_3() {
        val mockDevice = mockk<MyDevice>(relaxed = true)
        val newSource = MySensor<Double>(mockDevice, SensorType.POWER)
        newSource.newValue(275.0)

        var swapped = false
        lateinit var proxy: ProxySensor<Double>
        val swappingOldSource = object : MySensor<Double>(mockDevice, SensorType.POWER) {
            override fun addSensorListener(sensorListener: SensorListener<*>?) {
                super.addSensorListener(sensorListener)
                if (!swapped) {
                    swapped = true
                    // Simulate another thread swapping source sensor during addSensorListener (outside mSourceLock!)
                    proxy.setSourceSensor(newSource)
                }
            }
        }
        swappingOldSource.newValue(100.0)
        proxy = ProxySensor(mockDevice, SensorType.POWER, swappingOldSource)

        val received = mutableListOf<Double?>()
        proxy.addSensorListener { value -> received.add(value as? Double) }

        // After swap resolves, newSource emissions must be received and oldSource emissions must be ignored
        received.clear()
        swappingOldSource.newValue(50.0)
        newSource.newValue(310.0)
        assertEquals(listOf(310.0), received)
    }

    @Test
    fun testFilterManager_rebindFilter_ingestsInitialValueStrictlyOnceInMovingAverage_tstUi2800_3() {
        val device = mockk<MyDevice>(relaxed = true) { every { name } returns "PowerMeter" }
        val oldSensor = MySensor<Double>(device, SensorType.POWER)
        val newSensor = MySensor<Double>(device, SensorType.POWER)
        newSensor.newValue(200.0)

        val avgFilter = NumberedMovingAverageFilter("PowerMeter", SensorType.POWER, 5)
        filterManager.rebindFilter(avgFilter, oldSensor, newSensor)
        newSensor.newValue(100.0)

        // Strictly [200.0, 100.0] -> avg 150.0 (double-push bug would produce [200.0, 200.0, 100.0] -> 166.67)
        assertEquals(150.0, avgFilter.filteredValue?.toDouble() ?: -1.0, 0.001)
    }

    @Test
    fun testEndToEndDeviceRenameSurvival_filterManagerViewModelAndSparklinesStayLive_tstUi2800_3() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        BANALServiceRepository.setDispatcherForTesting(testDispatcher)

        // 1. Create FilterData pinned by deviceId = 42L ("Old HR")
        val filterData = FilterData("Old HR", SensorType.HR, FilterType.INSTANTANEOUS, 1.0, 42L)
        assertTrue(filterData.hashKey.startsWith("id:42-"))

        var currentDeviceName = "Old HR"
        val device = mockk<MyDevice>(relaxed = true) {
            every { deviceId } returns 42L
            every { name } answers { currentDeviceName }
        }
        val hrSensor = MySensor<Int>(device, SensorType.HR)
        every { device.getSensor(SensorType.HR) } returns hrSensor
        every { deviceManager.getMyDeviceById(42L) } returns device

        filterManager.createFilter(filterData)
        hrSensor.newValue(140)
        val initialData = filterManager.getFilteredSensorData(filterData)!!
        assertEquals(140, initialData.value)
        assertEquals(42L, initialData.deviceId)
        assertEquals("Old HR", initialData.deviceName)

        // 2. Rename device 42 to "New HR" and trigger rebindExistingFilters()
        currentDeviceName = "New HR"
        filterManager.rebindExistingFilters()
        hrSensor.newValue(158)

        val renamedData = filterManager.getFilteredSensorData(filterData)!!
        assertEquals(158, renamedData.value)
        assertEquals(42L, renamedData.deviceId)
        assertEquals("New HR", renamedData.deviceName)
        // Must resolve via getMyDeviceById directly WITHOUT falling back to getMyDeviceByName
        verify(exactly = 0) { deviceManager.getMyDeviceByName(any()) }

        // 3. Verify BANALServiceRepository sparkline + TrackingViewModel tile stay live after rename
        val repo = BANALServiceRepository.getInstance(context)
        repo.registerSparklineBuffer(
            sensorFieldId = 55L,
            horizonSeconds = 60,
            sensorType = SensorType.HR,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceName = "Old HR",
            sourceDeviceId = 42L
        )
        assertTrue(repo.updateSparklineBuffers(listOf(renamedData)))
        assertEquals(listOf(158f), repo.getSparklineSamples(55L))

        val allSensorFlow = MutableStateFlow<List<FilteredSensorData<*>>>(listOf(renamedData))
        val mockRepo = mockk<BANALServiceRepository>(relaxed = true)
        every { mockRepo.allFilteredSensorData } returns allSensorFlow
        every { mockRepo.activityType } returns MutableStateFlow(ActivityType.BIKE_POWER)
        every { mockRepo.bSportType } returns MutableStateFlow(BSportType.BIKE)
        every { mockRepo.getSparklineSamples(55L) } returns listOf(158f)
        every { mockRepo.getSparklineSpanSeconds(55L) } returns 1

        val trackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
        val routesRepo = mockk<RoutesRepository>(relaxed = true)
        val liveSegmentsRepo = mockk<LiveSegmentsRepository>(relaxed = true)
        val workoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { liveSegmentsRepo.liveSegments } returns MutableStateFlow(emptyList())
        every { trackingViewsRepo.getTrackingViewInfoFlow(1L) } returns flowOf(
            TrackingViewInfo(
                tabViewId = 1L,
                name = "Ride",
                showMap = false,
                showLapButton = true,
                showLiveSegments = false,
                showElevationProfile = false
            )
        )
        every { trackingViewsRepo.getSectionDividersFlow(1L) } returns flowOf(emptyList())
        coEvery { trackingViewsRepo.getActivityTypeForView(1L) } returns ActivityType.BIKE_POWER
        every { trackingViewsRepo.getSensorFieldConfigsForView(1L) } returns flowOf(
            listOf(
                SensorFieldConfig(
                    sensorFieldId = 55L,
                    rowNr = 1,
                    colNr = 1,
                    viewSize = ViewSize.NORMAL,
                    sensorType = SensorType.HR,
                    filterType = FilterType.INSTANTANEOUS,
                    filterConstant = 1.0,
                    sourceDeviceId = 42L,
                    sourceDeviceName = "Old HR", // Still holding pre-rename label from view config
                    showGraph = true
                )
            )
        )

        val vm = TrackingViewModel(
            application = context.applicationContext as Application,
            viewId = 1L,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = mockRepo,
            routesRepository = routesRepo,
            liveSegmentsRepository = liveSegmentsRepo,
            workoutRepository = workoutRepo
        )
        runCurrent()

        val field = vm.uiState.value.fields.first { it.sensorFieldId == 55L }
        assertEquals("158", field.value)
        assertEquals(listOf(158f), field.sparklineSamples)
    }

    @Test
    fun testResolveSensor_whenDeviceFoundByIdButSensorMissing_doesNotFallbackToDeviceName_tstUi2800_3() {
        val deviceById = mockk<MyDevice>(relaxed = true) {
            every { deviceId } returns 42L
            every { getSensor(SensorType.POWER) } returns null
        }
        val otherDeviceByName = mockk<MyDevice>(relaxed = true)
        val otherSensor = MySensor<Double>(otherDeviceByName, SensorType.POWER)
        every { otherDeviceByName.getSensor(SensorType.POWER) } returns otherSensor

        every { deviceManager.getMyDeviceById(42L) } returns deviceById
        every { deviceManager.getMyDeviceByName("SameName") } returns otherDeviceByName

        val filterData = FilterData("SameName", SensorType.POWER, FilterType.INSTANTANEOUS, 1.0, 42L)
        assertNull(filterManager.resolveSensor(filterData))
        verify(exactly = 0) { deviceManager.getMyDeviceByName(any()) }
    }
}
