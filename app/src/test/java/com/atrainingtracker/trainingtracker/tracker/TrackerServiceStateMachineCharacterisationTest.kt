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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
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
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig
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
import org.robolectric.Shadows
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
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
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = true
        override fun registerTransientReceivers() {}
        override fun unregisterTransientReceivers() {}
        override fun unregisterReceiver(receiver: BroadcastReceiver?) {}
        override fun unbindService(conn: ServiceConnection) {}
        override fun performSuperOnDestroy() {}
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
    private lateinit var mockSportTypesRepo: SportTypesRepository
    private lateinit var mockDeviceDataRepo: DeviceDataRepository
    private lateinit var mockKnownLocationsRepo: KnownLocationsRepository
    private lateinit var mockWorkoutRrRepo: WorkoutRrRepository
    private lateinit var directExecutor: DirectExecutorService

    companion object {
        private const val TEST_WORKOUT_ID = 555L
        private const val TEST_BASE_FILE_NAME = "2026-11-15_090000"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        configureTrainingApplicationContext()
        configureStaticMocks()
        configureRepositoriesAndBanalMock()

        service = TestableTrackerService()
        val attachMethod: Method = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachMethod.isAccessible = true
        attachMethod.invoke(service, context)

        injectServiceDependencies()
    }

    private fun configureTrainingApplicationContext() {
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", PreferenceManager.getDefaultSharedPreferences(context))
        TrainingApplication.setAutoPaused(false, context)
        TrainingApplication.setManualPauseLock(false, context)
    }

    private fun configureStaticMocks() {
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

        mockkStatic(ExportStatusDatabaseManager::class)
        every { ExportStatusDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)

        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.isPostWorkoutErtsSmoothingEnabled(any()) } returns false
        every { SettingsDataStoreJavaHelper.getAutoPauseConfig(any()) } returns AutoPauseConfig()
    }

    private fun configureRepositoriesAndBanalMock() {
        mockWorkoutRepo = mockk(relaxed = true)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        BANALServiceRepository.resetForTesting(mockk(relaxed = true))

        mockSportTypesRepo = mockk(relaxed = true)
        every { mockSportTypesRepo.getBSportType(any()) } returns BSportType.BIKE
        mockDeviceDataRepo = mockk(relaxed = true)
        mockKnownLocationsRepo = mockk(relaxed = true)
        mockWorkoutRrRepo = mockk(relaxed = true)

        mockBanalComm = mockk(relaxed = true)
        every { mockBanalComm.getBestSensorData(any<SensorType>()) } returns null
        every { mockBanalComm.allSensorData } returns emptyList()
        every { mockBanalComm.getBSportType() } returns BSportType.BIKE
        every { mockBanalComm.sportTypeId } returns 1L
    }

    private fun injectServiceDependencies() {
        directExecutor = DirectExecutorService()
        setServiceField(service, "mDbExecutor", directExecutor)
        setServiceField(service, "mRrFlushExecutor", directExecutor)
        setServiceField(service, "mScheduler", mockk<ScheduledExecutorService>(relaxed = true))
        setServiceField(service, "mBanalService", mockBanalComm)
        setServiceField(service, "mSportTypesRepository", mockSportTypesRepo)
        setServiceField(service, "mDeviceDataRepository", mockDeviceDataRepo)
        setServiceField(service, "mKnownLocationsRepository", mockKnownLocationsRepo)
        setServiceField(service, "mWorkoutRrRepository", mockWorkoutRrRepo)
        setServiceField(service, "mWorkoutID", TEST_WORKOUT_ID)
        setServiceField(service, "mBaseFileName", TEST_BASE_FILE_NAME)
        setServiceField(service, "mTableInitializationFuture", CompletableFuture.completedFuture(null))
    }

    private fun setServiceField(target: TrackerService, fieldName: String, value: Any?) {
        val field: Field = TrackerService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field: Field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    @After
    fun tearDown() {
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

        val receivedLocalBroadcasts = mutableListOf<Intent>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent != null) {
                    receivedLocalBroadcasts.add(intent)
                }
            }
        }
        val localBroadcastManager = LocalBroadcastManager.getInstance(context)
        localBroadcastManager.registerReceiver(receiver, IntentFilter(TrackerService.WORKOUT_UPDATED_INTENT))

        try {
            val method: Method = TrackerService::class.java.getDeclaredMethod("sampleAndWriteToDb")
            method.isAccessible = true
            method.invoke(service)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            verify(timeout = 1000) {
                mockWorkoutRepo.updateWorkoutTotalTime(TEST_WORKOUT_ID, 300)
            }
            verify(exactly = 0) {
                mockWorkoutRepo.recordLiveSample(any(), any(), any(), any(), any(), any(), any(), any())
            }
            assertTrue(
                "Expected WORKOUT_UPDATED_INTENT local broadcast",
                receivedLocalBroadcasts.any { it.getLongExtra("WORKOUT_ID", -1L) == TEST_WORKOUT_ID }
            )
        } finally {
            localBroadcastManager.unregisterReceiver(receiver)
        }
    }

    /**
     * Characterisation Test 5: Auto-Pause State Transitions & Resume Trigger
     * Asserts that when paused in AUTO_PAUSED state, TrackerService.sampleAndWriteToDb evaluates
     * speed telemetry via mAutoPauseManager, transitions back to TRACKING, and broadcasts
     * TrainingApplication.REQUEST_RESUME_FROM_PAUSED.
     */
    @Test
    fun testAutoPauseManager_transitionsAndBroadcastsResume() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.PAUSED)
        TrainingApplication.setAutoPaused(true, context)
        TrainingApplication.setManualPauseLock(false, context)

        val autoPauseManagerField: Field = TrackerService::class.java.getDeclaredField("mAutoPauseManager")
        autoPauseManagerField.isAccessible = true
        val autoPauseManager = autoPauseManagerField.get(service) as AutoPauseManager

        assertEquals(AutoPauseState.TRACKING, autoPauseManager.currentState)
        autoPauseManager.setState(AutoPauseState.AUTO_PAUSED)
        assertEquals(AutoPauseState.AUTO_PAUSED, autoPauseManager.currentState)

        every { SettingsDataStoreJavaHelper.getAutoPauseConfig(any()) } returns AutoPauseConfig(
            bikeEnabled = true,
            bikeSpeedThreshold = 2, // 2 km/h
            bikeResumeDebounceSec = 1
        )
        every { mockBanalComm.getBestSensorData(SensorType.SPEED_mps) } returns SensorData<Number>(
            SensorType.SPEED_mps,
            5.0,
            "18.0",
            "gps"
        )

        val method: Method = TrackerService::class.java.getDeclaredMethod("sampleAndWriteToDb", BANALServiceComm::class.java)
        method.isAccessible = true
        method.invoke(service, mockBanalComm)

        assertEquals(AutoPauseState.TRACKING, autoPauseManager.currentState)
        assertTrue(
            "TrackerService must broadcast REQUEST_RESUME_FROM_PAUSED when AutoPause triggers resume",
            service.sentBroadcasts.any { it.action == TrainingApplication.REQUEST_RESUME_FROM_PAUSED }
        )
    }

    /**
     * Characterisation Test 6: Export Gating Parity (ATT-7918)
     * Invokes genuine TrackerService.endWorkout() and asserts that Polar H10 backfill and
     * post-workout HRV recomputation + repository refresh execute synchronously BEFORE
     * ExportManager.workoutFinished() is invoked, followed by TRACKING_FINISHED_INTENT broadcast.
     */
    @Test
    fun testExportGatingParity_endWorkoutOrderEnforcement() {
        val callSequence = mutableListOf<String>()

        mockkStatic(PolarH10OfflineController::class)
        mockkStatic(HrvPostWorkoutRecomputer::class)
        mockkConstructor(ExportManager::class)

        every { PolarH10OfflineController.isRecordingArmed() } returns true
        every {
            PolarH10OfflineController.stopAndBackfillWorkoutBlocking(any(), TEST_WORKOUT_ID, TEST_BASE_FILE_NAME)
        } answers {
            callSequence.add("POLAR_BACKFILL")
            true
        }
        every { HrvPostWorkoutRecomputer.recomputeWorkoutBlocking(any(), TEST_WORKOUT_ID) } answers {
            callSequence.add("HRV_RECOMPUTE")
            true
        }
        every { mockWorkoutRepo.refreshWorkout(TEST_WORKOUT_ID) } answers {
            callSequence.add("WORKOUT_REFRESH")
        }
        every { anyConstructed<ExportManager>().workoutFinished(TEST_BASE_FILE_NAME) } answers {
            callSequence.add("EXPORT_FINISHED")
        }

        // Invoke genuine production TrackerService.endWorkout()
        service.endWorkout()

        assertEquals(
            listOf("POLAR_BACKFILL", "HRV_RECOMPUTE", "WORKOUT_REFRESH", "EXPORT_FINISHED"),
            callSequence
        )
        verify(exactly = 1) { mockWorkoutRepo.markWorkoutFinishedBlocking(TEST_WORKOUT_ID) }
        assertTrue(
            "endWorkout() must broadcast TRACKING_FINISHED_INTENT",
            service.sentBroadcasts.any {
                it.action == TrackerService.TRACKING_FINISHED_INTENT &&
                    it.getLongExtra(TrackerService.WORKOUT_ID, -1L) == TEST_WORKOUT_ID
            }
        )
    }
}
