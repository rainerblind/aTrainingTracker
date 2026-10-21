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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concurrency unit tests verifying thread safety, conflated reloads, safe streaming,
 * pure CAS invariants, and defensive deletion reporting in [WorkoutRepository]
 * (REQ-STB-018 / TST-STB-018.1..6).
 */
class WorkoutRepositoryConcurrencyTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockStravaUploadDbHelper = mockk<StravaUploadDbHelper>(relaxed = true)
    private val mockClusterDbManager = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
    private val mockLapsDbManager = mockk<LapsDatabaseManager>(relaxed = true)
    private val mockSportTypeDbManager = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDbHelper = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockPeriodsRepo = mockk<PeriodsRepository>(relaxed = true)
    private val mockClusterEngine = mockk<WorkoutClusterEngine>(relaxed = true)
    private val mockDeletionHelper = mockk<WorkoutDeletionHelper>(relaxed = true)

    private lateinit var repository: WorkoutRepository

    private val columns = listOf(
        WorkoutSummaries.C_ID,
        WorkoutSummaries.SPORT_ID,
        WorkoutSummaries.EQUIPMENT_ID,
        WorkoutSummaries.TIME_START,
        WorkoutSummaries.FILE_BASE_NAME,
        WorkoutSummaries.DISTANCE_TOTAL_m,
        WorkoutSummaries.MAP_POLYLINE,
        WorkoutSummaries.ALTITUDE_STREAM,
        WorkoutSummaries.DISTANCE_STREAM,
        WorkoutSummaries.WORKOUT_NAME,
        WorkoutSummaries.CLUSTER_ID,
        WorkoutSummaries.FINISHED,
        WorkoutSummaries.COMMUTE,
        WorkoutSummaries.TRAINER,
        WorkoutSummaries.RACE,
        WorkoutSummaries.UPLOAD_TO_STRAVA,
        WorkoutSummaries.BOUND_MIN_LAT,
        WorkoutSummaries.BOUND_MIN_LNG,
        WorkoutSummaries.BOUND_MAX_LAT,
        WorkoutSummaries.BOUND_MAX_LNG,
        WorkoutSummaries.TIME_ACTIVE_s,
        WorkoutSummaries.TIME_TOTAL_s,
        WorkoutSummaries.SPEED_AVERAGE_mps,
        WorkoutSummaries.ASCENDING,
        WorkoutSummaries.DESCENDING,
        WorkoutSummaries.DESCRIPTION,
        WorkoutSummaries.GOAL,
        WorkoutSummaries.METHOD
    )

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

        mockkStatic(LocalBroadcastManager::class)
        val mockLbm = mockk<LocalBroadcastManager>(relaxed = true)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLbm

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDbManager)

        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDbManager

        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDbManager
        every { mockSportTypeDbManager.getBSportType(any()) } returns BSportType.RUN
        every { mockSportTypeDbManager.getUIName(any()) } returns "Running"
        every { mockSportTypeDbManager.getStravaName(any()) } returns "Run"

        every { mockStravaUploadDbHelper.getStravaActivityDataForWorkouts(any()) } returns emptyMap()
        every { mockStravaUploadDbHelper.getStravaActivityData(any()) } returns null
        StravaUploadDbHelper.resetForTesting(mockStravaUploadDbHelper)

        EquipmentDbHelper.resetForTesting(mockEquipmentDbHelper)

        mockkConstructor(ExportManager::class)
        mockkConstructor(ExportStatusDataProvider::class)
        every { anyConstructed<ExportStatusDataProvider>().createGroupData(any(), any()) } returns mockk(relaxed = true)

        PeriodsRepository.setInstanceForTesting(mockPeriodsRepo)
        WorkoutClusterEngine.resetForTesting(mockClusterEngine)

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
        repository.deletionHelperInstance = mockDeletionHelper
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        PeriodsRepository.resetInstanceForTesting()
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        StravaUploadDbHelper.resetForTesting(null)
        EquipmentDbHelper.resetForTesting(null)
        unmockkAll()
    }

    private fun createSampleRow(id: Long, name: String = "Workout $id", finished: Int = 1, clusterId: Long = 10L): List<Any?> {
        return listOf(
            id,                                      // C_ID
            1L,                                      // SPORT_ID
            -1L,                                     // EQUIPMENT_ID
            "2026-10-04 10:00:00",                   // TIME_START
            "base_$id",                              // FILE_BASE_NAME
            5000.0,                                  // DISTANCE_TOTAL_m
            "",                                      // MAP_POLYLINE
            "",                                      // ALTITUDE_STREAM
            "",                                      // DISTANCE_STREAM
            name,                                    // WORKOUT_NAME
            clusterId,                               // CLUSTER_ID
            finished,                                // FINISHED
            0,                                       // COMMUTE
            0,                                       // TRAINER
            0,                                       // RACE
            0,                                       // UPLOAD_TO_STRAVA
            48.0,                                    // BOUND_MIN_LAT
            11.0,                                    // BOUND_MIN_LNG
            48.1,                                    // BOUND_MAX_LAT
            11.1,                                    // BOUND_MAX_LNG
            1800L,                                   // TIME_ACTIVE_s
            1850L,                                   // TIME_TOTAL_s
            2.77,                                    // SPEED_AVERAGE_mps
            50L,                                     // ASCENDING
            50L,                                     // DESCENDING
            "Test description",                      // DESCRIPTION
            "Test goal",                             // GOAL
            "Test method"                            // METHOD
        )
    }

    /**
     * [TST-STB-018.1] Conflated Per-Workout Reload & Lifecycle Callback
     * When reloadWorkoutData(101L) is already in-flight and a second reload arrives,
     * the second reload marks the ID dirty, executes sequentially upon release,
     * updates finished state, and triggers lifecycle callbacks.
     */
    @Test
    fun testConflatedPerWorkoutReload_marksDirtyAndInvokesLifecycleCallbacks() = runBlocking {
        // Arrange: Seed 101L in memory with finished = false
        val rowUnfinished = createSampleRow(101L, name = "Active Session", finished = 0, clusterId = 15L)
        every { mockSummariesManager.getWorkoutCursor(101L) } returns MockCursorFactory.create(columns, listOf(rowUnfinished))
        repository.reloadWorkoutData(101L)

        val initialWorkout = repository.allWorkouts.value.find { it.id == 101L }
        assertNotNull(initialWorkout)
        assertFalse("Precondition: workout must be unfinished", initialWorkout!!.finished)
        clearMocks(mockPeriodsRepo, mockClusterEngine)

        // Setup latch-paused two-pass reload
        val firstPassStarted = CountDownLatch(1)
        val releaseFirstPass = CountDownLatch(1)
        val passCounter = AtomicInteger(0)

        every { mockSummariesManager.getWorkoutCursor(101L) } answers {
            val pass = passCounter.incrementAndGet()
            if (pass == 1) {
                firstPassStarted.countDown()
                releaseFirstPass.await(5, TimeUnit.SECONDS)
                MockCursorFactory.create(columns, listOf(createSampleRow(101L, name = "Active Session", finished = 0, clusterId = 15L)))
            } else {
                MockCursorFactory.create(columns, listOf(createSampleRow(101L, name = "Finalized Session", finished = 1, clusterId = 15L)))
            }
        }

        // Act: Start reload 1
        val job1 = CoroutineScope(Dispatchers.Default).launch {
            repository.reloadWorkoutData(101L)
        }
        assertTrue("Timed out waiting for first pass to start", firstPassStarted.await(5, TimeUnit.SECONDS))

        // Trigger second reload while first is in-flight (simulating TRACKING_FINISHED_INTENT)
        val job2 = CoroutineScope(Dispatchers.Default).launch {
            repository.reloadWorkoutData(101L)
        }
        job2.join() // Second call marks dirty and returns immediately

        // Release first pass so sequential loop consumes dirty flag
        releaseFirstPass.countDown()
        job1.join()

        // Assert: Sequential pass executed and updated finished = true
        assertEquals("Both passes must execute sequentially", 2, passCounter.get())
        val updatedWorkout = repository.allWorkouts.value.find { it.id == 101L }
        assertNotNull(updatedWorkout)
        assertTrue("Workout must be finalized in memory", updatedWorkout!!.finished)

        // Verify lifecycle observers were triggered
        verify(atLeast = 1) { mockPeriodsRepo.onWorkoutFinished(match { it.id == 101L && it.finished }) }
        verify(atLeast = 1) { mockClusterEngine.onWorkoutFinished(any(), match { it.id == 101L && it.finished }) }
    }

    /**
     * [TST-STB-018.2] Concurrent Delete-During-Stream Stale Overwrite Defense
     * Workouts deleted via deleteWorkout while loadAllWorkouts is streaming
     * must NOT be resurrected by subsequent chunk emissions.
     */
    @Test
    fun testDeleteDuringStream_preventsResurrectionOfDeletedWorkout() = runBlocking {
        // Arrange: 120 rows across multiple chunks (batchSize = 50)
        val rows = (1L..120L).map { createSampleRow(it) }
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSummariesManager.getCursorForAllWorkouts() } returns cursor
        every { mockDeletionHelper.deleteWorkout(1L) } returns true

        val deletionTriggered = AtomicBoolean(false)
        every { mockSummariesManager.getExtremaForWorkouts(any()) } answers {
            val chunkIds = firstArg<List<Long>>()
            // Trigger deletion of item 1L (loaded in batch 1) during batch 2 processing
            if (chunkIds.contains(51L) && deletionTriggered.compareAndSet(false, true)) {
                repository.deleteWorkout(1L)
            }
            emptyList()
        }

        // Act: Run full progressive stream
        repository.loadAllWorkouts()

        // Assert: 119 items loaded, 1L is completely absent (no resurrection)
        val loaded = repository.allWorkouts.value
        assertEquals(119, loaded.size)
        assertFalse("Workout 1L must not be resurrected after deletion during load", loaded.any { it.id == 1L })
    }

    /**
     * [TST-STB-018.3] Skipped Row Recovery & Guaranteed Terminal Batch Emission
     * Exception during snapshot reading on a row is safely caught and shielded,
     * and unconditional terminal emission publishes all remaining valid workouts.
     */
    @Test
    fun testSkippedRowExceptionShielding_guaranteedTerminalBatchEmission() = runBlocking {
        // Arrange: 65 workouts, row at position 4 (id 5L) throws IllegalStateException
        val rows = (1L..65L).map { createSampleRow(it) }
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSummariesManager.getCursorForAllWorkouts() } returns cursor

        every { cursor.getLong(0) } answers {
            if (cursor.position == 4) {
                throw IllegalStateException("Simulated damaged CursorWindow buffer at pos 4")
            }
            rows[cursor.position][0] as Long
        }

        // Act
        repository.loadAllWorkouts()

        // Assert: 64 workouts loaded successfully, row 5 safely skipped
        val loaded = repository.allWorkouts.value
        assertEquals(64, loaded.size)
        assertFalse("Damaged row 5 must be skipped", loaded.any { it.id == 5L })
        verify { Log.e(any<String>(), match { it.contains("error reading row at position 4") }, any()) }
    }

    /**
     * [TST-STB-018.4] Atomic Loading Guard Concurrency Protection
     * Concurrent calls to loadAllWorkouts are atomically rejected via AtomicBoolean CAS.
     */
    @Test
    fun testAtomicLoadingGuard_concurrentCallRejected() = runBlocking {
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)

        val rows = (1L..10L).map { createSampleRow(it) }
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSummariesManager.getCursorForAllWorkouts() } answers {
            loadStarted.countDown()
            releaseLoad.await(5, TimeUnit.SECONDS)
            cursor
        }

        // Act: Start load 1 in background
        val job1 = CoroutineScope(Dispatchers.Default).launch {
            repository.loadAllWorkouts()
        }
        assertTrue("Timed out waiting for load 1 to start", loadStarted.await(5, TimeUnit.SECONDS))

        // Act: Start concurrent load 2
        val job2 = CoroutineScope(Dispatchers.Default).launch {
            repository.loadAllWorkouts()
        }
        job2.join() // Must immediately return without doing another query

        // Release load 1
        releaseLoad.countDown()
        job1.join()

        // Assert: Database cursor queried exactly once
        verify(exactly = 1) { mockSummariesManager.getCursorForAllWorkouts() }
    }

    /**
     * [TST-STB-018.5] Pure Functional CAS Transformation Contract
     * In-memory updates on cached workouts execute purely in-place with zero coroutine launches
     * or DB queries inside CAS retry lambdas.
     */
    @Test
    fun testPureFunctionalCasTransformation_contract() = runBlocking {
        // Arrange: Pre-populate workout 10L in memory
        val row10 = createSampleRow(10L, name = "Original Name")
        every { mockSummariesManager.getWorkoutCursor(10L) } returns MockCursorFactory.create(columns, listOf(row10))
        repository.reloadWorkoutData(10L)

        clearMocks(mockSummariesManager)

        // Act: Mutate cached workout via public memory mutator
        repository.setWorkoutName(10L, "Updated Pure Name")

        // Assert: In-memory name updated
        val updated = repository.allWorkouts.value.find { it.id == 10L }
        assertNotNull(updated)
        assertEquals("Updated Pure Name", updated!!.workoutName)

        // Verify zero DB queries or launches were triggered for the cached update
        verify(exactly = 0) { mockSummariesManager.getWorkoutCursor(10L) }
    }

    /**
     * [TST-STB-018.6] Defensive Deletion Reporting
     * Deletion failures and exceptions post Pair(id, false) and unconditionally reset
     * DeletionProgress to Idle via try/finally.
     */
    @Test
    fun testDefensiveDeletionReporting_failureAndExceptionResetIdle() = runBlocking {
        val events = java.util.concurrent.CopyOnWriteArrayList<Pair<Long, Boolean>>()
        val latchA = CountDownLatch(1)
        val latchB = CountDownLatch(2)

        val observer = Observer<Pair<Long, Boolean>> {
            events.add(it)
            latchA.countDown()
            latchB.countDown()
        }
        repository.deleteFinishedEvent.observeForever(observer)

        try {
            // Part A: Deletion returns false
            every { mockDeletionHelper.deleteWorkout(201L) } returns false
            repository.deleteWorkout(201L)
            assertTrue("Timed out waiting for Part A failure event", latchA.await(3, TimeUnit.SECONDS))
            assertEquals(1, events.size)
            assertEquals("Must report false on deletion failure", Pair(201L, false), events[0])
            assertEquals("Progress must reset to Idle on failure", DeletionProgress.Idle, repository.deletionProgress.value)

            // Part B: Deletion throws SQLiteException
            every { mockDeletionHelper.deleteWorkout(202L) } throws android.database.sqlite.SQLiteException("Disk error")
            repository.deleteWorkout(202L)
            assertTrue("Timed out waiting for Part B exception event", latchB.await(3, TimeUnit.SECONDS))
            assertEquals(2, events.size)
            assertEquals("Must report false on deletion exception", Pair(202L, false), events[1])
            assertEquals("Progress must reset to Idle on exception", DeletionProgress.Idle, repository.deletionProgress.value)
        } finally {
            repository.deleteFinishedEvent.removeObserver(observer)
        }
    }
}
