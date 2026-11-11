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

package com.atrainingtracker.banalservice.devices.radar

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying SimulatedRadarDevice sensor registration, 1 Hz snapshot dispatch,
 * clear/reset transitions, and shutdown unregistration (TST-CON-029.2, REQ-CON-029.2).
 */
class SimulatedRadarDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var simulatedDevice: SimulatedRadarDevice

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDbManager = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)

        simulatedDevice = SimulatedRadarDevice(mockContext, mockSensorManager)
    }

    @After
    fun tearDown() {
        simulatedDevice.shutDown()
        unmockkAll()
    }

    @Test
    fun testInitialization_registersSixScalarRadarSensors() {
        assertEquals(DeviceType.BIKE_RADAR, simulatedDevice.deviceType)
        assertEquals("Simulated Radar", simulatedDevice.name)

        val sensors = simulatedDevice.sensors
        assertNotNull(sensors)
        assertEquals(6, sensors.size)

        val sensorTypes = sensors.map { it.sensorType }.toSet()
        val expectedTypes = setOf(
            SensorType.RADAR_THREAT_COUNT,
            SensorType.RADAR_CLOSEST_DISTANCE_m,
            SensorType.RADAR_APPROACH_SPEED_mps,
            SensorType.RADAR_VEHICLE_COUNT,
            SensorType.RADAR_PASSING_DISTANCE_m,
            SensorType.RADAR_LARGE_VEHICLE_COUNT
        )
        assertEquals(expectedTypes, sensorTypes)

        // Verifies sensors registered with MySensorManager
        verify { mockSensorManager.registerSensors(any()) }
    }

    @Test
    fun testUpdateMetrics_dispatchesSnapshotValuesToSensors() {
        val snapshot = RadarLatchedSnapshot(
            threatCount = 2,
            closestDistanceMeters = 35.5,
            approachSpeedMps = 14.2,
            vehicleCount = 4,
            passingDistanceMeters = 1.65,
            largeVehicleCount = 1
        )

        simulatedDevice.updateMetrics(snapshot)

        val sensors = simulatedDevice.sensors.associateBy { it.sensorType }
        assertEquals(2, sensors[SensorType.RADAR_THREAT_COUNT]?.value)
        assertEquals(35.5, sensors[SensorType.RADAR_CLOSEST_DISTANCE_m]?.value)
        assertEquals(14.2, sensors[SensorType.RADAR_APPROACH_SPEED_mps]?.value)
        assertEquals(4, sensors[SensorType.RADAR_VEHICLE_COUNT]?.value)
        assertEquals(1.65, sensors[SensorType.RADAR_PASSING_DISTANCE_m]?.value)
        assertEquals(1, sensors[SensorType.RADAR_LARGE_VEHICLE_COUNT]?.value)
    }

    @Test
    fun testResetSensorsToClear_resetsDynamicRadarSensors() {
        val snapshot = RadarLatchedSnapshot(
            threatCount = 3,
            closestDistanceMeters = 20.0,
            approachSpeedMps = 18.0,
            vehicleCount = 5,
            passingDistanceMeters = 1.2,
            largeVehicleCount = 2
        )
        simulatedDevice.updateMetrics(snapshot)

        simulatedDevice.resetSensorsToClear()

        val sensors = simulatedDevice.sensors.associateBy { it.sensorType }
        assertEquals(0, sensors[SensorType.RADAR_THREAT_COUNT]?.value)
        assertNull(sensors[SensorType.RADAR_CLOSEST_DISTANCE_m]?.value)
        assertEquals(0.0, sensors[SensorType.RADAR_APPROACH_SPEED_mps]?.value)
        assertNull(sensors[SensorType.RADAR_PASSING_DISTANCE_m]?.value)
        // Cumulative vehicle counts are preserved across dynamic clears
        assertEquals(5, sensors[SensorType.RADAR_VEHICLE_COUNT]?.value)
        assertEquals(2, sensors[SensorType.RADAR_LARGE_VEHICLE_COUNT]?.value)
    }

    @Test
    fun testOnAccumulatorsReset_clearsVehicleCounts() {
        val snapshot = RadarLatchedSnapshot(
            threatCount = 1,
            closestDistanceMeters = 50.0,
            approachSpeedMps = 10.0,
            vehicleCount = 7,
            passingDistanceMeters = null,
            largeVehicleCount = 3
        )
        simulatedDevice.updateMetrics(snapshot)

        simulatedDevice.onAccumulatorsReset()

        val sensors = simulatedDevice.sensors.associateBy { it.sensorType }
        assertEquals(0, sensors[SensorType.RADAR_VEHICLE_COUNT]?.value)
        assertEquals(0, sensors[SensorType.RADAR_LARGE_VEHICLE_COUNT]?.value)
    }

    @Test
    fun testShutDown_unregistersSensorsFromSensorManager() {
        simulatedDevice.shutDown()

        verify { mockSensorManager.unregisterSensors(any()) }
    }
}
