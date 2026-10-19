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
import android.hardware.SensorManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying search queue integrity and isolation against alien device stop intents
 * in DeviceManager (REQ-CON-020.3, TST-CON-012.3).
 */
class DeviceManagerSearchQueueTest {

    private lateinit var mockBanalService: BANALService
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockAndroidSensorManager: SensorManager
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager

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

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockDevicesDbManager = mockk(relaxed = true)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

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
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun setCurrentlySearching(deviceManager: DeviceManager, device: MyRemoteDevice?) {
        val field = DeviceManager::class.java.getDeclaredField("cMyRemoteDeviceCurrentlySearchingFor")
        field.isAccessible = true
        field.set(deviceManager, device)
    }

    @Test
    fun testAlienDeviceEventIgnoredAndQueueNotPreempted() {
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)

        val deviceA = mockk<MyRemoteDevice>(relaxed = true)
        every { deviceA.deviceId } returns 101L
        every { deviceA.deviceType } returns DeviceType.HRM

        val deviceB = mockk<MyRemoteDevice>(relaxed = true)
        every { deviceB.deviceId } returns 102L
        every { deviceB.deviceType } returns DeviceType.BIKE_POWER
        every { deviceB.isSearching } returns false
        every { deviceB.isReceivingData } returns false

        setCurrentlySearching(deviceManager, deviceA)
        deviceManager.searchQueue.clear()
        deviceManager.searchQueue.addLast(deviceB)

        // Simulate alien device (ID = 999) firing SEARCHING_STOPPED_FOR_ONE_INTENT
        val alienIntent = mockk<Intent>()
        every { alienIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns 999L
        every { alienIntent.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns false

        val receiver = deviceManager.searchingStoppedForOneReceiver
        receiver.onReceive(mockBanalService, alienIntent)

        // Verify Device A remains currently searching
        assertSame(deviceA, deviceManager.currentlySearchingForRemoteDevice)

        // Verify Device B is NOT popped from mSearchQueue
        assertEquals(1, deviceManager.searchQueue.size)
        assertSame(deviceB, deviceManager.searchQueue.peekFirst())

        // Verify Device B was NOT started
        verify(exactly = 0) { deviceB.startSearching() }
    }

    @Test
    fun testMatchingDeviceEventAdvancesQueueToNextDevice() {
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)

        val deviceA = mockk<MyRemoteDevice>(relaxed = true)
        every { deviceA.deviceId } returns 101L
        every { deviceA.deviceType } returns DeviceType.HRM

        val deviceB = mockk<MyRemoteDevice>(relaxed = true)
        every { deviceB.deviceId } returns 102L
        every { deviceB.deviceType } returns DeviceType.HRM
        every { deviceB.isSearching } returns false
        every { deviceB.isReceivingData } returns false

        setCurrentlySearching(deviceManager, deviceA)
        deviceManager.searchQueue.clear()
        deviceManager.searchQueue.addLast(deviceB)

        // Simulate matching device A firing stop intent
        val matchingIntent = mockk<Intent>()
        every { matchingIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns 101L
        every { matchingIntent.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false) } returns true

        val receiver = deviceManager.searchingStoppedForOneReceiver
        receiver.onReceive(mockBanalService, matchingIntent)

        // Verify queue advanced to Device B
        assertSame(deviceB, deviceManager.currentlySearchingForRemoteDevice)
        assertTrue(deviceManager.searchQueue.isEmpty())
        verify(exactly = 1) { deviceB.startSearching() }
    }
}
