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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [ControlTrackingViewModel.hasPairedRemoteDevices] reactive state emissions,
 * cold-start seeding, and device filtering logic (REQ-UI-259, TST-UI-218, ATT-2057).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlTrackingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var application: Application
    private lateinit var mockDeviceDataRepository: DeviceDataRepository
    private lateinit var mockBanalRepo: BANALServiceRepository
    private lateinit var mockDevicesDb: DevicesDatabaseManager
    private lateinit var mockWorkoutSummariesDb: WorkoutSummariesDatabaseManager
    private val allDevicesFlow = MutableStateFlow<List<DeviceUiData>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockDeviceDataRepository = mockk(relaxed = true)
        mockkObject(DeviceDataRepository.Companion)
        every { DeviceDataRepository.getInstance(any()) } returns mockDeviceDataRepository
        every { mockDeviceDataRepository.allDevices } returns allDevicesFlow

        mockBanalRepo = mockk(relaxed = true)
        mockkObject(BANALServiceRepository.Companion)
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo
        every { mockBanalRepo.activeRemoteDevicesIds } returns MutableStateFlow(emptyList())
        every { mockBanalRepo.trackingMode } returns MutableLiveData(TrackingMode.IDLE)
        every { mockBanalRepo.activeSensors } returns MutableStateFlow(emptySet())
        every { mockBanalRepo.bSportType } returns MutableStateFlow(BSportType.RUN)

        mockDevicesDb = mockk(relaxed = true)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb
        every { mockDevicesDb.hasPairedRemoteDevices() } returns false

        mockWorkoutSummariesDb = mockk(relaxed = true)
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockWorkoutSummariesDb
        every { mockWorkoutSummariesDb.hasUnfinishedWorkout() } returns false

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setPackage(any()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().action } returns TrainingApplication.REQUEST_START_TRACKING
        every { anyConstructed<android.content.Intent>().`package` } returns "com.atrainingtracker"

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns false

        application = mockk(relaxed = true)
        every { application.packageName } returns "com.atrainingtracker"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    private fun createDevice(
        id: Long,
        protocol: Protocol,
        deviceType: DeviceType,
        isPaired: Boolean
    ): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = protocol,
            deviceType = deviceType,
            lastSeen = null,
            manufacturer = "Test Manufacturer",
            deviceName = "Test Device $id",
            isPaired = isPaired,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 100,
            linkedSportTypes = emptyList(),
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = false,
            mainValue = null,
            allValues = null
        )
    }

    @Test
    fun testHasPairedRemoteDevices_whenOnlySmartphoneDevicesExist_emitsFalse() = runTest {
        val phoneGps = createDevice(1L, Protocol.SMARTPHONE, DeviceType.SPEED_AND_LOCATION_GPS, isPaired = true)
        val phoneBattery = createDevice(2L, Protocol.SMARTPHONE, DeviceType.BATTERY, isPaired = true)
        allDevicesFlow.value = listOf(phoneGps, phoneBattery)

        val viewModel = ControlTrackingViewModel(application)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.hasPairedRemoteDevices.collect()
        }

        assertFalse(
            "Expected hasPairedRemoteDevices to be false when only smartphone devices exist",
            viewModel.hasPairedRemoteDevices.value
        )
    }

    @Test
    fun testHasPairedRemoteDevices_whenRealPairedDeviceAdded_emitsTrue() = runTest {
        allDevicesFlow.value = emptyList()
        val viewModel = ControlTrackingViewModel(application)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.hasPairedRemoteDevices.collect()
        }

        assertFalse(viewModel.hasPairedRemoteDevices.value)

        val pairedBleHrm = createDevice(10L, Protocol.BLUETOOTH_LE, DeviceType.HRM, isPaired = true)
        allDevicesFlow.value = listOf(pairedBleHrm)

        assertTrue(
            "Expected hasPairedRemoteDevices to be true after a paired Bluetooth LE sensor is registered",
            viewModel.hasPairedRemoteDevices.value
        )

        val pairedAntPower = createDevice(11L, Protocol.ANT_PLUS, DeviceType.BIKE_POWER, isPaired = true)
        allDevicesFlow.value = listOf(pairedBleHrm, pairedAntPower)

        assertTrue(
            "Expected hasPairedRemoteDevices to remain true when ANT+ sensor is also paired",
            viewModel.hasPairedRemoteDevices.value
        )
    }

    @Test
    fun testHasPairedRemoteDevices_whenRealDeviceIsUnpaired_emitsFalse() = runTest {
        val unpairedBleSensor = createDevice(20L, Protocol.BLUETOOTH_LE, DeviceType.BIKE_SPEED, isPaired = false)
        val unpairedAntSensor = createDevice(21L, Protocol.ANT_PLUS, DeviceType.BIKE_CADENCE, isPaired = false)
        allDevicesFlow.value = listOf(unpairedBleSensor, unpairedAntSensor)

        val viewModel = ControlTrackingViewModel(application)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.hasPairedRemoteDevices.collect()
        }

        assertFalse(
            "Expected hasPairedRemoteDevices to be false when all remote devices are unpaired (isPaired == false)",
            viewModel.hasPairedRemoteDevices.value
        )
    }

    @Test
    fun testHasPairedRemoteDevices_initialValueSeededFromDatabase() {
        every { mockDevicesDb.hasPairedRemoteDevices() } returns true

        val viewModel = ControlTrackingViewModel(application)

        assertTrue(
            "Expected initialValue of hasPairedRemoteDevices to match synchronous database query result",
            viewModel.hasPairedRemoteDevices.value
        )
    }

    @Test
    fun testOnStartTracking_emitsShowRecoveryDialog_whenUnfinishedWorkoutExistsAndNotTracking() = runTest {
        every { mockWorkoutSummariesDb.hasUnfinishedWorkout() } returns true
        every { TrainingApplication.isTracking() } returns false

        val viewModel = ControlTrackingViewModel(application)
        val emittedEvents = mutableListOf<ControlNavigation>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigationEvent.collect { emittedEvents.add(it) }
        }

        viewModel.onStartTracking()

        assertTrue(
            "Expected ControlNavigation.ShowRecoveryDialog when unfinished workout exists and not tracking",
            emittedEvents.contains(ControlNavigation.ShowRecoveryDialog)
        )
        verify(exactly = 0) { application.sendBroadcast(any()) }
        job.cancel()
    }

    @Test
    fun testOnStartTracking_sendsBroadcast_whenNoUnfinishedWorkout() = runTest {
        every { mockWorkoutSummariesDb.hasUnfinishedWorkout() } returns false
        every { TrainingApplication.isTracking() } returns false

        val viewModel = ControlTrackingViewModel(application)
        val emittedEvents = mutableListOf<ControlNavigation>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigationEvent.collect { emittedEvents.add(it) }
        }

        viewModel.onStartTracking()

        assertFalse(
            "Expected NO ShowRecoveryDialog event when no unfinished workout exists",
            emittedEvents.contains(ControlNavigation.ShowRecoveryDialog)
        )
        val slot = slot<android.content.Intent>()
        verify(exactly = 1) { application.sendBroadcast(capture(slot)) }
        org.junit.Assert.assertEquals(TrainingApplication.REQUEST_START_TRACKING, slot.captured.action)
        job.cancel()
    }

    @Test
    fun testOnStartTracking_sendsBroadcast_whenAlreadyTracking() = runTest {
        every { mockWorkoutSummariesDb.hasUnfinishedWorkout() } returns true
        every { TrainingApplication.isTracking() } returns true

        val viewModel = ControlTrackingViewModel(application)
        val emittedEvents = mutableListOf<ControlNavigation>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigationEvent.collect { emittedEvents.add(it) }
        }

        viewModel.onStartTracking()

        assertFalse(
            "Expected NO ShowRecoveryDialog event when already tracking",
            emittedEvents.contains(ControlNavigation.ShowRecoveryDialog)
        )
        val slot = slot<android.content.Intent>()
        verify(exactly = 1) { application.sendBroadcast(capture(slot)) }
        org.junit.Assert.assertEquals(TrainingApplication.REQUEST_START_TRACKING, slot.captured.action)
        job.cancel()
    }

    @Test
    fun testRemoteDevices_filtersOutOrphanedOrDeletedConnectedSensors() = runTest {
        // Given: activeRemoteDevicesIds has [10L, 20L, 30L] where 30L was deleted / not in DB
        val activeIdsFlow = MutableStateFlow(listOf(10L, 20L, 30L))
        every { mockBanalRepo.activeRemoteDevicesIds } returns activeIdsFlow

        val dbDevices = listOf(
            createDeviceUiData(10L, DeviceType.HRM, "Heart Rate Monitor"),
            createDeviceUiData(20L, DeviceType.BIKE_POWER, "Power Meter")
        )
        allDevicesFlow.value = dbDevices

        val viewModel = ControlTrackingViewModel(application)
        val emittedDevices = mutableListOf<List<RemoteDeviceUIData>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.remoteDevices.collect { emittedDevices.add(it) }
        }

        val latest = emittedDevices.last()
        org.junit.Assert.assertEquals("Expected exactly 2 valid devices", 2, latest.size)
        org.junit.Assert.assertEquals(10L, latest[0].id)
        org.junit.Assert.assertEquals(20L, latest[1].id)
        assertFalse(
            "Expected zero DUMMY or 'New Sensor' items for orphaned device 30L",
            latest.any { it.deviceType == DeviceType.DUMMY || it.name == "New Sensor" || it.id == 30L }
        )

        job.cancel()
    }

    private fun createDeviceUiData(id: Long, deviceType: DeviceType, name: String): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = deviceType,
            lastSeen = "Now",
            manufacturer = "Polar",
            deviceName = name,
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 90,
            linkedSportTypes = emptyList(),
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
    }
}
