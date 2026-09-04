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
import android.content.Intent
import android.content.ServiceConnection
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Android 14+ (API 34+) Foreground Service resilience for TrackerService
 * (REQ-STB-002, TST-STB-002, ATT-622).
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
}
