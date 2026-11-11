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

package com.atrainingtracker.banalservice.devices.ant_plus

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.hrv.HRVMetricsDevice
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityStatus
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc.DataState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * End-to-end integration test validating that [ANTHeartRateDevice] delivers UTC Unix epoch timestamps
 * that survive [HRVMetricsDevice.pruneOldBeats], producing valid non-null rMSSD and DFA a1 metrics
 * (REQ-TRK-069.4, TST-TRK-069.4, consolidating ATT-6742).
 */
class ANTHeartRateDeviceHrvIntegrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager

    private class TestANTHeartRateDevice(
        context: Context,
        sensorManager: MySensorManager
    ) : ANTHeartRateDevice(context, sensorManager, 301L, 54321) {
        override fun notifySensorsChanged() {
            // No-op for unit testing
        }
    }

    private class TestableHRVMetricsDevice(
        context: Context,
        sensorManager: MySensorManager,
        timeProvider: HRVMetricsDevice.TimeProvider
    ) : HRVMetricsDevice(context, sensorManager, timeProvider, startScheduler = false) {
        override fun notifySensorsChanged() {
            // No-op for unit testing
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testAntHeartRateDevice_e2e_hrvMetricsDeviceReceivesValidEpochAndComputesMetrics() {
        val baseWallClockMs = 1_700_000_000_000L
        var currentWallClockMs = baseWallClockMs
        var currentElapsedRealtimeMs = 100_000L

        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { currentElapsedRealtimeMs }

        val antDevice = TestANTHeartRateDevice(mockContext, mockSensorManager)
        val timeProvider = HRVMetricsDevice.TimeProvider { currentWallClockMs }
        val hrvDevice = TestableHRVMetricsDevice(mockContext, mockSensorManager, timeProvider)

        // Wire ANT+ beat events to HRVMetricsDevice
        antDevice.addRrBeatListener(hrvDevice)

        // Stream 120 seconds of continuous ANT+ beats at ~75 bpm (800ms intervals = 150 beats)
        var heartBeatEventTimeSec = 10.0
        var heartBeatCount = 100L

        // Baseline packet 0
        antDevice.parseHeartRateData(
            currentElapsedRealtimeMs,
            75,
            heartBeatCount,
            BigDecimal(String.format(java.util.Locale.US, "%.3f", heartBeatEventTimeSec)),
            DataState.LIVE_DATA
        )

        for (i in 1..150) {
            val intervalMs = 800.0 + (if (i % 2 == 0) 25.0 else -25.0) // Natural RSA oscillation (775ms / 825ms)
            heartBeatEventTimeSec += (intervalMs / 1000.0)
            heartBeatCount++
            currentElapsedRealtimeMs += Math.round(intervalMs)
            currentWallClockMs += Math.round(intervalMs)

            antDevice.parseHeartRateData(
                currentElapsedRealtimeMs,
                75,
                heartBeatCount,
                BigDecimal(String.format(java.util.Locale.US, "%.3f", heartBeatEventTimeSec % 64.0)),
                DataState.LIVE_DATA
            )
        }

        // Trigger 5-second scheduled calculation tick in HRVMetricsDevice
        hrvDevice.calculateMetrics()

        // Assertions:
        // 1. Beats must survive pruneOldBeats() because timestamps are UTC epoch ms matching currentWallClockMs
        assertNotNull("rMSSD must not be null after streaming 150 valid beats", hrvDevice.getHrvRmssdSensor().sensorData.value)
        val rmssd = (hrvDevice.getHrvRmssdSensor().sensorData.value as Number).toDouble()
        assertTrue("rMSSD must be positive (expected ~50ms from +-25ms delta)", rmssd > 20.0)

        // 2. DFA alpha 1 calculated over 120s window
        assertNotNull("DFA a1 must not be null", hrvDevice.getHrvDfaA1Sensor().sensorData.value)
        val a1 = (hrvDevice.getHrvDfaA1Sensor().sensorData.value as Number).toDouble()
        assertTrue("DFA a1 must be valid numeric value", a1 > 0.0 && !a1.isNaN())

        // 3. Artifact rate must be 0% for clean uninterrupted transmission
        assertNotNull("Artifact rate must not be null", hrvDevice.getHrvArtifactRateSensor().sensorData.value)
        val artifactRate = (hrvDevice.getHrvArtifactRateSensor().sensorData.value as Number).toDouble()
        assertEquals(0.0, artifactRate, 0.01)

        // 4. Signal quality status must be VALID
        val quality = hrvDevice.signalQualityResult
        assertNotNull("Signal quality result must be non-null", quality)
        assertEquals(HrvSignalQualityStatus.VALID, quality.status)
    }
}
