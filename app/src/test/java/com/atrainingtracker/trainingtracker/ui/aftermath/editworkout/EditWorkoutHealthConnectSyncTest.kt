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
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.time.LocalDateTime

/**
 * Unit tests verifying per-workout Health Connect synchronization opt-out,
 * single-trigger execution in ExportManager, and state preservation in WorkoutRepository
 * pursuant to REQ-REV-014.1, REQ-REV-014.3, TST-REV-015.3, and TST-REV-015.4 (ATT-7669).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditWorkoutHealthConnectSyncTest {

    private data class SyncInvocation(
        val context: Context,
        val workoutData: WorkoutData,
        val force: Boolean
    )

    private val syncInvocations = mutableListOf<SyncInvocation>()

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockClusterEngine = mockk<WorkoutClusterEngine>(relaxed = true)

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

        every { mockContext.applicationContext } returns mockContext
        every { mockApplication.applicationContext } returns mockApplication

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.exportToFile(any()) } returns false
        every { TrainingApplication.isWorkManagerAvailable() } returns true

        syncInvocations.clear()
        HealthConnectManager.syncWorkoutAsyncDelegate = { ctx, data, force ->
            syncInvocations.add(SyncInvocation(ctx, data, force))
        }
    }

    @After
    fun tearDown() {
        try {
            unmockkConstructor(ExportManager::class)
        } catch (ignored: Exception) {}
        HealthConnectManager.syncWorkoutAsyncDelegate = null
        ArchTaskExecutor.getInstance().setDelegate(null)
        WorkoutRepository.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        unmockkAll()
    }

    private fun createTestWorkout(
        workoutId: Long = 42L,
        syncToHealthConnect: Boolean? = null
    ): WorkoutData {
        return WorkoutData(
            id = workoutId,
            finished = true,
            fileBaseName = "2026-11-14_10-00-00",
            workoutName = "Test Session",
            sportId = 1L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1700000000L,
            formattedDate = "2026-11-14",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.of(2026, 11, 14, 10, 0),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 5000.0,
            maxDisplacement = 2000.0,
            activeTimeSec = 1500L,
            totalTimeSec = 1600L,
            avgSpeedMps = 3.33,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = null,
            maxAltitude = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            startLatLng = LatLng(48.137, 11.576),
            endLatLng = LatLng(48.138, 11.577),
            maxDisplacementLatLng = LatLng(48.140, 11.580),
            clusterId = -1L,
            clusterName = null,
            extremaRows = emptyList(),
            laps = emptyList(),
            exportStatuses = emptyList(),
            syncToHealthConnect = syncToHealthConnect
        )
    }

    /**
     * TST-REV-015.3: ExportManager evaluates workoutData.getSyncToHealthConnect():
     * - Opt-out (false) skips sync even if global setting is true.
     * - Opt-in (true) triggers sync with force = true.
     * - Null fallback evaluates global preference with force = false.
     * - Null with global setting false skips sync.
     */
    @Test
    fun testExportManager_honorsPerWorkoutSyncOptOutAndForceSync() {
        val exportManager = ExportManager(mockContext)

        // Case 1: Global sync enabled, but workout explicitly opts out (syncToHealthConnect = false)
        every { TrainingApplication.syncToHealthConnect() } returns true
        val optOutWorkout = createTestWorkout(workoutId = 101L, syncToHealthConnect = false)
        syncInvocations.clear()
        exportManager.exportWorkout(optOutWorkout)
        assertTrue("Sync must not be called when syncToHealthConnect is false", syncInvocations.isEmpty())

        // Case 2: Workout explicitly opts in (syncToHealthConnect = true) -> force = true
        every { TrainingApplication.syncToHealthConnect() } returns false // global false should be overridden by explicit true
        val optInWorkout = createTestWorkout(workoutId = 102L, syncToHealthConnect = true)
        syncInvocations.clear()
        exportManager.exportWorkout(optInWorkout)
        assertEquals("Sync must be invoked once for opt-in", 1, syncInvocations.size)
        assertEquals(102L, syncInvocations[0].workoutData.id)
        assertEquals(true, syncInvocations[0].force)

        // Case 3: Workout has syncToHealthConnect = null, global sync enabled -> legacy fallback, force = false
        every { TrainingApplication.syncToHealthConnect() } returns true
        val defaultEnabledWorkout = createTestWorkout(workoutId = 103L, syncToHealthConnect = null)
        syncInvocations.clear()
        exportManager.exportWorkout(defaultEnabledWorkout)
        assertEquals("Sync must be invoked once for legacy default", 1, syncInvocations.size)
        assertEquals(103L, syncInvocations[0].workoutData.id)
        assertEquals(false, syncInvocations[0].force)

        // Case 4: Workout has syncToHealthConnect = null, global sync disabled -> no sync
        every { TrainingApplication.syncToHealthConnect() } returns false
        val defaultDisabledWorkout = createTestWorkout(workoutId = 104L, syncToHealthConnect = null)
        syncInvocations.clear()
        exportManager.exportWorkout(defaultDisabledWorkout)
        assertTrue("Sync must not be called when global sync is false and per-workout sync is null", syncInvocations.isEmpty())
    }

    /**
     * TST-REV-015.4: WorkoutRepository.saveWorkout preserves syncToHealthConnect
     * both when passing to ExportManager and when updating in-memory cache.
     */
    @Test
    fun testWorkoutRepository_preservesSyncToHealthConnectOnSave() = runBlocking(Dispatchers.IO) {
        try {
            mockkConstructor(IntentFilter::class)
            every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

            mockkStatic(LocalBroadcastManager::class)
            val mockLbm = mockk<LocalBroadcastManager>(relaxed = true)
            every { LocalBroadcastManager.getInstance(any()) } returns mockLbm

            mockkStatic(ContextCompat::class)
            every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

            mockkStatic(WorkoutSummariesDatabaseManager::class)
            every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

            WorkoutClusterEngine.resetForTesting(mockClusterEngine)

            mockkConstructor(ExportManager::class)
            val capturedWorkouts = mutableListOf<WorkoutData>()
            every { anyConstructed<ExportManager>().exportWorkout(capture(capturedWorkouts)) } returns Unit

            val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
            constructor.isAccessible = true
            val repository = constructor.newInstance(mockApplication)

            // Seed in-memory cache
            val initialWorkout = createTestWorkout(workoutId = 42L, syncToHealthConnect = null)
            val field: Field = WorkoutRepository::class.java.getDeclaredField("_allWorkouts")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val flow = field.get(repository) as MutableStateFlow<List<WorkoutData>>
            flow.value = listOf(initialWorkout)

            // Test saving with syncToHealthConnect = false
            val editedOptOut = initialWorkout.copy(syncToHealthConnect = false)
            repository.saveWorkout(editedOptOut)
            delay(150)

            assertEquals("ExportManager must receive workout with syncToHealthConnect = false", false, capturedWorkouts.last().syncToHealthConnect)
            val inMemoryOptOut = repository.allWorkouts.value.find { it.id == 42L }
            assertEquals("In-memory cache must retain syncToHealthConnect = false", false, inMemoryOptOut?.syncToHealthConnect)

            // Test saving with syncToHealthConnect = true
            val editedOptIn = initialWorkout.copy(syncToHealthConnect = true)
            repository.saveWorkout(editedOptIn)
            delay(150)

            assertEquals("ExportManager must receive workout with syncToHealthConnect = true", true, capturedWorkouts.last().syncToHealthConnect)
            val inMemoryOptIn = repository.allWorkouts.value.find { it.id == 42L }
            assertEquals("In-memory cache must retain syncToHealthConnect = true", true, inMemoryOptIn?.syncToHealthConnect)
        } finally {
            try {
                unmockkConstructor(ExportManager::class)
            } catch (ignored: Exception) {}
        }
    }
}
