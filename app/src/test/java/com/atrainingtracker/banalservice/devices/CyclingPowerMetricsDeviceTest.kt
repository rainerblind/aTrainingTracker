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
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying real-time power-to-weight ratio (W/kg) calculation,
 * pause gating, unconfigured weight fallback, and sensor update emissions
 * conforming to REQ-TRK-019 and TST-TRK-011.
 */
class CyclingPowerMetricsDeviceTest {

    private lateinit var context: Context
    private lateinit var sensorManager: MySensorManager
    private lateinit var powerSensor: MySensor<Double>
    private lateinit var powerSensorData: SensorData<Double>

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }

        context = mockk(relaxed = true)
        sensorManager = mockk(relaxed = true)
        powerSensor = mockk(relaxed = true)
        powerSensorData = mockk(relaxed = true)

        every { sensorManager.getSensor(SensorType.POWER) } returns powerSensor
        every { powerSensor.sensorData } returns powerSensorData
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCalculateMetrics_validPowerAndWeight_publishesCorrectWPerKg() {
        every { powerSensorData.value } returns 300.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f }
        )
        device.calculateMetrics()

        val ptwSensor = device.powerToWeightSensor
        assertEquals(4.0, ptwSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_fractionalPowerAndWeight_publishesExactRatio() {
        every { powerSensorData.value } returns 250.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 65.0f }
        )
        device.calculateMetrics()

        val ptwSensor = device.powerToWeightSensor
        assertEquals(250.0 / 65.0, ptwSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_zeroPower_publishesZeroWPerKg() {
        every { powerSensorData.value } returns 0.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f }
        )
        device.calculateMetrics()

        val ptwSensor = device.powerToWeightSensor
        assertEquals(0.0, ptwSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_unconfiguredWeight_publishesNull() {
        every { powerSensorData.value } returns 300.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 0.0f }
        )
        device.calculateMetrics()

        val ptwSensor = device.powerToWeightSensor
        assertNull(ptwSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_recordingSuspended_skipsCalculation() {
        every { powerSensorData.value } returns 300.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { true }, // Suspended
            { 75.0f }
        )
        device.calculateMetrics()

        val ptwSensor = device.powerToWeightSensor
        // Value should remain unassigned / null because recording is suspended
        assertNull(ptwSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_negativeOrInvalidPower_publishesNull() {
        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f }
        )

        every { powerSensorData.value } returns -10.0
        device.calculateMetrics()
        assertNull(device.powerToWeightSensor.sensorData.value)

        every { powerSensorData.value } returns Double.NaN
        device.calculateMetrics()
        assertNull(device.powerToWeightSensor.sensorData.value)

        every { powerSensorData.value } returns Double.POSITIVE_INFINITY
        device.calculateMetrics()
        assertNull(device.powerToWeightSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_npWarmupWindow_emitsValidFromFirstSecond() {
        every { powerSensorData.value } returns 250.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 }
        )

        for (i in 1..29) {
            device.calculateMetrics()
            assertEquals(250.0, device.normalizedPowerSensor.sensorData.value as Double, 0.001)
            assertEquals(1.0, device.intensityFactorSensor.sensorData.value as Double, 0.001)
        }

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_npAndIf_emitsValidValuesAfter30Seconds() {
        every { powerSensorData.value } returns 200.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 }
        )

        for (i in 1..30) {
            device.calculateMetrics()
        }

        val npSensor = device.normalizedPowerSensor
        val ifSensor = device.intensityFactorSensor

        assertEquals(200.0, npSensor.sensorData.value as Double, 0.001)
        assertEquals(200.0 / 250.0, ifSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_unconfiguredFtp_emitsNullIf() {
        every { powerSensorData.value } returns 200.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { null } // Unconfigured FTP
        )

        for (i in 1..30) {
            device.calculateMetrics()
        }

        assertEquals(200.0, device.normalizedPowerSensor.sensorData.value as Double, 0.001)
        assertNull(device.intensityFactorSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_pauseGating_freezesNpAndIf() {
        every { powerSensorData.value } returns 200.0
        var isSuspended = false

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { isSuspended },
            { 75.0f },
            { 200 }
        )

        // 10 active ticks at 200W -> NP = 200W, IF = 1.0
        for (i in 1..10) {
            device.calculateMetrics()
            assertEquals(200.0, device.normalizedPowerSensor.sensorData.value as Double, 0.001)
            assertEquals(1.0, device.intensityFactorSensor.sensorData.value as Double, 0.001)
        }

        // Suspend recording for 30 ticks with different raw power (400W): sensors must stay frozen at 200W / 1.0
        every { powerSensorData.value } returns 400.0
        isSuspended = true
        for (i in 1..30) {
            device.calculateMetrics()
            assertEquals(200.0, device.normalizedPowerSensor.sensorData.value as Double, 0.001)
            assertEquals(1.0, device.intensityFactorSensor.sensorData.value as Double, 0.001)
        }

        // Resume at 200W: NP and IF continue smoothly at 200W / 1.0
        every { powerSensorData.value } returns 200.0
        isSuspended = false
        for (i in 1..20) {
            device.calculateMetrics()
            assertEquals(200.0, device.normalizedPowerSensor.sensorData.value as Double, 0.001)
            assertEquals(1.0, device.intensityFactorSensor.sensorData.value as Double, 0.001)
        }

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_workKj_accumulatesCorrectly() {
        every { powerSensorData.value } returns 200.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 200 }
        )

        for (i in 1..10) {
            device.calculateMetrics()
        }

        // 10 ticks at 200W = 2000 Joules = 2.0 kJ
        assertEquals(2.0, device.workKjSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_tss_calculatesCorrectly() {
        every { powerSensorData.value } returns 200.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 200 }
        )

        // Growing warm-up window: first 29 ticks emit live TSS = i / 36.0
        for (i in 1..29) {
            device.calculateMetrics()
            assertNotNull(device.tssSensor.sensorData.value)
            assertEquals(i.toDouble() / 36.0, device.tssSensor.sensorData.value as Double, 0.01)
        }

        // 30th tick: NP = 200, IF = 1.0, t = 30s
        // TSS = (30 * 200 * 1.0) / (200 * 36) = 30 / 36 = 0.8333...
        device.calculateMetrics()
        assertNotNull(device.tssSensor.sensorData.value)
        assertEquals(30.0 / 36.0, device.tssSensor.sensorData.value as Double, 0.01)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_tss_unconfiguredFtp_returnsNull() {
        every { powerSensorData.value } returns 200.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { null } // Unconfigured FTP
        )

        for (i in 1..30) {
            device.calculateMetrics()
        }

        assertNotNull(device.normalizedPowerSensor.sensorData.value)
        assertNull(device.intensityFactorSensor.sensorData.value)
        assertNull(device.tssSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_pauseGating_freezesWorkAndTss() {
        every { powerSensorData.value } returns 200.0
        var isSuspended = false

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { isSuspended },
            { 75.0f },
            { 200 }
        )

        for (i in 1..5) {
            device.calculateMetrics()
        }
        assertEquals(1.0, device.workKjSensor.sensorData.value as Double, 0.001)

        // Suspend
        isSuspended = true
        for (i in 1..10) {
            device.calculateMetrics()
            assertEquals(1.0, device.workKjSensor.sensorData.value as Double, 0.001)
        }

        // Resume
        isSuspended = false
        device.calculateMetrics()
        assertEquals(1.2, device.workKjSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_longPause_flushesRollingWindowAndRetainsNpContinuity() {
        every { powerSensorData.value } returns 200.0
        var isSuspended = false
        var currentTime = 1_000_000L

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { isSuspended },
            { 75.0f },
            { 200 },
            { currentTime }
        )

        // Warm up and fill initial 30s window
        for (i in 1..30) {
            currentTime += 1000L
            device.calculateMetrics()
        }
        assertEquals(200.0, device.normalizedPowerSensor.sensorData.value as Double, 0.001)
        val prePauseNp = device.normalizedPowerSensor.sensorData.value as Double

        // Pause for 45 seconds (> 30s threshold)
        isSuspended = true
        device.calculateMetrics() // marks mSuspendedStartMs = currentTime
        currentTime += 45_000L
        device.calculateMetrics() // still paused

        // Resume after 45s pause: window must be flushed, but pre-pause NP retained during post-pause refill
        isSuspended = false
        currentTime += 1000L
        device.calculateMetrics() // tick 1 after resume: window flushed, window.size == 1
        assertEquals(prePauseNp, device.normalizedPowerSensor.sensorData.value as Double, 0.001)

        // Ticks 2..29 after resume: window still filling (< 30) -> NP retained
        for (i in 2..29) {
            currentTime += 1000L
            device.calculateMetrics()
            assertEquals(prePauseNp, device.normalizedPowerSensor.sensorData.value as Double, 0.001)
        }

        // Tick 30 after resume: window has 30 fresh samples, rolling mean resumes update
        currentTime += 1000L
        device.calculateMetrics()
        assertEquals(200.0, device.normalizedPowerSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_wPrimeBal_depletesAboveCpAndReconstitutesDuringPause() {
        var currentPower = 380.0
        every { powerSensorData.value } answers { currentPower }
        var isSuspended = false
        var currentTime = 1_000_000L

        // CP = 280W, W' = 20.0 kJ
        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { isSuspended },
            { 75.0f },
            { 270 },
            { currentTime },
            { 280 },
            { 20.0f }
        )

        // 10 seconds at 380W (100W above CP = 1000 J = 1.0 kJ depletion -> 19.0 kJ remaining)
        for (i in 1..10) {
            currentTime += 1000L
            device.calculateMetrics()
        }
        assertEquals(19.0, device.wPrimeBalSensor.sensorData.value as Double, 0.01)

        // Suspend recording (paused/stopped) for 10 ticks -> W'bal must reconstitute exponentially at P=0W
        isSuspended = true
        for (i in 1..10) {
            currentTime += 1000L
            device.calculateMetrics()
        }
        val afterPauseReconstitution = device.wPrimeBalSensor.sensorData.value as Double
        org.junit.Assert.assertTrue("W'bal must reconstitute during pause at 0W", afterPauseReconstitution > 19.0)
        org.junit.Assert.assertTrue("W'bal must stay <= 20.0 kJ capacity", afterPauseReconstitution <= 20.0)

        // Reset session restores W'bal to full capacity on next active tick at <= CP
        device.resetSession()
        isSuspended = false
        currentPower = 200.0
        currentTime += 1000L
        device.calculateMetrics()
        assertEquals(20.0, device.wPrimeBalSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_wPrimeBal_fallsBackToFtpWhenCpUnconfiguredAndEmitsNullWithoutCapacity() {
        every { powerSensorData.value } returns 350.0
        var currentTime = 1_000_000L

        // CP = null, FTP = 250W, W' = 15.0 kJ -> uses FTP (250W) as effective CP (100W depletion = 0.1 kJ per second)
        val deviceWithFtpFallback = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { currentTime },
            { null },
            { 15.0f }
        )
        currentTime += 1000L
        deviceWithFtpFallback.calculateMetrics()
        assertEquals(14.9, deviceWithFtpFallback.wPrimeBalSensor.sensorData.value as Double, 0.001)
        deviceWithFtpFallback.shutDown()

        // W' = 0.0f -> wPrimeBalSensor emits null
        val deviceWithoutWPrime = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { currentTime },
            { 280 },
            { 0.0f }
        )
        currentTime += 1000L
        deviceWithoutWPrime.calculateMetrics()
        assertNull(deviceWithoutWPrime.wPrimeBalSensor.sensorData.value)
        deviceWithoutWPrime.shutDown()
    }

    @Test
    fun testResolveEffectiveCpWattsMultiTierFallback() {
        var currentTime = 1_000_000L

        // Tier 1: CP configured (280W) and FTP configured (250W) -> returns CP (280W)
        val deviceTier1 = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { currentTime },
            { 280 },
            { 20.0f }
        )
        assertEquals(280, deviceTier1.resolveEffectiveCpWatts())
        deviceTier1.shutDown()

        // Tier 2: CP null/unconfigured (0W or null), FTP configured (250W) -> returns FTP (250W)
        val deviceTier2 = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { currentTime },
            { null },
            { 20.0f }
        )
        assertEquals(250, deviceTier2.resolveEffectiveCpWatts())
        deviceTier2.shutDown()

        // Tier 3: CP null, FTP null/0 -> falls back to DEFAULT_FALLBACK_CP_WATTS (200W)
        val deviceTier3 = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 0 },
            { currentTime },
            { 0 },
            { 20.0f }
        )
        assertEquals(CyclingPowerMetricsDevice.DEFAULT_FALLBACK_CP_WATTS, deviceTier3.resolveEffectiveCpWatts())
        deviceTier3.shutDown()
    }

    @Test
    fun testResetSessionRestoresCapacityWithFtpFallback() {
        var currentTime = 1_000_000L
        every { powerSensorData.value } returns 350.0

        // CP = null, FTP = 250W, W' = 15.0 kJ
        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { currentTime },
            { null },
            { 15.0f }
        )
        // 1 tick depletion (350W vs 250W = 100W * 1s = 0.1 kJ)
        currentTime += 1000L
        device.calculateMetrics()
        assertEquals(14.9, device.wPrimeBalSensor.sensorData.value as Double, 0.001)

        // resetSession() must restore full 15.0 kJ capacity even when CP is null because FTP is configured
        device.resetSession()
        assertEquals(15.0, device.wPrimeBalSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }
}
