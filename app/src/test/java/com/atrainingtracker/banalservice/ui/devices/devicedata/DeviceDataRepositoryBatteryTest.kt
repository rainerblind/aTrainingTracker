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
import android.database.Cursor
import android.util.Log
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying reactive cache invalidation upon battery state updates in DeviceDataRepository
 * (REQ-CON-007, TST-CON-010.2, ATT-3312).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDataRepositoryBatteryTest {

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
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testNotifyBatteryUpdated_reactivelyUpdatesAllDevicesStateFlow() = runTest(testDispatcher) {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.moveToNext() } returns false
        every { mockCursor.isAfterLast } returns false andThen true
        every { mockDevicesDb.getCursorForAllDevices() } returns mockCursor

        val mockRawData = DeviceRawData(
            id = 42L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.HRM,
            lastSeen = "Now",
            batteryPercentage = 95,
            manufacturer = "Polar",
            deviceName = "Polar H10",
            isPaired = true,
            calibrationValue = null,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            linkedSportTypes = emptyList()
        )
        every { anyConstructed<RawDeviceDataProvider>().getDeviceData(mockCursor) } returns mockRawData

        val repository = DeviceDataRepository(mockApplication, testDispatcher)
        DeviceDataRepository.resetForTesting(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialDevices = repository.allDevices.value
        assertEquals(1, initialDevices.size)
        assertEquals(95, initialDevices[0].batteryPercentage)

        // Trigger reactive battery notification
        DeviceDataRepository.notifyBatteryUpdated(42L, 15)
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedDevices = repository.allDevices.value
        assertEquals(1, updatedDevices.size)
        assertEquals(15, updatedDevices[0].batteryPercentage)
    }
}
