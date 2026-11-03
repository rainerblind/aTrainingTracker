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
 * Unit tests verifying stationary GPS jitter suppression, Doppler zero velocity preservation,
 * anchor coordinate pinning, bearing lock, and non-Doppler fallback
 * (REQ-TRK-048, TST-TRK-048, ATT-4850).
 */
class SpeedAndLocationDeviceStationaryJitterTest {

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

    /**
     * TST-TRK-048.1: Doppler zero velocity preservation, anchor pinning, zero distance creep, and locked bearing.
     */
    @Test
    fun testStationaryGpsJitter_preservesDopplerZeroSpeedAndSuppressesDistance() {
        // Fix 1: Initial fix with Doppler 0.0 m/s
        val loc1 = createMockFix(lat = 48.13715, lon = 11.57549, timeMs = 1000L, speed = 0.0f, bearing = 85.0f)
        device.onNewLocation(loc1)

        assertTrue("Device must be pinned to stationary anchor", device.isStationaryPinned)
        assertEquals(48.13715, device.getLatitudeSensor().value ?: 0.0, 0.000001)
        assertEquals(11.57549, device.getLongitudeSensor().value ?: 0.0, 0.000001)
        assertEquals(0.0, device.getSpeedSensor().value ?: -1.0, 0.001)
        assertEquals(0.0, device.getDistanceSensor().value ?: -1.0, 0.001)
        assertEquals(85.0, device.getBearingSensor().value ?: -1.0, 0.001)

        // Fix 2: 1s later, multipath jitter shifts position by 2.2m, bearing jumps to 270 deg, but Doppler is 0.0f
        val loc2 = createMockFix(lat = 48.13717, lon = 11.57551, timeMs = 2000L, speed = 0.0f, bearing = 270.0f)
        every { loc1.distanceTo(loc2) } returns 2.2f
        device.onNewLocation(loc2)

        // Still within deadband (max(3.0, 0.6 * 5.0) = 3.0m): must remain pinned!
        assertTrue("Device must remain pinned under 2.2m jitter", device.isStationaryPinned)
        assertEquals("Speed must remain clamped to 0.0 m/s (no geometric override)", 0.0, device.getSpeedSensor().value ?: -1.0, 0.001)
        assertNull("Pace must remain null when stopped", device.getPaceSensor().value)
        assertEquals("Distance accumulator must NOT increment under stationary jitter", 0.0, device.getDistanceSensor().value ?: -1.0, 0.001)
        assertEquals("Lap distance accumulator must NOT increment", 0.0, device.getLapDistanceSensor().value ?: -1.0, 0.001)
        assertEquals("Bearing must be locked to previous value to prevent spinning", 85.0, device.getBearingSensor().value ?: -1.0, 0.001)
        // Published coordinates must equal the stationary anchor coordinates
        assertEquals(48.13715, device.getLatitudeSensor().value ?: 0.0, 0.000001)
        assertEquals(11.57549, device.getLongitudeSensor().value ?: 0.0, 0.000001)

        // Fix 3: 1s later, jitter drifts 2.8m in other direction, bearing reports 180 deg, Doppler still 0.0f
        val loc3 = createMockFix(lat = 48.13713, lon = 11.57547, timeMs = 3000L, speed = 0.0f, bearing = 180.0f)
        every { loc1.distanceTo(loc3) } returns 2.8f
        every { loc2.distanceTo(loc3) } returns 4.5f
        device.onNewLocation(loc3)

        assertTrue(device.isStationaryPinned)
        assertEquals(0.0, device.getSpeedSensor().value ?: -1.0, 0.001)
        assertEquals(0.0, device.getDistanceSensor().value ?: -1.0, 0.001)
        assertEquals(85.0, device.getBearingSensor().value ?: -1.0, 0.001)
        assertEquals(48.13715, device.getLatitudeSensor().value ?: 0.0, 0.000001)
    }

    /**
     * TST-TRK-048.2: Smooth transition from stationary anchor pinning to motion.
     */
    @Test
    fun testStationaryAnchor_unpinsOnTrueMotion() {
        // Fix 1: Stationary anchor
        val loc1 = createMockFix(lat = 48.0, lon = 11.0, timeMs = 1000L, speed = 0.0f, bearing = 0.0f)
        device.onNewLocation(loc1)
        assertTrue(device.isStationaryPinned)

        // Fix 2: Jitter within deadband (1.5m <= 3.0m)
        val loc2 = createMockFix(lat = 48.00001, lon = 11.0, timeMs = 2000L, speed = 0.0f)
        every { loc1.distanceTo(loc2) } returns 1.5f
        device.onNewLocation(loc2)
        assertTrue(device.isStationaryPinned)
        assertEquals(0.0, device.getDistanceSensor().value ?: -1.0, 0.001)

        // Fix 3: Athlete starts cycling! Displacement from anchor = 6.0m (> 3.0m deadband), speed = 4.0 m/s
        val loc3 = createMockFix(lat = 48.00006, lon = 11.0, timeMs = 3000L, speed = 4.0f, bearing = 90.0f)
        every { loc1.distanceTo(loc3) } returns 6.0f
        every { loc2.distanceTo(loc3) } returns 4.5f
        device.onNewLocation(loc3)

        // Must unpin immediately
        assertFalse("Anchor must unpin when displacement exceeds deadband with positive speed", device.isStationaryPinned)
        assertNull("Anchor must be cleared", device.getStationaryAnchor())
        // Smoothed speed: (0.0 + 4.0) / 2 = 2.0 m/s
        assertEquals(2.0, device.getSpeedSensor().value ?: 0.0, 0.001)
        // Distance accumulated from anchor: 6.0m
        assertEquals(6.0, device.getDistanceSensor().value ?: 0.0, 0.001)
        assertEquals(90.0, device.getBearingSensor().value ?: 0.0, 0.001)

        // Fix 4: Next fix in motion, 5.0m further at 5.0 m/s
        val loc4 = createMockFix(lat = 48.00011, lon = 11.0, timeMs = 4000L, speed = 5.0f, bearing = 90.0f)
        every { loc3.distanceTo(loc4) } returns 5.0f
        device.onNewLocation(loc4)

        assertFalse(device.isStationaryPinned)
        assertEquals(11.0, device.getDistanceSensor().value ?: 0.0, 0.001) // 6.0 + 5.0 = 11.0
        // Smoothed speed: (2.0 + 5.0) / 2 = 3.5 m/s
        assertEquals(3.5, device.getSpeedSensor().value ?: 0.0, 0.001)
    }

    /**
     * TST-TRK-048.3: Provider without Doppler velocity (!hasSpeed()) fallback.
     */
    @Test
    fun testProviderWithoutDoppler_computesGeometricSpeedOnlyBeyondDeadband() {
        // Fix 1: First fix without Doppler speed
        val loc1 = createMockFix(lat = 52.52, lon = 13.40, timeMs = 1000L, speed = null, accuracy = 4.0f)
        device.onNewLocation(loc1)

        assertTrue("Provider without Doppler should initialize pinned to anchor", device.isStationaryPinned)
        assertEquals(0.0, device.getSpeedSensor().value ?: -1.0, 0.001)
        assertEquals(0.0, device.getDistanceSensor().value ?: -1.0, 0.001)

        // Fix 2: Jitter of 2.0m (deadband is max(3.0, 0.6 * 4.0) = 3.0m)
        val loc2 = createMockFix(lat = 52.52002, lon = 13.40, timeMs = 2000L, speed = null, accuracy = 4.0f)
        every { loc1.distanceTo(loc2) } returns 2.0f
        device.onNewLocation(loc2)

        assertTrue("Must remain pinned when displacement is within 3.0m deadband", device.isStationaryPinned)
        assertEquals("Speed must remain 0.0 for non-Doppler jitter within deadband", 0.0, device.getSpeedSensor().value ?: -1.0, 0.001)
        assertEquals("Distance must NOT accumulate", 0.0, device.getDistanceSensor().value ?: -1.0, 0.001)

        // Fix 3: Displacement of 8.0m in 1s (> 3.0m deadband)
        val loc3 = createMockFix(lat = 52.52008, lon = 13.40, timeMs = 3000L, speed = null, accuracy = 4.0f)
        every { loc1.distanceTo(loc3) } returns 8.0f
        every { loc2.distanceTo(loc3) } returns 6.0f
        device.onNewLocation(loc3)

        assertFalse("Must unpin when non-Doppler displacement exceeds deadband", device.isStationaryPinned)
        // Geometric speed: 8.0m / 1s = 8.0 m/s, smoothed with initial 0.0: (0.0 + 8.0) / 2 = 4.0 m/s
        assertEquals(4.0, device.getSpeedSensor().value ?: 0.0, 0.001)
        assertEquals(8.0, device.getDistanceSensor().value ?: 0.0, 0.001)
    }

    /**
     * TST-TRK-048.4: Lifecycle resets clear stationary anchor state.
     */
    @Test
    fun testLifecycleResets_clearStationaryAnchor() {
        val loc1 = createMockFix(lat = 48.0, lon = 11.0, timeMs = 1000L, speed = 0.0f)
        device.onNewLocation(loc1)
        assertTrue(device.isStationaryPinned)
        assertNotNull(device.getStationaryAnchor())

        // Reset accumulators
        device.callAccumulatorsReset()
        assertFalse(device.isStationaryPinned)
        assertNull(device.getStationaryAnchor())

        // Re-pin on new stationary fix
        val loc2 = createMockFix(lat = 48.1, lon = 11.1, timeMs = 2000L, speed = 0.0f)
        device.onNewLocation(loc2)
        assertTrue(device.isStationaryPinned)

        // Shutdown device
        device.shutDown()
        assertFalse(device.isStationaryPinned)
        assertNull(device.getStationaryAnchor())
    }

    /**
     * TST-TRK-048.5: Doppler stationary fix clamps non-zero moving speed to 0.0 immediately upon stopping.
     */
    @Test
    fun testStoppingAtRedLight_immediatelyClampsSpeedToZeroAndPins() {
        // Step 1: Moving fix at 10.0 m/s
        val loc1 = createMockFix(lat = 48.0, lon = 11.0, timeMs = 1000L, speed = 10.0f)
        device.onNewLocation(loc1)
        assertEquals(10.0, device.getSpeedSensor().value ?: 0.0, 0.001)

        // Step 2: Athlete halts at red light (Doppler speed = 0.0f)
        val loc2 = createMockFix(lat = 48.00008, lon = 11.0, timeMs = 2000L, speed = 0.0f, bearing = 45.0f)
        every { loc1.distanceTo(loc2) } returns 8.0f
        device.onNewLocation(loc2)

        assertTrue("Must pin immediately upon Doppler zero speed report", device.isStationaryPinned)
        assertEquals("Speed must clamp to 0.0 immediately without lingering lag", 0.0, device.getSpeedSensor().value ?: -1.0, 0.001)
        assertNull("Pace must be null", device.getPaceSensor().value)

        // Step 3: Multipath jitter while waiting at light
        val loc3 = createMockFix(lat = 48.00010, lon = 11.0, timeMs = 3000L, speed = 0.0f, bearing = 310.0f)
        every { loc2.distanceTo(loc3) } returns 2.0f
        device.onNewLocation(loc3)

        assertTrue(device.isStationaryPinned)
        assertEquals(0.0, device.getSpeedSensor().value ?: -1.0, 0.001)
        assertEquals(45.0, device.getBearingSensor().value ?: -1.0, 0.001) // Bearing locked at 45.0
    }

    /**
     * TST-TRK-048.2 (Edge Case): Buggy GNSS provider reports hasSpeed()==true and speed==0.0f
     * but athlete is moving far beyond accuracy noise floor (escape radius). Device must unpin and compute geometric speed.
     */
    @Test
    fun testBuggyProvider_stuckZeroSpeed_unpinsWhenDisplacementExceedsEscapeRadius() {
        // Fix 1: Stationary anchor (accuracy = 4.0m -> escapeRadius = max(3.0, 1.5 * 4.0) = 6.0m)
        val loc1 = createMockFix(lat = 48.0, lon = 11.0, timeMs = 1000L, speed = 0.0f, accuracy = 4.0f)
        device.onNewLocation(loc1)
        assertTrue(device.isStationaryPinned)

        // Fix 2: Jitter 2.0m within deadband
        val loc2 = createMockFix(lat = 48.00002, lon = 11.0, timeMs = 2000L, speed = 0.0f, accuracy = 4.0f)
        every { loc1.distanceTo(loc2) } returns 2.0f
        device.onNewLocation(loc2)
        assertTrue(device.isStationaryPinned)

        // Fix 3: Buggy provider keeps reporting speed = 0.0f, but athlete has cycled 12m away (> 6.0m escape radius)
        val loc3 = createMockFix(lat = 48.00012, lon = 11.0, timeMs = 3000L, speed = 0.0f, accuracy = 4.0f)
        every { loc1.distanceTo(loc3) } returns 12.0f
        every { loc2.distanceTo(loc3) } returns 10.0f
        device.onNewLocation(loc3)

        assertFalse("Must unpin when displacement exceeds escape radius even if Doppler speed is stuck at 0.0f", device.isStationaryPinned)
        // Geometric speed: 12.0m / 1s = 12.0 m/s, smoothed with 0.0 -> (0.0 + 12.0) / 2 = 6.0 m/s
        assertEquals(6.0, device.getSpeedSensor().value ?: 0.0, 0.001)
        assertEquals(12.0, device.getDistanceSensor().value ?: 0.0, 0.001)
    }
}
