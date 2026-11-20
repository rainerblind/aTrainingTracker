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
}
