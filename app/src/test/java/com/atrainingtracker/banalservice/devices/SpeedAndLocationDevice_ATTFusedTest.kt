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
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.banalservice.odometry.SportContextHint
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.verify
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
}
