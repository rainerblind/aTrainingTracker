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
        mockkStatic(TrainingApplication::class)
        mockkStatic(SettingsDataStoreJavaHelper::class)
        mockkStatic(ContextCompat::class)

        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { TrainingApplication.isRecordingSuspended() } returns false
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false

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
        every { SettingsDataStoreJavaHelper.getAthleteWeightKg(context) } returns 75.0f
        every { powerSensorData.value } returns 300.0

        val device = CyclingPowerMetricsDevice(context, sensorManager)
        device.calculateMetrics()

        val ptwSensor = device.powerToWeightSensor
        assertEquals(4.0, ptwSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_fractionalPowerAndWeight_publishesExactRatio() {
        every { SettingsDataStoreJavaHelper.getAthleteWeightKg(context) } returns 65.0f
        every { powerSensorData.value } returns 250.0

        val device = CyclingPowerMetricsDevice(context, sensorManager)
        device.calculateMetrics()

        val ptwSensor = device.powerToWeightSensor
        assertEquals(250.0 / 65.0, ptwSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_zeroPower_publishesZeroWPerKg() {
        every { SettingsDataStoreJavaHelper.getAthleteWeightKg(context) } returns 75.0f
        every { powerSensorData.value } returns 0.0

        val device = CyclingPowerMetricsDevice(context, sensorManager)
        device.calculateMetrics()

        val ptwSensor = device.powerToWeightSensor
        assertEquals(0.0, ptwSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_unconfiguredWeight_publishesNull() {
        every { SettingsDataStoreJavaHelper.getAthleteWeightKg(context) } returns 0.0f
        every { powerSensorData.value } returns 300.0

        val device = CyclingPowerMetricsDevice(context, sensorManager)
        device.calculateMetrics()

        val ptwSensor = device.powerToWeightSensor
        assertNull(ptwSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_recordingSuspended_skipsCalculation() {
        every { TrainingApplication.isRecordingSuspended() } returns true
        every { SettingsDataStoreJavaHelper.getAthleteWeightKg(context) } returns 75.0f
        every { powerSensorData.value } returns 300.0

        val device = CyclingPowerMetricsDevice(context, sensorManager)
        device.calculateMetrics()

        val ptwSensor = device.powerToWeightSensor
        // Value should remain unassigned / null because recording is suspended
        assertNull(ptwSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun testCalculateMetrics_negativeOrInvalidPower_publishesNull() {
        every { SettingsDataStoreJavaHelper.getAthleteWeightKg(context) } returns 75.0f

        val device = CyclingPowerMetricsDevice(context, sensorManager)

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
}
