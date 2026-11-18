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
import com.atrainingtracker.banalservice.devices.DeviceType
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
        every { TrainingApplication.isTracking() } returns true
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
        val baroDevice = mockk<MyDevice>(relaxed = true)
        every { baroDevice.deviceType } returns DeviceType.ALTITUDE_FROM_PRESSURE
        val baroSensor = mockk<MySensor<Number>>(relaxed = true)
        every { baroSensor.device } returns baroDevice
        val proxySensor = mockk<ProxySensor<Number>>(relaxed = true)
        every { proxySensor.bestSensor } returns baroSensor
        every { mockSensorManager.getSensor(SensorType.ALTITUDE) } returns proxySensor

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

    /**
     * TST-FIL-024.1a: Verifies that ALTITUDE_CORRECTION_INTENT is ignored when ATTFused or DEM
     * is the active canonical SensorType.ALTITUDE source (REQ-FIL-024.1, ATT-8707).
     */
    @Test
    fun testAltitudeCorrectionIntent_ignoredWhenATTFusedOrDemIsActiveAltitudeSource() {
        for (nonBaroType in listOf(DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED, DeviceType.ALTITUDE_FROM_POSITION)) {
            mockkConstructor(Intent::class)
            every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
            mockkStatic(ContextCompat::class)
            every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
            mockkStatic(DevicesDatabaseManager::class)
            every { DevicesDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)

            val mockFilterManager = mockk<FilterManager>(relaxed = true)
            BANALService.setFilterManager(mockFilterManager)

            val mockContext = mockk<Context>(relaxed = true)
            val mockSensorManager = mockk<MySensorManager>(relaxed = true)
            val nonBaroDevice = mockk<MyDevice>(relaxed = true)
            every { nonBaroDevice.deviceType } returns nonBaroType
            val nonBaroSensor = mockk<MySensor<Number>>(relaxed = true)
            every { nonBaroSensor.device } returns nonBaroDevice
            val proxySensor = mockk<ProxySensor<Number>>(relaxed = true)
            every { proxySensor.bestSensor } returns nonBaroSensor
            every { mockSensorManager.getSensor(SensorType.ALTITUDE) } returns proxySensor

            val device = VerticalSpeedAndSlopeDevice(mockContext, mockSensorManager, false)
            device.lastAltitudeSuperFiltered = 438.0
            device.lastAltitude = 438.0

            val intent = mockk<Intent>()
            every { intent.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0) } returns 100.0

            device.altitudeCorrectionReceiver.onReceive(mockContext, intent)

            assertEquals("Super-filtered altitude must NOT shift for $nonBaroType", 438.0, device.lastAltitudeSuperFiltered, 0.001)
            assertEquals("Last altitude must NOT shift for $nonBaroType", 438.0, device.lastAltitude, 0.001)
            verify(exactly = 0) { mockFilterManager.shiftFilter(any(), any()) }

            device.shutDown()
            BANALService.setFilterManager(null)
            unmockkAll()
        }
    }

    /**
     * TST-FIL-024.1b: Verifies active ALTITUDE_FROM_PRESSURE shifts wrapped ring-buffer slots
     * alongside FilterManager filters and baseline fields (REQ-FIL-024.1, REQ-FIL-024.4).
     */
    @Test
    fun testAltitudeCorrectionIntent_shiftsWrappedRingBufferWhenBaroIsActive() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns true
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getSlopeMinSpeed(any()) } returns 0.5f

        val mockFilterManager = mockk<FilterManager>(relaxed = true)
        BANALService.setFilterManager(mockFilterManager)
        every { mockFilterManager.getFilteredSensorData(any()) } answers {
            val fd = firstArg<FilterData>()
            when (fd.sensorType) {
                SensorType.ALTITUDE -> FilteredSensorData(SensorType.ALTITUDE, 338.0, "338 m", null, FilterType.MOVING_AVERAGE_TIME, fd.filterConstant)
                SensorType.SPEED_mps -> FilteredSensorData(SensorType.SPEED_mps, 5.0, "5 m/s", null, FilterType.MOVING_AVERAGE_TIME, 21.0)
                else -> FilteredSensorData(SensorType.ACCURACY, 5.0, "5 m", null, FilterType.INSTANTANEOUS, 1.0)
            }
        }

        val mockContext = mockk<Context>(relaxed = true)
        val mockSensorManager = mockk<MySensorManager>(relaxed = true)
        val baroDevice = mockk<MyDevice>(relaxed = true)
        every { baroDevice.deviceType } returns DeviceType.ALTITUDE_FROM_PRESSURE
        val baroSensor = mockk<MySensor<Number>>(relaxed = true)
        every { baroSensor.device } returns baroDevice
        every { baroSensor.value } returns 338.0
        every { mockSensorManager.getSensor(SensorType.ALTITUDE) } returns baroSensor

        val device = VerticalSpeedAndSlopeDevice(mockContext, mockSensorManager, false)
        // Fill past VAM_WINDOW_SIZE (15) to wrap ring buffer head
        repeat(18) { device.calculateMetrics() }
        assertEquals(3, device.altitudeHistoryHead)
        assertEquals(windowSize, device.altitudeHistoryCount)

        val intent = mockk<Intent>()
        every { intent.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0) } returns 100.0
        device.altitudeCorrectionReceiver.onReceive(mockContext, intent)

        assertEquals(438.0, device.lastAltitudeSuperFiltered, 0.001)
        assertEquals(438.0, device.lastAltitude, 0.001)
        for (idx in 0 until windowSize) {
            assertEquals("Wrapped slot $idx must shift by +100.0m", 438.0, device.getAltitudeHistoryAt(idx), 0.001)
        }
        verify(exactly = 1) { mockFilterManager.shiftFilter(match { it.filterConstant == 21.0 }, 100.0) }
        verify(exactly = 1) { mockFilterManager.shiftFilter(match { it.filterConstant == 300.0 }, 100.0) }

        device.shutDown()
        unmockkAll()
    }

    /**
     * TST-FIL-024.2: Verifies synchronized onAccumulatorsReset() clears stale pre-workout baselines
     * and prevents phantom startup descent on RESET_ACCUMULATORS_INTENT (REQ-FIL-024.2).
     */
    @Test
    fun testOnAccumulatorsReset_clearsStaleAltitudeStateAndPreventsStartupPhantomDescent() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns true
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getSlopeMinSpeed(any()) } returns 0.5f

        val mockFilterManager = mockk<FilterManager>(relaxed = true)
        BANALService.setFilterManager(mockFilterManager)

        var currentFilteredAlt = 540.0
        every { mockFilterManager.getFilteredSensorData(any()) } answers {
            val fd = firstArg<FilterData>()
            when (fd.sensorType) {
                SensorType.ALTITUDE -> FilteredSensorData(SensorType.ALTITUDE, currentFilteredAlt, "$currentFilteredAlt m", null, FilterType.MOVING_AVERAGE_TIME, fd.filterConstant)
                SensorType.SPEED_mps -> FilteredSensorData(SensorType.SPEED_mps, 0.0, "0 m/s", null, FilterType.MOVING_AVERAGE_TIME, 21.0)
                else -> FilteredSensorData(SensorType.ACCURACY, 5.0, "5 m", null, FilterType.INSTANTANEOUS, 1.0)
            }
        }

        val mockContext = mockk<Context>(relaxed = true)
        val mockSensorManager = mockk<MySensorManager>(relaxed = true)
        val attFusedDevice = mockk<MyDevice>(relaxed = true)
        every { attFusedDevice.deviceType } returns DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED
        val attFusedSensor = mockk<MySensor<Number>>(relaxed = true)
        every { attFusedSensor.device } returns attFusedDevice
        every { attFusedSensor.value } returns 540.0
        every { mockSensorManager.getSensor(SensorType.ALTITUDE) } returns attFusedSensor

        val device = VerticalSpeedAndSlopeDevice(mockContext, mockSensorManager, false)
        repeat(5) { device.calculateMetrics() }

        // Simulate workout start accumulator reset while new baseline is 438.0m
        device.onAccumulatorsReset()

        assertEquals(null, device.lastAltitudeSuperFiltered)
        assertEquals(null, device.lastAltitude)
        assertEquals(null, device.lastAltitudeSourceDevice)
        assertEquals(0, device.altitudeHistoryCount)
        assertEquals(0, device.altitudeHistoryHead)

        currentFilteredAlt = 438.0
        every { attFusedSensor.value } returns 438.0
        device.calculateMetrics()

        assertEquals("Descent must be 0.0m after accumulator reset", 0.0, (device.descentSensor.value as Number).toDouble(), 0.001)
        assertEquals("Ascent must be 0.0m after accumulator reset", 0.0, (device.ascentSensor.value as Number).toDouble(), 0.001)

        device.shutDown()
        unmockkAll()
    }

    /**
     * TST-FIL-024.3a: Verifies same-device step discontinuity (e.g. ATTFused initial Baro-to-DEM
     * re-anchor snap 538m -> 438m) triggers reseedAltitudeFilters without phantom descent or -203% slope.
     */
    @Test
    fun testSameDeviceAltitudeStep_reseedsFiltersWithoutPhantomDescentOrSlope() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns true
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getSlopeMinSpeed(any()) } returns 0.5f

        val mockFilterManager = mockk<FilterManager>(relaxed = true)
        BANALService.setFilterManager(mockFilterManager)

        var filter21 = 538.0
        var filter300 = 538.0
        var speedVal = 2.5
        every { mockFilterManager.getFilteredSensorData(any()) } answers {
            val fd = firstArg<FilterData>()
            when (fd.sensorType) {
                SensorType.ALTITUDE -> {
                    val v = if (fd.filterConstant == 21.0) filter21 else filter300
                    FilteredSensorData(SensorType.ALTITUDE, v, "$v m", null, FilterType.MOVING_AVERAGE_TIME, fd.filterConstant)
                }
                SensorType.SPEED_mps -> FilteredSensorData(SensorType.SPEED_mps, speedVal, "$speedVal m/s", null, FilterType.MOVING_AVERAGE_TIME, 21.0)
                else -> FilteredSensorData(SensorType.ACCURACY, 5.0, "5 m", null, FilterType.INSTANTANEOUS, 1.0)
            }
        }
        every { mockFilterManager.reseedFilter(any(), any()) } answers {
            val fd = firstArg<FilterData>()
            val num = secondArg<Number>().toDouble()
            if (fd.filterConstant == 21.0) filter21 = num else filter300 = num
        }

        val mockContext = mockk<Context>(relaxed = true)
        val mockSensorManager = mockk<MySensorManager>(relaxed = true)
        val attFusedDevice = mockk<MyDevice>(relaxed = true)
        every { attFusedDevice.name } returns "ATT Fused Altitude"
        every { attFusedDevice.deviceType } returns DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED
        var rawAlt = 538.0
        val attFusedSensor = mockk<MySensor<Number>>(relaxed = true)
        every { attFusedSensor.device } returns attFusedDevice
        every { attFusedSensor.value } answers { rawAlt }
        val proxySensor = mockk<ProxySensor<Number>>(relaxed = true)
        every { proxySensor.bestSensor } returns attFusedSensor
        every { mockSensorManager.getSensor(SensorType.ALTITUDE) } returns proxySensor

        val device = VerticalSpeedAndSlopeDevice(mockContext, mockSensorManager, false)
        // Row 0: uncalibrated initial baro sample
        device.calculateMetrics()

        // Row 1: same ATTFused device snaps -100m to DEM anchor (538m -> 438m)
        rawAlt = 438.0
        device.calculateMetrics()

        assertEquals(438.0, device.lastAltitudeSuperFiltered, 0.001)
        assertEquals(438.0, device.lastAltitude, 0.001)
        assertEquals("Descent must stay 0.0m after same-device step reseed", 0.0, (device.descentSensor.value as Number).toDouble(), 0.001)
        assertEquals("Ascent must stay 0.0m after same-device step reseed", 0.0, (device.ascentSensor.value as Number).toDouble(), 0.001)
        assertEquals("Slope must not spike to -203% on same-device step", 0, (device.slopeSensor.value as Number).toInt())
        verify(exactly = 1) { mockFilterManager.reseedFilter(match { it.filterConstant == 21.0 }, 438.0) }
        verify(exactly = 1) { mockFilterManager.reseedFilter(match { it.filterConstant == 300.0 }, 438.0) }

        device.shutDown()
        unmockkAll()
    }

    /**
     * TST-FIL-024.3b: Verifies genuine steady-state climb and descent (+0.5 m/s and -0.5 m/s
     * at 5.0 m/s horizontal speed) integrate ASCENT, DESCENT, VAM, and SLOPE without false reseeding.
     */
    @Test
    fun testSteadyStateClimbAndDescent_preservedWithSameDeviceGuard() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns true
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getSlopeMinSpeed(any()) } returns 0.5f

        val mockFilterManager = mockk<FilterManager>(relaxed = true)
        BANALService.setFilterManager(mockFilterManager)

        var currentAlt = 400.0
        every { mockFilterManager.getFilteredSensorData(any()) } answers {
            val fd = firstArg<FilterData>()
            when (fd.sensorType) {
                SensorType.ALTITUDE -> FilteredSensorData(SensorType.ALTITUDE, currentAlt, "$currentAlt m", null, FilterType.MOVING_AVERAGE_TIME, fd.filterConstant)
                SensorType.SPEED_mps -> FilteredSensorData(SensorType.SPEED_mps, 5.0, "5 m/s", null, FilterType.MOVING_AVERAGE_TIME, 21.0)
                else -> FilteredSensorData(SensorType.ACCURACY, 5.0, "5 m", null, FilterType.INSTANTANEOUS, 1.0)
            }
        }

        val mockContext = mockk<Context>(relaxed = true)
        val mockSensorManager = mockk<MySensorManager>(relaxed = true)
        val attFusedDevice = mockk<MyDevice>(relaxed = true)
        every { attFusedDevice.deviceType } returns DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED
        val attFusedSensor = mockk<MySensor<Number>>(relaxed = true)
        every { attFusedSensor.device } returns attFusedDevice
        every { attFusedSensor.value } answers { currentAlt }
        every { mockSensorManager.getSensor(SensorType.ALTITUDE) } returns attFusedSensor

        val device = VerticalSpeedAndSlopeDevice(mockContext, mockSensorManager, false)

        // 10s steady climb at +0.5 m/s (10% grade at 5.0 m/s, 1800 m/h VAM)
        device.calculateMetrics() // baseline 400.0m
        repeat(10) {
            currentAlt += 0.5
            device.calculateMetrics()
        }
        assertEquals(5.0, (device.ascentSensor.value as Number).toDouble(), 0.001)
        assertEquals(0.0, (device.descentSensor.value as Number).toDouble(), 0.001)
        assertEquals(1800, (device.verticalSpeedSensor.value as Number).toInt())
        assertEquals(10, (device.slopeSensor.value as Number).toInt())

        // 6s steady descent at -0.5 m/s
        repeat(6) {
            currentAlt -= 0.5
            device.calculateMetrics()
        }
        assertEquals(3.0, (device.descentSensor.value as Number).toDouble(), 0.001)
        verify(exactly = 0) { mockFilterManager.reseedFilter(any(), any()) }

        device.shutDown()
        unmockkAll()
    }

    /**
     * TST-FIL-024.1c: Reproduces the Cold Stage 6 active-barometer calibration race where
     * AltitudeFromPressureDevice updates its sensor value (>= 15.0m) and calculateMetrics()
     * executes before asynchronous ALTITUDE_CORRECTION_INTENT delivery; verifies zero premature
     * reseed and zero double-shift.
     */
    @Test
    fun testActiveBaroCalibrationAboveThreshold_beforeCorrectionBroadcast_noDoubleShiftOrReseed() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns true
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getSlopeMinSpeed(any()) } returns 0.5f

        val mockFilterManager = mockk<FilterManager>(relaxed = true)
        BANALService.setFilterManager(mockFilterManager)

        var filter21 = 413.0
        var filter300 = 413.0
        every { mockFilterManager.getFilteredSensorData(any()) } answers {
            val fd = firstArg<FilterData>()
            when (fd.sensorType) {
                SensorType.ALTITUDE -> {
                    val v = if (fd.filterConstant == 21.0) filter21 else filter300
                    FilteredSensorData(SensorType.ALTITUDE, v, "$v m", null, FilterType.MOVING_AVERAGE_TIME, fd.filterConstant)
                }
                SensorType.SPEED_mps -> FilteredSensorData(SensorType.SPEED_mps, 3.0, "3 m/s", null, FilterType.MOVING_AVERAGE_TIME, 21.0)
                else -> FilteredSensorData(SensorType.ACCURACY, 5.0, "5 m", null, FilterType.INSTANTANEOUS, 1.0)
            }
        }
        every { mockFilterManager.shiftFilter(any(), any()) } answers {
            val fd = firstArg<FilterData>()
            val d = secondArg<Double>()
            if (fd.filterConstant == 21.0) filter21 += d else filter300 += d
        }

        val mockContext = mockk<Context>(relaxed = true)
        val mockSensorManager = mockk<MySensorManager>(relaxed = true)
        val baroDevice = mockk<MyDevice>(relaxed = true)
        every { baroDevice.name } returns "Barometer"
        every { baroDevice.deviceType } returns DeviceType.ALTITUDE_FROM_PRESSURE
        var rawAlt = 413.0
        val baroSensor = mockk<MySensor<Number>>(relaxed = true)
        every { baroSensor.device } returns baroDevice
        every { baroSensor.value } answers { rawAlt }
        val proxySensor = mockk<ProxySensor<Number>>(relaxed = true)
        every { proxySensor.bestSensor } returns baroSensor
        every { mockSensorManager.getSensor(SensorType.ALTITUDE) } returns proxySensor

        val device = VerticalSpeedAndSlopeDevice(mockContext, mockSensorManager, false)
        // Steady pre-calibration baseline at 413.0m
        device.calculateMetrics()
        device.calculateMetrics()
        assertEquals(413.0, device.lastAltitudeSuperFiltered, 0.001)

        // Baro calibrates +25.0m (413m -> 438m): newValue() updates synchronously before Intent arrives
        rawAlt = 438.0
        device.calculateMetrics()

        verify(exactly = 0) { mockFilterManager.reseedFilter(any(), any()) }
        assertEquals("Super-filtered baseline must remain 413.0m until broadcast shift", 413.0, device.lastAltitudeSuperFiltered, 0.001)

        // Now asynchronous ALTITUDE_CORRECTION_INTENT (+25.0m) arrives
        val intent = mockk<Intent>()
        every { intent.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0) } returns 25.0
        device.altitudeCorrectionReceiver.onReceive(mockContext, intent)

        assertEquals("Must shift once to 438.0m, never double-shift to 463.0m", 438.0, device.lastAltitudeSuperFiltered, 0.001)
        assertEquals(438.0, filter21, 0.001)
        assertEquals(438.0, filter300, 0.001)
        verify(exactly = 1) { mockFilterManager.shiftFilter(match { it.filterConstant == 21.0 }, 25.0) }
        verify(exactly = 1) { mockFilterManager.shiftFilter(match { it.filterConstant == 300.0 }, 25.0) }

        // Next tick at calibrated 438.0m produces zero phantom ascent/descent
        device.calculateMetrics()
        assertEquals(0.0, (device.ascentSensor.value as Number).toDouble(), 0.001)
        assertEquals(0.0, (device.descentSensor.value as Number).toDouble(), 0.001)

        device.shutDown()
        unmockkAll()
    }

    /**
     * TST-FIL-024.3c: Verifies non-finite delta (NaN, +/-Infinity) in ALTITUDE_CORRECTION_INTENT
     * and non-finite rawAlt in checkAltitudeSourceTransition() are rejected without poisoning baselines.
     */
    @Test
    fun testNonFiniteDeltaAndRawAltitude_rejectedCleanly() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isTracking() } returns true
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getSlopeMinSpeed(any()) } returns 0.5f

        val mockFilterManager = mockk<FilterManager>(relaxed = true)
        BANALService.setFilterManager(mockFilterManager)
        every { mockFilterManager.getFilteredSensorData(any()) } answers {
            val fd = firstArg<FilterData>()
            when (fd.sensorType) {
                SensorType.ALTITUDE -> FilteredSensorData(SensorType.ALTITUDE, 438.0, "438 m", null, FilterType.MOVING_AVERAGE_TIME, fd.filterConstant)
                SensorType.SPEED_mps -> FilteredSensorData(SensorType.SPEED_mps, 2.0, "2 m/s", null, FilterType.MOVING_AVERAGE_TIME, 21.0)
                else -> FilteredSensorData(SensorType.ACCURACY, 5.0, "5 m", null, FilterType.INSTANTANEOUS, 1.0)
            }
        }

        val mockContext = mockk<Context>(relaxed = true)
        val mockSensorManager = mockk<MySensorManager>(relaxed = true)
        val baroDevice = mockk<MyDevice>(relaxed = true)
        every { baroDevice.deviceType } returns DeviceType.ALTITUDE_FROM_PRESSURE
        var rawAlt = 438.0
        val altSensor = mockk<MySensor<Number>>(relaxed = true)
        every { altSensor.device } returns baroDevice
        every { altSensor.value } answers { rawAlt }
        every { mockSensorManager.getSensor(SensorType.ALTITUDE) } returns altSensor

        val device = VerticalSpeedAndSlopeDevice(mockContext, mockSensorManager, false)
        device.calculateMetrics()
        assertEquals(438.0, device.lastAltitude, 0.001)
        assertEquals(438.0, device.lastAltitudeSuperFiltered, 0.001)

        // Reject NaN, +Infinity, -Infinity deltas in ALTITUDE_CORRECTION_INTENT
        for (badDelta in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val intent = mockk<Intent>()
            every { intent.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0) } returns badDelta
            device.altitudeCorrectionReceiver.onReceive(mockContext, intent)
        }
        verify(exactly = 0) { mockFilterManager.shiftFilter(any(), any()) }
        assertEquals(438.0, device.lastAltitudeSuperFiltered, 0.001)
        assertEquals(438.0, device.lastAltitude, 0.001)

        // Reject +Infinity, -Infinity, NaN rawAlt on same-device ATTFused or baro ticks
        every { baroDevice.deviceType } returns DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED
        for (badRaw in listOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
            rawAlt = badRaw
            device.calculateMetrics()
        }
        verify(exactly = 0) { mockFilterManager.reseedFilter(any(), any()) }
        assertEquals(438.0, device.lastAltitude, 0.001)
        assertEquals(438.0, device.lastAltitudeSuperFiltered, 0.001)

        device.shutDown()
        unmockkAll()
    }
}
