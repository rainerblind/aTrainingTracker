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
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.odometry.MultisportTelemetryAnchor
import com.atrainingtracker.banalservice.odometry.MultisportTransitionBridge
import com.atrainingtracker.banalservice.odometry.MultisportTransitionEvent
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Robolectric wiring tests verifying [TrackerService.sampleAndWriteToDb] invokes
 * [MultisportLapCoordinator.enforceMonotonicLapNr] off the Android Main Thread on `mDbExecutor`
 * with real [ContentValues], and [TrackerService.endWorkout] invokes
 * [MultisportLapCoordinator.reconcileWorkoutSamplesOnFinalization] strictly before
 * [WorkoutPostProcessingCoordinator.enqueue] (`ATT-9468`, `REQ-TRK-096`, `TST-TRK-096.3`).
 */
// Linked test specs: TST-TRK-096
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackerServiceMultisportMonotonicLapTest {

    private class TestableTrackerService : TrackerService() {
        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true
        override fun showTrackingInterruptedNotification() {}
        override fun performStopSelf() {}
        override fun performStartForeground(id: Int, notification: Notification?, foregroundServiceType: Int) {}
        override fun getSportTypeId(): Long = 1L
        override fun getAutoPauseConfig(): AutoPauseConfig = AutoPauseConfig()
    }

    private lateinit var context: Context
    private lateinit var service: TestableTrackerService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkoutPostProcessingCoordinator.resetForTesting()
        LapsDatabaseManager.resetForTesting(null)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        WorkoutRepository.resetForTesting(null)

        service = TestableTrackerService()
        val attachMethod: Method = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachMethod.isAccessible = true
        attachMethod.invoke(service, context)
    }

    @After
    fun tearDown() {
        try {
            val dbExec = getPrivateField<ExecutorService>(service, "mDbExecutor")
            dbExec.shutdownNow()
        } catch (_: Throwable) {
        }
        try {
            val sched = getPrivateField<ExecutorService>(service, "mScheduler")
            sched.shutdownNow()
        } catch (_: Throwable) {
        }
        try {
            val rrExec = getPrivateField<ExecutorService>(service, "mRrFlushExecutor")
            rrExec.shutdownNow()
        } catch (_: Throwable) {
        }
        unmockkAll()
        WorkoutPostProcessingCoordinator.resetForTesting()
        WorkoutRepository.resetForTesting(null)
        LapsDatabaseManager.resetForTesting(null)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
    }

    // REQ-TRK-096 / TST-TRK-096.3
    @Test
    fun sampleAndWriteToDbAndEndWorkout_enforcesMonotonicLapNrAndReconcilesOnFinalization_tstTrk096_3() {
        val baseFileName = "2026-11-21_081228"
        val sessionStartMs = 1_795_248_748_000L
        val repo = WorkoutRepository.getInstance(context)
        val workoutId = repo.createNewTrackedWorkout(baseFileName, 1L, BSportType.RUN, false)
        repo.createSamplesTable(baseFileName, listOf(SensorType.TIME_TOTAL, SensorType.LAP_NR, SensorType.DISTANCE_m))
        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context).database
        val table = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

        // Pre-populate rows 1337..1352 with LAP_NR = 4
        for (t in 1337..1352) {
            samplesDb.insertOrThrow(table, null, ContentValues().apply {
                put(SensorType.TIME_TOTAL.name, t)
                put(SensorType.LAP_NR.name, 4)
            })
        }

        service.mWorkoutRepository = repo
        setPrivateField(service, "mWorkoutID", workoutId)
        setPrivateField(service, "mBaseFileName", baseFileName)
        setPrivateField(service, "mSamplesTableName", table)
        service.mMultisportLapCoordinator.resetForSession(sessionStartMs, 0, 0, 0.0)

        // Hold mTableInitializationFuture so sampleAndWriteToDb snapshots stale LAP_NR = 4 on caller
        // and only executes recordLiveSample AFTER the multisport transition commits!
        val gateFuture = CompletableFuture<Void>()
        setPrivateField(service, "mTableInitializationFuture", gateFuture)

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        every { TrainingApplication.isManualPauseLocked() } returns false

        val banal = mockk<BANALServiceComm>(relaxed = true)
        every { banal.bSportType } returns BSportType.RUN
        every { banal.databaseIdsOfActiveRemoteDevices } returns emptyList()
        every { banal.getBestSensorData(any<SensorType>()) } returns null
        every { banal.getBestSensorData(SensorType.TIME_TOTAL) } returns SensorData(SensorType.TIME_TOTAL, 1353, "1353", null)
        every { banal.getBestSensorData(SensorType.LAP_NR) } returns SensorData(SensorType.LAP_NR, 4, "4", null)
        every { banal.getBestSensorData(SensorType.SPEED_mps) } returns SensorData(SensorType.SPEED_mps, 3.0, "3.0", null)
        every { banal.allSensorData } returns listOf(
            SensorData(SensorType.TIME_TOTAL, 1353, "1353", null),
            SensorData(SensorType.LAP_NR, 4, "4", null),
            SensorData(SensorType.DISTANCE_m, 5360.0, "5360.0", null)
        )
        service.mBanalService = banal

        // 1. Snapshot stale LAP_NR = 4 at TIME_TOTAL = 1353 (queued behind gateFuture on mDbExecutor)
        service.sampleAndWriteToDb(banal)

        // 2. Multisport transition arrives BEFORE sample lambda flushes onto mDbExecutor!
        val event = MultisportTransitionEvent(
            fromSport = BSportType.RUN,
            toSport = BSportType.BIKE,
            exitAnchor = MultisportTelemetryAnchor(sessionStartMs + 1300_000L, 1300, 1300, 5200.0),
            enterAnchor = MultisportTelemetryAnchor(sessionStartMs + 1337_000L, 1337, 1337, 5240.0),
            confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + 1353_000L, 1353, 1353, 5360.0)
        )
        service.mMultisportTransitionReceiver.onReceive(
            context,
            MultisportTransitionBridge.toIntent(event, context.packageName)
        )

        // Release gateFuture and wait for single-threaded mDbExecutor to execute BOTH backfill and sample write off Main Thread
        val ranOffMainThread = AtomicBoolean(false)
        val dbDrained = CountDownLatch(1)
        gateFuture.complete(null)
        val dbExec = getPrivateField<ExecutorService>(service, "mDbExecutor")
        dbExec.execute {
            ranOffMainThread.set(Looper.myLooper() != Looper.getMainLooper())
            dbDrained.countDown()
        }
        assertTrue("mDbExecutor did not drain in time", dbDrained.await(10, TimeUnit.SECONDS))
        assertTrue("DB persistence must run off the Android Main Thread", ranOffMainThread.get())

        // Verify row 425 (t=1353) landed with LAP_NR = 6 and WorkoutSummaries.LAPS == 6!
        samplesDb.rawQuery("SELECT ${SensorType.LAP_NR.name} FROM $table WHERE ${SensorType.TIME_TOTAL.name} = 1353", null).use { c ->
            assertTrue("Row 1353 must exist", c.moveToFirst())
            assertEquals("Row 1353 must have enforced LAP_NR = 6", 6, c.getInt(0))
        }
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database
        summariesDb.rawQuery(
            "SELECT ${WorkoutSummaries.LAPS} FROM ${WorkoutSummaries.TABLE} WHERE ${WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString())
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("WorkoutSummaries.LAPS must be floored to 6", 6, c.getInt(0))
        }

        // 3. Seed an orphaned stale LAP_NR = 4 row at t=1355 and verify endWorkout() reconciles it
        //    strictly BEFORE WorkoutPostProcessingCoordinator.enqueue() runs!
        samplesDb.insertOrThrow(table, null, ContentValues().apply {
            put(SensorType.TIME_TOTAL.name, 1355)
            put(SensorType.LAP_NR.name, 4)
        })
        val observedLapAtEnqueue = AtomicInteger(-1)
        val enqueueCalled = AtomicBoolean(false)
        WorkoutPostProcessingCoordinator.enqueueHookForTesting = { _, _ ->
            enqueueCalled.set(true)
            samplesDb.rawQuery("SELECT ${SensorType.LAP_NR.name} FROM $table WHERE ${SensorType.TIME_TOTAL.name} = 1355", null).use { c ->
                if (c.moveToFirst()) observedLapAtEnqueue.set(c.getInt(0))
            }
            true
        }

        val snapshot = TrackerService.BanalFinalizationSnapshot(
            6, 120, 600.0, emptySet(), emptySet(), null, BSportType.BIKE, null, 1L
        )
        service.endWorkout(snapshot, workoutId, baseFileName, null)

        assertTrue("WorkoutPostProcessingCoordinator.enqueue must be invoked", enqueueCalled.get())
        assertEquals(
            "Row 1355 must already be reconciled to LAP_NR = 6 before post-processing enqueue",
            6,
            observedLapAtEnqueue.get()
        )
        assertFalse("Zero sample insert failures expected", service.sampleInsertFailureCount > 0)
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val f: Field = cls.getDeclaredField(name)
                f.isAccessible = true
                f.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        throw NoSuchFieldException(name)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(target: Any, name: String): T {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val f: Field = cls.getDeclaredField(name)
                f.isAccessible = true
                return f.get(target) as T
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        throw NoSuchFieldException(name)
    }
}
