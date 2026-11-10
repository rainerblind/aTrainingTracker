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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Integration unit test suite for [CyclingPowerMetricsDevice] Mean Maximal Power (MMP)
 * duration metrics, rolling averages, session peak tracking, pause gating, and reset parity
 * conforming to REQ-TRK-058 and TST-TRK-058.2.
 */
class CyclingPowerMetricsDeviceMmpTest {

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
    fun mmpSensorsAreRegisteredAndInitiallyNull_Rule52() {
        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f }
        )

        assertNotNull(device.power5sSensor)
        assertNotNull(device.power1mSensor)
        assertNotNull(device.power5mSensor)
        assertNotNull(device.power20mSensor)
        assertNotNull(device.powerPeak5sSensor)
        assertNotNull(device.powerPeak1mSensor)
        assertNotNull(device.powerPeak5mSensor)
        assertNotNull(device.powerPeak20mSensor)

        assertNull(device.power5sSensor.sensorData.value)
        assertNull(device.power1mSensor.sensorData.value)
        assertNull(device.power5mSensor.sensorData.value)
        assertNull(device.power20mSensor.sensorData.value)
        assertNull(device.powerPeak5sSensor.sensorData.value)
        assertNull(device.powerPeak1mSensor.sensorData.value)
        assertNull(device.powerPeak5mSensor.sensorData.value)
        assertNull(device.powerPeak20mSensor.sensorData.value)

        device.shutDown()
    }

    @Test
    fun calculateMetricsUpdatesRollingMeansAndSessionPeaks_REQ_TRK_058_2() {
        every { powerSensorData.value } returns 300.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f }
        )

        // 3 consecutive 1-Hz ticks at 300W
        repeat(3) {
            device.calculateMetrics()
        }

        assertEquals(300.0, device.power5sSensor.sensorData.value as Double, 0.001)
        assertEquals(300.0, device.powerPeak5sSensor.sensorData.value as Double, 0.001)
        assertEquals(300.0, device.power1mSensor.sensorData.value as Double, 0.001)
        assertEquals(300.0, device.powerPeak1mSensor.sensorData.value as Double, 0.001)

        // 4th tick: power drops to 100W
        every { powerSensorData.value } returns 100.0
        device.calculateMetrics()

        // 5s average is now (300*3 + 100)/4 = 1000/4 = 250W
        assertEquals(250.0, device.power5sSensor.sensorData.value as Double, 0.001)
        // Peak remains at 300W
        assertEquals(300.0, device.powerPeak5sSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun pauseSuspensionFreezesMmpAccumulators_REQ_TRK_058_5() {
        var isSuspended = false
        every { powerSensorData.value } returns 400.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { isSuspended },
            { 75.0f }
        )

        device.calculateMetrics()
        assertEquals(400.0, device.power5sSensor.sensorData.value as Double, 0.001)
        assertEquals(1, device.accumulator5s.count)

        // Enter pause
        isSuspended = true
        device.calculateMetrics()
        // Accumulator count remains 1, not incremented during pause
        assertEquals(1, device.accumulator5s.count)
        assertEquals(400.0, device.power5sSensor.sensorData.value as Double, 0.001)

        device.shutDown()
    }

    @Test
    fun resetSessionClearsAllMmpState_Rule51() {
        every { powerSensorData.value } returns 350.0

        val device = CyclingPowerMetricsDevice(
            context,
            sensorManager,
            { false },
            { 75.0f }
        )

        repeat(5) { device.calculateMetrics() }
        assertEquals(350.0, device.power5sSensor.sensorData.value as Double, 0.001)
        assertEquals(350.0, device.powerPeak5sSensor.sensorData.value as Double, 0.001)

        device.resetSession()

        assertEquals(0, device.accumulator5s.count)
        assertEquals(0, device.accumulator1m.count)
        assertNull(device.power5sSensor.sensorData.value)
        assertNull(device.powerPeak5sSensor.sensorData.value)
        assertNull(device.power1mSensor.sensorData.value)
        assertNull(device.powerPeak1mSensor.sensorData.value)

        device.shutDown()
    }
}
