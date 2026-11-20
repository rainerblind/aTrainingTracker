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
import android.location.Location
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Indoor GPS suppression, External Speed Sensor coupling, and IMU Rest-Variance stationary pinning
 * (REQ-TRK-077, TST-TRK-077, ATT-47).
 */
class SpeedAndLocationDeviceIndoorAndRestCouplingTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager
    private lateinit var mockHandler: Handler
    private lateinit var device: TestSpeedAndLocationDevice

    class TestSpeedAndLocationDevice(
        context: Context,
        sensorManager: MySensorManager
    ) : SpeedAndLocationDevice(context, sensorManager, DeviceType.SPEED_AND_LOCATION_GPS) {
        fun getSpeedSensor(): MySensor<Double> = mSpeedSensor
        fun getPaceSensor(): MySensor<Double> = mPaceSensor
        fun getBearingSensor(): MySensor<Double> = mBearingSensor
        fun getDistanceSensor(): MyDoubleAccumulatorSensor = mDistanceSensor
        fun getLapDistanceSensor(): MyDoubleAccumulatorSensor = mLapDistanceSensor
        fun getLatitudeSensor(): MySensor<Double> = mLatitudeSensor
        fun getLongitudeSensor(): MySensor<Double> = mLongitudeSensor
        fun callAccumulatorsReset() = onAccumulatorsReset()
        fun getHysteresisCount(): Int = getStationaryHysteresisCount()
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Double>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }

        mockkStatic(DevicesDatabaseManager::class)
        mockDevicesDbManager = mockk(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager
        every { mockDevicesDbManager.getSpeedAndLocationGPSDeviceId() } returns 101L

        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(any()) } returns 200.0f

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns true
        every { TrainingApplication.isPaused() } returns false

        mockContext = mockk(relaxed = true)
        every { mockContext.packageName } returns "com.atrainingtracker"
        mockSensorManager = mockk(relaxed = true)
        mockHandler = mockk(relaxed = true)

        device = TestSpeedAndLocationDevice(mockContext, mockSensorManager)
        device.setWatchdogHandler(mockHandler)
    }

    @After
    fun tearDown() {
        try {
            unmockkAll()
        } catch (_: Throwable) {
            // Guard against MockK concurrency disposal race
        }
    }

    private fun createMockFix(
        lat: Double,
        lon: Double,
        timeMs: Long,
        speed: Float? = null,
        accuracy: Float = 5.0f,
        bearing: Float = 0.0f
    ): Location {
        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns lat
        every { loc.longitude } returns lon
        every { loc.time } returns timeMs
        every { loc.accuracy } returns accuracy
        every { loc.bearing } returns bearing
        every { loc.altitude } returns 450.0
        every { loc.provider } returns "gps"
        if (speed != null) {
            every { loc.hasSpeed() } returns true
            every { loc.speed } returns speed
        } else {
            every { loc.hasSpeed() } returns false
            every { loc.speed } returns 0.0f
        }
        return loc
    }

    // =========================================================================
    // TST-TRK-077.1: Indoor Mode GPS Suppression & Distance Freezing
    // =========================================================================

    @Test
    fun testIndoorMode_suppressesGpsDistanceCreepAndPinsAnchorCoordinates() {
        device.setIndoorMode(true)
        assertTrue("Indoor mode must be active", device.isIndoorMode)

        // Fix 1: Initial fix arriving indoors
        val loc1 = createMockFix(lat = 48.13700, lon = 11.57500, timeMs = 1000L, speed = 0.0f, accuracy = 8.0f)
        device.onNewLocation(loc1)

        assertTrue("Device must be pinned to stationary anchor upon first fix in indoor mode", device.isStationaryPinned)
        assertEquals(48.13700, device.getLatitudeSensor().value ?: 0.0, 0.000001)
        assertEquals(11.57500, device.getLongitudeSensor().value ?: 0.0, 0.000001)
        assertEquals(0.0, device.getSpeedSensor().value ?: -1.0, 0.001)
        assertEquals(0.0, device.getDistanceSensor().value ?: -1.0, 0.001)

        // Fix 2: Severe multipath jump of 35m with reported speed = 4.5 m/s (normally unpins immediately)
        val loc2 = createMockFix(lat = 48.13735, lon = 11.57500, timeMs = 2000L, speed = 4.5f, accuracy = 10.0f)
        every { loc1.distanceTo(loc2) } returns 35.0f
        device.onNewLocation(loc2)

        // Indoor mode must unconditionally hold pinning, clamp speed to 0, and prevent distance accumulation
        assertTrue("Device must remain pinned regardless of 35m multipath jump in indoor mode", device.isStationaryPinned)
        assertEquals("Speed must remain clamped to 0.0 m/s", 0.0, device.getSpeedSensor().value ?: -1.0, 0.001)
        assertNull("Pace must remain null", device.getPaceSensor().value)
        assertEquals("Distance must remain 0.0 m (no GPS creep)", 0.0, device.getDistanceSensor().value ?: -1.0, 0.001)
        assertEquals("Lap distance must remain 0.0 m", 0.0, device.getLapDistanceSensor().value ?: -1.0, 0.001)
        // Published coordinates must remain anchored to loc1
        assertEquals(48.13700, device.getLatitudeSensor().value ?: 0.0, 0.000001)
        assertEquals(11.57500, device.getLongitudeSensor().value ?: 0.0, 0.000001)

        // Fix 3: Another multipath drift 50m away
        val loc3 = createMockFix(lat = 48.13650, lon = 11.57500, timeMs = 3000L, speed = 6.0f, accuracy = 15.0f)
        every { loc1.distanceTo(loc3) } returns 50.0f
        every { loc2.distanceTo(loc3) } returns 85.0f
        device.onNewLocation(loc3)

        assertTrue(device.isStationaryPinned)
        assertEquals(0.0, device.getDistanceSensor().value ?: -1.0, 0.001)
        assertEquals(48.13700, device.getLatitudeSensor().value ?: 0.0, 0.000001)
    }

    @Test
    fun testIndoorMode_toggleRestoresNormalUnpinning() {
        device.setIndoorMode(true)
        val loc1 = createMockFix(lat = 48.13700, lon = 11.57500, timeMs = 1000L, speed = 0.0f, accuracy = 5.0f)
        device.onNewLocation(loc1)
        assertTrue(device.isStationaryPinned)

        // Disabling indoor mode restores standard outdoor escape gating
        device.setIndoorMode(false)
        assertFalse(device.isIndoorMode)

        // A large movement jump > escapeRadius (escape = max(3.0, 1.5 * 5) = 7.5m) with speed > threshold unpins
        val loc2 = createMockFix(lat = 48.13750, lon = 11.57500, timeMs = 2000L, speed = 3.0f, accuracy = 5.0f)
        every { loc1.distanceTo(loc2) } returns 25.0f
        device.onNewLocation(loc2)

        assertFalse("Device must unpin once indoor mode is disabled and physical movement occurs", device.isStationaryPinned)
        assertEquals(25.0, device.getDistanceSensor().value ?: -1.0, 0.001)
    }

    // =========================================================================
    // TST-TRK-077.2: External Sensor Zero-Coupling
    // =========================================================================

    @Test
    fun testExternalSpeedSensor_zeroSpeedBlocksUnpinningOnMultipathJumps() {
        val now = System.currentTimeMillis()
        val mockSpeedSensor = mockk<MySensor<Double>>(relaxed = true)
        every { mockSpeedSensor.isActivated } returns true
        every { mockSpeedSensor.value } returns 0.0
        every { mockSpeedSensor.lastUpdateTimestamp } returns now

        every {
            mockSensorManager.getSensorForDeviceType(SensorType.SPEED_mps, DeviceType.BIKE_SPEED)
        } returns mockSpeedSensor

        assertTrue("External speed sensor must be reported as stationary", device.isExternalSpeedSensorStationary(now))

        // Fix 1: Pin at rest stop
        val loc1 = createMockFix(lat = 48.0, lon = 11.0, timeMs = now, speed = 0.0f, accuracy = 5.0f)
        device.onNewLocation(loc1)
        assertTrue("Device must be pinned", device.isStationaryPinned)

        // Fix 2: 20m multipath jump while bike wheel is stationary (speed = 0.0 m/s from wheel sensor)
        // Escape radius is 1.5 * 5.0 = 7.5m, so 20m would normally unpin
        val loc2 = createMockFix(lat = 48.0002, lon = 11.0, timeMs = now + 1000L, speed = 2.0f, accuracy = 5.0f)
        every { loc1.distanceTo(loc2) } returns 20.0f
        device.onNewLocation(loc2)

        assertTrue("Stationary wheel sensor must prevent unpinning on multipath jump", device.isStationaryPinned)
        assertEquals(0.0, device.getDistanceSensor().value ?: -1.0, 0.001)
        assertEquals(48.0, device.getLatitudeSensor().value ?: 0.0, 0.000001)
    }

    @Test
    fun testExternalSpeedSensor_motionImmediatelyReleasesPinning() {
        val now = System.currentTimeMillis()
        val mockSpeedSensor = mockk<MySensor<Double>>(relaxed = true)
        every { mockSpeedSensor.isActivated } returns true
        every { mockSpeedSensor.value } returns 0.0
        every { mockSpeedSensor.lastUpdateTimestamp } returns now

        every {
            mockSensorManager.getSensorForDeviceType(SensorType.SPEED_mps, DeviceType.BIKE_SPEED_AND_CADENCE)
        } returns mockSpeedSensor

        val loc1 = createMockFix(lat = 48.0, lon = 11.0, timeMs = now, speed = 0.0f, accuracy = 5.0f)
        device.onNewLocation(loc1)
        assertTrue(device.isStationaryPinned)

        // Update wheel speed sensor to 5.0 m/s (rider starts pedaling)
        every { mockSpeedSensor.value } returns 5.0
        every { mockSpeedSensor.lastUpdateTimestamp } returns (now + 1000L)

        assertFalse("External speed sensor reporting motion must not be stationary", device.isExternalSpeedSensorStationary(now + 1000L))

        val loc2 = createMockFix(lat = 48.0001, lon = 11.0, timeMs = now + 1000L, speed = 5.0f, accuracy = 5.0f)
        every { loc1.distanceTo(loc2) } returns 10.0f
        device.onNewLocation(loc2)

        assertFalse("Device must unpin immediately when wheel sensor reports motion", device.isStationaryPinned)
    }

    @Test
    fun testExternalSpeedSensor_staleDataDoesNotBlockUnpinning() {
        val t0 = 100000L
        val mockSpeedSensor = mockk<MySensor<Double>>(relaxed = true)
        every { mockSpeedSensor.isActivated } returns true
        every { mockSpeedSensor.value } returns 0.0
        every { mockSpeedSensor.lastUpdateTimestamp } returns t0 // Updated at t0

        every {
            mockSensorManager.getSensorForDeviceType(SensorType.SPEED_mps, DeviceType.BIKE_SPEED)
        } returns mockSpeedSensor

        // Fix 1 at t0: pinned
        val loc1 = createMockFix(lat = 48.0, lon = 11.0, timeMs = t0, speed = 0.0f, accuracy = 5.0f)
        device.onNewLocation(loc1)
        assertTrue(device.isStationaryPinned)

        // 6 seconds later (> 5000ms stale): external sensor has not been updated
        val t1 = t0 + 6000L
        assertFalse("Sensor older than 5000ms must be considered stale", device.isExternalSpeedSensorStationary(t1))

        // Legitimate GPS movement fix arrives
        val loc2 = createMockFix(lat = 48.0002, lon = 11.0, timeMs = t1, speed = 3.5f, accuracy = 5.0f)
        every { loc1.distanceTo(loc2) } returns 20.0f
        device.onNewLocation(loc2)

        assertFalse("Stale external sensor must NOT block normal GPS unpinning", device.isStationaryPinned)
        assertEquals(20.0, device.getDistanceSensor().value ?: -1.0, 0.001)
    }

    @Test
    fun testExternalSpeedSensor_supportsRunSpeedAndBikePower() {
        val now = 100000L
        val mockRunSpeedSensor = mockk<MySensor<Double>>(relaxed = true)
        every { mockRunSpeedSensor.isActivated } returns true
        every { mockRunSpeedSensor.value } returns 0.0
        every { mockRunSpeedSensor.lastUpdateTimestamp } returns now

        every {
            mockSensorManager.getSensorForDeviceType(SensorType.SPEED_mps, DeviceType.RUN_SPEED)
        } returns mockRunSpeedSensor

        assertTrue("RUN_SPEED reporting 0.0 m/s must be recognized as stationary", device.isExternalSpeedSensorStationary(now))

        // Now test BIKE_POWER
        every { mockSensorManager.getSensorForDeviceType(SensorType.SPEED_mps, DeviceType.RUN_SPEED) } returns null
        val mockPowerSpeedSensor = mockk<MySensor<Double>>(relaxed = true)
        every { mockPowerSpeedSensor.isActivated } returns true
        every { mockPowerSpeedSensor.value } returns 0.0
        every { mockPowerSpeedSensor.lastUpdateTimestamp } returns now
        every {
            mockSensorManager.getSensorForDeviceType(SensorType.SPEED_mps, DeviceType.BIKE_POWER)
        } returns mockPowerSpeedSensor

        assertTrue("BIKE_POWER speed reporting 0.0 m/s must be recognized as stationary", device.isExternalSpeedSensorStationary(now))
    }

    // =========================================================================
    // TST-TRK-077.3: Accelerometer Rest-Variance Gating
    // =========================================================================

    @Test
    fun testAccelerometerRestVariance_lowVarianceHoldsPinningAgainstDopplerSpikes() {
        val t0 = 10000L
        // Feed static accelerometer samples (norm ~9.81 m/s^2 with tiny noise variance <= 0.001)
        for (i in 0 until 10) {
            val ts = t0 + (i * 100L)
            // Near-constant gravity norm: 0, 0, 9.81
            device.processAccelerometerMeasurement(0.01f * (i % 2), 0.01f * ((i + 1) % 2), 9.81f, ts)
        }

        assertTrue("Accelerometer variance must be below 0.05 threshold", device.isAccelerometerAtRest(t0 + 900L))

        // Initial fix pins
        val loc1 = createMockFix(lat = 48.0, lon = 11.0, timeMs = t0 + 900L, speed = 0.0f, accuracy = 4.0f)
        device.onNewLocation(loc1)
        assertTrue(device.isStationaryPinned)

        // Fix 2: Doppler noise spike with speed = 1.2 m/s and 5m displacement (> deadband 3.0m)
        val loc2 = createMockFix(lat = 48.00005, lon = 11.0, timeMs = t0 + 1000L, speed = 1.2f, accuracy = 4.0f)
        every { loc1.distanceTo(loc2) } returns 5.0f
        device.onNewLocation(loc2)

        assertTrue("Stationary IMU rest floor must hold stationary pinning against Doppler spikes", device.isStationaryPinned)
        assertEquals(0.0, device.getDistanceSensor().value ?: -1.0, 0.001)
    }

    @Test
    fun testAccelerometerRestVariance_dynamicMotionReleasesGatePromptly() {
        val t0 = 20000L
        // Feed initial resting samples
        for (i in 0 until 6) {
            device.processAccelerometerMeasurement(0.0f, 0.0f, 9.81f, t0 + (i * 100L))
        }
        assertTrue(device.isAccelerometerAtRest(t0 + 500L))

        // Pin device
        val loc1 = createMockFix(lat = 48.0, lon = 11.0, timeMs = t0 + 500L, speed = 0.0f, accuracy = 4.0f)
        device.onNewLocation(loc1)
        assertTrue(device.isStationaryPinned)

        // Phone picked up / dynamic physical motion begins: norm fluctuates between 6.0 and 15.0 m/s^2 (variance > 5.0)
        device.processAccelerometerMeasurement(3.0f, 4.0f, 14.0f, t0 + 600L) // norm ~14.86
        device.processAccelerometerMeasurement(-4.0f, 2.0f, 6.0f, t0 + 700L) // norm ~7.48
        device.processAccelerometerMeasurement(5.0f, 5.0f, 12.0f, t0 + 800L) // norm ~13.92

        assertFalse("Dynamic accelerometer motion must release rest gate immediately", device.isAccelerometerAtRest(t0 + 800L))

        // Legitimate movement location arrives
        val loc2 = createMockFix(lat = 48.0002, lon = 11.0, timeMs = t0 + 800L, speed = 3.0f, accuracy = 4.0f)
        every { loc1.distanceTo(loc2) } returns 15.0f
        device.onNewLocation(loc2)

        assertFalse("Device must unpin once IMU detects physical movement", device.isStationaryPinned)
        assertEquals(15.0, device.getDistanceSensor().value ?: -1.0, 0.001)
    }

    @Test
    fun testAccelerometerRestVariance_staleDataReleasesRestGate() {
        val t0 = 30000L
        for (i in 0 until 5) {
            device.processAccelerometerMeasurement(0.0f, 0.0f, 9.81f, t0 + (i * 100L))
        }
        assertTrue(device.isAccelerometerAtRest(t0 + 400L))

        // Evaluate 1500ms after last sample (> ACCELEROMETER_STALENESS_MS = 1000ms)
        assertFalse("Stale accelerometer data (> 1s) must not hold rest gate", device.isAccelerometerAtRest(t0 + 2000L))
    }

    // =========================================================================
    // TST-TRK-077.4: Neutral Fallback When Sensors Absent
    // =========================================================================

    @Test
    fun testNeutralFallback_whenExternalSensorsAndImuAbsent() {
        // No external sensors registered in mockSensorManager
        every { mockSensorManager.getSensorForDeviceType(any(), any()) } returns null

        assertFalse("External sensor check must return false when none present", device.isExternalSpeedSensorStationary())
        assertFalse("Accelerometer check must return false when no samples fed", device.isAccelerometerAtRest())

        // Standard unpinning on movement fix works identically to baseline REQ-TRK-048
        val loc1 = createMockFix(lat = 48.0, lon = 11.0, timeMs = 1000L, speed = 0.0f, accuracy = 5.0f)
        device.onNewLocation(loc1)
        assertTrue(device.isStationaryPinned)

        val loc2 = createMockFix(lat = 48.0002, lon = 11.0, timeMs = 2000L, speed = 4.0f, accuracy = 5.0f)
        every { loc1.distanceTo(loc2) } returns 18.0f
        device.onNewLocation(loc2)

        assertFalse("Device must unpin normally on movement when auxiliary sensors absent", device.isStationaryPinned)
        assertEquals(18.0, device.getDistanceSensor().value ?: -1.0, 0.001)
    }

    // =========================================================================
    // REQ-FIL-036.1 / TST-FIL-036.1: Base-Class Veto Precedence Over Cold-Start Hook
    // =========================================================================

    @Test
    fun testColdStartReleaseHook_cannotBypassIndoorExternalWheelOrAccelRestVetoes_tstFil036_1() {
        var allowColdStartRelease = true
        val hookDevice = object : SpeedAndLocationDevice(mockContext, mockSensorManager, DeviceType.SPEED_AND_LOCATION_GPS) {
            override fun shouldReleaseColdStartStationaryPin(location: Location?): Boolean = allowColdStartRelease
            fun speedVal(): Double = mSpeedSensor.value ?: -1.0
            fun distVal(): Double = mDistanceSensor.value ?: -1.0
        }
        hookDevice.setWatchdogHandler(mockHandler)

        val t0 = 50_000L
        // 1. Indoor mode veto must block cold-start release hook unconditionally
        hookDevice.setIndoorMode(true)
        val indoor1 = createMockFix(lat = 48.0, lon = 11.0, timeMs = t0, speed = 0.0f, accuracy = 15.0f)
        hookDevice.onNewLocation(indoor1)
        assertTrue(hookDevice.isStationaryPinned)

        val indoor2 = createMockFix(lat = 48.00004, lon = 11.0, timeMs = t0 + 1000L, speed = 2.0f, accuracy = 15.0f)
        every { indoor1.distanceTo(indoor2) } returns 4.5f
        hookDevice.onNewLocation(indoor2)
        assertTrue("Indoor mode must veto cold-start release hook", hookDevice.isStationaryPinned)
        assertEquals(0.0, hookDevice.distVal(), 1e-6)
        hookDevice.setIndoorMode(false)

        // 2. Stationary external wheel sensor veto must block cold-start release hook
        val mockWheel = mockk<MySensor<Double>>(relaxed = true)
        every { mockWheel.isActivated } returns true
        every { mockWheel.value } returns 0.0
        every { mockWheel.lastUpdateTimestamp } returns t0 + 2000L
        every { mockSensorManager.getSensorForDeviceType(SensorType.SPEED_mps, DeviceType.BIKE_SPEED) } returns mockWheel

        val wheelFix = createMockFix(lat = 48.00008, lon = 11.0, timeMs = t0 + 2000L, speed = 2.0f, accuracy = 15.0f)
        every { indoor1.distanceTo(wheelFix) } returns 5.0f
        hookDevice.onNewLocation(wheelFix)
        assertTrue("Stationary external wheel must veto cold-start release hook", hookDevice.isStationaryPinned)
        assertEquals(0.0, hookDevice.distVal(), 1e-6)
        every { mockSensorManager.getSensorForDeviceType(any(), any()) } returns null

        // 3. Motionless IMU rest-variance veto must block cold-start release hook
        for (i in 0 until 5) {
            hookDevice.processAccelerometerMeasurement(0.0f, 0.0f, 9.81f, t0 + 2600L + i * 100L)
        }
        val restFix = createMockFix(lat = 48.00010, lon = 11.0, timeMs = t0 + 3000L, speed = 2.0f, accuracy = 15.0f)
        every { indoor1.distanceTo(restFix) } returns 5.5f
        hookDevice.onNewLocation(restFix)
        assertTrue("IMU rest variance must veto cold-start release hook", hookDevice.isStationaryPinned)
        assertEquals(0.0, hookDevice.speedVal(), 1e-6)

        // 4. Once all vetoes clear (dynamic IMU, outdoor, no stationary wheel), hook releases cleanly within deadband (< 0.6 * 15 = 9 m)
        for (i in 0 until 5) {
            hookDevice.processAccelerometerMeasurement(if (i % 2 == 0) 3.0f else -3.0f, 0.0f, 12.0f, t0 + 3600L + i * 100L)
        }
        val moveFix = createMockFix(lat = 48.00012, lon = 11.0, timeMs = t0 + 4000L, speed = 2.0f, accuracy = 15.0f)
        every { indoor1.distanceTo(moveFix) } returns 6.0f
        hookDevice.onNewLocation(moveFix)
        assertFalse("Cold-start release hook must unpin once all vetoes clear", hookDevice.isStationaryPinned)
        assertTrue("Unpinned speed must reflect motion", hookDevice.speedVal() > 0.5)
    }
}
