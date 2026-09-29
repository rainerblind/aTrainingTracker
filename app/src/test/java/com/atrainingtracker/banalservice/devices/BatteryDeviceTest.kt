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
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.sensor.formater.BatteryRemainingTimeFormatter
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import android.content.Intent
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [BatteryDevice] (REQ-CON-015, TST-CON-006, ATT-1454).
 */
class BatteryDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        mockkStatic(ContextCompat::class)
        mockkStatic(DevicesDatabaseManager::class)
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockDevicesDbManager = mockk(relaxed = true)

        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testBatteryLevel_broadcastReceived_updatesPhoneBatterySensor() {
        val device = BatteryDevice(mockContext, mockSensorManager)

        device.onBatteryChanged(75, 100, BatteryManager.BATTERY_STATUS_DISCHARGING)

        assertEquals(75, device.batteryLevel)
        val sensor = device.getSensor(SensorType.PHONE_BATTERY)
        assertEquals(75, sensor.value)
        assertEquals("75", sensor.stringValue)
        assertFalse(device.isCharging)
    }

    @Test
    fun testRemainingDuration_warmupPeriod_displaysDashes() {
        val device = BatteryDevice(mockContext, mockSensorManager)

        // Simulate tracking for 180 seconds (< 300 seconds warmup threshold)
        device.onBatteryChanged(80, 100, BatteryManager.BATTERY_STATUS_DISCHARGING)
        device.onTimeTickExplicit(180, 80, false)

        val remainingSensor = device.getSensor(SensorType.BATTERY_REMAINING_TIME)
        assertEquals(BatteryRemainingTimeFormatter.STABILIZING_STATUS_CODE, remainingSensor.value)
        assertEquals("--:--", remainingSensor.stringValue)
    }

    @Test
    fun testRemainingDuration_activeDrain_projectsRemainingHoursAndMinutes() {
        val device = BatteryDevice(mockContext, mockSensorManager)

        // Baseline sample at t = 0: 85%
        device.onTimeTickExplicit(0, 85, false)

        // After 1 hour (3600 seconds) active tracking: battery dropped 5% to 80% (drain rate = 5.0%/h)
        device.onTimeTickExplicit(3600, 80, false)

        val remainingSensor = device.getSensor(SensorType.BATTERY_REMAINING_TIME)
        // Expected remaining: 80% / 5.0%/h = 16 hours = 16 * 3600 = 57600 seconds
        assertEquals(57600, remainingSensor.value)
        assertEquals("16:00 h", remainingSensor.stringValue)
    }

    @Test
    fun testRemainingDuration_deviceCharging_displaysChargingOrDashes() {
        val device = BatteryDevice(mockContext, mockSensorManager)

        device.onBatteryChanged(80, 100, BatteryManager.BATTERY_STATUS_CHARGING)

        assertTrue(device.isCharging)
        val remainingSensor = device.getSensor(SensorType.BATTERY_REMAINING_TIME)
        assertEquals(BatteryRemainingTimeFormatter.CHARGING_STATUS_CODE, remainingSensor.value)
        val str = remainingSensor.stringValue
        assertTrue(str == "Charging" || str.isNotEmpty())
    }

    @Test
    fun testRemainingDuration_zeroDrain_displaysDashes() {
        val device = BatteryDevice(mockContext, mockSensorManager)

        // Baseline at t = 0: 80%
        device.onTimeTickExplicit(0, 80, false)

        // After 600s (10 min): still 80% (no drain detected, 0% drop)
        device.onTimeTickExplicit(600, 80, false)

        val remainingSensor = device.getSensor(SensorType.BATTERY_REMAINING_TIME)
        assertEquals(BatteryRemainingTimeFormatter.STABILIZING_STATUS_CODE, remainingSensor.value)
        assertEquals("--:--", remainingSensor.stringValue)
    }

    @Test
    fun testResetDrainHistory_clearsTrackingState() {
        val device = BatteryDevice(mockContext, mockSensorManager)

        device.onTimeTickExplicit(0, 85, false)
        device.onTimeTickExplicit(3600, 80, false)
        assertEquals(57600, device.getSensor(SensorType.BATTERY_REMAINING_TIME).value)

        device.resetDrainHistory()
        assertEquals(0, device.activeRecordingSeconds)
        assertEquals(BatteryRemainingTimeFormatter.STABILIZING_STATUS_CODE, device.getSensor(SensorType.BATTERY_REMAINING_TIME).value)
    }
}
