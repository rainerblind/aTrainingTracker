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

package com.atrainingtracker.banalservice.devices

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.hardware.SensorManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.monetization.ProFeatureManager
import com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying defense-in-depth Free-tier hardware connection clamping
 * in [DeviceManager.startSearchForPairedDevices] (REQ-SET-084.7, TST-SET-084.12).
 */
class DeviceManagerQuotaClampingTest {

    private lateinit var mockBanalService: BANALService
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockAndroidSensorManager: SensorManager
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager
    private lateinit var mockProFeatureManager: ProFeatureManager
    private val isProActiveFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.startSearchWhenAppStarts() } returns false
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.havePermission(any()) } returns false
        every { TrainingApplication.searchOnlyForSportSpecificDevices() } returns false
        every { TrainingApplication.getNumberOfSearchTries() } returns 3

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockDevicesDbManager = mockk(relaxed = true)
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToNext() } returns false
        every { mockDb.query(any(), any(), any(), any(), any(), any(), any()) } returns mockCursor
        every { mockDevicesDbManager.database } returns mockDb

        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockProFeatureManager = mockk(relaxed = true)
        every { mockProFeatureManager.isProActive } returns isProActiveFlow
        mockkObject(ProFeatureManager.Companion)
        every { ProFeatureManager.getInstance(any()) } returns mockProFeatureManager

        mockLocationManager = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockAndroidSensorManager = mockk(relaxed = true)
        every { mockAndroidSensorManager.getDefaultSensor(any()) } returns null

        mockBanalService = mockk(relaxed = true)
        every { mockBanalService.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { mockBanalService.getSystemService(Context.SENSOR_SERVICE) } returns mockAndroidSensorManager
        every { mockBanalService.packageName } returns "com.atrainingtracker"
        every { mockBanalService.userSelectedBSportType } returns null

        mockkConstructor(ClockDevice::class)
        mockkConstructor(BatteryDevice::class)
        resetStaticSearchingDevice()
    }

    @After
    fun tearDown() {
        resetStaticSearchingDevice()
        unmockkAll()
        SensorQuotaReconciliationManager.setInstanceForTesting(null)
        ProFeatureManager.setInstanceForTesting(null)
    }

    private fun resetStaticSearchingDevice() {
        val field = DeviceManager::class.java.getDeclaredField("cMyRemoteDeviceCurrentlySearchingFor")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun addMockDeviceToQueue(deviceManager: DeviceManager, id: Long, name: String) {
        val dev = mockk<MyRemoteDevice>(relaxed = true)
        every { dev.deviceId } returns id
        every { dev.name } returns name
        every { dev.deviceType } returns DeviceType.HRM
        deviceManager.searchQueue.add(dev)
    }

    @Test
    fun testStartSearchForPairedDevices_clampsSearchQueue_whenFreeTier() {
        isProActiveFlow.value = false
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)

        // Seed 4 devices into the search queue
        addMockDeviceToQueue(deviceManager, 1L, "Device 1")
        addMockDeviceToQueue(deviceManager, 2L, "Device 2")
        addMockDeviceToQueue(deviceManager, 3L, "Device 3")
        addMockDeviceToQueue(deviceManager, 4L, "Device 4")

        assertEquals(4, deviceManager.searchQueue.size)

        deviceManager.startSearchForPairedDevices()

        // Total active search items (current searching device + searchQueue remaining) clamped to MAX_FREE_PAIRED_DEVICES (2)
        val totalActiveDevices = (if (deviceManager.currentlySearchingForRemoteDevice != null) 1 else 0) + deviceManager.searchQueue.size
        assertEquals(SensorQuotaReconciliationManager.MAX_FREE_PAIRED_DEVICES, totalActiveDevices)
        assertEquals(SensorQuotaReconciliationManager.MAX_FREE_PAIRED_DEVICES, deviceManager.mMyRemoteDeviceTries.size)
    }

    @Test
    fun testStartSearchForPairedDevices_doesNotClampSearchQueue_whenProTier() {
        isProActiveFlow.value = true
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)

        // Seed 4 devices into the search queue
        addMockDeviceToQueue(deviceManager, 1L, "Device 1")
        addMockDeviceToQueue(deviceManager, 2L, "Device 2")
        addMockDeviceToQueue(deviceManager, 3L, "Device 3")
        addMockDeviceToQueue(deviceManager, 4L, "Device 4")

        assertEquals(4, deviceManager.searchQueue.size)

        deviceManager.startSearchForPairedDevices()

        // Total active search items (current searching device + searchQueue remaining) retains all 4 devices on Pro tier
        val totalActiveDevices = (if (deviceManager.currentlySearchingForRemoteDevice != null) 1 else 0) + deviceManager.searchQueue.size
        assertEquals(4, totalActiveDevices)
        assertEquals(4, deviceManager.mMyRemoteDeviceTries.size)
    }
}
