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
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.filters.FilterData
import com.atrainingtracker.banalservice.filters.FilterManager
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.ProxySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying stabilized VAM calculation, linear regression smoothing,
 * stationary gating, flat road deadband, and outlier clamping (REQ-FIL-012, TST-FIL-004, ATT-1621).
 */
class VerticalSpeedAndSlopeDeviceTest {

    private val windowSize = VerticalSpeedAndSlopeDevice.VAM_WINDOW_SIZE // 15

    @After
    fun tearDown() {
        BANALService.setFilterManager(null)
        unmockkAll()
    }

    @Test
    fun testCalculateLinearRegressionSlope_withLinearRamp_computesExactSlope() {
        val history = DoubleArray(windowSize)
        val rateOfAscentMps = 0.2222 // ~800 m/h
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (i * rateOfAscentMps)
        }

        val slope = VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, windowSize, 0)
        assertEquals(rateOfAscentMps, slope, 0.001)
    }

    @Test
    fun testCalculateLinearRegressionSlope_insufficientSamples_returnsZero() {
        val history = DoubleArray(windowSize) { 100.0 }
        assertEquals(0.0, VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, 0, 0), 0.0001)
        assertEquals(0.0, VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, 1, 1), 0.0001)
        assertEquals(0.0, VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(null, 5, 0), 0.0001)
    }

    @Test
    fun testCalculateLinearRegressionSlope_warmup_computesImmediateDynamicSlope() {
        val history = DoubleArray(windowSize)
        history[0] = 100.0
        history[1] = 100.5 // 0.5 m/s delta across 2 samples

        val slope2 = VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, 2, 2)
        assertEquals(0.5, slope2, 0.001)

        history[2] = 101.0 // 0.5 m/s across 3 samples
        val slope3 = VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, 3, 3)
        assertEquals(0.5, slope3, 0.001)
    }

    @Test
    fun testCalculateVam_stationaryIndoorNoise_suppressedToZero() {
        val history = DoubleArray(windowSize)
        // Oscillating altitude representing stationary indoor/basement GPS noise (+-0.03 m/s = +-108 m/h)
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (if (i % 2 == 0) 0.15 else -0.15)
        }

        // Speed is 0.0 m/s (stationary), minSpeed is 0.5 m/s
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 0.0, 0.5)
        assertEquals("Stationary noise below 150 m/h must be clamped to 0", 0, vam)
    }

    @Test
    fun testCalculateVam_stationaryElevator_preservesGenuineAscent() {
        val history = DoubleArray(windowSize)
        val climbRateMps = 0.5 // 1800 m/h
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (i * climbRateMps)
        }

        // Horizontal speed is 0.0 m/s (e.g. elevator or ski lift), but vertical movement is genuine (1800 m/h >= 150 m/h)
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 0.0, 0.5)
        assertEquals(1800, vam)
    }

    @Test
    fun testCalculateVam_steadyClimb_convergesAccurately() {
        val history = DoubleArray(windowSize)
        val targetVam = 800.0 // 800 m/h
        val mps = targetVam / 3600.0
        for (i in 0 until windowSize) {
            history[i] = 250.0 + (i * mps)
        }

        // Moving at 5.0 m/s (> minSpeed 0.5 m/s)
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 5.0, 0.5)
        assertEquals(800, vam)
    }

    @Test
    fun testCalculateVam_flatRoadDeadband_clampsMicroFluctuationsToZero() {
        val history = DoubleArray(windowSize)
        // Tiny barometric drift of 0.005 m/s (~18 m/h < 35 m/h deadband)
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (i * 0.005)
        }

        // Moving at 8.0 m/s on flat road
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 8.0, 0.5)
        assertEquals("Micro-fluctuations below 35 m/h must clamp to 0", 0, vam)
    }

    @Test
    fun testCalculateVam_steadyDescent_convergesToNegativeVam() {
        val history = DoubleArray(windowSize)
        val targetVam = -600.0 // -600 m/h
        val mps = targetVam / 3600.0
        for (i in 0 until windowSize) {
            history[i] = 500.0 + (i * mps)
        }

        // Moving at 10.0 m/s downhill
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 10.0, 0.5)
        assertEquals(-600, vam)
    }

    @Test
    fun testCalculateVam_glitchOutlierClamping_boundsExtremeJumps() {
        val history = DoubleArray(windowSize)
        // Extreme positive anomaly: +20 m/s = 72,000 m/h
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (i * 20.0)
        }

        val vamMax = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 5.0, 0.5)
        assertEquals("Extreme jump must be clamped to +3000 m/h", 3000, vamMax)

        // Extreme negative anomaly: -20 m/s = -72,000 m/h
        for (i in 0 until windowSize) {
            history[i] = 500.0 - (i * 20.0)
        }
        val vamMin = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 5.0, 0.5)
        assertEquals("Extreme negative jump must be clamped to -3000 m/h", -3000, vamMin)
    }

    @Test
    fun testCircularBufferWrapping_maintainsCorrectChronologicalOrder() {
        val history = DoubleArray(windowSize)
        val rateMps = 0.25 // 900 m/h

        // Simulate 30 samples inserted into size 15 ring buffer
        var head = 0
        for (step in 0 until 30) {
            history[head] = step * rateMps
            head = (head + 1) % windowSize
        }

        // History count is full windowSize (15)
        val slope = VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, windowSize, head)
        assertEquals(rateMps, slope, 0.001)

        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, head, 6.0, 0.5)
        assertEquals(900, vam)
    }

    @Test
    fun testCalculateVam_degradedGpsAccuracy_suppressesVamToZero() {
        val history = DoubleArray(windowSize)
        val rateMps = 0.5 // 1800 m/h climb ramp
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (i * rateMps)
        }

        // Horizontal speed is 2.0 m/s (moving), but GPS accuracy is degraded (28.0m > 20.0m)
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 2.0, 0.5, 28.0)
        assertEquals("Degraded GPS accuracy (> 20.0m) must suppress VAM to 0", 0, vam)
    }

    @Test
    fun testCalculateVam_lowSpeedIndoorWander_suppressedByElevatedDeadband() {
        val history = DoubleArray(windowSize)
        // Simulated multipath wander: 110 m/h altitude drift (< 150 m/h deadband)
        val driftMps = 110.0 / 3600.0
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (i * driftMps)
        }

        // Phantom horizontal speed from wander is 0.8 m/s (0.5 <= speed < 1.2 m/s), accuracy is 12.0m (<= 20m)
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 0.8, 0.5, 12.0)
        assertEquals("Low-speed indoor GPS wander with raw VAM < 150 m/h must be clamped to 0", 0, vam)
    }

    @Test
    fun testCalculateVam_lowSpeedGenuineClimb_preserved() {
        val history = DoubleArray(windowSize)
        // Genuine steep climb or elevator: 600 m/h (> 150 m/h)
        val climbMps = 600.0 / 3600.0
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (i * climbMps)
        }

        // Low speed 0.8 m/s (e.g. steep mountain hiking or walking up stairs), accuracy 8.0m
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 0.8, 0.5, 8.0)
        assertEquals("Genuine vertical rate >= 150 m/h at low speed must be preserved", 600, vam)
    }

    @Test
    fun testCalculateVam_backwardCompatibleFiveParamOverload_worksIdentically() {
        val history = DoubleArray(windowSize)
        val targetVam = 800.0
        val mps = targetVam / 3600.0
        for (i in 0 until windowSize) {
            history[i] = 250.0 + (i * mps)
        }

        // Calling 5-param overload delegates to accuracy = 0.0m
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 5.0, 0.5)
        assertEquals(800, vam)
    }

    /**
     * TST-FIL-004.4: Replicates Workout #455 trace (GPS 492.0m -> DEM 438.0m at standstill,
     * followed by first 20m movement) and asserts that re-seeding eliminates the +22.5m DESCENT
     * jump and -59% SLOPE spike (REQ-FIL-012.5, ATT-5373).
     */
    @Test
    fun testStandstillAltitudeSourceTransition_suppressesDescentAndSlopeArtifacts() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        val mockFilterManager = mockk<FilterManager>()
        BANALService.setFilterManager(mockFilterManager)
        every { mockFilterManager.createFilter(any()) } returns Unit
        every { mockFilterManager.shiftFilter(any(), any()) } returns Unit

        var altitudeFilterVal = 492.0
        var altitudeSuperFilterVal = 492.0
        var speedFilterVal = 0.0

        every { mockFilterManager.getFilteredSensorData(any()) } answers {
            val fd = firstArg<FilterData>()
            if (fd.sensorType == SensorType.ALTITUDE) {
                if (fd.filterConstant == 21.0) {
                    FilteredSensorData(SensorType.ALTITUDE, altitudeFilterVal, "$altitudeFilterVal m", null, FilterType.MOVING_AVERAGE_TIME, 21.0)
                } else {
                    FilteredSensorData(SensorType.ALTITUDE, altitudeSuperFilterVal, "$altitudeSuperFilterVal m", null, FilterType.MOVING_AVERAGE_TIME, 300.0)
                }
            } else if (fd.sensorType == SensorType.SPEED_mps) {
                FilteredSensorData(SensorType.SPEED_mps, speedFilterVal, "$speedFilterVal km/h", null, FilterType.MOVING_AVERAGE_TIME, 21.0)
            } else {
                FilteredSensorData(SensorType.ACCURACY, 5.0, "5 m", null, FilterType.INSTANTANEOUS, 1.0)
            }
        }
        every { mockFilterManager.reseedFilter(any(), any()) } answers {
            val fd = firstArg<FilterData>()
            val num = secondArg<Number>().toDouble()
            if (fd.filterConstant == 21.0) {
                altitudeFilterVal = num
            } else if (fd.filterConstant == 300.0) {
                altitudeSuperFilterVal = num
            }
        }

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false

        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getSlopeMinSpeed(any()) } returns 0.5f

        val mockContext = mockk<Context>(relaxed = true)
        val mockSensorManager = mockk<MySensorManager>(relaxed = true)

        // GPS Device emitting 492.0m WGS84 ellipsoid height
        val gpsDevice = mockk<MyDevice>(relaxed = true)
        every { gpsDevice.name } returns "SpeedAndLocationDevice (GPS)"
        val gpsSensor = mockk<MySensor<Number>>(relaxed = true)
        every { gpsSensor.device } returns gpsDevice
        every { gpsSensor.value } returns 492.0

        val proxySensor = mockk<ProxySensor<Number>>(relaxed = true)
        every { proxySensor.bestSensor } returns gpsSensor
        every { mockSensorManager.getSensor(SensorType.ALTITUDE) } returns proxySensor

        val device = VerticalSpeedAndSlopeDevice(mockContext, mockSensorManager, false)

        // Second 1 & 2: Standstill with GPS (492.0m)
        device.calculateMetrics()
        assertEquals(gpsDevice, device.lastAltitudeSourceDevice)

        device.calculateMetrics()
        assertEquals("Descent during GPS warmup must be 0.0m", 0.0, (device.descentSensor.value as Number).toDouble(), 0.001)

        // Second 3: Standstill (distance = 0.0m) - DEM elevation arrives (438.0m, a -54.0m geoid undulation drop!)
        val demDevice = mockk<MyDevice>(relaxed = true)
        every { demDevice.name } returns "AltitudeFromPositionDevice"
        val demSensor = mockk<MySensor<Number>>(relaxed = true)
        every { demSensor.device } returns demDevice
        every { demSensor.value } returns 438.0
        every { proxySensor.bestSensor } returns demSensor

        device.calculateMetrics()

        assertEquals("Source device must transition to DEM device", demDevice, device.lastAltitudeSourceDevice)
        assertEquals("Super-filtered reference must be reseeded to DEM altitude (438.0m)", 438.0, device.lastAltitudeSuperFiltered, 0.001)
        assertEquals("Descent must remain strictly 0.0m upon DEM transition at standstill", 0.0, (device.descentSensor.value as Number).toDouble(), 0.001)
        assertEquals("Vertical speed must remain 0 m/h upon DEM transition at standstill", 0, (device.verticalSpeedSensor.value as Number).toInt())

        // Seconds 11 to 16: Athlete starts riding (speed = 4.0 m/s > minSpeed 0.5 m/s) on flat/gentle DEM terrain
        speedFilterVal = 4.0
        for (sec in 11..16) {
            altitudeFilterVal = 438.0 + (sec - 10) * 0.05
            altitudeSuperFilterVal = 438.0 + (sec - 10) * 0.02
            device.calculateMetrics()
            val slope = (device.slopeSensor.value as Number).toInt()
            assertTrue("Slope must not jump to negative anomaly like -59% (actual: $slope%)", slope >= -5)
        }

        assertEquals("Total descent must remain strictly 0.0m throughout initial riding", 0.0, (device.descentSensor.value as Number).toDouble(), 0.001)

        device.shutDown()
        unmockkAll()
    }

    /**
     * TST-FIL-004.5: Verifies that ALTITUDE_CORRECTION_INTENT uniformly shifts super-filtered altitude
     * and delegates shiftFilter to BANALService for both filters (REQ-FIL-012.6, ATT-5373).
     */
    @Test
    fun testAltitudeCorrectionIntent_shiftsFiltersAndSuperFilteredAltitude() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        val mockFilterManager = mockk<FilterManager>(relaxed = true)
        BANALService.setFilterManager(mockFilterManager)

        val mockContext = mockk<Context>(relaxed = true)
        val mockSensorManager = mockk<MySensorManager>(relaxed = true)

        val device = VerticalSpeedAndSlopeDevice(mockContext, mockSensorManager, false)
        device.lastAltitudeSuperFiltered = 500.0

        val intent = mockk<Intent>()
        every { intent.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0) } returns 25.0

        device.altitudeCorrectionReceiver.onReceive(mockContext, intent)

        assertEquals("Super-filtered altitude must shift by +25.0m", 525.0, device.lastAltitudeSuperFiltered, 0.001)
        verify(exactly = 1) { mockFilterManager.shiftFilter(match { it.filterConstant == 21.0 }, 25.0) }
        verify(exactly = 1) { mockFilterManager.shiftFilter(match { it.filterConstant == 300.0 }, 25.0) }

        device.shutDown()
        unmockkAll()
    }
}
