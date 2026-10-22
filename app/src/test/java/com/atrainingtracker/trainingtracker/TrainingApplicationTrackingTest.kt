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

package com.atrainingtracker.trainingtracker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests verifying Foreground Service startup delegation and background exception shielding
 * for TrainingApplication (REQ-STB-014, TST-STB-014, ATT-2661).
 */
class TrainingApplicationTrackingTest {

    private class TestableTrainingApplication : TrainingApplication() {
        var stateChangedNotified = false
        var startedServiceIntent: Intent? = null
        var stoppedServiceIntent: Intent? = null

        override fun getApplicationContext(): Context = this

        override fun startService(service: Intent?): ComponentName? {
            startedServiceIntent = service
            return null
        }

        override fun stopService(name: Intent?): Boolean {
            stoppedServiceIntent = name
            return true
        }

        override fun sendBroadcast(intent: Intent) {
            // No-op for unit tests
        }

        override fun getPackageName(): String = "com.atrainingtracker.trainingtracker"

        override fun notifyTrackingStateChanged() {
            stateChangedNotified = true
        }

        override fun startPebbleWatchapp() {
            // No-op for unit test
        }

        override fun stopPebbleWatchapp() {
            // No-op for unit test
        }
    }

    private lateinit var app: TestableTrainingApplication
    private lateinit var mockPrefs: SharedPreferences
    private val intentExtras = mutableMapOf<String, String>()
    private var intentAction: String? = null

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockPrefs = mockk(relaxed = true)
        every { mockPrefs.getBoolean(any(), any()) } returns false

        setStaticField(TrainingApplication::class.java, "cSharedPreferences", mockPrefs)

        mockkObject(com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository.Companion)
        val mockRepo = mockk<com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository>(relaxed = true)
        every { com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository.Companion.getInstance(any()) } returns mockRepo

        intentExtras.clear()
        intentAction = null
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().setAction(any()) } answers {
            intentAction = firstArg()
            self as Intent
        }
        every { anyConstructed<Intent>().action } answers { intentAction }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers {
            intentExtras[firstArg<String>()] = secondArg<String>()
            self as Intent
        }
        every { anyConstructed<Intent>().getStringExtra(any<String>()) } answers {
            intentExtras[firstArg<String>()]
        }

        mockkStatic(ContextCompat::class)
        mockkStatic(TrackerService::class)
        every { TrackerService.showTrackingInterruptedNotification(any()) } just Runs
        every { TrackerService.notifyManualResume() } just Runs

        app = TestableTrainingApplication()
        TrainingApplication.cTrackingMode = TrackingMode.READY
    }

    @After
    fun tearDown() {
        unmockkAll()
        TrainingApplication.cTrackingMode = TrackingMode.READY
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    @Test
    fun testStartTracking_normal_delegatesToStartForegroundService() {
        val capturedIntent = slot<Intent>()
        every { ContextCompat.startForegroundService(any(), capture(capturedIntent)) } just Runs

        app.startTracking()

        assertEquals("TrackingMode must transition to TRACKING", TrackingMode.TRACKING, TrainingApplication.cTrackingMode)
        assertTrue("isTracking() must return true", TrainingApplication.isTracking())

        verify(exactly = 1) { ContextCompat.startForegroundService(app, any()) }
        assertEquals(TrackerService.StartType.START_NORMAL.name, capturedIntent.captured.getStringExtra(TrackerService.START_TYPE))
        assertTrue("notifyTrackingStateChanged must be called", app.stateChangedNotified)
    }

    @Test
    fun testStartTracking_resumeFromCrash_passesResumeByUser() {
        TrainingApplication.setResumeFromCrash(true)
        val capturedIntent = slot<Intent>()
        every { ContextCompat.startForegroundService(any(), capture(capturedIntent)) } just Runs

        app.startTracking()

        assertEquals("TrackingMode must transition to TRACKING", TrackingMode.TRACKING, TrainingApplication.cTrackingMode)
        verify(exactly = 1) { ContextCompat.startForegroundService(app, any()) }
        assertEquals(TrackerService.StartType.RESUME_BY_USER.name, capturedIntent.captured.getStringExtra(TrackerService.START_TYPE))
        assertFalse("cResumeFromCrash must be reset to false", TrainingApplication.cResumeFromCrash)
    }

    @Test
    fun testStartTracking_whenIllegalStateExceptionThrown_recoversGracefully() {
        every { ContextCompat.startForegroundService(any(), any()) } throws IllegalStateException("Not allowed to start service: app is in background")

        // Must not throw exception
        app.startTracking()

        assertEquals("TrackingMode must reset to READY on IllegalStateException", TrackingMode.READY, TrainingApplication.cTrackingMode)
        assertFalse("isTracking() must return false", TrainingApplication.isTracking())
        verify(exactly = 1) { TrackerService.showTrackingInterruptedNotification(app) }
        assertTrue("notifyTrackingStateChanged must be invoked", app.stateChangedNotified)
    }

    @Test
    fun testStartTracking_whenSecurityExceptionThrown_recoversGracefully() {
        every { ContextCompat.startForegroundService(any(), any()) } throws SecurityException("Missing FGS permission")

        // Must not throw exception
        app.startTracking()

        assertEquals("TrackingMode must reset to READY on SecurityException", TrackingMode.READY, TrainingApplication.cTrackingMode)
        assertFalse("isTracking() must return false", TrainingApplication.isTracking())
        verify(exactly = 1) { TrackerService.showTrackingInterruptedNotification(app) }
        assertTrue("notifyTrackingStateChanged must be invoked", app.stateChangedNotified)
    }

    @Test
    fun testSetTracking_callsNotifyTrackingStateChanged() {
        app.stateChangedNotified = false
        app.setTracking()

        assertEquals(TrackingMode.TRACKING, TrainingApplication.cTrackingMode)
        assertTrue("notifyTrackingStateChanged must be invoked on setTracking", app.stateChangedNotified)
    }

    /**
     * TST-TRK-026.4: Verifies that TrainingApplication.stopTracking() dispatches ACTION_STOP via startService()
     * when TrackerService is running (REQ-TRK-034.1).
     */
    @Test
    fun testStopTracking_whenTrackerServiceIsRunning_dispatchesActionStopViaStartService() {
        val mockService = mockk<TrackerService>(relaxed = true)
        TrackerService.setInstanceForTesting(mockService)
        assertTrue(TrackerService.isRunning())

        TrainingApplication.cTrackingMode = TrackingMode.TRACKING

        val stopTrackingMethod = TrainingApplication::class.java.getDeclaredMethod("stopTracking")
        stopTrackingMethod.isAccessible = true
        stopTrackingMethod.invoke(app)

        assertEquals("TrackingMode must transition to READY", TrackingMode.READY, TrainingApplication.cTrackingMode)
        assertNotNull("startService must be invoked with Intent", app.startedServiceIntent)
        assertEquals(TrackerService.ACTION_STOP, app.startedServiceIntent?.action)
        assertNull("stopService must NOT be called when TrackerService is running", app.stoppedServiceIntent)
        assertTrue("notifyTrackingStateChanged must be invoked", app.stateChangedNotified)

        TrackerService.setInstanceForTesting(null)
    }

    /**
     * TST-TRK-026.4: Verifies that TrainingApplication.stopTracking() falls back to stopService()
     * when TrackerService is not running (REQ-TRK-034.1).
     */
    @Test
    fun testStopTracking_whenTrackerServiceNotRunning_fallsBackToStopService() {
        TrackerService.setInstanceForTesting(null)
        assertFalse(TrackerService.isRunning())

        TrainingApplication.cTrackingMode = TrackingMode.TRACKING

        val stopTrackingMethod = TrainingApplication::class.java.getDeclaredMethod("stopTracking")
        stopTrackingMethod.isAccessible = true
        stopTrackingMethod.invoke(app)

        assertEquals("TrackingMode must transition to READY", TrackingMode.READY, TrainingApplication.cTrackingMode)
        assertNull("startService must NOT be called when TrackerService is not running", app.startedServiceIntent)
        assertNotNull("stopService must be called as fallback", app.stoppedServiceIntent)
        assertTrue("notifyTrackingStateChanged must be invoked", app.stateChangedNotified)
    }

    @Test
    fun testResumeFromPaused_whenAutoPaused_setsWasAutoPausedTrue() {
        TrainingApplication.setAutoPaused(true, app)
        assertTrue(TrainingApplication.isAutoPaused())

        app.resumeFromPaused()

        assertTrue("wasAutoPaused must be true when resuming from auto-pause", TrainingApplication.wasAutoPaused())
        assertFalse("isAutoPaused must be cleared to false", TrainingApplication.isAutoPaused())
        assertEquals(TrackingMode.TRACKING, TrainingApplication.cTrackingMode)
        assertTrue(app.stateChangedNotified)
    }

    @Test
    fun testResumeFromPaused_whenManualPaused_setsWasAutoPausedFalse() {
        TrainingApplication.setAutoPaused(false, app)
        assertFalse(TrainingApplication.isAutoPaused())

        app.resumeFromPaused()

        assertFalse("wasAutoPaused must be false when resuming from manual pause", TrainingApplication.wasAutoPaused())
        assertFalse(TrainingApplication.isAutoPaused())
        assertEquals(TrackingMode.TRACKING, TrainingApplication.cTrackingMode)
        assertTrue(app.stateChangedNotified)
    }

    @Test
    fun testStopTracking_resetsWasAutoPaused() {
        TrainingApplication.setAutoPaused(true, app)
        app.resumeFromPaused()
        assertTrue(TrainingApplication.wasAutoPaused())

        val stopTrackingMethod = TrainingApplication::class.java.getDeclaredMethod("stopTracking")
        stopTrackingMethod.isAccessible = true
        stopTrackingMethod.invoke(app)

        assertFalse("wasAutoPaused must be reset to false on stopTracking", TrainingApplication.wasAutoPaused())
    }

    @Test
    fun testResetTrackingStateOnAbort_resetsWasAutoPaused() {
        TrainingApplication.setAutoPaused(true, app)
        app.resumeFromPaused()
        assertTrue(TrainingApplication.wasAutoPaused())

        TrainingApplication.resetTrackingStateOnAbort()

        assertFalse("wasAutoPaused must be reset to false on resetTrackingStateOnAbort", TrainingApplication.wasAutoPaused())
    }

    @Test
    fun testOnTrackingAborted_resetsWasAutoPaused() {
        TrainingApplication.setAutoPaused(true, app)
        app.resumeFromPaused()
        assertTrue(TrainingApplication.wasAutoPaused())

        app.onTrackingAborted()

        assertFalse("wasAutoPaused must be reset to false on onTrackingAborted", TrainingApplication.wasAutoPaused())
    }

    /**
     * TST-TRK-039.2: Verifies that updateTimeAndDistanceToNotification short-circuits
     * when tracking is stopped (TrackingMode.READY), preventing notification resurrection (REQ-TRK-039.3).
     */
    @Test
    fun testUpdateTimeAndDistanceToNotification_whenNotTracking_suppressesNotification() {
        TrainingApplication.cTrackingMode = TrackingMode.READY
        assertFalse("App must not be tracking", TrainingApplication.isTracking())

        // Must return cleanly without attempting to access or build notifications
        app.updateTimeAndDistanceToNotification(null, null, "Running")
    }
}

