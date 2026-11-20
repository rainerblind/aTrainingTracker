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
import android.content.IntentFilter
import android.database.Cursor
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.tracker.WorkoutPostProcessingCoordinator
import com.atrainingtracker.trainingtracker.tracker.WorkoutPostProcessingCoordinator.HostController
import com.atrainingtracker.trainingtracker.tracker.WorkoutPostProcessingCoordinator.PostProcessingRequest
import com.atrainingtracker.trainingtracker.tracker.WorkoutPostProcessingCoordinator.PostProcessingSteps
import com.atrainingtracker.trainingtracker.tracker.WorkoutPostProcessingNotificationManager.PostProcessingStage
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.time.LocalDateTime

/**
 * Unit tests verifying [WorkoutRepository.saveWorkout] atomic export deferral while
 * [WorkoutPostProcessingCoordinator] is active, immediate `rpe`/`feel` in-memory parity,
 * and direct cursor-based [WorkoutRepository.loadFreshWorkoutForExport]
 * (`REQ-TRK-091.2`, `TST-TRK-091.2`, `ATT-9154`).
 */
// Linked test specs: TST-TRK-091, TST-TRK-091.2
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutRepositoryDeferredExportTest {

    private lateinit var mockApp: Application
    private lateinit var mockSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockClusterEngine: WorkoutClusterEngine
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

        mockkStatic(LocalBroadcastManager::class)
        val mockLbm = mockk<LocalBroadcastManager>(relaxed = true)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLbm

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        WorkoutPostProcessingCoordinator.resetForTesting()

        mockApp = mockk(relaxed = true)
        every { mockApp.applicationContext } returns mockApp
        mockSummariesManager = mockk(relaxed = true)
        mockClusterEngine = mockk(relaxed = true)

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager

        WorkoutClusterEngine.resetForTesting(mockClusterEngine)

        mockkConstructor(ExportManager::class)
        every { anyConstructed<ExportManager>().exportWorkout(any()) } just Runs

        val ctor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        ctor.isAccessible = true
        repository = ctor.newInstance(mockApp)
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        WorkoutPostProcessingCoordinator.resetForTesting()
        WorkoutRepository.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        unmockkAll()
    }

    private fun sampleWorkout(
        id: Long = 2414L,
        name: String = "Evening Run",
        rpe: Int? = 8,
        feel: Int? = 5
    ): WorkoutData = WorkoutData(
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
        mapPolyline = "poly",
        encodedAltitudes = "alt",
        encodedDistances = "dist",
        uploadToStrava = 1,
        totalDistance = 8000.0,
        maxDisplacement = 1200.0,
        activeTimeSec = 2400L,
        totalTimeSec = 2410L,
        avgSpeedMps = 3.33,
        ascentMeters = 30L,
        descentMeters = 28L,
        minAltitude = 100.0,
        maxAltitude = 130.0,
        description = "Felt strong",
        goal = "Easy",
        method = "Aerobic",
        stravaSportName = "Run",
        rpe = rpe,
        feel = feel
    )

    @Suppress("UNCHECKED_CAST")
    private fun seedAllWorkouts(workout: WorkoutData) {
        val field: Field = WorkoutRepository::class.java.getDeclaredField("_allWorkouts")
        field.isAccessible = true
        val flow = field.get(repository) as MutableStateFlow<List<WorkoutData>>
        flow.value = listOf(workout)
    }

    @Test
    fun testSaveWorkout_whenPostProcessingActive_persistsMetadataAndRpeFeelWhileDeferringExport_tstTrk091_2() = runTest {
        val blocker = CompletableDeferred<Unit>()
        WorkoutPostProcessingCoordinator.dispatcher = StandardTestDispatcher(testScheduler)
        WorkoutPostProcessingCoordinator.hostControllerOverride = object : HostController {
            override fun startForegroundHost(context: android.content.Context): Boolean = true
            override fun stopForegroundHost(context: android.content.Context) {}
            override fun onStageStarted(context: android.content.Context, stage: PostProcessingStage, currentStep: Int, totalSteps: Int) {}
            override fun onBatchCompleted(context: android.content.Context, hadWarning: Boolean) {}
        }
        WorkoutPostProcessingCoordinator.stepsOverride = object : PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: android.content.Context, request: PostProcessingRequest): Boolean {
                blocker.await()
                return true
            }
            override suspend fun runPolarVerityBackfill(context: android.content.Context, request: PostProcessingRequest): Boolean = true
            override suspend fun runHrvRecompute(context: android.content.Context, workoutId: Long): Boolean = true
            override suspend fun runErtsRecompute(context: android.content.Context, workoutId: Long): Boolean = true
            override suspend fun reloadWorkout(context: android.content.Context, workoutId: Long) {}
            override suspend fun markExportWorkoutFinished(context: android.content.Context, baseFileName: String) {}
            override suspend fun loadFreshWorkoutForExport(context: android.content.Context, workoutId: Long): WorkoutData? = null
            override suspend fun triggerDeferredExport(context: android.content.Context, workoutData: WorkoutData) {}
        }

        val initial = sampleWorkout(id = 2414L, name = "Initial", rpe = null, feel = null)
        seedAllWorkouts(initial)

        WorkoutPostProcessingCoordinator.enqueue(
            mockApp,
            PostProcessingRequest(2414L, "2026-11-20_180000", true, 1000L, 1L, false, 0L, 0L, false)
        )

        val edited = initial.copy(workoutName = "Edited Tempo", rpe = 8, feel = 5, description = "Updated notes")
        every { mockSummariesManager.updateWorkoutData(edited) } just Runs

        repository.saveWorkout(edited)
        Thread.sleep(200)

        verify(exactly = 1) { mockSummariesManager.updateWorkoutData(edited) }
        verify(exactly = 0) { anyConstructed<ExportManager>().exportWorkout(any()) }

        val cached = repository.allWorkouts.value.find { it.id == 2414L }
        assertNotNull(cached)
        assertEquals("Edited Tempo", cached?.workoutName)
        assertEquals(8, cached?.rpe)
        assertEquals(5, cached?.feel)

        blocker.complete(Unit)
    }

    @Test
    fun testSaveWorkout_whenPostProcessingInactive_triggersExportImmediately_tstTrk091_2() {
        val initial = sampleWorkout(id = 2415L, name = "After Finish", rpe = 6, feel = 4)
        seedAllWorkouts(initial)
        every { mockSummariesManager.updateWorkoutData(initial) } just Runs

        repository.saveWorkout(initial)
        Thread.sleep(200)

        verify(exactly = 1) { mockSummariesManager.updateWorkoutData(initial) }
        verify(exactly = 1) { anyConstructed<ExportManager>().exportWorkout(initial) }
    }

    @Test
    fun testLoadFreshWorkoutForExport_readsDirectSqliteCursorAndClosesCleanly_tstTrk091_2() = runTest {
        val mockCursor = mockk<Cursor>(relaxed = true)
        val expected = sampleWorkout(id = 2414L, name = "Fresh From DB", rpe = 7, feel = 4)
        mockkConstructor(WorkoutDataMapper::class)
        every { anyConstructed<WorkoutDataMapper>().fromCursor(mockCursor) } returns expected

        every { mockSummariesManager.getWorkoutCursor(2414L) } returns mockCursor
        every { mockCursor.moveToFirst() } returns true

        val result = repository.loadFreshWorkoutForExport(2414L)

        assertEquals(expected, result)
        verify(exactly = 1) { mockSummariesManager.getWorkoutCursor(2414L) }
        verify(exactly = 1) { mockCursor.close() }
    }

    @Test
    fun testSaveWorkout_deferredExportPreservesSyncToHealthConnectTrueAndFalseOverrides_tstTrk091_2() = runTest {
        val gate1 = CompletableDeferred<Unit>()
        val gate2 = CompletableDeferred<Unit>()
        val deferredExports = mutableMapOf<Long, WorkoutData>()

        WorkoutPostProcessingCoordinator.dispatcher = StandardTestDispatcher(testScheduler)
        WorkoutPostProcessingCoordinator.hostControllerOverride = object : HostController {
            override fun startForegroundHost(context: android.content.Context): Boolean = true
            override fun stopForegroundHost(context: android.content.Context) {}
            override fun onStageStarted(context: android.content.Context, stage: PostProcessingStage, currentStep: Int, totalSteps: Int) {}
            override fun onBatchCompleted(context: android.content.Context, hadWarning: Boolean) {}
        }
        WorkoutPostProcessingCoordinator.stepsOverride = object : PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: android.content.Context, request: PostProcessingRequest): Boolean {
                if (request.workoutId == 2420L) gate1.await() else gate2.await()
                return true
            }
            override suspend fun runPolarVerityBackfill(context: android.content.Context, request: PostProcessingRequest): Boolean = true
            override suspend fun runHrvRecompute(context: android.content.Context, workoutId: Long): Boolean = true
            override suspend fun runErtsRecompute(context: android.content.Context, workoutId: Long): Boolean = true
            override suspend fun reloadWorkout(context: android.content.Context, workoutId: Long) {}
            override suspend fun markExportWorkoutFinished(context: android.content.Context, baseFileName: String) {}
            override suspend fun loadFreshWorkoutForExport(context: android.content.Context, workoutId: Long): WorkoutData {
                // SQLite does not persist syncToHealthConnect, so reloaded row has null
                return sampleWorkout(id = workoutId, name = "Reloaded_$workoutId").copy(syncToHealthConnect = null)
            }
            override suspend fun triggerDeferredExport(context: android.content.Context, workoutData: WorkoutData) {
                deferredExports[workoutData.id] = workoutData
            }
        }

        val wTrue = sampleWorkout(id = 2420L, name = "HC Enabled").copy(syncToHealthConnect = true)
        val wFalse = sampleWorkout(id = 2421L, name = "HC Disabled").copy(syncToHealthConnect = false)
        seedAllWorkouts(wTrue)
        every { mockSummariesManager.updateWorkoutData(any()) } just Runs

        WorkoutPostProcessingCoordinator.enqueue(
            mockApp,
            PostProcessingRequest(2420L, "2026-11-20_181000", true, 1000L, 1L, false, 0L, 0L, false)
        )
        WorkoutPostProcessingCoordinator.enqueue(
            mockApp,
            PostProcessingRequest(2421L, "2026-11-20_182000", true, 2000L, 2L, false, 0L, 0L, false)
        )
        testScheduler.runCurrent()

        repository.saveWorkout(wTrue)
        repository.saveWorkout(wFalse)
        Thread.sleep(200)

        verify(exactly = 0) { anyConstructed<ExportManager>().exportWorkout(any()) }

        gate1.complete(Unit)
        gate2.complete(Unit)
        advanceUntilIdle()

        assertEquals(true, deferredExports[2420L]?.syncToHealthConnect)
        assertEquals(false, deferredExports[2421L]?.syncToHealthConnect)
    }
}
