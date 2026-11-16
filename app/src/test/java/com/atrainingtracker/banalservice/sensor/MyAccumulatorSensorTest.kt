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

package com.atrainingtracker.banalservice.sensor

import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test for [MyAccumulatorSensor] baseline seeding, listener reactivity, and late-binding
 * inheritance from [MySensorManager] (REQ-TRK-078.4, TST-TRK-078.4).
 */
class MyAccumulatorSensorTest {

    @Before
    fun setUp() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns true
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testConstructor_inheritsRegisteredInitialValueFromSensorManager_double() {
        val mockManager = mockk<MySensorManager>()
        every { mockManager.getInitialValue(SensorType.DISTANCE_m) } returns 1098.8

        val mockDevice = mockk<MyDevice>()
        every { mockDevice.mySensorManager } returns mockManager

        val sensor = MyDoubleAccumulatorSensor(mockDevice, SensorType.DISTANCE_m, true)
        sensor.activateSensor()

        assertEquals("Sensor must inherit registered initial value from MySensorManager", 1098.8, sensor.value, 0.001)

        // Subsequent increment adds onto pre-crash baseline
        sensor.increment(10.0)
        assertEquals("Increment must add onto inherited baseline", 1108.8, sensor.value, 0.001)
    }

    @Test
    fun testConstructor_inheritsRegisteredInitialValueFromSensorManager_integer() {
        val mockManager = mockk<MySensorManager>()
        every { mockManager.getInitialValue(SensorType.TIME_TOTAL) } returns 643

        val mockDevice = mockk<MyDevice>()
        every { mockDevice.mySensorManager } returns mockManager

        val sensor = MyIntegerAccumulatorSensor(mockDevice, SensorType.TIME_TOTAL, false)
        sensor.activateSensor()

        assertEquals("Integer accumulator must inherit initial value", 643, sensor.value)

        sensor.increment(1)
        assertEquals("Tick must advance from restored initial value", 644, sensor.value)
    }

    @Test
    fun testConstructor_defaultsToZeroValue_whenNoRegisteredInitialValue() {
        val mockManager = mockk<MySensorManager>()
        every { mockManager.getInitialValue(SensorType.ASCENT) } returns null

        val mockDevice = mockk<MyDevice>()
        every { mockDevice.mySensorManager } returns mockManager

        val sensor = MyDoubleAccumulatorSensor(mockDevice, SensorType.ASCENT, true)
        sensor.activateSensor()

        assertEquals(0.0, sensor.value, 0.001)
    }

    @Test
    fun testSetInitialValue_notifiesRegisteredListeners() {
        val sensor = MyDoubleAccumulatorSensor(null, SensorType.DISTANCE_m, true)
        sensor.activateSensor()

        var reportedValue: Double? = null
        sensor.addSensorListener { reportedValue = it as? Double }

        sensor.setInitialValue(500.0)
        assertEquals("Listener must receive rehydrated total immediately", 500.0, reportedValue ?: 0.0, 0.001)

        sensor.increment(25.0)
        assertEquals("Subsequent increment must notify listener with baseline + delta", 525.0, reportedValue ?: 0.0, 0.001)
    }

    @Test
    fun testReset_clearsToZeroValueAndNotifiesListeners() {
        val sensor = MyDoubleAccumulatorSensor(null, SensorType.DISTANCE_m, true)
        sensor.activateSensor()
        sensor.setInitialValue(500.0)
        sensor.increment(50.0)
        assertEquals(550.0, sensor.value, 0.001)

        var reportedOnReset: Double? = null
        sensor.addSensorListener { reportedOnReset = it as? Double }

        sensor.reset()
        assertEquals(0.0, sensor.value, 0.001)
        assertEquals("Listener must be notified of 0.0 on reset", 0.0, reportedOnReset ?: -1.0, 0.001)
    }
}
