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
import com.atrainingtracker.trainingtracker.database.ClusterMutationEvent
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.toClusterWorkoutSnapshot
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
// Linked test specs: TST-SET-002, TST-SET-014, TST-SET-023
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
    private val mockKnownLocationsRepo = mockk<KnownLocationsRepository>(relaxed = true)

    private val allClustersFlow = MutableStateFlow<List<WorkoutCluster>>(emptyList())
    private val clusterMutationEventsFlow = MutableSharedFlow<ClusterMutationEvent>(extraBufferCapacity = 16)
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
        every { mockWorkoutRepo.unassignClusterFromWorkout(any()) } returns kotlinx.coroutines.CompletableDeferred(Unit)

        mockkObject(KnownLocationsRepository.Companion)
        every { KnownLocationsRepository.getInstance(any()) } returns mockKnownLocationsRepo
        every { mockKnownLocationsRepo.locationsFlow } returns MutableStateFlow(emptyList())

        val mockPrefManager = mockk<MyPreferenceManager>(relaxed = true)
        every { mockPrefManager.enabledClusterMarkerTypesFlow } returns MutableStateFlow(emptySet())
        every { mockPrefManager.clusterFilterCriteriaFlow } returns MutableStateFlow(ClusterFilterCriteria())
        MyPreferenceManager.setInstanceForTesting(mockPrefManager)

        val mockTuningDataStore = mockk<com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore>(relaxed = true)
        com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore.setInstanceForTesting(mockTuningDataStore)

        every { mockClusterRepo.allClusters } returns allClustersFlow
        every { mockClusterRepo.clusterMutationEvents } returns clusterMutationEventsFlow
        every { mockBanalRepo.currentLocation } returns currentLocationFlow
        every { mockClusterRepo.migrationStatus } returns MutableStateFlow(null)
        every { mockClusterRepo.clusterStats } returns MutableStateFlow(emptyMap())
        coEvery { mockClusterRepo.getUnclusteredWorkouts() } returns emptyList()
        coEvery { mockClusterRepo.refreshClusters() } just Runs
        coEvery { mockClusterRepo.refreshClusterStats() } just Runs
        coEvery { mockClusterRepo.getWorkoutTrackPoints(any(), any()) } returns emptyList()
        coEvery { mockWorkoutRepo.getWorkoutMarkers(any()) } returns emptyList()
    }

    private var activeViewModel: WorkoutClustersViewModel? = null

    @After
    fun tearDown() {
        activeViewModel?.viewModelScope?.cancel()
        activeViewModel = null
        com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore.setInstanceForTesting(null)
        MyPreferenceManager.setInstanceForTesting(null)
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun selectCluster_whenClusterSelected_updatesClusterWorkoutsState() = runTest(testDispatcher) {
        // TST-SET-002, TST-SET-014
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
        assertEquals(2, viewModel.mapState.value.heatmapPaths.size)
        assertTrue(viewModel.mapState.value.memberMarkers.any { it.markerType == ClusterMarkerType.START })
        assertTrue(viewModel.mapState.value.memberMarkers.any { it.markerType == ClusterMarkerType.END })
        assertTrue(viewModel.mapState.value.memberMarkers.any { it.markerType == ClusterMarkerType.DISTANCE })
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
        assertEquals(310.0, peeked.trackPoints[0].altitude!!, 0.01)
        assertEquals(1, peeked.markers.size)
    }

    @Test
    fun selectWorkoutForPeek_cancelsPreviousInFlightJob() = runTest(testDispatcher) {
        val cluster = createCluster(10L, "Isar Trail")
        val workout1 = createWorkout(101L, 10L)
        val workout2 = createWorkout(102L, 10L)

        coEvery { mockClusterRepo.getWorkoutsForCluster(10L) } returns listOf(workout1, workout2)
        coEvery { mockRoutesRepo.getRouteByClusterId(10L) } returns null
        coEvery { mockClusterRepo.getWorkoutTrackPoints(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
            emptyList()
        }
        coEvery { mockWorkoutRepo.getWorkoutMarkers(any()) } returns emptyList()

        val viewModel = WorkoutClustersViewModel(mockApplication)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.selectCluster(cluster)
        viewModel.mapState.first { !it.isLoading && it.tracks.isNotEmpty() }
        advanceUntilIdle()

        viewModel.selectWorkoutForPeek(101L)
        val firstJob = viewModel.currentPeekJob
        assertNotNull(firstJob)
        assertTrue(firstJob!!.isActive)

        viewModel.selectWorkoutForPeek(102L)
        assertTrue("Previous peek job must be cancelled", firstJob.isCancelled)
        val secondJob = viewModel.currentPeekJob
        assertNotNull(secondJob)
        assertTrue(secondJob!!.isActive)
        advanceUntilIdle()
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

    @Test
    fun testLinkedEquipmentBySport_precomputesEquipmentMappingWithoutMainThreadDb() = runTest(testDispatcher) {
        val cluster1 = createCluster(101L, "Isar Trail").copy(probableSportId = 2L)
        val cluster2 = createCluster(102L, "Forstenrieder").copy(probableSportId = 3L)
        allClustersFlow.value = listOf(cluster1, cluster2)

        every { mockClusterRepo.getSportName(2L) } returns "Running"
        every { mockClusterRepo.getSportName(3L) } returns "Cycling"
        every { mockDiscovery.getEquipmentNamesForSport("Running") } returns setOf("Running Shoes A", "Running Shoes B")
        every { mockDiscovery.getEquipmentNamesForSport("Cycling") } returns setOf("Road Bike")

        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        val map = viewModel.linkedEquipmentBySport.value
        assertEquals(2, map.size)
        assertEquals(setOf("Running Shoes A", "Running Shoes B"), map[2L])
        assertEquals(setOf("Road Bike"), map[3L])

        // Cached accessor returns precomputed set
        assertEquals(setOf("Road Bike"), viewModel.getLinkedEquipmentSetCached(3L))
    }

    @Test
    fun filterCriteria_defaultsToMinHitCount5OnInitialization() = runTest(testDispatcher) {
        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        // REQ-UI-467.1 / ATT-5822: WorkoutClustersViewModel defaults to minHitCount = 5 on screen entry
        assertEquals(5, viewModel.filterCriteria.value.minHitCount)
        assertTrue(viewModel.filterCriteria.value.isNotEmpty)
    }

    @Test
    fun clearFilterCriteria_resetsCriteriaToEmptyWithNullMinHitCount() = runTest(testDispatcher) {
        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        assertEquals(5, viewModel.filterCriteria.value.minHitCount)

        viewModel.clearFilterCriteria()
        advanceUntilIdle()

        assertNull(viewModel.filterCriteria.value.minHitCount)
        assertTrue(viewModel.filterCriteria.value.isEmpty)
    }

    @Test
    fun updateFilterCriteria_preservesOrModifiesCriteriaState() = runTest(testDispatcher) {
        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        assertEquals(5, viewModel.filterCriteria.value.minHitCount)

        viewModel.updateFilterCriteria { it.copy(query = "Trail") }
        advanceUntilIdle()

        assertEquals("Trail", viewModel.filterCriteria.value.query)
        assertEquals(5, viewModel.filterCriteria.value.minHitCount)

        viewModel.updateFilterCriteria { it.copy(minHitCount = null) }
        advanceUntilIdle()

        assertEquals("Trail", viewModel.filterCriteria.value.query)
        assertNull(viewModel.filterCriteria.value.minHitCount)
    }

    @Test
    fun testWorkoutClustersViewModel_unassignWorkout_updatesRepository() = runTest(testDispatcher) {
        val cluster = createCluster(10L, "Isar Trail")
        val workout = createWorkout(101L, 10L)
        val unclusteredWorkout = workout.copy(clusterId = -1L, clusterName = null)

        coEvery { mockClusterRepo.getWorkoutsForCluster(10L) } returns listOf(workout) andThen emptyList()
        coEvery { mockClusterRepo.getUnclusteredWorkouts() } returns listOf(unclusteredWorkout)

        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.selectCluster(cluster)
        advanceUntilIdle()
        assertEquals(1, viewModel.clusterWorkouts.value.size)

        viewModel.unassignWorkout(workout)
        advanceUntilIdle()

        verify { mockWorkoutRepo.unassignClusterFromWorkout(101L) }
        coVerify { mockClusterRepo.refreshClusters() }
        coVerify { mockWorkoutRepo.reloadWorkoutData(101L) }
        assertEquals(0, viewModel.clusterWorkouts.value.size)
        assertEquals(1, viewModel.unclusteredWorkouts.value.size)
        assertEquals(101L, viewModel.unclusteredWorkouts.value[0].id)
    }

    @Test
    fun testWorkoutClustersViewModel_createNewClusterFromWorkout_delegatesToEngineAndRefreshes() = runTest(testDispatcher) {
        val workout = createWorkout(101L, -1L)
        val snapshot = workout.toClusterWorkoutSnapshot()
        every { mockClusterEngine.createNewClusterFromWorkout(any(), snapshot) } returns 15L
        coEvery { mockClusterRepo.getUnclusteredWorkouts() } returns emptyList()

        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.createNewClusterFromWorkout(workout)
        advanceUntilIdle()

        verify { mockClusterEngine.createNewClusterFromWorkout(mockApplication, snapshot) }
        coVerify { mockClusterRepo.refreshClusters() }
        coVerify { mockWorkoutRepo.reloadWorkoutData(101L) }
    }

    /**
     * TST-PER-048.3: ClusterMutationEvents reconcile clusterWorkouts, peekedWorkoutDataWithTrack,
     * and selectedCluster immediately across rename, merge, and delete (REQ-PER-040.1).
     */
    @Test
    fun testClusterMutationEvents_renamedMergedDeleted_reconcilesViewModelState_TST_PER_048_3() =
        runTest(testDispatcher) {
            val cluster10 = createCluster(10L, "Isar Trail")
            val cluster20 = createCluster(20L, "Target Loop")
            val workout = createWorkout(101L, 10L).copy(clusterName = "Isar Trail")
            allClustersFlow.value = listOf(cluster10, cluster20)
            coEvery { mockClusterRepo.getWorkoutsForCluster(10L) } returns listOf(workout)
            coEvery { mockClusterRepo.getWorkoutsForCluster(20L) } returns listOf(
                workout.copy(clusterId = 20L, clusterName = "Target Loop")
            )
            coEvery { mockClusterRepo.getUnclusteredWorkouts() } returns listOf(
                workout.copy(clusterId = -1L, clusterName = null)
            )

        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.selectCluster(cluster10)
        advanceUntilIdle()
        viewModel.selectWorkoutForPeek(101L)
        advanceUntilIdle()

        assertEquals("Isar Trail", viewModel.clusterWorkouts.value.first().clusterName)
        assertEquals("Isar Trail", viewModel.peekedWorkoutDataWithTrack.value?.workoutData?.clusterName)

        // 1. Rename event
        clusterMutationEventsFlow.emit(ClusterMutationEvent.Renamed(10L, "Favorite Isar"))
        advanceUntilIdle()
        assertEquals("Favorite Isar", viewModel.clusterWorkouts.value.first().clusterName)
        assertEquals("Favorite Isar", viewModel.peekedWorkoutDataWithTrack.value?.workoutData?.clusterName)
        assertEquals("Favorite Isar", viewModel.selectedCluster.value?.name)

        // 2. Merge event (10 -> 20)
        clusterMutationEventsFlow.emit(ClusterMutationEvent.Merged(10L, 20L, "Target Loop"))
        advanceUntilIdle()
        assertEquals(20L, viewModel.selectedCluster.value?.id)
        assertEquals(20L, viewModel.clusterWorkouts.value.first().clusterId)
        assertEquals("Target Loop", viewModel.clusterWorkouts.value.first().clusterName)
        assertEquals(20L, viewModel.peekedWorkoutDataWithTrack.value?.workoutData?.clusterId)
        assertEquals("Target Loop", viewModel.peekedWorkoutDataWithTrack.value?.workoutData?.clusterName)

        // 3. Delete event (20)
        clusterMutationEventsFlow.emit(ClusterMutationEvent.Deleted(20L))
        advanceUntilIdle()
        assertNull(viewModel.selectedCluster.value)
        assertTrue(viewModel.clusterWorkouts.value.isEmpty())
        assertEquals(-1L, viewModel.peekedWorkoutDataWithTrack.value?.workoutData?.clusterId)
        assertNull(viewModel.peekedWorkoutDataWithTrack.value?.workoutData?.clusterName)
        assertEquals(1, viewModel.unclusteredWorkouts.value.size)
    }

    /**
     * TST-PER-048.3: executeClusterSplit delegates through WorkoutClusterRepository.splitCluster and
     * ClusterMutationEvent.Split removes split workouts from clusterWorkouts & reconciles peek (REQ-PER-040.1).
     */
    @Test
    fun testExecuteClusterSplit_delegatesToRepositoryAndReconcilesSplitEvent_TST_PER_048_3() = runTest(testDispatcher) {
        val cluster = createCluster(10L, "Main Trail")
        val remainingWorkout = createWorkout(101L, 10L).copy(clusterName = "Main Trail")
        val splitWorkout = createWorkout(102L, 10L).copy(clusterName = "Main Trail")
        val reloadedSplitWorkout = splitWorkout.copy(clusterId = 30L, clusterName = "Variant B")
        allClustersFlow.value = listOf(cluster)
        coEvery { mockClusterRepo.getWorkoutsForCluster(10L) } returns
            listOf(remainingWorkout, splitWorkout) andThen listOf(remainingWorkout)
        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(listOf(remainingWorkout, reloadedSplitWorkout))
        coEvery { mockClusterRepo.splitCluster(10L, any(), true) } coAnswers {
            clusterMutationEventsFlow.emit(ClusterMutationEvent.Split(10L, listOf(30L), listOf(102L)))
            listOf(30L)
        }

        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.selectCluster(cluster)
        advanceUntilIdle()
        viewModel.selectWorkoutForPeek(102L)
        advanceUntilIdle()

        viewModel.executeClusterSplit(listOf("Variant B" to listOf(splitWorkout)), renameWorkouts = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockClusterRepo.splitCluster(10L, any(), true) }
        assertEquals(listOf(101L), viewModel.clusterWorkouts.value.map { it.id })
        assertEquals(30L, viewModel.peekedWorkoutDataWithTrack.value?.workoutData?.clusterId)
        assertEquals("Variant B", viewModel.peekedWorkoutDataWithTrack.value?.workoutData?.clusterName)
    }

    /**
     * TST-PER-049.1: WorkoutClustersViewModel trims cluster names and rejects blank-only names
     * on rename, manual cluster creation, and cluster split (REQ-PER-041.1).
     */
    @Test
    fun testClusterMutations_trimWhitespaceAndRejectBlank_TST_PER_049_1() = runTest(testDispatcher) {
        val cluster = createCluster(10L, "Main Trail")
        val splitWorkout = createWorkout(102L, 10L)
        allClustersFlow.value = listOf(cluster)
        coEvery { mockClusterRepo.getWorkoutsForCluster(10L) } returns listOf(splitWorkout)

        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.selectCluster(cluster)
        advanceUntilIdle()

        // Blank rename is ignored; padded rename is trimmed
        viewModel.updateClusterIdentity(cluster, "   ", 1L, true)
        advanceUntilIdle()
        coVerify(exactly = 0) { mockClusterRepo.updateCluster(any()) }

        viewModel.updateClusterIdentity(cluster, "  Trimmed Trail  ", 1L, true)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            mockClusterRepo.updateCluster(match { it.id == 10L && it.name == "Trimmed Trail" })
        }
        assertEquals("Trimmed Trail", viewModel.selectedCluster.value?.name)

        // Blank manual cluster is ignored; padded manual cluster is trimmed
        val pos = LatLng(48.0, 11.0)
        viewModel.addManualCluster(" \t ", 1L, pos, pos, pos, 5000.0)
        advanceUntilIdle()
        verify(exactly = 0) {
            mockClusterEngine.manuallyCreateCluster(any(), any(), any(), any(), any(), any())
        }

        viewModel.addManualCluster("  Manual Trail  ", 1L, pos, pos, pos, 5000.0)
        advanceUntilIdle()
        verify(exactly = 1) {
            mockClusterEngine.manuallyCreateCluster("Manual Trail", 1L, pos, pos, pos, 5000.0)
        }

        // Split filters out blank names and trims valid variant names
        viewModel.executeClusterSplit(
            listOf("   " to listOf(splitWorkout), "  Sub Trail  " to listOf(splitWorkout)),
            renameWorkouts = true
        )
        advanceUntilIdle()
        coVerify(exactly = 1) {
            mockClusterRepo.splitCluster(
                primaryClusterId = 10L,
                variantsToSplit = listOf("Sub Trail" to listOf(splitWorkout)),
                renameWorkouts = true
            )
        }
    }

    /**
     * TST-PER-051.2: Constructing WorkoutClustersViewModel under UnconfinedTestDispatcher when
     * clusterMutationEvents emits synchronously on subscription does not throw NullPointerException (REQ-PER-043.2).
     */
    @Test
    fun testEagerMainDispatcherInit_withSynchronousMutationEmission_doesNotThrowNpe_TST_PER_051_2() {
        val eagerDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(eagerDispatcher)

        val eagerMutationFlow = MutableStateFlow<ClusterMutationEvent>(
            ClusterMutationEvent.Renamed(clusterId = 10L, newName = "Eager Trail")
        )
        every { mockClusterRepo.clusterMutationEvents } returns eagerMutationFlow

        val viewModel = WorkoutClustersViewModel(mockApplication, eagerDispatcher)
        activeViewModel = viewModel

        assertNull(viewModel.peekedWorkoutDataWithTrack.value)
    }

    /**
     * TST-PERF-002 / TST-BUG-001 (REQ-PER-001, REQ-PER-002): Selecting a cluster with >30 workouts
     * transitions isLoading true -> false, populates tracks/heatmapPaths/memberMarkers, and
     * cancels superseded selections (Cluster A -> Cluster B race condition).
     */
    @Test
    fun selectCluster_withOver30Workouts_loadsDetailMapAndCancelsSupersededSelection_TST_PERF_002() = runTest(testDispatcher) {
        val clusterA = createCluster(10L, "Long Loop")
        val clusterB = createCluster(20L, "Target Loop")
        val largeWorkoutSet = (1L..35L).map { createWorkout(it, 20L) }
        val gateA = kotlinx.coroutines.CompletableDeferred<List<WorkoutData>>()
        val gateB = kotlinx.coroutines.CompletableDeferred<List<WorkoutData>>()

        coEvery { mockClusterRepo.getWorkoutsForCluster(10L) } coAnswers { gateA.await() }
        coEvery { mockClusterRepo.getWorkoutsForCluster(20L) } coAnswers { gateB.await() }

        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        // Start slow selection for A, then immediately supersede with B (>30 workouts)
        viewModel.selectCluster(clusterA)
        testScheduler.runCurrent()
        assertTrue("isLoading must become true on initial selection", viewModel.mapState.value.isLoading)

        viewModel.selectCluster(clusterB)
        testScheduler.runCurrent()
        assertTrue("isLoading must remain true while B is loading", viewModel.mapState.value.isLoading)

        // Complete B (A's selectionJob was cancelled)
        gateB.complete(largeWorkoutSet)
        gateA.cancel()
        advanceUntilIdle()
        // Wait for withContext(Dispatchers.Default) polyline/marker computation to settle on Main
        val deadlineMs = System.currentTimeMillis() + 5_000L
        while (viewModel.mapState.value.isLoading && System.currentTimeMillis() < deadlineMs) {
            Thread.sleep(10)
            advanceUntilIdle()
        }

        assertEquals(clusterB, viewModel.selectedCluster.value)
        assertFalse("isLoading must clear after background decoding completes", viewModel.mapState.value.isLoading)
        assertEquals(35, viewModel.clusterWorkouts.value.size)
        assertEquals(35, viewModel.mapState.value.tracks.size)
        assertEquals(35, viewModel.mapState.value.heatmapPaths.size)
        assertTrue("Precomputed memberMarkers must populate for cluster workouts", viewModel.mapState.value.memberMarkers.isNotEmpty())
    }

    /**
     * TST-PERF-003 (REQ-PER-003) / TST-PERF-007 (REQ-PER-006):
     * Propagates multi-phase repository migrationStatus to UI state, toggles isRecalculating,
     * and emits recalculationFinished when complete.
     */
    @Test
    fun recalculateClusters_propagatesMigrationStatusAndCompletionEvent_TST_PERF_003_007() = runTest(testDispatcher) {
        val statusFlow = MutableStateFlow<com.atrainingtracker.trainingtracker.ui.util.MigrationStatus?>(null)
        every { mockClusterRepo.migrationStatus } returns statusFlow
        val completionEvents = mutableListOf<Unit>()

        coEvery { mockClusterRepo.recalculateClustersWithProgress() } coAnswers {
            statusFlow.value = com.atrainingtracker.trainingtracker.ui.util.MigrationStatus(
                title = "Analyzing Locations",
                phases = listOf(com.atrainingtracker.trainingtracker.ui.util.ProgressPhase(1, "Phase 1", 0.5f))
            )
            statusFlow.value = com.atrainingtracker.trainingtracker.ui.util.MigrationStatus(
                title = "Analyzing Locations",
                phases = listOf(
                    com.atrainingtracker.trainingtracker.ui.util.ProgressPhase(1, "Phase 1", 1.0f),
                    com.atrainingtracker.trainingtracker.ui.util.ProgressPhase(2, "Phase 2", 0.7f)
                )
            )
            statusFlow.value = null
        }

        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.recalculationFinished.collect { completionEvents.add(it) }
        }

        val observedStatuses = mutableListOf<com.atrainingtracker.trainingtracker.ui.util.MigrationStatus?>()
        val statusJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.migrationStatus.collect { observedStatuses.add(it) }
        }

        viewModel.recalculateClusters()
        advanceUntilIdle()

        assertFalse("isRecalculating must reset to false upon finish", viewModel.isRecalculating.value)
        assertEquals(1, completionEvents.size)
        assertTrue(observedStatuses.filterNotNull().any { it.phases.size == 2 })
        assertNull(viewModel.migrationStatus.value)

        job.cancel()
        statusJob.cancel()
    }

    /**
     * TST-SET-023 (REQ-SET-032): Reference Route in Heatmap.
     * Verifies selectCluster hydrates linkedRoute when the cluster has a linked explicit route.
     */
    @Test
    fun selectCluster_withLinkedRoute_populatesLinkedRouteStateFlow_TST_SET_023() = runTest(testDispatcher) {
        // TST-SET-023
        val cluster = createCluster(10L, "Linked Reference Loop")
        val expectedRoute = com.atrainingtracker.trainingtracker.database.RouteWithPath(
            summary = com.atrainingtracker.trainingtracker.database.RouteSummary(
                id = 77L,
                externalId = "gpx_77",
                name = "Linked Reference Loop",
                description = "GPX ref",
                isSelected = false,
                distance = 10000.0,
                elevationGain = 80.0,
                bSportType = BSportType.RUN,
                source = com.atrainingtracker.trainingtracker.database.RouteSource.LOCAL_GPX,
                clusterId = 10L
            ),
            path = listOf(
                PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.5)),
                PathPoint(distance = 1400.0, latLng = LatLng(48.01, 11.51))
            ),
            waypoints = emptyList()
        )

        coEvery { mockClusterRepo.getWorkoutsForCluster(10L) } returns emptyList()
        coEvery { mockRoutesRepo.getRouteByClusterId(10L) } returns expectedRoute

        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        advanceUntilIdle()

        viewModel.selectCluster(cluster)
        advanceUntilIdle()

        assertEquals(expectedRoute, viewModel.linkedRoute.value)
        assertEquals(77L, viewModel.linkedRoute.value?.summary?.id)
    }

    @Test
    fun inheritsBaseMappableListViewModel_managesSortOrderScrollAndLocation_TST_PRO_137_2() = runTest(testDispatcher) {
        val viewModel = WorkoutClustersViewModel(mockApplication, testDispatcher)
        activeViewModel = viewModel
        assertTrue(
            com.atrainingtracker.trainingtracker.ui.util.BaseMappableListViewModel::class.java
                .isAssignableFrom(WorkoutClustersViewModel::class.java)
        )

        assertEquals(ClusterSortOrder.RECORDINGS, viewModel.sortOrder.value)
        assertTrue(viewModel.shouldScrollToTop(ClusterSortOrder.RECORDINGS))
        assertFalse(viewModel.shouldScrollToTop(ClusterSortOrder.RECORDINGS))

        viewModel.setSortOrder(ClusterSortOrder.DISTANCE_TO_USER)
        assertEquals(ClusterSortOrder.DISTANCE_TO_USER, viewModel.sortOrder.value)
        assertTrue(viewModel.shouldScrollToTop(ClusterSortOrder.DISTANCE_TO_USER))

        val locJob = launch { viewModel.isLocationAvailable.collect() }
        currentLocationFlow.value = LatLng(48.137, 11.576)
        advanceUntilIdle()
        assertTrue(viewModel.isLocationAvailable.value)
        assertTrue(viewModel.shouldScrollToTop(ClusterSortOrder.DISTANCE_TO_USER))
        locJob.cancel()
    }
}

