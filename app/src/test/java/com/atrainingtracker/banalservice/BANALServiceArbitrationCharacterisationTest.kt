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

@file:Suppress("MaxLineLength", "WildcardImport")

package com.atrainingtracker.banalservice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensor.SensorListener
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.MySensorManager.WheelMeasurementListener
import com.atrainingtracker.banalservice.sensor.ProxySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Golden Master Characterisation Test Suite for BANALService & MySensorManager
 * Sensor Arbitration, ProxySensor Listener Migration, and Fallback Invariants.
 *
 * Traceability:
 * - REQ-ARC-008.2: BANALService Sensor Arbitration & Dispatching
 * - TST-ARC-008.3: BANALService Multi-Sensor Arbitration & Dispatching
 * - REQ-SEN-021: ProxySensor Dynamic Listener Migration & Non-blocking Dispatch
 * - REQ-TRK-024: Priority Resolution Across Dedicated & Fused Sensors
 * - REQ-TRK-055.1: Unbound ProxySensor Retention
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class BANALServiceArbitrationCharacterisationTest {

    private lateinit var context: Context
    private lateinit var sensorManager: MySensorManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val field = com.atrainingtracker.trainingtracker.TrainingApplication::class.java.getDeclaredField("cAppContext")
        field.isAccessible = true
        field.set(null, context)

        val prefField = com.atrainingtracker.trainingtracker.TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        prefField.isAccessible = true
        prefField.set(null, androidx.preference.PreferenceManager.getDefaultSharedPreferences(context))

        sensorManager = MySensorManager(context)
    }

    @After
    fun tearDown() {
        if (::sensorManager.isInitialized) {
            sensorManager.shutDown()
        }
        val field = com.atrainingtracker.trainingtracker.TrainingApplication::class.java.getDeclaredField("cAppContext")
        field.isAccessible = true
        field.set(null, null)

        val prefField = com.atrainingtracker.trainingtracker.TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        prefField.isAccessible = true
        prefField.set(null, null)

        unmockkAll()
    }

    private fun createMockDevice(deviceType: DeviceType, name: String, deviceId: Long = 1L): MyDevice {
        return mockk<MyDevice>(relaxed = true) {
            every { this@mockk.deviceType } returns deviceType
            every { this@mockk.name } returns name
            every { this@mockk.deviceId } returns deviceId
        }
    }

    /**
     * Characterisation Test 1: Speed Sensor Priority Arbitration & Dynamic Fallback
     * Verifies SPEED_PRIORITY hierarchy:
     * ATT_FUSED > BIKE_SPEED > GPS
     * and fallback when higher-priority sensors are unregistered.
     */
    @Test
    fun testSpeedSensorPriorityArbitration_andDynamicFallback() {
        val gpsDevice = createMockDevice(DeviceType.SPEED_AND_LOCATION_GPS, "GPS Sensor")
        val gpsSpeed = MySensor<Double>(gpsDevice, SensorType.SPEED_mps)

        val bikeDevice = createMockDevice(DeviceType.BIKE_SPEED, "Bike Wheel Sensor")
        val bikeSpeed = MySensor<Double>(bikeDevice, SensorType.SPEED_mps)

        val fusedDevice = createMockDevice(DeviceType.SPEED_AND_LOCATION_ATT_FUSED, "ATT Fused")
        val fusedSpeed = MySensor<Double>(fusedDevice, SensorType.SPEED_mps)

        // 1. Register GPS speed
        sensorManager.registerSensor(gpsSpeed)
        val proxy = sensorManager.getSensor(SensorType.SPEED_mps) as ProxySensor<Double>
        assertSame("Proxy should bind to GPS speed", gpsSpeed, proxy.sourceSensor)

        // 2. Register Bike Speed (higher priority than GPS)
        sensorManager.registerSensor(bikeSpeed)
        assertSame("Proxy should preempt GPS with Bike Speed", bikeSpeed, proxy.sourceSensor)

        // 3. Register ATT Fused Speed (highest priority)
        sensorManager.registerSensor(fusedSpeed)
        assertSame("Proxy should preempt Bike Speed with ATT Fused", fusedSpeed, proxy.sourceSensor)

        // 4. Unregister ATT Fused -> Falls back to Bike Speed
        sensorManager.unregisterSensor(fusedSpeed)
        assertSame("Proxy should fall back to Bike Speed", bikeSpeed, proxy.sourceSensor)

        // 5. Unregister Bike Speed -> Falls back to GPS Speed
        sensorManager.unregisterSensor(bikeSpeed)
        assertSame("Proxy should fall back to GPS Speed", gpsSpeed, proxy.sourceSensor)

        // 6. Unregister GPS Speed -> Proxy retained but source is null (REQ-TRK-055.1)
        sensorManager.unregisterSensor(gpsSpeed)
        assertNull("Proxy source should be null when all sources unregistered", proxy.sourceSensor)
        assertNull("Proxy value should be null when unbound", proxy.value)
    }

    /**
     * Characterisation Test 2: Altitude Sensor Priority Arbitration
     * Verifies ALTITUDE_PRIORITY hierarchy:
     * ATT_FUSED > POSITION (DEM) > PRESSURE (Barometer)
     */
    @Test
    fun testAltitudeSensorPriorityArbitration() {
        val baroDevice = createMockDevice(DeviceType.ALTITUDE_FROM_PRESSURE, "Barometer")
        val baroAlt = MySensor<Double>(baroDevice, SensorType.ALTITUDE)

        val demDevice = createMockDevice(DeviceType.ALTITUDE_FROM_POSITION, "DEM Position")
        val demAlt = MySensor<Double>(demDevice, SensorType.ALTITUDE)

        val fusedDevice = createMockDevice(DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED, "ATT Fused Alt")
        val fusedAlt = MySensor<Double>(fusedDevice, SensorType.ALTITUDE)

        // Register Baro
        sensorManager.registerSensor(baroAlt)
        val proxy = sensorManager.getSensor(SensorType.ALTITUDE) as ProxySensor<Double>
        assertSame(baroAlt, proxy.sourceSensor)

        // Register DEM Position -> Takes priority over Pressure
        sensorManager.registerSensor(demAlt)
        assertSame(demAlt, proxy.sourceSensor)

        // Register ATT Fused -> Takes highest priority
        sensorManager.registerSensor(fusedAlt)
        assertSame(fusedAlt, proxy.sourceSensor)
    }

    /**
     * Characterisation Test 3: ProxySensor Listener Transparent Migration
     * Asserts that when source sensor switches dynamically, listeners attached to
     * ProxySensor are seamlessly transferred without losing callbacks or retaining stale bindings.
     */
    @Test
    fun testProxySensor_listenerMigrationOnSourceSwitch() {
        val gpsDevice = createMockDevice(DeviceType.SPEED_AND_LOCATION_GPS, "GPS Sensor")
        val gpsSpeed = MySensor<Double>(gpsDevice, SensorType.SPEED_mps)

        val bikeDevice = createMockDevice(DeviceType.BIKE_SPEED, "Bike Wheel Sensor")
        val bikeSpeed = MySensor<Double>(bikeDevice, SensorType.SPEED_mps)

        sensorManager.registerSensor(gpsSpeed)
        val proxy = sensorManager.getSensor(SensorType.SPEED_mps) as ProxySensor<Double>

        val receivedValues = mutableListOf<Double>()
        val listener = SensorListener<Double> { value ->
            if (value != null) {
                receivedValues.add(value)
            }
        }

        // Attach listener to proxy
        proxy.addSensorListener(listener)

        // Emit from GPS
        gpsSpeed.newValue(5.0)
        assertEquals(1, receivedValues.size)
        assertEquals(5.0, receivedValues.last(), 0.001)

        // Register Bike Speed (causes source switch)
        sensorManager.registerSensor(bikeSpeed)
        assertSame(bikeSpeed, proxy.sourceSensor)

        // Old source emission should NOT reach proxy listener anymore
        gpsSpeed.newValue(6.0)
        assertEquals("Old source update must not reach listener", 1, receivedValues.size)

        // New source emission reaches proxy listener
        bikeSpeed.newValue(8.5)
        assertEquals(2, receivedValues.size)
        assertEquals(8.5, receivedValues.last(), 0.001)

        // Remove listener
        proxy.removeSensorListener(listener)
        bikeSpeed.newValue(10.0)
        assertEquals("Unregistered listener must not receive further updates", 2, receivedValues.size)
    }

    /**
     * Characterisation Test 4: Wheel Measurement Event Propagation
     * Asserts that WheelMeasurementListener receives live speed, distance, and revolution callbacks.
     */
    @Test
    fun testWheelMeasurementListener_callbacksDispatched() {
        val speedMeasurements = mutableListOf<Double>()
        val distanceMeasurements = mutableListOf<Double>()
        val revolutionMeasurements = mutableListOf<Double>()

        val wheelListener = object : WheelMeasurementListener {
            override fun onWheelMeasurement(speedMps: Double, variance: Double, timestampMs: Long) {
                speedMeasurements.add(speedMps)
            }
            override fun onWheelDistance(accumulatedDistanceMeters: Double, timestampMs: Long) {
                distanceMeasurements.add(accumulatedDistanceMeters)
            }
            override fun onWheelRevolutions(deltaRevolutions: Double, totalRevolutions: Double, timestampMs: Long) {
                revolutionMeasurements.add(totalRevolutions)
            }
        }

        sensorManager.addWheelMeasurementListener(wheelListener)

        sensorManager.notifyWheelMeasurement(7.2, 0.05, 1000L)
        sensorManager.notifyWheelDistance(150.0, 1000L)
        sensorManager.notifyWheelRevolutions(2.5, 75.0, 1000L)

        assertEquals(1, speedMeasurements.size)
        assertEquals(7.2, speedMeasurements[0], 0.001)

        assertEquals(1, distanceMeasurements.size)
        assertEquals(150.0, distanceMeasurements[0], 0.001)

        assertEquals(1, revolutionMeasurements.size)
        assertEquals(75.0, revolutionMeasurements[0], 0.001)

        sensorManager.removeWheelMeasurementListener(wheelListener)
        sensorManager.notifyWheelMeasurement(8.0, 0.05, 2000L)
        assertEquals("Removed listener should not receive updates", 1, speedMeasurements.size)
    }

    /**
     * Characterisation Test 5: Defensive Manager Teardown Invariants
     * Asserts that shutDown cleanly unbinds proxy sensors and clears internal device maps.
     */
    @Test
    fun testShutDown_cleanlyUnbindsProxiesAndClearsState() {
        val gpsDevice = createMockDevice(DeviceType.SPEED_AND_LOCATION_GPS, "GPS Sensor")
        val gpsSpeed = MySensor<Double>(gpsDevice, SensorType.SPEED_mps)
        sensorManager.registerSensor(gpsSpeed)

        val proxy = sensorManager.getSensor(SensorType.SPEED_mps) as ProxySensor<Double>
        assertNotNull(proxy.sourceSensor)

        sensorManager.shutDown()

        assertNull("shutDown must nullify proxy source sensor", proxy.sourceSensor)
        assertTrue("shutDown must clear accumulated sensor types", sensorManager.accumulatedSensorTypeSet.isEmpty())
        assertTrue("shutDown must clear registered device sensor mappings", sensorManager.allButBestSensors.isEmpty())
    }
}
