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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [MyAccumulatorSensor.resetTo] (`ATT-8917`, `REQ-FIL-031.2`, `TST-FIL-031.2`).
 */
// Linked test specs: TST-FIL-031
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyAccumulatorSensorResetToTest {

    @Test
    fun resetTo_integerAccumulator_setsExactCompositeValueWithoutMutatingValueOrLastAbsolute() {
        val sensorManager = mockk<MySensorManager>(relaxed = true)
        every { sensorManager.getInitialValue(SensorType.TIME_LAP) } returns null
        val device = mockk<MyDevice>(relaxed = true)
        every { device.mySensorManager } returns sensorManager

        val sensor = MyIntegerAccumulatorSensor(device, SensorType.TIME_LAP, false)
        sensor.activateSensor()

        // Simulate 1865 s elapsed in Leg 1 + T2 + 15 s Run via accumulate()
        sensor.accumulate(1000)
        sensor.accumulate(2865) // mValue = 1865, mLastAbsoluteValue = 2865
        assertEquals(1865, sensor.value)

        // Contrast with reset() + setInitialValue(15) bug (which would yield 1880):
        // reset() followed by resetTo(15) MUST yield exact 15 s!
        sensor.reset()
        assertEquals(0, sensor.value)
        // Re-seed lastAbsoluteValue as active hardware tick, then test resetTo preserves it
        sensor.accumulate(2865)
        sensor.accumulate(2866) // +1 s -> value = 1, mLastAbsoluteValue = 2866
        assertEquals(1, sensor.value)

        var notifiedValue: Any? = null
        var heldLockInCallback = true
        sensor.addSensorListener { value ->
            notifiedValue = value
            heldLockInCallback = Thread.holdsLock(sensor)
        }

        sensor.resetTo(15)
        assertEquals(15, sensor.value)
        assertEquals(15, notifiedValue)
        assertFalse("Listeners must be notified outside synchronized(this) lock", heldLockInCallback)

        // Subsequent accumulate(2870) must add exact delta (2870 - 2866 = +4) -> 19!
        sensor.accumulate(2870)
        assertEquals(19, sensor.value)

        // Zero mutation of MySensorManager global initial values
        verify(exactly = 0) { sensorManager.setInitialValue(any(), any()) }
    }

    @Test
    fun resetTo_doubleAccumulator_preservesMonotonicDeltaAndInactiveGuard() {
        val sensor = MyDoubleAccumulatorSensor(null, SensorType.DISTANCE_m_LAP, false)
        sensor.activateSensor()
        sensor.accumulate(100.0)
        sensor.accumulate(14600.0) // +14500.0 m
        assertEquals(14500.0, sensor.value!!, 1e-6)

        sensor.resetTo(60.0)
        assertEquals(60.0, sensor.value!!, 1e-6)

        // Next cumulative tick adds +12.5 m -> 72.5 m (mLastAbsoluteValue preserved)
        sensor.accumulate(14612.5)
        assertEquals(72.5, sensor.value!!, 1e-6)

        // Inactive sensor updates mInitialValue without firing listeners
        sensor.deactivateSensor()
        var callbackCount = 0
        sensor.addSensorListener { callbackCount++ }
        sensor.resetTo(42.0)
        assertEquals(0, callbackCount)
        assertNull(sensor.value)
        sensor.activateSensor()
        assertEquals(42.0, sensor.value!!, 1e-6)
    }
}
