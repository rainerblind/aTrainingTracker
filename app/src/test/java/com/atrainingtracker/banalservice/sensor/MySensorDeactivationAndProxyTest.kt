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
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [MySensor.deactivateSensor], [MyAccumulatorSensor.deactivateSensor],
 * and [ProxySensor] silent deactivation / outside-lock null unbinding (`REQ-FIL-038.1` / `TST-FIL-038.1`).
 */
class MySensorDeactivationAndProxyTest {

    @Test
    fun testMySensorDeactivate_clearsValueAndNotifiesListenersNullLockFree_TST_FIL_038_1() {
        // REQ-FIL-038.1 / TST-FIL-038.1
        val mockDevice = mockk<MyDevice>(relaxed = true)
        val sensor = MySensor<Int>(mockDevice, SensorType.RR_INTERVAL)
        val observed = mutableListOf<Any?>()
        val sawDeactivatedInCallback = mutableListOf<Boolean>()

        sensor.addSensorListener { value ->
            observed.add(value)
            sawDeactivatedInCallback.add(!sensor.isActivated)
        }
        observed.clear()
        sawDeactivatedInCallback.clear()

        sensor.newValue(827)
        assertEquals(listOf(827), observed)
        assertEquals(827, sensor.value)

        // Deactivate must mark inactive and clear mValue BEFORE invoking listeners with null
        sensor.deactivateSensor()
        assertFalse(sensor.isActivated)
        assertNull(sensor.value)
        assertEquals(listOf(827, null), observed)
        assertTrue("Sensor must already be deactivated before null callback runs", sawDeactivatedInCallback.last())

        // Re-activating must not resurrect stale value 827
        sensor.activateSensor()
        assertNull("Re-activated sensor must keep cleared null value until next sample", sensor.value)
    }

    @Test
    fun testMyAccumulatorSensorDeactivate_staysSilentAndPreservesTotals_TST_FIL_038_1() {
        // REQ-FIL-038.1 / TST-FIL-038.1
        val mockDevice = mockk<MyDevice>(relaxed = true)
        val strides = MyIntegerAccumulatorSensor(mockDevice, SensorType.STRIDES, false, 0)
        val observed = mutableListOf<Any?>()
        strides.addSensorListener { observed.add(it) }
        observed.clear()

        strides.increment(42)
        assertEquals(listOf(42), observed)

        // Deactivating an accumulator must NOT notify listeners with null and must keep totals
        strides.deactivateSensor()
        assertFalse(strides.isActivated)
        assertEquals("Accumulator deactivateSensor must remain silent", listOf(42), observed)

        strides.activateSensor()
        assertEquals("Re-activated accumulator must retain accumulated total", 42, strides.value)
    }

    @Test
    fun testProxySensorDeactivateAndSetSourceNull_notifiesOnlyNonAccumulatorsAndTracksAccumulatorBacked_TST_FIL_038_1() {
        // REQ-FIL-038.1 / TST-FIL-038.1
        val mockDevice = mockk<MyDevice>(relaxed = true)

        // 1. Non-accumulator proxy: setSourceSensor(null) pushes null; deactivateSensor() stays silent
        val hrSource = MySensor<Int>(mockDevice, SensorType.HR)
        val hrProxy = ProxySensor(mockDevice, SensorType.HR, hrSource)
        assertFalse(hrProxy.isAccumulatorBacked)

        val hrObserved = mutableListOf<Any?>()
        hrProxy.addSensorListener { hrObserved.add(it) }
        hrObserved.clear()

        hrSource.newValue(148)
        assertEquals(listOf(148), hrObserved)

        // ProxySensor.deactivateSensor() must stay silent (lock safety during MySensorManager.shutDown)
        hrProxy.deactivateSensor()
        assertFalse(hrProxy.isActivated)
        assertEquals("ProxySensor.deactivateSensor must stay silent", listOf(148), hrObserved)

        hrProxy.activateSensor()
        hrProxy.setSourceSensor(null)
        assertEquals("Unbinding non-accumulator source from ProxySensor must push null", listOf(148, null), hrObserved)

        // 2. Accumulator proxy: constructor & setSourceSensor set isAccumulatorBacked; setSourceSensor(null) stays silent
        val stridesSource = MyIntegerAccumulatorSensor(mockDevice, SensorType.STRIDES, false, 0)
        stridesSource.increment(120)
        val stridesProxy = ProxySensor(mockDevice, SensorType.STRIDES, stridesSource)
        assertTrue("ProxySensor initialized with MyAccumulatorSensor must mark isAccumulatorBacked", stridesProxy.isAccumulatorBacked)

        val strideObserved = mutableListOf<Any?>()
        stridesProxy.addSensorListener { strideObserved.add(it) }
        strideObserved.clear()

        stridesProxy.setSourceSensor(null)
        assertTrue("isAccumulatorBacked must remain true after source unbinds", stridesProxy.isAccumulatorBacked)
        assertTrue("Unbinding accumulator source must NOT push null", strideObserved.isEmpty())

        // 3. Proxy initialized with null source, later bound to accumulator -> sets isAccumulatorBacked = true
        val lateProxy = ProxySensor<Int>(mockDevice, SensorType.STRIDES, null)
        assertFalse(lateProxy.isAccumulatorBacked)
        lateProxy.setSourceSensor(stridesSource)
        assertTrue(lateProxy.isAccumulatorBacked)
        lateProxy.setSourceSensor(null)
        assertTrue(lateProxy.isAccumulatorBacked)
    }
}
