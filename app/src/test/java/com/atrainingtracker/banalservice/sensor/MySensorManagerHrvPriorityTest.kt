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

import android.content.Context
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.filters.FilterData
import com.atrainingtracker.banalservice.filters.FilterManager
import com.atrainingtracker.banalservice.filters.FilterType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [MySensorManager] `HRV_PRIORITY` arbitration and [FilterManager]
 * pinned-vs-best resolution for per-device HRV sensors (`REQ-HRV-050.1` / `TST-HRV-050.1`).
 */
class MySensorManagerHrvPriorityTest {

    private lateinit var context: Context

    private class FakeDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceType: DeviceType,
        private val deviceName: String
    ) : MyDevice(context, sensorManager, deviceType) {
        override fun addSensors() {
            // Sensors attached explicitly in test cases
        }

        override fun getName(): String = deviceName

        fun attachAndRegister(sensorType: SensorType): MySensor<Double> {
            val sensor = MySensor<Double>(this, sensorType)
            addSensor(sensor)
            mMySensorManager.registerSensor(sensor)
            return sensor
        }
    }

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        context = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testHrvPriorityArray_prefersHrvMetricsOverHrmForAllFourHrvTypes_TST_HRV_050_1() {
        // REQ-HRV-050.1 / TST-HRV-050.1
        val hrvTypes = listOf(
            SensorType.HRV_RMSSD,
            SensorType.HRV_DFA_A1,
            SensorType.HRV_ARTIFACT_RATE,
            SensorType.HRV_SDNN
        )

        for (sensorType in hrvTypes) {
            val priority = MySensorManager.getPriorityArray(sensorType)
            assertNotNull("Priority array must be registered for $sensorType", priority)
            assertEquals(2, priority!!.size)
            assertEquals(DeviceType.HRV_METRICS, priority[0])
            assertEquals(DeviceType.HRM, priority[1])
            assertTrue(
                MySensorManager.getPriorityRank(sensorType, DeviceType.HRV_METRICS) <
                    MySensorManager.getPriorityRank(sensorType, DeviceType.HRM)
            )
        }
    }

    @Test
    fun testBestSensorResolution_remainsOnHrvMetricsEvenWhenHrmRegistersFirst_TST_HRV_050_1() {
        // REQ-HRV-050.1 / TST-HRV-050.1
        val sensorManager = object : MySensorManager(context) {
            override fun notifySensorsChanged() {}
        }
        val verityHrm = FakeDevice(context, sensorManager, DeviceType.HRM, "Polar Verity Sense")
        val h10Hrm = FakeDevice(context, sensorManager, DeviceType.HRM, "Polar H10")
        val globalHrv = FakeDevice(context, sensorManager, DeviceType.HRV_METRICS, "HRV metrics")

        val hrvTypes = listOf(
            SensorType.HRV_RMSSD,
            SensorType.HRV_DFA_A1,
            SensorType.HRV_ARTIFACT_RATE,
            SensorType.HRV_SDNN
        )

        // Register HRM sensors BEFORE global HRV_METRICS (would hijack Best Sensor under EnumMap order!)
        val veritySensors = hrvTypes.associateWith { verityHrm.attachAndRegister(it) }
        val h10Sensors = hrvTypes.associateWith { h10Hrm.attachAndRegister(it) }
        val globalSensors = hrvTypes.associateWith { globalHrv.attachAndRegister(it) }

        for (type in hrvTypes) {
            val proxy = sensorManager.getSensor(type) as ProxySensor<*>
            assertSame(
                "Best Sensor proxy for $type must bind to arbitrated HRV_METRICS device",
                globalSensors[type],
                proxy.sourceSensor
            )
        }

        // Verify FilterManager routes pinned deviceName to that HRM device and null to Best Sensor
        val deviceManager = mockk<DeviceManager>(relaxed = true)
        every { deviceManager.getMyDeviceByName("Polar H10") } returns h10Hrm
        every { deviceManager.getMyDeviceByName("Polar Verity Sense") } returns verityHrm
        val filterManager = FilterManager(context, deviceManager, sensorManager)

        for (type in hrvTypes) {
            val pinnedH10 = filterManager.resolveSensor(FilterData("Polar H10", type, FilterType.INSTANTANEOUS, 1.0))
            val pinnedVerity = filterManager.resolveSensor(FilterData("Polar Verity Sense", type, FilterType.INSTANTANEOUS, 1.0))
            val best = filterManager.resolveSensor(FilterData(null, type, FilterType.INSTANTANEOUS, 1.0))

            assertSame("Pinned Polar H10 must resolve to H10 $type sensor", h10Sensors[type], pinnedH10)
            assertSame("Pinned Polar Verity Sense must resolve to Verity $type sensor", veritySensors[type], pinnedVerity)
            assertSame("Best Sensor must resolve to MySensorManager proxy for $type", sensorManager.getSensor(type), best)
        }

        filterManager.shutDown()
        sensorManager.shutDown()
    }
}
