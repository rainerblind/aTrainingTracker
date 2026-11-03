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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.IntentFilter
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.time.LocalDateTime

/**
 * Unit tests verifying WorkoutRepository.saveWorkout clustering behavior (REQ-SET-081, TST-SET-081, ATT-4862).
 * Verifies that unclustered workouts ('Offen', clusterId <= 0) strictly remain unclustered when saving
 * workout metadata edits and never trigger automatic cluster creation or assignment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutRepositorySaveClusteringTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockClusterEngine = mockk<WorkoutClusterEngine>(relaxed = true)
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

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

        mockkStatic(LocalBroadcastManager::class)
        val mockLbm = mockk<LocalBroadcastManager>(relaxed = true)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLbm

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        every { mockApplication.applicationContext } returns mockApplication

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        WorkoutClusterEngine.resetForTesting(mockClusterEngine)

        mockkConstructor(ExportManager::class)
        every { anyConstructed<ExportManager>().exportWorkout(any()) } returns mockk(relaxed = true)

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        WorkoutRepository.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        unmockkAll()
    }

    private fun seedWorkouts(workouts: List<WorkoutData>) {
        val field: Field = WorkoutRepository::class.java.getDeclaredField("_allWorkouts")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(repository) as MutableStateFlow<List<WorkoutData>>
        flow.value = workouts
    }

    private fun createTestWorkout(
        workoutId: Long,
        workoutName: String,
        clusterId: Long = -1L,
        clusterName: String? = null
    ): WorkoutData {
        return WorkoutData(
            id = workoutId,
            finished = true,
            fileBaseName = "workout_$workoutId",
            workoutName = workoutName,
            sportId = 1L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1700000000L,
            formattedDate = "2026-10-31",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.of(2026, 10, 31, 10, 0),
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
            clusterId = clusterId,
            clusterName = clusterName,
            extremaRows = emptyList(),
            laps = emptyList(),
            exportStatuses = emptyList()
        )
    }

    /**
     * TST-SET-081.1: Unclustered workout name edit must NOT create or assign a new cluster (REQ-SET-081.1).
     */
    @Test
    fun testSaveWorkout_whenUnclustered_preservesUnclusteredStatusWithoutCreatingCluster() = runBlocking(Dispatchers.IO) {
        val initialWorkout = createTestWorkout(42L, "2026-10-31 10:00", clusterId = -1L)
        seedWorkouts(listOf(initialWorkout))

        val editedWorkout = initialWorkout.copy(
            workoutName = "Custom Morning Jog"
        )

        repository.saveWorkout(editedWorkout)
        delay(150) // allow launch(Dispatchers.IO) in repository to execute

        // Verify cluster learning and assignment are never invoked for clusterId <= 0
        verify(exactly = 0) {
            mockClusterEngine.learnFromWorkout(any(), any(), any(), any(), any(), any(), any())
        }
        verify(exactly = 0) {
            mockClusterEngine.assignClusterToWorkout(any(), any(), any())
        }

        // Verify in-memory cache updated with custom name while preserving clusterId == -1L
        val saved = repository.allWorkouts.value.find { it.id == 42L }
        assertEquals("Custom Morning Jog", saved?.workoutName)
        assertEquals(-1L, saved?.clusterId)
    }

    /**
     * TST-SET-081.2: Unassigning a cluster ('Offen') and saving custom name edit preserves unclustered status (REQ-SET-081.2).
     */
    @Test
    fun testSaveWorkout_afterUnassignCluster_preservesUnclusteredStatusOnCustomNameEdit() = runBlocking(Dispatchers.IO) {
        val previouslyClustered = createTestWorkout(42L, "Old Cluster #3", clusterId = 10L, clusterName = "Old Cluster")
        seedWorkouts(listOf(previouslyClustered))

        // User unassigned cluster to 'Offen' (clusterId = -1L) and entered custom name
        val unclusteredEdit = previouslyClustered.copy(
            clusterId = -1L,
            clusterName = null,
            workoutName = "Standalone Evening Run"
        )

        repository.saveWorkout(unclusteredEdit)
        delay(150)

        // Cluster engine should not learn or assign
        verify(exactly = 0) {
            mockClusterEngine.learnFromWorkout(any(), any(), any(), any(), any(), any(), any())
        }
        verify(exactly = 0) {
            mockClusterEngine.assignClusterToWorkout(any(), any(), any())
        }

        val saved = repository.allWorkouts.value.find { it.id == 42L }
        assertEquals("Standalone Evening Run", saved?.workoutName)
        assertEquals(-1L, saved?.clusterId)
    }

    /**
     * TST-SET-081.3: Clustered workout updates centroids on save without reassigning cluster (REQ-SET-081.3).
     */
    @Test
    fun testSaveWorkout_whenClustered_updatesCentroidsWithoutReassigningCluster() = runBlocking(Dispatchers.IO) {
        val clusteredWorkout = createTestWorkout(42L, "Hausrunde #5", clusterId = 10L, clusterName = "Hausrunde")
        seedWorkouts(listOf(clusteredWorkout))

        every {
            mockClusterEngine.learnFromWorkout(any(), any(), any(), any(), any(), any(), any())
        } returns 10L

        val editedWorkout = clusteredWorkout.copy(
            workoutName = "Hausrunde #5 (Fast)"
        )

        repository.saveWorkout(editedWorkout)
        delay(150)

        // Verify learnFromWorkout invoked with clusterIdOverride = 10L
        verify(exactly = 1) {
            mockClusterEngine.learnFromWorkout(
                start = any(),
                end = any(),
                apex = any(),
                distance = any(),
                userSpecifiedName = any(),
                userSportId = any(),
                clusterIdOverride = 10L
            )
        }
        // Verify assignClusterToWorkout is NOT invoked
        verify(exactly = 0) {
            mockClusterEngine.assignClusterToWorkout(any(), any(), any())
        }

        val saved = repository.allWorkouts.value.find { it.id == 42L }
        assertEquals("Hausrunde #5 (Fast)", saved?.workoutName)
        assertEquals(10L, saved?.clusterId)
    }
}
