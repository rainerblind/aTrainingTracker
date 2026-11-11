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
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.database.CardioPowerTrendSample
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Unit tests verifying real-time 1st-order dynamical Live HR Drift calculation,
 * growing warm-up window (Rule 40), sensor-presence gating (Rule 52), and
 * lifecycle parity (Rule 51) in [CyclingPowerMetricsDevice]
 * (REQ-REV-012, TST-REV-013.1, TST-REV-013.2).
 */
class CyclingPowerMetricsDeviceLiveHrDriftTest {

    private lateinit var context: Context
    private lateinit var sensorManager: MySensorManager
    private lateinit var powerSensor: MySensor<Double>
    private lateinit var powerSensorData: SensorData<Double>
    private lateinit var hrSensor: MySensor<Double>
    private lateinit var hrSensorData: SensorData<Double>

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
        hrSensor = mockk(relaxed = true)
        hrSensorData = mockk(relaxed = true)

        every { sensorManager.getSensor(SensorType.POWER) } returns powerSensor
        every { powerSensor.sensorData } returns powerSensorData

        every { sensorManager.getSensor(SensorType.HR) } returns hrSensor
        every { hrSensor.sensorData } returns hrSensorData
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testLiveHrDrift_steadyStateExactMatch_emitsZeroDrift() {
        // Given baseline: tau=40s, HR0=90 bpm, K=0.20 bpm/W -> HR@200W = 130 bpm
        every { powerSensorData.value } returns 200.0
        every { hrSensorData.value } returns 130.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { 1000L },
            { 250 },
            { 20.0f },
            { true },
            { CyclingPowerMetricsDevice.DynamicalBaseline(40.0, 90.0, 0.20) }
        )

        // When: 1st tick at 200W, HR=130 bpm
        device.calculateMetrics()

        val driftSensor = device.liveHrDriftSensor
        assertNotNull("Live HR Drift sensor must be registered", driftSensor)
        val value = driftSensor.sensorData.value as Double
        assertEquals(0.0, value, 0.001)

        device.shutDown()
    }

    @Test
    fun testLiveHrDrift_growingWarmUpWindow_emitsFromFirstSampleWithoutBlankDelay() {
        // Rule 40: no 30s delay. Emits valid smoothed value starting at k=1.
        // Baseline: tau=40s, HR0=80, K=0.25 -> at 200W, HR_pred = 80 + 0.25*200 = 130 bpm.
        // Actual HR = 135 bpm -> residual = +5 bpm.
        every { powerSensorData.value } returns 200.0
        every { hrSensorData.value } returns 135.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 70.0f },
            { 250 },
            { 1000L },
            { 250 },
            { 20.0f },
            { true },
            { CyclingPowerMetricsDevice.DynamicalBaseline(40.0, 80.0, 0.25) }
        )

        device.calculateMetrics()
        val drift1 = device.liveHrDriftSensor.sensorData.value as Double
        assertEquals(5.0, drift1, 0.001)

        // 2nd tick
        device.calculateMetrics()
        val drift2 = device.liveHrDriftSensor.sensorData.value as Double
        assertEquals(5.0, drift2, 0.001)

        device.shutDown()
    }

    @Test
    fun testLiveHrDrift_stepResponse_appliesLagCompensation() {
        // At t=0, state x=100W. Step to 300W.
        // With tau=40s, alpha=exp(-1/40) ~= 0.9753.
        // x1 = alpha*100 + (1-alpha)*300 = 97.53 + 4.94 = 104.94W.
        // HR0=90, K=0.20 -> HR_pred1 = 90 + 0.2*104.94 = 110.99 bpm.
        // If actual HR is 120 bpm, residual = 120 - 110.99 = 9.01 -> rounded to +9.
        every { powerSensorData.value } returns 100.0
        every { hrSensorData.value } returns 110.0 // 90 + 0.2*100 = 110 (0 drift at start)

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { 1000L },
            { 250 },
            { 20.0f },
            { true },
            { CyclingPowerMetricsDevice.DynamicalBaseline(40.0, 90.0, 0.20) }
        )

        device.calculateMetrics()
        assertEquals(0.0, device.liveHrDriftSensor.sensorData.value as Double, 0.001)

        // Step power to 300W, HR to 120 bpm
        every { powerSensorData.value } returns 300.0
        every { hrSensorData.value } returns 120.0
        device.calculateMetrics()

        val driftStep = device.liveHrDriftSensor.sensorData.value as Double
        // Residual at tick 2: 120 - (90 + 0.2*(exp(-1/40)*100 + (1-exp(-1/40))*300)) = 9.014 bpm.
        // Window has [0, 9.014], mean is ~4.5 bpm -> rounded to 5.0.
        assertTrue("Drift must reflect lag compensation", driftStep > 0.0)

        device.shutDown()
    }

    @Test
    fun testLiveHrDrift_gating_disabledSettingEmitsNull() {
        // Rule 52: when enableCardioPowerDynamics == false, emit null
        every { powerSensorData.value } returns 200.0
        every { hrSensorData.value } returns 135.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { 1000L },
            { 250 },
            { 20.0f },
            { false }, // Disabled
            { CyclingPowerMetricsDevice.DynamicalBaseline(40.0, 90.0, 0.20) }
        )

        device.calculateMetrics()
        assertNull(device.liveHrDriftSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testLiveHrDrift_gating_missingHrSensorEmitsNull() {
        // Rule 52: when HR sensor is not paired/registered in sensorManager, emit null
        every { sensorManager.getSensor(SensorType.HR) } returns null
        every { powerSensorData.value } returns 200.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { 1000L },
            { 250 },
            { 20.0f },
            { true },
            { CyclingPowerMetricsDevice.DynamicalBaseline(40.0, 90.0, 0.20) }
        )

        device.calculateMetrics()
        assertNull(device.liveHrDriftSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testLiveHrDrift_gating_missingPowerSensorEmitsNull() {
        // Rule 52: when POWER sensor is not paired/registered in sensorManager, emit null
        every { sensorManager.getSensor(SensorType.POWER) } returns null
        every { hrSensorData.value } returns 135.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { 1000L },
            { 250 },
            { 20.0f },
            { true },
            { CyclingPowerMetricsDevice.DynamicalBaseline(40.0, 90.0, 0.20) }
        )

        device.calculateMetrics()
        assertNull(device.liveHrDriftSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testLiveHrDrift_lifecycleParity_resetSessionClearsState() {
        // Rule 51: resetSession() must reset drift state and sensor value
        every { powerSensorData.value } returns 200.0
        every { hrSensorData.value } returns 140.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { 1000L },
            { 250 },
            { 20.0f },
            { true },
            { CyclingPowerMetricsDevice.DynamicalBaseline(40.0, 90.0, 0.20) }
        )

        device.calculateMetrics()
        assertNotNull(device.liveHrDriftSensor.sensorData.value)

        device.resetSession()
        assertNull(device.liveHrDriftSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testLiveHrDrift_pauseHandling_flushesWindowAfterLongPause() {
        var suspended = false
        var currentTime = 1000L
        every { powerSensorData.value } returns 200.0
        every { hrSensorData.value } returns 140.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { suspended },
            { 75.0f },
            { 250 },
            { currentTime },
            { 250 },
            { 20.0f },
            { true },
            { CyclingPowerMetricsDevice.DynamicalBaseline(40.0, 90.0, 0.20) }
        )

        // 1. Active tick
        device.calculateMetrics()
        assertNotNull(device.liveHrDriftSensor.sensorData.value)

        // 2. Pause tracking
        suspended = true
        device.calculateMetrics()
        assertNull(device.liveHrDriftSensor.sensorData.value)

        // 3. Long pause (> 30s = 35s later)
        currentTime += 35000L
        suspended = false
        // Next tick flushes and starts fresh
        device.calculateMetrics()
        assertNotNull(device.liveHrDriftSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testZeroQueryLoop_baselineAndEnablementCachedAtSessionReset() {
        val enabledProvider = mockk<CyclingPowerMetricsDevice.CardioPowerDynamicsEnabledProvider>()
        val baselineProvider = mockk<CyclingPowerMetricsDevice.CardioPowerBaselineProvider>()

        every { enabledProvider.isCardioPowerDynamicsEnabled(any()) } returns true
        every { baselineProvider.getBaseline(any()) } returns CyclingPowerMetricsDevice.DynamicalBaseline(45.0, 95.0, 0.22)

        every { powerSensorData.value } returns 200.0
        every { hrSensorData.value } returns 139.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { 1000L },
            { 250 },
            { 20.0f },
            enabledProvider,
            baselineProvider
        )

        // Providers invoked once during initial resetSession() in constructor
        verify(exactly = 1) { enabledProvider.isCardioPowerDynamicsEnabled(any()) }
        verify(exactly = 1) { baselineProvider.getBaseline(any()) }

        // Execute 1-Hz calculateMetrics() loop for 10 ticks
        repeat(10) {
            device.calculateMetrics()
        }

        // Zero additional provider queries during 1-Hz loop!
        verify(exactly = 1) { enabledProvider.isCardioPowerDynamicsEnabled(any()) }
        verify(exactly = 1) { baselineProvider.getBaseline(any()) }

        // Calling resetSession() refreshes cache
        device.resetSession()
        verify(exactly = 2) { enabledProvider.isCardioPowerDynamicsEnabled(any()) }
        verify(exactly = 2) { baselineProvider.getBaseline(any()) }

        device.shutDown()
    }

    @Test
    fun testCardioBaselineProvider_30DayRollingAverageResolution() {
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        val mockDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockDb

        val nowMs = 1_700_000_000_000L
        val tenDaysAgoMs = nowMs - 10L * 24L * 3600L * 1000L
        val twentyDaysAgoMs = nowMs - 20L * 24L * 3600L * 1000L
        val fortyDaysAgoMs = nowMs - 40L * 24L * 3600L * 1000L

        val rec1 = mockk<CardioPowerDynamicsRecord>(relaxed = true)
        every { rec1.tauStart } returns 38.0
        every { rec1.hr0Start } returns 88.0
        every { rec1.kStart } returns 0.18

        val rec2 = mockk<CardioPowerDynamicsRecord>(relaxed = true)
        every { rec2.tauStart } returns 42.0
        every { rec2.hr0Start } returns 92.0
        every { rec2.kStart } returns 0.22

        val rec3 = mockk<CardioPowerDynamicsRecord>(relaxed = true)
        every { rec3.tauStart } returns 50.0
        every { rec3.hr0Start } returns 100.0
        every { rec3.kStart } returns 0.30

        val sample1 = CardioPowerTrendSample(1L, tenDaysAgoMs, rec1)
        val sample2 = CardioPowerTrendSample(2L, twentyDaysAgoMs, rec2)
        val sample3 = CardioPowerTrendSample(3L, fortyDaysAgoMs, rec3)

        every { mockDb.getCardioPowerDynamicsFitnessTrendSamples(0.50) } returns listOf(sample3, sample2, sample1)

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { nowMs },
            { 250 },
            { 20.0f },
            { true },
            null // Default provider uses 30-day average
        )

        val baseline = device.currentBaseline
        assertNotNull(baseline)
        // Average of sample 1 and 2: tau=(38+42)/2=40, hr0=(88+92)/2=90, k=(0.18+0.22)/2=0.20
        assertEquals(40.0, baseline.tau, 0.001)
        assertEquals(90.0, baseline.hr0, 0.001)
        assertEquals(0.20, baseline.k, 0.001)

        device.shutDown()
    }

    @Test
    fun testInRideMinute8To25Calibration_updatesBaselineWhenR2Qualifies() {
        val mockCalibrator = mockk<CyclingPowerMetricsDevice.InRideCalibrator>()
        every { mockCalibrator.calibrate(any(), any()) } returns CyclingPowerMetricsDevice.DynamicalBaseline(35.0, 85.0, 0.24)

        every { powerSensorData.value } returns 200.0
        every { hrSensorData.value } returns 133.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { 1000L },
            { 250 },
            { 20.0f },
            { true },
            { CyclingPowerMetricsDevice.DynamicalBaseline(40.0, 90.0, 0.20) },
            mockCalibrator
        )

        // Initial baseline
        assertEquals(40.0, device.currentBaseline.tau, 0.001)
        assertEquals(90.0, device.currentBaseline.hr0, 0.001)
        assertEquals(0.20, device.currentBaseline.k, 0.001)
        assertFalse(device.isInRideCalibrated)

        // Buffer 600 samples during active window (480s..1079s)
        device.setActivePowerDurationSec(479L)
        repeat(600) {
            device.calculateMetrics()
        }
        assertTrue("Expected >= 600 samples, was ${device.inRidePowerSamplesCount}", device.inRidePowerSamplesCount >= 600)
        assertFalse(device.isInRideCalibrated)

        // Advance to minute 25 (1500s)
        device.setActivePowerDurationSec(1499L)
        device.calculateMetrics()

        // Minute 25 reached (1500 sec) -> in-ride calibration triggered
        assertTrue(device.isInRideCalibrated)
        assertEquals(35.0, device.currentBaseline.tau, 0.001)
        assertEquals(85.0, device.currentBaseline.hr0, 0.001)
        assertEquals(0.24, device.currentBaseline.k, 0.001)

        // Subsequent ticks do not re-run calibrator
        device.calculateMetrics()
        verify(exactly = 1) {
            mockCalibrator.calibrate(any(), any())
        }

        device.shutDown()
    }

    @Test
    fun testInRideMinute8To25Calibration_retainsBaselineWhenR2BelowThreshold() {
        val mockCalibrator = mockk<CyclingPowerMetricsDevice.InRideCalibrator>()
        every { mockCalibrator.calibrate(any(), any()) } returns null

        every { powerSensorData.value } returns 200.0
        every { hrSensorData.value } returns 133.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { 1000L },
            { 250 },
            { 20.0f },
            { true },
            { CyclingPowerMetricsDevice.DynamicalBaseline(40.0, 90.0, 0.20) },
            mockCalibrator
        )

        device.setActivePowerDurationSec(479L)
        repeat(600) {
            device.calculateMetrics()
        }

        device.setActivePowerDurationSec(1499L)
        device.calculateMetrics()

        assertTrue(device.isInRideCalibrated)
        // Baseline retained from initial historical seed!
        assertEquals(40.0, device.currentBaseline.tau, 0.001)
        assertEquals(90.0, device.currentBaseline.hr0, 0.001)
        assertEquals(0.20, device.currentBaseline.k, 0.001)

        device.shutDown()
    }

    @Test
    fun testDefaultInRideCalibrator_fitsSyntheticStepSignal() {
        // Build 600 synthetic samples where true tau=35s, hr0=85 bpm, k=0.20
        val pArr = DoubleArray(600)
        val hArr = DoubleArray(600)
        var x = 150.0
        val alpha = exp(-1.0 / 35.0)
        for (i in 0 until 600) {
            val p = if ((i / 60) % 2 == 0) 150.0 else 250.0
            x = alpha * x + (1.0 - alpha) * p
            pArr[i] = p
            hArr[i] = 85.0 + 0.20 * x
        }

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f },
            { 250 },
            { 1000L },
            { 250 },
            { 20.0f },
            { true },
            { CyclingPowerMetricsDevice.DynamicalBaseline(40.0, 90.0, 0.20) }
        )

        device.setActivePowerDurationSec(479L)
        for (i in 0 until 600) {
            every { powerSensorData.value } returns pArr[i]
            every { hrSensorData.value } returns hArr[i]
            device.calculateMetrics()
        }

        device.setActivePowerDurationSec(1500L)
        every { powerSensorData.value } returns pArr[599]
        every { hrSensorData.value } returns hArr[599]
        device.calculateMetrics()

        assertTrue(device.isInRideCalibrated)
        // Verified updated baseline matches synthetic true values (tau ~ 35, hr0 ~ 85, k ~ 0.20)
        assertEquals(35.0, device.currentBaseline.tau, 4.0)
        assertEquals(85.0, device.currentBaseline.hr0, 4.0)
        assertEquals(0.20, device.currentBaseline.k, 0.04)

        device.shutDown()
    }
}
