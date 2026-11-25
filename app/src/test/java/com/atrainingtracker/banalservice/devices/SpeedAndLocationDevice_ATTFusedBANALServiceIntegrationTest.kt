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

    /**
     * TST-FIL-030.2: End-to-end Robolectric verification on real `BANALService` +
     * `SpeedAndLocationDevice_ATTFused` with ZERO test hints:
     * 1. Sensorless unselected running infers `BSportType.RUN` from 50-Hz `ACC` + GNSS speed
     *    even without `ACTIVITY_RECOGNITION` (`TYPE_STEP_DETECTOR`).
     * 2. `BANALService.getAuthoritativeBSportType()` stays `UNKNOWN` (anti-circular isolation
     *    for `PhoneCarriageClassifier`) while `getBSportType()` / `getActiveBSportType()` expose
     *    `BSportType.RUN` and automatically register `SensorType.STRIDES` across `WALKING_TRANSITION`.
     * 3. Inferred transition NEVER broadcasts `SPORT_TYPE_CHANGED_BY_USER_INTENT`.
     * 4. `onAccumulatorsReset()` clears discriminator & `BANALService` Tier 3 inferred state.
     */
    @Test
    fun sensorlessUnselectedRun_infersRunWithSplitAuthoritativeHintAndActivatesStrides_tstFil030_2() {
        shadowOf(appContext as android.app.Application).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        val controller = Robolectric.buildService(BANALService::class.java)
        val service = controller.create().get()
        var fakeClockMs = 1000L
        val sensorManager = MySensorManager(service)
        val device = object : SpeedAndLocationDevice_ATTFused(service, sensorManager) {
            override fun currentTimeMillis(): Long = fakeClockMs
            override fun hasActivityRecognitionPermission(): Boolean = false
            fun triggerResetForTest() { onAccumulatorsReset() }
        }

        val userChangedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val userChangedReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                if (intent?.action == BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT) {
                    userChangedCount.incrementAndGet()
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            service,
            userChangedReceiver,
            android.content.IntentFilter(BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        try {
            assertEquals(BSportType.UNKNOWN, service.bSportType)
            assertEquals(BSportType.UNKNOWN, service.authoritativeBSportType)

            // Stream 15 s of sensorless 2.8 Hz running (2.5 m/s -> L_step = 0.89 m) via 50-Hz ACC + GNSS
            for (sec in 0 until 15) {
                for (k in 0 until 50) {
                    fakeClockMs = 1000L + sec * 1000L + k * 20L
                    val tSec = fakeClockMs / 1000.0
                    val az = (9.81 + 6.5 * sin(2.0 * PI * 2.8 * tSec)).toFloat()
                    device.onSensorChanged(
                        createSyntheticSensorEvent(
                            Sensor.TYPE_ACCELEROMETER,
                            fakeClockMs * 1_000_000L,
                            floatArrayOf(0.4f, 0.3f, az)
                        )
                    )
                }
                val loc = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                    latitude = 48.137 + sec * 0.000022
                    longitude = 11.576
                    accuracy = 4.0f
                    speed = 2.5f
                    time = fakeClockMs
                }
                device.onLocationChanged(loc)
            }
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(BSportType.RUN, service.bSportType)
            assertEquals(BSportType.RUN, BANALService.getActiveBSportType())
            // Anti-circular split hint: authoritative sport MUST stay UNKNOWN!
            assertEquals(BSportType.UNKNOWN, service.authoritativeBSportType)
            assertEquals(BSportType.UNKNOWN, BANALService.getAuthoritativeActiveBSportType())
            // Inferred sport must NEVER broadcast SPORT_TYPE_CHANGED_BY_USER_INTENT
            assertEquals(0, userChangedCount.get())
            assertNotNull("Inferred RUN should activate STRIDES sensor automatically", device.mStridesSensor)
            assertNotNull("MovingSpeedSummary should be published to BANALService", service.movingSpeedSummary)
            assertTrue(service.movingSpeedSummary!!.movingSampleCount >= 10)

            // Reset clears discriminator & Tier 3 BANALService state cleanly
            device.triggerResetForTest()
            assertEquals(BSportType.UNKNOWN, service.bSportType)
            assertEquals(BSportType.UNKNOWN, device.sportDiscriminator.inferredBSportType)
            assertNull(service.movingSpeedSummary)
        } finally {
            try { service.unregisterReceiver(userChangedReceiver) } catch (_: Throwable) {}
            device.shutDown()
            sensorManager.shutDown()
            controller.destroy()
        }
    }

    /**
     * TST-FIL-046.3: End-to-end Robolectric verification on real `BANALService` +
     * `SpeedAndLocationDevice_ATTFused` with ZERO test hints:
     * 1. Tier 4 `comm.setResolvedBSportType(BSportType.RUN)` activates walking `CADENCE` & `STRIDES`
     *    within `<= 1.0 s` in `WALKING_TRANSITION` (zero `SPORT_TYPE_CHANGED_BY_USER_INTENT`,
     *    zero per-sample SQLite lookups, `getAuthoritativeBSportType() == UNKNOWN`).
     * 2. Explicit Tier 1 user `OTHER` (`sportId = 1`, `UNKNOWN`) suppresses walking `CADENCE`/`STRIDES`.
     * 3. Tier 3 `BIKE` overrides Tier 4 `RUN` and immediately unregisters `STRIDES`.
     * 4. Watchpoint 1: `WALKING_TRANSITION (UNKNOWN)` -> `BIKE_FRONT_POCKET (UNKNOWN)` immediately
     *    unregisters `STRIDES` before Tier 3 infers `BIKE`.
     */
    @Test
    fun tier4ResolvedWalk_activatesCadenceAndStridesAndRespectsOtherAndBikeGuards_tstFil046_3() {
        shadowOf(appContext as android.app.Application).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
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
            override fun elapsedRealtimeNanos(): Long = fakeClockMs * 1_000_000L
            override fun hasActivityRecognitionPermission(): Boolean = false
            fun triggerResetForTest() { onAccumulatorsReset() }
        }
        val userChangedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val userChangedReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                if (intent?.action == BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT) {
                    userChangedCount.incrementAndGet()
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            service,
            userChangedReceiver,
            android.content.IntentFilter(BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        try {
            val walkStepHz = 1.75 // 105 steps/min = 52.5 strides/min
            // Phase 1: Establish WALKING_TRANSITION via 5 s of 1.3 m/s GNSS + 50-Hz ACC (zero STEP_DETECTOR)
            for (sec in 0 until 5) {
                for (k in 0 until 50) {
                    fakeClockMs = 1000L + sec * 1000L + k * 20L
                    val tSec = fakeClockMs / 1000.0
                    val halfTilt = (Math.toRadians(18.0 + 4.0 * sin(2.0 * PI * (walkStepHz / 2.0) * tSec)) * 0.5).toFloat()
                    device.onSensorChanged(createSyntheticSensorEvent(Sensor.TYPE_GAME_ROTATION_VECTOR, fakeClockMs * 1_000_000L, floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())))
                    device.onSensorChanged(createSyntheticSensorEvent(Sensor.TYPE_ACCELEROMETER, fakeClockMs * 1_000_000L, floatArrayOf(0.3f, (9.81 + 2.2 * sin(2.0 * PI * walkStepHz * tSec)).toFloat(), 0.4f)))
                }
                val loc = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                    latitude = 48.137 + sec * 0.000012; longitude = 11.576; accuracy = 4.0f; speed = 1.3f; time = fakeClockMs; elapsedRealtimeNanos = fakeClockMs * 1_000_000L
                }
                device.onLocationChanged(loc)
            }
            assertEquals(PhoneCarriageMode.WALKING_TRANSITION.code, device.mActivityAndCarriageModeSensor?.value)
            assertNull("Prior to Tier 4 RUN and < 6 s dwell, STRIDES must stay null", device.mStridesSensor)

            // Phase 2: TrackerService.onSearchingFinished() sets Tier 4 RUN -> activates within <= 1 s, 0 SQLite, 0 user broadcast
            comm.setResolvedBSportType(BSportType.RUN)
            assertEquals(BSportType.UNKNOWN, service.bSportType)
            assertEquals(BSportType.UNKNOWN, service.authoritativeBSportType)
            assertEquals(BSportType.RUN, service.effectiveBSportType)

            val walkStart2 = fakeClockMs
            for (sec in 0 until 7) {
                for (k in 1..50) {
                    fakeClockMs = walkStart2 + sec * 1000L + k * 20L
                    val tSec = fakeClockMs / 1000.0
                    val halfTilt = (Math.toRadians(18.0 + 4.0 * sin(2.0 * PI * (walkStepHz / 2.0) * tSec)) * 0.5).toFloat()
                    device.onSensorChanged(createSyntheticSensorEvent(Sensor.TYPE_GAME_ROTATION_VECTOR, fakeClockMs * 1_000_000L, floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())))
                    device.onSensorChanged(createSyntheticSensorEvent(Sensor.TYPE_ACCELEROMETER, fakeClockMs * 1_000_000L, floatArrayOf(0.3f, (9.81 + 2.2 * sin(2.0 * PI * walkStepHz * tSec)).toFloat(), 0.4f)))
                }
                val loc = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                    latitude = 48.1371 + sec * 0.000012; longitude = 11.576; accuracy = 4.0f; speed = 1.3f; time = fakeClockMs; elapsedRealtimeNanos = fakeClockMs * 1_000_000L
                }
                device.onLocationChanged(loc)
            }
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals("Tier 4 resolved RUN must NEVER emit SPORT_TYPE_CHANGED_BY_USER_INTENT", 0, userChangedCount.get())
            verify(exactly = 0) { spyRepo.getBSportType(any()) }
            assertNotNull("Tier 4 RUN must register CADENCE in WALKING_TRANSITION", device.mCadenceSensor)
            assertNotNull("Tier 4 RUN must register STRIDES in WALKING_TRANSITION", device.mStridesSensor)
            val walkCad = (sensorManager.getSensor(SensorType.CADENCE)?.value as? Number)?.toDouble()
            val walkStrides = (sensorManager.getSensor(SensorType.STRIDES)?.value as? Number)?.toInt() ?: 0
            assertNotNull(walkCad)
            assertTrue("Expected walking cadence in 45..60 spm (got $walkCad)", walkCad!! in 45.0..60.0)
            assertTrue("Expected walking strides >= 2 (got $walkStrides)", walkStrides >= 2)

            // Phase 3: Tier 3 kinematic BIKE overrides Tier 4 RUN and immediately tears down STRIDES
            service.setInferredSportTelemetry(BSportType.BIKE, null)
            fakeClockMs += 1100L
            device.handleSportTypeChanged(fakeClockMs)
            assertNull("Tier 3 BIKE must immediately unregister STRIDES", device.mStridesSensor)

            // Phase 4: Explicit Tier 1 user OTHER (sportId = 1, UNKNOWN) suppresses walking CADENCE & STRIDES
            device.triggerResetForTest()
            comm.setUserSelectedSportTypeId(1L)
            shadowOf(Looper.getMainLooper()).idle()
            val otherStart = fakeClockMs
            for (sec in 0 until 8) {
                for (k in 1..50) {
                    fakeClockMs = otherStart + sec * 1000L + k * 20L
                    val tSec = fakeClockMs / 1000.0
                    device.onSensorChanged(createSyntheticSensorEvent(Sensor.TYPE_ACCELEROMETER, fakeClockMs * 1_000_000L, floatArrayOf(0.3f, (9.81 + 2.2 * sin(2.0 * PI * walkStepHz * tSec)).toFloat(), 0.4f)))
                }
                val loc = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                    latitude = 48.138 + sec * 0.000012; longitude = 11.576; accuracy = 4.0f; speed = 1.3f; time = fakeClockMs; elapsedRealtimeNanos = fakeClockMs * 1_000_000L
                }
                device.onLocationChanged(loc)
            }
            assertNull("Explicit user OTHER must suppress walking STRIDES", device.mStridesSensor)
            assertNull("Explicit user OTHER must suppress walking CADENCE", device.mCadenceSensor)

            // Phase 5 (Watchpoint 1): Sustained UNKNOWN walk (>= 25 s) -> BIKE_FRONT_POCKET (still UNKNOWN) unregisters STRIDES
            device.triggerResetForTest()
            BANALService::class.java.getDeclaredField("mHaveUserSelectedSportType").apply { isAccessible = true; setBoolean(service, false) }
            val wpStart = fakeClockMs
            for (sec in 0 until 34) { // 4 s classify + 30 s (>= 25 s UNKNOWN walk dwell)
                for (k in 1..50) {
                    fakeClockMs = wpStart + sec * 1000L + k * 20L
                    val tSec = fakeClockMs / 1000.0
                    val halfTilt = (Math.toRadians(18.0 + 4.0 * sin(2.0 * PI * (walkStepHz / 2.0) * tSec)) * 0.5).toFloat()
                    device.onSensorChanged(createSyntheticSensorEvent(Sensor.TYPE_GAME_ROTATION_VECTOR, fakeClockMs * 1_000_000L, floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())))
                    device.onSensorChanged(createSyntheticSensorEvent(Sensor.TYPE_ACCELEROMETER, fakeClockMs * 1_000_000L, floatArrayOf(0.3f, (9.81 + 2.2 * sin(2.0 * PI * walkStepHz * tSec)).toFloat(), 0.4f)))
                }
                val loc = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                    latitude = 48.139 + sec * 0.000012; longitude = 11.576; accuracy = 4.0f; speed = 1.3f; time = fakeClockMs; elapsedRealtimeNanos = fakeClockMs * 1_000_000L
                }
                device.onLocationChanged(loc)
            }
            assertNotNull("Sustained UNKNOWN walk (>= 25 s) must register STRIDES without Tier 4", device.mStridesSensor)
            // Transition carriage mode to BIKE_FRONT_POCKET while sportHint stays UNKNOWN (before Tier 3 infers BIKE)
            device.setSportContextHintForTesting(com.atrainingtracker.banalservice.odometry.SportContextHint.UNKNOWN)
            val bikeStart = fakeClockMs
            for (sec in 0 until 7) {
                for (k in 1..50) {
                    fakeClockMs = bikeStart + sec * 1000L + k * 20L
                    val tSec = fakeClockMs / 1000.0
                    val halfTilt = (Math.toRadians(55.0 + 25.0 * sin(2.0 * PI * 1.25 * tSec)) * 0.5).toFloat()
                    device.onSensorChanged(createSyntheticSensorEvent(Sensor.TYPE_GAME_ROTATION_VECTOR, fakeClockMs * 1_000_000L, floatArrayOf(sin(halfTilt.toDouble()).toFloat(), 0f, 0f, cos(halfTilt.toDouble()).toFloat())))
                    device.onSensorChanged(createSyntheticSensorEvent(Sensor.TYPE_GYROSCOPE, fakeClockMs * 1_000_000L, floatArrayOf((2.2 * sin(2.0 * PI * 1.25 * tSec)).toFloat(), 0.5f, 0.3f)))
                    device.onSensorChanged(createSyntheticSensorEvent(Sensor.TYPE_ACCELEROMETER, fakeClockMs * 1_000_000L, floatArrayOf((2.2 * sin(2.0 * PI * 1.25 * tSec)).toFloat(), 9.81f, (1.5 * cos(2.0 * PI * 1.25 * tSec)).toFloat())))
                }
                val loc = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                    latitude = 48.140 + sec * 0.000055; longitude = 11.576; accuracy = 4.0f; speed = 6.0f; time = fakeClockMs; elapsedRealtimeNanos = fakeClockMs * 1_000_000L
                }
                device.onLocationChanged(loc)
            }
            assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET.code, device.mActivityAndCarriageModeSensor?.value)
            assertNull("Switching WALKING_TRANSITION -> BIKE_FRONT_POCKET under UNKNOWN must immediately unregister STRIDES", device.mStridesSensor)
        } finally {
            try { service.unregisterReceiver(userChangedReceiver) } catch (_: Throwable) {}
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
