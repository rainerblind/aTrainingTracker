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
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRetrySyncCoordinator
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.repositories.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying workout classification tag handling in [EditWorkoutViewModel]
 * pursuant to REQ-REV-013.2 and TST-REV-014.1 (ATT-7517).
 *
 * Verifies mutual exclusivity between Commute and Trainer (Indoor),
 * while keeping Race independently combinable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
// Linked test specs: TST-UNT-007
class EditWorkoutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockApplication: Application
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockEquipmentRepository: EquipmentRepository
    private lateinit var mockSportTypesRepository: SportTypesRepository
    private lateinit var mockSportTypeDatabaseManager: SportTypeDatabaseManager
    private lateinit var mockDiscoveryManager: EquipmentAndSportTypeDiscoveryManager
    private lateinit var mockClusterEngine: WorkoutClusterEngine

    private val initialWorkoutLoadedLiveEvent = MutableLiveData<WorkoutData>()

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
        every { TrainingApplication.syncToHealthConnect() } returns false

        mockApplication = mockk(relaxed = true)
        every { mockApplication.getString(R.string.all_sports) } returns "+ all sports +"
        every { mockApplication.getString(R.string.all_shoes) } returns "+ all shoes +"
        every { mockApplication.getString(R.string.all_bikes) } returns "+ all bikes +"
        every { mockApplication.getString(R.string.no_equipment) } returns "- none -"
        every {
            mockApplication.getString(R.string.cluster_autoname_format, *anyVararg())
        } answers {
            val formatArgs = args[1] as Array<*>
            val name = formatArgs[0]
            val count = formatArgs[1]
            "$name #$count"
        }

        mockWorkoutRepository = mockk(relaxed = true)
        mockEquipmentRepository = mockk(relaxed = true)
        mockSportTypesRepository = mockk(relaxed = true)
        mockSportTypeDatabaseManager = mockk(relaxed = true)
        mockDiscoveryManager = mockk(relaxed = true)
        mockClusterEngine = mockk(relaxed = true)

        WorkoutRepository.resetForTesting(mockWorkoutRepository)
        EquipmentRepository.resetForTesting(mockEquipmentRepository)
        SportTypesRepository.resetForTesting(mockSportTypesRepository)
        SportTypeDatabaseManager.resetForTesting(mockSportTypeDatabaseManager)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(mockDiscoveryManager)
        WorkoutClusterEngine.resetForTesting(mockClusterEngine)

        every { mockEquipmentRepository.equipmentList } returns emptyList()
        every { mockSportTypesRepository.sportTypesList } returns listOf(
            SimpleSportTypeInfo(1L, "Road Cycling", BSportType.BIKE)
        )
        every { mockWorkoutRepository.initialWorkoutLoaded } returns initialWorkoutLoadedLiveEvent
    }

    @After
    fun tearDown() {
        PolarRetrySyncCoordinator.resetForTesting()
        WorkoutRepository.resetForTesting(null)
        EquipmentRepository.resetForTesting(null)
        SportTypesRepository.resetForTesting(null)
        SportTypeDatabaseManager.resetForTesting(null)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)

        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createTestWorkout(
        commute: Boolean = false,
        trainer: Boolean = false,
        race: Boolean = false
    ): WorkoutData {
        return WorkoutData(
            id = 42L,
            finished = true,
            fileBaseName = "test_42",
            workoutName = "Test Ride",
            sportId = 1L,
            sportName = "Road Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = 1000L,
            formattedDate = "2026-11-13",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            commute = commute,
            trainer = trainer,
            race = race,
            equipmentName = null,
            equipmentId = -1L,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 10000.0,
            maxDisplacement = 5000.0,
            activeTimeSec = 1800L,
            totalTimeSec = 2000L,
            avgSpeedMps = 5.55,
            ascentMeters = 100L,
            descentMeters = 100L,
            minAltitude = 50.0,
            maxAltitude = 150.0,
            description = null,
            goal = null,
            method = null,
            stravaSportName = "Ride"
        )
    }

    private fun createViewModel(initialWorkout: WorkoutData): EditWorkoutViewModel {
        coEvery { mockWorkoutRepository.loadWorkout(initialWorkout.id) } coAnswers {
            initialWorkoutLoadedLiveEvent.value = initialWorkout
        }
        val vm = EditWorkoutViewModel(mockApplication, initialWorkout.id)
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun testUpdateIsCommute_setsCommuteTrueAndClearsTrainer() {
        val initial = createTestWorkout(commute = false, trainer = true, race = false)
        val viewModel = createViewModel(initial)

        assertTrue("Initially trainer should be true", viewModel.workoutData.value?.trainer == true)
        assertFalse("Initially commute should be false", viewModel.workoutData.value?.commute == true)

        viewModel.updateIsCommute(true)

        val updated = viewModel.workoutData.value
        assertNotNull(updated)
        assertTrue("Commute must be true after updateIsCommute(true)", updated!!.commute)
        assertFalse("Trainer must be cleared to false when commute is selected", updated.trainer)
    }

    @Test
    fun testUpdateIsTrainer_setsTrainerTrueAndClearsCommute() {
        val initial = createTestWorkout(commute = true, trainer = false, race = false)
        val viewModel = createViewModel(initial)

        assertTrue("Initially commute should be true", viewModel.workoutData.value?.commute == true)
        assertFalse("Initially trainer should be false", viewModel.workoutData.value?.trainer == true)

        viewModel.updateIsTrainer(true)

        val updated = viewModel.workoutData.value
        assertNotNull(updated)
        assertTrue("Trainer must be true after updateIsTrainer(true)", updated!!.trainer)
        assertFalse("Commute must be cleared to false when trainer is selected", updated.commute)
    }

    @Test
    fun testUpdateIsRace_operatesIndependentlyOfCommuteAndTrainer() {
        val initial = createTestWorkout(commute = true, trainer = false, race = false)
        val viewModel = createViewModel(initial)

        viewModel.updateIsRace(true)

        var updated = viewModel.workoutData.value
        assertNotNull(updated)
        assertTrue("Race must be true", updated!!.race)
        assertTrue("Commute must remain true", updated.commute)
        assertFalse("Trainer must remain false", updated.trainer)

        // Now toggle to trainer
        viewModel.updateIsTrainer(true)
        updated = viewModel.workoutData.value
        assertNotNull(updated)
        assertTrue("Trainer must now be true", updated!!.trainer)
        assertFalse("Commute must now be false", updated.commute)
        assertTrue("Race must still remain true when trainer is toggled (Zwift race)", updated.race)

        // Toggle race off
        viewModel.updateIsRace(false)
        updated = viewModel.workoutData.value
        assertNotNull(updated)
        assertFalse("Race must now be false", updated!!.race)
        assertTrue("Trainer must remain true", updated.trainer)
    }

    @Test
    fun testTogglingCommuteOff_doesNotAffectTrainer() {
        val initial = createTestWorkout(commute = true, trainer = false, race = false)
        val viewModel = createViewModel(initial)

        viewModel.updateIsCommute(false)

        val updated = viewModel.workoutData.value
        assertNotNull(updated)
        assertFalse("Commute must be false", updated!!.commute)
        assertFalse("Trainer must remain false", updated.trainer)
    }

    // --- TST-SET-105: Immediate Workout Name Synchronization on Route Cluster Change (ATT-7667) ---

    @Test
    fun testApplyClusterIdentity_updatesWorkoutName_andResetsManualFlag() {
        // Given: ViewModel loaded with existing workout and user manually modified workout name (TST-SET-105.1)
        val initial = createTestWorkout()
        val viewModel = createViewModel(initial)
        viewModel.onWorkoutNameChanged("Manual User Title")
        assertTrue("User manual flag must be true after typing", viewModel.userManuallyChangedWorkoutName)
        assertEquals("Manual User Title", viewModel.workoutData.value?.workoutName)

        val targetCluster = WorkoutCluster(
            id = 55L,
            name = "Morning Route",
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.01,
            endLng = 11.01,
            maxDispLat = 48.005,
            maxDispLng = 11.005,
            refDistance = 10000.0,
            hitCount = 2,
            hasCounter = true,
            bSportType = BSportType.BIKE,
            probableSportId = 1L
        )

        // When: User explicitly applies cluster identity
        viewModel.applyClusterIdentity(targetCluster)

        // Then: Workout name must update immediately to formatted name ("Morning Route #3"), and manual flag resets
        val updated = viewModel.workoutData.value
        assertNotNull(updated)
        assertEquals("Morning Route #3", updated!!.workoutName)
        assertEquals(55L, updated.clusterId)
        assertEquals("Morning Route", updated.clusterName)
        assertFalse("User manual flag must be reset to false after selecting cluster", viewModel.userManuallyChangedWorkoutName)
        verify { mockWorkoutRepository.assignClusterToWorkout(initial.id, 55L) }
    }

    @Test
    fun testRepositoryEmission_whenManualFlagFalse_preservesNewClusterName() {
        // Given: Cluster identity applied and manual flag false (TST-SET-105.2)
        val initial = createTestWorkout()
        val viewModel = createViewModel(initial)

        val targetCluster = WorkoutCluster(
            id = 55L,
            name = "Morning Route",
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.01,
            endLng = 11.01,
            maxDispLat = 48.005,
            maxDispLng = 11.005,
            refDistance = 10000.0,
            hitCount = 2,
            hasCounter = true,
            bSportType = BSportType.BIKE,
            probableSportId = 1L
        )
        viewModel.applyClusterIdentity(targetCluster)
        assertFalse(viewModel.userManuallyChangedWorkoutName)

        // When: Repository emits an update with saved cluster data
        val repoWorkoutUpdate = initial.copy(
            clusterId = 55L,
            clusterName = "Morning Route",
            workoutName = "Morning Route #3"
        )
        initialWorkoutLoadedLiveEvent.value = repoWorkoutUpdate
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Formatted cluster name is preserved (not overwritten by stale name)
        assertEquals("Morning Route #3", viewModel.workoutData.value?.workoutName)
        assertEquals(55L, viewModel.workoutData.value?.clusterId)
    }

    @Test
    fun testUnassignCluster_resetsManualFlag_andRevertsToFallbackName() {
        // Given: Workout with assigned cluster and manual flag set (TST-SET-105.3)
        val initial = createTestWorkout().copy(
            clusterId = 55L,
            clusterName = "Morning Route",
            workoutName = "Morning Route #3",
            fileBaseName = "test_42"
        )
        val viewModel = createViewModel(initial)
        viewModel.onWorkoutNameChanged("Custom Title")
        assertTrue(viewModel.userManuallyChangedWorkoutName)

        // When: Unassigning cluster
        viewModel.unassignCluster()

        // Then: Cluster cleared, manual flag reset, and name reverted to fallback fileBaseName
        val updated = viewModel.workoutData.value
        assertNotNull(updated)
        assertEquals(-1L, updated!!.clusterId)
        assertNull(updated.clusterName)
        assertEquals("test_42", updated.workoutName)
        assertFalse("Manual flag must be reset to false on unassign", viewModel.userManuallyChangedWorkoutName)
        verify { mockWorkoutRepository.unassignClusterFromWorkout(initial.id) }
    }

    @Test
    fun testOnWorkoutNameChanged_setsManualFlag_andProtectsAgainstRepoEmissions() {
        // Given: Workout with assigned cluster (TST-SET-105.4)
        val initial = createTestWorkout().copy(
            clusterId = 55L,
            clusterName = "Morning Route",
            workoutName = "Morning Route #3"
        )
        val viewModel = createViewModel(initial)

        // When: User types a custom workout name
        viewModel.onWorkoutNameChanged("Custom Athlete Title")
        assertTrue(viewModel.userManuallyChangedWorkoutName)
        assertEquals("Custom Athlete Title", viewModel.workoutData.value?.workoutName)

        // And: Background repository emits an update with default/cluster name
        val repoWorkoutUpdate = initial.copy(
            workoutName = "Morning Route #3"
        )
        initialWorkoutLoadedLiveEvent.value = repoWorkoutUpdate
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Custom athlete title must be protected against repo overwrite
        assertEquals("Custom Athlete Title", viewModel.workoutData.value?.workoutName)
    }

    @Test
    fun testCreateNewCluster_resetsManualFlag_andUpdatesWorkoutName() {
        // Given: Workout loaded and user had previously modified workout name (TST-SET-105.5)
        val initial = createTestWorkout()
        val viewModel = createViewModel(initial)
        viewModel.onWorkoutNameChanged("Stale Custom Name")
        assertTrue(viewModel.userManuallyChangedWorkoutName)

        // When: Creating a new cluster with counter
        viewModel.createNewCluster("Morning Loop", hasCounter = true)

        // Then: Workout name updates to new cluster formatted name and manual flag is reset
        val updated = viewModel.workoutData.value
        assertNotNull(updated)
        assertEquals("Morning Loop", updated!!.clusterName)
        assertEquals("Morning Loop", updated.workoutName)
        assertFalse("Manual flag must be reset to false on creating new cluster", viewModel.userManuallyChangedWorkoutName)
        verify { mockWorkoutRepository.createNewClusterFromWorkout(any<WorkoutData>(), "Morning Loop", true, null) }
    }

    @Test
    fun testEditWorkoutViewModel_initializesAndUpdatesSyncToHealthConnect() {
        // TST-REV-015.1: Initialize ViewModel with sample workout data having syncToHealthConnect = null
        every { TrainingApplication.syncToHealthConnect() } returns true
        val initial = createTestWorkout().copy(syncToHealthConnect = null)
        val viewModel = createViewModel(initial)

        // Verify loaded workoutData.value?.syncToHealthConnect matches TrainingApplication.syncToHealthConnect()
        assertEquals(true, viewModel.workoutData.value?.syncToHealthConnect)

        // Invoke viewModel.updateSyncToHealthConnect(false), assert == false
        viewModel.updateSyncToHealthConnect(false)
        assertEquals(false, viewModel.workoutData.value?.syncToHealthConnect)

        // Invoke viewModel.updateSyncToHealthConnect(true), assert == true
        viewModel.updateSyncToHealthConnect(true)
        assertEquals(true, viewModel.workoutData.value?.syncToHealthConnect)
    }

    /**
     * TST-INT-002 (REQ-EXP-006): Selecting a sport type configured with "- No upload -"
     * (stravaName == null) disables Strava upload on the edited workout (uploadToStrava == 0).
     */
    @Test
    fun testOnSportTypeSelected_noUploadStravaMapping_setsUploadToStravaZero_TST_INT_002() {
        every { mockSportTypesRepository.sportTypesList } returns listOf(
            SimpleSportTypeInfo(1L, "Road Cycling", BSportType.BIKE),
            SimpleSportTypeInfo(99L, "Private Walk", BSportType.UNKNOWN)
        )
        every { mockSportTypesRepository.getBSportType(99L) } returns BSportType.UNKNOWN
        every { mockSportTypesRepository.getStravaName(99L) } returns null

        val initial = createTestWorkout().copy(uploadToStrava = 1, stravaSportName = "Ride")
        val viewModel = createViewModel(initial)

        viewModel.updateSportName("Private Walk")

        val updated = viewModel.workoutData.value
        assertNotNull(updated)
        assertEquals(99L, updated!!.sportId)
        assertNull("Strava name must be null for '- No upload -'", updated.stravaSportName)
        assertEquals("uploadToStrava must be 0 when Strava mapping is null", 0, updated.uploadToStrava)
    }

    /**
     * TST-SET-046 (REQ-SET-018): Reactive Editor Suggestions.
     * Verifies editing a Running workout on a Cycling cluster starts with score > 5.0,
     * and calling updateSportName("Road Cycling") triggers debounced fetchClusterSuggestions
     * dropping score below 1.0 (penalty removed).
     */
    @Test
    fun testUpdateSportName_reactivelyRefreshesClusterSuggestionsAndDropsScore_TST_SET_046() {
        // TST-SET-046
        every { mockSportTypesRepository.sportTypesList } returns listOf(
            SimpleSportTypeInfo(1L, "Road Cycling", BSportType.BIKE),
            SimpleSportTypeInfo(2L, "Running", BSportType.RUN)
        )
        every { mockSportTypesRepository.getBSportType(1L) } returns BSportType.BIKE
        every { mockSportTypesRepository.getBSportType(2L) } returns BSportType.RUN
        every { mockSportTypesRepository.getStravaName(any()) } returns "Ride"
        every { mockDiscoveryManager.getLinkedSportTypeIds(any<Long>()) } returns emptySet()
        every {
            mockDiscoveryManager.getCandidateBSportTypes(BSportType.UNKNOWN, any<Double>())
        } returns setOf(BSportType.RUN, BSportType.BIKE)

        val cyclingCluster = WorkoutCluster(
            id = 88L,
            name = "River Bike Loop",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.0,
            endLng = 11.0,
            maxDispLat = 48.05,
            maxDispLng = 11.05,
            refDistance = 10000.0,
            hitCount = 4,
            bSportType = BSportType.BIKE
        )

        every {
            mockClusterEngine.getClusterScores(
                any<com.google.android.gms.maps.model.LatLng>(),
                any<com.google.android.gms.maps.model.LatLng>(),
                any<com.google.android.gms.maps.model.LatLng>(),
                any<Double>(),
                any<String>(),
                match<Set<BSportType>> { it == setOf(BSportType.RUN) || !it.contains(BSportType.BIKE) },
                isNull(),
                isNull()
            )
        } returns listOf(cyclingCluster to 5.15)
        every {
            mockClusterEngine.getClusterScores(
                any<com.google.android.gms.maps.model.LatLng>(),
                any<com.google.android.gms.maps.model.LatLng>(),
                any<com.google.android.gms.maps.model.LatLng>(),
                any<Double>(),
                any<String>(),
                match<Set<BSportType>> { it.contains(BSportType.BIKE) },
                isNull(),
                isNull()
            )
        } returns listOf(cyclingCluster to 0.15)

        val runningWorkout = createTestWorkout().copy(
            sportId = 2L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            avgSpeedMps = 0.0,
            startLatLng = com.google.android.gms.maps.model.LatLng(48.0, 11.0),
            endLatLng = com.google.android.gms.maps.model.LatLng(48.0, 11.0),
            maxDisplacementLatLng = com.google.android.gms.maps.model.LatLng(48.05, 11.05)
        )

        val viewModel = createViewModel(runningWorkout)
        testDispatcher.scheduler.advanceTimeBy(600)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialScore = viewModel.clusterSuggestions.value.firstOrNull()?.second
        assertNotNull("Initial suggestion must be emitted", initialScore)
        assertTrue("Initial mismatched RUN->BIKE score must exceed 5.0, was $initialScore", initialScore!! > 5.0)

        // Switch sport to Road Cycling via updateSportName
        viewModel.updateSportName("Road Cycling")
        testDispatcher.scheduler.advanceTimeBy(600)
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedScore = viewModel.clusterSuggestions.value.firstOrNull()?.second
        assertNotNull("Updated suggestion must be emitted after sport switch", updatedScore)
        assertTrue("Matching BIKE score must drop below 1.0, was $updatedScore", updatedScore!! < 1.0)
    }

    @Test
    fun testReactiveTelemetryRefresh_preservesAllUserEditsAndSkipsSuggestionRewrites_tstTrk091_2() {
        // REQ-TRK-091.2 / TST-TRK-091.2
        val workoutsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<WorkoutData>>(emptyList())
        every { mockWorkoutRepository.allWorkouts } returns workoutsFlow

        val initial = createTestWorkout().copy(
            id = 2414L,
            workoutName = "Auto Run",
            totalDistance = 5000.0,
            avgSpeedMps = 3.0,
            ascentMeters = 20L,
            mapPolyline = "raw_poly",
            polarSyncPending = false,
            recordingErrors = """["warn"]"""
        )
        val viewModel = createViewModel(initial)
        testDispatcher.scheduler.advanceUntilIdle()

        // User edits multiple metadata fields while background Polar/ERTS post-processing is running
        viewModel.updateWorkoutName("Custom Interval Session")
        viewModel.updateRpe(8)
        viewModel.updateFeel(4)
        viewModel.updateDescription("Great negative splits")
        viewModel.updateGoal("Threshold")
        viewModel.updateMethod("4x8min")
        viewModel.updateIsCommute(true)
        viewModel.updateIsRace(true)
        viewModel.updateSyncToHealthConnect(true)
        viewModel.clearRecordingErrors()
        testDispatcher.scheduler.advanceUntilIdle()

        val sportBeforeRefresh = viewModel.suggestedSportTypeName
        val equipBeforeRefresh = viewModel.suggestedEquipmentName

        // Background post-processing completes and refreshes telemetry into repository.allWorkouts
        val refreshedFromRepo = initial.copy(
            workoutName = "Auto Run", // stale DB name before user taps Save
            rpe = null,
            feel = null,
            description = "",
            commute = false,
            race = false,
            totalDistance = 5320.5,
            avgSpeedMps = 3.45,
            ascentMeters = 68L,
            mapPolyline = "smoothed_erts_poly",
            polarSyncPending = true,
            recordingErrors = """["warn"]"""
        )
        workoutsFlow.value = listOf(refreshedFromRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val merged = viewModel.workoutData.value!!
        // Telemetry fields refreshed reactively
        assertEquals(5320.5, merged.totalDistance, 0.001)
        assertEquals(3.45, merged.avgSpeedMps, 0.001)
        assertEquals(68L, merged.ascentMeters)
        assertEquals("smoothed_erts_poly", merged.mapPolyline)
        assertTrue(merged.polarSyncPending)

        // All user edits strictly preserved
        assertEquals("Custom Interval Session", merged.workoutName)
        assertEquals(8, merged.rpe)
        assertEquals(4, merged.feel)
        assertEquals("Great negative splits", merged.description)
        assertEquals("Threshold", merged.goal)
        assertEquals("4x8min", merged.method)
        assertTrue(merged.commute)
        assertTrue(merged.race)
        assertEquals(true, merged.syncToHealthConnect)
        assertNull("User-cleared recordingErrors must stay cleared", merged.recordingErrors)
        assertEquals(sportBeforeRefresh, viewModel.suggestedSportTypeName)
        assertEquals(equipBeforeRefresh, viewModel.suggestedEquipmentName)
    }

    @Test
    fun testCreateNewClusterInitialReload_adoptsClusterIdWhileAllWorkoutsRefreshPreservesIt_tstTrk091_2() {
        // REQ-TRK-091.2 / TST-TRK-091.2 (Gate 4 regression guard for createNewCluster -> Save)
        val workoutsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<WorkoutData>>(emptyList())
        every { mockWorkoutRepository.allWorkouts } returns workoutsFlow

        val initial = createTestWorkout().copy(
            id = 2414L,
            workoutName = "Evening Ride",
            clusterId = -1L,
            clusterName = null,
            totalDistance = 12000.0
        )
        val viewModel = createViewModel(initial)
        testDispatcher.scheduler.advanceUntilIdle()

        // User edits RPE/description and triggers createNewCluster() which re-emits initialWorkoutLoaded with new clusterId
        viewModel.updateRpe(7)
        viewModel.updateDescription("Windy loop")
        viewModel.createNewCluster("River Loop", hasCounter = true)
        initialWorkoutLoadedLiveEvent.value = initial.copy(
            clusterId = 77L,
            clusterName = "River Loop",
            workoutName = "River Loop #1"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(77L, viewModel.workoutData.value?.clusterId)
        assertEquals("River Loop", viewModel.workoutData.value?.clusterName)
        assertEquals(7, viewModel.workoutData.value?.rpe)
        assertEquals("Windy loop", viewModel.workoutData.value?.description)

        // Subsequent background telemetry refresh arrives on repository.allWorkouts with stale clusterId=-1L
        workoutsFlow.value = listOf(
            initial.copy(
                clusterId = -1L,
                clusterName = null,
                totalDistance = 12450.0,
                ascentMeters = 140L
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val merged = viewModel.workoutData.value!!
        assertEquals(12450.0, merged.totalDistance, 0.001)
        assertEquals(140L, merged.ascentMeters)
        assertEquals("Adopted clusterId must remain preserved across telemetry refresh", 77L, merged.clusterId)
        assertEquals("River Loop", merged.clusterName)
        assertEquals(7, merged.rpe)
        assertEquals("Windy loop", merged.description)
    }

    @Test
    fun testDismissPolarSyncPending_clearsStatePersistsAndSurvivesStaleRefresh_tstSet129_2() {
        // REQ-SET-127.2 / TST-SET-129.2 (ATT-9174)
        val workoutsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<WorkoutData>>(emptyList())
        every { mockWorkoutRepository.allWorkouts } returns workoutsFlow

        val initial = createTestWorkout().copy(
            id = 2417L,
            polarSyncPending = true,
            polarExerciseId = "verity:/U/0/HR.BPB",
            polarDeviceId = "24825632"
        )
        val viewModel = createViewModel(initial)
        assertTrue(viewModel.workoutData.value?.polarSyncPending == true)

        viewModel.dismissPolarSyncPending()

        assertFalse("polarSyncPending must clear immediately on dismiss", viewModel.workoutData.value?.polarSyncPending == true)
        verify(exactly = 1) { mockWorkoutRepository.dismissPolarSyncPending(2417L) }

        // Stale allWorkouts emission before DB update commits must not re-open dismissed banner
        workoutsFlow.value = listOf(initial.copy(polarSyncPending = true, totalDistance = 10150.0))
        testDispatcher.scheduler.advanceUntilIdle()

        val merged = viewModel.workoutData.value!!
        assertFalse("Dismissed polarSyncPending must stay false across stale refresh", merged.polarSyncPending)
        assertEquals(10150.0, merged.totalDistance, 0.001)
    }

    @Test
    fun testPolarRetrySyncCompletion_refreshesMatchingWorkoutAndPreservesUserEdits_tstSet129_3() {
        // REQ-SET-127.3 / TST-SET-129.3 (ATT-9174)
        PolarRetrySyncCoordinator.resetForTesting()
        PolarRetrySyncCoordinator.dispatcher = Dispatchers.Unconfined
        PolarRetrySyncCoordinator.syncExecutorOverride = { _, _, _ -> true }

        val workoutsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<WorkoutData>>(emptyList())
        every { mockWorkoutRepository.allWorkouts } returns workoutsFlow

        val initial = createTestWorkout().copy(
            id = 2417L,
            workoutName = "Auto Run",
            polarSyncPending = true,
            polarExerciseId = "verity:/U/0/HR.BPB",
            polarDeviceId = "24825632",
            totalDistance = 5000.0
        )
        val viewModel = createViewModel(initial)
        viewModel.updateWorkoutName("Edited Morning Run")
        viewModel.updateDescription("Felt strong")

        // Unrelated workout completion must NOT refresh workout 2417
        PolarRetrySyncCoordinator.startSync(mockApplication, 9999L, "verity:", "failed")
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { mockWorkoutRepository.refreshWorkout(2417L) }

        // Matching workout completion triggers refreshWorkout(2417L)
        PolarRetrySyncCoordinator.startSync(mockApplication, 2417L, "verity:/U/0/HR.BPB", "failed")
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { mockWorkoutRepository.refreshWorkout(2417L) }

        // Repository emits refreshed workout with polarSyncPending=false and updated telemetry
        workoutsFlow.value = listOf(
            initial.copy(
                workoutName = "Auto Run",
                description = "",
                polarSyncPending = false,
                totalDistance = 5320.0,
                ascentMeters = 88L
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val merged = viewModel.workoutData.value!!
        assertFalse(merged.polarSyncPending)
        assertEquals(5320.0, merged.totalDistance, 0.001)
        assertEquals(88L, merged.ascentMeters)
        assertEquals("Edited Morning Run", merged.workoutName)
        assertEquals("Felt strong", merged.description)
    }
}
