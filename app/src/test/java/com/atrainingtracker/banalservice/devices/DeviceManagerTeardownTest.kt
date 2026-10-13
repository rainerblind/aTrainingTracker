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
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying defensive sensor teardown and unregistration in DeviceManager (REQ-UI-349, TST-UI-309.4).
 */
class DeviceManagerTeardownTest {

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

        mockkConstructor(ClockDevice::class)
        mockkConstructor(BatteryDevice::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-UI-309.4: DeviceManager.removeAndStopRemoteDevice executes defensively
     * without throwing NullPointerException when device is not in mMyRemoteDevices.
     */
    @Test
    fun testRemoveAndStopRemoteDevice_safeWhenDeviceNotRegistered() {
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        // Act: invoking removeAndStopRemoteDevice for an unmapped device ID must execute gracefully without NPE
        deviceManager.removeAndStopRemoteDevice(99999L)
    }
}
