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

package com.atrainingtracker.banalservice.devices

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying null-safe barometric altitude sensor initialization,
 * raw altitude fallback, and correction dispatch (REQ-CON-013, TST-CON-004, ATT-1353).
 */
class AltitudeFromPressureDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockHardwareSensorManager: SensorManager
    private lateinit var mockPressureSensor: Sensor
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager
    private lateinit var mockKnownLocationsDbManager: KnownLocationsDatabaseManager

    private lateinit var latSensor: MySensor<Number>
    private lateinit var lngSensor: MySensor<Number>

    private val intentExtras = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        intentExtras.clear()

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Double>()) } answers {
            intentExtras[firstArg()] = secondArg<Double>()
            self as Intent
        }
        every { anyConstructed<Intent>().getDoubleExtra(any<String>(), any<Double>()) } answers {
            (intentExtras[firstArg()] as? Double) ?: secondArg()
        }
        every { anyConstructed<Intent>().action } answers { AltitudeFromPressureDevice.ALTITUDE_CORRECTION_INTENT }

        mockkStatic(SensorManager::class)
        every { SensorManager.getAltitude(any(), any()) } answers {
            val pressure = secondArg<Float>()
            (44330.0 * (1.0 - Math.pow((pressure / 1013.25), 0.19029495718363465))).toFloat()
        }

        mockkStatic(DevicesDatabaseManager::class)
        mockDevicesDbManager = mockk(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager
        every { mockDevicesDbManager.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_PRESSURE) } returns 201L

        mockkStatic(KnownLocationsDatabaseManager::class)
        mockKnownLocationsDbManager = mockk(relaxed = true)
        every { KnownLocationsDatabaseManager.getInstance(any()) } returns mockKnownLocationsDbManager

        mockPressureSensor = mockk(relaxed = true)
        mockHardwareSensorManager = mockk(relaxed = true)
        every { mockHardwareSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) } returns mockPressureSensor

        mockContext = mockk(relaxed = true)
        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockHardwareSensorManager
        every { mockContext.packageName } returns "com.atrainingtracker"

        mockSensorManager = mockk(relaxed = true)

        latSensor = MySensor(mockk(relaxed = true), SensorType.LATITUDE)
        lngSensor = MySensor(mockk(relaxed = true), SensorType.LONGITUDE)
        every { mockSensorManager.getSensor(SensorType.LATITUDE) } returns latSensor
        every { mockSensorManager.getSensor(SensorType.LONGITUDE) } returns lngSensor
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-CON-004.1: Cold-start null sensor value resilience.
     * When mAltitudeSensor.getValue() is null, setAltitudeCorrection falls back to mLastRawAltitude.
     */
    @Test
    fun testSetAltitudeCorrection_whenSensorValueNull_fallsBackToLastRawAltitude() {
        val device = AltitudeFromPressureDevice(mockContext, mockSensorManager)
        val altitudeSensor = device.altitudeSensor
        assertNotNull("Altitude sensor must be created", altitudeSensor)
        assertNull("On cold-start, sensor value must be null", altitudeSensor.value)

        // Set valid raw altitude (simulating pressure sensor fired first reading)
        device.lastRawAltitude = 500.0

        val broadcastSlot = slot<Intent>()
        every { mockContext.sendBroadcast(capture(broadcastSlot)) } returns Unit

        // Act: target location reference altitude is 520.0m
        device.setAltitudeCorrection(520.0)

        // Assert: correction should be 520.0 - 500.0 = +20.0m
        assertEquals(20.0, device.altitudeCorrection, 0.001)

        // Verify broadcast was dispatched
        verify(exactly = 1) { mockContext.sendBroadcast(any()) }
        assertEquals(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_INTENT, broadcastSlot.captured.action)
        assertEquals(20.0, broadcastSlot.captured.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0), 0.001)
    }

    /**
     * TST-CON-004.2: Post-warmup calibration when sensor value is non-null.
     */
    @Test
    fun testSetAltitudeCorrection_whenSensorValuePresent_calculatesDeltaFromCurrentValue() {
        val device = AltitudeFromPressureDevice(mockContext, mockSensorManager)
        val altitudeSensor = device.altitudeSensor

        // Simulate sensor already emitted readings
        altitudeSensor.newValue(505.0)
        device.lastRawAltitude = 500.0

        val broadcastSlot = slot<Intent>()
        every { mockContext.sendBroadcast(capture(broadcastSlot)) } returns Unit

        // Act: correct altitude is 520.0m
        device.setAltitudeCorrection(520.0)

        // Assert: correction is 520.0 - 505.0 = +15.0m
        assertEquals(15.0, device.altitudeCorrection, 0.001)

        verify(exactly = 1) { mockContext.sendBroadcast(any()) }
        assertEquals(15.0, broadcastSlot.captured.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0), 0.001)
    }

    /**
     * TST-CON-004.3: Double-fault graceful degradation.
     * When both mAltitudeSensor.getValue() is null and mLastRawAltitude is NaN, returns cleanly.
     */
    @Test
    fun testSetAltitudeCorrection_whenBothSensorValueAndLastRawAltitudeUnavailable_handlesGracefully() {
        val device = AltitudeFromPressureDevice(mockContext, mockSensorManager)
        assertNull(device.altitudeSensor.value)
        assertTrue(device.lastRawAltitude.isNaN())

        // Act
        device.setAltitudeCorrection(520.0)

        // Assert: no crash, correction remains default 0.0, zero broadcasts sent
        assertEquals(0.0, device.altitudeCorrection, 0.001)
        verify(exactly = 0) { mockContext.sendBroadcast(any()) }
    }

    /**
     * TST-CON-004.4: Zero-correction suppression.
     * When calculated correction is 0.0, no broadcast is emitted.
     */
    @Test
    fun testSetAltitudeCorrection_whenCorrectionIsZero_suppressesBroadcast() {
        val device = AltitudeFromPressureDevice(mockContext, mockSensorManager)
        device.altitudeSensor.newValue(520.0)

        // Act: target altitude matches current value
        device.setAltitudeCorrection(520.0)

        // Assert
        assertEquals(0.0, device.altitudeCorrection, 0.001)
        verify(exactly = 0) { mockContext.sendBroadcast(any()) }
    }

    /**
     * TST-CON-004.5: End-to-end cold-start sensor event integration (The ATT-1353 Crash Reproduction).
     * Simulates delivering the first pressure measurement when GPS location is already known.
     */
    @Test
    fun testOnSensorChanged_coldStartWithKnownLocation_initializesAndEmitsWithoutCrashing() {
        // Arrange
        val device = AltitudeFromPressureDevice(mockContext, mockSensorManager)

        // GPS coordinates already available
        latSensor.newValue(48.65)
        lngSensor.newValue(9.06)

        // Known location found at (48.65, 9.06) with reference altitude 520.0m
        val mockLocation = KnownLocationsDatabaseManager.MyLocation(
            1L,
            48.65,
            9.06,
            "Home Test Base",
            520.0,
            50,
            10
        )
        every { mockKnownLocationsDbManager.getMyLocation(any<LatLng>()) } returns mockLocation

        // Mock SensorManager.getAltitude to return 500.0f for this test pressure value
        val testPressure = 954.61f
        every { SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, testPressure) } returns 500.0f

        val broadcastSlot = slot<Intent>()
        every { mockContext.sendBroadcast(capture(broadcastSlot)) } returns Unit

        // Act: dispatch the first pressure measurement
        device.handlePressureMeasurement(testPressure)

        // Assert: initialized without NullPointerException / SIGABRT
        assertTrue("Pressure sensor must be marked initialized", device.isPressureSensorInitialized)
        assertEquals("Raw altitude must be 500.0", 500.0, device.lastRawAltitude, 0.001)
        assertEquals("Altitude correction must be +20.0", 20.0, device.altitudeCorrection, 0.001)

        // Sensor value must now reflect corrected reference altitude (500 + 20 = 520)
        assertEquals(520.0, (device.altitudeSensor.value as Number).toDouble(), 0.001)

        // Verify broadcast was dispatched
        verify(exactly = 1) { mockContext.sendBroadcast(any()) }
        assertEquals(20.0, broadcastSlot.captured.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0), 0.001)

        // Verify raw altitude was passed to location learning (REQ-DAT-007 invariant)
        verify(exactly = 1) {
            mockKnownLocationsDbManager.learnLocation(any<LatLng>(), 500.0, ExtremaType.START)
        }
    }

    /**
     * Additional coverage: when known location is NOT found, raw altitude is emitted without correction.
     */
    @Test
    fun testOnSensorChanged_whenKnownLocationNotFound_doesNotTriggerCorrection() {
        val device = AltitudeFromPressureDevice(mockContext, mockSensorManager)

        latSensor.newValue(48.65)
        lngSensor.newValue(9.06)

        // No known location found
        every { mockKnownLocationsDbManager.getMyLocation(any<LatLng>()) } returns null

        val testPressure = 954.61f
        every { SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, testPressure) } returns 500.0f

        // Act
        device.handlePressureMeasurement(testPressure)

        // Assert
        assertTrue(device.isPressureSensorInitialized)
        assertEquals(0.0, device.altitudeCorrection, 0.001)
        assertEquals(500.0, (device.altitudeSensor.value as Number).toDouble(), 0.001)
        verify(exactly = 0) { mockContext.sendBroadcast(any()) }
    }
}
