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

package com.atrainingtracker.trainingtracker.ui.clusters

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.map.LocationMarker
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Automated unit test suite verifying [WorkoutClustersViewModel] cluster selection,
 * workout list state propagation, and peek selection resolution (REQ-UI-138, TST-UI-091, ATT-818).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutClustersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)

    private val mockClusterRepo = mockk<WorkoutClusterRepository>(relaxed = true)
    private val mockClusterEngine = mockk<WorkoutClusterEngine>(relaxed = true)
    private val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
    private val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)

    private val allClustersFlow = MutableStateFlow<List<WorkoutCluster>>(emptyList())
    private val currentLocationFlow = MutableStateFlow<LatLng?>(null)

    private fun createWorkout(id: Long, clusterId: Long): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = "Cluster Run $id",
            sportId = 2L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1700000000L + id * 3600L,
            formattedDate = "2026-09-10",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = com.google.maps.android.PolyUtil.encode(listOf(LatLng(48.0, 11.5), LatLng(48.01, 11.51))),
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 10000.0,
            maxDisplacement = 4000.0,
            activeTimeSec = 3000L,
            totalTimeSec = 3100L,
            avgSpeedMps = 3.33,
            ascentMeters = 80L,
            descentMeters = 80L,
            minAltitude = 300.0,
            maxAltitude = 380.0,
            startLatLng = LatLng(48.0, 11.5),
            endLatLng = LatLng(48.0, 11.5),
            maxDisplacementLatLng = LatLng(48.03, 11.54),
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            clusterId = clusterId,
            clusterName = "Test Cluster"
        )
    }

    private fun createCluster(id: Long, name: String): WorkoutCluster {
        return WorkoutCluster(
            id = id,
            name = name,
            probableSportId = 2L,
            startLat = 48.0,
            startLng = 11.5,
            endLat = 48.0,
            endLng = 11.5,
            maxDispLat = 48.03,
            maxDispLng = 11.54,
            refDistance = 10000.0,
            hitCount = 2,
            bSportType = BSportType.RUN
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getClusterTolEndpoints() } returns 200f
        every { TrainingApplication.getClusterTolApex() } returns 400f
        every { TrainingApplication.getClusterTolDistance() } returns 0.20f
        every { TrainingApplication.getClusterTolAltitudePos() } returns 400f
        every { TrainingApplication.useSportTypeForClustering() } returns true
        every { TrainingApplication.useAltitudePosForClustering() } returns false

        mockkObject(WorkoutClusterRepository.Companion)
        every { WorkoutClusterRepository.getInstance(any()) } returns mockClusterRepo

        mockkObject(WorkoutClusterEngine.Companion)
        every { WorkoutClusterEngine.getInstance(any()) } returns mockClusterEngine
        every { mockClusterEngine.findApexFromPoints(any(), any()) } returns LatLng(48.03, 11.54)
        every { mockClusterEngine.distanceBetween(any(), any()) } returns 0.0f

        mockkObject(RoutesRepository.Companion)
        every { RoutesRepository.getInstance(any()) } returns mockRoutesRepo

        mockkObject(BANALServiceRepository.Companion)
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo

        mockkObject(EquipmentAndSportTypeDiscoveryManager.Companion)
        every { EquipmentAndSportTypeDiscoveryManager.getInstance(any()) } returns mockDiscovery

        mockkObject(WorkoutRepository.Companion)
        every { WorkoutRepository.getInstance(any()) } returns mockWorkoutRepo

        mockkConstructor(MyPreferenceManager::class)
        every { anyConstructed<MyPreferenceManager>().enabledClusterMarkerTypesFlow } returns MutableStateFlow(emptySet())
        every { anyConstructed<MyPreferenceManager>().clusterFilterCriteriaFlow } returns MutableStateFlow(ClusterFilterCriteria())

        every { mockClusterRepo.allClusters } returns allClustersFlow
        every { mockBanalRepo.currentLocation } returns currentLocationFlow
        every { mockClusterRepo.migrationStatus } returns MutableStateFlow(null)
        every { mockClusterRepo.clusterStats } returns MutableStateFlow(emptyMap())
        coEvery { mockClusterRepo.getUnclusteredWorkouts() } returns emptyList()
        coEvery { mockClusterRepo.refreshClusters() } just Runs
        coEvery { mockClusterRepo.refreshClusterStats() } just Runs
    }

    private var activeViewModel: WorkoutClustersViewModel? = null

    @After
    fun tearDown() {
        activeViewModel?.viewModelScope?.cancel()
        activeViewModel = null
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun selectCluster_whenClusterSelected_updatesClusterWorkoutsState() = runTest(testDispatcher) {
        val cluster = createCluster(10L, "Isar Trail")
        val workouts = listOf(createWorkout(101L, 10L), createWorkout(102L, 10L))

        coEvery { mockClusterRepo.getWorkoutsForCluster(10L) } returns workouts
        coEvery { mockRoutesRepo.getRouteByClusterId(10L) } returns null

        val viewModel = WorkoutClustersViewModel(mockApplication)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.selectCluster(cluster)
        viewModel.mapState.first { !it.isLoading && it.tracks.isNotEmpty() }
        advanceUntilIdle()

        assertEquals(2, viewModel.clusterWorkouts.value.size)
        assertEquals(101L, viewModel.clusterWorkouts.value[0].id)
        assertEquals(102L, viewModel.clusterWorkouts.value[1].id)
    }

    @Test
    fun selectWorkoutForPeek_afterSelectCluster_resolvesWorkoutAndLoadsTrackAndMarkers() = runTest(testDispatcher) {
        val cluster = createCluster(10L, "Isar Trail")
        val workout = createWorkout(101L, 10L)

        coEvery { mockClusterRepo.getWorkoutsForCluster(10L) } returns listOf(workout)
        coEvery { mockRoutesRepo.getRouteByClusterId(10L) } returns null

        val samplePoints = listOf(
            PathPoint(altitude = 310.0, latLng = LatLng(48.0, 11.5), distance = 0.0),
            PathPoint(altitude = 325.0, latLng = LatLng(48.01, 11.51), distance = 1000.0)
        )
        val sampleMarkers = listOf(
            LocationMarker(position = LatLng(48.0, 11.5), iconResId = 0, title = "Start")
        )

        coEvery { mockClusterRepo.getWorkoutTrackPoints(101L, TrackType.BEST) } returns samplePoints
        coEvery { mockWorkoutRepo.getWorkoutMarkers(workout) } returns sampleMarkers

        val viewModel = WorkoutClustersViewModel(mockApplication)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.selectCluster(cluster)
        viewModel.mapState.first { !it.isLoading && it.tracks.isNotEmpty() }
        advanceUntilIdle()

        viewModel.selectWorkoutForPeek(101L)
        advanceUntilIdle()

        val peeked = viewModel.peekedWorkoutDataWithTrack.value
        assertNotNull("Peeked workout data should not be null", peeked)
        assertEquals(101L, peeked!!.workoutData?.id)
        assertEquals(2, peeked.trackPoints.size)
        assertEquals(310.0, peeked.trackPoints[0].altitude, 0.01)
        assertEquals(1, peeked.markers.size)
    }

    @Test
    fun selectCluster_whenNull_clearsClusterWorkoutsAndPeekSelection() = runTest(testDispatcher) {
        val cluster = createCluster(10L, "Isar Trail")
        val workout = createWorkout(101L, 10L)

        coEvery { mockClusterRepo.getWorkoutsForCluster(10L) } returns listOf(workout)
        coEvery { mockRoutesRepo.getRouteByClusterId(10L) } returns null

        val viewModel = WorkoutClustersViewModel(mockApplication)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.selectCluster(cluster)
        viewModel.mapState.first { !it.isLoading && it.tracks.isNotEmpty() }
        advanceUntilIdle()
        assertEquals(1, viewModel.clusterWorkouts.value.size)

        viewModel.selectCluster(null)
        viewModel.mapState.first { it.tracks.isEmpty() }
        advanceUntilIdle()

        assertTrue(viewModel.clusterWorkouts.value.isEmpty())
        assertNull(viewModel.peekedWorkoutDataWithTrack.value)
    }

    @Test
    fun selectClusterById_whenClusterInList_selectsDirectly() = runTest(testDispatcher) {
        val cluster = createCluster(42L, "Morning Ride")
        allClustersFlow.value = listOf(cluster)

        coEvery { mockClusterRepo.getWorkoutsForCluster(42L) } returns emptyList()
        coEvery { mockRoutesRepo.getRouteByClusterId(42L) } returns null

        val viewModel = WorkoutClustersViewModel(mockApplication)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.selectClusterById(42L)
        advanceUntilIdle()

        assertEquals(42L, viewModel.selectedCluster.value?.id)
        assertEquals("Morning Ride", viewModel.selectedCluster.value?.name)
    }

    @Test
    fun selectClusterById_whenClusterNotInList_queriesRepository() = runTest(testDispatcher) {
        val cluster = createCluster(99L, "Evening Commute")
        allClustersFlow.value = emptyList()

        coEvery { mockClusterRepo.getClusterById(99L) } returns cluster
        coEvery { mockClusterRepo.getWorkoutsForCluster(99L) } returns emptyList()
        coEvery { mockRoutesRepo.getRouteByClusterId(99L) } returns null

        val viewModel = WorkoutClustersViewModel(mockApplication)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.selectClusterById(99L)
        advanceUntilIdle()

        assertEquals(99L, viewModel.selectedCluster.value?.id)
        assertEquals("Evening Commute", viewModel.selectedCluster.value?.name)
    }
}

