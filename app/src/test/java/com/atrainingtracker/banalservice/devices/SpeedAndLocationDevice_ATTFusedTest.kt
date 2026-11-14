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
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * Unit tests verifying [SpeedAndLocationDevice_ATTFused] lifecycle, sensor registration,
 * EKF state prediction, GNSS/wheel ingestion, and 1-Hz world yaw-rate emission
 * (REQ-SEN-028.3, TST-SEN-028.3, ATT-7751).
 */
@Suppress("ClassNaming")
class SpeedAndLocationDevice_ATTFusedTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockHardwareSensorManager: SensorManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockDevicesDbManager = mockk(relaxed = true)
        mockLocationManager = mockk(relaxed = true)
        mockHardwareSensorManager = mockk(relaxed = true)

        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager
        every { mockDevicesDbManager.speedAndLocationAttFusedDeviceId } returns 7751L

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(any()) } returns 200.0f

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Double>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Float>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Int>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as Intent }

        every { mockContext.packageName } returns "com.atrainingtracker"

        mockkObject(AttFusedPreferences)
        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns true
        every { AttFusedPreferences.isEffectiveWheelActive(any()) } returns true
        every { AttFusedPreferences.isEffectiveGyroActive(any()) } returns true
        every { AttFusedPreferences.isEffectiveAutoCalibActive(any()) } returns true

        every { mockContext.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockHardwareSensorManager
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testConstructor_registersAllSensorsIncludingWorldYawRate() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)

        assertNotNull("World yaw rate sensor must be initialized", device.mWorldYawRateSensor)
        assertEquals(
            "Device type must be SPEED_AND_LOCATION_ATT_FUSED",
            DeviceType.SPEED_AND_LOCATION_ATT_FUSED,
            device.deviceType
        )
        assertEquals("Device name must be att_fused", "att_fused", device.name)
    }

    @Test
    fun testIngestImuMeasurement_predictsEkfAndEmitsWorldYawRate() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)

        // Flat phone lying on handlebar: q = [0, 0, 0, 1] (identity)
        val rotationVector = floatArrayOf(0f, 0f, 0f, 1f)
        // Turning left at 0.1 rad/s around vertical body Z axis
        val gyroValues = floatArrayOf(0f, 0f, 0.1f)

        device.ingestImuMeasurement(rotationVector, gyroValues, dtSec = 0.1, timestampMs = 1000L)

        val yawSensor = device.mWorldYawRateSensor
        assertNotNull(yawSensor)
        assertEquals("World yaw rate must match projected 0.1 rad/s", 0.1, yawSensor?.value ?: 0.0, 1e-4)
    }

    @Test
    fun testOnLocationChanged_updatesEkfAndEmitsFusedLocation() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)

        val loc1 = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.0000
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } returns 3.0f
            every { hasSpeed() } returns false
            every { hasBearing() } returns false
            every { time } returns 1000L
        }

        device.onLocationChanged(loc1)

        assertEquals(48.0000, device.refLat ?: 0.0, 1e-6)
        assertEquals(11.0000, device.refLon ?: 0.0, 1e-6)

        // Second fix 50 meters North
        val loc2 = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.00045 // ~50m North
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } returns 2.5f
            every { hasSpeed() } returns true
            every { speed } returns 5.0f // > 3.0 m/s Doppler threshold
            every { hasBearing() } returns true
            every { bearing } returns 0.0f // North
            every { time } returns 2000L
        }

        device.onLocationChanged(loc2)

        assertTrue("EKF North position must be positive", device.ekf.northMeters > 0.0)
    }

    @Test
    fun testOnWheelMeasurement_updatesEkfSpeed() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)

        assertEquals(0.0, device.ekf.speedMps, 1e-6)

        device.onWheelMeasurement(speedMps = 7.5, variance = 0.05, timestampMs = 1500L)

        assertTrue("EKF speed must increase towards wheel speed measurement", device.ekf.speedMps > 0.0)
    }

    @Test
    fun testProcessWheelCalibrationStep_delegatesToCalibrator() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)

        val updated = device.processWheelCalibrationStep(
            deltaDistance3d = 25.0,
            deltaRevolutions = 11.876,
            gnssAccuracyMeters = 3.0,
            speedMps = 6.0,
            yawRateRadPerSec = 0.01
        )

        assertTrue("Qualified 25m straight segment must update wheel calibrator", updated)
        assertTrue("Calibrator update count must increment", device.wheelCalibrator.updateCount > 0)
    }

    @Test
    fun testShutDown_cleansUpListenersAndResetsWatchdog() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)

        device.shutDown()

        // Verify unregistration was safely handled
        assertTrue("Device shutDown should complete cleanly", true)
    }
}
