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
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying 1 Hz timer loop fault isolation in ClockDevice
 * (REQ-TRK-027.2, TST-TRK-019.3, ATT-3981).
 */
class ClockDeviceResilienceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private val mockDevicesDbManager = mockk<DevicesDatabaseManager>(relaxed = true)
    private val mockIntent = mockk<Intent>(relaxed = true)
    private var clockDevice: ClockDevice? = null

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(BANALService::class)
        every { BANALService.getDebug(any()) } returns false

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false

        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } returns mockIntent

        mockContext = mockk(relaxed = true)
        every { mockContext.packageName } returns "com.atrainingtracker"
        mockSensorManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clockDevice?.shutDown()
        unmockkAll()
    }

    /**
     * TST-TRK-019.3: Verifies that an unchecked exception thrown during a ClockTimeTask.run() tick
     * (e.g. from mContext.sendBroadcast or a sensor listener) is caught, logged, and counted without
     * killing the 1 Hz clock timer or preventing subsequent ticks from incrementing TIME_TOTAL,
     * TIME_ACTIVE, and TIME_LAP (REQ-TRK-027.2).
     */
    @Test
    fun testClockTimeTaskCatchesTransientExceptionAndContinuesIncrementingSensors() {
        val device = ClockDevice(mockContext, mockSensorManager, false)
        clockDevice = device

        val initialTotal = (device.getSensor(SensorType.TIME_TOTAL).value as Number).toInt()
        val initialActive = (device.getSensor(SensorType.TIME_ACTIVE).value as Number).toInt()
        val initialLap = (device.getSensor(SensorType.TIME_LAP).value as Number).toInt()
        val initialErrorCount = device.clockTimerErrorCount

        val broadcastAttempt = AtomicInteger(0)
        every { mockContext.sendBroadcast(any()) } answers {
            val attempt = broadcastAttempt.incrementAndGet()
            if (attempt == 1) {
                throw IllegalStateException("Simulated transient broadcast failure on tick 1")
            }
        }

        // Execute 3 deterministic ticks where the first sendBroadcast throws IllegalStateException
        device.runClockTickForTesting()
        device.runClockTickForTesting()
        device.runClockTickForTesting()

        val finalTotal = (device.getSensor(SensorType.TIME_TOTAL).value as Number).toInt()
        val finalActive = (device.getSensor(SensorType.TIME_ACTIVE).value as Number).toInt()
        val finalLap = (device.getSensor(SensorType.TIME_LAP).value as Number).toInt()

        assertEquals("Expected exactly 1 caught clock timer exception across the 3 deterministic ticks", initialErrorCount + 1, device.clockTimerErrorCount)
        assertEquals("TIME_TOTAL must increment across all 3 ticks", initialTotal + 3, finalTotal)
        assertEquals("TIME_ACTIVE must increment across all 3 ticks", initialActive + 3, finalActive)
        assertEquals("TIME_LAP must increment across all 3 ticks", initialLap + 3, finalLap)
        verify(atLeast = 1) { Log.e("ClockDevice", any<String>(), any<Throwable>()) }
    }

    /**
     * TST-TRK-039.3: Rate-limited error logging policy for ClockDevice timer loop (REQ-TRK-039.4).
     */
    @Test
    fun testClockDeviceRateLimitsErrorLogging() {
        // Initial burst 1..3
        assertTrue("Error #1 must be logged", ClockDevice.shouldLogClockTimerError(1))
        assertTrue("Error #2 must be logged", ClockDevice.shouldLogClockTimerError(2))
        assertTrue("Error #3 must be logged", ClockDevice.shouldLogClockTimerError(3))

        // Suppressed interval 4..59
        for (i in 4..59) {
            assertFalse("Error #$i must be suppressed", ClockDevice.shouldLogClockTimerError(i))
        }

        // Periodic 60
        assertTrue("Error #60 must be logged", ClockDevice.shouldLogClockTimerError(60))

        // Suppressed interval 61..119
        for (i in 61..119) {
            assertFalse("Error #$i must be suppressed", ClockDevice.shouldLogClockTimerError(i))
        }

        // Periodic 120
        assertTrue("Error #120 must be logged", ClockDevice.shouldLogClockTimerError(120))
    }

    /**
     * TST-TRK-039.3: Monotonic time anchoring for ClockDevice timer ticks (REQ-TRK-039.4).
     */
    @Test
    fun testClockDeviceMonotonicTimeAdvancement() {
        val device = ClockDevice(mockContext, mockSensorManager, false)
        clockDevice = device

        val initialTimestamp = device.lastTickElapsedRealtimeMs
        device.runClockTickForTesting()
        val tickTimestamp = device.lastTickElapsedRealtimeMs

        assertTrue("Last tick timestamp must be updated on tick execution", tickTimestamp > 0L)
        assertTrue("Tick timestamp must be >= initial timestamp", tickTimestamp >= initialTimestamp)

        val monotonicNow = ClockDevice.getMonotonicTimeMs()
        assertTrue("Monotonic timestamp must be non-negative", monotonicNow >= 0L)
    }
}
