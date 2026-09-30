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

    @Test
    fun testRemainingDuration_constantPercentage_countsDownMonotonicallyWithoutDrift() {
        val device = BatteryDevice(mockContext, mockSensorManager)

        // Baseline at t = 0: 85%
        device.onTimeTickExplicit(0, 85, false)

        // At t = 3600: battery drops 5% to 80% (drain rate = 5.0%/h, base remaining = 57600s = 16:00 h)
        device.onTimeTickExplicit(3600, 80, false)
        val remainingSensor = device.getSensor(SensorType.BATTERY_REMAINING_TIME)
        assertEquals(57600, remainingSensor.value)
        assertEquals("16:00 h", remainingSensor.stringValue)

        // At t = 3660 (1 min later, still 80%): remaining time MUST count down to 57540s
        device.onTimeTickExplicit(3660, 80, false)
        assertEquals(57540, remainingSensor.value)
        assertTrue((remainingSensor.value as Int) < 57600)

        // At t = 3900 (5 min later, still 80%): remaining time counts down to 57300s
        device.onTimeTickExplicit(3900, 80, false)
        assertEquals(57300, remainingSensor.value)
        assertTrue((remainingSensor.value as Int) < 57540)
    }

    @Test
    fun testRemainingDuration_stepDrop_recalibratesSmoothly() {
        val device = BatteryDevice(mockContext, mockSensorManager)

        // Baseline at t = 0: 85%
        device.onTimeTickExplicit(0, 85, false)

        // At t = 3600: battery drops to 80% (rate = 5.0%/h, remaining = 57600s)
        device.onTimeTickExplicit(3600, 80, false)
        val remainingSensor = device.getSensor(SensorType.BATTERY_REMAINING_TIME)
        assertEquals(57600, remainingSensor.value)

        // At t = 4320 (12 min after 3600s, total 4320s = 1.2h): drops 1% to 79% (total drop = 6%, rate = 6/1.2 = 5.0%/h)
        // Base remaining at 79% = 79 / 5.0 * 3600 = 56880s (15:48 h)
        device.onTimeTickExplicit(4320, 79, false)
        assertEquals(56880, remainingSensor.value)
        assertEquals("15:48 h", remainingSensor.stringValue)

        // Countdown continues from 56880 at t = 4350 (30s later) -> 56850s
        device.onTimeTickExplicit(4350, 79, false)
        assertEquals(56850, remainingSensor.value)
    }

    @Test
    fun testRemainingDuration_floorClamping_preventsPrematureDepletion() {
        val device = BatteryDevice(mockContext, mockSensorManager)

        // Baseline at t = 0: 85%
        device.onTimeTickExplicit(0, 85, false)

        // At t = 3600: drops to 80% (rate = 5.0%/h, base remaining = 57600s)
        // Floor for 80% is (79 / 5.0) * 3600 = 56880s
        device.onTimeTickExplicit(3600, 80, false)
        val remainingSensor = device.getSensor(SensorType.BATTERY_REMAINING_TIME)
        assertEquals(57600, remainingSensor.value)

        // At t = 4320 (elapsed 720s): countdown reaches floor exactly (57600 - 720 = 56880)
        device.onTimeTickExplicit(4320, 80, false)
        assertEquals(56880, remainingSensor.value)

        // At t = 4500 (elapsed 900s, battery still 80%): remaining time clamped to floor 56880s
        device.onTimeTickExplicit(4500, 80, false)
        assertEquals(56880, remainingSensor.value)

        // At t = 5000 (elapsed 1400s, battery still 80%): remaining time remains clamped to floor 56880s
        device.onTimeTickExplicit(5000, 80, false)
        assertEquals(56880, remainingSensor.value)
    }

    @Test
    fun testRemainingDuration_earlyDropBeforeStabilization_stabilizesAtWarmupThreshold() {
        val device = BatteryDevice(mockContext, mockSensorManager)

        // Baseline at t = 0: 85%
        device.onTimeTickExplicit(0, 85, false)

        // At t = 200: drops 1% to 84% (< 300s warmup threshold)
        device.onTimeTickExplicit(200, 84, false)
        val remainingSensor = device.getSensor(SensorType.BATTERY_REMAINING_TIME)
        assertEquals(BatteryRemainingTimeFormatter.STABILIZING_STATUS_CODE, remainingSensor.value)
        assertEquals("--:--", remainingSensor.stringValue)

        // At t = 300: reaches MIN_STABILIZATION_SECONDS threshold!
        // Effective elapsed time = 300 - 0 = 300s, drop = 1% -> rate = 1.0 / (300 / 3600) = 12.0%/h
        // Base remaining = (84 / 12.0) * 3600 = 25200s (7:00 h)
        device.onTimeTickExplicit(300, 84, false)
        assertEquals(25200, remainingSensor.value)
        assertEquals("7:00 h", remainingSensor.stringValue)

        // At t = 310: counts down monotonically to 25190s
        device.onTimeTickExplicit(310, 84, false)
        assertEquals(25190, remainingSensor.value)
    }

    @Test
    fun testRemainingDuration_pauseNeutrality_preservesCountdownWithoutDrift() {
        val device = BatteryDevice(mockContext, mockSensorManager)

        // Baseline at t = 0: 85%
        device.onTimeTickExplicit(0, 85, false)
        // At t = 3600: drops to 80% (57600s remaining)
        device.onTimeTickExplicit(3600, 80, false)
        val remainingSensor = device.getSensor(SensorType.BATTERY_REMAINING_TIME)
        assertEquals(57600, remainingSensor.value)

        // While tracking is paused, time ticks do not advance active recording seconds
        device.onTimeTick(true, true)
        device.onTimeTick(true, true)
        assertEquals(3600, device.activeRecordingSeconds)
        assertEquals(57600, remainingSensor.value)
    }
}
