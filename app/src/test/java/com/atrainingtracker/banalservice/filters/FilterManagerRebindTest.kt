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
import com.atrainingtracker.banalservice.sensor.MyIntegerAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.ProxySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun testDevicePinnedDisconnect_clearsInstantaneousAndMovingAverage_preservesCumulativeAndStrides_andRebinds_TST_FIL_038_3() {
        // REQ-FIL-038.3 / TST-FIL-038.3
        mockkStatic(TrainingApplication::class)
        try {
            every { TrainingApplication.isPaused() } returns false
            val deviceName = "Polar H10 ANT+"
            val device = mockk<MyDevice>(relaxed = true) { every { name } returns deviceName }

            val hrSensor = MySensor<Int>(device, SensorType.HR)
            val rrSensor = MySensor<Int>(device, SensorType.RR_INTERVAL)
            val stridesSensor = MyIntegerAccumulatorSensor(device, SensorType.STRIDES, false, 0)

            every { device.getSensor(SensorType.HR) } returns hrSensor
            every { device.getSensor(SensorType.RR_INTERVAL) } returns rrSensor
            every { device.getSensor(SensorType.STRIDES) } returns stridesSensor
            every { deviceManager.getMyDeviceByName(deviceName) } returns device

            val instRr = FilterData(deviceName, SensorType.RR_INTERVAL, FilterType.INSTANTANEOUS, 1.0)
            val instHr = FilterData(deviceName, SensorType.HR, FilterType.INSTANTANEOUS, 1.0)
            val timedRr = FilterData(deviceName, SensorType.RR_INTERVAL, FilterType.MOVING_AVERAGE_TIME, 10.0)
            val numberedRr = FilterData(deviceName, SensorType.RR_INTERVAL, FilterType.MOVING_AVERAGE_NUMBER, 5.0)
            val avgHr = FilterData(deviceName, SensorType.HR, FilterType.AVERAGE, 1.0)
            val maxHr = FilterData(deviceName, SensorType.HR, FilterType.MAX_VALUE, 1.0)
            val stridesFilter = FilterData(deviceName, SensorType.STRIDES, FilterType.INSTANTANEOUS, 1.0)

            listOf(instRr, instHr, timedRr, numberedRr, avgHr, maxHr, stridesFilter).forEach {
                filterManager.createFilter(it)
            }

            hrSensor.newValue(150)
            rrSensor.newValue(827)
            stridesSensor.increment(64)

            assertEquals(827, filterManager.getFilteredSensorData(instRr)?.value)
            assertEquals(150, filterManager.getFilteredSensorData(instHr)?.value)
            assertEquals(827.0, (filterManager.getFilteredSensorData(timedRr)?.value as Number).toDouble(), 0.01)
            assertEquals(827.0, (filterManager.getFilteredSensorData(numberedRr)?.value as Number).toDouble(), 0.01)
            assertEquals(150.0, (filterManager.getFilteredSensorData(avgHr)?.value as Number).toDouble(), 0.01)
            assertEquals(150, filterManager.getFilteredSensorData(maxHr)?.value)
            assertEquals(64, filterManager.getFilteredSensorData(stridesFilter)?.value)

            // Disconnect: static HR becomes deactivated (!isActivated); dynamic RR_INTERVAL & STRIDES removed (null)
            hrSensor.deactivateSensor()
            every { device.getSensor(SensorType.RR_INTERVAL) } returns null
            every { device.getSensor(SensorType.STRIDES) } returns null

            // Fire repeated SENSORS_CHANGED rebind sweeps
            filterManager.rebindExistingFilters()
            filterManager.rebindExistingFilters()

            // Non-accumulator live filters must clear to null / "--"
            assertNull(filterManager.getFilteredSensorData(instRr)?.value)
            assertEquals("--", filterManager.getFilteredSensorData(instRr)?.stringValue)
            assertNull(filterManager.getFilteredSensorData(instHr)?.value)
            assertEquals("--", filterManager.getFilteredSensorData(instHr)?.stringValue)
            assertNull(filterManager.getFilteredSensorData(timedRr)?.value)
            assertNull(filterManager.getFilteredSensorData(numberedRr)?.value)

            // Cumulative filters & dynamic STRIDES accumulator MUST retain workout totals
            assertEquals(150.0, (filterManager.getFilteredSensorData(avgHr)?.value as Number).toDouble(), 0.01)
            assertEquals(150, filterManager.getFilteredSensorData(maxHr)?.value)
            assertEquals(64, filterManager.getFilteredSensorData(stridesFilter)?.value)

            // Reconnect: static HR reactivates; fresh RR_INTERVAL instance created
            hrSensor.activateSensor()
            val rrSensor2 = MySensor<Int>(device, SensorType.RR_INTERVAL)
            every { device.getSensor(SensorType.RR_INTERVAL) } returns rrSensor2
            filterManager.rebindExistingFilters()

            hrSensor.newValue(162)
            rrSensor2.newValue(740)
            assertEquals(162, filterManager.getFilteredSensorData(instHr)?.value)
            assertEquals(740, filterManager.getFilteredSensorData(instRr)?.value)
            assertEquals(740.0, (filterManager.getFilteredSensorData(numberedRr)?.value as Number).toDouble(), 0.01)
            assertEquals(162, filterManager.getFilteredSensorData(maxHr)?.value)
        } finally {
            unmockkStatic(TrainingApplication::class)
        }
    }

    @Test
    fun testBestSensorProxyDisconnect_preservesFallback_clearsOnLastDisconnect_andRetainsProxyBinding_TST_FIL_038_3() {
        // REQ-FIL-038.3 / TST-FIL-038.3
        val dev1 = mockk<MyDevice>(relaxed = true) { every { name } returns "Polar H10" }
        val dev2 = mockk<MyDevice>(relaxed = true) { every { name } returns "Polar Verity Sense" }
        val hr1 = MySensor<Int>(dev1, SensorType.HR)
        val hr2 = MySensor<Int>(dev2, SensorType.HR)
        hr2.newValue(138)

        val hrProxy = ProxySensor(dev1, SensorType.HR, hr1)
        every { sensorManager.getSensor(SensorType.HR) } returns hrProxy

        val bestInst = FilterData(null, SensorType.HR, FilterType.INSTANTANEOUS, 1.0)
        val bestNumAvg = FilterData(null, SensorType.HR, FilterType.MOVING_AVERAGE_NUMBER, 3.0)
        filterManager.createFilter(bestInst)
        filterManager.createFilter(bestNumAvg)

        hr1.newValue(144)
        assertEquals(144, filterManager.getFilteredSensorData(bestInst)?.value)
        assertEquals(144.0, (filterManager.getFilteredSensorData(bestNumAvg)?.value as Number).toDouble(), 0.01)

        // 1. Primary disconnects, fallback hr2 takes over -> "Bester Sensor" stays live on fallback
        hr1.deactivateSensor()
        hrProxy.setSourceSensor(hr2)
        filterManager.rebindExistingFilters()
        assertEquals(138, filterManager.getFilteredSensorData(bestInst)?.value)
        assertNotNull(filterManager.getFilteredSensorData(bestNumAvg)?.value)

        // 2. Last sensor disconnects -> ProxySensor retained (REQ-TRK-055.1), BOTH Inst & MovingAvg clear to null
        hr2.deactivateSensor()
        hrProxy.setSourceSensor(null)
        filterManager.rebindExistingFilters()
        assertNull(filterManager.getFilteredSensorData(bestInst)?.value)
        assertEquals("--", filterManager.getFilteredSensorData(bestInst)?.stringValue)
        assertNull(filterManager.getFilteredSensorData(bestNumAvg)?.value)

        // 3. Reconnect via ProxySensor.setSourceSensor(hr3) resumes automatically WITHOUT needing rebindExistingFilters
        val hr3 = MySensor<Int>(dev1, SensorType.HR)
        hrProxy.setSourceSensor(hr3)
        hr3.newValue(151)
        assertEquals(151, filterManager.getFilteredSensorData(bestInst)?.value)
        assertEquals(151.0, (filterManager.getFilteredSensorData(bestNumAvg)?.value as Number).toDouble(), 0.01)
    }

    @Test
    fun testCreateFilter_defersInactiveDevicePinnedSensor_andNeverDefersRetainedProxy_TST_FIL_038_3() {
        // REQ-FIL-038.3 / TST-FIL-038.3
        val deviceName = "Magene HR ANT+"
        val device = mockk<MyDevice>(relaxed = true) { every { name } returns deviceName }
        val inactiveHr = MySensor<Int>(device, SensorType.HR)
        inactiveHr.deactivateSensor()
        every { device.getSensor(SensorType.HR) } returns inactiveHr
        every { deviceManager.getMyDeviceByName(deviceName) } returns device

        val pinnedHr = FilterData(deviceName, SensorType.HR, FilterType.INSTANTANEOUS, 1.0)
        filterManager.createFilter(pinnedHr)
        assertNull("Device-pinned filter on inactive static sensor must defer creation", filterManager.getFilteredSensorData(pinnedHr))

        // Sensor connects (activateSensor) -> re-calling createFilter creates & delivers live data
        inactiveHr.activateSensor()
        filterManager.createFilter(pinnedHr)
        inactiveHr.newValue(132)
        assertEquals(132, filterManager.getFilteredSensorData(pinnedHr)?.value)

        // Existing filter synced via createFilter when sensor disconnects clears to null
        inactiveHr.deactivateSensor()
        filterManager.createFilter(pinnedHr)
        assertNull(filterManager.getFilteredSensorData(pinnedHr)?.value)

        // Retained ProxySensor with sourceSensor == null MUST NOT be deferred
        val emptyProxy = ProxySensor<Int>(device, SensorType.RR_INTERVAL, null)
        every { sensorManager.getSensor(SensorType.RR_INTERVAL) } returns emptyProxy
        val bestRr = FilterData(null, SensorType.RR_INTERVAL, FilterType.INSTANTANEOUS, 1.0)
        filterManager.createFilter(bestRr)
        assertNotNull("Retained ProxySensor must bind immediately even with null source", filterManager.getFilteredSensorData(bestRr))

        val liveRr = MySensor<Int>(device, SensorType.RR_INTERVAL)
        emptyProxy.setSourceSensor(liveRr)
        liveRr.newValue(795)
        assertEquals(795, filterManager.getFilteredSensorData(bestRr)?.value)
    }

    @Test
    fun testDisconnectStopsFlatLineSparklineFeedingAcrossRepeatedTicks_TST_FIL_038_4() {
        // REQ-FIL-038.4 / TST-FIL-038.4
        val deviceName = "Magene HR ANT+"
        val device = mockk<MyDevice>(relaxed = true) { every { name } returns deviceName }
        val rrSensor = MySensor<Int>(device, SensorType.RR_INTERVAL)
        every { device.getSensor(SensorType.RR_INTERVAL) } returns rrSensor
        every { deviceManager.getMyDeviceByName(deviceName) } returns device

        val filterData = FilterData(deviceName, SensorType.RR_INTERVAL, FilterType.INSTANTANEOUS, 1.0)
        filterManager.createFilter(filterData)

        val repo = BANALServiceRepository.getInstance(context)
        repo.clearSparklineBuffers()
        val fieldId = 998877L
        try {
            repo.registerSparklineBuffer(
                sensorFieldId = fieldId,
                horizonSeconds = 60,
                sensorType = SensorType.RR_INTERVAL,
                filterType = FilterType.INSTANTANEOUS,
                filterConstant = 1.0,
                sourceDeviceName = deviceName
            )

            // 3 live ticks feed 3 samples into the sparkline buffer
            rrSensor.newValue(791)
            repo.updateSparklineBuffers(filterManager.getAllFilteredSensorData())
            rrSensor.newValue(804)
            repo.updateSparklineBuffers(filterManager.getAllFilteredSensorData())
            rrSensor.newValue(798)
            repo.updateSparklineBuffers(filterManager.getAllFilteredSensorData())
            assertEquals(3, repo.getSparklineSamples(fieldId).size)

            // Sensor disconnects -> filter resets to null & 10 subsequent 1-Hz ticks add 0 flat-line samples
            rrSensor.deactivateSensor()
            every { device.getSensor(SensorType.RR_INTERVAL) } returns null
            filterManager.rebindExistingFilters()

            repeat(10) {
                repo.updateSparklineBuffers(filterManager.getAllFilteredSensorData())
            }
            assertEquals(
                "Disconnected sensor must stop feeding samples into LiveSparklineBuffer (no flat line)",
                3,
                repo.getSparklineSamples(fieldId).size
            )
            assertNull(filterManager.getFilteredSensorData(filterData)?.value)
        } finally {
            repo.removeSparklineBuffer(fieldId)
        }
    }
}
