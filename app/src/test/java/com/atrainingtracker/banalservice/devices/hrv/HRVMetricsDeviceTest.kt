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
        artifactFilter: RrArtifactFilter = RrArtifactFilter(),
        artifactGateThresholdPercent: Double? = null
    ) : HRVMetricsDevice(
        context,
        sensorManager,
        timeProvider,
        artifactFilter,
        startScheduler = false,
        artifactGateThresholdPercent = artifactGateThresholdPercent
    ) {
        override fun notifySensorsChanged() {
            // no-op in unit tests to eliminate Intent constructor mocking
        }

        public override fun onAccumulatorsReset() {
            super.onAccumulatorsReset()
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

    @Test
    fun testStaleMetricsClearedOnQueueDrain() {
        var currentTime = 1000000L
        val device = TestableHRVMetricsDevice(context, sensorManager, { currentTime })

        // Feed 30 clean beats (within 24 seconds, well inside the 60s/120s window)
        for (i in 0 until 30) {
            val t = currentTime - (30 - i) * 800L
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
        // Sensors should have non-null metrics
        assertTrue("rMSSD must be non-null", device.getHrvRmssdSensor().sensorData.value != null)
        assertTrue("Artifact rate must be non-null", device.getHrvArtifactRateSensor().sensorData.value != null)

        // Advance clock by 150 seconds (> 120s rolling window) to simulate queue drain on strap disconnect
        currentTime += 150_000L
        device.calculateMetrics()

        // All stale telemetry sensors must be cleared to null ("--")
        assertNull("rMSSD must clear to null on queue drain", device.getHrvRmssdSensor().sensorData.value)
        assertNull("DFA alpha 1 must clear to null on queue drain", device.getHrvDfaA1Sensor().sensorData.value)
        assertNull("Artifact rate must clear to null on queue drain", device.getHrvArtifactRateSensor().sensorData.value)

        device.shutDown()
    }

    @Test
    fun testOnAccumulatorsResetInvokesResetSession() {
        var currentTime = 1000000L
        val device = TestableHRVMetricsDevice(context, sensorManager, { currentTime })

        // Feed RR beat to initialize telemetry state
        device.onRrBeat(
            RrBeatEvent(
                beatTimestampMs = currentTime,
                rrMs = 800.0,
                rawCounter = 1,
                protocol = ProtocolType.BLE
            )
        )
        assertTrue(device.hasReceivedRrData)

        // Trigger onAccumulatorsReset() as called by BANALService.RESET_ACCUMULATORS_INTENT
        device.onAccumulatorsReset()

        assertFalse("hasReceivedRrData must be false after accumulator reset", device.hasReceivedRrData)
        assertEquals(HrvSignalQualityStatus.INSUFFICIENT_DATA, device.signalQualityResult.status)

        device.shutDown()
    }

    @Test
    fun testConfigurableArtifactGateThreshold() {
        var currentTime = 1000000L

        // Device A: gate threshold configured to 3.5%
        val deviceA = TestableHRVMetricsDevice(
            context = context,
            sensorManager = sensorManager,
            timeProvider = { currentTime },
            artifactGateThresholdPercent = 3.5
        )

        // Device B: gate threshold configured to 5.0% (default)
        val deviceB = TestableHRVMetricsDevice(
            context = context,
            sensorManager = sensorManager,
            timeProvider = { currentTime },
            artifactGateThresholdPercent = 5.0
        )

        // Feed 100 beats with 4 spaced-out RF gap artifacts (4.21% artifact rate over 95 causal evaluated beats)
        // Rate 4.21% is > 3.5% (Device A: suppressed) and <= 5.0% (Device B: not suppressed)
        for (i in 0 until 100) {
            val t = currentTime - (100 - i) * 800L
            val isGap = i == 20 || i == 40 || i == 60 || i == 80
            val event = RrBeatEvent(
                beatTimestampMs = t,
                rrMs = 800.0 + 30.0 * Math.sin(2.0 * Math.PI * i / 10.0),
                rawCounter = i,
                protocol = ProtocolType.BLE,
                artifactFlag = if (isGap) RrBeatEvent.ARTIFACT_GAP else RrBeatEvent.ARTIFACT_NORMAL,
                gapBeats = 0
            )
            deviceA.onRrBeat(event)
            deviceB.onRrBeat(event)
        }

        deviceA.calculateMetrics()
        deviceB.calculateMetrics()

        val artifactRateA = deviceA.getHrvArtifactRateSensor().sensorData.value
        val artifactRateB = deviceB.getHrvArtifactRateSensor().sensorData.value
        assertTrue(artifactRateA != null)
        assertTrue(artifactRateB != null)
        assertEquals(artifactRateA!!, artifactRateB!!, 0.001)

        // For deviceA (threshold 3.5%), 4.0% > 3.5% -> suppressed (null)
        assertNull("Device A must suppress DFA alpha 1 when artifact rate exceeds 3.5%", deviceA.getHrvDfaA1Sensor().sensorData.value)

        // For deviceB (threshold 5.0%), 4.0% <= 5.0% -> computed (non-null)
        assertTrue("Device B must evaluate DFA alpha 1 when artifact rate is within 5.0%", deviceB.getHrvDfaA1Sensor().sensorData.value != null)

        deviceA.shutDown()
        deviceB.shutDown()
    }
}
