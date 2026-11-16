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
}
