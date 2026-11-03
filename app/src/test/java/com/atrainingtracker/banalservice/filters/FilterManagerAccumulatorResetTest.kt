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
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MyIntegerAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.ProxySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test verifying accumulator zero baseline re-seeding and non-accumulator cache flushing
 * during FilterManager.resetAllFilters() (REQ-TRK-049.4, TST-TRK-049.5a, ATT-5080, ATT-5191).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilterManagerAccumulatorResetTest {

    private lateinit var context: Context
    private lateinit var deviceManager: DeviceManager
    private lateinit var sensorManager: MySensorManager
    private lateinit var testFilterManager: TestFilterManager
    private lateinit var mockDevice: MyDevice

    private class TestFilterManager(
        context: Context,
        deviceManager: DeviceManager,
        sensorManager: MySensorManager
    ) : FilterManager(context, deviceManager, sensorManager) {
        fun registerTestFilter(filter: MyFilter<*>, sensor: MySensor<*>) {
            myFilterMap[filter.filteredSensorData.filterData.hashKey] = filter
            mMapFilter2Sensor[filter] = sensor
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        deviceManager = mockk(relaxed = true)
        sensorManager = mockk(relaxed = true)
        testFilterManager = TestFilterManager(context, deviceManager, sensorManager)
        mockDevice = mockk(relaxed = true)
    }

    @Test
    fun resetAllFilters_accumulatorSensor_reseedsZeroBaselineAndFormattedString() {
        // Create an accumulator sensor (e.g. time active, zeroValue = 0.0)
        val activeTimeSensor = MyDoubleAccumulatorSensor(mockDevice, SensorType.TIME_ACTIVE, true)
        val accumulatorFilter = InstantaneousFilter<Double>("test_device", SensorType.TIME_ACTIVE)

        testFilterManager.registerTestFilter(accumulatorFilter, activeTimeSensor)

        // Seed with non-zero value during active tracking
        accumulatorFilter.newValue(3600.0)
        assertEquals(3600.0, accumulatorFilter.filteredValue, 0.001)

        // Reset accumulator sensor to baseline zero
        activeTimeSensor.reset()
        assertEquals(0.0, activeTimeSensor.value ?: -1.0, 0.001)

        // Execute FilterManager.resetAllFilters()
        testFilterManager.resetAllFilters()

        // Accumulator filter must be re-seeded with 0.0 (0:00:00) rather than null (--:--:--)
        assertEquals(0.0, accumulatorFilter.filteredValue ?: -1.0, 0.001)
        val filteredData = accumulatorFilter.filteredSensorData
        assertEquals("0:00:00", filteredData.stringValue)
    }

    @Test
    fun resetAllFilters_nonAccumulatorSensor_resetsToNullAndDash() {
        // Create a stateless instantaneous sensor (e.g. heart rate)
        val hrSensor = object : MySensor<Number>(mockDevice, SensorType.HR) {}
        val hrFilter = InstantaneousFilter<Number>("test_device", SensorType.HR)

        testFilterManager.registerTestFilter(hrFilter, hrSensor)

        // Seed with active heart rate
        hrFilter.newValue(145)
        assertEquals(145, hrFilter.filteredValue)

        // Execute FilterManager.resetAllFilters()
        testFilterManager.resetAllFilters()

        // Non-accumulator filter must reset to null, resulting in "--"
        assertNull(hrFilter.filteredValue)
        val filteredData = hrFilter.filteredSensorData
        assertEquals("--", filteredData.stringValue)
    }

    @Test
    fun resetAllFilters_proxySensorWrappedAccumulator_reseedsZeroBaselineAndFormattedString() {
        // Create an accumulator sensor (e.g. time active, zeroValue = 0)
        val activeTimeSensor = MyIntegerAccumulatorSensor(mockDevice, SensorType.TIME_ACTIVE, true, 0)
        // Wrap in ProxySensor as done for "Best Sensor" (deviceName = null)
        val proxySensor = ProxySensor<Int>(mockDevice, SensorType.TIME_ACTIVE, activeTimeSensor)
        val accumulatorFilter = InstantaneousFilter<Int>(null, SensorType.TIME_ACTIVE)

        testFilterManager.registerTestFilter(accumulatorFilter, proxySensor)

        // Seed with non-zero value during active tracking
        accumulatorFilter.newValue(3600)
        assertEquals(3600, accumulatorFilter.filteredValue)

        // Note: activeTimeSensor has NOT been reset yet (simulating FilterManager broadcast receiver running first)
        // Execute FilterManager.resetAllFilters()
        testFilterManager.resetAllFilters()

        // Accumulator filter must unwrap ProxySensor and re-seed with getZeroValue() (0 -> 0:00:00) rather than null (--:--:--)
        assertEquals(0, accumulatorFilter.filteredValue)
        val filteredData = accumulatorFilter.filteredSensorData
        assertEquals("0:00:00", filteredData.stringValue)
    }

    @Test
    fun resetAllFilters_proxySensorWrappedLapSensor_reseedsInitLapNr() {
        // Create a lap sensor with zeroValue = INIT_LAP_NR (1)
        val lapSensor = MyIntegerAccumulatorSensor(mockDevice, SensorType.LAP_NR, false, BANALService.INIT_LAP_NR)
        val proxySensor = ProxySensor<Int>(mockDevice, SensorType.LAP_NR, lapSensor)
        val lapFilter = InstantaneousFilter<Int>(null, SensorType.LAP_NR)

        testFilterManager.registerTestFilter(lapFilter, proxySensor)

        // Seed with lap 4 during active workout
        lapFilter.newValue(4)
        assertEquals(4, lapFilter.filteredValue)

        // Execute FilterManager.resetAllFilters()
        testFilterManager.resetAllFilters()

        // Lap filter must re-seed with 1 rather than null or 0
        assertEquals(BANALService.INIT_LAP_NR, lapFilter.filteredValue)
        val filteredData = lapFilter.filteredSensorData
        assertEquals("1", filteredData.stringValue)
    }

    @Test
    fun resetAllFilters_proxySensorWrappedStatelessSensor_resetsToNullAndDash() {
        val hrSensor = object : MySensor<Number>(mockDevice, SensorType.HR) {}
        val proxySensor = ProxySensor<Number>(mockDevice, SensorType.HR, hrSensor)
        val hrFilter = InstantaneousFilter<Number>(null, SensorType.HR)

        testFilterManager.registerTestFilter(hrFilter, proxySensor)

        // Seed with active heart rate
        hrFilter.newValue(150)
        assertEquals(150, hrFilter.filteredValue)

        // Execute FilterManager.resetAllFilters()
        testFilterManager.resetAllFilters()

        // Non-accumulator wrapped in ProxySensor must reset to null / "--"
        assertNull(hrFilter.filteredValue)
        val filteredData = hrFilter.filteredSensorData
        assertEquals("--", filteredData.stringValue)
    }
}
