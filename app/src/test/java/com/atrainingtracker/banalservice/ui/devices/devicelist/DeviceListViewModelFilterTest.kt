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
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModelFilterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockDeviceRepo = mockk<DeviceDataRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)

    private val allDevicesFlow = MutableStateFlow<List<DeviceUiData>>(emptyList())
    private val newlyFoundIdsFlow = MutableStateFlow<List<Long>>(emptyList())
    private val activeTelemetryFlow = MutableStateFlow<List<com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(DeviceDataRepository.Companion)
        mockkObject(BANALServiceRepository.Companion)

        every { DeviceDataRepository.getInstance(any()) } returns mockDeviceRepo
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo

        every { mockDeviceRepo.allDevices } returns allDevicesFlow
        every { mockBanalRepo.newlyFoundDevicesIds } returns newlyFoundIdsFlow
        every { mockBanalRepo.allActiveDevicesTelemetry } returns activeTelemetryFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createUiDevice(
        id: Long,
        protocol: Protocol,
        deviceType: DeviceType,
        isPaired: Boolean = true,
        battery: Int = 80
    ): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = protocol,
            deviceType = deviceType,
            lastSeen = null,
            manufacturer = "Test",
            deviceName = "Device $id",
            isPaired = isPaired,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = battery,
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

    @Test
    fun testGetFilteredDevices_appliesDeviceFilterCriteria() = runTest(testDispatcher) {
        val dev1 = createUiDevice(1L, Protocol.ANT_PLUS, DeviceType.HRM, battery = 15) // critical
        val dev2 = createUiDevice(2L, Protocol.ANT_PLUS, DeviceType.BIKE_POWER, battery = 85) // normal
        val dev3 = createUiDevice(3L, Protocol.BLUETOOTH_LE, DeviceType.HRM, battery = 10) // critical
        val dev4 = createUiDevice(4L, Protocol.BLUETOOTH_LE, DeviceType.BIKE_CADENCE, battery = 90) // normal

        allDevicesFlow.value = listOf(dev1, dev2, dev3, dev4)

        val viewModel = DeviceListViewModel(mockApplication)
        val spec = DeviceFilterSpec(DeviceFilterType.ALL_KNOWN, Protocol.ALL, DeviceType.ALL)

        // 1. No criteria -> all 4 devices
        val allResult = viewModel.getFilteredDevices(spec, DeviceFilterCriteria()).first()
        assertEquals(4, allResult.size)

        // 2. Protocol criterion -> ANT_PLUS only (dev1, dev2)
        val antResult = viewModel.getFilteredDevices(spec, DeviceFilterCriteria(protocol = Protocol.ANT_PLUS)).first()
        assertEquals(2, antResult.size)
        assertTrue(antResult.all { it.protocol == Protocol.ANT_PLUS })

        // 3. Category criterion -> HRM only (dev1, dev3)
        val hrmResult = viewModel.getFilteredDevices(spec, DeviceFilterCriteria(deviceTypes = setOf(DeviceType.HRM))).first()
        assertEquals(2, hrmResult.size)
        assertTrue(hrmResult.all { it.deviceType == DeviceType.HRM })

        // 4. Battery criterion -> CRITICAL_ONLY (dev1, dev3)
        val critResult = viewModel.getFilteredDevices(spec, DeviceFilterCriteria(batteryFilter = BatteryFilter.CRITICAL_ONLY)).first()
        assertEquals(2, critResult.size)
        assertTrue(critResult.all { it.batteryPercentage in 0..20 })

        // 5. Combined criterion -> BLE + Critical (dev3)
        val bleCritResult = viewModel.getFilteredDevices(
            spec,
            DeviceFilterCriteria(protocol = Protocol.BLUETOOTH_LE, batteryFilter = BatteryFilter.CRITICAL_ONLY)
        ).first()
        assertEquals(1, bleCritResult.size)
        assertEquals(3L, bleCritResult[0].id)
    }
}
