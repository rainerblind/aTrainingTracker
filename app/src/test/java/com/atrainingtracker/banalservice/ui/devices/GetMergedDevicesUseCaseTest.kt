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

package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying GetMergedDevicesUseCase correctly maps isNewlyDiscovered
 * based on BANALServiceRepository.newlyFoundDevicesIds (REQ-UI-334, TST-UI-294, ATT-3099).
 */
class GetMergedDevicesUseCaseTest {

    private lateinit var mockApplication: Application
    private lateinit var mockDbRepo: DeviceDataRepository
    private lateinit var mockServiceRepo: BANALServiceRepository

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
        mockApplication = mockk(relaxed = true)
        mockDbRepo = mockk(relaxed = true)
        mockServiceRepo = mockk(relaxed = true)

        every { mockDbRepo.allDevices } returns allDevicesFlow
        every { mockServiceRepo.allActiveDevicesTelemetry } returns telemetryFlow
        every { mockServiceRepo.newlyFoundDevicesIds } returns newlyFoundFlow
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testNewlyDiscoveredFlag_whenDeviceIdInNewlyFoundList_isSetToTrue() = runTest {
        val device1 = createDevice(id = 101L, name = "Polar H10", protocol = Protocol.BLUETOOTH_LE, deviceType = DeviceType.HRM, isPaired = false)
        val device2 = createDevice(id = 102L, name = "Wahoo KICKR", protocol = Protocol.BLUETOOTH_LE, deviceType = DeviceType.BIKE_POWER, isPaired = true)

        allDevicesFlow.value = listOf(device1, device2)
        newlyFoundFlow.value = listOf(101L) // only device 101 is newly found

        val useCase = GetMergedDevicesUseCase(mockDbRepo, mockServiceRepo, mockApplication)
        val result = useCase.mergedDevices.first()

        assertEquals(2, result.size)
        val resultDevice1 = result.first { it.id == 101L }
        val resultDevice2 = result.first { it.id == 102L }

        assertTrue("Device 101 must have isNewlyDiscovered = true", resultDevice1.isNewlyDiscovered)
        assertFalse("Device 102 must have isNewlyDiscovered = false", resultDevice2.isNewlyDiscovered)
    }

    @Test
    fun testNewlyDiscoveredFlag_withActiveTelemetry_retainsNewlyDiscoveredTrue() = runTest {
        val device1 = createDevice(id = 201L, name = "Garmin HRM-Pro", protocol = Protocol.ANT_PLUS, deviceType = DeviceType.HRM, isPaired = true)

        allDevicesFlow.value = listOf(device1)
        newlyFoundFlow.value = listOf(201L)
        telemetryFlow.value = listOf(
            DeviceTelemetry(
                deviceId = 201L,
                mainValue = null,
                allValues = emptyList()
            )
        )

        val useCase = GetMergedDevicesUseCase(mockDbRepo, mockServiceRepo, mockApplication)
        val result = useCase.mergedDevices.first()

        assertEquals(1, result.size)
        assertTrue("Device with telemetry and in newlyFound must have isNewlyDiscovered = true", result[0].isNewlyDiscovered)
        assertTrue("Device must be marked connected", result[0].isConnected)
    }
}
