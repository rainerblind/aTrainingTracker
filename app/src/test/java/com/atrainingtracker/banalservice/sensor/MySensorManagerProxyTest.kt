/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.banalservice.sensor

import android.content.Context
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test verifying persistent [ProxySensor] retention in [MySensorManager] across sensor
 * unregistration and re-registration cycles without listener loss (REQ-TRK-055.1, TST-TRK-055.1, ATT-6257).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MySensorManagerProxyTest {

    private lateinit var context: Context
    private lateinit var sensorManager: MySensorManager

    @Before
    fun setUp() {
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        context = mockk(relaxed = true)
        sensorManager = MySensorManager(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockDevice(type: DeviceType): MyDevice {
        return mockk(relaxed = true) {
            every { deviceType } returns type
            every { name } returns "TestDevice_${type.name}"
        }
    }

    @Test
    fun registerAndUnregister_retainsProxySensorInMapWithNullSource() {
        val device = mockDevice(DeviceType.SPEED_AND_LOCATION_GPS)
        val gpsSpeedSensor = object : MySensor<Double>(device, SensorType.SPEED_mps) {}

        // 1. Register sensor -> ProxySensor created in MySensorManager
        sensorManager.registerSensor(gpsSpeedSensor)
        val proxy1 = sensorManager.getSensor(SensorType.SPEED_mps)
        assertNotNull(proxy1)
        assertTrue(proxy1 is ProxySensor<*>)
        val proxySensor = proxy1 as ProxySensor<Double>
        assertSame(gpsSpeedSensor, proxySensor.sourceSensor)

        // 2. Unregister sensor -> ProxySensor MUST be retained, but source sensor becomes null
        sensorManager.unregisterSensor(gpsSpeedSensor)
        val proxyAfterUnregister = sensorManager.getSensor(SensorType.SPEED_mps)
        assertNotNull(proxyAfterUnregister)
        assertSame(proxySensor, proxyAfterUnregister)
        assertNull(proxySensor.sourceSensor)
        assertNull(proxySensor.value)
    }

    @Test
    fun reconnectSensor_reusesRetainedProxyAndRestoresListenerUpdates() {
        val device1 = mockDevice(DeviceType.SPEED_AND_LOCATION_GPS)
        val gpsSpeed1 = object : MySensor<Double>(device1, SensorType.SPEED_mps) {}

        // 1. Initial registration
        sensorManager.registerSensor(gpsSpeed1)
        val proxy = sensorManager.getSensor(SensorType.SPEED_mps) as ProxySensor<Double>

        // Register a listener on the proxy (like FilterManager does)
        val receivedValues = mutableListOf<Double?>()
        val listener = MySensor.SensorListener<Double> { value -> receivedValues.add(value) }
        proxy.addSensorListener(listener)
        receivedValues.clear()

        // Emit sample from device 1
        gpsSpeed1.newValue(10.5)
        assertEquals(listOf(10.5), receivedValues)

        // 2. Disconnect/unregister device 1
        sensorManager.unregisterSensor(gpsSpeed1)
        assertNull(proxy.sourceSensor)

        // 3. Reconnect via a new device/sensor instance (e.g. GPS re-acquired)
        val device2 = mockDevice(DeviceType.SPEED_AND_LOCATION_GPS)
        val gpsSpeed2 = object : MySensor<Double>(device2, SensorType.SPEED_mps) {}
        sensorManager.registerSensor(gpsSpeed2)

        // Proxy instance must be reused
        val proxyReconnected = sensorManager.getSensor(SensorType.SPEED_mps)
        assertSame(proxy, proxyReconnected)
        assertSame(gpsSpeed2, proxy.sourceSensor)

        // New samples from device 2 must arrive at the listener seamlessly
        gpsSpeed2.newValue(12.8)
        assertEquals(listOf(10.5, 12.8), receivedValues.filterNotNull())
    }

    @Test
    fun shutDown_clearsSourceAndInternalMaps() {
        val device = mockDevice(DeviceType.HRM)
        val hrSensor = object : MySensor<Int>(device, SensorType.HR) {}
        sensorManager.registerSensor(hrSensor)

        val proxy = sensorManager.getSensor(SensorType.HR) as ProxySensor<Int>
        assertSame(hrSensor, proxy.sourceSensor)

        sensorManager.shutDown()
        assertNull(proxy.sourceSensor)
    }
}
