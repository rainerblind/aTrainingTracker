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
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
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
        val broadcastActions = mutableListOf<String?>()
        var onStartServiceHook: (() -> Unit)? = null

        override fun getApplicationContext(): Context = this

        override fun startService(service: Intent?): ComponentName? {
            onStartServiceHook?.invoke()
            startedServiceIntent = service
            return null
        }

        override fun stopService(name: Intent?): Boolean {
            stoppedServiceIntent = name
            return true
        }

        override fun sendBroadcast(intent: Intent) {
            broadcastActions.add(intent.action)
        }

        override fun getPackageName(): String = "com.atrainingtracker.trainingtracker"

        override fun notifyTrackingStateChanged() {
            stateChangedNotified = true
        }
    }

    private lateinit var app: TestableTrainingApplication
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockRepo: com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
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
        mockRepo = mockk<com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository>(relaxed = true)
        val mockSportFlow = MutableStateFlow(BSportType.UNKNOWN)
        val mockSpeedFlow = MutableStateFlow<Double?>(null)
        every { mockRepo.bSportType } returns mockSportFlow
        every { mockRepo.currentSpeed } returns mockSpeedFlow
        every { mockRepo.hasSpeedSource } returns false
        every { com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository.Companion.getInstance(any()) } returns mockRepo

        // startTracking() evaluates "Start in Auto-Paused" (ATT-4840). Without this stub, the real
        // TuningPreferencesDataStore initializes on Dispatchers.Default with a non-mocked Context
        // (getFilesDir), and the resulting uncaught coroutine exception leaks into whichever
        // runTest-based test runs next in the same JVM (e.g. BatterySaverControllerTest).
        mockkStatic(com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper::class)
        every {
            com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getAutoPauseConfig(any())
        } returns com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig()

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
        TrackerService.resetFgsStartStateForTesting()
        TrainingApplication.cTrackingMode = TrackingMode.READY
    }

    @After
    fun tearDown() {
        unmockkAll()
        TrackerService.setInstanceForTesting(null)
        TrackerService.resetFgsStartStateForTesting()
        TrainingApplication.cTrackingMode = TrackingMode.READY
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)
        TrainingApplication.setManualPauseLock(false, null)
        TrainingApplication.setAutoPaused(false, null)
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
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

    /**
     * TST-TRK-044.2: Verifies that when Start in Auto-Paused is enabled and sport auto-pause is enabled,
     * but hasSpeedSource is false (indoor HR-only workout), startTracking() initializes in TRACKING (REQ-TRK-044, ATT-4925).
     */
    @Test
    fun testStartTracking_whenStartInAutoPauseEnabled_butNoSpeedSource_startsInTracking() {
        val config = com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig(
            bikeEnabled = true,
            startInAutoPauseEnabled = true
        )
        every {
            com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getAutoPauseConfig(any())
        } returns config
        every { mockRepo.bSportType } returns MutableStateFlow(BSportType.BIKE)
        every { mockRepo.hasSpeedSource } returns false

        every { ContextCompat.startForegroundService(any(), any()) } just Runs

        app.startTracking()

        assertEquals("Must start in TRACKING when no speed source exists", TrackingMode.TRACKING, TrainingApplication.cTrackingMode)
        assertFalse("Must not be auto-paused", TrainingApplication.isAutoPaused())
    }

    /**
     * TST-TRK-044.2: Verifies that when Start in Auto-Paused is enabled and sport auto-pause is enabled,
     * and hasSpeedSource is true, startTracking() initializes directly in PAUSED (isAutoPaused = true) (REQ-TRK-044, ATT-4925).
     */
    @Test
    fun testStartTracking_whenStartInAutoPauseEnabled_andSpeedSourcePresent_startsInAutoPaused() {
        val config = com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig(
            bikeEnabled = true,
            startInAutoPauseEnabled = true,
            bikeSpeedThreshold = 3
        )
        every {
            com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getAutoPauseConfig(any())
        } returns config
        every { mockRepo.bSportType } returns MutableStateFlow(BSportType.BIKE)
        every { mockRepo.hasSpeedSource } returns true
        every { mockRepo.currentSpeed } returns MutableStateFlow<Double?>(null)

        every { ContextCompat.startForegroundService(any(), any()) } just Runs

        app.startTracking()

        assertEquals("Must initialize in PAUSED", TrackingMode.PAUSED, TrainingApplication.cTrackingMode)
        assertTrue("Must be auto-paused", TrainingApplication.isAutoPaused())
    }

    /**
     * TST-TRK-094.3 (ATT-6034): Verifies exact ordering in stopTracking(): pause locks are cleared
     * BEFORE ACTION_STOP dispatch, while cTrackingMode=READY, sActiveWorkoutId=-1, and clearBreadcrumbs()
     * execute AFTER dispatch without broadcasting RESET_ACCUMULATORS_INTENT.
     */
    @Test
    fun testStopTracking_resetsPauseLocksBeforeDispatchAndActiveStateAfterDispatch() {
        val mockService = mockk<TrackerService>(relaxed = true)
        TrackerService.setInstanceForTesting(mockService)

        TrainingApplication.cTrackingMode = TrackingMode.PAUSED
        TrainingApplication.setActiveWorkoutIdForTesting(42L)
        TrainingApplication.setManualPauseLock(true, app)
        TrainingApplication.setAutoPaused(true, app)

        var seenManualPauseDuringDispatch = true
        var seenAutoPauseDuringDispatch = true
        var seenModeDuringDispatch: TrackingMode? = null
        var seenActiveWorkoutIdDuringDispatch = -1L

        app.onStartServiceHook = {
            seenManualPauseDuringDispatch = TrainingApplication.isManualPauseLocked()
            seenAutoPauseDuringDispatch = TrainingApplication.isAutoPaused()
            seenModeDuringDispatch = TrainingApplication.cTrackingMode
            seenActiveWorkoutIdDuringDispatch = TrainingApplication.getActiveWorkoutID()
        }

        val stopTrackingMethod = TrainingApplication::class.java.getDeclaredMethod("stopTracking")
        stopTrackingMethod.isAccessible = true
        stopTrackingMethod.invoke(app)

        assertFalse("Manual pause lock must be cleared BEFORE ACTION_STOP dispatch", seenManualPauseDuringDispatch)
        assertFalse("Auto-pause flag must be cleared BEFORE ACTION_STOP dispatch", seenAutoPauseDuringDispatch)
        assertEquals("TrackingMode must NOT reset to READY until AFTER dispatch", TrackingMode.PAUSED, seenModeDuringDispatch)
        assertEquals("Active workout ID must NOT reset until AFTER dispatch", 42L, seenActiveWorkoutIdDuringDispatch)

        assertEquals("TrackingMode must be READY after stopTracking", TrackingMode.READY, TrainingApplication.cTrackingMode)
        assertEquals("Active workout ID must be -1 after stopTracking", -1L, TrainingApplication.getActiveWorkoutID())
        assertTrue("stopTracking must not broadcast RESET_ACCUMULATORS_INTENT", app.broadcastActions.isEmpty())
        verify(exactly = 1) { mockRepo.clearBreadcrumbs() }
    }

    /**
     * TST-TRK-094.3 (ATT-6034): Verifies static resetTrackingStateOnAbort() handles both null
     * and non-null cAppContext cleanly.
     */
    @Test
    fun testResetTrackingStateOnAbort_nullAndNonNullContextParity() {
        TrainingApplication.cTrackingMode = TrackingMode.TRACKING
        TrainingApplication.setActiveWorkoutIdForTesting(55L)
        TrainingApplication.setManualPauseLock(true, app)
        TrainingApplication.setAutoPaused(true, app)
        setStaticField(TrainingApplication::class.java, "cAppContext", null)

        TrainingApplication.resetTrackingStateOnAbort()

        assertEquals(TrackingMode.READY, TrainingApplication.cTrackingMode)
        assertEquals(-1L, TrainingApplication.getActiveWorkoutID())
        assertFalse(TrainingApplication.isManualPauseLocked())
        assertFalse(TrainingApplication.isAutoPaused())
        verify(exactly = 0) { mockRepo.clearBreadcrumbs() }

        // Now with non-null cAppContext
        TrainingApplication.cTrackingMode = TrackingMode.TRACKING
        TrainingApplication.setActiveWorkoutIdForTesting(56L)
        setStaticField(TrainingApplication::class.java, "cAppContext", app)

        TrainingApplication.resetTrackingStateOnAbort()

        assertEquals(TrackingMode.READY, TrainingApplication.cTrackingMode)
        assertEquals(-1L, TrainingApplication.getActiveWorkoutID())
        assertEquals(1, app.broadcastActions.size)
        verify(exactly = 1) { mockRepo.clearBreadcrumbs() }
    }

    /**
     * TST-TRK-094.3 (ATT-6034): Verifies onTrackingAborted() clears pause locks, resets active
     * tracking state, cancels notification, broadcasts RESET_ACCUMULATORS_INTENT, and clears breadcrumbs.
     */
    @Test
    fun testOnTrackingAborted_resetsAllStateCancelsNotificationAndBroadcastsReset() {
        val mockNotifManager = mockk<androidx.core.app.NotificationManagerCompat>(relaxed = true)
        val notifField = TrainingApplication::class.java.getDeclaredField("mNotificationManager").apply { isAccessible = true }
        notifField.set(app, mockNotifManager)
        val workoutIdField = TrainingApplication::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(app, 77L)

        TrainingApplication.cTrackingMode = TrackingMode.TRACKING
        TrainingApplication.setActiveWorkoutIdForTesting(77L)
        TrainingApplication.setManualPauseLock(true, app)
        TrainingApplication.setAutoPaused(true, app)

        app.onTrackingAborted()

        assertEquals(TrackingMode.READY, TrainingApplication.cTrackingMode)
        assertEquals(-1L, TrainingApplication.getActiveWorkoutID())
        assertEquals(-1L, workoutIdField.get(app))
        assertFalse(TrainingApplication.isManualPauseLocked())
        assertFalse(TrainingApplication.isAutoPaused())
        verify(exactly = 1) { mockNotifManager.cancel(TrainingApplication.TRACKING_NOTIFICATION_ID) }
        verify(exactly = 1) { mockRepo.clearBreadcrumbs() }
        assertEquals(1, app.broadcastActions.size)
        assertTrue(app.stateChangedNotified)
    }

    /**
     * TST-TRK-094.3 (ATT-6034): Verifies trackingStopped() preserves its distinct semantics and
     * does NOT reset cTrackingMode, sActiveWorkoutId, or pause locks.
     */
    @Test
    fun testTrackingStopped_preservesUntouchedSemantics_doesNotResetModeOrPauseLocks() {
        val mockNotifManager = mockk<androidx.core.app.NotificationManagerCompat>(relaxed = true)
        val notifField = TrainingApplication::class.java.getDeclaredField("mNotificationManager").apply { isAccessible = true }
        notifField.set(app, mockNotifManager)
        val workoutIdField = TrainingApplication::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(app, 99L)

        mockkStatic(WorkoutNavigationEvents::class)
        every { WorkoutNavigationEvents.triggerEdit(any()) } just Runs

        TrainingApplication.cTrackingMode = TrackingMode.TRACKING
        TrainingApplication.setActiveWorkoutIdForTesting(99L)
        TrainingApplication.setManualPauseLock(true, app)
        TrainingApplication.setAutoPaused(true, app)

        val trackingStoppedMethod = TrainingApplication::class.java.getDeclaredMethod("trackingStopped")
        trackingStoppedMethod.isAccessible = true
        trackingStoppedMethod.invoke(app)

        assertEquals("trackingStopped must NOT mutate cTrackingMode", TrackingMode.TRACKING, TrainingApplication.cTrackingMode)
        assertEquals("trackingStopped must NOT mutate sActiveWorkoutId", 99L, TrainingApplication.getActiveWorkoutID())
        assertTrue("trackingStopped must NOT clear manual pause lock", TrainingApplication.isManualPauseLocked())
        assertTrue("trackingStopped must NOT clear auto-pause flag", TrainingApplication.isAutoPaused())
        verify(exactly = 1) { mockRepo.clearBreadcrumbs() }
        verify(exactly = 1) { WorkoutNavigationEvents.triggerEdit(99L) }
        verify(exactly = 1) { mockNotifManager.cancel(TrainingApplication.TRACKING_NOTIFICATION_ID) }
        assertEquals(1, app.broadcastActions.size)
    }

    /**
     * TST-TRK-094.4 (ATT-6034): Characterization & unit test covering all 18 Strava sync
     * preference getters/setters, distinct "never" string resources, and null-prefs safety.
     */
    @Test
    fun testStravaSyncPreferences_all18MethodsDistinctKeysNeverStringsAndNullSafety() {
        val backingStrings = mutableMapOf<String, String>()
        val backingBooleans = mutableMapOf<String, Boolean>()
        val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { mockEditor.putString(any(), any()) } answers {
            backingStrings[firstArg()] = secondArg()
            mockEditor
        }
        every { mockEditor.putBoolean(any(), any()) } answers {
            backingBooleans[firstArg()] = secondArg()
            mockEditor
        }
        every { mockPrefs.edit() } returns mockEditor
        every { mockPrefs.getString(any(), any()) } answers {
            backingStrings[firstArg()] ?: secondArg()
        }
        every { mockPrefs.getBoolean(any(), any()) } answers {
            backingBooleans[firstArg()] ?: secondArg()
        }

        val mockCtx = mockk<Context>(relaxed = true)
        every { mockCtx.getString(R.string.lastUpdateOfEquipmentNever) } returns "equipment-never"
        every { mockCtx.getString(R.string.lastUpdateOfRoutesNever) } returns "routes-never"
        every { mockCtx.getString(R.string.lastUpdateOfSegmentsNever) } returns "segments-never"
        setStaticField(TrainingApplication::class.java, "cAppContext", mockCtx)

        // Default values with initialized prefs + distinct "never" string resources
        assertEquals("equipment-never", TrainingApplication.getLastUpdateTimeOfStravaEquipment())
        assertEquals("routes-never", TrainingApplication.getLastUpdateTimeOfStravaRoutes())
        assertEquals("segments-never", TrainingApplication.getLastUpdateTimeOfStravaSegments())
        assertTrue(TrainingApplication.isAutomatedStravaEquipmentSyncEnabled())
        assertTrue(TrainingApplication.isAutomatedStravaRoutesSyncEnabled())
        assertTrue(TrainingApplication.isAutomatedStravaSegmentsSyncEnabled())
        assertEquals("1", TrainingApplication.getStravaEquipmentSyncIntervalDays())
        assertEquals("1", TrainingApplication.getStravaRoutesSyncIntervalDays())
        assertEquals("1", TrainingApplication.getStravaSegmentsSyncIntervalDays())

        // Write & read back independently (ensuring no cross-entity key collision)
        TrainingApplication.setLastUpdateTimeOfStravaEquipment("2026-11-22 08:00")
        TrainingApplication.setLastUpdateTimeOfStravaRoutes("2026-11-22 09:00")
        TrainingApplication.setLastUpdateTimeOfStravaSegments("2026-11-22 10:00")
        TrainingApplication.setAutomatedStravaEquipmentSyncEnabled(false)
        TrainingApplication.setAutomatedStravaRoutesSyncEnabled(true)
        TrainingApplication.setAutomatedStravaSegmentsSyncEnabled(false)
        TrainingApplication.setStravaEquipmentSyncIntervalDays("7")
        TrainingApplication.setStravaRoutesSyncIntervalDays("14")
        TrainingApplication.setStravaSegmentsSyncIntervalDays("30")

        assertEquals("2026-11-22 08:00", TrainingApplication.getLastUpdateTimeOfStravaEquipment())
        assertEquals("2026-11-22 09:00", TrainingApplication.getLastUpdateTimeOfStravaRoutes())
        assertEquals("2026-11-22 10:00", TrainingApplication.getLastUpdateTimeOfStravaSegments())
        assertFalse(TrainingApplication.isAutomatedStravaEquipmentSyncEnabled())
        assertTrue(TrainingApplication.isAutomatedStravaRoutesSyncEnabled())
        assertFalse(TrainingApplication.isAutomatedStravaSegmentsSyncEnabled())
        assertEquals("7", TrainingApplication.getStravaEquipmentSyncIntervalDays())
        assertEquals("14", TrainingApplication.getStravaRoutesSyncIntervalDays())
        assertEquals("30", TrainingApplication.getStravaSegmentsSyncIntervalDays())

        // Null cSharedPreferences fallback safety (coding_standards.md §5.5)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
        assertEquals("equipment-never", TrainingApplication.getLastUpdateTimeOfStravaEquipment())
        assertEquals("routes-never", TrainingApplication.getLastUpdateTimeOfStravaRoutes())
        assertEquals("segments-never", TrainingApplication.getLastUpdateTimeOfStravaSegments())
        assertTrue(TrainingApplication.isAutomatedStravaEquipmentSyncEnabled())
        assertTrue(TrainingApplication.isAutomatedStravaRoutesSyncEnabled())
        assertTrue(TrainingApplication.isAutomatedStravaSegmentsSyncEnabled())
        assertEquals("1", TrainingApplication.getStravaEquipmentSyncIntervalDays())
        assertEquals("1", TrainingApplication.getStravaRoutesSyncIntervalDays())
        assertEquals("1", TrainingApplication.getStravaSegmentsSyncIntervalDays())

        // Null cAppContext + null cSharedPreferences returns "" cleanly without NPE
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
        assertEquals("", TrainingApplication.getLastUpdateTimeOfStravaEquipment())
        assertEquals("", TrainingApplication.getLastUpdateTimeOfStravaRoutes())
        assertEquals("", TrainingApplication.getLastUpdateTimeOfStravaSegments())
        // Setters with null prefs must be no-op without throwing
        TrainingApplication.setLastUpdateTimeOfStravaEquipment("x")
        TrainingApplication.setAutomatedStravaRoutesSyncEnabled(false)
        TrainingApplication.setStravaSegmentsSyncIntervalDays("3")
    }

    /**
     * TST-TRK-095.1 (ATT-9498): Verifies rapid stopTracking() after startTracking() while
     * TrackerService.onCreate() has not yet run suppresses stopService() to prevent
     * ForegroundServiceDidNotStartInTimeException (REQ-TRK-095.1).
     */
    @Test
    fun testStopTracking_whenFgsStartPendingAndServiceNotYetRunning_suppressesStopService_TST_TRK_095_1() {
        TrackerService.setInstanceForTesting(null)
        every { ContextCompat.startForegroundService(any(), any()) } just Runs

        app.startTracking()
        assertFalse("TrackerService.onCreate has not yet run", TrackerService.isRunning())

        val stopTrackingMethod = TrainingApplication::class.java.getDeclaredMethod("stopTracking")
        stopTrackingMethod.isAccessible = true
        stopTrackingMethod.invoke(app)

        assertNull("stopService() MUST be suppressed while FGS start is pending", app.stoppedServiceIntent)
        assertNull("startService(ACTION_STOP) must not be called before TrackerService exists", app.startedServiceIntent)
        assertEquals(TrackingMode.READY, TrainingApplication.cTrackingMode)
    }

    /**
     * TST-TRK-095.1 (ATT-9498): Verifies a second startTracking() clears any earlier deferred
     * pre-start stop so the subsequent TrackerService.onStartCommand() runs normally.
     */
    @Test
    fun testStartTracking_afterDeferredPreStartStop_clearsPendingStopForNewStart_TST_TRK_095_1() {
        TrackerService.setInstanceForTesting(null)
        every { ContextCompat.startForegroundService(any(), any()) } just Runs

        app.startTracking()
        val stopTrackingMethod = TrainingApplication::class.java.getDeclaredMethod("stopTracking")
        stopTrackingMethod.isAccessible = true
        stopTrackingMethod.invoke(app)
        assertNull(app.stoppedServiceIntent)

        // Start a second session before AMS delivered the first start
        app.startTracking()
        assertEquals(TrackingMode.TRACKING, TrainingApplication.cTrackingMode)
        assertTrue(
            "FGS start must still be marked pending so another rapid stop is still guarded",
            TrackerService.requestStopIfForegroundStartPending()
        )
    }
}

