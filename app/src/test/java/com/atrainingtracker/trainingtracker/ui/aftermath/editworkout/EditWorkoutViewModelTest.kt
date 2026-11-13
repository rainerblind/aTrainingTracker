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
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.repositories.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
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

        mockApplication = mockk(relaxed = true)
        every { mockApplication.getString(R.string.all_sports) } returns "+ all sports +"
        every { mockApplication.getString(R.string.all_shoes) } returns "+ all shoes +"
        every { mockApplication.getString(R.string.all_bikes) } returns "+ all bikes +"
        every { mockApplication.getString(R.string.no_equipment) } returns "- none -"

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
}
