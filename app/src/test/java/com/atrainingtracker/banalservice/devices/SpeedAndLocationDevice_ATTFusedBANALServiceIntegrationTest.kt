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
import android.hardware.SensorEvent
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.ProxySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * End-to-end Robolectric integration test verifying that smartphone-only workouts (zero external
 * ANT+/BLE sensors, WITHOUT [SpeedAndLocationDevice_ATTFused.setSportContextHintForTesting]) resolve
 * user-selected [BSportType] via live [BANALService] / [BANALService.getActiveBSportType] to drive
 * [SensorType.STRIDES] registration, [PhoneCarriageMode.WALKING_TRANSITION] cadence/strides under
 * [BSportType.RUN], live [BSportType.BIKE] teardown via [BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT],
 * and clean `cServiceInstance` release on [BANALService.onDestroy] (REQ-FIL-029.3, TST-FIL-029.3, ATT-8895).
 */
@Suppress("ClassNaming")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest {

    private lateinit var appContext: Context

    @Suppress("DEPRECATION")
    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        TrainingApplication::class.java.getDeclaredField("cAppContext").apply {
            isAccessible = true
            set(null, appContext)
        }
        TrainingApplication::class.java.getDeclaredField("cSharedPreferences").apply {
            isAccessible = true
            set(null, PreferenceManager.getDefaultSharedPreferences(appContext))
        }
        mockkStatic(GooglePlayServicesUtil::class)
        every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.SUCCESS
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
    }

    @After
    fun tearDown() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        BANALService.setServiceInstanceForTesting(null)
        BANALService.setServiceContextForTesting(null)
        SportTypesRepository.resetForTesting(null)
        TrainingApplication::class.java.getDeclaredField("cAppContext").apply {
            isAccessible = true
            set(null, null)
        }
        TrainingApplication::class.java.getDeclaredField("cSharedPreferences").apply {
            isAccessible = true
            set(null, null)
        }
        unmockkAll()
    }

    // TST-FIL-029.3 (Cold Final Bounce 3): Real BANALService smartphone-only sport resolution without test hints.
    @Test
    fun smartphoneOnlyRunning_resolvesBanalServiceSportType_registersStridesAndWalkingTransition_tearsDownOnBikeAndDestroy_tstFil029_3() {
        val controller = Robolectric.buildService(BANALService::class.java)
        val service = controller.create().get()
        val comm = service.onBind(Intent()) as BANALService.BANALServiceComm

        // Preserve leak-free getServiceContext() == Application context, with zero external sensors connected
        assertNotNull(BANALService.getServiceContext())
        assertFalse(
            "BANALService.getServiceContext() must remain Application context, not BANALService",
            BANALService.getServiceContext() is BANALService
        )
        assertTrue("Smartphone-only test must have zero active remote devices", comm.activeRemoteDevices.isEmpty())
        assertEquals(BSportType.UNKNOWN, BANALService.getActiveBSportType())

        // User selects RUN via BANALServiceComm with zero external remote sensors
        comm.setUserSelectedSportType(BSportType.RUN)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            "BANALService.getActiveBSportType() must return BSportType.RUN for smartphone-only running",
            BSportType.RUN,
            BANALService.getActiveBSportType()
        )

        var fakeClockMs = 1000L
        val serviceSensorManager = MySensorManager(service)
        val serviceBackedDevice = object : SpeedAndLocationDevice_ATTFused(service, serviceSensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
            override fun hasActivityRecognitionPermission(): Boolean = true
        }
        // Also verify fallback via BANALService.getActiveBSportType() when mContext is Application context
        val appSensorManager = MySensorManager(appContext)
        val appContextBackedDevice = object : SpeedAndLocationDevice_ATTFused(appContext, appSensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
            override fun hasActivityRecognitionPermission(): Boolean = true
        }

        try {
            // Phase 1: Walking transition under BSportType.RUN (stepFreq ~1.82 Hz -> PhoneCarriageMode.WALKING_TRANSITION)
            // Previously suppressed when BANALService.getActiveBSportType() returned UNKNOWN.
            var nextWalkStepMs = 1550L
            for (tick in 0..350) { // 7.0 s walking @ 1.82 steps/s
                fakeClockMs = 1000L + tick * 20L
                val tSec = fakeClockMs / 1000.0
                val halfTilt = (Math.toRadians(20.0 + 5.0 * sin(2.0 * PI * 0.91 * tSec)) * 0.5).toFloat()
                val rv = createSyntheticSensorEvent(
                    Sensor.TYPE_GAME_ROTATION_VECTOR,
                    fakeClockMs * 1_000_000L,
                    floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())
                )
                val acc = createSyntheticSensorEvent(
                    Sensor.TYPE_ACCELEROMETER,
                    fakeClockMs * 1_000_000L,
                    floatArrayOf(0.2f, (9.81 + 1.2 * sin(2.0 * PI * 1.82 * tSec)).toFloat(), 0.3f)
                )
                serviceBackedDevice.onSensorChanged(rv)
                serviceBackedDevice.onSensorChanged(acc)
                appContextBackedDevice.onSensorChanged(rv)
                appContextBackedDevice.onSensorChanged(acc)

                if (fakeClockMs >= nextWalkStepMs) {
                    val step = createSyntheticSensorEvent(
                        Sensor.TYPE_STEP_DETECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(1.0f)
                    )
                    serviceBackedDevice.onSensorChanged(step)
                    appContextBackedDevice.onSensorChanged(step)
                    nextWalkStepMs += 550L
                }
            }

            assertEquals(
                "Walking steps without external sensors must classify as WALKING_TRANSITION",
                PhoneCarriageMode.WALKING_TRANSITION.code,
                serviceBackedDevice.mActivityAndCarriageModeSensor?.value
            )
            val walkingStridesService = (serviceSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            val walkingCadenceService = (serviceSensorManager.getSensor(SensorType.CADENCE)?.value as? Number)?.toDouble()
            assertNotNull("Service-backed device must register STRIDES under WALKING_TRANSITION + RUN", serviceBackedDevice.mStridesSensor)
            assertNotNull("Service-backed device must emit CADENCE under WALKING_TRANSITION + RUN", walkingCadenceService)
            assertTrue(
                "WALKING_TRANSITION under BSportType.RUN must count strides >= 2 (got $walkingStridesService)",
                walkingStridesService >= 2
            )

            val walkingStridesApp = (appSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertNotNull("AppContext-backed device must also register STRIDES via BANALService.getActiveBSportType()", appContextBackedDevice.mStridesSensor)
            assertTrue(
                "AppContext-backed device must also count strides >= 2 via cServiceInstance (got $walkingStridesApp)",
                walkingStridesApp >= 2
            )

            // Phase 2: Transition to running (~170 steps/min = 85 strides/min) -> STRIDES continues advancing
            var nextRunStepMs = maxOf(fakeClockMs + 353L, nextWalkStepMs)
            val runStartMs = fakeClockMs
            for (tick in 1..250) { // 5.0 s running
                fakeClockMs = runStartMs + tick * 20L
                val tSec = fakeClockMs / 1000.0
                val halfTilt = (Math.toRadians(45.0 + 18.0 * sin(2.0 * PI * 1.4167 * tSec)) * 0.5).toFloat()
                val rv = createSyntheticSensorEvent(
                    Sensor.TYPE_GAME_ROTATION_VECTOR,
                    fakeClockMs * 1_000_000L,
                    floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())
                )
                val acc = createSyntheticSensorEvent(
                    Sensor.TYPE_ACCELEROMETER,
                    fakeClockMs * 1_000_000L,
                    floatArrayOf(0.9f, (9.81 + 3.4 * sin(2.0 * PI * 2.8333 * tSec)).toFloat(), 1.0f)
                )
                serviceBackedDevice.onSensorChanged(rv)
                serviceBackedDevice.onSensorChanged(acc)
                appContextBackedDevice.onSensorChanged(rv)
                appContextBackedDevice.onSensorChanged(acc)

                if (fakeClockMs >= nextRunStepMs) {
                    val step = createSyntheticSensorEvent(
                        Sensor.TYPE_STEP_DETECTOR,
                        fakeClockMs * 1_000_000L,
                        floatArrayOf(1.0f)
                    )
                    serviceBackedDevice.onSensorChanged(step)
                    appContextBackedDevice.onSensorChanged(step)
                    nextRunStepMs += 353L
                }
            }

            val runStridesService = (serviceSensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            val runCadenceService = (serviceSensorManager.getSensor(SensorType.CADENCE)?.value as? Number)?.toDouble() ?: 0.0
            assertTrue(
                "Running phase must advance STRIDES beyond walking baseline ($walkingStridesService -> $runStridesService)",
                runStridesService >= walkingStridesService + 4
            )
            assertTrue(
                "Running CADENCE must converge near 85 strides/min (got $runCadenceService)",
                runCadenceService in 72.0..96.0
            )

            // Phase 3: Switch user-selected sport to BIKE -> real SPORT_TYPE_CHANGED_BY_USER_INTENT tears down STRIDES
            comm.setUserSelectedSportType(BSportType.BIKE)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(BSportType.BIKE, BANALService.getActiveBSportType())
            assertNull("Switching BANALService to BIKE must unregister service-backed STRIDES", serviceBackedDevice.mStridesSensor)
            assertNull("Switching BANALService to BIKE must unregister appContext-backed STRIDES", appContextBackedDevice.mStridesSensor)
            val serviceProxyStrides = serviceSensorManager.getSensor(SensorType.STRIDES) as? ProxySensor<*>
            assertNull("ProxySensor(STRIDES) must have null sourceSensor after BIKE switch", serviceProxyStrides?.sourceSensor)
        } finally {
            serviceBackedDevice.shutDown()
            appContextBackedDevice.shutDown()
            serviceSensorManager.shutDown()
            appSensorManager.shutDown()
            controller.destroy()
        }

        // Phase 4: Verify BANALService.onDestroy() cleared cServiceInstance cleanly
        assertEquals(
            "BANALService.onDestroy() must clear cServiceInstance so getActiveBSportType() returns UNKNOWN",
            BSportType.UNKNOWN,
            BANALService.getActiveBSportType()
        )
        assertNull(BANALService.getServiceContext())
        assertNull(BANALService.getDeviceManager())
    }

    // TST-FIL-029.3 (Gate 4 Bounce 3 REVISION): Verify 100+ Hz IMU callbacks and repeated active sport
    // reads hit the volatile mUserSelectedBSportType cache with ZERO per-sample SQLite lookups while
    // RUN -> BIKE user toggles still propagate immediately.
    @Test
    fun activeBSportTypeHotPath_usesVolatileCacheWithZeroPerSampleSqliteQueries_tstFil029_3() {
        val realRepo = SportTypesRepository.getInstance(appContext)
        val spyRepo = spyk(realRepo)
        SportTypesRepository.resetForTesting(spyRepo)

        val controller = Robolectric.buildService(BANALService::class.java)
        val service = controller.create().get()
        val comm = service.onBind(Intent()) as BANALService.BANALServiceComm
        var fakeClockMs = 1000L
        val sensorManager = MySensorManager(service)
        val device = object : SpeedAndLocationDevice_ATTFused(service, sensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
            override fun hasActivityRecognitionPermission(): Boolean = true
        }

        try {
            // 1. Selecting RUN resolves BSportType once from SportTypesRepository
            comm.setUserSelectedSportType(BSportType.RUN)
            shadowOf(Looper.getMainLooper()).idle()
            verify(exactly = 1) { spyRepo.getBSportType(any()) }

            // 2. Repeated active-sport reads + 200 high-rate 50-Hz ACC/RV samples must trigger ZERO extra DB lookups
            repeat(200) {
                assertEquals(BSportType.RUN, BANALService.getActiveBSportType())
                assertEquals(BSportType.RUN, service.bSportType)
                assertEquals(BSportType.RUN, service.userSelectedBSportType)
            }
            for (tick in 0 until 100) {
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
            }
            verify(exactly = 1) { spyRepo.getBSportType(any()) }

            // 3. Toggling RUN -> BIKE resolves BSportType once more and updates the volatile cache immediately
            comm.setUserSelectedSportType(BSportType.BIKE)
            shadowOf(Looper.getMainLooper()).idle()
            verify(exactly = 2) { spyRepo.getBSportType(any()) }
            repeat(100) {
                assertEquals(BSportType.BIKE, BANALService.getActiveBSportType())
                assertEquals(BSportType.BIKE, service.bSportType)
                assertEquals(BSportType.BIKE, service.userSelectedBSportType)
            }
            verify(exactly = 2) { spyRepo.getBSportType(any()) }
        } finally {
            device.shutDown()
            sensorManager.shutDown()
            controller.destroy()
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
