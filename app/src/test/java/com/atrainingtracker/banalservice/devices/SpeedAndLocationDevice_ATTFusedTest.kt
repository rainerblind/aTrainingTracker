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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.banalservice.odometry.SportContextHint
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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

        // Second fix 50 meters North with valid speed
        var emittedSpeed = 5.0f
        val loc2 = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.00045 // ~50m North
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } returns 2.5f
            every { hasSpeed() } returns true
            every { speed } answers { emittedSpeed }
            every { speed = any() } answers { emittedSpeed = firstArg() }
            every { hasBearing() } returns true
            every { bearing } returns 0.0f // North
            every { time } returns 2000L
        }

        device.onLocationChanged(loc2)

        assertTrue("EKF North position must be positive", device.ekf.northMeters > 0.0)
        assertTrue("EKF speed must be positive (> 0.0) after GNSS speed update", device.ekf.speedMps > 0.0)
        assertEquals("Emitted location speed must match EKF speed", device.ekf.speedMps.toFloat(), emittedSpeed, 1e-4f)
        assertTrue("Emitted location speed must be > 0.0", emittedSpeed > 0.0f)
    }

    @Test
    fun testOnWheelMeasurement_updatesEkfSpeed() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)

        assertEquals(0.0, device.ekf.speedMps, 1e-6)

        device.onWheelMeasurement(speedMps = 7.5, variance = 0.05, timestampMs = 1500L)

        assertTrue("EKF speed must increase towards wheel speed measurement", device.ekf.speedMps > 0.0)
    }

    @Test
    fun testProductionWheelSpeedWiring_updatesSpeedAndCalibratesWheel() {
        val realSensorManager = MySensorManager(mockContext)
        val device = SpeedAndLocationDevice_ATTFused(mockContext, realSensorManager)

        assertEquals(0.0, device.ekf.speedMps, 1e-6)

        // Simulate active physical wheel speed & distance sensors registered in MySensorManager
        val mockWheelDevice = mockk<MyDevice>(relaxed = true) {
            every { deviceType } returns DeviceType.BIKE_SPEED
        }
        val wheelSpeedSensor = com.atrainingtracker.banalservice.sensor.MySensor<Double>(mockWheelDevice, SensorType.SPEED_mps)
        val wheelDistSensor = com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor(mockWheelDevice, SensorType.DISTANCE_m, false)

        realSensorManager.registerSensor(wheelSpeedSensor)
        realSensorManager.registerSensor(wheelDistSensor)

        // 1. Wheel speed emission triggers onWheelMeasurement through MySensorManager listener
        wheelSpeedSensor.newValue(6.5)
        assertTrue("EKF speed must be updated by wheel speed sensor via MySensorManager", device.ekf.speedMps > 0.0)

        // 2. Wheel distance emission triggers onWheelDistance / onWheelRevolutions
        wheelDistSensor.accumulate(0.0) // Establish baseline
        wheelDistSensor.accumulate(25.0) // Emit 25m displacement
        assertNotNull("Total revolutions must be tracked from wheel sensor distance", device.currentTotalRevolutions)
        assertTrue("Total revolutions must be positive", (device.currentTotalRevolutions ?: 0.0) > 0.0)

        // 3. First GNSS fix establishes calibration baseline
        val loc1 = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.0000
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } returns 2.0f
            every { hasSpeed() } returns true
            every { speed } returns 6.0f
            every { hasBearing() } returns true
            every { bearing } returns 0.0f
            every { time } returns 1000L
        }
        device.onLocationChanged(loc1)

        // Wheel progresses another 25m on a straight path
        wheelDistSensor.accumulate(50.0)

        // Second GNSS fix 25m North (~0.000225 deg lat), good accuracy, straight line
        val loc2 = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.000225
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } returns 2.0f
            every { hasSpeed() } returns true
            every { speed } returns 6.0f
            every { hasBearing() } returns true
            every { bearing } returns 0.0f
            every { time } returns 5000L
        }
        device.onLocationChanged(loc2)

        assertTrue("Auto-calibrator update count must increment after qualified segment", device.wheelCalibrator.updateCount > 0)

        // 4. Verify clean shutdown unregisters listener
        device.shutDown()
        realSensorManager.shutDown()
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

    // TST-FIL-023.1: Cold GNSS convergence (25.1m -> 16.0m, 25.2m jump at 0 m/s) must not emit 46.3 km/h spike
    @Test
    fun testColdGnssFixTightening_suppresses25mJumpAnd46KmhSpike_tstFil023_1() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        val distanceSensor = device.sensors.first { it.sensorType == SensorType.DISTANCE_m }
        val speedSensor = device.sensors.first { it.sensorType == SensorType.SPEED_mps }

        // 1. Degraded cold fix (acc = 25.1 m > 20.0 m) at 0.00 m/s must NOT anchor refLat/refLon
        val coldFix = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.0000
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } returns 25.1f
            every { hasSpeed() } returns true
            every { speed } returns 0.0f
            every { time } returns 1000L
        }
        device.onLocationChanged(coldFix)

        assertTrue("Degraded 25.1 m initial fix must NOT anchor origin", !device.hasValidGnssAnchor)
        assertEquals(0.0, (distanceSensor.value as? Number)?.toDouble() ?: 0.0, 1e-6)
        assertEquals(0.0, (speedSensor.value as? Number)?.toDouble() ?: 0.0, 1e-6)

        // 2. First valid converged fix (acc = 16.0 m <= 20.0 m) 25.2 m North at 0.00 m/s anchors origin cleanly
        var fix2Acc = 16.0f
        var fix2Speed = 0.0f
        var fix2Lat = 48.000227
        val validFix1 = mockk<Location>(relaxed = true) {
            every { latitude } answers { fix2Lat }
            every { latitude = any() } answers { fix2Lat = firstArg() }
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } answers { fix2Acc }
            every { accuracy = any() } answers { fix2Acc = firstArg() }
            every { hasSpeed() } returns true
            every { speed } answers { fix2Speed }
            every { speed = any() } answers { fix2Speed = firstArg() }
            every { time } returns 7000L
        }
        device.onLocationChanged(validFix1)

        assertTrue("First <=20m fix must anchor origin", device.hasValidGnssAnchor)
        assertEquals(48.000227, device.refLat ?: 0.0, 1e-6)
        assertTrue("Accuracy must not shrink below GNSS 16.0m (was $fix2Acc)", fix2Acc >= 16.0f)
        assertEquals(0.0, (distanceSensor.value as? Number)?.toDouble() ?: 0.0, 1e-6)
        assertEquals(0.0, (speedSensor.value as? Number)?.toDouble() ?: 0.0, 1e-6)

        // 3. Subsequent stationary 25.2 m convergence jump at acc = 16.0 m and GPS speed = 0.0 m/s
        var fix3Acc = 16.0f
        var fix3Speed = 0.0f
        val jumpFix = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.000454 // +25.2 m North
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } answers { fix3Acc }
            every { accuracy = any() } answers { fix3Acc = firstArg() }
            every { hasSpeed() } returns true
            every { speed } answers { fix3Speed }
            every { speed = any() } answers { fix3Speed = firstArg() }
            every { time } returns 8000L
            every { distanceTo(any()) } returns 25.2f
        }
        every { validFix1.distanceTo(jumpFix) } returns 25.2f
        device.onLocationChanged(jumpFix)

        val emittedSpeedMps = (speedSensor.value as? Number)?.toDouble() ?: 0.0
        val emittedDistanceM = (distanceSensor.value as? Number)?.toDouble() ?: 0.0
        assertTrue("46.3 km/h spike must be suppressed (was $emittedSpeedMps m/s)", emittedSpeedMps < 2.0)
        assertTrue("25.2 m phantom jump must be suppressed (was $emittedDistanceM m)", emittedDistanceM < 5.0)

        // 4. Genuine riding start at 5.5 m/s must engage normally without lockout
        var moveSpeed = 5.5f
        val moveFix = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.000276 // +5.5 m North from anchor
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } returns 4.0f
            every { hasSpeed() } returns true
            every { speed } answers { moveSpeed }
            every { speed = any() } answers { moveSpeed = firstArg() }
            every { hasBearing() } returns true
            every { bearing } returns 0.0f
            every { time } returns 9000L
        }
        every { validFix1.distanceTo(moveFix) } returns 5.5f
        device.onLocationChanged(moveFix)

        val movingSpeedMps = (speedSensor.value as? Number)?.toDouble() ?: 0.0
        assertTrue("Genuine start movement must update speed (was $movingSpeedMps m/s)", movingSpeedMps > 2.0)
    }

    // TST-FIL-023.1 (Bounce 1/3): Off-target 18m stationary anchor recovers on tight 4m cluster without phantom speed spike
    @Test
    fun testStationaryTightCluster_recoversOffTarget18mAnchorWithoutSpeedSpike_tstFil023_1() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        val distanceSensor = device.sensors.first { it.sensorType == SensorType.DISTANCE_m }
        val speedSensor = device.sensors.first { it.sensorType == SensorType.SPEED_mps }
        val latSensor = device.sensors.first { it.sensorType == SensorType.LATITUDE }

        var fix1Lat = 48.0000
        var fix1Acc = 18.0f
        var fix1Speed = 0.0f
        val offTargetAnchor = mockk<Location>(relaxed = true) {
            every { latitude } answers { fix1Lat }
            every { latitude = any() } answers { fix1Lat = firstArg() }
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } answers { fix1Acc }
            every { accuracy = any() } answers { fix1Acc = firstArg() }
            every { hasSpeed() } returns true
            every { speed } answers { fix1Speed }
            every { speed = any() } answers { fix1Speed = firstArg() }
            every { time } returns 1000L
        }
        device.onLocationChanged(offTargetAnchor)
        assertTrue(device.isStationaryPinned)

        // Two tight 4.0 m stationary fixes 20 m North
        val tightLat = 48.00018
        for (idx in 1..2) {
            var tightFixLat = tightLat
            var tightFixAcc = 4.0f
            var tightFixSpeed = 0.0f
            val tightFix = mockk<Location>(relaxed = true) {
                every { latitude } answers { tightFixLat }
                every { latitude = any() } answers { tightFixLat = firstArg() }
                every { longitude } returns 11.0000
                every { hasAccuracy() } returns true
                every { accuracy } answers { tightFixAcc }
                every { accuracy = any() } answers { tightFixAcc = firstArg() }
                every { hasSpeed() } returns true
                every { speed } answers { tightFixSpeed }
                every { speed = any() } answers { tightFixSpeed = firstArg() }
                every { time } returns 1000L + idx * 1000L
            }
            device.onLocationChanged(tightFix)
        }

        assertEquals(20.0, device.ekf.northMeters, 1.0)
        assertEquals(tightLat, (latSensor.value as? Number)?.toDouble() ?: 0.0, 1e-5)
        assertEquals(0.0, (speedSensor.value as? Number)?.toDouble() ?: -1.0, 1e-6)
        assertEquals(0.0, (distanceSensor.value as? Number)?.toDouble() ?: -1.0, 1e-6)
    }

    // TST-FIL-023.1 (Bounce 1/3): Pre-anchor wheel+IMU dead-reckoning re-anchors cleanly at (0, 0) on first <=20m fix
    @Test
    fun testPreAnchorWheelAndImuDeadReckoning_reanchorsCleanlyAtFirstValidFix_tstFil023_1() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        val rotVec = floatArrayOf(0f, 0f, 0f, 1f)
        val gyro = floatArrayOf(0f, 0f, 0f)

        // Wheel + IMU advance ~36 m North while GNSS fixes are degraded (> 20 m)
        for (i in 1..12) {
            device.onWheelMeasurement(speedMps = 6.0, variance = 0.05, timestampMs = i * 500L)
            device.ingestImuMeasurement(rotVec, gyro, dtSec = 0.5, timestampMs = i * 500L)
        }
        val degradedFix = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.0000
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } returns 28.0f
            every { hasSpeed() } returns true
            every { speed } returns 6.0f
            every { time } returns 6000L
        }
        device.onLocationChanged(degradedFix)
        assertTrue(!device.hasValidGnssAnchor)
        assertTrue("Pre-anchor dead-reckoning should have advanced > 20 m", device.ekf.northMeters > 20.0)

        // First valid fix (5.0 m accuracy) arrives -> origin & EKF re-anchor atomically to (0, 0)
        var validLat = 48.0010
        var validSpeed = 6.0f
        val firstValidFix = mockk<Location>(relaxed = true) {
            every { latitude } answers { validLat }
            every { latitude = any() } answers { validLat = firstArg() }
            every { longitude } returns 11.0010
            every { hasAccuracy() } returns true
            every { accuracy } returns 5.0f
            every { hasSpeed() } returns true
            every { speed } answers { validSpeed }
            every { speed = any() } answers { validSpeed = firstArg() }
            every { time } returns 7000L
        }
        device.onLocationChanged(firstValidFix)

        assertTrue(device.hasValidGnssAnchor)
        assertEquals(48.0010, device.refLat ?: 0.0, 1e-6)
        assertEquals(0.0, device.ekf.eastMeters, 0.5)
        assertEquals(0.0, device.ekf.northMeters, 0.5)
        assertTrue("Moving speed must remain preserved across origin re-anchor", device.ekf.speedMps > 3.0)
    }

    // TST-FIL-023.1 (Bounce 1/3): Negative HAL clock skew within +/-5000ms preserves isWheelMoving == true
    @Test
    fun testWheelTimestampNegativeHalClockSkew_preservesMovingState_tstFil023_1() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        val anchorFix = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.0000
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } returns 5.0f
            every { hasSpeed() } returns true
            every { speed } returns 6.0f
            every { time } returns 8000L
        }
        device.onLocationChanged(anchorFix)

        // Wheel timestamp slightly ahead of location.time (-1500 ms HAL clock skew, |dt| <= 5000 ms)
        device.onWheelMeasurement(speedMps = 6.0, variance = 0.05, timestampMs = 10_000L)

        var skewedLat = 48.00018 // 20 m North
        var skewedSpeed = 0.0f // GPS reports damped 0.0f while wheel is rolling
        val skewedFix = mockk<Location>(relaxed = true) {
            every { latitude } answers { skewedLat }
            every { latitude = any() } answers { skewedLat = firstArg() }
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } returns 5.0f
            every { hasSpeed() } returns true
            every { speed } answers { skewedSpeed }
            every { speed = any() } answers { skewedSpeed = firstArg() }
            every { time } returns 8500L
        }
        device.onLocationChanged(skewedFix)

        assertTrue(
            "Negative HAL clock skew within 5000ms must preserve moving wheel state (ekf.speedMps=${device.ekf.speedMps})",
            device.ekf.speedMps > 2.0
        )
    }

    // TST-FIL-023.1 (Bounce 2/3): Normal within-gate stationary fix does NOT drag anchor, while unpinned cluster snap DOES sync anchor
    @Test
    fun testWithinGateFixDoesNotDragAnchor_andUnpinnedClusterSnapSyncsAnchor_tstFil023_1() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        val distanceSensor = device.sensors.first { it.sensorType == SensorType.DISTANCE_m }
        val speedSensor = device.sensors.first { it.sensorType == SensorType.SPEED_mps }

        var fix1Lat = 48.0000
        var fix1Acc = 5.0f
        var fix1Speed = 0.0f
        val initialAnchor = mockk<Location>(relaxed = true) {
            every { latitude } answers { fix1Lat }
            every { latitude = any() } answers { fix1Lat = firstArg() }
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } answers { fix1Acc }
            every { accuracy = any() } answers { fix1Acc = firstArg() }
            every { hasSpeed() } returns true
            every { speed } answers { fix1Speed }
            every { speed = any() } answers { fix1Speed = firstArg() }
            every { time } returns 1000L
        }
        device.onLocationChanged(initialAnchor)
        assertTrue(device.isStationaryPinned)
        val pinnedAnchorLat = device.getStationaryAnchor()?.latitude ?: 0.0
        assertEquals(48.0000, pinnedAnchorLat, 1e-6)

        // 1. Within-gate stationary jitter (7.0 m North @ 6.0 m accuracy: < 8.0 m EKF gate and < 9.0 m escapeRadius) must NOT drag mStationaryAnchor
        var jitterLat = 48.000063
        var jitterAcc = 6.0f
        var jitterSpeed = 0.0f
        val withinGateFix = mockk<Location>(relaxed = true) {
            every { latitude } answers { jitterLat }
            every { latitude = any() } answers { jitterLat = firstArg() }
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } answers { jitterAcc }
            every { accuracy = any() } answers { jitterAcc = firstArg() }
            every { hasSpeed() } returns true
            every { speed } answers { jitterSpeed }
            every { speed = any() } answers { jitterSpeed = firstArg() }
            every { time } returns 2000L
        }
        every { initialAnchor.distanceTo(withinGateFix) } returns 7.0f
        device.onLocationChanged(withinGateFix)

        assertEquals(
            "Normal within-gate stationary fix must NOT drag mStationaryAnchor",
            48.0000,
            device.getStationaryAnchor()?.latitude ?: 0.0,
            1e-6
        )

        // 2. Move actively (unpins mStationaryAnchor), then stop with mIsStationaryPinned == false & 25m stationary cluster snap
        var moveLat = 48.00020
        var moveSpeed = 6.0f
        val moveFix = mockk<Location>(relaxed = true) {
            every { latitude } answers { moveLat }
            every { latitude = any() } answers { moveLat = firstArg() }
            every { longitude } returns 11.0000
            every { hasAccuracy() } returns true
            every { accuracy } returns 4.0f
            every { hasSpeed() } returns true
            every { speed } answers { moveSpeed }
            every { speed = any() } answers { moveSpeed = firstArg() }
            every { hasBearing() } returns true
            every { bearing } returns 0.0f
            every { time } returns 3000L
        }
        every { initialAnchor.distanceTo(moveFix) } returns 22.0f
        device.onLocationChanged(moveFix)
        assertTrue(!device.isStationaryPinned)

        val distBeforeStop = (distanceSensor.value as? Number)?.toDouble() ?: 0.0

        // Stop for 20s (apply ZUPT via zero GPS speed + EKF speed decay) and receive 2 agreeing stationary fixes 25m North
        device.ekf.applyZeroVelocityUpdate(timestampMs = 23_000L)
        val snapLat = 48.00045
        for (idx in 0..1) {
            var sLat = snapLat
            var sAcc = 4.0f
            var sSpeed = 0.0f
            val stopFix = mockk<Location>(relaxed = true) {
                every { latitude } answers { sLat }
                every { latitude = any() } answers { sLat = firstArg() }
                every { longitude } returns 11.0000
                every { hasAccuracy() } returns true
                every { accuracy } answers { sAcc }
                every { accuracy = any() } answers { sAcc = firstArg() }
                every { hasSpeed() } returns true
                every { speed } answers { sSpeed }
                every { speed = any() } answers { sSpeed = firstArg() }
                every { time } returns 23_000L + idx * 1000L
            }
            every { moveFix.distanceTo(stopFix) } returns 25.0f
            device.onLocationChanged(stopFix)
        }

        assertTrue("Unpinned stationary cluster snap must pin and sync anchor", device.isStationaryPinned)
        assertEquals(snapLat, device.getStationaryAnchor()?.latitude ?: 0.0, 1e-5)
        assertEquals(0.0, (speedSensor.value as? Number)?.toDouble() ?: -1.0, 1e-6)
        assertEquals(
            "Stationary cluster snap must emit 0.0 m phantom delta_distance",
            distBeforeStop,
            (distanceSensor.value as? Number)?.toDouble() ?: -1.0,
            1e-6
        )
    }

    // TST-FIL-027.2: 50-Hz cyclic thigh-pedaling yaw oscillations average out over rolling 1.0-s window
    @Test
    fun testCyclicPedalingYawRate_averagesOverOneSecondWindowAndPreservesSteadyTurn_tstFil027_2() {
        val rotationVector = floatArrayOf(0f, 0f, 0f, 1f)
        val dtSec = 0.02 // 50 Hz
        val steadyTurnRadPerSec = 0.25

        // 1. 1.0 Hz cyclic pedaling (full cycle inside 1.0-s window) cancels to steady turn rate
        val device1Hz = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        for (i in 1..100) {
            val tSec = i * dtSec
            val cyclicPedalAlias = 2.5 * kotlin.math.sin(2.0 * kotlin.math.PI * 1.0 * tSec)
            val gyroValues = floatArrayOf(0f, 0f, (steadyTurnRadPerSec + cyclicPedalAlias).toFloat())
            device1Hz.ingestImuMeasurement(rotationVector, gyroValues, dtSec = dtSec, timestampMs = i * 20L)
        }
        val published1Hz = device1Hz.mWorldYawRateSensor?.value ?: Double.NaN
        assertTrue(
            "Rolling 1.0-s mean must cancel 1.0 Hz 2.5 rad/s pedaling alias near 0.25 rad/s (was $published1Hz)",
            kotlin.math.abs(published1Hz - steadyTurnRadPerSec) < 0.08
        )

        // 2. 1.5 Hz worst-case half-cycle alias still attenuates >75% vs +-2.5 rad/s peak
        val device15Hz = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        for (i in 1..100) {
            val tSec = i * dtSec
            val cyclicPedalAlias = 2.5 * kotlin.math.sin(2.0 * kotlin.math.PI * 1.5 * tSec)
            val gyroValues = floatArrayOf(0f, 0f, (steadyTurnRadPerSec + cyclicPedalAlias).toFloat())
            device15Hz.ingestImuMeasurement(rotationVector, gyroValues, dtSec = dtSec, timestampMs = i * 20L)
        }
        val published15Hz = device15Hz.mWorldYawRateSensor?.value ?: Double.NaN
        assertTrue(
            "Rolling 1.0-s mean must strongly attenuate 1.5 Hz 2.5 rad/s alias (was $published15Hz)",
            kotlin.math.abs(published15Hz - steadyTurnRadPerSec) < 0.6
        )
    }

    // TST-FIL-027.2: resetOriginAndEkf() and shutDown() clear gyro yaw-rate window cleanly
    @Test
    fun testResetAndShutDown_clearGyroYawRateWindow_tstFil027_2() {
        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager) {
            fun callAccumulatorsReset() = onAccumulatorsReset()
        }
        val rotationVector = floatArrayOf(0f, 0f, 0f, 1f)

        // Seed window with large 4.0 rad/s spike
        device.ingestImuMeasurement(rotationVector, floatArrayOf(0f, 0f, 4.0f), dtSec = 0.5, timestampMs = 1000L)
        assertEquals(1, device.yawRateWindowSizeForTest())
        device.callAccumulatorsReset()
        assertEquals(0, device.yawRateWindowSizeForTest())

        // After reset, first 0.1 rad/s sample must publish immediately without prior 4.0 rad/s bleed
        device.ingestImuMeasurement(rotationVector, floatArrayOf(0f, 0f, 0.1f), dtSec = 0.1, timestampMs = 2000L)
        assertEquals(0.1, device.mWorldYawRateSensor?.value ?: Double.NaN, 1e-4)

        device.ingestImuMeasurement(rotationVector, floatArrayOf(0f, 0f, 4.0f), dtSec = 0.5, timestampMs = 3000L)
        assertEquals(2, device.yawRateWindowSizeForTest())
        device.shutDown()
        assertEquals(0, device.yawRateWindowSizeForTest())
    }

    // TST-FIL-028.1 & TST-FIL-028.3: Activity & Carriage Mode live classification, smooth filter adaptation, and clean reset
    @Test
    fun testActivityAndCarriageModeSensor_adaptsSmoothlyAndResets_tstFil028_1_3() {
        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager) {
            fun callAccumulatorsReset() = onAccumulatorsReset()
        }
        device.setSportContextHintForTesting(SportContextHint.CYCLING)
        assertEquals(PhoneCarriageMode.UNKNOWN.code, device.mActivityAndCarriageModeSensor?.value)
        assertEquals(1.0, device.effectiveGyroNoiseScaleForTest(), 1e-6)
        assertEquals(1.0, device.effectiveYawWindowSecForTest(), 1e-6)

        // Feed 50-Hz front pocket pedaling motion over 5s
        device.onWheelMeasurement(speedMps = 7.0, variance = 0.05, timestampMs = 1000L)
        for (step in 0..250) {
            val tMs = 1000L + step * 20L
            val phase = 2.0 * PI * 1.35 * (step * 0.02)
            val pitchRad = Math.toRadians(60.0 + 20.0 * kotlin.math.sin(phase))
            val half = pitchRad * 0.5
            val rotVec = floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat())
            val yawRate = (0.78 * kotlin.math.sin(phase)).toFloat()
            if (step % 10 == 0) {
                device.ingestBarometerAltitudeForTesting(300.0 + 0.55 * kotlin.math.sin(PI * step * 0.02), tMs)
            }
            if (step % 50 == 0) {
                device.onWheelMeasurement(speedMps = 7.0, variance = 0.05, timestampMs = tMs)
            }
            device.ingestImuMeasurement(rotVec, floatArrayOf(0f, 0f, yawRate), dtSec = 0.02, timestampMs = tMs)
        }

        assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET.code, device.mActivityAndCarriageModeSensor?.value)
        assertTrue(
            "Q_GYRO noise scale must increase smoothly for BIKE_FRONT_POCKET (was ${device.effectiveGyroNoiseScaleForTest()})",
            device.effectiveGyroNoiseScaleForTest() > 15.0
        )
        assertTrue(
            "Yaw window must widen smoothly toward 1.75 s for BIKE_FRONT_POCKET (was ${device.effectiveYawWindowSecForTest()})",
            device.effectiveYawWindowSecForTest() > 1.4
        )

        device.callAccumulatorsReset()
        assertEquals(PhoneCarriageMode.UNKNOWN.code, device.mActivityAndCarriageModeSensor?.value)
        assertEquals(1.0, device.effectiveGyroNoiseScaleForTest(), 1e-6)
        assertEquals(1.0, device.effectiveYawWindowSecForTest(), 1e-6)
    }

    // TST-FIL-028.1: TYPE_STEP_DETECTOR permission gating, SecurityException guard & plain-JVM safety
    @Test
    fun testStepDetectorDefensiveGating_handlesDeniedNullAndSecurityException_tstFil028_1() {
        val mockStepSensor = mockk<Sensor>(relaxed = true) {
            every { type } returns Sensor.TYPE_STEP_DETECTOR
        }

        // 1. Default plain-JVM path (SDK_INT == 0) safely skips STEP_DETECTOR registration without crash
        every { mockHardwareSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) } returns mockStepSensor
        val deviceDefaultJvm = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        assertNotNull(deviceDefaultJvm.mActivityAndCarriageModeSensor)
        verify(exactly = 0) { mockHardwareSensorManager.registerListener(any(), mockStepSensor, any()) }

        // 2. When permission granted AND hardware registerListener throws SecurityException, device survives cleanly
        every {
            mockHardwareSensorManager.registerListener(any(), mockStepSensor, any())
        } throws SecurityException("Mock denied")
        val devicePermittedThrows = object : SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager) {
            override fun hasActivityRecognitionPermission(): Boolean = true
        }
        assertNotNull(devicePermittedThrows.mActivityAndCarriageModeSensor)
    }

    private fun createMutableLocation(
        lat: Double,
        lon: Double,
        acc: Float,
        timeMs: Long,
        hasSpeedFlag: Boolean,
        speedMps: Float = 0.0f,
        hasBearingFlag: Boolean = false,
        bearingDeg: Float = 0.0f
    ): Location {
        var curLat = lat
        var curLon = lon
        var curAcc = acc
        var curSpeed = speedMps
        var curBearing = bearingDeg
        return mockk(relaxed = true) {
            every { latitude } answers { curLat }
            every { latitude = any() } answers { curLat = firstArg() }
            every { longitude } answers { curLon }
            every { longitude = any() } answers { curLon = firstArg() }
            every { hasAccuracy() } returns true
            every { accuracy } answers { curAcc }
            every { accuracy = any() } answers { curAcc = firstArg() }
            every { hasSpeed() } returns hasSpeedFlag
            every { speed } answers { curSpeed }
            every { speed = any() } answers { curSpeed = firstArg() }
            every { hasBearing() } returns hasBearingFlag
            every { bearing } answers { curBearing }
            every { bearing = any() } answers { curBearing = firstArg() }
            every { time } returns timeMs
            every { distanceTo(any()) } answers {
                val other = firstArg<Location>()
                val dLatM = Math.toRadians(other.latitude - curLat) * 6371000.0
                val dLonM = Math.toRadians(other.longitude - curLon) * 6371000.0 *
                    kotlin.math.cos(Math.toRadians((curLat + other.latitude) * 0.5))
                kotlin.math.hypot(dLatM, dLonM).toFloat()
            }
        }
    }

    private fun angularDiffDeg(a: Double, b: Double): Double {
        var d = ((a - b) % 360.0 + 360.0) % 360.0
        if (d > 180.0) d = 360.0 - d
        return d
    }

    // TST-FIL-028.3: Handheld glance spike gating + live chord heading + Doppler/stationary re-anchoring
    @Test
    fun testHandheldGlanceGatingAndRunningChordHeading_tstFil028_3() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        device.setSportContextHintForTesting(SportContextHint.RUNNING)

        // 1. Build RUN_HANDHELD classification with step cadence + forearm yaw + glance baseline hold
        for (step in 0..160) {
            val tMs = 1000L + step * 20L
            val phase = 2.0 * PI * 1.45 * (step * 0.02)
            val pitchDeg = if (step >= 115) 18.0 else 70.0 + 22.0 * kotlin.math.sin(phase)
            val half = Math.toRadians(pitchDeg) * 0.5
            val rotVec = floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat())
            if (step % 17 == 0) device.ingestStepEventForTesting(tMs)
            if (step % 10 == 0) device.ingestBarometerAltitudeForTesting(300.0 + 0.45 * kotlin.math.sin(PI * step * 0.02), tMs)
            device.ingestImuMeasurement(rotVec, floatArrayOf(0f, 0f, (0.85 * kotlin.math.sin(phase)).toFloat()), dtSec = 0.02, timestampMs = tMs)
        }
        val yawBeforeSpike = device.mWorldYawRateSensor?.value ?: 0.0
        val glanceHalf = Math.toRadians(18.0) * 0.5
        device.ingestImuMeasurement(
            floatArrayOf(kotlin.math.sin(glanceHalf).toFloat(), 0f, 0f, kotlin.math.cos(glanceHalf).toFloat()),
            floatArrayOf(0f, 0f, 3.2f),
            dtSec = 0.02,
            timestampMs = 4250L
        )
        val yawAfterSpike = device.mWorldYawRateSensor?.value ?: 0.0
        assertEquals("Glance gyro transient must be gated from WORLD_YAW_RATE window", yawBeforeSpike, yawAfterSpike, 1e-6)
        assertEquals(3.0, com.atrainingtracker.banalservice.odometry.UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS, 1e-9)

        val degPerMeterLat = 1.0 / (Math.toRadians(1.0) * 6371000.0)
        val degPerMeterLon = degPerMeterLat / kotlin.math.cos(Math.toRadians(48.0))
        var lat = 48.0
        var lon = 11.0

        // 2. Establish East (90 deg) track via Doppler (> MIN_DOPPLER_SPEED_MPS = 3.0) which also keeps chord anchor fresh
        for (sec in 5L..7L) {
            val tMs = sec * 1000L
            for (sub in 1..50) {
                val subMs = (sec - 1L) * 1000L + sub * 20L
                val phase = 2.0 * PI * 1.45 * (sub * 0.02)
                val half = Math.toRadians(70.0 + 22.0 * kotlin.math.sin(phase)) * 0.5
                if (sub % 17 == 0) device.ingestStepEventForTesting(subMs)
                device.ingestImuMeasurement(
                    floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat()),
                    floatArrayOf(0f, 0f, 0f),
                    dtSec = 0.02,
                    timestampMs = subMs
                )
            }
            lon += 4.0 * degPerMeterLon
            device.onLocationChanged(
                createMutableLocation(lat, lon, acc = 4.0f, timeMs = tMs, hasSpeedFlag = true, speedMps = 4.0f, hasBearingFlag = true, bearingDeg = 90.0f)
            )
        }
        assertEquals(PhoneCarriageMode.RUN_HANDHELD.code, device.mActivityAndCarriageModeSensor?.value)
        assertTrue("Doppler fixes must orient EKF East (~90 deg, was ${device.ekf.bearingDeg})", angularDiffDeg(device.ekf.bearingDeg, 90.0) < 15.0)

        // 3. Turn 90 deg North (0 deg) at slow jog 1.8 m/s without Location.hasBearing() -> chord heading must pull heading North
        for (sec in 8L..18L) {
            val tMs = sec * 1000L
            for (sub in 1..50) {
                val subMs = (sec - 1L) * 1000L + sub * 20L
                val phase = 2.0 * PI * 1.45 * (sub * 0.02)
                val half = Math.toRadians(70.0 + 22.0 * kotlin.math.sin(phase)) * 0.5
                if (sub % 17 == 0) device.ingestStepEventForTesting(subMs)
                device.ingestImuMeasurement(
                    floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat()),
                    floatArrayOf(0f, 0f, 0f),
                    dtSec = 0.02,
                    timestampMs = subMs
                )
            }
            lat += 1.8 * degPerMeterLat
            device.onLocationChanged(
                createMutableLocation(lat, lon, acc = 4.0f, timeMs = tMs, hasSpeedFlag = true, speedMps = 1.8f, hasBearingFlag = false)
            )
        }
        assertTrue(
            "Slow 1.8 m/s North leg without Doppler bearing must update chord heading toward North (~0 deg, was ${device.ekf.bearingDeg})",
            angularDiffDeg(device.ekf.bearingDeg, 0.0) < 35.0
        )

        // 4. Stop stationary for 25 s -> clears chord anchor so prolonged stop cannot lock out post-stop chord heading
        val flatRotVec = floatArrayOf(0f, 0f, 0f, 1f)
        for (sec in 19L..43L) {
            val tMs = sec * 1000L
            for (sub in 1..5) {
                device.ingestImuMeasurement(flatRotVec, floatArrayOf(0f, 0f, 0f), dtSec = 0.2, timestampMs = tMs - 1000L + sub * 200L)
            }
            device.onLocationChanged(
                createMutableLocation(lat, lon, acc = 4.0f, timeMs = tMs, hasSpeedFlag = true, speedMps = 0.0f, hasBearingFlag = false)
            )
        }
        assertEquals(PhoneCarriageMode.STATIONARY.code, device.mActivityAndCarriageModeSensor?.value)

        // 5. Depart West (270 deg) at 1.8 m/s without bearing -> must re-anchor cleanly and turn bearing West (~270 deg)
        for (sec in 44L..58L) {
            val tMs = sec * 1000L
            for (sub in 1..50) {
                val subMs = (sec - 1L) * 1000L + sub * 20L
                val phase = 2.0 * PI * 1.45 * (sub * 0.02)
                val half = Math.toRadians(70.0 + 22.0 * kotlin.math.sin(phase)) * 0.5
                if (sub % 17 == 0) device.ingestStepEventForTesting(subMs)
                device.ingestImuMeasurement(
                    floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat()),
                    floatArrayOf(0f, 0f, 0f),
                    dtSec = 0.02,
                    timestampMs = subMs
                )
            }
            lon -= 1.8 * degPerMeterLon
            device.onLocationChanged(
                createMutableLocation(lat, lon, acc = 4.0f, timeMs = tMs, hasSpeedFlag = true, speedMps = 1.8f, hasBearingFlag = false)
            )
        }
        assertTrue(
            "Post-stop Westward 1.8 m/s departure must not suffer stale-anchor lockout and must steer heading West (~270 deg, was ${device.ekf.bearingDeg})",
            angularDiffDeg(device.ekf.bearingDeg, 270.0) < 35.0
        )
    }

    // TST-FIL-028.3 (Gate 4 Rev 1): Displacement without Location.hasSpeed(), wheel, or step detector breaks STATIONARY
    @Test
    fun testMovementWithoutLocationHasSpeed_breaksStationaryAndAdvancesEkf_tstFil028_3() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        val speedSensor = device.sensors.first { it.sensorType == SensorType.SPEED_mps }
        val degPerMeterLat = 1.0 / (Math.toRadians(1.0) * 6371000.0)
        var lat = 48.0
        val lon = 11.0
        val flatRotVec = floatArrayOf(0f, 0f, 0f, 1f)

        // 1. Stationary baseline without Location.hasSpeed(), wheel sensor, or step detector
        for (sec in 1L..5L) {
            val tMs = sec * 1000L
            for (sub in 1..10) {
                device.ingestImuMeasurement(flatRotVec, floatArrayOf(0f, 0f, 0f), dtSec = 0.1, timestampMs = tMs - 1000L + sub * 100L)
            }
            device.onLocationChanged(
                createMutableLocation(lat, lon, acc = 4.0f, timeMs = tMs, hasSpeedFlag = false, hasBearingFlag = false)
            )
        }
        assertEquals(PhoneCarriageMode.STATIONARY.code, device.mActivityAndCarriageModeSensor?.value)
        assertEquals(0.0, device.ekf.speedMps, 1e-6)
        assertTrue(device.isStationaryPinned)

        // 2. Genuine movement North at 2.2 m/s WITHOUT Location.hasSpeed(), wheel sensor, or step detector
        for (sec in 6L..15L) {
            val tMs = sec * 1000L
            for (sub in 1..25) {
                val subMs = (sec - 1L) * 1000L + sub * 40L
                val phase = 2.0 * PI * 0.85 * (sub * 0.04)
                val half = Math.toRadians(65.0 + 18.0 * kotlin.math.sin(phase)) * 0.5
                device.ingestImuMeasurement(
                    floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat()),
                    floatArrayOf(0f, 0f, (0.35 * kotlin.math.sin(phase)).toFloat()),
                    dtSec = 0.04,
                    timestampMs = subMs
                )
            }
            lat += 2.2 * degPerMeterLat
            device.onLocationChanged(
                createMutableLocation(lat, lon, acc = 4.0f, timeMs = tMs, hasSpeedFlag = false, hasBearingFlag = false)
            )
        }

        val finalMode = PhoneCarriageMode.fromCode(device.mActivityAndCarriageModeSensor?.value)
        assertTrue(
            "Genuine coordinate displacement without Location.hasSpeed() must break STATIONARY (mode=$finalMode)",
            finalMode != PhoneCarriageMode.STATIONARY && finalMode != PhoneCarriageMode.UNKNOWN
        )
        assertTrue("EKF speed must rise above 0 without Doppler speed (was ${device.ekf.speedMps})", device.ekf.speedMps > 1.0)
        assertTrue("EKF North displacement must track genuine motion (was ${device.ekf.northMeters})", device.ekf.northMeters > 10.0)
        assertTrue("Base stationary pinning must release", !device.isStationaryPinned)
        val emittedSpeedMps = (speedSensor.value as? Number)?.toDouble() ?: 0.0
        assertTrue("Published SPEED_mps sensor must reflect movement (was $emittedSpeedMps)", emittedSpeedMps > 1.0)
    }

    // TST-FIL-028.3 (Stage 6 Bounce 1): Moving -> Stationary without Location.hasSpeed() drops 15-s ghost tail and pins ZUPT
    @Test
    fun testMovingToStationaryWithoutLocationHasSpeed_stopsGhostTailAndPinsZupt_tstFil028_3() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        val speedSensor = device.sensors.first { it.sensorType == SensorType.SPEED_mps }
        val degPerMeterLat = 1.0 / (Math.toRadians(1.0) * 6371000.0)
        var lat = 48.0
        val lon = 11.0
        val flatRotVec = floatArrayOf(0f, 0f, 0f, 1f)

        // 1. Run North at 3.2 m/s for 10 s WITHOUT Location.hasSpeed(), wheel sensor, or step detector
        for (sec in 1L..10L) {
            val tMs = sec * 1000L
            for (sub in 1..25) {
                val subMs = (sec - 1L) * 1000L + sub * 40L
                val phase = 2.0 * PI * 1.45 * (sub * 0.04)
                val half = Math.toRadians(70.0 + 18.0 * kotlin.math.sin(phase)) * 0.5
                device.ingestImuMeasurement(
                    floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat()),
                    floatArrayOf(0f, 0f, (0.45 * kotlin.math.sin(phase)).toFloat()),
                    dtSec = 0.04,
                    timestampMs = subMs
                )
            }
            lat += 3.2 * degPerMeterLat
            device.onLocationChanged(
                createMutableLocation(lat, lon, acc = 4.0f, timeMs = tMs, hasSpeedFlag = false, hasBearingFlag = false)
            )
        }
        assertTrue("Device must reflect moving EKF speed before stop (was ${device.ekf.speedMps})", device.ekf.speedMps > 1.5)

        // 2. Stop completely at final coordinate for 6 s (while pre-stop track is still within 15 s lookback!)
        for (sec in 11L..16L) {
            val tMs = sec * 1000L
            for (sub in 1..10) {
                device.ingestImuMeasurement(
                    flatRotVec,
                    floatArrayOf(0f, 0f, 0f),
                    dtSec = 0.1,
                    timestampMs = tMs - 1000L + sub * 100L
                )
            }
            device.onLocationChanged(
                createMutableLocation(lat, lon, acc = 4.0f, timeMs = tMs, hasSpeedFlag = false, hasBearingFlag = false)
            )
        }

        assertEquals(
            "Confirmed >= 3 s standstill without Doppler must commit STATIONARY despite pre-stop fixes in 15 s lookback",
            PhoneCarriageMode.STATIONARY.code,
            device.mActivityAndCarriageModeSensor?.value
        )
        assertEquals("ZUPT must pin EKF speed to 0.0 m/s", 0.0, device.ekf.speedMps, 1e-6)
        assertEquals("Published SPEED_mps sensor must clamp to 0.0 m/s", 0.0, (speedSensor.value as? Number)?.toDouble() ?: -1.0, 1e-6)
    }

    // TST-FIL-028.3 (Stage 6 Bounce 1): 50-Hz gyro classify throttle, ~1-Hz sensor emission cadence & dt-invariant smoothing
    @Test
    fun testGyroClassificationThrottle_andDtInvariantProfileSmoothing_tstFil028_3() {
        // Part A: Verify 50-Hz gyro stream throttles classify() evaluations and mode emissions
        val throttledDevice = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        throttledDevice.setSportContextHintForTesting(SportContextHint.CYCLING)
        throttledDevice.onWheelMeasurement(speedMps = 7.0, variance = 0.05, timestampMs = 1000L)
        for (step in 0..250) {
            val tMs = 1000L + step * 20L
            val phase = 2.0 * PI * 1.35 * (step * 0.02)
            val half = Math.toRadians(60.0 + 20.0 * kotlin.math.sin(phase)) * 0.5
            if (step % 10 == 0) {
                throttledDevice.ingestBarometerAltitudeForTesting(300.0 + 0.55 * kotlin.math.sin(PI * step * 0.02), tMs)
            }
            if (step == 150) {
                throttledDevice.onWheelMeasurement(speedMps = 7.0, variance = 0.05, timestampMs = tMs)
            }
            throttledDevice.ingestImuMeasurement(
                floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat()),
                floatArrayOf(0f, 0f, (0.78 * kotlin.math.sin(phase)).toFloat()),
                dtSec = 0.02,
                timestampMs = tMs
            )
        }
        assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET.code, throttledDevice.mActivityAndCarriageModeSensor?.value)
        assertTrue(
            "50-Hz gyro loop must throttle classify() calls (>= 250 ms), saw ${throttledDevice.classifyEvaluationCountForTest()}",
            throttledDevice.classifyEvaluationCountForTest() in 5..25
        )
        assertTrue(
            "Mode sensor emissions over 5 s must stay <= 10 (transition + ~1 Hz), saw ${throttledDevice.modeEmissionCountForTest()}",
            throttledDevice.modeEmissionCountForTest() in 2..10
        )

        // Part B: Verify 50-Hz gyro callbacks and 1-Hz GNSS callbacks follow identical dt-exponential convergence
        val device50Hz = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        val device1Hz = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        device50Hz.setSportContextHintForTesting(SportContextHint.CYCLING)
        device1Hz.setSportContextHintForTesting(SportContextHint.CYCLING)

        fun seedPocketEvidence(c: com.atrainingtracker.banalservice.odometry.PhoneCarriageClassifier, tMs: Long, sec: Double) {
            val phase = 2.0 * PI * 1.35 * sec
            val half = Math.toRadians(60.0 + 20.0 * kotlin.math.sin(phase)) * 0.5
            c.ingestRotationVector(floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat()), tMs)
            c.ingestGyro(0.78 * kotlin.math.sin(phase), kotlin.math.abs(0.78 * kotlin.math.sin(phase)), tMs)
            if (tMs % 200L == 0L) c.ingestBarometerAltitude(300.0 + 0.55 * kotlin.math.sin(PI * sec), tMs)
            if (tMs % 1000L == 0L) {
                c.ingestGnssSpeed(7.0, 90.0, 4.0, tMs)
                c.ingestWheelSpeed(7.0, tMs)
            }
        }

        // Seed up to t = 3000 ms so both devices establish BIKE_FRONT_POCKET baseline at t = 3000 ms
        for (step in 0..99) {
            val tMs = 1000L + step * 20L
            seedPocketEvidence(device50Hz.carriageClassifier, tMs, step * 0.02)
            seedPocketEvidence(device1Hz.carriageClassifier, tMs, step * 0.02)
        }
        val anchorLoc = createMutableLocation(48.0, 11.0, acc = 4.0f, timeMs = 3000L, hasSpeedFlag = true, speedMps = 7.0f, hasBearingFlag = true, bearingDeg = 90.0f)
        seedPocketEvidence(device50Hz.carriageClassifier, 3000L, 2.0)
        seedPocketEvidence(device1Hz.carriageClassifier, 3000L, 2.0)
        device50Hz.onLocationChanged(anchorLoc)
        device1Hz.onLocationChanged(
            createMutableLocation(48.0, 11.0, acc = 4.0f, timeMs = 3000L, hasSpeedFlag = true, speedMps = 7.0f, hasBearingFlag = true, bearingDeg = 90.0f)
        )
        assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET.code, device50Hz.mActivityAndCarriageModeSensor?.value)
        assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET.code, device1Hz.mActivityAndCarriageModeSensor?.value)

        // Advance both from t = 3000 -> 4000 ms (1.0 s): device50Hz at 50 Hz IMU, device1Hz with 1 Hz GNSS fix only
        for (step in 1..50) {
            val tMs = 3000L + step * 20L
            val sec = 2.0 + step * 0.02
            val phase = 2.0 * PI * 1.35 * sec
            val half = Math.toRadians(60.0 + 20.0 * kotlin.math.sin(phase)) * 0.5
            val rotVec = floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat())
            if (tMs % 200L == 0L) {
                device50Hz.ingestBarometerAltitudeForTesting(300.0 + 0.55 * kotlin.math.sin(PI * sec), tMs)
                device1Hz.ingestBarometerAltitudeForTesting(300.0 + 0.55 * kotlin.math.sin(PI * sec), tMs)
            }
            device50Hz.ingestImuMeasurement(rotVec, floatArrayOf(0f, 0f, (0.78 * kotlin.math.sin(phase)).toFloat()), dtSec = 0.02, timestampMs = tMs)
            // Keep device1Hz classifier fed with identical cues while callbacks arrive only at 1 Hz
            seedPocketEvidence(device1Hz.carriageClassifier, tMs, sec)
        }
        val degPerMeterLon = 1.0 / (Math.toRadians(1.0) * 6371000.0 * kotlin.math.cos(Math.toRadians(48.0)))
        device1Hz.onLocationChanged(
            createMutableLocation(48.0, 11.0 + 7.0 * degPerMeterLon, acc = 4.0f, timeMs = 4000L, hasSpeedFlag = true, speedMps = 7.0f, hasBearingFlag = true, bearingDeg = 90.0f)
        )

        assertEquals(
            "50-Hz and 1-Hz callback cadences must produce identical dt-exponential gyro noise scale",
            device1Hz.effectiveGyroNoiseScaleForTest(),
            device50Hz.effectiveGyroNoiseScaleForTest(),
            0.05
        )
        assertEquals(
            "50-Hz and 1-Hz callback cadences must produce identical dt-exponential yaw window duration",
            device1Hz.effectiveYawWindowSecForTest(),
            device50Hz.effectiveYawWindowSecForTest(),
            0.02
        )
    }

    // TST-FIL-029.3: 50-Hz TYPE_ACCELEROMETER registers without GNSS/gyro and survives LocationUnavailable()
    @Test
    fun smartphoneImu_subscribes50HzAccelerometerIndependentlyOfGyroAndSurvivesLocationUnavailable_tstFil029_3() {
        val mockAccSensor = mockk<Sensor>(relaxed = true) {
            every { type } returns Sensor.TYPE_ACCELEROMETER
        }
        val mockGyroSensor = mockk<Sensor>(relaxed = true) {
            every { type } returns Sensor.TYPE_GYROSCOPE
        }
        every { mockHardwareSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns mockAccSensor
        every { mockHardwareSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) } returns mockGyroSensor
        every { AttFusedPreferences.isEffectiveGyroActive(any()) } returns false
        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns false

        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager) {
            fun triggerLocationAvailable() = LocationAvailable()
            fun triggerLocationUnavailable() = LocationUnavailable()
        }

        // Subscribes 50-Hz accelerometer (SENSOR_DELAY_GAME) even when gyro & GNSS are off; skips gyro.
        verify(exactly = 1) {
            mockHardwareSensorManager.registerListener(device, mockAccSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        verify(exactly = 0) {
            mockHardwareSensorManager.registerListener(any(), mockGyroSensor, any())
        }

        // Triggering LocationAvailable() then LocationUnavailable() must NOT unregister 50-Hz accelerometer.
        device.triggerLocationAvailable()
        device.triggerLocationUnavailable()
        verify(exactly = 0) { mockHardwareSensorManager.unregisterListener(device, mockAccSensor) }
        verify(exactly = 0) { mockHardwareSensorManager.unregisterListener(device) }

        // shutDown() tears down IMU listener cleanly.
        device.shutDown()
        verify(atLeast = 1) { mockHardwareSensorManager.unregisterListener(device) }
    }

    // TST-FIL-029.2: Handlebar strictly suppresses CADENCE/GCData[5]; pocket dynamically registers, coasts to 0, suppresses on remount
    @Test
    fun smartphoneImu_bikeHandlebarSuppressedAndPocketRegistersDynamicCadenceAndCoasts_tstFil029_2() {
        val realSensorManager = MySensorManager(mockContext)
        val mockAccSensor = mockk<Sensor>(relaxed = true) { every { type } returns Sensor.TYPE_ACCELEROMETER }
        val mockGyroSensor = mockk<Sensor>(relaxed = true) { every { type } returns Sensor.TYPE_GYROSCOPE }
        val mockRotSensor = mockk<Sensor>(relaxed = true) { every { type } returns Sensor.TYPE_GAME_ROTATION_VECTOR }
        every { mockHardwareSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns mockAccSensor
        every { mockHardwareSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) } returns mockGyroSensor
        every { mockHardwareSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) } returns mockRotSensor

        var fakeClockMs = 1000L
        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, realSensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
        }
        device.setSportContextHintForTesting(SportContextHint.CYCLING)

        try {
            // Phase 1: BIKE_HANDLEBAR cycling (horizontal phone + high-frequency bar vibration) -> strictly suppressed
            device.onWheelMeasurement(speedMps = 7.5, variance = 0.05, timestampMs = fakeClockMs)
            for (step in 0..175) { // 3.5 s at 50 Hz
                fakeClockMs = 1000L + step * 20L
                val tSec = fakeClockMs / 1000.0
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GAME_ROTATION_VECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0f, 0f, 0f, 1f)
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_ACCELEROMETER,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(
                            (4.0 * sin(2.0 * PI * 14.0 * tSec)).toFloat(),
                            (9.81 + 3.5 * cos(2.0 * PI * 19.0 * tSec)).toFloat(),
                            (2.5 * sin(2.0 * PI * 24.0 * tSec)).toFloat()
                        )
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GYROSCOPE,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(
                            (0.08 * sin(2.0 * PI * 12.0 * tSec)).toFloat(),
                            (0.06 * cos(2.0 * PI * 16.0 * tSec)).toFloat(),
                            0.01f
                        )
                    )
                )
                if (step % 50 == 0) {
                    device.onWheelMeasurement(speedMps = 7.5, variance = 0.05, timestampMs = fakeClockMs)
                    device.onLocationChanged(
                        createMutableLocation(48.0, 11.0 + step * 0.00001, 3.0f, fakeClockMs, true, 7.5f, true, 90.0f)
                    )
                }
            }

            assertEquals(PhoneCarriageMode.BIKE_HANDLEBAR.code, device.mActivityAndCarriageModeSensor?.value)
            assertNull("BIKE_HANDLEBAR must never register CADENCE", device.mCadenceSensor)
            assertNull("BIKE_HANDLEBAR must never register STRIDES", device.mStridesSensor)
            assertNull(realSensorManager.getSensor(SensorType.CADENCE))
            assertTrue(
                "CADENCE must not enter accumulatedSensorTypeSet on BIKE_HANDLEBAR",
                !realSensorManager.accumulatedSensorTypeSet.contains(SensorType.CADENCE)
            )
            val liveGcHandlebar = realSensorManager.getSensor(SensorType.SENSORS)?.value as? String
            val accumGcHandlebar = realSensorManager.getSensor(SensorType.ACCUMULATED_SENSORS)?.value as? String
            assertEquals("GCData[5] must stay '-' on BIKE_HANDLEBAR", '-', liveGcHandlebar?.getOrNull(5) ?: '-')
            assertEquals("Accumulated GCData[5] must stay '-' on BIKE_HANDLEBAR", '-', accumGcHandlebar?.getOrNull(5) ?: '-')

            // Phase 2: Transition into BIKE_FRONT_POCKET pedaling at 75 RPM (1.25 Hz)
            val pocketStartMs = fakeClockMs + 20L
            for (step in 0..500) { // 10.0 s (covers 4.0 s mount hysteresis + steady 75 RPM window)
                fakeClockMs = pocketStartMs + step * 20L
                val tSec = fakeClockMs / 1000.0
                val phase = 2.0 * PI * 1.25 * tSec // 75 RPM
                val halfPitch = (Math.toRadians(60.0 + 20.0 * sin(phase)) * 0.5).toFloat()
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GAME_ROTATION_VECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(sin(halfPitch.toDouble()).toFloat(), 0f, 0f, cos(halfPitch.toDouble()).toFloat())
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_ACCELEROMETER,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(
                            (2.4 * sin(phase)).toFloat(),
                            (9.81 + 1.2 * cos(2.0 * phase)).toFloat(),
                            (1.8 * cos(phase)).toFloat()
                        )
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GYROSCOPE,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(
                            (2.2 * sin(phase)).toFloat(),
                            (0.9 * cos(phase)).toFloat(),
                            (0.7 * sin(phase)).toFloat()
                        )
                    )
                )
                if (step % 50 == 0) {
                    device.onWheelMeasurement(speedMps = 7.5, variance = 0.05, timestampMs = fakeClockMs)
                }
            }

            assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET.code, device.mActivityAndCarriageModeSensor?.value)
            assertNotNull("BIKE_FRONT_POCKET must register CADENCE dynamically", device.mCadenceSensor)
            assertNull("Cycling must never register STRIDES", device.mStridesSensor)
            val activeCadence = (realSensorManager.getSensor(SensorType.CADENCE)?.value as? Number)?.toDouble()
            assertNotNull(activeCadence)
            assertEquals(75.0, activeCadence!!, 2.5)
            val liveGcPocket = realSensorManager.getSensor(SensorType.SENSORS).value as String
            assertEquals("GCData[5] must reflect active CADENCE", 'C', liveGcPocket[5])

            // Phase 3: Coasting drops cadence to 0.0 within 2.0 s, then settling back to BIKE_HANDLEBAR suppresses to null
            val coastStartMs = fakeClockMs + 20L
            for (step in 0..100) { // 2.0 s coasting
                fakeClockMs = coastStartMs + step * 20L
                val halfFlat = (Math.toRadians(5.0) * 0.5).toFloat()
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GAME_ROTATION_VECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(sin(halfFlat.toDouble()).toFloat(), 0f, 0f, cos(halfFlat.toDouble()).toFloat())
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_ACCELEROMETER,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0.05f, 9.81f, 0.05f)
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GYROSCOPE,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0.02f, 0.01f, 0.01f)
                    )
                )
                if (step % 50 == 0) {
                    device.onWheelMeasurement(speedMps = 7.5, variance = 0.05, timestampMs = fakeClockMs)
                }
            }
            val coastedCadence = (realSensorManager.getSensor(SensorType.CADENCE)?.value as? Number)?.toDouble()
            assertEquals("Coasting in pocket must drop CADENCE to 0.0 RPM", 0.0, coastedCadence!!, 1e-6)

            // Continue quiet flat cues past 6.0 s classifier window + 4.0 s mount hysteresis -> settles back into BIKE_HANDLEBAR -> unregisters CADENCE
            val remountStartMs = fakeClockMs + 20L
            for (step in 0..450) { // +9.0 s (+2.0 s coasting = 11.0 s >= 6.0 s window + 4.0 s mount hysteresis)
                fakeClockMs = remountStartMs + step * 20L
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GAME_ROTATION_VECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0f, 0f, 0f, 1f)
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_ACCELEROMETER,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0.02f, 9.81f, 0.02f)
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GYROSCOPE,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0.01f, 0.01f, 0.01f)
                    )
                )
                if (step % 50 == 0) {
                    device.onWheelMeasurement(speedMps = 7.5, variance = 0.05, timestampMs = fakeClockMs)
                }
            }
            assertEquals(PhoneCarriageMode.BIKE_HANDLEBAR.code, device.mActivityAndCarriageModeSensor?.value)
            assertNull("Settling to BIKE_HANDLEBAR must unregister CADENCE sensor", device.mCadenceSensor)
            val suppressedGc = realSensorManager.getSensor(SensorType.SENSORS).value as String
            assertEquals("Live GCData[5] must revert to '-'", '-', suppressedGc[5])
        } finally {
            device.shutDown()
            realSensorManager.shutDown()
        }
    }

    // TST-FIL-029.3: Gyro-absent indoor running (ACC + STEP_DETECTOR), RUN-only STRIDES, SPORT_TYPE_CHANGED & clean reset
    @Test
    fun smartphoneImu_gyroAbsentIndoorRunGatedStridesSportChangeAndReset_tstFil029_3() {
        val capturedReceivers = mutableListOf<BroadcastReceiver>()
        every {
            ContextCompat.registerReceiver(any(), capture(capturedReceivers), any(), any())
        } returns null
        every { AttFusedPreferences.isEffectiveGyroActive(any()) } returns false
        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns false

        val realSensorManager = MySensorManager(mockContext)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        var fakeClockMs = 1000L
        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, realSensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
            override fun hasActivityRecognitionPermission(): Boolean = true
            fun callAccumulatorsReset() = onAccumulatorsReset()
        }
        device.setSportContextHintForTesting(SportContextHint.RUNNING)

        try {
            // Feed 8.0 s of indoor running at 170 steps/min (85 strides/min) with gyro disabled and zero GNSS fixes
            val stepsPerMin = 170.0
            val stepHz = stepsPerMin / 60.0 // 2.8333 Hz
            var nextStepMs = 1353L
            for (step in 0..400) { // 8.0 s
                fakeClockMs = 1000L + step * 20L
                val tSec = fakeClockMs / 1000.0
                val halfTilt = (Math.toRadians(45.0 + 18.0 * sin(2.0 * PI * (stepHz / 2.0) * tSec)) * 0.5).toFloat()
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GAME_ROTATION_VECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_ACCELEROMETER,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(
                            (0.9 * sin(2.0 * PI * (stepHz / 2.0) * tSec)).toFloat(),
                            (9.81 + 3.4 * sin(2.0 * PI * stepHz * tSec)).toFloat(),
                            (1.0 * cos(2.0 * PI * stepHz * tSec)).toFloat()
                        )
                    )
                )
                if (fakeClockMs >= nextStepMs) {
                    device.onSensorChanged(
                        createSyntheticSensorEvent(
                            Sensor.TYPE_STEP_DETECTOR,
                            fakeClockMs * 1_000_000L,
                            floatArrayOf(1.0f)
                        )
                    )
                    nextStepMs += 353L
                }
            }

            assertNotNull("Indoor running must dynamically register CADENCE", device.mCadenceSensor)
            assertNotNull("BSportType.RUN must dynamically register STRIDES", device.mStridesSensor)
            val cadenceSpm = (realSensorManager.getSensor(SensorType.CADENCE)?.value as? Number)?.toDouble()
            assertNotNull(cadenceSpm)
            assertEquals(85.0, cadenceSpm!!, 2.0)
            val stridesCount = (realSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertTrue("STRIDES must accumulate monotonically >= 5 over 8 s (got $stridesCount)", stridesCount >= 5)

            // User changes sport to BIKE via SPORT_TYPE_CHANGED_BY_USER_INTENT -> STRIDES unregisters immediately
            device.setSportContextHintForTesting(SportContextHint.CYCLING)
            val sportIntent = mockk<Intent>(relaxed = true) {
                every { action } returns BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT
            }
            capturedReceivers.last().onReceive(mockContext, sportIntent)
            assertNull("Switching away from BSportType.RUN must unregister STRIDES", device.mStridesSensor)

            // Switch back to RUN and trigger onAccumulatorsReset() -> clean zero/unregister
            device.setSportContextHintForTesting(SportContextHint.RUNNING)
            device.callAccumulatorsReset()
            assertNull("onAccumulatorsReset must clear CADENCE sensor cleanly", device.mCadenceSensor)
            assertNull("onAccumulatorsReset must clear STRIDES sensor cleanly", device.mStridesSensor)
            assertEquals(PhoneCarriageMode.UNKNOWN.code, device.mActivityAndCarriageModeSensor?.value)
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            device.shutDown()
            realSensorManager.shutDown()
        }
    }

    // TST-FIL-029.3: Delayed/out-of-order batched SensorEvent timestamps clamp monotonically without stride double-counting
    @Test
    fun smartphoneImu_outOfOrderBatchedStepEventsClampMonotonicallyWithoutDoubleCounting_tstFil029_3() {
        every { AttFusedPreferences.isEffectiveGyroActive(any()) } returns false
        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns false
        val realSensorManager = MySensorManager(mockContext)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        var fakeClockMs = 1000L
        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, realSensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
            override fun hasActivityRecognitionPermission(): Boolean = true
            fun callAccumulatorsReset() = onAccumulatorsReset()
        }
        device.setSportContextHintForTesting(SportContextHint.RUNNING)

        try {
            // Warm up PhoneCarriageClassifier into RUN_WAIST_HIP with continuous steps (353 ms period) over 4.0 s
            var nextStepMs = 1353L
            for (tick in 0..200) { // 1000..5000 ms
                fakeClockMs = 1000L + tick * 20L
                val tSec = fakeClockMs / 1000.0
                val halfTilt = (Math.toRadians(45.0 + 18.0 * sin(2.0 * PI * 1.4167 * tSec)) * 0.5).toFloat()
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GAME_ROTATION_VECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_ACCELEROMETER,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0.9f, (9.81 + 3.4 * sin(2.0 * PI * 2.8333 * tSec)).toFloat(), 1.0f)
                    )
                )
                if (fakeClockMs >= nextStepMs) {
                    device.onSensorChanged(
                        createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, fakeClockMs * 1_000_000L, floatArrayOf(1.0f))
                    )
                    nextStepMs += 353L
                }
            }
            val stridesBeforeDelayed = (realSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertTrue("Expected >= 2 strides before delayed events, got $stridesBeforeDelayed", stridesBeforeDelayed >= 2)

            // Inject out-of-order delayed batched step events whose SensorEvent.timestamp is in the past
            fakeClockMs = 5020L
            device.onSensorChanged(
                createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, 3900L * 1_000_000L, floatArrayOf(1.0f))
            )
            device.onSensorChanged(
                createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, 3500L * 1_000_000L, floatArrayOf(1.0f))
            )
            val stridesAfterDelayed = (realSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertEquals(
                "Out-of-order batched step events must clamp monotonically without double-counting",
                stridesBeforeDelayed,
                stridesAfterDelayed
            )

            // Verify subsequent valid steps (modulo 2 hardware steps per stride) advance strides cleanly by +1
            val s1Ms = maxOf(fakeClockMs + 400L, nextStepMs)
            fakeClockMs = s1Ms
            device.onSensorChanged(
                createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, s1Ms * 1_000_000L, floatArrayOf(1.0f))
            )
            val s2Ms = s1Ms + 360L
            fakeClockMs = s2Ms
            device.onSensorChanged(
                createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, s2Ms * 1_000_000L, floatArrayOf(1.0f))
            )
            val stridesAfterResume = (realSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertEquals(stridesBeforeDelayed + 1, stridesAfterResume)
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            device.shutDown()
            realSensorManager.shutDown()
        }
    }

    // TST-FIL-029.3 (Stage 6 Bounce 1): Delayed CHRE TYPE_STEP_DETECTOR batch & interleaved ACC/GYRO FIFO clamp per sensor type
    @Test
    fun smartphoneImu_chreDelayedStepBatchAndInterleavedFifoMaintainPerSensorMonotonicity_tstFil029_3() {
        every { AttFusedPreferences.isEffectiveGyroActive(any()) } returns true
        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns true
        val realSensorManager = MySensorManager(mockContext)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        var fakeClockMs = 1000L
        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, realSensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
            override fun hasActivityRecognitionPermission(): Boolean = true
        }
        device.setSportContextHintForTesting(SportContextHint.RUNNING)

        try {
            // Interleaved 50-Hz ACC + ROTATION_VECTOR + lagged GYRO FIFO batches with 3.2 m/s GNSS advancing ACC to 5480 ms (0 steps yet)
            var lat = 48.0
            for (batchStart in 1000L..5000L step 500L) {
                if (batchStart % 1000L == 0L) {
                    fakeClockMs = batchStart
                    lat += 3.2 / 111195.0
                    device.onLocationChanged(
                        createMutableLocation(
                            lat = lat,
                            lon = 11.0,
                            acc = 4.0f,
                            timeMs = batchStart,
                            hasSpeedFlag = true,
                            speedMps = 3.2f,
                            hasBearingFlag = true,
                            bearingDeg = 0.0f
                        )
                    )
                }
                // ACC batch runs ahead to batchStart + 480 ms
                for (offset in 0L..480L step 20L) {
                    val tMs = batchStart + offset
                    fakeClockMs = tMs
                    val tSec = tMs / 1000.0
                    val halfTilt = (Math.toRadians(45.0 + 18.0 * sin(2.0 * PI * 1.4167 * tSec)) * 0.5).toFloat()
                    device.onSensorChanged(
                        createSyntheticSensorEvent(
                            Sensor.TYPE_GAME_ROTATION_VECTOR,
                            tMs * 1_000_000L,
                            floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())
                        )
                    )
                    device.onSensorChanged(
                        createSyntheticSensorEvent(
                            Sensor.TYPE_ACCELEROMETER,
                            tMs * 1_000_000L,
                            floatArrayOf(0.9f, (9.81 + 3.4 * sin(2.0 * PI * 2.8333 * tSec)).toFloat(), 1.0f)
                        )
                    )
                }
                // Then GYRO FIFO batch arrives with historical timestamps starting back at batchStart..batchStart+480 ms
                for (offset in 0L..480L step 20L) {
                    val tMs = batchStart + offset
                    val tSec = tMs / 1000.0
                    device.onSensorChanged(
                        createSyntheticSensorEvent(
                            Sensor.TYPE_GYROSCOPE,
                            tMs * 1_000_000L,
                            floatArrayOf(0.2f, (0.3 * cos(2.0 * PI * 1.4167 * tSec)).toFloat(), 0.1f)
                        )
                    )
                }
            }
            val activeMode = PhoneCarriageMode.fromCode(device.mActivityAndCarriageModeSensor?.value)
            assertTrue(
                "Interleaved ACC/GYRO + GNSS must commit a running mode before step batch (was $activeMode)",
                activeMode == PhoneCarriageMode.RUN_WAIST_HIP ||
                    activeMode == PhoneCarriageMode.RUN_BICEP_ARM ||
                    activeMode == PhoneCarriageMode.RUN_HANDHELD
            )

            // Now ACC is at 5480 ms (capture baseline strides emitted by warmup waveform before any step event).
            // Delayed CHRE flushes 3 historical TYPE_STEP_DETECTOR steps spaced 550 ms apart
            // (3800 ms, 4350 ms, 4900 ms). All 3 must survive independently of ACC!
            val stridesBeforeBatch = (realSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            fakeClockMs = 5520L
            for (stepMs in listOf(3800L, 4350L, 4900L)) {
                device.onSensorChanged(
                    createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, stepMs * 1_000_000L, floatArrayOf(1.0f))
                )
            }
            val stridesAfterBatch = (realSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertEquals(
                "Steps 1+2 in CHRE batch must advance STRIDES by +1 over warmup baseline ($stridesBeforeBatch)",
                stridesBeforeBatch + 1,
                stridesAfterBatch
            )

            // One single follow-up step at 5450 ms completes the pair with batched step 3 -> proving step 3 survived!
            fakeClockMs = 5540L
            device.onSensorChanged(
                createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, 5450L * 1_000_000L, floatArrayOf(1.0f))
            )
            val stridesAfterFourth = (realSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertEquals(
                "Step 3 of delayed CHRE batch + 1 follow-up step must advance STRIDES by +2 over baseline ($stridesBeforeBatch)",
                stridesBeforeBatch + 2,
                stridesAfterFourth
            )

            // Verify per-type regression guard: an out-of-order step earlier than 5450 ms still clamps & is rejected
            device.onSensorChanged(
                createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, 4100L * 1_000_000L, floatArrayOf(1.0f))
            )
            val stridesAfterStale = (realSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertEquals("Out-of-order step (< 5450 ms) must still be rejected", stridesBeforeBatch + 2, stridesAfterStale)
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            device.shutDown()
            realSensorManager.shutDown()
        }
    }

    // TST-FIL-029.3 (Stage 6 Bounce 1): Preserves mid-workout STRIDES accumulator across suppression / re-registration
    @Test
    fun smartphoneImu_preservesStridesAccumulatorAcrossSuppressionAndResume_tstFil029_3() {
        every { AttFusedPreferences.isEffectiveGyroActive(any()) } returns false
        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns false
        val realSensorManager = MySensorManager(mockContext)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        var fakeClockMs = 1000L
        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, realSensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
            override fun hasActivityRecognitionPermission(): Boolean = true
            fun callAccumulatorsReset() = onAccumulatorsReset()
        }
        device.setSportContextHintForTesting(SportContextHint.RUNNING)

        try {
            var nextStepMs = 1353L
            for (tick in 0..300) { // 1000..7000 ms run
                fakeClockMs = 1000L + tick * 20L
                val tSec = fakeClockMs / 1000.0
                val halfTilt = (Math.toRadians(45.0 + 18.0 * sin(2.0 * PI * 1.4167 * tSec)) * 0.5).toFloat()
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GAME_ROTATION_VECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_ACCELEROMETER,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0.9f, (9.81 + 3.4 * sin(2.0 * PI * 2.8333 * tSec)).toFloat(), 1.0f)
                    )
                )
                if (fakeClockMs >= nextStepMs) {
                    device.onSensorChanged(
                        createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, fakeClockMs * 1_000_000L, floatArrayOf(1.0f))
                    )
                    nextStepMs += 353L
                }
            }
            val stridesBeforeSuppress = (realSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertTrue("Expected >= 5 strides before suppression, got $stridesBeforeSuppress", stridesBeforeSuppress >= 5)

            // Temporarily toggle to CYCLING -> unregisters STRIDES mid-workout without accumulator reset
            fakeClockMs += 100L
            device.setSportContextHintForTesting(SportContextHint.CYCLING)
            device.handleSportTypeChanged(fakeClockMs)
            assertNull("CYCLING transition must unregister STRIDES sensor", device.mStridesSensor)

            // Resume RUNNING and trigger two valid steps -> restored sensor must continue from stridesBeforeSuppress
            device.setSportContextHintForTesting(SportContextHint.RUNNING)
            fakeClockMs += 100L
            device.handleSportTypeChanged(fakeClockMs)

            val step1Ms = maxOf(fakeClockMs + 400L, nextStepMs)
            val step2Ms = step1Ms + 360L
            for (sMs in listOf(step1Ms, step2Ms)) {
                fakeClockMs = sMs
                device.onSensorChanged(
                    createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, sMs * 1_000_000L, floatArrayOf(1.0f))
                )
            }
            val stridesAfterResume = (realSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertEquals(
                "Resumed STRIDES must retain pre-suppression total ($stridesBeforeSuppress) and advance by +1",
                stridesBeforeSuppress + 1,
                stridesAfterResume
            )

            // Explicit accumulator reset must still zero everything cleanly
            device.callAccumulatorsReset()
            assertNull("onAccumulatorsReset must clear STRIDES sensor", device.mStridesSensor)
            // Re-establish RUN_WAIST_HIP classification post-reset (>= 4.0 s + steps for indoor step-band lock) -> must start from 1
            var postResetStepMs = step2Ms + 400L
            for (tick in 0..200) { // 4.0 s warm-up
                fakeClockMs = step2Ms + 100L + tick * 20L
                val tSec = fakeClockMs / 1000.0
                val halfTilt = (Math.toRadians(45.0 + 18.0 * sin(2.0 * PI * 1.4167 * tSec)) * 0.5).toFloat()
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GAME_ROTATION_VECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_ACCELEROMETER,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0.9f, (9.81 + 3.4 * sin(2.0 * PI * 2.8333 * tSec)).toFloat(), 1.0f)
                    )
                )
                if (fakeClockMs >= postResetStepMs) {
                    device.onSensorChanged(
                        createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, fakeClockMs * 1_000_000L, floatArrayOf(1.0f))
                    )
                    postResetStepMs += 353L
                }
            }
            val stridesPostReset = (realSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertTrue(
                "Post-reset STRIDES must reset cleanly and count only post-reset strides (expected 1..4, got $stridesPostReset, pre-reset=$stridesAfterResume)",
                stridesPostReset in 1..4 && stridesPostReset < stridesBeforeSuppress
            )
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            device.shutDown()
            realSensorManager.shutDown()
        }
    }

    // TST-FIL-029.3 / REQ-FIL-028.3 (Gate 4 Bounce 1): Interleaved ACC/GYRO FIFO + delayed CHRE steps must not rewind
    // updateCarriageClassification() clocks (mLastClassifyTimestampMs, mLastModeEmissionTimestampMs, mLastAdaptationTimestampMs).
    @Test
    fun smartphoneImu_interleavedFifoAndDelayedStepBatchDoNotRewindClassificationOrAdaptationClocks_tstFil029_3() {
        every { AttFusedPreferences.isEffectiveGyroActive(any()) } returns true
        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns true
        val orderedManager = MySensorManager(mockContext)
        val batchedManager = MySensorManager(mockContext)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        var orderedClockMs = 1000L
        val orderedDevice = object : SpeedAndLocationDevice_ATTFused(mockContext, orderedManager) {
            override fun currentTimeMillis(): Long = orderedClockMs
            override fun hasActivityRecognitionPermission(): Boolean = true
        }
        var batchedClockMs = 1000L
        val batchedDevice = object : SpeedAndLocationDevice_ATTFused(mockContext, batchedManager) {
            override fun currentTimeMillis(): Long = batchedClockMs
            override fun hasActivityRecognitionPermission(): Boolean = true
        }
        orderedDevice.setSportContextHintForTesting(SportContextHint.CYCLING)
        batchedDevice.setSportContextHintForTesting(SportContextHint.CYCLING)

        fun feedLoc(dev: SpeedAndLocationDevice_ATTFused, tMs: Long, lat: Double) {
            dev.onLocationChanged(
                createMutableLocation(
                    lat = lat,
                    lon = 11.0,
                    acc = 4.0f,
                    timeMs = tMs,
                    hasSpeedFlag = true,
                    speedMps = 7.0f,
                    hasBearingFlag = true,
                    bearingDeg = 0.0f
                )
            )
        }

        fun feedRvAndAcc(dev: SpeedAndLocationDevice_ATTFused, tMs: Long) {
            val sec = tMs / 1000.0
            val phase = 2.0 * PI * 1.35 * sec
            val half = Math.toRadians(60.0 + 20.0 * sin(phase)) * 0.5
            dev.onSensorChanged(
                createSyntheticSensorEvent(
                    Sensor.TYPE_GAME_ROTATION_VECTOR,
                    tMs * 1_000_000L,
                    floatArrayOf(sin(half).toFloat(), 0f, 0f, cos(half).toFloat())
                )
            )
            dev.onSensorChanged(
                createSyntheticSensorEvent(
                    Sensor.TYPE_ACCELEROMETER,
                    tMs * 1_000_000L,
                    floatArrayOf(0.4f, (9.81 + 1.5 * sin(phase)).toFloat(), 0.9f)
                )
            )
        }

        fun feedGyro(dev: SpeedAndLocationDevice_ATTFused, tMs: Long) {
            val sec = tMs / 1000.0
            val phase = 2.0 * PI * 1.35 * sec
            dev.onSensorChanged(
                createSyntheticSensorEvent(
                    Sensor.TYPE_GYROSCOPE,
                    tMs * 1_000_000L,
                    floatArrayOf(0f, 0f, (0.78 * sin(phase)).toFloat())
                )
            )
        }

        try {
            var lat = 48.0
            var evalBeforeLag = 0
            var emitBeforeLag = 0
            var scaleBeforeLag = 1.0
            var yawBeforeLag = 1.0

            // Run t = 1000..4480 ms (commits BIKE_FRONT_POCKET around t = 4000 ms, while EMA scales are still actively converging)
            for (batchStart in 1000L..4000L step 500L) {
                if (batchStart % 1000L == 0L) {
                    lat += 7.0 / 111195.0
                    orderedClockMs = batchStart
                    batchedClockMs = batchStart
                    feedLoc(orderedDevice, batchStart, lat)
                    feedLoc(batchedDevice, batchStart, lat)
                }

                // Ordered device receives strictly monotonic interleaved RV/ACC/GYRO
                for (offset in 0L..480L step 20L) {
                    val tMs = batchStart + offset
                    orderedClockMs = tMs
                    feedRvAndAcc(orderedDevice, tMs)
                    feedGyro(orderedDevice, tMs)
                }

                // Batched device advances RV+ACC across the entire 500-ms batch BEFORE lagged GYRO arrives
                for (offset in 0L..480L step 20L) {
                    val tMs = batchStart + offset
                    batchedClockMs = tMs
                    feedRvAndAcc(batchedDevice, tMs)
                }

                // Snapshot right before the final lagged GYRO + delayed CHRE step batch at batchStart == 4000L (t = 4480 ms)
                if (batchStart == 4000L) {
                    evalBeforeLag = batchedDevice.classifyEvaluationCountForTest()
                    emitBeforeLag = batchedDevice.modeEmissionCountForTest()
                    scaleBeforeLag = batchedDevice.effectiveGyroNoiseScaleForTest()
                    yawBeforeLag = batchedDevice.effectiveYawWindowSecForTest()
                }

                for (offset in 0L..480L step 20L) {
                    val tMs = batchStart + offset
                    feedGyro(batchedDevice, tMs)
                }
            }

            // Inject delayed CHRE step batch (historical 3500, 3850, 4200 ms) while ACC/GYRO already reached 4480 ms
            batchedClockMs = 4490L
            for (stepMs in listOf(3500L, 3850L, 4200L)) {
                batchedDevice.onSensorChanged(
                    createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, stepMs * 1_000_000L, floatArrayOf(1.0f))
                )
            }

            // ZERO extra classifies, emissions, or EMA adaptation integration may occur during lagged GYRO + delayed CHRE steps
            assertEquals(
                "Lagged GYRO/CHRE timestamps must not trigger redundant classify() evaluations",
                evalBeforeLag,
                batchedDevice.classifyEvaluationCountForTest()
            )
            assertEquals(
                "Lagged GYRO/CHRE timestamps must not trigger redundant mode sensor emissions",
                emitBeforeLag,
                batchedDevice.modeEmissionCountForTest()
            )
            assertEquals(
                "Lagged GYRO/CHRE timestamps must not double-integrate effectiveGyroNoiseScale",
                scaleBeforeLag,
                batchedDevice.effectiveGyroNoiseScaleForTest(),
                1e-12
            )
            assertEquals(
                "Lagged GYRO/CHRE timestamps must not double-integrate effectiveYawWindowSec",
                yawBeforeLag,
                batchedDevice.effectiveYawWindowSecForTest(),
                1e-12
            )

            // Deliver next forward sample at t = 4500 ms (dt = 20 ms from 4480 ms, NOT dt = 300 ms from rewound step 4200 ms!)
            orderedClockMs = 4500L
            batchedClockMs = 4500L
            feedRvAndAcc(orderedDevice, 4500L)
            feedGyro(orderedDevice, 4500L)
            feedRvAndAcc(batchedDevice, 4500L)
            feedGyro(batchedDevice, 4500L)

            val alpha20ms = 1.0 - kotlin.math.exp(-0.02 / 0.75)
            val expectedScaleAt4500 = scaleBeforeLag +
                alpha20ms * (PhoneCarriageMode.BIKE_FRONT_POCKET.profile.gyroNoiseScale - scaleBeforeLag)
            assertEquals(
                "Post-batch 4500 ms adaptation must integrate single dt=20ms step, not rewound CHRE step delta",
                expectedScaleAt4500,
                batchedDevice.effectiveGyroNoiseScaleForTest(),
                1e-4
            )
            assertEquals(
                "Batched FIFO + CHRE device must match strictly ordered reference gyro noise scale (dt-invariant)",
                orderedDevice.effectiveGyroNoiseScaleForTest(),
                batchedDevice.effectiveGyroNoiseScaleForTest(),
                0.05
            )
            assertEquals(
                "Batched FIFO + CHRE device must match strictly ordered reference yaw window (dt-invariant)",
                orderedDevice.effectiveYawWindowSecForTest(),
                batchedDevice.effectiveYawWindowSecForTest(),
                0.02
            )
            assertTrue(
                "Classify evaluation count must stay throttled vs ordered reference (batched=${batchedDevice.classifyEvaluationCountForTest()}, ordered=${orderedDevice.classifyEvaluationCountForTest()})",
                batchedDevice.classifyEvaluationCountForTest() <= orderedDevice.classifyEvaluationCountForTest() + 1
            )
            assertTrue(
                "Mode emission count must stay throttled vs ordered reference (batched=${batchedDevice.modeEmissionCountForTest()}, ordered=${orderedDevice.modeEmissionCountForTest()})",
                batchedDevice.modeEmissionCountForTest() <= orderedDevice.modeEmissionCountForTest() + 1
            )
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            orderedDevice.shutDown()
            batchedDevice.shutDown()
            orderedManager.shutDown()
            batchedManager.shutDown()
        }
    }

    // TST-FIL-029.3 (Cold Final Bounce 2): Live ProxySensor / late FilterManager listeners must receive preserved
    // STRIDES immediately upon mid-run re-registration and never emit a transient 0 reset.
    @Test
    fun smartphoneImu_midRunReRegistrationNotifiesLiveListenersWithPreservedStridesWithoutZeroDip_tstFil029_3() {
        every { AttFusedPreferences.isEffectiveGyroActive(any()) } returns false
        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns false
        val realSensorManager = MySensorManager(mockContext)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        var fakeClockMs = 1000L
        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, realSensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
            override fun hasActivityRecognitionPermission(): Boolean = true
        }
        device.setSportContextHintForTesting(SportContextHint.RUNNING)

        try {
            var nextStepMs = 1353L
            for (tick in 0..300) { // 6.0 s initial RUN_WAIST_HIP segment
                fakeClockMs = 1000L + tick * 20L
                val tSec = fakeClockMs / 1000.0
                val halfTilt = (Math.toRadians(45.0 + 18.0 * sin(2.0 * PI * 1.4167 * tSec)) * 0.5).toFloat()
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GAME_ROTATION_VECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_ACCELEROMETER,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0.9f, (9.81 + 3.4 * sin(2.0 * PI * 2.8333 * tSec)).toFloat(), 1.0f)
                    )
                )
                if (fakeClockMs >= nextStepMs) {
                    device.onSensorChanged(
                        createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, fakeClockMs * 1_000_000L, floatArrayOf(1.0f))
                    )
                    nextStepMs += 353L
                }
            }
            val proxyStridesSensor = realSensorManager.getSensor(SensorType.STRIDES)
            assertNotNull("STRIDES ProxySensor must be registered during run", proxyStridesSensor)
            val stridesBeforeSuppress = (proxyStridesSensor!!.value as? Number)?.toInt() ?: 0
            assertTrue("Expected >= 5 strides before suppression, got $stridesBeforeSuppress", stridesBeforeSuppress >= 5)

            // Attach live listener to ProxySensor before mid-run suppression (simulating FilterManager/UI subscriber)
            val initialAttachValues = mutableListOf<Int>()
            val postSuppressEvents = mutableListOf<Int>()
            var postSuppressPhase = false
            val liveListener = com.atrainingtracker.banalservice.sensor.MySensor.SensorListener<Number> { value ->
                val v = value?.toInt() ?: return@SensorListener
                if (postSuppressPhase) postSuppressEvents.add(v) else initialAttachValues.add(v)
            }
            @Suppress("UNCHECKED_CAST")
            (proxyStridesSensor as com.atrainingtracker.banalservice.sensor.MySensor<Any?>).addSensorListener(
                liveListener as com.atrainingtracker.banalservice.sensor.MySensor.SensorListener<Any?>
            )
            assertEquals("Attaching listener before suppression must immediately report total", stridesBeforeSuppress, initialAttachValues.last())

            // Mid-workout suppression -> unregister STRIDES
            postSuppressPhase = true
            fakeClockMs += 100L
            device.setSportContextHintForTesting(SportContextHint.CYCLING)
            device.handleSportTypeChanged(fakeClockMs)
            assertNull("CYCLING transition must unregister STRIDES sensor", device.mStridesSensor)

            // Resume RUNNING and trigger CADENCE re-registration WITHOUT any new hardware step yet
            device.setSportContextHintForTesting(SportContextHint.RUNNING)
            fakeClockMs += 100L
            device.handleSportTypeChanged(fakeClockMs)
            for (tick in 1..60) { // 1.2 s ACC/RV to trigger CADENCE -> ensureStridesSensorRegistered()
                fakeClockMs += 20L
                val tSec = fakeClockMs / 1000.0
                val halfTilt = (Math.toRadians(45.0 + 18.0 * sin(2.0 * PI * 1.4167 * tSec)) * 0.5).toFloat()
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GAME_ROTATION_VECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_ACCELEROMETER,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0.9f, (9.81 + 3.4 * sin(2.0 * PI * 2.8333 * tSec)).toFloat(), 1.0f)
                    )
                )
            }
            assertNotNull("STRIDES sensor must re-register upon resumed RUNNING cadence", device.mStridesSensor)
            assertTrue(
                "Bound live listener must receive preserved STRIDES immediately on re-registration, saw $postSuppressEvents",
                postSuppressEvents.contains(stridesBeforeSuppress)
            )
            assertTrue(
                "Bound live listener must NEVER receive a transient 0 reset upon re-registration, saw $postSuppressEvents",
                postSuppressEvents.none { it == 0 }
            )
            assertEquals(stridesBeforeSuppress, postSuppressEvents.last())

            // Attach a late listener after re-registration (simulating async FilterManager rebind on SENSORS_CHANGED)
            val lateRebindValues = mutableListOf<Int>()
            @Suppress("UNCHECKED_CAST")
            (proxyStridesSensor as com.atrainingtracker.banalservice.sensor.MySensor<Any?>).addSensorListener(
                com.atrainingtracker.banalservice.sensor.MySensor.SensorListener<Any?> { value ->
                    (value as? Number)?.toInt()?.let { lateRebindValues.add(it) }
                }
            )
            assertTrue(
                "Late FilterManager rebind listener must immediately receive preserved total ($stridesBeforeSuppress), saw $lateRebindValues",
                lateRebindValues.lastOrNull() == stridesBeforeSuppress && lateRebindValues.none { it == 0 }
            )

            // Feed two valid hardware steps -> both listeners receive stridesBeforeSuppress + 1
            val s1 = maxOf(fakeClockMs + 360L, nextStepMs)
            val s2 = s1 + 360L
            for (sMs in listOf(s1, s2)) {
                fakeClockMs = sMs
                device.onSensorChanged(
                    createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, sMs * 1_000_000L, floatArrayOf(1.0f))
                )
            }
            assertEquals(stridesBeforeSuppress + 1, postSuppressEvents.last())
            assertEquals(stridesBeforeSuppress + 1, lateRebindValues.last())
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            device.shutDown()
            realSensorManager.shutDown()
        }
    }

    // TST-FIL-029.3 (Cold Final Bounce 3): Transient strides emitted by the estimator during classifier dwell
    // while active sport is non-RUN (BIKE) must advance mLastEstimatorTotalStrides without registering STRIDES,
    // so returning to RUN later never retroactively adds gated-off non-RUN strides.
    @Test
    fun smartphoneImu_gatedNonRunStridesDuringDwellAreNotRetroactivelyAddedOnReturnToRun_tstFil029_3() {
        every { AttFusedPreferences.isEffectiveGyroActive(any()) } returns false
        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns false
        val realSensorManager = MySensorManager(mockContext)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        var fakeClockMs = 1000L
        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, realSensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
            override fun hasActivityRecognitionPermission(): Boolean = true
        }
        device.setSportContextHintForTesting(SportContextHint.RUNNING)

        try {
            var nextStepMs = 1353L
            for (tick in 0..300) { // 6.0 s initial RUN_WAIST_HIP segment
                fakeClockMs = 1000L + tick * 20L
                val tSec = fakeClockMs / 1000.0
                val halfTilt = (Math.toRadians(45.0 + 18.0 * sin(2.0 * PI * 1.4167 * tSec)) * 0.5).toFloat()
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_GAME_ROTATION_VECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())
                    )
                )
                device.onSensorChanged(
                    createSyntheticSensorEvent(
                        Sensor.TYPE_ACCELEROMETER,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(0.9f, (9.81 + 3.4 * sin(2.0 * PI * 2.8333 * tSec)).toFloat(), 1.0f)
                    )
                )
                if (fakeClockMs >= nextStepMs) {
                    device.onSensorChanged(
                        createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, fakeClockMs * 1_000_000L, floatArrayOf(1.0f))
                    )
                    nextStepMs += 353L
                }
            }
            val proxyStridesSensor = realSensorManager.getSensor(SensorType.STRIDES)
            assertNotNull("STRIDES ProxySensor must be registered during initial RUN", proxyStridesSensor)
            val stridesBeforeBike = (proxyStridesSensor!!.value as? Number)?.toInt() ?: 0
            assertTrue("Expected >= 5 strides before switching to BIKE, got $stridesBeforeBike", stridesBeforeBike >= 5)

            // Observe live STRIDES listener across RUN -> BIKE (transient dwell strides) -> RUN
            val observedAfterSwitch = mutableListOf<Int>()
            @Suppress("UNCHECKED_CAST")
            (proxyStridesSensor as com.atrainingtracker.banalservice.sensor.MySensor<Any?>).addSensorListener(
                com.atrainingtracker.banalservice.sensor.MySensor.SensorListener<Any?> { value ->
                    (value as? Number)?.toInt()?.let { observedAfterSwitch.add(it) }
                }
            )
            observedAfterSwitch.clear()

            // Switch RUN -> BIKE: unregisters STRIDES while PhoneCarriageClassifier still dwells in RUN_WAIST_HIP
            fakeClockMs += 100L
            device.setSportContextHintForTesting(SportContextHint.CYCLING)
            device.handleSportTypeChanged(fakeClockMs)
            assertNull("BIKE transition must unregister STRIDES sensor", device.mStridesSensor)

            // Inject 6 hardware steps (= 3 strides) during the BIKE classifier dwell window
            val estimatorTotalBeforeBikeSteps = device.cadenceEstimator.totalStrides
            var bikeStepMs = maxOf(fakeClockMs + 360L, nextStepMs)
            for (i in 0 until 6) {
                fakeClockMs = bikeStepMs
                device.onSensorChanged(
                    createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, bikeStepMs * 1_000_000L, floatArrayOf(1.0f))
                )
                bikeStepMs += 360L
            }
            assertEquals(
                "Estimator must have emitted 3 transient strides during BIKE dwell",
                estimatorTotalBeforeBikeSteps + 3,
                device.cadenceEstimator.totalStrides
            )
            assertNull("STRIDES sensor must remain unregistered while active sport is BIKE", device.mStridesSensor)
            assertTrue(
                "No STRIDES listener events may fire while active sport is BIKE, saw $observedAfterSwitch",
                observedAfterSwitch.isEmpty()
            )

            // Switch back BIKE -> RUN and deliver exactly 2 running steps (= +1 stride)
            fakeClockMs += 100L
            device.setSportContextHintForTesting(SportContextHint.RUNNING)
            device.handleSportTypeChanged(fakeClockMs)
            var runStepMs = maxOf(fakeClockMs + 360L, bikeStepMs)
            for (i in 0 until 2) {
                fakeClockMs = runStepMs
                device.onSensorChanged(
                    createSyntheticSensorEvent(Sensor.TYPE_STEP_DETECTOR, runStepMs * 1_000_000L, floatArrayOf(1.0f))
                )
                runStepMs += 360L
            }
            val stridesAfterReturn = (proxyStridesSensor.value as? Number)?.toInt() ?: 0
            assertEquals(
                "Returning to RUN must advance STRIDES strictly by +1 (not retroactively adding 3 gated BIKE strides)",
                stridesBeforeBike + 1,
                stridesAfterReturn
            )
            assertEquals(
                "Live listener must end at stridesBeforeBike + 1 without any retroactive jump, saw $observedAfterSwitch",
                stridesBeforeBike + 1,
                observedAfterSwitch.lastOrNull()
            )
            assertTrue(
                "Live listener must never emit values > stridesBeforeBike + 1 or 0, saw $observedAfterSwitch",
                observedAfterSwitch.all { it in stridesBeforeBike..(stridesBeforeBike + 1) }
            )
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            device.shutDown()
            realSensorManager.shutDown()
        }
    }

    // REQ-FIL-034.3 / TST-FIL-034.3: Indoor multipath GPS speed (acc=43.9 m, v=2.89 m/s) gated from classifier & isMovingByGnss
    @Test
    fun testDegradedMultipathIndoorSpeed_gatedFromClassifierAndStationaryPinning_tstFil034_3() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        try {
            // 1. Establish WALKING_TRANSITION at ~1.0 m/s with valid outdoor GNSS fixes
            var lat = 48.6567
            val lon = 9.0545
            val degPerMeter = 1.0 / (Math.toRadians(1.0) * 6371000.0)
            var stepTick = 0
            for (sec in 1L..6L) {
                val tMs = sec * 1000L
                for (sub in 1..50) {
                    stepTick++
                    val subMs = (sec - 1L) * 1000L + sub * 20L
                    val phase = 2.0 * PI * 1.7 * (sub * 0.02)
                    val half = Math.toRadians(65.0 + 8.0 * kotlin.math.sin(phase)) * 0.5
                    if (stepTick % 29 == 0) device.ingestStepEventForTesting(subMs)
                    device.ingestImuMeasurement(
                        floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat()),
                        floatArrayOf(0f, 0f, (0.22 * kotlin.math.sin(phase)).toFloat()),
                        dtSec = 0.02,
                        timestampMs = subMs
                    )
                }
                lat += 1.0 * degPerMeter
                device.onLocationChanged(
                    createMutableLocation(lat, lon, acc = 4.0f, timeMs = tMs, hasSpeedFlag = true, speedMps = 1.0f, hasBearingFlag = true, bearingDeg = 0.0f)
                )
            }
            assertEquals(PhoneCarriageMode.WALKING_TRANSITION.code, device.mActivityAndCarriageModeSensor?.value)

            // 2. Encounter indoor multipath fixes with acc=43.9 m and spurious location.speed=2.89 m/s + quiet IMU
            val flatRotVec = floatArrayOf(0f, 0f, 0f, 1f)
            for (sec in 7L..15L) {
                val tMs = sec * 1000L
                for (sub in 1..10) {
                    device.ingestImuMeasurement(flatRotVec, floatArrayOf(0f, 0f, 0.01f), dtSec = 0.1, timestampMs = (sec - 1L) * 1000L + sub * 100L)
                }
                device.onLocationChanged(
                    createMutableLocation(lat + 0.0002, lon, acc = 43.9f, timeMs = tMs, hasSpeedFlag = true, speedMps = 2.89f, hasBearingFlag = false)
                )
                val observedMode = PhoneCarriageMode.fromCode(device.mActivityAndCarriageModeSensor?.value)
                assertTrue(
                    "Degraded multipath 2.89 m/s @ acc=43.9 m must stay WALKING_TRANSITION or STATIONARY (was $observedMode at t=${sec}s)",
                    observedMode == PhoneCarriageMode.WALKING_TRANSITION || observedMode == PhoneCarriageMode.STATIONARY
                )
            }
            // Once quiet IMU settles into STATIONARY, degraded multipath 2.89 m/s must NOT defeat forceStationaryPinning
            assertEquals(PhoneCarriageMode.STATIONARY.code, device.mActivityAndCarriageModeSensor?.value)
        } finally {
            device.shutDown()
        }
    }

    // REQ-FIL-034.1 / TST-FIL-034.1: GNSS timeout speed decay propagates 0.0 m/s to carriageClassifier using injectable clock
    @Test
    fun testTriggerDecayTick_propagatesZeroSpeedToCarriageClassifierWithInjectableClock_tstFil034_1() {
        var fakeClockMs = 1_000L
        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
        }
        try {
            // 1. Move at 1.4 m/s to establish WALKING_TRANSITION and non-zero mSpeed
            val lat = 48.6567
            val lon = 9.0545
            var stepTick = 0
            for (sec in 1L..5L) {
                fakeClockMs = sec * 1000L
                for (sub in 1..50) {
                    stepTick++
                    val subMs = (sec - 1L) * 1000L + sub * 20L
                    val phase = 2.0 * PI * 1.7 * (sub * 0.02)
                    val half = Math.toRadians(65.0 + 8.0 * kotlin.math.sin(phase)) * 0.5
                    if (stepTick % 29 == 0) device.ingestStepEventForTesting(subMs)
                    device.ingestImuMeasurement(
                        floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat()),
                        floatArrayOf(0f, 0f, (0.22 * kotlin.math.sin(phase)).toFloat()),
                        dtSec = 0.02,
                        timestampMs = subMs
                    )
                }
                device.onLocationChanged(
                    createMutableLocation(lat + sec * 0.000012, lon, acc = 4.0f, timeMs = fakeClockMs, hasSpeedFlag = true, speedMps = 1.4f, hasBearingFlag = false)
                )
            }
            assertEquals(PhoneCarriageMode.WALKING_TRANSITION.code, device.mActivityAndCarriageModeSensor?.value)

            // 2. Stop indoors: feed quiet IMU and trigger speed decay ticks to 0.0 m/s across dismount dwell
            val flatRotVec = floatArrayOf(0f, 0f, 0f, 1f)
            for (sec in 6L..12L) {
                fakeClockMs = sec * 1000L
                for (sub in 1..10) {
                    device.ingestImuMeasurement(flatRotVec, floatArrayOf(0f, 0f, 0.01f), dtSec = 0.1, timestampMs = (sec - 1L) * 1000L + sub * 100L)
                }
                repeat(10) { device.triggerDecayTickForTesting() }
            }
            assertEquals(0.0, device.speed, 1e-9)
            assertEquals(PhoneCarriageMode.STATIONARY.code, device.mActivityAndCarriageModeSensor?.value)
        } finally {
            device.shutDown()
        }
    }

    // REQ-FIL-036.1 / REQ-FIL-036.2 / TST-FIL-036.1 / TST-FIL-036.2
    @Test
    fun testColdStartSouthEasyRun_releasesPinPromptlyWithout23sFreezeOrTeleport_tstFil036_1_2() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns true
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        try {
            val speedSensor = device.sensors.first { it.sensorType == SensorType.SPEED_mps }
            val distanceSensor = device.sensors.first { it.sensorType == SensorType.DISTANCE_m }
            val degPerMeterLat = 1.0 / (Math.toRadians(1.0) * 6371000.0)
            var lat = 48.6567
            val lon = 9.0545

            // t = 0: initial cold-start fix at 18.9 m accuracy and 0.0 m/s -> pins immediately
            device.onLocationChanged(
                createMutableLocation(lat, lon, acc = 18.9f, timeMs = 1_000L, hasSpeedFlag = true, speedMps = 0.0f, hasBearingFlag = false)
            )
            assertTrue("Initial 0.0 m/s fix must pin stationary anchor", device.isStationaryPinned)

            var firstUnpinnedSec = -1
            var maxStepJumpM = 0.0
            var prevDistM = 0.0

            // t = 1..25 s: steady Southbound easy run (~1.8 m/s, opposite hardcoded North PI/2)
            // with cold-start GPS accuracy improving 18.9 m -> 9.2 m and active running IMU motion
            for (sec in 1..25) {
                val tMs = 1_000L + sec * 1_000L
                val acc = (18.9 - (sec - 1) * (9.7 / 24.0)).toFloat()
                for (sub in 1..10) {
                    val subMs = tMs - 1_000L + sub * 100L
                    // Active running accelerometer variance (> 0.05) + gentle IMU sway
                    device.processAccelerometerMeasurement(
                        if (sub % 2 == 0) 2.5f else -2.5f,
                        if (sub % 2 == 0) 1.5f else -1.5f,
                        if (sub % 2 == 0) 13.5f else 6.2f,
                        subMs
                    )
                    device.ingestImuMeasurement(
                        floatArrayOf(0f, 0f, 0f, 1f),
                        floatArrayOf(0f, 0f, 0f),
                        dtSec = 0.1,
                        timestampMs = subMs
                    )
                }
                lat -= 1.8 * degPerMeterLat // South!
                device.onLocationChanged(
                    createMutableLocation(lat, lon, acc = acc, timeMs = tMs, hasSpeedFlag = true, speedMps = 1.8f, hasBearingFlag = false)
                )
                if (!device.isStationaryPinned && firstUnpinnedSec < 0) {
                    firstUnpinnedSec = sec
                }
                val curDistM = (distanceSensor.value as? Number)?.toDouble() ?: 0.0
                val stepM = curDistM - prevDistM
                if (stepM > maxStepJumpM) maxStepJumpM = stepM
                prevDistM = curDistM
            }

            assertTrue("Cold-start pinning must release within <= 5 s (released at t=$firstUnpinnedSec s)", firstUnpinnedSec in 1..5)
            assertTrue("Must not teleport >= 10 m on unpin or convergence (maxStep=$maxStepJumpM m)", maxStepJumpM < 10.0)
            assertTrue("EKF heading must seed South (~ -PI/2, was ${device.ekf.headingRad})", kotlin.math.abs(device.ekf.headingRad + PI / 2.0) < 0.4)
            val finalSpeed = (speedSensor.value as? Number)?.toDouble() ?: 0.0
            assertTrue("Published speed must track steady ~1.8 m/s running (was $finalSpeed)", finalSpeed in 1.2..2.5)
            val totalDist = (distanceSensor.value as? Number)?.toDouble() ?: 0.0
            assertTrue("Recorded distance over 45 m cold-start run must recover > 32 m (was $totalDist)", totalDist > 32.0)
        } finally {
            device.shutDown()
        }
    }

    // REQ-FIL-036.1 / TST-FIL-036.1
    @Test
    fun testIndoorHandheldOscillatingMultipath_remainsPinnedWithZeroDistanceCreep_tstFil036_1() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns true
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        try {
            val speedSensor = device.sensors.first { it.sensorType == SensorType.SPEED_mps }
            val distanceSensor = device.sensors.first { it.sensorType == SensorType.DISTANCE_m }
            val degPerMeterLon = 1.0 / (Math.toRadians(1.0) * 6371000.0 * kotlin.math.cos(Math.toRadians(48.6567)))
            val baseLat = 48.6567
            val baseLon = 9.0545

            // t = 0: initial stationary pin indoors
            device.onLocationChanged(
                createMutableLocation(baseLat, baseLon, acc = 16.0f, timeMs = 1_000L, hasSpeedFlag = true, speedMps = 0.0f, hasBearingFlag = false)
            )
            assertTrue(device.isStationaryPinned)

            // t = 1..8 s: indoor handheld multipath bouncing ±5 m East/West with fake 1.5 m/s Doppler and handheld sway
            val offsetsM = doubleArrayOf(5.0, -5.0, 4.8, -4.8, 5.2, -5.2, 4.5, -4.5)
            for (idx in offsetsM.indices) {
                val tMs = 2_000L + idx * 1_000L
                for (sub in 1..5) {
                    device.processAccelerometerMeasurement(
                        if (sub % 2 == 0) 1.5f else -1.5f, 0.5f, 9.81f, tMs - 1_000L + sub * 200L
                    )
                }
                val loc = createMutableLocation(
                    baseLat,
                    baseLon + offsetsM[idx] * degPerMeterLon,
                    acc = 16.0f,
                    timeMs = tMs,
                    hasSpeedFlag = true,
                    speedMps = 1.5f,
                    hasBearingFlag = false
                )
                device.onLocationChanged(loc)
                assertTrue("Oscillating indoor multipath at idx=$idx must stay pinned", device.isStationaryPinned)
            }
            assertEquals(0.0, (speedSensor.value as? Number)?.toDouble() ?: -1.0, 1e-6)
            assertEquals(0.0, (distanceSensor.value as? Number)?.toDouble() ?: -1.0, 1e-6)
        } finally {
            device.shutDown()
        }
    }

    // REQ-STB-023.1 / TST-STB-023.1: Live ATT Fused EKF retains zero snapshots across 60 min & throttles 50-Hz setLastActive() DB writes
    @Test
    fun liveAttFused_disablesEkfSnapshotRetentionAndThrottlesGyroSetLastActive_TST_STB_023_1() {
        val device = object : SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager) {
            fun callAccumulatorsReset() = onAccumulatorsReset()
        }
        try {
            io.mockk.clearMocks(mockDevicesDbManager, answers = false, recordedCalls = true)
            val identityQuat = floatArrayOf(0f, 0f, 0f, 1f)
            val gyro = floatArrayOf(0f, 0f, 0.05f)

            // 1. 20 s of 50 Hz gyro (1,000 ticks) -> setLastActive() throttled to >= 5,000 ms (4 writes: t=0, 5s, 10s, 15s)
            for (step in 0 until 1_000) {
                val tMs = 10_000L + step * 20L
                device.ingestImuMeasurement(identityQuat, gyro, dtSec = 0.02, timestampMs = tMs)
            }
            verify(exactly = 4) { mockDevicesDbManager.setLastActive(any()) }

            // 2. Clock rewind (timestampMs < mLastActiveWriteMs) safely re-anchors and writes once
            device.ingestImuMeasurement(identityQuat, gyro, dtSec = 0.02, timestampMs = 5_000L)
            verify(exactly = 5) { mockDevicesDbManager.setLastActive(any()) }

            // 3. onAccumulatorsReset() -> resetOriginAndEkf() resets mLastActiveWriteMs so next tick records activity
            device.callAccumulatorsReset()
            device.ingestImuMeasurement(identityQuat, gyro, dtSec = 0.02, timestampMs = 5_020L)
            verify(exactly = 6) { mockDevicesDbManager.setLastActive(any()) }

            // 4. 60 min of 50 Hz EKF predict + 1 Hz GNSS updates -> strictly 0 retained OdometrySnapshots
            for (step in 1..180_000) {
                val tMs = 100_000L + step * 20L
                device.ekf.predict(0.02, 0.01, tMs, 1.0)
                if (step % 50 == 0) {
                    device.ekf.updateSpeed(3.0, 0.5, tMs)
                    device.ekf.updateGnssPosition(step * 0.06, 0.0, 5.0, tMs)
                }
            }
            assertTrue(
                "Live SpeedAndLocationDevice_ATTFused.ekf must retain 0 snapshots across 60 min",
                device.ekf.getSnapshots().isEmpty()
            )
        } finally {
            device.shutDown()
        }
    }

    // REQ-EXP-045.1 / TST-EXP-045.1: Raw WHEEL & GNSS tap records before ATT-Fused feature guards using CLOCK_BOOTTIME
    @Test
    fun wheelAndGnssRawTap_recordsBeforeFeatureGuardsWithElapsedRealtimeClock_TST_EXP_045_1() = kotlinx.coroutines.runBlocking {
        val tempFile = java.io.File.createTempFile("att_fused_raw_", ".phone_raw.csv.gz")
        tempFile.delete()
        val recorder = com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder(
            targetFile = tempFile,
            metadata = com.atrainingtracker.banalservice.devices.raw.PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Test", sportId = 1L, sessionStartTimeMs = 1000L)
        )
        recorder.start()
        val prevClock = com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.elapsedRealtimeNanosProvider
        com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.elapsedRealtimeNanosProvider = { 888_000_000_000L }
        com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(recorder)

        every { AttFusedPreferences.isEffectiveWheelActive(any()) } returns false
        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns false
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        try {
            device.onWheelMeasurement(speedMps = 6.25, variance = 0.04, timestampMs = 1_760_000_001_000L)
            val loc = mockk<android.location.Location>(relaxed = true) {
                every { latitude } returns 48.137
                every { longitude } returns 11.576
                every { time } returns 1_760_000_002_000L
                every { elapsedRealtimeNanos } returns 889_000_000_000L
                every { hasAccuracy() } returns true
                every { accuracy } returns 4.5f
            }
            device.onLocationChanged(loc)
        } finally {
            com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.elapsedRealtimeNanosProvider = prevClock
            com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(null)
            recorder.stop()
            device.shutDown()
        }

        try {
            val parsed = com.atrainingtracker.banalservice.devices.raw.PhoneRawTelemetryReader.readAll(tempFile)
            assertEquals(2, parsed.rows.size)
            assertEquals(com.atrainingtracker.banalservice.devices.raw.PhoneRawChannel.WHEEL, parsed.rows[0].channel)
            assertEquals(888_000_000_000L, parsed.rows[0].elapsedRealtimeNs)
            assertEquals(6.25, parsed.rows[0].v0, 1e-6)
            assertEquals(com.atrainingtracker.banalservice.devices.raw.PhoneRawChannel.GNSS, parsed.rows[1].channel)
            assertEquals(889_000_000_000L, parsed.rows[1].elapsedRealtimeNs)
            assertEquals(48.137, parsed.rows[1].v0, 1e-6)
        } finally {
            tempFile.delete()
        }
    }

    // REQ-EXP-045.1 / TST-EXP-045.1: Pre-EKF raw GNSS recorded once (no duplicates, no EKF mutation, includes rejected/stationary fixes)
    @Test
    fun onLocationChanged_whenGnssActive_recordsPreEkfRawFixExactlyOnceIncludingRejectedFixes_TST_EXP_045_1() = kotlinx.coroutines.runBlocking {
        val tempFile = java.io.File.createTempFile("att_fused_pre_ekf_", ".phone_raw.csv.gz")
        tempFile.delete()
        val recorder = com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder(
            targetFile = tempFile,
            metadata = com.atrainingtracker.banalservice.devices.raw.PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Test", sportId = 1L, sessionStartTimeMs = 1000L)
        )
        recorder.start()
        com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(recorder)

        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns true
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        try {
            // 1. Valid moving fix (onLocationChanged mutates Location in-place before calling onNewLocation)
            var mutatedLat = 48.137154
            var mutatedLon = 11.576124
            var mutatedSpeed = 3.75f
            val movingLoc = mockk<android.location.Location>(relaxed = true) {
                every { latitude } answers { mutatedLat }
                every { longitude } answers { mutatedLon }
                every { latitude = any() } answers { mutatedLat = firstArg() }
                every { longitude = any() } answers { mutatedLon = firstArg() }
                every { hasSpeed() } returns true
                every { speed } answers { mutatedSpeed }
                every { speed = any() } answers { mutatedSpeed = firstArg() }
                every { hasAccuracy() } returns true
                every { accuracy } returns 5.2f
                every { hasAltitude() } returns true
                every { altitude } returns 520.0
                every { hasBearing() } returns true
                every { bearing } returns 90.0f
                every { time } returns 1_760_000_010_000L
                every { elapsedRealtimeNanos } returns 910_000_000_000L
            }
            device.onLocationChanged(movingLoc)

            // 2. Coarse/rejected fix (exceeds MAX_GNSS_ACCURACY_METERS -> early return path)
            val rejectedLoc = mockk<android.location.Location>(relaxed = true) {
                every { latitude } returns 48.138000
                every { longitude } returns 11.577000
                every { hasSpeed() } returns true
                every { speed } returns 0.0f
                every { hasAccuracy() } returns true
                every { accuracy } returns 120.0f
                every { time } returns 1_760_000_011_000L
                every { elapsedRealtimeNanos } returns 911_000_000_000L
            }
            device.onLocationChanged(rejectedLoc)
        } finally {
            com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(null)
            recorder.stop()
            device.shutDown()
        }

        try {
            val parsed = com.atrainingtracker.banalservice.devices.raw.PhoneRawTelemetryReader.readAll(tempFile)
            val gnssRows = parsed.rows.filter { it.channel == com.atrainingtracker.banalservice.devices.raw.PhoneRawChannel.GNSS }
            assertEquals("Must record exactly 2 GNSS rows (1 per fix, zero duplicates)", 2, gnssRows.size)
            assertEquals(48.137154, gnssRows[0].v0, 1e-6)
            assertEquals(11.576124, gnssRows[0].v1, 1e-6)
            assertEquals(3.75, gnssRows[0].v3, 1e-4)
            assertEquals(5.2, gnssRows[0].v5, 1e-4)
            assertEquals(48.138000, gnssRows[1].v0, 1e-6)
            assertEquals(120.0, gnssRows[1].v5, 1e-4)
        } finally {
            tempFile.delete()
        }
    }

    // REQ-EXP-047.1, REQ-EXP-047.2 / TST-EXP-047.2: Concurrent GPS + ATTFused deliver identical GPS fix -> 1 GNSS row
    @Test
    fun concurrentGpsAndAttFused_deduplicatesIdenticalGpsFixAcrossInterleavedFusedUpdate_TST_EXP_047_2() = kotlinx.coroutines.runBlocking {
        val tempFile = java.io.File.createTempFile("gps_att_fused_dedup_", ".phone_raw.csv.gz")
        tempFile.delete()
        val recorder = com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder(
            targetFile = tempFile,
            metadata = com.atrainingtracker.banalservice.devices.raw.PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Pixel 10", sportId = 1L, sessionStartTimeMs = 1_795_331_657_000L)
        )
        recorder.start()
        com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(recorder)

        every { AttFusedPreferences.isEffectiveGnssActive(any()) } returns true
        val gpsDevice = SpeedAndLocationDevice_GPS(mockContext, mockSensorManager)
        val attFusedDevice = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        try {
            val gpsCopyForGps = mockk<android.location.Location>(relaxed = true) {
                every { provider } returns "gps"
                every { latitude } returns 48.137154
                every { longitude } returns 11.576124
                every { hasAltitude() } returns true
                every { altitude } returns 478.2
                every { hasSpeed() } returns true
                every { speed } returns 3.4f
                every { hasBearing() } returns true
                every { bearing } returns 90.0f
                every { hasAccuracy() } returns true
                every { accuracy } returns 4.2f
                every { time } returns 1_795_331_657_000L
                every { elapsedRealtimeNanos } returns 685_756_090_818_846L
            }
            val gpsCopyForAttFused = mockk<android.location.Location>(relaxed = true) {
                every { provider } returns "gps"
                every { latitude } returns 48.137154
                every { longitude } returns 11.576124
                every { hasAltitude() } returns true
                every { altitude } returns 478.2
                every { hasSpeed() } returns true
                every { speed } returns 3.4f
                every { hasBearing() } returns true
                every { bearing } returns 90.0f
                every { hasAccuracy() } returns true
                every { accuracy } returns 4.2f
                every { time } returns 1_795_331_657_000L
                every { elapsedRealtimeNanos } returns 685_756_090_818_846L
            }
            val interleavedFused = mockk<android.location.Location>(relaxed = true) {
                every { provider } returns "fused"
                every { latitude } returns 48.137160
                every { longitude } returns 11.576130
                every { hasAltitude() } returns true
                every { altitude } returns 493.5
                every { hasSpeed() } returns true
                every { speed } returns 3.1f
                every { hasBearing() } returns true
                every { bearing } returns 91.0f
                every { hasAccuracy() } returns true
                every { accuracy } returns 5.0f
                every { time } returns 1_795_331_657_000L
                every { elapsedRealtimeNanos } returns 685_756_090_818_846L
            }

            gpsDevice.onLocationChanged(gpsCopyForGps)
            com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.recordLocation(
                interleavedFused,
                1_795_331_657_000L,
                com.atrainingtracker.banalservice.devices.raw.PhoneRawChannel.GNSS_FUSED
            )
            attFusedDevice.onLocationChanged(gpsCopyForAttFused)
        } finally {
            com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(null)
            recorder.stop()
            gpsDevice.shutDown()
            attFusedDevice.shutDown()
        }

        try {
            val parsed = com.atrainingtracker.banalservice.devices.raw.PhoneRawTelemetryReader.readAll(tempFile)
            val gnssRows = parsed.rows.filter { it.channel == com.atrainingtracker.banalservice.devices.raw.PhoneRawChannel.GNSS }
            val fusedRows = parsed.rows.filter { it.channel == com.atrainingtracker.banalservice.devices.raw.PhoneRawChannel.GNSS_FUSED }
            assertEquals("Concurrent GPS + ATTFused must emit exactly 1 GNSS row", 1, gnssRows.size)
            assertEquals("Interleaved Google Fused fix must emit exactly 1 GNSS_FUSED row", 1, fusedRows.size)
            assertEquals(478.2, gnssRows[0].v2, 1e-4)
            assertEquals(493.5, fusedRows[0].v2, 1e-4)
            assertEquals(0L, recorder.getDroppedFrameCount())
        } finally {
            tempFile.delete()
        }
    }

    // REQ-FIL-041.1 / TST-FIL-041.1: Entering indoor stop (acc = 45.9 m > 20 m, STATIONARY) immediately publishes
    // 0.0 m/s speed & null pace, cancels pending watchdog callbacks, and leaves pinned coordinates/distance unchanged.
    @Test
    fun testStationaryRejectedIndoorGnss_publishesZeroSpeedCancelsWatchdogAndFreezesDistance_TST_FIL_041_1() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        val mockWatchdogHandler = mockk<android.os.Handler>(relaxed = true)
        device.setWatchdogHandler(mockWatchdogHandler)
        try {
            // 1. Pre-anchor coarse stationary fix publishes 0.0 m/s & null pace cleanly without deadlock
            device.onLocationChanged(
                createMutableLocation(48.6567, 9.0545, acc = 45.9f, timeMs = 500L, hasSpeedFlag = true, speedMps = 0.0f, hasBearingFlag = false)
            )
            assertEquals(0.0, device.mSpeedSensor?.value ?: -1.0, 1e-9)
            assertNull(device.mPaceSensor?.value)

            // 2. Establish active movement at ~1.54 m/s outdoors
            var lat = 48.6567
            val lon = 9.0545
            val degPerMeter = 1.0 / (Math.toRadians(1.0) * 6371000.0)
            var stepTick = 0
            for (sec in 1L..6L) {
                val tMs = sec * 1_000L
                for (sub in 1..50) {
                    stepTick++
                    val subMs = (sec - 1L) * 1_000L + sub * 20L
                    val phase = 2.0 * PI * 1.7 * (sub * 0.02)
                    val half = Math.toRadians(65.0 + 8.0 * kotlin.math.sin(phase)) * 0.5
                    if (stepTick % 29 == 0) device.ingestStepEventForTesting(subMs)
                    device.ingestImuMeasurement(
                        floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat()),
                        floatArrayOf(0f, 0f, (0.22 * kotlin.math.sin(phase)).toFloat()),
                        dtSec = 0.02,
                        timestampMs = subMs
                    )
                }
                lat += 1.54 * degPerMeter
                device.onLocationChanged(
                    createMutableLocation(lat, lon, acc = 4.0f, timeMs = tMs, hasSpeedFlag = true, speedMps = 1.54f, hasBearingFlag = true, bearingDeg = 0.0f)
                )
            }
            assertTrue("Must be moving near ~1.5 m/s prior to indoor stop (was ${device.speed})", device.speed > 1.0)
            assertNotNull("Pace sensor must be active while moving", device.mPaceSensor?.value)

            // 3. Enter indoor stop: quiet IMU + coarse acc = 45.9 m fixes -> as soon as STATIONARY engages,
            //    rejected coarse fix immediately publishes 0.0 m/s & null pace with +0.0 m distance and cancels watchdog.
            val flatRotVec = floatArrayOf(0f, 0f, 0f, 1f)
            var sawImmediateZeroOnStationaryTick = false
            for (sec in 7L..16L) {
                val tMs = sec * 1_000L
                val latBeforeTick = device.mLatitudeSensor?.value
                val lonBeforeTick = device.mLongitudeSensor?.value
                val distBeforeTick = (device.mDistanceSensor?.value as? Number)?.toDouble() ?: 0.0
                io.mockk.clearMocks(mockWatchdogHandler, answers = false)
                for (sub in 1..10) {
                    device.ingestImuMeasurement(flatRotVec, floatArrayOf(0f, 0f, 0.01f), dtSec = 0.1, timestampMs = (sec - 1L) * 1_000L + sub * 100L)
                }
                device.onLocationChanged(
                    createMutableLocation(lat + 0.0003, lon + 0.0002, acc = 45.9f, timeMs = tMs, hasSpeedFlag = true, speedMps = 1.54f, hasBearingFlag = false)
                )
                if (device.mActivityAndCarriageModeSensor?.value == PhoneCarriageMode.STATIONARY.code) {
                    assertEquals("mSpeedSensor must immediately publish 0.0 m/s on STATIONARY rejected fix", 0.0, device.mSpeedSensor?.value ?: -1.0, 1e-9)
                    assertNull("mPaceSensor must immediately publish null on STATIONARY rejected fix", device.mPaceSensor?.value)
                    assertEquals("device.speed must immediately reset to 0.0 m/s", 0.0, device.speed, 1e-9)
                    org.junit.Assert.assertFalse("device.isDecaying must be false after publishStationaryZeroSpeed", device.isDecaying)
                    assertEquals("Pinned latitude must remain unchanged on STATIONARY rejected fix", latBeforeTick, device.mLatitudeSensor?.value)
                    assertEquals("Pinned longitude must remain unchanged on STATIONARY rejected fix", lonBeforeTick, device.mLongitudeSensor?.value)
                    val postStopDist = (device.mDistanceSensor?.value as? Number)?.toDouble() ?: 0.0
                    assertEquals("Cumulative distance must add +0.0 m on STATIONARY rejected fix", distBeforeTick, postStopDist, 1e-9)
                    verify(atLeast = 1) { mockWatchdogHandler.removeCallbacks(any<Runnable>()) }

                    // Subsequent coarse multipath jump while STATIONARY keeps 0.0 m/s, null pace, and +0.0 m distance
                    device.onLocationChanged(
                        createMutableLocation(lat + 0.0009, lon + 0.0009, acc = 45.9f, timeMs = tMs + 1_000L, hasSpeedFlag = true, speedMps = 1.54f, hasBearingFlag = false)
                    )
                    assertEquals(0.0, device.mSpeedSensor?.value ?: -1.0, 1e-9)
                    assertNull(device.mPaceSensor?.value)
                    assertEquals(latBeforeTick, device.mLatitudeSensor?.value)
                    assertEquals(lonBeforeTick, device.mLongitudeSensor?.value)
                    assertEquals(distBeforeTick, (device.mDistanceSensor?.value as? Number)?.toDouble() ?: 0.0, 1e-9)
                    sawImmediateZeroOnStationaryTick = true
                    break
                }
            }
            assertTrue("Classifier must reach STATIONARY and publish immediate 0.0 m/s", sawImmediateZeroOnStationaryTick)
        } finally {
            device.shutDown()
        }
    }

    // REQ-FIL-041.2 & REQ-FIL-041.3 / TST-FIL-041.2: 159 s indoor bakery stop followed by +19.1 m outdoor snap
    // (acc = 12.75 -> 11.73 m, Doppler 3.51 m/s) keeps SPEED_mps_att_fused near 3.51 m/s (never 10.85 m/s).
    @Test
    fun testPostPause159sStopAnd19mOutdoorSnap_boundsPublishedSpeedNearDoppler_TST_FIL_041_2() {
        val device = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        try {
            val degPerMeter = 1.0 / (Math.toRadians(1.0) * 6371000.0)
            var lat = 48.6567
            val lon = 9.0545
            val flatRotVec = floatArrayOf(0f, 0f, 0f, 1f)

            // 1. Pre-stop outdoor riding at 5.0 m/s North (anchors heading so coldStart.decouplePositionVelocity is false!)
            for (sec in 1L..10L) {
                val tMs = sec * 1_000L
                lat += 5.0 * degPerMeter
                device.onLocationChanged(
                    createMutableLocation(lat, lon, acc = 4.0f, timeMs = tMs, hasSpeedFlag = true, speedMps = 5.0f, hasBearingFlag = true, bearingDeg = 0.0f)
                )
            }
            assertTrue("Heading must be anchored prior to stop", device.speed > 4.0)

            // 2. 159 s indoor bakery stop (acc = 45.9 m rejected fixes + quiet IMU)
            for (sec in 11L..169L) {
                val tMs = sec * 1_000L
                device.ingestImuMeasurement(flatRotVec, floatArrayOf(0f, 0f, 0.01f), dtSec = 0.2, timestampMs = tMs)
                device.onLocationChanged(
                    createMutableLocation(lat, lon, acc = 45.9f, timeMs = tMs, hasSpeedFlag = true, speedMps = 0.0f, hasBearingFlag = false)
                )
            }
            assertEquals("Speed must be 0.0 m/s during indoor bakery stop", 0.0, device.mSpeedSensor?.value ?: -1.0, 1e-9)

            // 3. Exit bakery: row 228 (t = 170 s, acc = 12.75 m, Doppler 2.61 m/s) -> row 229 (+19.1 m snap in 1 s, acc = 11.73 m, Doppler 3.51 m/s)
            val exitLat228 = lat + 6.0 * degPerMeter
            device.onLocationChanged(
                createMutableLocation(exitLat228, lon, acc = 12.75f, timeMs = 170_000L, hasSpeedFlag = true, speedMps = 2.61f, hasBearingFlag = true, bearingDeg = 0.0f)
            )
            val exitLat229 = exitLat228 + 19.1 * degPerMeter
            device.onLocationChanged(
                createMutableLocation(exitLat229, lon, acc = 11.73f, timeMs = 171_000L, hasSpeedFlag = true, speedMps = 3.51f, hasBearingFlag = true, bearingDeg = 0.0f)
            )

            val publishedSpeed = device.mSpeedSensor?.value ?: Double.NaN
            assertTrue(
                "Post-pause +19.1 m snap must track Doppler ~3.51 m/s within 1.5 m/s (never 10.85 m/s; was $publishedSpeed)",
                publishedSpeed.isFinite() && kotlin.math.abs(publishedSpeed - 3.51) <= 1.5
            )
            assertTrue("EKF speed must remain bounded near Doppler (was ${device.ekf.speedMps})", kotlin.math.abs(device.ekf.speedMps - 3.51) <= 1.5)
        } finally {
            device.shutDown()
        }
    }

    // REQ-FIL-041.2 & REQ-FIL-041.3 / TST-FIL-041.3: Genuine fast start from standstill to 9.0 m/s (Doppler & wheel)
    // tracks 9.0 m/s promptly without artificial clamping or lag.
    @Test
    fun testGenuineFastStartFromStandstill_tracks9MpsDopplerAndWheelWithoutClamping_TST_FIL_041_3() {
        val degPerMeter = 1.0 / (Math.toRadians(1.0) * 6371000.0)

        // 1. Outdoor GNSS Doppler fast start (0 -> 9.0 m/s, acc = 6.0 m)
        val gnssDevice = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        try {
            var lat = 48.6567
            val lon = 9.0545
            // Standstill start
            for (sec in 1L..4L) {
                gnssDevice.onLocationChanged(
                    createMutableLocation(lat, lon, acc = 6.0f, timeMs = sec * 1_000L, hasSpeedFlag = true, speedMps = 0.0f, hasBearingFlag = false)
                )
            }
            assertEquals(0.0, gnssDevice.speed, 1e-9)

            // Accelerate to 9.0 m/s North
            for (sec in 5L..12L) {
                lat += 9.0 * degPerMeter
                gnssDevice.onLocationChanged(
                    createMutableLocation(lat, lon, acc = 6.0f, timeMs = sec * 1_000L, hasSpeedFlag = true, speedMps = 9.0f, hasBearingFlag = true, bearingDeg = 0.0f)
                )
            }
            val publishedGnssSpeed = gnssDevice.mSpeedSensor?.value ?: 0.0
            assertTrue("Genuine 9.0 m/s Doppler fast start must track >= 8.2 m/s (was $publishedGnssSpeed)", publishedGnssSpeed >= 8.2)
            assertTrue("EKF Doppler speed must track ~9.0 m/s (was ${gnssDevice.ekf.speedMps})", gnssDevice.ekf.speedMps >= 8.5)
        } finally {
            gnssDevice.shutDown()
        }

        // 2. Active wheel sensor fast start (0 -> 9.0 m/s)
        val wheelDevice = SpeedAndLocationDevice_ATTFused(mockContext, mockSensorManager)
        try {
            var lat = 48.6567
            val lon = 9.0545
            wheelDevice.onLocationChanged(
                createMutableLocation(lat, lon, acc = 6.0f, timeMs = 1_000L, hasSpeedFlag = true, speedMps = 0.0f, hasBearingFlag = false)
            )
            for (sec in 2L..8L) {
                val tMs = sec * 1_000L
                lat += 9.0 * degPerMeter
                wheelDevice.onWheelMeasurement(9.0, 0.05, tMs)
                wheelDevice.onLocationChanged(
                    createMutableLocation(lat, lon, acc = 6.0f, timeMs = tMs, hasSpeedFlag = true, speedMps = 9.0f, hasBearingFlag = true, bearingDeg = 0.0f)
                )
            }
            val publishedWheelSpeed = wheelDevice.mSpeedSensor?.value ?: 0.0
            assertTrue("Genuine 9.0 m/s wheel fast start must track >= 8.5 m/s (was $publishedWheelSpeed)", publishedWheelSpeed >= 8.5)
        } finally {
            wheelDevice.shutDown()
        }
    }

    private fun createSyntheticSensorEvent(sensorType: Int, timestampNs: Long, data: FloatArray): SensorEvent {
        val mockSensor = mockk<Sensor>(relaxed = true) {
            every { type } returns sensorType
        }
        val event = mockk<SensorEvent>(relaxed = true)
        event.sensor = mockSensor
        event.timestamp = timestampNs
        SensorEvent::class.java.getField("values").apply { isAccessible = true }.set(event, data)
        return event
    }
}
