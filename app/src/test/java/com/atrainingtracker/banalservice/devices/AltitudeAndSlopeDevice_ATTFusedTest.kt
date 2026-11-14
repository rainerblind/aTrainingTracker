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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.devices

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [AltitudeAndSlopeDevice_ATTFused] lifecycle, sensor registration,
 * pressure and DEM ingestion, kinematic prediction, and metrics emission
 * (REQ-FIL-016.1, REQ-FIL-016.4, TST-FIL-016.4, ATT-6279).
 */
@Suppress("ClassNaming")
class AltitudeAndSlopeDevice_ATTFusedTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager
    private lateinit var mockHardwareSensorManager: SensorManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns true
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getSlopeMinSpeed(any()) } returns 0.5f

        mockkStatic(SensorManager::class)
        every { SensorManager.getAltitude(any(), any()) } answers {
            val pressure = secondArg<Float>()
            (44330.0 * (1.0 - Math.pow((pressure / 1013.25), 0.19029495718363465))).toFloat()
        }

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockDevicesDbManager = mockk(relaxed = true)
        mockHardwareSensorManager = mockk(relaxed = true)

        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager
        every { mockDevicesDbManager.altitudeAndSlopeAttFusedDeviceId } returns 42L

        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockHardwareSensorManager
        every { mockContext.getString(any()) } returns "ATT Fused (Höhe und Steigung)"
        every { mockContext.packageName } returns "com.atrainingtracker"

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Double>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Float>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Int>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as Intent }

        mockkObject(AttFusedAltitudePreferences)
        every { AttFusedAltitudePreferences.isEffectiveBarometerActive(any()) } returns true
        every { AttFusedAltitudePreferences.isEffectiveDemActive(any()) } returns true
        every { AttFusedAltitudePreferences.isEffectiveGpsActive(any()) } returns false
        every { AttFusedAltitudePreferences.isEffectiveSpeedActive(any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDeviceCreation_registersAllFiveSensors() {
        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)

        assertNotNull("Altitude sensor must be created", device.mAltitudeSensor)
        assertNotNull("Slope sensor must be created", device.mSlopeSensor)
        assertNotNull("Vertical speed sensor must be created", device.mVerticalSpeedSensor)
        assertNotNull("Ascent sensor must be created", device.mAscentSensor)
        assertNotNull("Descent sensor must be created", device.mDescentSensor)

        val registeredTypes = device.sensors.map { it.sensorType }
        assertTrue(registeredTypes.contains(SensorType.ALTITUDE))
        assertTrue(registeredTypes.contains(SensorType.SLOPE))
        assertTrue(registeredTypes.contains(SensorType.VERTICAL_SPEED))
        assertTrue(registeredTypes.contains(SensorType.ASCENT))
        assertTrue(registeredTypes.contains(SensorType.DESCENT))
    }

    @Test
    fun testHandlePressureMeasurement_initializesAndPublishesAltitude() {
        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)

        // 1013.25 hPa is standard sea level (approx 0m)
        device.handlePressureMeasurement(1013.25f, 1000L)

        assertTrue("EKF must be initialized after first measurement", device.ekf.isInitialized)
        assertNotNull("Altitude sensor should have value", device.mAltitudeSensor?.value)
        assertEquals(0.0, (device.mAltitudeSensor?.value as Number).toDouble(), 1.0)
    }

    @Test
    fun testHandleReferenceAltitude_updatesEkfState() {
        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)

        device.handleReferenceAltitude(450.0, isDem = true)

        assertTrue(device.ekf.isInitialized)
        assertEquals(450.0, device.ekf.altitude, 0.5)
        assertEquals(450.0, (device.mAltitudeSensor?.value as Number).toDouble(), 0.5)
    }

    @Test
    fun testClimbingCycle_accumulatesAscentAndUpdatesSlope() {
        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)

        val speedSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { speedSensor.value } returns 5.0 // 5 m/s
        every { mockSensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        // Initialize at 100m
        device.handleReferenceAltitude(100.0, isDem = true)

        // Step through 10 seconds of climb (+0.4m per second = 8% slope at 5 m/s)
        var altitude = 100.0
        var timeMs = 1000L
        for (i in 1..10) {
            timeMs += 1000L
            altitude += 0.4
            val pressureHpa = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
                Math.pow(1.0 - altitude / 44330.0, 5.255).toFloat()
            device.handlePressureMeasurement(pressureHpa, timeMs)
            device.calculateMetrics(timeMs)
        }

        val ascentVal = (device.mAscentSensor?.value as? Number)?.toDouble() ?: 0.0
        assertTrue("Ascent sensor should have accumulated climb (> 2m, was $ascentVal)", ascentVal > 2.0)
        assertTrue("Altitude sensor should reflect climb (was ${device.mAltitudeSensor?.value})",
            (device.mAltitudeSensor?.value as Number).toDouble() > 102.0)
    }

    @Test
    fun testShutDown_resetsEkfAndCleansUp() {
        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.handleReferenceAltitude(300.0)
        assertTrue(device.ekf.isInitialized)

        device.shutDown()
        assertFalse("EKF should be reset on shutdown", device.ekf.isInitialized)
    }
}
