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
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridge
import com.polar.sdk.api.model.PolarAccelerometerData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests verifying `BSportType.RUN` gating (including walking) and [MySensorManager.CADENCE_PRIORITY]
 * arbitration so dedicated footpods and cycling crank sensors strictly outrank Polar IMU cadence
 * (`ATT-8016`, `REQ-SET-112.4`, `TST-SET-114.5`).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class MySensorManagerCadenceArbitrationTest {

    private lateinit var context: Context
    private lateinit var sensorManager: MySensorManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sensorManager = MySensorManager(context)
    }

    @After
    fun tearDown() {
        sensorManager.shutDown()
    }

    @Test
    fun testPolarImuCadence_registersOnlyInRunSport_andNeverInBikeSport() {
        // TST-SET-114.5
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:55",
            "Polar H10 55667788",
            "Polar Electro",
            95,
            true
        )

        var currentSport = BSportType.BIKE
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10Id,
            macAddress = "AA:BB:CC:DD:EE:55",
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            sportTypeProvider = { currentSport }
        )

        // Feed synthetic steps while in BIKE mode -> must register zero CADENCE/STRIDES
        bridge.processAccData(syntheticStepFrame())
        assertNull("Bike sport must receive zero Polar IMU cadence", bridge.getSensor(SensorType.CADENCE))
        assertNull("Bike sport must receive zero Polar IMU strides", bridge.getSensor(SensorType.STRIDES))
        assertNull(sensorManager.getSensor(SensorType.CADENCE))

        // Switch to RUN (covers both Running & Walking TTSportType.WALK) -> registers CADENCE & STRIDES
        currentSport = BSportType.RUN
        bridge.syncCadenceSensorRegistration()
        assertNotNull("Run sport must register Polar IMU cadence", bridge.getSensor(SensorType.CADENCE))
        assertNotNull("Run sport must register Polar IMU strides", bridge.getSensor(SensorType.STRIDES))

        // Switch back to BIKE -> unregisters IMU cadence immediately
        currentSport = BSportType.BIKE
        bridge.syncCadenceSensorRegistration()
        assertNull(bridge.getSensor(SensorType.CADENCE))
        assertNull((sensorManager.getSensor(SensorType.CADENCE) as? ProxySensor<*>)?.sourceSensor)

        bridge.shutDown()
    }

    @Test
    fun testCadencePriority_yieldsToDedicatedFootpodAndBikeSensors() {
        // TST-SET-114.5
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:66",
            "Polar H10 AABBCCDD",
            "Polar Electro",
            95,
            true
        )

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10Id,
            macAddress = "AA:BB:CC:DD:EE:66",
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN }
        )
        bridge.syncCadenceSensorRegistration(true)
        val imuCadence = bridge.getSensor(SensorType.CADENCE)
        val proxy = sensorManager.getSensor(SensorType.CADENCE) as ProxySensor<*>
        assertSame("Polar IMU cadence active when no dedicated sensor exists", imuCadence, proxy.sourceSensor)

        val footpodDevice = mockk<MyDevice>(relaxed = true) {
            every { deviceType } returns DeviceType.RUN_SPEED
            every { name } returns "Stryd Footpod"
            every { deviceId } returns 101L
        }
        val footpodCadence = MySensor<Double>(footpodDevice, SensorType.CADENCE)
        sensorManager.registerSensor(footpodCadence)
        assertSame("Dedicated footpod must preempt Polar IMU cadence", footpodCadence, proxy.sourceSensor)

        sensorManager.unregisterSensor(footpodCadence)
        assertSame("Fallback to Polar IMU cadence after footpod unregisters", imuCadence, proxy.sourceSensor)

        val bikeCadenceDevice = mockk<MyDevice>(relaxed = true) {
            every { deviceType } returns DeviceType.BIKE_CADENCE
            every { name } returns "Garmin Cadence"
            every { deviceId } returns 102L
        }
        val bikeCadence = MySensor<Double>(bikeCadenceDevice, SensorType.CADENCE)
        sensorManager.registerSensor(bikeCadence)
        assertSame("Dedicated BIKE_CADENCE must preempt Polar IMU cadence", bikeCadence, proxy.sourceSensor)

        val bikePowerDevice = mockk<MyDevice>(relaxed = true) {
            every { deviceType } returns DeviceType.BIKE_POWER
            every { name } returns "Assioma Power"
            every { deviceId } returns 103L
        }
        val bikePowerCadence = MySensor<Double>(bikePowerDevice, SensorType.CADENCE)
        sensorManager.unregisterSensor(bikeCadence)
        sensorManager.registerSensor(bikePowerCadence)
        assertSame("Dedicated BIKE_POWER cadence must preempt Polar IMU cadence", bikePowerCadence, proxy.sourceSensor)

        assertEquals(
            DeviceType.HRM,
            MySensorManager.CADENCE_PRIORITY.last()
        )

        bridge.shutDown()
    }

    private fun syntheticStepFrame(): PolarAccelerometerData {
        return PolarAccelerometerData(
            listOf(
                PolarAccelerometerData.PolarAccelerometerDataSample(100_000_000L, 0, 0, 1000),
                PolarAccelerometerData.PolarAccelerometerDataSample(200_000_000L, 0, 0, 1500),
                PolarAccelerometerData.PolarAccelerometerDataSample(300_000_000L, 0, 0, 1000)
            )
        )
    }
}
