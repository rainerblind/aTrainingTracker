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
        every { AttFusedAltitudePreferences.getEffectiveState(any()) } answers {
            AttFusedAltitudePreferences.EffectiveState(
                isBarometerActive = AttFusedAltitudePreferences.isEffectiveBarometerActive(firstArg()),
                isDemActive = AttFusedAltitudePreferences.isEffectiveDemActive(firstArg()),
                isGpsActive = AttFusedAltitudePreferences.isEffectiveGpsActive(firstArg()),
                isSpeedActive = AttFusedAltitudePreferences.isEffectiveSpeedActive(firstArg())
            )
        }
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

    /**
     * Finding 1: Caches preference and pairing states in memory; refreshes via snapshot.
     */
    @Test
    fun testFinding1_cachesPreferenceAndPairingStates_inMemory() {
        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)

        assertTrue(device.isEffectiveBarometer)
        assertTrue(device.isEffectiveDem)
        assertFalse(device.isEffectiveGps)
        assertTrue(device.isEffectiveSpeed)

        every { AttFusedAltitudePreferences.isEffectiveDemActive(any()) } returns false
        every { AttFusedAltitudePreferences.isEffectiveGpsActive(any()) } returns true
        device.refreshCachedPreferences()

        assertFalse(device.isEffectiveDem)
        assertTrue(device.isEffectiveGps)
    }

    /**
     * Finding 2: Concurrency safety across calculateMetrics() and handlePressureMeasurement().
     */
    @Test
    fun testFinding2_concurrencyBetweenMetricsAndPressureMeasurement() {
        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        val speedSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { speedSensor.value } returns 4.0
        every { mockSensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        device.handleReferenceAltitude(100.0, isDem = true)

        val threads = mutableListOf<Thread>()
        var errorOccurred = false

        repeat(4) { t ->
            val thread = Thread {
                try {
                    repeat(100) { i ->
                        if (t % 2 == 0) {
                            device.calculateMetrics(1000L + i * 200L)
                        } else {
                            val pressure = 1013.25f - (i * 0.1f)
                            device.handlePressureMeasurement(pressure, 1000L + i * 200L)
                        }
                    }
                } catch (e: Exception) {
                    errorOccurred = true
                }
            }
            threads.add(thread)
            thread.start()
        }

        threads.forEach { it.join() }
        assertFalse("Concurrent execution must not throw exceptions", errorOccurred)
        assertTrue("Altitude must remain finite and positive", (device.mAltitudeSensor?.value as Number).toDouble() > 0.0)
    }

    /**
     * Finding 3: DEM reference altitude is ingested ONLY when demSensor.lastUpdateTimestamp > mLastDemUpdateTimestampMs.
     * Prevents static DEM over-ingestion from collapsing P00 covariance.
     */
    @Test
    fun testFinding3_demTimestampGating_preventsCovarianceCollapse() {
        val demSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { demSensor.value } returns 400.0
        every { demSensor.lastUpdateTimestamp } returns 1000L
        every {
            mockSensorManager.getSensorForDeviceType(SensorType.ALTITUDE, DeviceType.ALTITUDE_FROM_POSITION)
        } returns demSensor

        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.handlePressureMeasurement(1013.25f, 1000L)

        // First calculateMetrics: ingests DEM at timestamp 1000L
        device.calculateMetrics(1000L)
        assertEquals(1000L, device.lastDemUpdateTimestampMs)
        val p00AfterFirstIngest = device.ekf.p00

        // 10 subsequent calculateMetrics with SAME timestamp 1000L (static DEM)
        repeat(10) { i ->
            device.calculateMetrics(2000L + i * 1000L)
        }

        // P00 should NOT have collapsed down to 1e-6 because static DEM was ignored!
        assertTrue("P00 must not collapse on static DEM (was ${device.ekf.p00})", device.ekf.p00 >= p00AfterFirstIngest * 0.5)

        // Now new DEM fix arrives at timestamp 25000L with 410m
        every { demSensor.value } returns 410.0
        every { demSensor.lastUpdateTimestamp } returns 25000L

        device.calculateMetrics(25000L)
        assertEquals(25000L, device.lastDemUpdateTimestampMs)
    }

    /**
     * Finding 4: Ingests GPS elevation in 3-State mode when isEffectiveGpsActive is enabled.
     */
    @Test
    fun testFinding4_gpsElevationIngestion_whenGpsActive() {
        every { AttFusedAltitudePreferences.isEffectiveDemActive(any()) } returns false
        every { AttFusedAltitudePreferences.isEffectiveGpsActive(any()) } returns true

        val gpsSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { gpsSensor.value } returns 600.0
        every { gpsSensor.lastUpdateTimestamp } returns 5000L
        every {
            mockSensorManager.getSensorForDeviceType(SensorType.ALTITUDE, DeviceType.SPEED_AND_LOCATION_GPS)
        } returns gpsSensor

        val speedSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { speedSensor.value } returns 5.0
        every { mockSensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.refreshCachedPreferences()

        assertTrue("EKF must be in 3-State mode when GPS is active", device.ekf.is3StateMode)

        // Initialize at 590m (within 3-sigma of GPS 600m to avoid Huber gate rejection)
        val pressure590m = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
            Math.pow(1.0 - 590.0 / 44330.0, 5.255).toFloat()
        device.handlePressureMeasurement(pressure590m, 4000L)
        device.calculateMetrics(5000L)

        assertEquals("GPS timestamp must be recorded", 5000L, device.lastGpsUpdateTimestampMs)
        // With R_gps = 16.0 and P00 ~ 0.25, Kalman gain K0 ~ 0.0154, updating elevation by ~+0.15m towards 600m
        assertTrue("Altitude should adjust towards GPS 600m (was ${device.ekf.altitude})", device.ekf.altitude > 590.1)
    }

    /**
     * Finding 5: Workout pause updates mLastStepTimestampMs and clamps dtSec to MAX_DT_SEC (5.0s).
     */
    @Test
    fun testFinding5_workoutPause_clampsDtSecAndPreventsSpike() {
        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.handleReferenceAltitude(250.0, isDem = true)

        // Pause workout
        every { TrainingApplication.isPaused() } returns true
        device.calculateMetrics(1000L)

        val p00BeforeResume = device.ekf.p00

        // Resume after 30 minutes (1800s pause)
        every { TrainingApplication.isPaused() } returns false
        device.calculateMetrics(1801000L)

        // dtSec must have been clamped to 5.0s, preventing massive covariance explosion
        assertTrue("Covariance growth must be bounded by MAX_DT_SEC", device.ekf.p00 < p00BeforeResume + 1.0)
    }

    // TST-FIL-020.1: DEM reference precedence over unnormalized WGS84 GPS altitude
    @Test
    fun testDemReferencePrecedence_overridesGpsAltitude_tstFil020_1() {
        every { AttFusedAltitudePreferences.isEffectiveDemActive(any()) } returns true
        every { AttFusedAltitudePreferences.isEffectiveGpsActive(any()) } returns true

        val demSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { demSensor.value } returns 500.0
        every { demSensor.lastUpdateTimestamp } returns 5000L
        every {
            mockSensorManager.getSensorForDeviceType(SensorType.ALTITUDE, DeviceType.ALTITUDE_FROM_POSITION)
        } returns demSensor

        val gpsSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { gpsSensor.value } returns 547.5 // WGS84 ellipsoid offset by +47.5m!
        every { gpsSensor.lastUpdateTimestamp } returns 5000L
        every {
            mockSensorManager.getSensorForDeviceType(SensorType.ALTITUDE, DeviceType.SPEED_AND_LOCATION_GPS)
        } returns gpsSensor

        val speedSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { speedSensor.value } returns 5.0
        every { mockSensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.refreshCachedPreferences()

        val pressure500m = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
            Math.pow(1.0 - 500.0 / 44330.0, 5.255).toFloat()
        device.handlePressureMeasurement(pressure500m, 4000L)
        device.calculateMetrics(5000L)

        // Assert DEM was ingested and GPS was bypassed
        assertEquals("DEM timestamp must be recorded", 5000L, device.lastDemUpdateTimestampMs)
        assertEquals("GPS update must be bypassed due to DEM precedence", 0L, device.lastGpsUpdateTimestampMs)
    }

    // TST-FIL-020.2: 5-Hz barometric micro-ripple hysteresis deadband rejection
    @Test
    fun testBarometricMicroRippleHysteresisRejection_tstFil020_2() {
        every { AttFusedAltitudePreferences.isEffectiveDemActive(any()) } returns false
        every { AttFusedAltitudePreferences.isEffectiveGpsActive(any()) } returns false

        val speedSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { speedSensor.value } returns 5.0
        every { mockSensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.refreshCachedPreferences()

        val basePressure = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
            Math.pow(1.0 - 500.0 / 44330.0, 5.255).toFloat()
        device.handlePressureMeasurement(basePressure, 1000L)
        device.calculateMetrics(1000L)

        // Feed 300 5-Hz samples (60 seconds) with micro-ripples <= ±0.4m
        var tMs = 1200L
        for (i in 0 until 300) {
            val rippleAlt = 500.0 + 0.35 * kotlin.math.sin(i * 0.5)
            val p = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
                Math.pow(1.0 - rippleAlt / 44330.0, 5.255).toFloat()
            device.handlePressureMeasurement(p, tMs)
            if (i % 5 == 0) {
                device.calculateMetrics(tMs)
            }
            tMs += 200L
        }

        // Hysteresis deadband (2.0m) must reject 100% of micro-ripples
        val ascent = (device.mAscentSensor?.value as? Number)?.toDouble() ?: 0.0
        val descent = (device.mDescentSensor?.value as? Number)?.toDouble() ?: 0.0
        assertEquals("Ascent must remain exactly 0.0m on micro-ripples", 0.0, ascent, 1e-6)
        assertEquals("Descent must remain exactly 0.0m on micro-ripples", 0.0, descent, 1e-6)
    }

    // TST-FIL-020.3: True terrain climbing ascent accumulation with hysteresis
    @Test
    fun testTerrainClimbAccumulation_withHysteresis_tstFil020_3() {
        every { AttFusedAltitudePreferences.isEffectiveDemActive(any()) } returns false
        every { AttFusedAltitudePreferences.isEffectiveGpsActive(any()) } returns false

        val speedSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { speedSensor.value } returns 5.0
        every { mockSensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.refreshCachedPreferences()

        val basePressure = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
            Math.pow(1.0 - 500.0 / 44330.0, 5.255).toFloat()
        device.handlePressureMeasurement(basePressure, 1000L)
        device.calculateMetrics(1000L)

        // Climb ~25m from 500m over 30 seconds (+0.84m/s, within physical slew limit)
        var currentAlt = 500.0
        var tMs = 2000L
        for (i in 0 until 30) {
            currentAlt += 0.84
            val p = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
                Math.pow(1.0 - currentAlt / 44330.0, 5.255).toFloat()
            device.handlePressureMeasurement(p, tMs)
            device.calculateMetrics(tMs)
            tMs += 1000L
        }

        val ascent = (device.mAscentSensor?.value as? Number)?.toDouble() ?: 0.0
        assertTrue("Cumulative ascent must reflect the climb (was $ascent)", ascent in 22.0..26.0)
    }

    // TST-FIL-020.4: Locale-invariant technical device identifier
    @Test
    fun testTechnicalDeviceName_localeInvariant_tstFil020_4() {
        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        assertEquals(
            "Device name must be canonical technical identifier",
            AltitudeAndSlopeDevice_ATTFused.TECHNICAL_DEVICE_NAME,
            device.name
        )
        assertEquals("att_fused_altitude_slope", device.name)
    }

    // TST-FIL-022.3: Initial 3-state DEM calibration jump resets hysteresis anchor (0.0 m bogus descent)
    @Test
    fun testCalibrationJumpAscentDescentGuard_zeroBogusDescentAndRealClimbAfterward_tstFil022_3() {
        every { AttFusedAltitudePreferences.isEffectiveDemActive(any()) } returns true
        every { AttFusedAltitudePreferences.isEffectiveGpsActive(any()) } returns false

        val speedSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { speedSensor.value } returns 0.0 // Standstill at start
        every { mockSensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        val demSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { demSensor.value } returns 438.0
        every { demSensor.lastUpdateTimestamp } returns 5000L
        every {
            mockSensorManager.getSensorForDeviceType(SensorType.ALTITUDE, DeviceType.ALTITUDE_FROM_POSITION)
        } returns demSensor

        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.refreshCachedPreferences()

        // Early barometer arrives at 568.4 m (+130.4 m weather offset) before DEM lock
        val p568 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
            Math.pow(1.0 - 568.4 / 44330.0, 5.255).toFloat()
        repeat(5) { idx ->
            device.handlePressureMeasurement(p568, 1000L + idx * 200L)
        }
        assertEquals(568.4, (device.mAltitudeSensor?.value as Number).toDouble(), 1.0)

        // First DEM fix (438.0 m) arrives at calculateMetrics(5000L)
        device.calculateMetrics(5000L)

        assertEquals("Fused altitude must anchor at 438.0 m DEM", 438.0, (device.mAltitudeSensor?.value as Number).toDouble(), 0.5)
        assertEquals(
            "Initial -130.4 m calibration jump must add 0.0 m bogus ascent",
            0.0,
            (device.mAscentSensor?.value as? Number)?.toDouble() ?: 0.0,
            1e-6
        )
        assertEquals(
            "Initial -130.4 m calibration jump must add 0.0 m bogus descent",
            0.0,
            (device.mDescentSensor?.value as? Number)?.toDouble() ?: 0.0,
            1e-6
        )

        // Now ride uphill +25 m (438 -> 463 m) at 5 m/s (+0.83 m/s within slew limit)
        every { speedSensor.value } returns 5.0
        var baroAlt = 568.4
        var trueAlt = 438.0
        var tMs = 6000L
        for (i in 1..30) {
            baroAlt += 25.0 / 30.0
            trueAlt += 25.0 / 30.0
            val p = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
                Math.pow(1.0 - baroAlt / 44330.0, 5.255).toFloat()
            every { demSensor.value } returns trueAlt
            every { demSensor.lastUpdateTimestamp } returns tMs
            device.handlePressureMeasurement(p, tMs)
            device.calculateMetrics(tMs)
            tMs += 1000L
        }

        val ascentAfterClimb = (device.mAscentSensor?.value as? Number)?.toDouble() ?: 0.0
        val descentAfterClimb = (device.mDescentSensor?.value as? Number)?.toDouble() ?: 0.0
        assertTrue("Post-lock real 25 m climb must accumulate ~25 m ascent (was $ascentAfterClimb)", ascentAfterClimb in 22.0..27.0)
        assertEquals("Descent must stay 0.0 m during pure climb", 0.0, descentAfterClimb, 1e-6)
    }

    // TST-FIL-023.2: Pre-stop -21% slope clears to 0% on sustained standstill instead of staying frozen
    @Test
    fun testStandstillSlopeClearing_publishesZeroInsteadOfFrozenNegativeSlope_tstFil023_2() {
        every { AttFusedAltitudePreferences.isEffectiveDemActive(any()) } returns true
        every { AttFusedAltitudePreferences.isEffectiveGpsActive(any()) } returns false

        val speedSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { speedSensor.value } returns 6.0
        every { mockSensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        val demSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        var currentAlt = 500.0
        every { demSensor.value } answers { currentAlt }
        var demTs = 1000L
        every { demSensor.lastUpdateTimestamp } answers { demTs }
        every {
            mockSensorManager.getSensorForDeviceType(SensorType.ALTITUDE, DeviceType.ALTITUDE_FROM_POSITION)
        } returns demSensor

        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.refreshCachedPreferences()
        device.calculateMetrics(1000L)

        // Ride downhill -20% at 6.0 m/s for 20s
        var tMs = 2000L
        for (i in 1..20) {
            currentAlt -= 1.2
            demTs = tMs
            val p = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
                Math.pow(1.0 - currentAlt / 44330.0, 5.255).toFloat()
            device.handlePressureMeasurement(p, tMs)
            device.calculateMetrics(tMs)
            tMs += 1000L
        }
        val slopeBeforeStop = device.mSlopeSensor?.value ?: 0
        assertTrue("Slope before stop must be strongly negative (was $slopeBeforeStop%)", slopeBeforeStop <= -10)

        val ascentAtStop = (device.mAscentSensor?.value as? Number)?.toDouble() ?: 0.0
        val descentAtStop = (device.mDescentSensor?.value as? Number)?.toDouble() ?: 0.0

        // Stop for 13 seconds (t=200..213s scenario)
        every { speedSensor.value } returns 0.0
        val pStop = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
            Math.pow(1.0 - currentAlt / 44330.0, 5.255).toFloat()
        for (i in 1..13) {
            device.handlePressureMeasurement(pStop, tMs)
            device.calculateMetrics(tMs)
            tMs += 1000L
        }

        assertEquals("Slope must clear to 0% at sustained stop", 0, device.mSlopeSensor?.value)
        assertEquals(ascentAtStop, (device.mAscentSensor?.value as? Number)?.toDouble() ?: 0.0, 1e-6)
        assertEquals(descentAtStop, (device.mDescentSensor?.value as? Number)?.toDouble() ?: 0.0, 1e-6)
    }

    // TST-FIL-023.3: Walking-speed roll-away (0.85 m/s) prevents +29% slope & ascent inflation while keeping +8% climbs
    @Test
    fun testLowSpeedRollAway_suppressesSlopeSpikeAndAscentInflation_tstFil023_3() {
        every { AttFusedAltitudePreferences.isEffectiveDemActive(any()) } returns true
        every { AttFusedAltitudePreferences.isEffectiveGpsActive(any()) } returns false

        val speedSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { speedSensor.value } returns 0.85
        every { mockSensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        val demSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        var demAlt = 442.0
        every { demSensor.value } answers { demAlt }
        var demTs = 1000L
        every { demSensor.lastUpdateTimestamp } answers { demTs }
        every {
            mockSensorManager.getSensorForDeviceType(SensorType.ALTITUDE, DeviceType.ALTITUDE_FROM_POSITION)
        } returns demSensor

        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.refreshCachedPreferences()
        device.calculateMetrics(1000L)

        // Roll away at 0.85 m/s for 30s on flat 442m DEM with +/- 0.35m 5-Hz baro chatter
        var maxAbsSlope = 0
        var tMs = 2000L
        for (sec in 0 until 30) {
            for (sub in 0 until 5) {
                val baroAlt = 442.0 + 0.35 * kotlin.math.sin((sec * 5 + sub) * 0.9)
                val p = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
                    Math.pow(1.0 - baroAlt / 44330.0, 5.255).toFloat()
                device.handlePressureMeasurement(p, tMs + sub * 200L)
                maxAbsSlope = maxOf(maxAbsSlope, kotlin.math.abs(device.mSlopeSensor?.value ?: 0))
            }
            demTs = tMs
            device.calculateMetrics(tMs)
            maxAbsSlope = maxOf(maxAbsSlope, kotlin.math.abs(device.mSlopeSensor?.value ?: 0))
            tMs += 1000L
        }

        val ascentAfterFlatWalk = (device.mAscentSensor?.value as? Number)?.toDouble() ?: 0.0
        assertTrue("Flat low-speed roll-away must not spike slope (max was $maxAbsSlope%)", maxAbsSlope < 10)
        assertTrue("Flat low-speed roll-away must not inflate ascent (was $ascentAfterFlatWalk m)", ascentAfterFlatWalk < 2.0)

        // Transition to genuine +8% climb at 5.0 m/s (+0.4 m/s) for 60s -> ~24 m ascent
        every { speedSensor.value } returns 5.0
        for (sec in 1..60) {
            demAlt += 0.4
            demTs = tMs
            val p = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
                Math.pow(1.0 - demAlt / 44330.0, 5.255).toFloat()
            device.handlePressureMeasurement(p, tMs)
            device.calculateMetrics(tMs)
            tMs += 1000L
        }

        val ascentAfterClimb = (device.mAscentSensor?.value as? Number)?.toDouble() ?: 0.0
        val slopeOnClimb = device.mSlopeSensor?.value ?: 0
        assertTrue("Real +8% climb must accumulate ~24 m ascent (was $ascentAfterClimb)", ascentAfterClimb in 21.0..27.0)
        assertTrue("Real +8% climb must report positive slope 5..11% (was $slopeOnClimb%)", slopeOnClimb in 5..11)
    }

    // TST-FIL-026.1: 5-Hz jeans-pocket pedaling oscillations (±1.2 m) + ±8 m pulses on flat 438 m DEM keep slope within ±2% and ascent/descent 0.0 m
    @Test
    fun testFiveHzJeansPocketPedalingOscillations_rateMatchedAndSlopeBounded_tstFil026_1() {
        every { AttFusedAltitudePreferences.isEffectiveDemActive(any()) } returns true
        every { AttFusedAltitudePreferences.isEffectiveGpsActive(any()) } returns false

        val speedSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { speedSensor.value } returns 6.0
        every { mockSensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        val demSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        val flatDemAlt = 438.0
        every { demSensor.value } returns flatDemAlt
        var demTs = 1000L
        every { demSensor.lastUpdateTimestamp } answers { demTs }
        every {
            mockSensorManager.getSensorForDeviceType(SensorType.ALTITUDE, DeviceType.ALTITUDE_FROM_POSITION)
        } returns demSensor

        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.refreshCachedPreferences()
        device.calculateMetrics(1000L)

        var maxAbsSlopeAfterWarmup = 0
        var tMs = 2000L
        for (sec in 0 until 60) {
            for (sub in 0 until 5) {
                val tSec = sec + sub * 0.2
                val pedalingRipple = 1.2 * kotlin.math.sin(2.0 * Math.PI * 1.25 * tSec)
                val pulse = when (sec) {
                    20 -> -8.05
                    21 -> 6.87
                    40 -> 4.11
                    41 -> -4.84
                    else -> 0.0
                }
                val baroAlt = flatDemAlt + pedalingRipple + pulse
                val p = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
                    Math.pow(1.0 - baroAlt / 44330.0, 5.255).toFloat()
                device.handlePressureMeasurement(p, tMs + sub * 200L)
            }
            demTs = tMs
            device.calculateMetrics(tMs)
            if (sec >= 5) {
                maxAbsSlopeAfterWarmup = maxOf(maxAbsSlopeAfterWarmup, kotlin.math.abs(device.mSlopeSensor?.value ?: 0))
            }
            tMs += 1000L
        }

        val ascent = (device.mAscentSensor?.value as? Number)?.toDouble() ?: 0.0
        val descent = (device.mDescentSensor?.value as? Number)?.toDouble() ?: 0.0
        assertTrue("Flat-road slope under 5-Hz pocket jitter must stay within ±2% (max was $maxAbsSlopeAfterWarmup%)", maxAbsSlopeAfterWarmup <= 2)
        assertEquals("Flat-road ascent must remain 0.0 m (was $ascent)", 0.0, ascent, 1e-6)
        assertEquals("Flat-road descent must remain 0.0 m (was $descent)", 0.0, descent, 1e-6)
        assertEquals("Fused altitude must stay anchored near 438.0 m", flatDemAlt, device.ekf.altitude, 1.5)
    }

    // TST-FIL-026.1: Synthetic 2 km Zum Bäcker #57 replay (435..446 m envelope, 5-Hz ±1.2 m jitter & ±8 m pulses) keeps totals in 10..16 m
    @Test
    fun testSyntheticZumBaecker57Replay_preventsAscentInflation_tstFil026_1() {
        every { AttFusedAltitudePreferences.isEffectiveDemActive(any()) } returns true
        every { AttFusedAltitudePreferences.isEffectiveGpsActive(any()) } returns false

        val speedSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        every { speedSensor.value } returns 6.0
        every { mockSensorManager.getSensor(SensorType.SPEED_mps) } returns speedSensor

        val demSensor = mockk<com.atrainingtracker.banalservice.sensor.MySensor<Number>>(relaxed = true)
        var demAlt = 438.0
        every { demSensor.value } answers { demAlt }
        var demTs = 1000L
        every { demSensor.lastUpdateTimestamp } answers { demTs }
        every {
            mockSensorManager.getSensorForDeviceType(SensorType.ALTITUDE, DeviceType.ALTITUDE_FROM_POSITION)
        } returns demSensor

        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)
        device.refreshCachedPreferences()
        device.calculateMetrics(1000L)

        var maxFlatSlopePct = 0
        var tMs = 2000L
        // 334 s at 6.0 m/s ≈ 2,004 m loop within 435..446 m DEM envelope (+17 m / -17 m terrain -> 10..16 m post-hysteresis)
        for (sec in 0 until 334) {
            demAlt = when {
                sec < 85 -> 438.0
                sec < 140 -> 438.0 + 8.0 * ((sec - 85) / 55.0) // +8 m -> 446 m
                sec < 210 -> 446.0 - 11.0 * ((sec - 140) / 70.0) // -11 m -> 435 m
                sec < 275 -> 435.0 + 9.0 * ((sec - 210) / 65.0) // +9 m -> 444 m
                else -> 444.0 - 6.0 * ((sec - 275) / 59.0) // -6 m -> 438 m
            }
            for (sub in 0 until 5) {
                val tSec = sec + sub * 0.2
                val pedalingRipple = 1.2 * kotlin.math.sin(2.0 * Math.PI * 1.25 * tSec) +
                    0.35 * kotlin.math.sin(2.0 * Math.PI * 0.35 * tSec)
                val pulse = when (sec) {
                    50 -> -8.05
                    51 -> 6.87
                    185 -> 4.11
                    186 -> -4.84
                    255 -> -6.5
                    else -> 0.0
                }
                val baroAlt = demAlt + 130.4 + pedalingRipple + pulse
                val p = SensorManager.PRESSURE_STANDARD_ATMOSPHERE *
                    Math.pow(1.0 - baroAlt / 44330.0, 5.255).toFloat()
                device.handlePressureMeasurement(p, tMs + sub * 200L)
            }
            demTs = tMs
            device.calculateMetrics(tMs)
            if (sec in 31..84) {
                maxFlatSlopePct = maxOf(maxFlatSlopePct, kotlin.math.abs(device.mSlopeSensor?.value ?: 0))
            }
            tMs += 1000L
        }

        val totalAscent = (device.mAscentSensor?.value as? Number)?.toDouble() ?: 0.0
        val totalDescent = (device.mDescentSensor?.value as? Number)?.toDouble() ?: 0.0
        assertTrue("Flat segment (t=31..85s) slope must stay within ±2% (max was $maxFlatSlopePct%)", maxFlatSlopePct <= 2)
        assertTrue("2 km replay ascent must stay within 10..16 m (was $totalAscent m)", totalAscent in 10.0..16.0)
        assertTrue("2 km replay descent must stay within 10..16 m (was $totalDescent m)", totalDescent in 10.0..16.0)
    }

    // TST-FIL-026.3: shutDown() clears pending sub-second aggregator buffer cleanly across restarts
    @Test
    fun testShutDown_clearsSubSecondBaroBucket_acrossRestart_tstFil026_3() {
        val device = AltitudeAndSlopeDevice_ATTFused(mockContext, mockSensorManager, autoStartScheduler = false)

        val p500 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE * Math.pow(1.0 - 500.0 / 44330.0, 5.255).toFloat()
        val p520 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE * Math.pow(1.0 - 520.0 / 44330.0, 5.255).toFloat()
        val p400 = SensorManager.PRESSURE_STANDARD_ATMOSPHERE * Math.pow(1.0 - 400.0 / 44330.0, 5.255).toFloat()

        device.handlePressureMeasurement(p500, 1000L)
        assertTrue(device.ekf.isInitialized)
        // Buffer 4 sub-second samples at 520 m without flushing
        repeat(4) { idx ->
            device.handlePressureMeasurement(p520, 1200L + idx * 200L)
        }

        device.shutDown()
        assertFalse("shutDown must reset EKF", device.ekf.isInitialized)

        // Restart at 400 m: immediate 1st-sample init with zero leakage from pre-shutdown 520 m bucket
        device.handlePressureMeasurement(p400, 2000L)
        assertTrue(device.ekf.isInitialized)
        assertEquals(400.0, device.ekf.altitude, 1.0)
        assertEquals(400.0, device.baroFilter.getSlewAltitude(), 1.0)
    }
}
