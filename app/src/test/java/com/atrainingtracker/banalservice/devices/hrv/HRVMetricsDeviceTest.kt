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

package com.atrainingtracker.banalservice.devices.hrv

import android.content.Context
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.sensor.MySensorManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests validating [HRVMetricsDevice] lifecycle, Rule 51/52 invariants, 60s bias-free rMSSD calculation,
 * artifact pair-exclusion, 120s DFA alpha 1 calculation, and artifact rate gate suppression
 * pursuant to REQ-TRK-060.3, REQ-TRK-060.4, REQ-TRK-060.5, TST-TRK-060.3, and TST-TRK-060.5.
 */
class HRVMetricsDeviceTest {

    private lateinit var context: Context
    private lateinit var sensorManager: MySensorManager

    private class TestableHRVMetricsDevice(
        context: Context,
        sensorManager: MySensorManager,
        timeProvider: TimeProvider? = null,
        artifactFilter: RrArtifactFilter = RrArtifactFilter()
    ) : HRVMetricsDevice(context, sensorManager, timeProvider, artifactFilter, startScheduler = false) {
        override fun notifySensorsChanged() {
            // no-op in unit tests to eliminate Intent constructor mocking
        }
    }

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        context = mockk(relaxed = true)
        sensorManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testRule51ConstructorBaselineStateParityWithResetSession() {
        var currentTime = 1000000L
        val device = TestableHRVMetricsDevice(context, sensorManager, { currentTime })

        // Immediately after constructor, hasReceivedRrData must be false and quality INSUFFICIENT_DATA
        assertFalse(device.hasReceivedRrData)
        assertEquals(HrvSignalQualityStatus.INSUFFICIENT_DATA, device.signalQualityResult.status)

        // Send a beat
        device.onRrBeat(
            RrBeatEvent(
                beatTimestampMs = currentTime,
                rrMs = 800.0,
                rawCounter = 1,
                protocol = ProtocolType.BLE
            )
        )
        assertTrue(device.hasReceivedRrData)

        // Reset session
        device.resetSession()
        assertFalse(device.hasReceivedRrData)
        assertEquals(HrvSignalQualityStatus.INSUFFICIENT_DATA, device.signalQualityResult.status)
        device.shutDown()
    }

    @Test
    fun testRule52SensorPresenceGatingZeroTelemetryEmittedWithoutRrData() {
        var currentTime = 1000000L
        val device = TestableHRVMetricsDevice(context, sensorManager, { currentTime })

        // Execute periodic calculation before any RR packets arrive
        device.calculateMetrics()

        // Sensors must not publish any values (values remain null)
        assertNull(device.getHrvRmssdSensor().sensorData.value)
        assertNull(device.getHrvDfaA1Sensor().sensorData.value)
        assertNull(device.getHrvArtifactRateSensor().sensorData.value)

        device.shutDown()
    }

    @Test
    fun testRmssdMinimumSampleGuardRequiresAtLeast10ValidPairs() {
        var currentTime = 1000000L
        val device = TestableHRVMetricsDevice(context, sensorManager, { currentTime })

        // Feed only 8 beats (7 pairs < 10)
        for (i in 0 until 8) {
            val t = currentTime - (8 - i) * 800L
            device.onRrBeat(
                RrBeatEvent(
                    beatTimestampMs = t,
                    rrMs = 800.0 + (i % 2) * 20.0,
                    rawCounter = i,
                    protocol = ProtocolType.BLE
                )
            )
        }

        device.calculateMetrics()
        // Insufficient pairs (< 10) -> rMSSD emits null
        assertNull(device.getHrvRmssdSensor().sensorData.value)

        device.shutDown()
    }

    @Test
    fun testBiasFreeRmssdExcludesArtifactAdjacentPairs() {
        var currentTime = 1000000L
        val device = TestableHRVMetricsDevice(context, sensorManager, { currentTime })

        // Feed 20 beats: clean baseline 800ms with constant alternating +-10ms
        // At beat index 10, inject an ectopic short beat (450ms) followed by compensatory pause (1150ms) at 11
        for (i in 0 until 20) {
            val t = currentTime - (20 - i) * 800L
            val rr = when (i) {
                10 -> 450.0
                11 -> 1150.0
                else -> 800.0 + if (i % 2 == 0) 10.0 else -10.0
            }
            device.onRrBeat(
                RrBeatEvent(
                    beatTimestampMs = t,
                    rrMs = rr,
                    rawCounter = i,
                    protocol = ProtocolType.BLE
                )
            )
        }

        device.calculateMetrics()
        val rmssd = device.getHrvRmssdSensor().sensorData.value
        assertTrue("Expected non-null rMSSD, was $rmssd", rmssd != null)

        // For alternating 810 and 790, successive differences are 20ms, so rMSSD is exactly 20.0ms!
        // Downward bias would occur if the artifact was interpolated to 800ms yielding 10ms diffs.
        // Because artifact-adjacent pairs are excluded, rMSSD should be exactly 20.0ms!
        assertEquals(20.0, rmssd!!, 0.5)

        device.shutDown()
    }

    @Test
    fun testDfaArtifactGateSuppressesEmissionWhenArtifactRateExceeds5Percent() {
        var currentTime = 1000000L
        val device = TestableHRVMetricsDevice(context, sensorManager, { currentTime })

        // Feed 100 beats. 10 beats flagged as artifact/gaps (10% artifact rate > 5.0%)
        for (i in 0 until 100) {
            val t = currentTime - (100 - i) * 800L
            val isGap = i in 10..19
            device.onRrBeat(
                RrBeatEvent(
                    beatTimestampMs = t,
                    rrMs = if (isGap) 1600.0 else 800.0,
                    rawCounter = i,
                    protocol = ProtocolType.BLE,
                    artifactFlag = if (isGap) RrBeatEvent.ARTIFACT_GAP else RrBeatEvent.ARTIFACT_NORMAL
                )
            )
        }

        device.calculateMetrics()
        val artifactRate = device.getHrvArtifactRateSensor().sensorData.value
        val dfaAlpha1 = device.getHrvDfaA1Sensor().sensorData.value

        assertTrue(artifactRate != null)
        assertTrue(artifactRate!! > 5.0)
        // High artifact rate (> 5%) must suppress DFA alpha 1 (emits null)
        assertNull(dfaAlpha1)

        device.shutDown()
    }

    @Test
    fun testSignalQualityResultUpdatedOnCalculateMetrics() {
        var currentTime = 1000000L
        val device = TestableHRVMetricsDevice(context, sensorManager, { currentTime })

        // Feed 50 beats of stepped/synthetic intervals (zero differences)
        for (i in 0 until 50) {
            val t = currentTime - (50 - i) * 800L
            val stepVal = (i / 10) * 10.0 // plateaus of 10 identical beats
            device.onRrBeat(
                RrBeatEvent(
                    beatTimestampMs = t,
                    rrMs = 500.0 + stepVal,
                    rawCounter = i,
                    protocol = ProtocolType.BLE
                )
            )
        }

        device.calculateMetrics()

        val quality = device.signalQualityResult
        assertEquals(HrvSignalQualityStatus.QUANTIZED_SYNTHETIC, quality.status)
        assertTrue(quality.zeroDiffRate != null && quality.zeroDiffRate > 0.55)

        device.shutDown()
    }
}
