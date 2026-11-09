/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.banalservice.filters

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test verifying dynamic sensor filter re-binding in [FilterManager] when backing sensors
 * are re-registered or peripheral devices are recreated (REQ-TRK-055.2, REQ-TRK-055.3, TST-TRK-055.2, TST-TRK-055.3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilterManagerRebindTest {

    private lateinit var context: Context
    private lateinit var deviceManager: DeviceManager
    private lateinit var sensorManager: MySensorManager
    private lateinit var filterManager: FilterManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        deviceManager = mockk(relaxed = true)
        sensorManager = mockk(relaxed = true)
        filterManager = FilterManager(context, deviceManager, sensorManager)
    }

    @Test
    fun createFilter_existingFilterWithChangedSensor_rebindsListenerAndDeliversUpdates() {
        val mockDevice1 = mockk<MyDevice>(relaxed = true) { every { name } returns "CadenceSensor_1" }
        val sensor1 = object : MySensor<Double>(mockDevice1, SensorType.CADENCE) {}
        every { sensorManager.getSensor(SensorType.CADENCE) } returns sensor1

        val filterData = FilterData(null, SensorType.CADENCE, FilterType.INSTANTANEOUS, 0.0)

        // 1. Create filter bound to sensor1
        filterManager.createFilter(filterData)
        sensor1.newValue(85.0)

        var filteredData = filterManager.getFilteredSensorData(filterData)
        assertNotNull(filteredData)
        assertEquals(85.0, filteredData!!.value as Double, 0.001)

        // 2. Backing sensor changes to sensor2 (e.g. source switched or re-bound)
        val mockDevice2 = mockk<MyDevice>(relaxed = true) { every { name } returns "CadenceSensor_2" }
        val sensor2 = object : MySensor<Double>(mockDevice2, SensorType.CADENCE) {}
        every { sensorManager.getSensor(SensorType.CADENCE) } returns sensor2

        // Re-call createFilter with identical FilterData
        filterManager.createFilter(filterData)

        // 3. Emit on new sensor -> filter must receive new value
        sensor2.newValue(92.0)
        filteredData = filterManager.getFilteredSensorData(filterData)
        assertEquals(92.0, filteredData!!.value as Double, 0.001)

        // Old sensor updates must be ignored
        sensor1.newValue(60.0)
        filteredData = filterManager.getFilteredSensorData(filterData)
        assertEquals(92.0, filteredData!!.value as Double, 0.001)
    }

    @Test
    fun rebindExistingFilters_namedPeripheralDeviceRecreated_migratesFilterListener() {
        val deviceName = "Polar_H10"
        val filterData = FilterData(deviceName, SensorType.HR, FilterType.INSTANTANEOUS, 0.0)

        // Initial device instance
        val device1 = mockk<MyDevice>(relaxed = true) { every { name } returns deviceName }
        val hrSensor1 = object : MySensor<Int>(device1, SensorType.HR) {}
        every { device1.getSensor(SensorType.HR) } returns hrSensor1
        every { deviceManager.getMyDeviceByName(deviceName) } returns device1

        filterManager.createFilter(filterData)
        hrSensor1.newValue(145)
        assertEquals(145, filterManager.getFilteredSensorData(filterData)?.value)

        // Device is disconnected and recreated with new instance
        val device2 = mockk<MyDevice>(relaxed = true) { every { name } returns deviceName }
        val hrSensor2 = object : MySensor<Int>(device2, SensorType.HR) {}
        every { device2.getSensor(SensorType.HR) } returns hrSensor2
        every { deviceManager.getMyDeviceByName(deviceName) } returns device2

        // Trigger rebind
        filterManager.rebindExistingFilters()

        // hrSensor2 emits
        hrSensor2.newValue(152)
        assertEquals(152, filterManager.getFilteredSensorData(filterData)?.value)

        // hrSensor1 emits and is ignored
        hrSensor1.newValue(120)
        assertEquals(152, filterManager.getFilteredSensorData(filterData)?.value)
    }

    @Test
    fun rebindFilter_seedsCurrentValueIfPresent() {
        val filterData = FilterData(null, SensorType.SPEED_mps, FilterType.INSTANTANEOUS, 0.0)
        val mockDevice = mockk<MyDevice>(relaxed = true)
        val speedSensor = object : MySensor<Double>(mockDevice, SensorType.SPEED_mps) {}
        speedSensor.newValue(15.4)
        every { sensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        filterManager.createFilter(filterData)
        val filteredData = filterManager.getFilteredSensorData(filterData)
        assertNotNull(filteredData)
        assertEquals(15.4, filteredData!!.value as Double, 0.001)
    }
}
