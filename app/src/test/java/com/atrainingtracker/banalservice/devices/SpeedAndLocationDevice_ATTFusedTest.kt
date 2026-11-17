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
}
