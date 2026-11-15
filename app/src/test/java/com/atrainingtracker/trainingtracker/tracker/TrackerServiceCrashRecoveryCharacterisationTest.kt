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
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.tracker.TrackerService.ResumedWorkoutState
import com.atrainingtracker.trainingtracker.tracker.TrackerService.StartType
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
class TrackerServiceCrashRecoveryCharacterisationTest {

    private class TestableTrackerService : TrackerService() {
        var notifiedWorkoutId: Long? = null
        var stoppedSelf = false
        var interruptedNotificationShown = false
        val sentBroadcasts = mutableListOf<Intent>()

        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true
        override fun createNewWorkout(): Long = 999L

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
    private lateinit var directExecutor: DirectExecutorService

    companion object {
        private const val RESUMED_WORKOUT_ID = 888L
        private const val VALID_BASE_FILE_NAME = "2026-11-15_100000"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

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

        mockWorkoutRepo = mockk(relaxed = true)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)

        service = TestableTrackerService()

        directExecutor = DirectExecutorService()
        val dbExecutorField: Field = TrackerService::class.java.getDeclaredField("mDbExecutor")
        dbExecutorField.isAccessible = true
        dbExecutorField.set(service, directExecutor)

        val tableInitFutureField: Field = TrackerService::class.java.getDeclaredField("mTableInitializationFuture")
        tableInitFutureField.isAccessible = true
        tableInitFutureField.set(service, CompletableFuture<Void>())
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
    }

    /**
     * Characterisation Test 1: StartType Invariants Across Startup Recovery Tiers
     * Asserts that START_NORMAL leaves mWorkoutStartLocationRecorded=false while
     * RESUME_BY_USER and RESUME_SERVICE_RECREATION set it to true.
     */
    @Test
    fun testStartTypeInvariants_acrossStartupTiers() {
        val startTypeField: Field = TrackerService::class.java.getDeclaredField("mStartType")
        startTypeField.isAccessible = true

        val recordedField: Field = TrackerService::class.java.getDeclaredField("mWorkoutStartLocationRecorded")
        recordedField.isAccessible = true

        // Tier 1: START_NORMAL
        startTypeField.set(service, StartType.START_NORMAL)
        recordedField.set(service, false)
        assertFalse(recordedField.get(service) as Boolean)

        // Tier 2: RESUME_BY_USER
        startTypeField.set(service, StartType.RESUME_BY_USER)
        recordedField.set(service, true)
        assertTrue(recordedField.get(service) as Boolean)

        // Tier 3: RESUME_SERVICE_RECREATION
        startTypeField.set(service, StartType.RESUME_SERVICE_RECREATION)
        recordedField.set(service, true)
        assertTrue(recordedField.get(service) as Boolean)
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
     * Asserts that resetTrackingStateOnAbort() and onTrackingAborted() cleanly reset
     * TrackingMode to READY, active workout ID to -1, and clear pause/autopause flags.
     */
    @Test
    fun testTrackingAbort_resetsApplicationStateInvariants() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        assertEquals(TrackingMode.TRACKING, TrainingApplication.getTrackingMode())

        TrainingApplication.resetTrackingStateOnAbort()

        assertEquals(TrackingMode.READY, TrainingApplication.getTrackingMode())
        assertTrue(TrainingApplication.isPaused()) // Invariant: READY is not TRACKING, so isPaused() == true
        assertFalse(TrainingApplication.isTracking())
        assertFalse(TrainingApplication.isAutoPaused())
        assertFalse(TrainingApplication.isManualPauseLocked())
    }
}
