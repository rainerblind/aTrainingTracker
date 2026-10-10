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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import android.app.Application
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying DeviceListViewModel prioritizes newly discovered sensors
 * at the top of the scan list in recency order (REQ-UI-334, TST-UI-294, ATT-3099).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModelPriorityTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockApplication: Application
    private lateinit var mockDeviceDataRepository: DeviceDataRepository
    private lateinit var mockBanalServiceRepository: BANALServiceRepository

    private val allDevicesFlow = MutableStateFlow<List<DeviceUiData>>(emptyList())
    private val telemetryFlow = MutableStateFlow<List<DeviceTelemetry>>(emptyList())
    private val newlyFoundFlow = MutableStateFlow<List<Long>>(emptyList())

    private fun createDevice(
        id: Long,
        name: String,
        protocol: Protocol = Protocol.BLUETOOTH_LE,
        deviceType: DeviceType = DeviceType.HRM,
        isPaired: Boolean = false
    ): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = protocol,
            deviceType = deviceType,
            lastSeen = null,
            manufacturer = "Manufacturer",
            deviceName = name,
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
            allValues = null,
            isNewlyDiscovered = false
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockApplication = mockk(relaxed = true)
        mockDeviceDataRepository = mockk(relaxed = true)
        mockBanalServiceRepository = mockk(relaxed = true)

        mockkObject(DeviceDataRepository.Companion)
        every { DeviceDataRepository.getInstance(any()) } returns mockDeviceDataRepository
        every { mockDeviceDataRepository.allDevices } returns allDevicesFlow

        mockkObject(BANALServiceRepository.Companion)
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalServiceRepository
        every { mockBanalServiceRepository.allActiveDevicesTelemetry } returns telemetryFlow
        every { mockBanalServiceRepository.newlyFoundDevicesIds } returns newlyFoundFlow
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testNewlyDiscoveredSensors_areSortedAtTopOfListInRecencyOrder() = runTest {
        // Device A: Known paired device (not new, not connected)
        val deviceA = createDevice(id = 1L, name = "Alpha Sensor", isPaired = true)
        // Device B: Known connected device (telemetry active, not new)
        val deviceB = createDevice(id = 2L, name = "Beta Sensor", isPaired = true)
        // Device C: Newly discovered sensor 1
        val deviceC = createDevice(id = 3L, name = "Charlie New", isPaired = false)
        // Device D: Newly discovered sensor 2 (discovered later)
        val deviceD = createDevice(id = 4L, name = "Delta Newest", isPaired = false)

        allDevicesFlow.value = listOf(deviceA, deviceB, deviceC, deviceD)
        // Device B has telemetry
        telemetryFlow.value = listOf(DeviceTelemetry(deviceId = 2L, mainValue = null, allValues = emptyList()))
        // Newly found order: 3L found first, then 4L found second
        newlyFoundFlow.value = listOf(3L, 4L)

        val viewModel = DeviceListViewModel(mockApplication)
        val spec = DeviceFilterSpec(DeviceFilterType.ALL_KNOWN, Protocol.ALL, DeviceType.ALL)

        val resultList = viewModel.getFilteredDevices(spec).first()

        assertEquals(4, resultList.size)
        // Delta Newest (id 4, index 1 in newlyFound) must be at index 0 (most recent discovery)
        assertEquals(4L, resultList[0].id)
        assertTrue(resultList[0].isNewlyDiscovered)

        // Charlie New (id 3, index 0 in newlyFound) must be at index 1
        assertEquals(3L, resultList[1].id)
        assertTrue(resultList[1].isNewlyDiscovered)

        // Beta Sensor (id 2, connected) must be at index 2
        assertEquals(2L, resultList[2].id)
        assertFalse(resultList[2].isNewlyDiscovered)
        assertTrue(resultList[2].isConnected)

        // Alpha Sensor (id 1, disconnected) must be at index 3
        assertEquals(1L, resultList[3].id)
        assertFalse(resultList[3].isNewlyDiscovered)
        assertFalse(resultList[3].isConnected)
    }
}
