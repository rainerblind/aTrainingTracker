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

package com.atrainingtracker.trainingtracker.tracker

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Intent
import android.content.ServiceConnection
import android.util.Log
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Modifier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying Android 14+ (API 34+) Foreground Service resilience and 1 Hz sampling loop
 * fault isolation for TrackerService (REQ-STB-002, TST-STB-002, ATT-622; REQ-TRK-027, TST-TRK-019, ATT-3981).
 */
class TrackerServiceResilienceTest {

    private class TestableTrackerService : TrackerService() {
        var stoppedSelf = false
        var notificationShown = false
        var bgLocationGranted = false
        var fgLocationGranted = true
        var startForegroundException: Exception? = null
        var startForegroundCalled = false
        var endWorkoutCalled = false
        var testPackageName = "com.atrainingtracker"

        override fun hasBackgroundLocationPermission(): Boolean = bgLocationGranted
        override fun hasLocationPermission(): Boolean = fgLocationGranted

        override fun showTrackingInterruptedNotification() {
            notificationShown = true
        }

        override fun performStopSelf() {
            stoppedSelf = true
        }

        override fun performStartForeground(id: Int, notification: Notification?, foregroundServiceType: Int) {
            startForegroundCalled = true
            startForegroundException?.let { throw it }
        }

        override fun endWorkout() {
            endWorkoutCalled = true
        }

        override fun sendBroadcast(intent: Intent?) {
            // No-op in unit tests
        }

        override fun getPackageName(): String = testPackageName

        override fun notifyTrackingStarted(workoutId: Long) {
            // No-op in unit tests
        }

        override fun performSuperOnDestroy() {
            // No-op in unit tests
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            // No-op in unit tests to prevent "Receiver not registered" exceptions
        }

        override fun unbindService(conn: ServiceConnection) {
            // No-op in unit tests
        }
    }

    private lateinit var service: TestableTrackerService

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        service = TestableTrackerService()
    }

    @After
    fun tearDown() {
        service.setTrackingInterruptedForTesting(true)
        service.onDestroy()
        unmockkAll()
    }

    @Test
    fun testRecreationWithoutBackgroundLocationStopsSelfAndReturnsSticky() {
        // Given: The service is being recreated by Android OS after process death (intent == null)
        // and ACCESS_BACKGROUND_LOCATION is NOT granted.
        service.bgLocationGranted = false

        // When: onStartCommand is invoked with a null intent
        val result = service.onStartCommand(null, 0, 1)

        // Then:
        // 1. Service returns START_STICKY to preserve Android service lifecycle contract
        assertEquals(Service.START_STICKY, result)
        // 2. Service immediately stops itself to avoid fatal background FGS violation on Android 14+
        assertTrue("stopSelf() must be called when recreated in background without permission", service.stoppedSelf)
        // 3. User is notified that tracking was interrupted with a tap-to-resume banner
        assertTrue("Tracking interrupted notification must be shown to user", service.notificationShown)
        // 4. startForeground must NOT be called
        assertFalse("startForeground must not be attempted when background permission is missing", service.startForegroundCalled)
        // 5. Interrupted flag is set
        assertTrue("mTrackingInterrupted flag must be set to true", service.isTrackingInterrupted)
    }

    @Test
    fun testSecurityExceptionDuringStartForegroundCaughtGracefully() {
        // Given: startForeground will throw a SecurityException (e.g. Android 14+ FGS location restriction)
        service.bgLocationGranted = true
        service.startForegroundException = SecurityException("Starting FGS with type location requires ACCESS_BACKGROUND_LOCATION")

        val mockApp = mockk<TrainingApplication>(relaxed = true)
        val mockNotification = mockk<Notification>(relaxed = true)
        every { mockApp.searchingAndTrackingNotification } returns mockNotification

        // Inject mock application into service
        val appField = TrackerService::class.java.getDeclaredField("mTrainingApplication")
        appField.isAccessible = true
        appField.set(service, mockApp)

        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.RESUME_SERVICE_RECREATION.name

        // When: onStartCommand is called
        val result = service.onStartCommand(intent, 0, 1)

        // Then:
        // 1. Exception is caught, method does not crash and returns START_STICKY
        assertEquals(Service.START_STICKY, result)
        // 2. Service stops itself
        assertTrue("stopSelf() must be called when startForeground fails", service.stoppedSelf)
        // 3. User notification is displayed
        assertTrue("User must be notified about tracking interruption", service.notificationShown)
        // 4. Tracking interrupted flag is true
        assertTrue("isTrackingInterrupted must be true", service.isTrackingInterrupted)
    }

    @Test
    fun testIllegalStateExceptionDuringStartForegroundCaughtGracefully() {
        // Given: startForeground throws IllegalStateException (e.g. ForegroundServiceStartNotAllowedException)
        service.bgLocationGranted = true
        service.startForegroundException = IllegalStateException("ForegroundServiceStartNotAllowedException: Service.startForeground() not allowed due to mAllowStartForeground false")

        val mockApp = mockk<TrainingApplication>(relaxed = true)
        val mockNotification = mockk<Notification>(relaxed = true)
        every { mockApp.searchingAndTrackingNotification } returns mockNotification

        val appField = TrackerService::class.java.getDeclaredField("mTrainingApplication")
        appField.isAccessible = true
        appField.set(service, mockApp)

        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.RESUME_SERVICE_RECREATION.name

        // When: onStartCommand is called
        val result = service.onStartCommand(intent, 0, 1)

        // Then:
        assertEquals(Service.START_STICKY, result)
        assertTrue("stopSelf() must be called when IllegalStateException is thrown", service.stoppedSelf)
        assertTrue("Notification must be shown", service.notificationShown)
        assertTrue("isTrackingInterrupted must be true", service.isTrackingInterrupted)
    }

    @Test
    fun testOnDestroySkipsEndWorkoutWhenTrackingInterrupted() {
        // Given: Tracking was marked as interrupted
        service.setTrackingInterruptedForTesting(true)

        // When: onDestroy is invoked
        service.onDestroy()

        // Then: endWorkout must NOT be called so the unfinished workout data is preserved
        assertFalse("endWorkout() must NOT be called if tracking was interrupted", service.endWorkoutCalled)
    }

    @Test
    fun testOnDestroyCallsEndWorkoutWhenTrackingNotInterrupted() {
        // Given: Tracking was NOT interrupted (normal stop)
        service.setTrackingInterruptedForTesting(false)

        // When: onDestroy is invoked
        service.onDestroy()

        // Then: endWorkout() must be called to properly finalize and export the workout
        assertTrue("endWorkout() must be called when service stops normally", service.endWorkoutCalled)
    }

    /**
     * TST-TRK-019.1: Verifies that an unchecked exception thrown during a 1 Hz sampling tick
     * in TrackerService.tracker.run() is caught, logged, and counted without terminating
     * subsequent ticks on a ScheduledExecutorService.scheduleAtFixedRate loop (REQ-TRK-027.1).
     */
    @Test
    fun testSamplingLoopContinuesAfterTransientUncheckedException() {
        mockkStatic(TrainingApplication::class)
        mockkStatic(SettingsDataStoreJavaHelper::class)
        mockkStatic(SportTypeDatabaseManager::class)
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } just Runs
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } just Runs
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } just Runs

        val mockAutoPauseConfig = mockk<AutoPauseConfig>(relaxed = true)
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isManualPauseLocked() } returns false
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC
        every { SettingsDataStoreJavaHelper.getAutoPauseConfig(any()) } returns mockAutoPauseConfig

        val mockSportDbManager = mockk<SportTypeDatabaseManager>(relaxed = true)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDbManager
        every { mockSportDbManager.getUIName(any()) } returns "Run"

        val mockApp = mockk<TrainingApplication>(relaxed = true)
        val appField = TrackerService::class.java.getDeclaredField("mTrainingApplication")
        appField.isAccessible = true
        appField.set(service, mockApp)

        // Set incomplete table initialization future so async DB callback doesn't touch real SQLite in this test
        val initFutureField = TrackerService::class.java.getDeclaredField("mTableInitializationFuture")
        initFutureField.isAccessible = true
        initFutureField.set(service, CompletableFuture<Void>())

        val tickCounter = AtomicInteger(0)
        val threeTicksLatch = CountDownLatch(3)

        every { mockApp.updateTimeAndDistanceToNotification(any(), any(), any()) } answers {
            threeTicksLatch.countDown()
        }

        val mockBanal = mockk<BANALServiceComm>(relaxed = true)
        every { mockBanal.databaseIdsOfActiveRemoteDevices } returns emptyList()
        every { mockBanal.bSportType } returns BSportType.RUN
        every { mockBanal.sportTypeId } returns 1L

        every { mockBanal.getBestSensorData(SensorType.TIME_TOTAL) } answers {
            val tick = tickCounter.incrementAndGet()
            if (tick == 1) {
                threeTicksLatch.countDown()
                throw RuntimeException("Simulated transient sensor fault on tick 1")
            }
            SensorData(SensorType.TIME_TOTAL, tick, "$tick", "clock")
        }
        every { mockBanal.getBestSensorData(SensorType.SPEED_mps) } returns null
        every { mockBanal.getBestSensorData(SensorType.LATITUDE) } returns null
        every { mockBanal.getBestSensorData(SensorType.LONGITUDE) } returns null
        every { mockBanal.getBestSensorData(SensorType.TIME_ACTIVE) } answers {
            val t = tickCounter.get()
            SensorData(SensorType.TIME_ACTIVE, t, "$t", "clock")
        }
        every { mockBanal.getBestSensorData(SensorType.DISTANCE_m) } returns SensorData(SensorType.DISTANCE_m, 100.0, "100.0", "gps")
        every { mockBanal.allSensorData } answers {
            val currentTick = tickCounter.get()
            listOf(
                SensorData<Number>(SensorType.TIME_TOTAL, currentTick, "$currentTick", "clock"),
                SensorData<Number>(SensorType.TIME_ACTIVE, currentTick, "$currentTick", "clock")
            )
        }

        service.mBanalService = mockBanal

        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val future = scheduler.scheduleAtFixedRate(service.tracker, 0L, 10L, TimeUnit.MILLISECONDS)
            assertTrue("Expected at least 3 ticks to execute despite tick 1 throwing RuntimeException", threeTicksLatch.await(2, TimeUnit.SECONDS))
            future.cancel(false)
        } finally {
            scheduler.shutdownNow()
        }

        // Verify tick 1 error was caught and logged, and subsequent ticks (tick >= 3) succeeded and updated state
        assertEquals(1, service.samplingErrorCount)
        assertTrue("Expected mTimeTotal_s to advance on subsequent ticks after tick 1 exception", service.getTimeTotal_s() >= 3)
        assertTrue("Expected mTimeActive_s to advance on subsequent ticks after tick 1 exception", service.getTimeActive_s() >= 3)
        verify(atLeast = 1) { Log.e("TrackerService", any<String>(), any<Throwable>()) }
    }

    /**
     * TST-TRK-019.2: Verifies that mBanalService is declared volatile and snapshotted into a local
     * variable so concurrent unbinding (mBanalService = null on main thread) mid-tick does not cause
     * a NullPointerException in tracker.run() or sampleAndWriteToDb() (REQ-TRK-027.3).
     */
    @Test
    fun testVolatileBanalServiceAndLocalSnapshotPreventsMidTickNullPointerException() {
        // 1. Assert mBanalService field has the volatile modifier
        val banalField = TrackerService::class.java.getDeclaredField("mBanalService")
        assertTrue(
            "mBanalService must be declared volatile for cross-thread visibility",
            Modifier.isVolatile(banalField.modifiers)
        )

        mockkStatic(TrainingApplication::class)
        mockkStatic(SettingsDataStoreJavaHelper::class)
        mockkStatic(SportTypeDatabaseManager::class)
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } just Runs
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } just Runs
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } just Runs

        val mockAutoPauseConfig = mockk<AutoPauseConfig>(relaxed = true)
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isManualPauseLocked() } returns false
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC
        every { SettingsDataStoreJavaHelper.getAutoPauseConfig(any()) } returns mockAutoPauseConfig

        val mockSportDbManager = mockk<SportTypeDatabaseManager>(relaxed = true)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDbManager
        every { mockSportDbManager.getUIName(any()) } returns "Bike"

        val mockApp = mockk<TrainingApplication>(relaxed = true)
        val appField = TrackerService::class.java.getDeclaredField("mTrainingApplication")
        appField.isAccessible = true
        appField.set(service, mockApp)

        val initFutureField = TrackerService::class.java.getDeclaredField("mTableInitializationFuture")
        initFutureField.isAccessible = true
        initFutureField.set(service, CompletableFuture<Void>())

        val mockBanal = mockk<BANALServiceComm>(relaxed = true)
        every { mockBanal.databaseIdsOfActiveRemoteDevices } returns emptyList()
        every { mockBanal.bSportType } returns BSportType.BIKE
        every { mockBanal.sportTypeId } returns 2L

        // Simulate onServiceDisconnected setting service.mBanalService = null right in the middle of the tick!
        every { mockBanal.getBestSensorData(SensorType.TIME_TOTAL) } answers {
            service.mBanalService = null
            SensorData(SensorType.TIME_TOTAL, 42, "42", "clock")
        }
        every { mockBanal.getBestSensorData(SensorType.SPEED_mps) } returns null
        every { mockBanal.getBestSensorData(SensorType.LATITUDE) } returns null
        every { mockBanal.getBestSensorData(SensorType.LONGITUDE) } returns null
        every { mockBanal.getBestSensorData(SensorType.TIME_ACTIVE) } returns SensorData(SensorType.TIME_ACTIVE, 40, "40", "clock")
        every { mockBanal.getBestSensorData(SensorType.DISTANCE_m) } returns SensorData(SensorType.DISTANCE_m, 500.0, "500.0", "gps")
        every { mockBanal.allSensorData } returns listOf(
            SensorData<Number>(SensorType.TIME_TOTAL, 42, "42", "clock"),
            SensorData<Number>(SensorType.TIME_ACTIVE, 40, "40", "clock"),
            SensorData<Number>(SensorType.DISTANCE_m, 500.0, "500.0", "gps")
        )

        service.mBanalService = mockBanal

        // When: tracker.run() executes while mBanalService becomes null mid-tick
        service.tracker.run()

        // Then: Zero exceptions were thrown (error count == 0) because the local snapshot remained non-null
        assertEquals("Mid-tick mBanalService = null must not throw NPE when snapshotted locally", 0, service.samplingErrorCount)
        assertEquals(42, service.getTimeTotal_s())
        assertEquals(40, service.getTimeActive_s())
        assertEquals(500.0, service.getDistanceTotal_m(), 1e-6)
    }
}
