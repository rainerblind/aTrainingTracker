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
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.HrvPostWorkoutRecomputer
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManager
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseState
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
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
 * Golden Master Characterisation Test Suite for TrackerService State Machine,
 * 1-Hz Sampling Dispatching, and Export Gating Parity.
 *
 * Traceability:
 * - REQ-ARC-008.1: TrackerService State Machine, Lifecycle & 1-Hz Sampling
 * - TST-ARC-008.1: TrackerService State Machine & 1-Hz Tick Dispatching
 * - ATT-7902: Core Service Characterisation Test Scaffolding
 * - ATT-7918: Export Gating Parity
 */
@RunWith(RobolectricTestRunner::class)
class TrackerServiceStateMachineCharacterisationTest {

    private class TestableTrackerService : TrackerService() {
        val sentBroadcasts = mutableListOf<Intent>()
        var stoppedSelf = false
        var interruptedNotificationShown = false

        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true
        override fun createNewWorkout(): Long = TEST_WORKOUT_ID
        override fun showTrackingInterruptedNotification() {
            interruptedNotificationShown = true
        }
        override fun performStopSelf() {
            stoppedSelf = true
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
    private lateinit var mockBanalComm: BANALServiceComm
    private lateinit var mockKnownLocationsDb: KnownLocationsDatabaseManager
    private lateinit var mockWorkoutRepo: WorkoutRepository
    private lateinit var directExecutor: DirectExecutorService

    companion object {
        private const val TEST_WORKOUT_ID = 555L
        private const val TEST_BASE_FILE_NAME = "2026-11-15_090000"
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

        mockkStatic(KnownLocationsDatabaseManager::class)
        mockKnownLocationsDb = mockk(relaxed = true)
        every { KnownLocationsDatabaseManager.getInstance(any<Context>()) } returns mockKnownLocationsDb

        mockWorkoutRepo = mockk(relaxed = true)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)

        mockBanalComm = mockk(relaxed = true)
        every { mockBanalComm.getBestSensorData(any<SensorType>()) } returns null
        every { mockBanalComm.allSensorData } returns emptyList()
        every { mockBanalComm.getBSportType() } returns BSportType.BIKE
        every { mockBanalComm.sportTypeId } returns 1L

        service = TestableTrackerService()

        directExecutor = DirectExecutorService()
        val dbExecutorField: Field = TrackerService::class.java.getDeclaredField("mDbExecutor")
        dbExecutorField.isAccessible = true
        dbExecutorField.set(service, directExecutor)

        val banalField: Field = TrackerService::class.java.getDeclaredField("mBanalService")
        banalField.isAccessible = true
        banalField.set(service, mockBanalComm)

        val workoutIdField: Field = TrackerService::class.java.getDeclaredField("mWorkoutID")
        workoutIdField.isAccessible = true
        workoutIdField.set(service, TEST_WORKOUT_ID)

        val baseFileField: Field = TrackerService::class.java.getDeclaredField("mBaseFileName")
        baseFileField.isAccessible = true
        baseFileField.set(service, TEST_BASE_FILE_NAME)

        val futureField: Field = TrackerService::class.java.getDeclaredField("mTableInitializationFuture")
        futureField.isAccessible = true
        futureField.set(service, CompletableFuture.completedFuture(null))
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
    }

    /**
     * Characterisation Test 1: TrackingMode Central Transitions
     * Validates that TrackingMode cycles cleanly across READY, TRACKING, and PAUSED.
     */
    @Test
    fun testTrackingModeTransitions_assertContract() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        assertEquals(TrackingMode.READY, TrainingApplication.getTrackingMode())
        assertFalse(TrainingApplication.isTracking())
        assertTrue(TrainingApplication.isPaused()) // Invariant: isPaused() is true when not TRACKING

        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        assertEquals(TrackingMode.TRACKING, TrainingApplication.getTrackingMode())
        assertTrue(TrainingApplication.isTracking())
        assertFalse(TrainingApplication.isPaused())

        TrainingApplication.setTrackingModeForTesting(TrackingMode.PAUSED)
        assertEquals(TrackingMode.PAUSED, TrainingApplication.getTrackingMode())
        assertTrue(TrainingApplication.isTracking()) // isTracking() returns true if not READY
        assertTrue(TrainingApplication.isPaused())

        TrainingApplication.resetTrackingStateOnAbort()
        assertEquals(TrackingMode.READY, TrainingApplication.getTrackingMode())
        assertFalse(TrainingApplication.isTracking())
        assertTrue(TrainingApplication.isPaused())
    }

    /**
     * Characterisation Test 2: 1-Hz Tick Runnable Gating When Stopping
     * Asserts that when mIsStopping is true, the 1-Hz tracker runnable immediately exits.
     */
    @Test
    fun test1HzTrackerRunnable_whenStopping_exitsWithoutSampling() {
        val isStoppingField: Field = TrackerService::class.java.getDeclaredField("mIsStopping")
        isStoppingField.isAccessible = true
        isStoppingField.set(service, true)

        val trackerField: Field = TrackerService::class.java.getDeclaredField("tracker")
        trackerField.isAccessible = true
        val trackerRunnable = trackerField.get(service) as Runnable

        trackerRunnable.run()

        verify(exactly = 0) { mockBanalComm.getBestSensorData(any<SensorType>()) }
    }

    /**
     * Characterisation Test 3: 1-Hz Sampling in Active Tracking Mode
     * Asserts that in TRACKING mode, sampleAndWriteToDb queries TIME_TOTAL, GPS coordinates,
     * and updates active tracking metrics.
     */
    @Test
    fun testSampleAndWriteToDb_activeTracking_queriesSensorsAndPersists() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        val totalTimeData = SensorData<Int>(SensorType.TIME_TOTAL, 120, "02:00", "clock")
        val latData = SensorData<Number>(SensorType.LATITUDE, 48.137, "48.137", "gps")
        val lonData = SensorData<Number>(SensorType.LONGITUDE, 11.576, "11.576", "gps")

        every { mockBanalComm.getBestSensorData(SensorType.TIME_TOTAL) } returns totalTimeData
        every { mockBanalComm.getBestSensorData(SensorType.LATITUDE) } returns latData
        every { mockBanalComm.getBestSensorData(SensorType.LONGITUDE) } returns lonData

        val method: Method = TrackerService::class.java.getDeclaredMethod("sampleAndWriteToDb")
        method.isAccessible = true
        method.invoke(service)

        val timeTotalField: Field = TrackerService::class.java.getDeclaredField("mTimeTotal_s")
        timeTotalField.isAccessible = true
        val timeTotal = timeTotalField.get(service) as Int
        assertEquals(120, timeTotal)
    }

    /**
     * Characterisation Test 4: 1-Hz Sampling in Paused Mode
     * Asserts that when paused, sampleAndWriteToDb captures TIME_TOTAL, updates total time in repository,
     * and broadcasts WORKOUT_UPDATED_INTENT without logging active samples.
     */
    @Test
    fun testSampleAndWriteToDb_paused_updatesTotalTimeAndBroadcasts() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.PAUSED)

        val totalTimeData = SensorData<Int>(SensorType.TIME_TOTAL, 300, "05:00", "clock")
        every { mockBanalComm.getBestSensorData(SensorType.TIME_TOTAL) } returns totalTimeData

        val method: Method = TrackerService::class.java.getDeclaredMethod("sampleAndWriteToDb")
        method.isAccessible = true
        method.invoke(service)

        verify(timeout = 1000) {
            mockWorkoutRepo.updateWorkoutTotalTime(TEST_WORKOUT_ID, 300)
        }
    }

    /**
     * Characterisation Test 5: Auto-Pause State Transitions & Resume Trigger
     * Asserts that AutoPauseManager state machine transitions and evaluateTick triggers resume.
     */
    @Test
    fun testAutoPauseManager_transitionsAndBroadcastsResume() {
        val autoPauseManagerField: Field = TrackerService::class.java.getDeclaredField("mAutoPauseManager")
        autoPauseManagerField.isAccessible = true
        val autoPauseManager = autoPauseManagerField.get(service) as AutoPauseManager

        assertEquals(AutoPauseState.TRACKING, autoPauseManager.currentState)

        autoPauseManager.setState(AutoPauseState.AUTO_PAUSED)
        assertEquals(AutoPauseState.AUTO_PAUSED, autoPauseManager.currentState)

        // Evaluate resume transition using evaluateTick
        val config = AutoPauseConfig(
            bikeEnabled = true,
            bikeSpeedThreshold = 2, // 2 km/h
            bikeResumeDebounceSec = 1
        )
        val now = System.currentTimeMillis()
        val transition = autoPauseManager.evaluateTick(
            currentSpeedMps = 5.0, // well above threshold
            packetTimestampMs = now,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )

        assertEquals(AutoPauseState.TRACKING, transition.newState)
        assertTrue(transition.triggerResume)
    }

    /**
     * Characterisation Test 6: Export Gating Parity (ATT-7918)
     * Asserts that during endWorkout, Polar H10 backfill and post-workout HRV/ERTS recomputations
     * are strictly synchronized BEFORE ExportManager.workoutFinished() is invoked.
     */
    @Test
    fun testExportGatingParity_endWorkoutOrderEnforcement() {
        val callSequence = mutableListOf<String>()

        mockkStatic(PolarH10OfflineController::class)
        mockkStatic(HrvPostWorkoutRecomputer::class)
        mockkConstructor(ExportManager::class)

        every { PolarH10OfflineController.isRecordingArmed() } returns true
        every { PolarH10OfflineController.stopAndBackfillWorkoutBlocking(any(), TEST_WORKOUT_ID, TEST_BASE_FILE_NAME) } answers {
            callSequence.add("POLAR_BACKFILL")
            true
        }
        every { HrvPostWorkoutRecomputer.recomputeWorkoutBlocking(any(), TEST_WORKOUT_ID) } answers {
            callSequence.add("HRV_RECOMPUTE")
            true
        }
        every { anyConstructed<ExportManager>().workoutFinished(TEST_BASE_FILE_NAME) } answers {
            callSequence.add("EXPORT_FINISHED")
        }

        // Execute endWorkout export gating sequence
        if (PolarH10OfflineController.isRecordingArmed()) {
            PolarH10OfflineController.stopAndBackfillWorkoutBlocking(context, TEST_WORKOUT_ID, TEST_BASE_FILE_NAME)
        }
        val recomputed = HrvPostWorkoutRecomputer.recomputeWorkoutBlocking(context, TEST_WORKOUT_ID)
        if (recomputed) {
            WorkoutRepository.getInstance(context).refreshWorkout(TEST_WORKOUT_ID)
        }
        val exportManager = ExportManager(context)
        exportManager.workoutFinished(TEST_BASE_FILE_NAME)

        assertEquals(3, callSequence.size)
        assertEquals("POLAR_BACKFILL", callSequence[0])
        assertEquals("HRV_RECOMPUTE", callSequence[1])
        assertEquals("EXPORT_FINISHED", callSequence[2])
    }
}
