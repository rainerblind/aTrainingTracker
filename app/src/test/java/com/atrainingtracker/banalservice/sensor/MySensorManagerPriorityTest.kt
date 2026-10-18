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
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [MySensorManager] speed, distance, altitude, and location priority
 * hierarchies, [ProxySensor] retention for [DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED],
 * and dynamic source switching (`REQ-TRK-024` / `TST-TRK-016` / `ATT-3961`).
 */
class MySensorManagerPriorityTest {

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
        }
    }

    /**
     * `TST-TRK-016.1`: Verifies that `SPEED_PRIORITY` includes `SPEED_AND_LOCATION_GOOGLE_FUSED`
     * immediately after `SPEED_AND_LOCATION_GPS` and before `SPEED_AND_LOCATION_NETWORK`,
     * matching the relative location hierarchy of `ALTITUDE_PRIORITY` and `LOCATION_PRIORITY`.
     */
    @Test
    fun speedPriority_includesGoogleFusedAfterGpsAndBeforeNetwork() {
        val expectedSpeedPriority = arrayOf(
            DeviceType.RUN_SPEED,
            DeviceType.BIKE_SPEED,
            DeviceType.BIKE_SPEED_AND_CADENCE,
            DeviceType.BIKE_POWER,
            DeviceType.SPEED_AND_LOCATION_GPS,
            DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
            DeviceType.SPEED_AND_LOCATION_NETWORK
        )
        assertArrayEquals(expectedSpeedPriority, MySensorManager.SPEED_PRIORITY)

        // Verify relative location priority consistency across SPEED, ALTITUDE, and LOCATION
        val locationProviders = listOf(
            DeviceType.SPEED_AND_LOCATION_GPS,
            DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
            DeviceType.SPEED_AND_LOCATION_NETWORK
        )
        assertEquals(
            locationProviders,
            MySensorManager.SPEED_PRIORITY.filter { it in locationProviders }
        )
        assertEquals(
            locationProviders,
            MySensorManager.ALTITUDE_PRIORITY.filter { it in locationProviders }
        )
        assertEquals(
            locationProviders,
            MySensorManager.LOCATION_PRIORITY.toList()
        )
    }

    /**
     * `TST-TRK-016.2`: Verifies that registering `SPEED_mps`, `PACE_spm`, `DISTANCE_m`,
     * and `DISTANCE_m_LAP` from a `SPEED_AND_LOCATION_GOOGLE_FUSED` device retains active
     * [ProxySensor] instances and updates `SensorType.SENSORS` (`-DS----AG--`).
     */
    @Test
    fun registerGoogleFusedSensors_retainsSpeedAndDistanceProxySensorsAndUpdatesGcData() {
        val fusedDevice = mockDevice(DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)

        val speedSensor = MySensor<Number>(fusedDevice, SensorType.SPEED_mps)
        val paceSensor = MySensor<Number>(fusedDevice, SensorType.PACE_spm)
        val distanceSensor = MySensor<Number>(fusedDevice, SensorType.DISTANCE_m)
        val lapDistanceSensor = MySensor<Number>(fusedDevice, SensorType.DISTANCE_m_LAP)
        val altitudeSensor = MySensor<Number>(fusedDevice, SensorType.ALTITUDE)
        val latitudeSensor = MySensor<Number>(fusedDevice, SensorType.LATITUDE)
        val longitudeSensor = MySensor<Number>(fusedDevice, SensorType.LONGITUDE)

        sensorManager.registerSensors(
            listOf(
                speedSensor,
                paceSensor,
                distanceSensor,
                lapDistanceSensor,
                altitudeSensor,
                latitudeSensor,
                longitudeSensor
            )
        )

        assertSame(speedSensor, sensorManager.getBestSensor(SensorType.SPEED_mps))
        assertSame(paceSensor, sensorManager.getBestSensor(SensorType.PACE_spm))
        assertSame(distanceSensor, sensorManager.getBestSensor(SensorType.DISTANCE_m))
        assertSame(lapDistanceSensor, sensorManager.getBestSensor(SensorType.DISTANCE_m_LAP))

        val speedProxy = sensorManager.getSensor(SensorType.SPEED_mps)
        val distanceProxy = sensorManager.getSensor(SensorType.DISTANCE_m)
        assertNotNull("SPEED_mps ProxySensor must be retained for Google Fused", speedProxy)
        assertNotNull("DISTANCE_m ProxySensor must be retained for Google Fused", distanceProxy)

        speedSensor.newValue(6.5)
        distanceSensor.newValue(2896.48)
        assertEquals(6.5, (speedProxy.value as Number).toDouble(), 1e-6)
        assertEquals(2896.48, (distanceProxy.value as Number).toDouble(), 1e-6)

        val gcString = sensorManager.getSensor(SensorType.SENSORS).value as String
        assertEquals('D', gcString[1])
        assertEquals('S', gcString[2])
        assertEquals('A', gcString[7])
        assertEquals('G', gcString[8])
        assertEquals("-DS----AG--", gcString)
    }

    /**
     * `TST-TRK-016.3`: Verifies that `SPEED_mps` and `DISTANCE_m` dynamically upgrade and fall back
     * across `BIKE_SPEED > SPEED_AND_LOCATION_GPS > SPEED_AND_LOCATION_GOOGLE_FUSED > SPEED_AND_LOCATION_NETWORK`.
     */
    @Test
    fun speedAndDistancePriority_upgradesToGpsAndBikeSpeedAndFallsBackToGoogleFused() {
        val networkDevice = mockDevice(DeviceType.SPEED_AND_LOCATION_NETWORK)
        val fusedDevice = mockDevice(DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val gpsDevice = mockDevice(DeviceType.SPEED_AND_LOCATION_GPS)
        val bikeSpeedDevice = mockDevice(DeviceType.BIKE_SPEED)

        val netSpeed = MySensor<Double>(networkDevice, SensorType.SPEED_mps).apply { newValue(4.0) }
        val fusedSpeed = MySensor<Double>(fusedDevice, SensorType.SPEED_mps).apply { newValue(6.5) }
        val gpsSpeed = MySensor<Double>(gpsDevice, SensorType.SPEED_mps).apply { newValue(7.2) }
        val wheelSpeed = MySensor<Double>(bikeSpeedDevice, SensorType.SPEED_mps).apply { newValue(8.1) }

        // 1. Network only
        sensorManager.registerSensor(netSpeed)
        assertSame(netSpeed, sensorManager.getBestSensor(SensorType.SPEED_mps))
        assertEquals(4.0, sensorManager.getSensor(SensorType.SPEED_mps).value as Double, 1e-6)

        // 2. Google Fused registers -> upgrades from Network to Google Fused
        sensorManager.registerSensor(fusedSpeed)
        assertSame(fusedSpeed, sensorManager.getBestSensor(SensorType.SPEED_mps))
        assertEquals(6.5, sensorManager.getSensor(SensorType.SPEED_mps).value as Double, 1e-6)

        // 3. Raw GPS registers -> upgrades from Google Fused to GPS
        sensorManager.registerSensor(gpsSpeed)
        assertSame(gpsSpeed, sensorManager.getBestSensor(SensorType.SPEED_mps))
        assertEquals(7.2, sensorManager.getSensor(SensorType.SPEED_mps).value as Double, 1e-6)

        // 4. Dedicated Bike Speed sensor registers -> upgrades from GPS to Bike Speed
        sensorManager.registerSensor(wheelSpeed)
        assertSame(wheelSpeed, sensorManager.getBestSensor(SensorType.SPEED_mps))
        assertEquals(8.1, sensorManager.getSensor(SensorType.SPEED_mps).value as Double, 1e-6)

        // 5. Unregister Bike Speed -> falls back to GPS
        sensorManager.unregisterSensor(wheelSpeed)
        assertSame(gpsSpeed, sensorManager.getBestSensor(SensorType.SPEED_mps))
        assertEquals(7.2, sensorManager.getSensor(SensorType.SPEED_mps).value as Double, 1e-6)

        // 6. Unregister GPS -> falls back to Google Fused
        sensorManager.unregisterSensor(gpsSpeed)
        assertSame(fusedSpeed, sensorManager.getBestSensor(SensorType.SPEED_mps))
        assertEquals(6.5, sensorManager.getSensor(SensorType.SPEED_mps).value as Double, 1e-6)

        // 7. Unregister Google Fused -> falls back to Network
        sensorManager.unregisterSensor(fusedSpeed)
        assertSame(netSpeed, sensorManager.getBestSensor(SensorType.SPEED_mps))
        assertEquals(4.0, sensorManager.getSensor(SensorType.SPEED_mps).value as Double, 1e-6)
    }
}
