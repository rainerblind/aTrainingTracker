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

package com.atrainingtracker.trainingtracker.ui.map

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit verification for [WorkoutClusterRepository] & [TrackOnMapAftermathViewModel] cluster swap
 * orchestration and indoor/non-GPS workout fallback (ATT-8097 / REQ-UI-2786.3 / TST-UI-2786.2).
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackOnMapClusterActionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockClusterDb: WorkoutClusterDatabaseManager
    private lateinit var mockClusterEngine: WorkoutClusterEngine
    private lateinit var mockWorkoutRepo: WorkoutRepository

    private companion object {
        const val SAMPLE_TIME_S = 1_760_000_000L
        const val SAMPLE_DISTANCE_M = 12_000.0
        const val SAMPLE_DURATION_S = 2_400L
        const val WORKOUT_ID = 77L
        const val TARGET_CLUSTER_ID = 12L
        const val OLD_CLUSTER_ID = 4L
        const val CREATED_CLUSTER_ID = 19L
        const val SAMPLE_LAT = 48.1351
        const val SAMPLE_LNG = 11.5820
        const val SAMPLE_END_LAT = 48.1400
        const val SAMPLE_END_LNG = 11.5900
        const val SAMPLE_APEX_LAT = 48.1600
        const val SAMPLE_APEX_LNG = 11.6100
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        WorkoutClusterRepository.resetForTesting(null)

        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockApplication = mockk(relaxed = true)
        every { mockApplication.applicationContext } returns mockApplication
        every { mockApplication.getString(any()) } returns "Loading"

        mockkStatic(PreferenceManager::class)
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.getInt(any(), any()) } returns 10
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

        mockkObject(WorkoutRepository.Companion)
        mockWorkoutRepo = mockk(relaxed = true)
        every { WorkoutRepository.getInstance(any<Application>()) } returns mockWorkoutRepo
        every { WorkoutRepository.getInstance(any<Context>()) } returns mockWorkoutRepo
        coEvery { mockWorkoutRepo.reloadWorkoutData(any()) } returns Unit
        coEvery { mockWorkoutRepo.loadWorkout(any()) } returns Unit

        mockkObject(SegmentsRepository.Companion)
        every { SegmentsRepository.getInstance(any()) } returns mockk(relaxed = true)

        mockkObject(RoutesRepository.Companion)
        val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
        every { RoutesRepository.getInstance(any()) } returns mockRoutesRepo
        every { mockRoutesRepo.allRoutes } returns MutableStateFlow(emptyList())

        val mockPrefManager = mockk<MyPreferenceManager>(relaxed = true)
        every { mockPrefManager.enabledTrackTypesFlow } returns MutableStateFlow(emptySet())
        MyPreferenceManager.setInstanceForTesting(mockPrefManager)

        mockkObject(WorkoutClusterDatabaseManager.Companion)
        mockClusterDb = mockk(relaxed = true)
        every { WorkoutClusterDatabaseManager.getInstance(any()) } returns mockClusterDb

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb
        every { mockSummariesDb.getWorkoutClusterStatsForAllClusters() } returns emptyMap()

        mockkObject(WorkoutClusterEngine.Companion)
        mockClusterEngine = mockk(relaxed = true)
        every { WorkoutClusterEngine.getInstance(any()) } returns mockClusterEngine
    }

    @After
    fun tearDown() {
        WorkoutClusterRepository.resetForTesting(null)
        MyPreferenceManager.setInstanceForTesting(null)
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    private fun sampleWorkout(
        id: Long = WORKOUT_ID,
        clusterId: Long = OLD_CLUSTER_ID,
        withGps: Boolean = true
    ) = WorkoutData(
        id = id,
        finished = true,
        fileBaseName = "w_$id",
        workoutName = if (withGps) "Isar Ride" else "Indoor Trainer",
        sportId = 1L,
        sportName = "Cycling",
        bSportType = BSportType.BIKE,
        startTimeS = SAMPLE_TIME_S,
        formattedDate = "18.11.2026",
        formattedTime = "08:30",
        localDateTime = LocalDateTime.now(),
        equipmentName = null,
        equipmentId = 0L,
        commute = false,
        trainer = !withGps,
        mapPolyline = "",
        encodedAltitudes = "",
        encodedDistances = "",
        uploadToStrava = 0,
        totalDistance = SAMPLE_DISTANCE_M,
        maxDisplacement = null,
        activeTimeSec = SAMPLE_DURATION_S,
        totalTimeSec = SAMPLE_DURATION_S,
        avgSpeedMps = 5.0,
        ascentMeters = 100L,
        descentMeters = 100L,
        minAltitude = 500.0,
        maxAltitude = 550.0,
        startLatLng = if (withGps) LatLng(SAMPLE_LAT, SAMPLE_LNG) else null,
        endLatLng = if (withGps) LatLng(SAMPLE_END_LAT, SAMPLE_END_LNG) else null,
        maxDisplacementLatLng = if (withGps) LatLng(SAMPLE_APEX_LAT, SAMPLE_APEX_LNG) else null,
        description = null,
        goal = null,
        method = null,
        stravaSportName = null,
        clusterId = clusterId,
        clusterName = if (clusterId > 0) "Old Route" else null
    )

    private fun sampleCluster(id: Long, name: String, sport: BSportType = BSportType.BIKE) = WorkoutCluster(
        id = id,
        name = name,
        probableSportId = 1L,
        startLat = SAMPLE_LAT,
        startLng = SAMPLE_LNG,
        endLat = SAMPLE_END_LAT,
        endLng = SAMPLE_END_LNG,
        maxDispLat = SAMPLE_APEX_LAT,
        maxDispLng = SAMPLE_APEX_LNG,
        refDistance = SAMPLE_DISTANCE_M,
        hitCount = 3,
        bSportType = sport
    )

    @Test
    fun getCandidateClustersForWorkout_nonGpsWorkout_fallsBackToAllClustersWithNaN() {
        val runCluster = sampleCluster(1L, "Alpha Trail", BSportType.RUN)
        val bikeCluster = sampleCluster(2L, "Zwift Watopia", BSportType.BIKE)
        every { mockClusterDb.getAllClusters() } returns listOf(runCluster, bikeCluster)

        val repo = WorkoutClusterRepository.getInstance(
            mockApplication as Context,
            CoroutineScope(SupervisorJob() + testDispatcher)
        )
        val indoor = sampleWorkout(withGps = false)

        assertFalse(repo.hasSpatialFingerprint(indoor))
        val candidates = repo.getCandidateClustersForWorkout(indoor)

        assertEquals(2, candidates.size)
        assertEquals(bikeCluster.id, candidates[0].first.id)
        assertTrue(candidates[0].second.isNaN())
        assertEquals(runCluster.id, candidates[1].first.id)
        assertTrue(candidates[1].second.isNaN())
        verify(exactly = 0) {
            mockClusterEngine.getClusterScores(any(), any(), any(), any(), any(), any<BSportType>(), any(), any(), any())
        }
    }

    @Test
    fun moveOrAssignWorkoutToCluster_nonGpsWorkout_usesAssignClusterToWorkout() = runTest(testDispatcher) {
        every { mockClusterDb.getAllClusters() } returns listOf(sampleCluster(TARGET_CLUSTER_ID, "Zwift Watopia").copy(hitCount = 0))
        val repo = WorkoutClusterRepository.getInstance(
            mockApplication as Context,
            CoroutineScope(SupervisorJob() + testDispatcher)
        )
        every { mockClusterDb.getClusterNameById(TARGET_CLUSTER_ID) } returns "Zwift Watopia"
        val indoor = sampleWorkout(withGps = false)

        val resolvedName = repo.moveOrAssignWorkoutToCluster(indoor, TARGET_CLUSTER_ID)

        assertEquals("Zwift Watopia", resolvedName)
        verify(exactly = 1) {
            mockClusterEngine.assignClusterToWorkout(any(), WORKOUT_ID, TARGET_CLUSTER_ID, forceIdentity = true)
        }
        verify(exactly = 0) {
            mockClusterEngine.moveWorkoutToCluster(any(), any(), any(), any())
        }
        coVerify(exactly = 1) { mockWorkoutRepo.reloadWorkoutData(WORKOUT_ID) }
        coVerify(exactly = 1) { mockWorkoutRepo.loadWorkout(WORKOUT_ID) }
    }

    @Test
    fun moveOrAssignWorkoutToCluster_gpsWorkout_usesMoveWorkoutToCluster() = runTest(testDispatcher) {
        every { mockClusterDb.getAllClusters() } returns listOf(sampleCluster(TARGET_CLUSTER_ID, "Isar Valley").copy(hitCount = 0))
        val repo = WorkoutClusterRepository.getInstance(
            mockApplication as Context,
            CoroutineScope(SupervisorJob() + testDispatcher)
        )
        every { mockClusterDb.getClusterNameById(TARGET_CLUSTER_ID) } returns "Isar Valley"
        val outdoor = sampleWorkout(withGps = true)

        assertTrue(repo.hasSpatialFingerprint(outdoor))
        val resolvedName = repo.moveOrAssignWorkoutToCluster(outdoor, TARGET_CLUSTER_ID)

        assertEquals("Isar Valley", resolvedName)
        verify(exactly = 1) {
            mockClusterEngine.moveWorkoutToCluster(any(), WORKOUT_ID, OLD_CLUSTER_ID, TARGET_CLUSTER_ID)
        }
        verify(exactly = 0) {
            mockClusterEngine.assignClusterToWorkout(any(), any(), any(), any(), any())
        }
        coVerify(exactly = 1) { mockWorkoutRepo.reloadWorkoutData(WORKOUT_ID) }
    }

    @Test
    fun trackOnMapAftermathViewModel_clusterActionsDelegateToRepository() = runTest(testDispatcher) {
        val mockClusterRepo = mockk<WorkoutClusterRepository>(relaxed = true)
        WorkoutClusterRepository.resetForTesting(mockClusterRepo)

        val indoor = sampleWorkout(withGps = false)
        val outdoor = sampleWorkout(withGps = true)
        val expected = listOf(sampleCluster(TARGET_CLUSTER_ID, "Zwift") to Double.NaN)
        every { mockClusterRepo.getCandidateClustersForWorkout(indoor) } returns expected
        coEvery { mockClusterRepo.moveOrAssignWorkoutToCluster(indoor, TARGET_CLUSTER_ID) } returns "Zwift"
        coEvery { mockClusterRepo.createNewClusterFromWorkout(outdoor) } returns (CREATED_CLUSTER_ID to "New Route")
        coEvery { mockClusterRepo.unassignWorkoutFromCluster(WORKOUT_ID) } returns Unit

        val viewModel = TrackOnMapAftermathViewModel(mockApplication, ioDispatcher = testDispatcher)
        viewModel.loadCandidateClustersForWorkout(indoor)
        advanceUntilIdle()
        assertEquals(expected, viewModel.clusterCandidates.value)

        viewModel.moveOrAssignWorkoutToCluster(indoor, TARGET_CLUSTER_ID)
        viewModel.createNewClusterFromWorkout(outdoor)
        viewModel.unassignWorkoutFromCluster(WORKOUT_ID)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockClusterRepo.moveOrAssignWorkoutToCluster(indoor, TARGET_CLUSTER_ID) }
        coVerify(exactly = 1) { mockClusterRepo.createNewClusterFromWorkout(outdoor) }
        coVerify(exactly = 1) { mockClusterRepo.unassignWorkoutFromCluster(WORKOUT_ID) }
    }
}
