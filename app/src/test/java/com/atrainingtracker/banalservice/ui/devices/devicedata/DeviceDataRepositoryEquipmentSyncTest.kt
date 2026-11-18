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

package com.atrainingtracker.banalservice.ui.devices.devicedata

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.util.Log
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceViewModel
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying reactive equipment-to-sensor synchronization in DeviceDataRepository
 * and fresh snapshot loading in EditDeviceViewModel (REQ-UI-257, TST-UI-216.1, TST-UI-216.3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDataRepositoryEquipmentSyncTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockDevicesDb: DevicesDatabaseManager
    private lateinit var mockDiscoveryManager: EquipmentAndSportTypeDiscoveryManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        DeviceDataRepository.resetForTesting(null)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockApplication = mockk(relaxed = true)

        mockDevicesDb = mockk(relaxed = true)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        mockDiscoveryManager = mockk(relaxed = true)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(mockDiscoveryManager)

        mockkConstructor(EquipmentDbHelper::class)
        every { anyConstructed<EquipmentDbHelper>().getLinkedEquipmentFromDeviceId(any()) } returns emptyList()
        every { anyConstructed<EquipmentDbHelper>().getEquipment(any()) } returns emptyList()

        mockkConstructor(RawDeviceDataProvider::class)
    }

    @After
    fun tearDown() {
        DeviceDataRepository.resetForTesting(null)
        EquipmentRepository.resetForTesting(null)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createSampleDeviceUiData(id: Long, linkedEquipment: List<String>): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.HRM,
            lastSeen = "Now",
            manufacturer = "Polar",
            deviceName = "Polar H10",
            isPaired = true,
            linkedEquipment = linkedEquipment,
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
            isConnected = false,
            mainValue = null,
            allValues = null
        )
    }

    /**
     * TST-UI-216.1: When EquipmentRepository emits equipmentLinksChanged with a deviceId,
     * DeviceDataRepository invokes refreshDeviceFromDb(deviceId) to refresh the sensor record.
     */
    @Test
    fun testEquipmentLinksChanged_notifiesDeviceDataRepository_refreshesAffectedDevice() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true
        every { mockDevicesDb.getDeviceCursor(42L) } returns mockCursor

        val mockRawData = DeviceRawData(
            id = 42L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.HRM,
            lastSeen = "Now",
            batteryPercentage = 90,
            manufacturer = "Polar",
            deviceName = "Polar H10",
            isPaired = true,
            calibrationValue = null,
            linkedEquipment = listOf("Gravel Bike"),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            linkedSportTypes = emptyList()
        )
        every { anyConstructed<RawDeviceDataProvider>().getDeviceData(mockCursor) } returns mockRawData

        // Initialize repository with testDispatcher
        val repository = DeviceDataRepository(mockApplication, testDispatcher)
        DeviceDataRepository.resetForTesting(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act: Equipment links changed for device 42
        EquipmentRepository.notifyEquipmentLinksChanged(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert: DeviceDataRepository refreshed device 42 from DB
        verify(atLeast = 1) { mockDevicesDb.getDeviceCursor(42L) }
        val refreshed = repository.getDeviceById(42L)
        assertNotNull(refreshed)
        assertEquals(listOf("Gravel Bike"), refreshed?.linkedEquipment)
    }

    /**
     * TST-UI-216.3: EditDeviceViewModel.loadInitialDeviceData retrieves fresh snapshot
     * from DeviceDataRepository without stale caching across dialog openings.
     */
    @Test
    fun testEditDeviceViewModel_loadInitialDeviceData_alwaysFetchesFreshSnapshot() {
        val mockRepo = mockk<DeviceDataRepository>(relaxed = true)
        DeviceDataRepository.resetForTesting(mockRepo)

        val mockBanalServiceRepo = mockk<BANALServiceRepository>(relaxed = true)
        mockkObject(BANALServiceRepository.Companion)
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalServiceRepo
        every { mockBanalServiceRepo.allActiveDevicesTelemetry } returns MutableStateFlow(emptyList<DeviceTelemetry>())
        every { mockBanalServiceRepo.newlyFoundDevicesIds } returns MutableStateFlow(emptyList<Long>())
        every { mockRepo.allDevices } returns MutableStateFlow(emptyList<DeviceUiData>())

        // Initial device state
        val initialDevice = createSampleDeviceUiData(100L, listOf("Old Bike"))
        every { mockRepo.getDeviceSnapshotById(100L) } returns initialDevice

        val viewModel = EditDeviceViewModel(mockApplication)
        viewModel.loadInitialDeviceData(100L)
        assertEquals(listOf("Old Bike"), viewModel.deviceSnapshot.value?.linkedEquipment)

        // Updated device state in repository (e.g. after matrix edit)
        val updatedDevice = createSampleDeviceUiData(100L, listOf("Updated Bike"))
        every { mockRepo.getDeviceSnapshotById(100L) } returns updatedDevice

        // Act: re-open or re-invoke loadInitialDeviceData for the same deviceId
        viewModel.loadInitialDeviceData(100L)

        // Assert: ViewModel holds fresh updated state, not stale old state
        assertEquals(listOf("Updated Bike"), viewModel.deviceSnapshot.value?.linkedEquipment)
    }

    /**
     * TST-UI-309.2: deleteDevice broadcasts BANALService.PAIRING_CHANGED with paired=false
     * and deletes the device from the database.
     */
    @Test
    fun testDeleteDevice_broadcastsPairingChangedWithUnpairedFlag() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as Intent }

        every { mockApplication.packageName } returns "com.atrainingtracker.trainingtracker"
        val repository = DeviceDataRepository(mockApplication, testDispatcher)
        DeviceDataRepository.resetForTesting(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Act: delete device 42L
        repository.deleteDevice(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert: database deletion invoked
        verify(exactly = 1) { mockDevicesDb.deleteDevice(42L) }

        // Assert: broadcast dispatched with paired=false and deviceId=42L
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(com.atrainingtracker.banalservice.BANALService.DEVICE_ID, 42L) }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra(com.atrainingtracker.banalservice.BANALService.PAIRED, false) }
        verify(atLeast = 1) { mockApplication.sendBroadcast(any()) }
    }

    /**
     * TST-UI-309.3: EditDeviceViewModel.loadInitialDeviceData sets deviceNotFound to true
     * when the device snapshot cannot be resolved from the repository.
     */
    @Test
    fun testEditDeviceViewModel_deviceNotFound_whenSnapshotIsNull() {
        val mockRepo = mockk<DeviceDataRepository>(relaxed = true)
        DeviceDataRepository.resetForTesting(mockRepo)

        val mockBanalServiceRepo = mockk<BANALServiceRepository>(relaxed = true)
        mockkObject(BANALServiceRepository.Companion)
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalServiceRepo
        every { mockBanalServiceRepo.allActiveDevicesTelemetry } returns MutableStateFlow(emptyList())
        every { mockBanalServiceRepo.newlyFoundDevicesIds } returns MutableStateFlow(emptyList())
        every { mockRepo.allDevices } returns MutableStateFlow(emptyList())

        // Device 999L is deleted / missing
        every { mockRepo.getDeviceSnapshotById(999L) } returns null

        val viewModel = EditDeviceViewModel(mockApplication)
        viewModel.loadInitialDeviceData(999L)

        // Assert: deviceNotFound is true and snapshot is null
        assertEquals(true, viewModel.deviceNotFound.value)
        assertEquals(null, viewModel.deviceSnapshot.value)
    }

    /**
     * TST-SEN-031.1 / REQ-SEN-031.3: When only WheelPosition changes on a BIKE_SPEED sensor
     * (linkedEquipment names unchanged), updateDevice() persists via EquipmentRepository.setWheelSensorLink(..., swapConflict = true).
     */
    @Test
    fun testUpdateDevice_wheelPositionOnlyChange_commitsViaSetWheelSensorLinkWithSwap() = kotlinx.coroutines.test.runTest(testDispatcher) {
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().size() } returns 0
        val mockEquipmentRepo = mockk<EquipmentRepository>(relaxed = true)
        EquipmentRepository.resetForTesting(mockEquipmentRepo)

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true
        every { mockDevicesDb.getCursorForAllDevices() } returns mockCursor
        every { mockDevicesDb.getDeviceCursor(42L) } returns mockCursor

        val initialRaw = DeviceRawData(
            id = 42L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.BIKE_SPEED,
            lastSeen = "Now",
            batteryPercentage = 95,
            manufacturer = "Garmin",
            deviceName = "Speed 2",
            isPaired = true,
            calibrationValue = 2.105,
            linkedEquipment = listOf("Gravel Bike"),
            availableEquipment = listOf("Gravel Bike", "Road Bike"),
            powerFeaturesFlags = null,
            linkedSportTypes = listOf("Cycling"),
            linkedWheelPositions = mapOf("Gravel Bike" to WheelPosition.FRONT)
        )
        val mockEqDb = mockk<EquipmentDbHelper>(relaxed = true)
        mockkStatic(EquipmentDbHelper::class)
        every { EquipmentDbHelper.getInstance(any()) } returns mockEqDb
        every { mockEqDb.getEquipmentId("Gravel Bike") } returns 10L
        every { mockEqDb.getEquipmentId("Road Bike") } returns 20L
        every { anyConstructed<RawDeviceDataProvider>().getDeviceData(mockCursor) } returns initialRaw

        val repository = DeviceDataRepository(mockApplication, testDispatcher)
        DeviceDataRepository.resetForTesting(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialUi = repository.getDeviceSnapshotById(42L)!!
        // Change ONLY WheelPosition (FRONT -> REAR), keeping linkedEquipment identical
        val updatedPositionOnly = initialUi.copy(
            linkedWheelPositions = mapOf("Gravel Bike" to WheelPosition.REAR)
        )

        repository.updateDevice(updatedPositionOnly)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) {
            mockEquipmentRepo.setWheelSensorLink(10L, 42L, WheelPosition.REAR, true)
        }
        verify(exactly = 0) {
            anyConstructed<EquipmentDbHelper>().setEquipmentLinks(any(), any())
        }
    }

    /**
     * TST-SEN-031.1 / REQ-SEN-031.3: Unlinking a bike and linking a new bike as FRONT on a BIKE_SPEED_AND_CADENCE
     * sensor calls setDeviceLink(..., false) for removed bikes and setWheelSensorLink(..., swapConflict = true) for active bikes.
     */
    @Test
    fun testUpdateDevice_wheelSpeedAndCadence_unlinksRemovedAndSetsNewPosition() = kotlinx.coroutines.test.runTest(testDispatcher) {
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().size() } returns 0
        val mockEquipmentRepo = mockk<EquipmentRepository>(relaxed = true)
        EquipmentRepository.resetForTesting(mockEquipmentRepo)

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true
        every { mockDevicesDb.getCursorForAllDevices() } returns mockCursor

        val initialRaw = DeviceRawData(
            id = 55L,
            protocol = Protocol.ANT_PLUS,
            deviceType = DeviceType.BIKE_SPEED_AND_CADENCE,
            lastSeen = "Now",
            batteryPercentage = 88,
            manufacturer = "Wahoo",
            deviceName = "Blue SC",
            isPaired = true,
            calibrationValue = 2.110,
            linkedEquipment = listOf("Gravel Bike"),
            availableEquipment = listOf("Gravel Bike", "Road Bike"),
            powerFeaturesFlags = null,
            linkedSportTypes = listOf("Cycling"),
            linkedWheelPositions = mapOf("Gravel Bike" to WheelPosition.REAR)
        )
        val mockEqDb = mockk<EquipmentDbHelper>(relaxed = true)
        mockkStatic(EquipmentDbHelper::class)
        every { EquipmentDbHelper.getInstance(any()) } returns mockEqDb
        every { mockEqDb.getEquipmentId("Gravel Bike") } returns 10L
        every { mockEqDb.getEquipmentId("Road Bike") } returns 20L
        every { anyConstructed<RawDeviceDataProvider>().getDeviceData(mockCursor) } returns initialRaw

        val repository = DeviceDataRepository(mockApplication, testDispatcher)
        DeviceDataRepository.resetForTesting(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialUi = repository.getDeviceSnapshotById(55L)!!
        val updated = initialUi.copy(
            linkedEquipment = listOf("Road Bike"),
            linkedWheelPositions = mapOf("Road Bike" to WheelPosition.FRONT)
        )

        repository.updateDevice(updated)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockEquipmentRepo.setDeviceLink(10L, 55L, false) }
        verify(exactly = 1) { mockEquipmentRepo.setWheelSensorLink(20L, 55L, WheelPosition.FRONT, true) }
    }

    /**
     * TST-SEN-031.1 / REQ-SEN-031.3 (Stage 6 Bounce 1 heal): Updating a wheel sensor's position
     * (FRONT -> REAR) emits a bulk refresh AFTER updating _allDevices so other unswapped wheel
     * speed sensors in allDevices immediately refresh their wheelPositionConflicts maps.
     */
    @Test
    fun testUpdateDevice_wheelPositionChange_refreshesUnswappedSiblingSensorConflictsInAllDevices() = kotlinx.coroutines.test.runTest(testDispatcher) {
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().size() } returns 0
        val mockEquipmentRepo = mockk<EquipmentRepository>(relaxed = true)
        EquipmentRepository.resetForTesting(mockEquipmentRepo)

        val mockEqDb = mockk<EquipmentDbHelper>(relaxed = true)
        mockkStatic(EquipmentDbHelper::class)
        every { EquipmentDbHelper.getInstance(any()) } returns mockEqDb
        every { mockEqDb.getEquipmentId("Gravel Bike") } returns 10L

        val sensorAInitial = DeviceRawData(
            id = 42L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.BIKE_SPEED,
            lastSeen = "Now",
            batteryPercentage = 95,
            manufacturer = "Garmin",
            deviceName = "Front Speed",
            isPaired = true,
            calibrationValue = 2.105,
            linkedEquipment = listOf("Gravel Bike"),
            availableEquipment = listOf("Gravel Bike"),
            powerFeaturesFlags = null,
            linkedSportTypes = listOf("Cycling"),
            linkedWheelPositions = mapOf("Gravel Bike" to WheelPosition.FRONT)
        )
        val sensorBInitial = DeviceRawData(
            id = 99L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.BIKE_SPEED,
            lastSeen = "Now",
            batteryPercentage = 80,
            manufacturer = "Wahoo",
            deviceName = "Spare Speed",
            isPaired = true,
            calibrationValue = 2.105,
            linkedEquipment = emptyList(),
            availableEquipment = listOf("Gravel Bike"),
            powerFeaturesFlags = null,
            linkedSportTypes = emptyList(),
            linkedWheelPositions = emptyMap(),
            wheelPositionConflicts = mapOf("Gravel Bike" to mapOf(WheelPosition.FRONT to "Front Speed"))
        )

        val sensorAAfter = sensorAInitial.copy(
            linkedWheelPositions = mapOf("Gravel Bike" to WheelPosition.REAR)
        )
        val sensorBAfter = sensorBInitial.copy(
            wheelPositionConflicts = mapOf("Gravel Bike" to mapOf(WheelPosition.REAR to "Front Speed"))
        )

        var phaseAfterMove = false
        val mockCursor = mockk<Cursor>(relaxed = true)
        var cursorIdx = -1
        every { mockCursor.moveToFirst() } answers {
            cursorIdx = 0
            true
        }
        every { mockCursor.moveToNext() } answers {
            cursorIdx++
            cursorIdx < 2
        }
        every { mockDevicesDb.getCursorForAllDevices() } returns mockCursor
        every { anyConstructed<RawDeviceDataProvider>().getDeviceData(mockCursor) } answers {
            when (cursorIdx) {
                0 -> if (phaseAfterMove) sensorAAfter else sensorAInitial
                else -> if (phaseAfterMove) sensorBAfter else sensorBInitial
            }
        }

        val repository = DeviceDataRepository(mockApplication, testDispatcher)
        DeviceDataRepository.resetForTesting(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            mapOf("Gravel Bike" to mapOf(WheelPosition.FRONT to "Front Speed")),
            repository.getDeviceById(99L)?.wheelPositionConflicts
        )

        // Move Sensor A from FRONT to REAR; Sensor B is unlinked/unswapped
        phaseAfterMove = true
        val sensorAUi = repository.getDeviceSnapshotById(42L)!!
        repository.updateDevice(
            sensorAUi.copy(linkedWheelPositions = mapOf("Gravel Bike" to WheelPosition.REAR))
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Unswapped Sensor B in allDevices must now see REAR conflict instead of stale FRONT
        assertEquals(
            mapOf("Gravel Bike" to mapOf(WheelPosition.REAR to "Front Speed")),
            repository.getDeviceById(99L)?.wheelPositionConflicts
        )
        assertEquals(
            mapOf("Gravel Bike" to WheelPosition.REAR),
            repository.getDeviceById(42L)?.linkedWheelPositions
        )
    }
}
