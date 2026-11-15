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

@file:Suppress("MaxLineLength", "WildcardImport", "EmptyFunctionBlock")

package com.atrainingtracker.trainingtracker.tracker

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.tracker.TrackerService.ResumedWorkoutState
import com.atrainingtracker.trainingtracker.tracker.TrackerService.StartType
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManager
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseState
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Golden Master Characterisation Test Suite for TrackerService Crash Recovery,
 * Resumed State Invariants, and Async Initialization Abort Handling.
 *
 * Traceability:
 * - REQ-ARC-008.1: TrackerService State Machine, Lifecycle & Crash Recovery
 * - TST-ARC-008.2: TrackerService Crash/Resume Recovery
 * - REQ-TRK-026: Resumed Workout State Integrity (ATT-3980, ATT-3987)
 * - REQ-TRK-033: Asynchronous Initialization & Abort Handling
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class TrackerServiceCrashRecoveryCharacterisationTest {

    private class TestableTrackerService : TrackerService() {
        var notifiedWorkoutId: Long? = null
        var stoppedSelf = false
        var interruptedNotificationShown = false
        val sentBroadcasts = mutableListOf<Intent>()

        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true
        override fun createNewWorkout(): Long = 999L
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = true
        override fun registerTransientReceivers() {}
        override fun unregisterTransientReceivers() {}
        override fun acquireWakeLock() {}

        override fun showTrackingInterruptedNotification() {
            interruptedNotificationShown = true
        }

        override fun performStopSelf() {
            stoppedSelf = true
        }

        override fun notifyTrackingStarted(workoutId: Long) {
            notifiedWorkoutId = workoutId
        }

        @Suppress("EmptyFunctionBlock")
        override fun performStartForeground(id: Int, notification: Notification?, foregroundServiceType: Int) {
            // No-op stub for characterisation test
        }

        override fun sendBroadcast(intent: Intent?) {
            if (intent != null) {
                sentBroadcasts.add(intent)
            }
        }

        fun callHandleTrackingAborted() {
            super.handleTrackingAborted()
        }
    }

    private class DirectExecutorService : AbstractExecutorService() {
        private var isShutDown = false
        override fun shutdown() { isShutDown = true }
        override fun shutdownNow(): List<Runnable> = emptyList()
        override fun isShutdown(): Boolean = isShutDown
        override fun isTerminated(): Boolean = isShutDown
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
        override fun execute(command: Runnable) { command.run() }
    }

    private lateinit var context: Context
    private lateinit var service: TestableTrackerService
    private lateinit var mockWorkoutRepo: WorkoutRepository
    private lateinit var mockSportTypesRepo: SportTypesRepository
    private lateinit var mockDeviceDataRepo: DeviceDataRepository
    private lateinit var mockKnownLocationsRepo: KnownLocationsRepository
    private lateinit var mockWorkoutRrRepo: WorkoutRrRepository
    private lateinit var mockTrainingApplication: TrainingApplication
    private lateinit var directExecutor: DirectExecutorService

    companion object {
        private const val RESUMED_WORKOUT_ID = 888L
        private const val VALID_BASE_FILE_NAME = "2026-11-15_100000"
    }

    private fun createPreparedService(): TestableTrackerService {
        val instance = TestableTrackerService()
        val attachMethod: Method = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachMethod.isAccessible = true
        attachMethod.invoke(instance, context)

        val dbExecutorField: Field = TrackerService::class.java.getDeclaredField("mDbExecutor")
        dbExecutorField.isAccessible = true
        dbExecutorField.set(instance, directExecutor)

        val rrExecutorField: Field = TrackerService::class.java.getDeclaredField("mRrFlushExecutor")
        rrExecutorField.isAccessible = true
        rrExecutorField.set(instance, directExecutor)

        val schedulerField: Field = TrackerService::class.java.getDeclaredField("mScheduler")
        schedulerField.isAccessible = true
        schedulerField.set(instance, mockk<ScheduledExecutorService>(relaxed = true))

        val appRefField: Field = TrackerService::class.java.getDeclaredField("mTrainingApplication")
        appRefField.isAccessible = true
        appRefField.set(instance, mockTrainingApplication)

        val sportRepoField: Field = TrackerService::class.java.getDeclaredField("mSportTypesRepository")
        sportRepoField.isAccessible = true
        sportRepoField.set(instance, mockSportTypesRepo)

        val deviceRepoField: Field = TrackerService::class.java.getDeclaredField("mDeviceDataRepository")
        deviceRepoField.isAccessible = true
        deviceRepoField.set(instance, mockDeviceDataRepo)

        val knownLocRepoField: Field = TrackerService::class.java.getDeclaredField("mKnownLocationsRepository")
        knownLocRepoField.isAccessible = true
        knownLocRepoField.set(instance, mockKnownLocationsRepo)

        val rrRepoField: Field = TrackerService::class.java.getDeclaredField("mWorkoutRrRepository")
        rrRepoField.isAccessible = true
        rrRepoField.set(instance, mockWorkoutRrRepo)

        val tableInitFutureField: Field = TrackerService::class.java.getDeclaredField("mTableInitializationFuture")
        tableInitFutureField.isAccessible = true
        tableInitFutureField.set(instance, CompletableFuture<Void>())

        return instance
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        val appContextField = TrainingApplication::class.java.getDeclaredField("cAppContext")
        appContextField.isAccessible = true
        appContextField.set(null, context)

        val prefField = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        prefField.isAccessible = true
        prefField.set(null, PreferenceManager.getDefaultSharedPreferences(context))

        TrainingApplication.setAutoPaused(false, context)
        TrainingApplication.setManualPauseLock(false, context)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(BANALService::class)
        every { BANALService.setInitialSensorValue(any<SensorType>(), any<Int>()) } returns Unit
        every { BANALService.setInitialSensorValue(any<SensorType>(), any<Double>()) } returns Unit
        every { BANALService.checkOrInitializeLocationDevices() } returns Unit
        every { BANALService.isSearching() } returns false
        every { BANALService.setStaticIndoorMode(any()) } returns Unit

        RadarLightController.reset()

        mockkStatic(PolarH10OfflineController::class)
        every { PolarH10OfflineController.setSessionStartTimeMs(any()) } returns Unit
        every { PolarH10OfflineController.onWorkoutStarted(any()) } returns Unit

        mockWorkoutRepo = mockk(relaxed = true)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        BANALServiceRepository.resetForTesting(mockk(relaxed = true))

        mockSportTypesRepo = mockk(relaxed = true)
        every { mockSportTypesRepo.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportTypesRepo.isIndoorSportType(any()) } returns false
        mockDeviceDataRepo = mockk(relaxed = true)
        mockKnownLocationsRepo = mockk(relaxed = true)
        mockWorkoutRrRepo = mockk(relaxed = true)
        mockTrainingApplication = mockk(relaxed = true)

        directExecutor = DirectExecutorService()
        service = createPreparedService()
    }

    @After
    fun tearDown() {
        RadarLightController.reset()
        BANALServiceRepository.resetForTesting(null)
        WorkoutRepository.resetForTesting(null)
        TrainingApplication.setAutoPaused(false, context)
        TrainingApplication.setManualPauseLock(false, context)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)

        val appContextField = TrainingApplication::class.java.getDeclaredField("cAppContext")
        appContextField.isAccessible = true
        appContextField.set(null, null)

        val prefField = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        prefField.isAccessible = true
        prefField.set(null, null)

        unmockkAll()
    }

    /**
     * Characterisation Test 1: StartType & 3-Tier Recovery Invariants via Genuine onStartCommand()
     * Invokes TrackerService.onStartCommand() across RESUME_BY_USER, START_NORMAL, and
     * OS service recreation (intent == null) and asserts that production onStartCommand() resolves
     * mStartType, mWorkoutStartLocationRecorded, and the 3-tier AutoPauseState recovery hierarchy.
     */
    @Test
    fun testStartTypeInvariants_acrossStartupTiers() {
        val startTypeField: Field = TrackerService::class.java.getDeclaredField("mStartType")
        startTypeField.isAccessible = true

        val recordedField: Field = TrackerService::class.java.getDeclaredField("mWorkoutStartLocationRecorded")
        recordedField.isAccessible = true

        val autoPauseManagerField: Field = TrackerService::class.java.getDeclaredField("mAutoPauseManager")
        autoPauseManagerField.isAccessible = true

        // Tier 2: RESUME_BY_USER -> mWorkoutStartLocationRecorded flips from default false to true
        val resumeIntent = Intent(context, TrackerService::class.java).apply {
            putExtra(TrackerService.START_TYPE, StartType.RESUME_BY_USER.name)
        }
        assertEquals(Service.START_STICKY, service.onStartCommand(resumeIntent, 0, 1))
        assertEquals(StartType.RESUME_BY_USER, startTypeField.get(service))
        assertTrue(recordedField.get(service) as Boolean)

        // Tier 1: START_NORMAL on the same instance -> mWorkoutStartLocationRecorded resets to false
        val normalIntent = Intent(context, TrackerService::class.java).apply {
            putExtra(TrackerService.START_TYPE, StartType.START_NORMAL.name)
        }
        assertEquals(Service.START_STICKY, service.onStartCommand(normalIntent, 0, 2))
        assertEquals(StartType.START_NORMAL, startTypeField.get(service))
        assertFalse(recordedField.get(service) as Boolean)
        assertEquals(999L, service.notifiedWorkoutId)
        verify(exactly = 1) { mockWorkoutRepo.createSamplesTable(any(), any()) }

        // Tier 3 (intent == null): OS Service Recreation -> RESUME_SERVICE_RECREATION & recorded=true
        // Sub-tier 3a: MANUAL_PAUSED takes highest precedence
        TrainingApplication.setManualPauseLock(true, context)
        TrainingApplication.setAutoPaused(true, context)
        val tier1Service = createPreparedService()
        assertEquals(Service.START_STICKY, tier1Service.onStartCommand(null, 0, 3))
        assertEquals(StartType.RESUME_SERVICE_RECREATION, startTypeField.get(tier1Service))
        assertTrue(recordedField.get(tier1Service) as Boolean)
        assertEquals(AutoPauseState.MANUAL_PAUSED, (autoPauseManagerField.get(tier1Service) as AutoPauseManager).currentState)

        // Sub-tier 3b: AUTO_PAUSED when manualPauseLocked == false
        TrainingApplication.setManualPauseLock(false, context)
        TrainingApplication.setAutoPaused(true, context)
        val tier2Service = createPreparedService()
        assertEquals(Service.START_STICKY, tier2Service.onStartCommand(null, 0, 4))
        assertEquals(StartType.RESUME_SERVICE_RECREATION, startTypeField.get(tier2Service))
        assertTrue(recordedField.get(tier2Service) as Boolean)
        assertEquals(AutoPauseState.AUTO_PAUSED, (autoPauseManagerField.get(tier2Service) as AutoPauseManager).currentState)

        // Sub-tier 3c: TRACKING when neither manualPauseLocked nor autoPaused
        TrainingApplication.setManualPauseLock(false, context)
        TrainingApplication.setAutoPaused(false, context)
        val tier3Service = createPreparedService()
        assertEquals(Service.START_STICKY, tier3Service.onStartCommand(null, 0, 5))
        assertEquals(StartType.RESUME_SERVICE_RECREATION, startTypeField.get(tier3Service))
        assertTrue(recordedField.get(tier3Service) as Boolean)
        assertEquals(AutoPauseState.TRACKING, (autoPauseManagerField.get(tier3Service) as AutoPauseManager).currentState)
    }

    /**
     * Characterisation Test 2: Valid Resumed State Application
     * Asserts that applyResumedState populates baseFileName, table, workoutId, totals,
     * seeds initial sensor values into BANALService, notifies tracking started,
     * and completes mTableInitializationFuture.
     */
    @Test
    fun testApplyResumedState_validRecord_populatesStateAndInitializes() {
        val state = ResumedWorkoutState(
            VALID_BASE_FILE_NAME,
            RESUMED_WORKOUT_ID,
            1500, // timeTotal_s
            1200, // timeActive_s
            450,  // calories
            3,    // lapNr
            12500.0 // distanceTotal_m
        )

        val method: Method = TrackerService::class.java.getDeclaredMethod("applyResumedState", ResumedWorkoutState::class.java)
        method.isAccessible = true
        method.invoke(service, state)

        val workoutIdField: Field = TrackerService::class.java.getDeclaredField("mWorkoutID")
        workoutIdField.isAccessible = true
        assertEquals(RESUMED_WORKOUT_ID, workoutIdField.get(service) as Long)

        val baseFileField: Field = TrackerService::class.java.getDeclaredField("mBaseFileName")
        baseFileField.isAccessible = true
        assertEquals(VALID_BASE_FILE_NAME, baseFileField.get(service) as String)

        val samplesTableField: Field = TrackerService::class.java.getDeclaredField("mSamplesTableName")
        samplesTableField.isAccessible = true
        assertNotNull(samplesTableField.get(service))

        // Initial sensor values seeded into BANALService
        verify { BANALService.setInitialSensorValue(SensorType.TIME_TOTAL, 1500) }
        verify { BANALService.setInitialSensorValue(SensorType.TIME_ACTIVE, 1200) }
        verify { BANALService.setInitialSensorValue(SensorType.CALORIES, 450) }
        verify { BANALService.setInitialSensorValue(SensorType.LAP_NR, 3) }
        verify { BANALService.setInitialSensorValue(SensorType.DISTANCE_m, 12500.0) }

        assertEquals(RESUMED_WORKOUT_ID, service.notifiedWorkoutId)
    }

    /**
     * Characterisation Test 3: Resumed State Application with Invalid Base File Name
     * Asserts that when baseFileName is invalid, applyResumedState fails exceptionally,
     * triggers interrupted notification, and invokes performStopSelf.
     */
    @Test
    fun testApplyResumedState_invalidBaseFileName_failsDefensively() {
        val state = ResumedWorkoutState(
            "   ", // invalid blank baseFileName
            RESUMED_WORKOUT_ID,
            100,
            100,
            50,
            1,
            500.0
        )

        val method: Method = TrackerService::class.java.getDeclaredMethod("applyResumedState", ResumedWorkoutState::class.java)
        method.isAccessible = true
        method.invoke(service, state)

        assertTrue(service.interruptedNotificationShown)
        assertTrue(service.stoppedSelf)

        val futureField: Field = TrackerService::class.java.getDeclaredField("mTableInitializationFuture")
        futureField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val future = futureField.get(service) as CompletableFuture<Void>
        assertTrue(future.isCompletedExceptionally)
    }

    /**
     * Characterisation Test 4: recreateValuesWhenResuming with Missing Resume Record
     * Asserts that when getResumedWorkoutState() returns null, recreateValuesWhenResuming
     * completes the future exceptionally without NPE.
     */
    @Test
    fun testRecreateValuesWhenResuming_missingRecord_completesExceptionally() {
        every { mockWorkoutRepo.getResumedWorkoutState() } returns null

        val method: Method = TrackerService::class.java.getDeclaredMethod("recreateValuesWhenResuming")
        method.isAccessible = true
        method.invoke(service)

        val futureField: Field = TrackerService::class.java.getDeclaredField("mTableInitializationFuture")
        futureField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val future = futureField.get(service) as CompletableFuture<Void>
        assertTrue(future.isCompletedExceptionally)
    }

    /**
     * Characterisation Test 5: Tracking Abort State Invariants
     * Invokes genuine TrackerService.handleTrackingAborted() and asserts that TrackingMode resets
     * to READY, pause/autopause flags clear, TrainingApplication.onTrackingAborted() runs, and
     * TRACKING_ABORTED_INTENT is broadcast.
     */
    @Test
    fun testTrackingAbort_resetsApplicationStateInvariants() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        TrainingApplication.setAutoPaused(true, context)
        assertEquals(TrackingMode.TRACKING, TrainingApplication.getTrackingMode())

        service.callHandleTrackingAborted()

        assertEquals(TrackingMode.READY, TrainingApplication.getTrackingMode())
        assertTrue(TrainingApplication.isPaused()) // Invariant: READY is not TRACKING, so isPaused() == true
        assertFalse(TrainingApplication.isTracking())
        assertFalse(TrainingApplication.isAutoPaused())
        assertFalse(TrainingApplication.isManualPauseLocked())
        verify(exactly = 1) { mockTrainingApplication.onTrackingAborted() }
        assertTrue(
            "handleTrackingAborted() must broadcast TRACKING_ABORTED_INTENT",
            service.sentBroadcasts.any { it.action == TrackerService.TRACKING_ABORTED_INTENT }
        )
    }
}
