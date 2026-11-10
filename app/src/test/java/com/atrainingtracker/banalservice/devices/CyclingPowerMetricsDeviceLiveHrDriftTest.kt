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
}
