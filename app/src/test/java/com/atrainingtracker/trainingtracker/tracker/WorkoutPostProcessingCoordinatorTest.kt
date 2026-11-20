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

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.tracker.WorkoutPostProcessingCoordinator.HostController
import com.atrainingtracker.trainingtracker.tracker.WorkoutPostProcessingCoordinator.PostProcessingRequest
import com.atrainingtracker.trainingtracker.tracker.WorkoutPostProcessingCoordinator.PostProcessingSteps
import com.atrainingtracker.trainingtracker.tracker.WorkoutPostProcessingNotificationManager.PostProcessingStage
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Pure-JVM unit tests for [WorkoutPostProcessingCoordinator] verifying decoupled async stage
 * ordering, fault isolation, atomic export deferral lock, and fresh SQLite reload ordering
 * (`REQ-TRK-091.1`, `REQ-TRK-091.2`, `TST-TRK-091.1`, `TST-TRK-091.2`, `ATT-9154`).
 */
// Linked test specs: TST-TRK-091, TST-TRK-091.1, TST-TRK-091.2
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutPostProcessingCoordinatorTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        WorkoutPostProcessingCoordinator.resetForTesting()
    }

    @After
    fun tearDown() {
        WorkoutPostProcessingCoordinator.resetForTesting()
        unmockkAll()
    }

    private fun sampleWorkoutData(id: Long, name: String, distance: Double): WorkoutData = WorkoutData(
        id = id,
        finished = true,
        fileBaseName = "2026-11-20_180000",
        workoutName = name,
        sportId = 1L,
        sportName = "Running",
        bSportType = BSportType.RUN,
        startTimeS = 1763661600L,
        formattedDate = "20.11.2026",
        formattedTime = "18:00",
        localDateTime = LocalDateTime.now(),
        equipmentName = "Pegasus",
        equipmentId = 3L,
        commute = false,
        trainer = false,
        mapPolyline = "smoothed_poly",
        encodedAltitudes = "alt",
        encodedDistances = "dist",
        uploadToStrava = 1,
        totalDistance = distance,
        maxDisplacement = 1500.0,
        activeTimeSec = 1800L,
        totalTimeSec = 1820L,
        avgSpeedMps = 3.5,
        ascentMeters = 45L,
        descentMeters = 40L,
        minAltitude = 100.0,
        maxAltitude = 145.0,
        description = "Saved user notes",
        goal = "Tempo",
        method = "Intervals",
        stravaSportName = "Run",
        rpe = 7,
        feel = 4
    )

    @Test
    fun testEnqueue_executesStrictStageOrderWithoutBlockingCaller_tstTrk091_1() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        WorkoutPostProcessingCoordinator.dispatcher = dispatcher

        val events = mutableListOf<String>()
        val stages = mutableListOf<Triple<PostProcessingStage, Int, Int>>()
        var serviceStarted = 0
        var serviceStopped = 0
        var completionWarning: Boolean? = null

        WorkoutPostProcessingCoordinator.hostControllerOverride = object : HostController {
            override fun startForegroundHost(context: Context): Boolean {
                serviceStarted++
                return true
            }

            override fun stopForegroundHost(context: Context) {
                serviceStopped++
            }

            override fun onStageStarted(context: Context, stage: PostProcessingStage, currentStep: Int, totalSteps: Int) {
                stages.add(Triple(stage, currentStep, totalSteps))
            }

            override fun onBatchCompleted(context: Context, hadWarning: Boolean) {
                completionWarning = hadWarning
            }
        }

        WorkoutPostProcessingCoordinator.stepsOverride = object : PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: Context, request: PostProcessingRequest): Boolean {
                events.add("H10(token=${request.h10ArmedToken})")
                return true
            }

            override suspend fun runPolarVerityBackfill(context: Context, request: PostProcessingRequest): Boolean {
                events.add("VERITY(token=${request.verityArmedToken})")
                return true
            }

            override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean {
                events.add("HRV")
                return true
            }

            override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean {
                events.add("ERTS")
                return true
            }

            override suspend fun reloadWorkout(context: Context, workoutId: Long) {
                events.add("RELOAD")
            }

            override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {
                events.add("WORKOUT_FINISHED($baseFileName)")
            }

            override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long): WorkoutData? {
                events.add("LOAD_FRESH")
                return null
            }

            override suspend fun triggerDeferredExport(context: Context, workoutData: WorkoutData) {
                events.add("DEFERRED_EXPORT")
            }
        }

        val req = PostProcessingRequest(
            workoutId = 2414L,
            baseFileName = "2026-11-20_180000",
            h10Armed = true,
            h10StartMs = 1000L,
            h10ArmedToken = 11L,
            verityArmed = true,
            verityStartMs = 1005L,
            verityArmedToken = 22L,
            ertsEnabled = true
        )

        // REQ-TRK-091.1: enqueue returns immediately before any blocking step executes
        WorkoutPostProcessingCoordinator.enqueue(mockContext, req)
        assertTrue("Workout must be active immediately after enqueue", WorkoutPostProcessingCoordinator.isPostProcessingActive(2414L))
        assertTrue("No blocking steps should have executed synchronously", events.isEmpty())
        assertEquals(1, serviceStarted)

        advanceUntilIdle()

        assertEquals(
            listOf("H10(token=11)", "VERITY(token=22)", "HRV", "ERTS", "RELOAD", "WORKOUT_FINISHED(2026-11-20_180000)"),
            events
        )
        assertEquals(
            listOf(
                Triple(PostProcessingStage.POLAR_H10, 0, 4),
                Triple(PostProcessingStage.POLAR_VERITY, 1, 4),
                Triple(PostProcessingStage.RECOMPUTE, 2, 4),
                Triple(PostProcessingStage.EXPORT, 3, 4)
            ),
            stages
        )
        assertFalse(WorkoutPostProcessingCoordinator.isPostProcessingActive(2414L))
        assertFalse(WorkoutPostProcessingCoordinator.hasActiveWork())
        assertEquals(false, completionWarning)
        assertEquals(1, serviceStopped)
    }

    @Test
    fun testEarlySaveDuringPostProcessing_defersExportAndExportsFreshRowAfterWorkoutFinished_tstTrk091_2() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        WorkoutPostProcessingCoordinator.dispatcher = dispatcher

        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var exportedWorkout: WorkoutData? = null
        val freshFromDb = sampleWorkoutData(2414L, "User Renamed Tempo", 10420.0)

        WorkoutPostProcessingCoordinator.hostControllerOverride = object : HostController {
            override fun startForegroundHost(context: Context): Boolean = true
            override fun stopForegroundHost(context: Context) {}
            override fun onStageStarted(context: Context, stage: PostProcessingStage, currentStep: Int, totalSteps: Int) {}
            override fun onBatchCompleted(context: Context, hadWarning: Boolean) {}
        }

        WorkoutPostProcessingCoordinator.stepsOverride = object : PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: Context, request: PostProcessingRequest): Boolean {
                events.add("H10_START")
                gate.await()
                events.add("H10_DONE")
                return true
            }

            override suspend fun runPolarVerityBackfill(context: Context, request: PostProcessingRequest): Boolean = true

            override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean {
                events.add("HRV")
                return true
            }

            override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean {
                events.add("ERTS")
                return true
            }

            override suspend fun reloadWorkout(context: Context, workoutId: Long) {
                events.add("RELOAD")
            }

            override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {
                events.add("WORKOUT_FINISHED")
            }

            override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long): WorkoutData {
                events.add("LOAD_FRESH_SQLITE")
                return freshFromDb
            }

            override suspend fun triggerDeferredExport(context: Context, workoutData: WorkoutData) {
                events.add("DEFERRED_EXPORT")
                exportedWorkout = workoutData
            }
        }

        WorkoutPostProcessingCoordinator.enqueue(
            mockContext,
            PostProcessingRequest(2414L, "2026-11-20_180000", true, 1000L, 1L, false, 0L, 0L, true)
        )
        testScheduler.runCurrent()

        // User taps Save on EditWorkoutScreen while H10 backfill is in flight
        assertTrue(
            "Early Save must atomically defer export while post-processing is active",
            WorkoutPostProcessingCoordinator.deferExportIfPostProcessing(2414L)
        )

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("H10_START", "H10_DONE", "HRV", "ERTS", "RELOAD", "WORKOUT_FINISHED", "LOAD_FRESH_SQLITE", "DEFERRED_EXPORT"),
            events
        )
        assertEquals("User Renamed Tempo", exportedWorkout?.workoutName)
        assertEquals(10420.0, exportedWorkout?.totalDistance ?: 0.0, 0.001)
        assertEquals(7, exportedWorkout?.rpe)
        assertEquals(4, exportedWorkout?.feel)
        assertFalse(
            "Subsequent Save after completion must NOT defer export",
            WorkoutPostProcessingCoordinator.deferExportIfPostProcessing(2414L)
        )
    }

    @Test
    fun testFaultIsolation_stageExceptionContinuesPipelineClearsActiveStateAndPostsWarning_tstTrk091_1() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        WorkoutPostProcessingCoordinator.dispatcher = dispatcher

        val events = mutableListOf<String>()
        var warningFlag: Boolean? = null

        WorkoutPostProcessingCoordinator.hostControllerOverride = object : HostController {
            override fun startForegroundHost(context: Context): Boolean = false // simulate FGS fallback
            override fun stopForegroundHost(context: Context) {
                events.add("STOP_SERVICE")
            }
            override fun onStageStarted(context: Context, stage: PostProcessingStage, currentStep: Int, totalSteps: Int) {}
            override fun onBatchCompleted(context: Context, hadWarning: Boolean) {
                warningFlag = hadWarning
            }
        }

        WorkoutPostProcessingCoordinator.stepsOverride = object : PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: Context, request: PostProcessingRequest): Boolean {
                events.add("H10_THROW")
                throw IllegalStateException("Simulated BLE PFTP failure")
            }

            override suspend fun runPolarVerityBackfill(context: Context, request: PostProcessingRequest): Boolean {
                events.add("VERITY_OK")
                return true
            }

            override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean {
                events.add("HRV_OK")
                return true
            }

            override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean = true

            override suspend fun reloadWorkout(context: Context, workoutId: Long) {
                events.add("RELOAD_OK")
            }

            override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {
                events.add("EXPORT_FINISHED_OK")
            }

            override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long): WorkoutData {
                events.add("LOAD_FRESH_OK")
                return sampleWorkoutData(workoutId, "Recovered", 5000.0)
            }

            override suspend fun triggerDeferredExport(context: Context, workoutData: WorkoutData) {
                events.add("DEFERRED_EXPORT_OK")
            }
        }

        WorkoutPostProcessingCoordinator.enqueue(
            mockContext,
            PostProcessingRequest(99L, "2026-11-20_190000", true, 1000L, 1L, true, 1000L, 2L, false)
        )
        assertTrue(WorkoutPostProcessingCoordinator.deferExportIfPostProcessing(99L))
        advanceUntilIdle()

        assertEquals(
            listOf("H10_THROW", "VERITY_OK", "HRV_OK", "RELOAD_OK", "EXPORT_FINISHED_OK", "LOAD_FRESH_OK", "DEFERRED_EXPORT_OK", "STOP_SERVICE"),
            events
        )
        assertFalse("Workout must never remain stuck in activeWorkouts after exception", WorkoutPostProcessingCoordinator.isPostProcessingActive(99L))
        assertEquals(true, warningFlag)
    }

    @Test
    fun testBackToBackEnqueue_serializesSingleWorkerAndNeverClobbersNewWork_tstTrk091_1() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        WorkoutPostProcessingCoordinator.dispatcher = dispatcher

        val gate1 = CompletableDeferred<Unit>()
        val gate2 = CompletableDeferred<Unit>()
        var inFlight = 0
        var maxInFlight = 0
        val events = mutableListOf<String>()
        var batchCompletedCount = 0
        var stopHostCount = 0

        WorkoutPostProcessingCoordinator.hostControllerOverride = object : HostController {
            override fun startForegroundHost(context: Context): Boolean = true
            override fun stopForegroundHost(context: Context) {
                stopHostCount++
                events.add("STOP_HOST")
            }
            override fun onStageStarted(context: Context, stage: PostProcessingStage, currentStep: Int, totalSteps: Int) {}
            override fun onBatchCompleted(context: Context, hadWarning: Boolean) {
                batchCompletedCount++
                events.add("BATCH_DONE($hadWarning)")
            }
        }

        WorkoutPostProcessingCoordinator.stepsOverride = object : PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: Context, request: PostProcessingRequest): Boolean {
                inFlight++
                if (inFlight > maxInFlight) maxInFlight = inFlight
                events.add("START_${request.workoutId}")
                if (request.workoutId == 101L) gate1.await() else gate2.await()
                events.add("END_${request.workoutId}")
                inFlight--
                return true
            }
            override suspend fun runPolarVerityBackfill(context: Context, request: PostProcessingRequest): Boolean = true
            override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun reloadWorkout(context: Context, workoutId: Long) {}
            override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {
                events.add("EXPORT_$baseFileName")
            }
            override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long): WorkoutData? = null
            override suspend fun triggerDeferredExport(context: Context, workoutData: WorkoutData) {}
        }

        // Enqueue workout 101, start processing, then enqueue workout 102 while 101 is active
        WorkoutPostProcessingCoordinator.enqueue(
            mockContext,
            PostProcessingRequest(101L, "w101", true, 1000L, 1L, false, 0L, 0L, false)
        )
        testScheduler.runCurrent()
        assertTrue(WorkoutPostProcessingCoordinator.isPostProcessingActive(101L))

        WorkoutPostProcessingCoordinator.enqueue(
            mockContext,
            PostProcessingRequest(102L, "w102", true, 2000L, 2L, false, 0L, 0L, false)
        )
        testScheduler.runCurrent()

        // Complete workout 101 -> batch must NOT complete or stop FGS while 102 is queued/running
        gate1.complete(Unit)
        testScheduler.runCurrent()
        assertEquals("FGS must not stop while workout 102 is still active", 0, stopHostCount)
        assertEquals("onBatchCompleted must not fire while workout 102 is still active", 0, batchCompletedCount)
        assertTrue(WorkoutPostProcessingCoordinator.isPostProcessingActive(102L))

        // Complete workout 102 -> now batch completes and stops FGS exactly once
        gate2.complete(Unit)
        advanceUntilIdle()

        assertEquals("Single-worker serialization must never overlap", 1, maxInFlight)
        assertEquals(1, batchCompletedCount)
        assertEquals(1, stopHostCount)
        assertEquals(
            listOf("START_101", "END_101", "EXPORT_w101", "START_102", "END_102", "EXPORT_w102", "BATCH_DONE(false)", "STOP_HOST"),
            events
        )
        assertFalse(WorkoutPostProcessingCoordinator.hasActiveWork())
    }

    @Test
    fun testDeferredExport_preservesExplicitSyncToHealthConnectTrueAndFalseOverrides_tstTrk091_2() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        WorkoutPostProcessingCoordinator.dispatcher = dispatcher

        val gateTrue = CompletableDeferred<Unit>()
        val gateFalse = CompletableDeferred<Unit>()
        val exportedPayloads = mutableMapOf<Long, WorkoutData>()

        WorkoutPostProcessingCoordinator.hostControllerOverride = object : HostController {
            override fun startForegroundHost(context: Context): Boolean = true
            override fun stopForegroundHost(context: Context) {}
            override fun onStageStarted(context: Context, stage: PostProcessingStage, currentStep: Int, totalSteps: Int) {}
            override fun onBatchCompleted(context: Context, hadWarning: Boolean) {}
        }

        WorkoutPostProcessingCoordinator.stepsOverride = object : PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: Context, request: PostProcessingRequest): Boolean {
                if (request.workoutId == 3001L) gateTrue.await() else gateFalse.await()
                return true
            }
            override suspend fun runPolarVerityBackfill(context: Context, request: PostProcessingRequest): Boolean = true
            override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun reloadWorkout(context: Context, workoutId: Long) {}
            override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {}
            override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long): WorkoutData {
                // SQLite cursor reload always yields syncToHealthConnect = null
                return sampleWorkoutData(workoutId, "SQLiteRow_$workoutId", 9000.0).copy(syncToHealthConnect = null)
            }
            override suspend fun triggerDeferredExport(context: Context, workoutData: WorkoutData) {
                exportedPayloads[workoutData.id] = workoutData
            }
        }

        WorkoutPostProcessingCoordinator.enqueue(
            mockContext,
            PostProcessingRequest(3001L, "w3001", true, 1000L, 1L, false, 0L, 0L, false)
        )
        WorkoutPostProcessingCoordinator.enqueue(
            mockContext,
            PostProcessingRequest(3002L, "w3002", true, 2000L, 2L, false, 0L, 0L, false)
        )
        testScheduler.runCurrent()

        assertTrue(WorkoutPostProcessingCoordinator.deferExportIfPostProcessing(3001L, syncToHealthConnect = true))
        assertTrue(WorkoutPostProcessingCoordinator.deferExportIfPostProcessing(3002L, syncToHealthConnect = false))

        gateTrue.complete(Unit)
        gateFalse.complete(Unit)
        advanceUntilIdle()

        assertEquals(true, exportedPayloads[3001L]?.syncToHealthConnect)
        assertEquals(false, exportedPayloads[3002L]?.syncToHealthConnect)
    }

    @Test
    fun testEnqueueDuringIdleFinalization_hostLockPreventsFgsOrNotificationClobber_tstTrk091_1() {
        val executor = Executors.newFixedThreadPool(2)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val firstBatchEnteredFinalize = CountDownLatch(1)
        val allBatchesDone = CountDownLatch(2)
        var batchCalls = 0

        try {
            WorkoutPostProcessingCoordinator.dispatcher = executor.asCoroutineDispatcher()
            WorkoutPostProcessingCoordinator.hostControllerOverride = object : HostController {
                override fun startForegroundHost(context: Context): Boolean {
                    events.add("START_HOST")
                    return true
                }
                override fun stopForegroundHost(context: Context) {
                    events.add("STOP_HOST")
                    allBatchesDone.countDown()
                }
                override fun onStageStarted(context: Context, stage: PostProcessingStage, currentStep: Int, totalSteps: Int) {}
                override fun onBatchCompleted(context: Context, hadWarning: Boolean) {
                    batchCalls++
                    events.add("BATCH_DONE_$batchCalls($hadWarning)")
                    if (batchCalls == 1) {
                        firstBatchEnteredFinalize.countDown()
                        // Hold hostLock long enough for concurrent Thread 2 to attempt enqueue()
                        Thread.sleep(80)
                    }
                }
            }
            WorkoutPostProcessingCoordinator.stepsOverride = object : PostProcessingSteps {
                override suspend fun runPolarH10Backfill(context: Context, request: PostProcessingRequest): Boolean = true
                override suspend fun runPolarVerityBackfill(context: Context, request: PostProcessingRequest): Boolean = true
                override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean = true
                override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean = true
                override suspend fun reloadWorkout(context: Context, workoutId: Long) {}
                override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {
                    events.add("WORKOUT_$baseFileName")
                }
                override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long): WorkoutData? = null
                override suspend fun triggerDeferredExport(context: Context, workoutData: WorkoutData) {}
            }

            WorkoutPostProcessingCoordinator.enqueue(
                mockContext,
                PostProcessingRequest(4001L, "w4001", false, 0L, 0L, false, 0L, 0L, false)
            )
            assertTrue(
                "First batch should enter idle finalization promptly",
                firstBatchEnteredFinalize.await(3, TimeUnit.SECONDS)
            )

            // Concurrent enqueue arriving while batch 1 is inside onBatchCompleted() holding hostLock
            val enqueuerThread = Thread {
                WorkoutPostProcessingCoordinator.enqueue(
                    mockContext,
                    PostProcessingRequest(4002L, "w4002", false, 0L, 0L, false, 0L, 0L, false)
                )
            }
            enqueuerThread.start()
            enqueuerThread.join(3000)

            assertTrue(
                "Both batches must complete cleanly without deadlocking or clobbering FGS",
                allBatchesDone.await(5, TimeUnit.SECONDS)
            )

            assertEquals(
                listOf(
                    "START_HOST",
                    "WORKOUT_w4001",
                    "BATCH_DONE_1(false)",
                    "STOP_HOST",
                    "START_HOST",
                    "WORKOUT_w4002",
                    "BATCH_DONE_2(false)",
                    "STOP_HOST"
                ),
                events.toList()
            )
            assertFalse(WorkoutPostProcessingCoordinator.hasActiveWork())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun testShouldRestartDrain_preservesBatchHadWarningFromPrecedingJob_tstTrk091_1() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        WorkoutPostProcessingCoordinator.dispatcher = dispatcher

        val completionWarnings = mutableListOf<Boolean>()
        val exported = mutableListOf<String>()

        WorkoutPostProcessingCoordinator.hostControllerOverride = object : HostController {
            override fun startForegroundHost(context: Context): Boolean = true
            override fun stopForegroundHost(context: Context) {}
            override fun onStageStarted(context: Context, stage: PostProcessingStage, currentStep: Int, totalSteps: Int) {}
            override fun onBatchCompleted(context: Context, hadWarning: Boolean) {
                completionWarnings.add(hadWarning)
            }
        }

        WorkoutPostProcessingCoordinator.stepsOverride = object : PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: Context, request: PostProcessingRequest): Boolean {
                return when (request.workoutId) {
                    5001L -> throw IllegalStateException("Job 1 failure triggers warning")
                    5002L -> throw CancellationException("Force drainQueue exit into finally while Job 3 is pending")
                    else -> true
                }
            }
            override suspend fun runPolarVerityBackfill(context: Context, request: PostProcessingRequest): Boolean = true
            override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun reloadWorkout(context: Context, workoutId: Long) {}
            override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {
                exported.add(baseFileName)
            }
            override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long): WorkoutData? = null
            override suspend fun triggerDeferredExport(context: Context, workoutData: WorkoutData) {}
        }

        // Enqueue 3 jobs in a single batch: Job 1 sets batchHadWarning=true, Job 2 exits via CancellationException
        // triggering shouldRestartDrain=true while Job 3 is still pending, and Job 3 succeeds cleanly.
        WorkoutPostProcessingCoordinator.enqueue(
            mockContext,
            PostProcessingRequest(5001L, "w5001", true, 1000L, 1L, false, 0L, 0L, false)
        )
        WorkoutPostProcessingCoordinator.enqueue(
            mockContext,
            PostProcessingRequest(5002L, "w5002", true, 2000L, 2L, false, 0L, 0L, false)
        )
        WorkoutPostProcessingCoordinator.enqueue(
            mockContext,
            PostProcessingRequest(5003L, "w5003", true, 3000L, 3L, false, 0L, 0L, false)
        )

        advanceUntilIdle()

        assertEquals(listOf("w5001", "w5003"), exported)
        assertEquals(
            "Restarted drain must preserve batchHadWarning=true from Job 1 instead of resetting to false",
            listOf(true),
            completionWarnings
        )
        assertFalse(WorkoutPostProcessingCoordinator.hasActiveWork())
    }
}
