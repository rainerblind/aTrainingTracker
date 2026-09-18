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
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper.EquipmentData
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
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

/**
 * Unit tests verifying that retired equipment is excluded from workout pickers
 * while strictly preserving existing retired equipment assignments (REQ-UI-158, TST-UI-111, ATT-1118).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditWorkoutViewModelRetiredEquipmentTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockApplication: Application
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockEquipmentRepository: EquipmentRepository
    private lateinit var mockSportTypesRepository: SportTypesRepository
    private lateinit var mockSportTypeDatabaseManager: SportTypeDatabaseManager
    private lateinit var mockDiscoveryManager: EquipmentAndSportTypeDiscoveryManager
    private lateinit var mockClusterEngine: WorkoutClusterEngine

    private val initialWorkoutLoadedLiveEvent = MutableLiveData<WorkoutData>()

    private val activeBike = EquipmentData(1L, "Active Canyon", BSportType.BIKE, 3, "Canyon", "canyon1", false)
    private val retiredBikeAssigned = EquipmentData(2L, "Retired Trek", BSportType.BIKE, 3, "Trek", "trek2", true)
    private val retiredBikeOther = EquipmentData(3L, "Retired Pinarello", BSportType.BIKE, 3, "Pinarello", "pinarello3", true)

    private val activeShoe = EquipmentData(10L, "Active Pegasus", BSportType.RUN, 0, "Nike", "nike10", false)
    private val retiredShoe = EquipmentData(11L, "Retired Kayano", BSportType.RUN, 0, "Asics", "asics11", true)

    private val allEquipmentList = listOf(activeBike, retiredBikeAssigned, retiredBikeOther, activeShoe, retiredShoe)

    private val sportRoadBike = SimpleSportTypeInfo(100L, "Road Cycling", BSportType.BIKE)
    private val sportRunning = SimpleSportTypeInfo(200L, "Running", BSportType.RUN)

    private val allSportTypesList = listOf(sportRoadBike, sportRunning)

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

        every { mockEquipmentRepository.equipmentList } returns allEquipmentList
        every { mockSportTypesRepository.sportTypesList } returns allSportTypesList
        every { mockWorkoutRepository.initialWorkoutLoaded } returns initialWorkoutLoadedLiveEvent

        every { mockSportTypeDatabaseManager.getBSportType(100L) } returns BSportType.BIKE
        every { mockSportTypeDatabaseManager.getBSportType(200L) } returns BSportType.RUN
        every { mockSportTypeDatabaseManager.getStravaName(any()) } returns "Ride"
        every { mockDiscoveryManager.getSpeedBasedSportTypeNames(any(), any()) } returns emptySet()
        every { mockDiscoveryManager.getEquipmentNamesForSport(any()) } returns emptySet()
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
        id: Long = 1L,
        sportName: String = "Road Cycling",
        bSportType: BSportType = BSportType.BIKE,
        sportId: Long = 100L,
        equipmentName: String? = null,
        equipmentId: Long = -1L
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "test_$id",
            workoutName = "Workout $id",
            sportId = sportId,
            sportName = sportName,
            bSportType = bSportType,
            startTimeS = 1000L,
            formattedDate = "2026-09-07",
            formattedTime = "10:00",
            localDateTime = java.time.LocalDateTime.now(),
            equipmentName = equipmentName,
            equipmentId = equipmentId,
            commute = false,
            trainer = false,
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
    fun testPicker_withoutEquipment_excludesRetiredEquipment() {
        val workout = createTestWorkout(equipmentId = -1L, equipmentName = null)
        val vm = createViewModel(workout)

        val options = vm.equipmentNames.value ?: emptyList()
        assertTrue(options.contains("- none -"))
        assertTrue(options.contains("Active Canyon"))
        assertFalse("Retired equipment must not appear in options for unassigned workout", options.contains("Retired Trek"))
        assertFalse("Retired equipment must not appear in options for unassigned workout", options.contains("Retired Pinarello"))
    }

    @Test
    fun testPicker_withActiveEquipment_excludesRetiredEquipment() {
        val workout = createTestWorkout(equipmentId = 1L, equipmentName = "Active Canyon")
        val vm = createViewModel(workout)

        val options = vm.equipmentNames.value ?: emptyList()
        assertTrue(options.contains("- none -"))
        assertTrue(options.contains("Active Canyon"))
        assertFalse("Retired equipment must be excluded", options.contains("Retired Trek"))
        assertFalse("Retired equipment must be excluded", options.contains("Retired Pinarello"))
    }

    @Test
    fun testPicker_withAssignedRetiredEquipment_preservesThatSpecificRetiredEquipment() {
        val workout = createTestWorkout(equipmentId = 2L, equipmentName = "Retired Trek")
        val vm = createViewModel(workout)

        val options = vm.equipmentNames.value ?: emptyList()
        assertTrue(options.contains("- none -"))
        assertTrue(options.contains("Active Canyon"))
        assertTrue("Assigned retired equipment MUST be retained in options to preserve attribution", options.contains("Retired Trek"))
        assertFalse("Other retired equipment must still be excluded", options.contains("Retired Pinarello"))
    }

    @Test
    fun testShowAllBikes_preservesAssignedRetiredBike_excludesOtherRetiredBikes() {
        val workout = createTestWorkout(equipmentId = 2L, equipmentName = "Retired Trek")
        val vm = createViewModel(workout)

        vm.updateEquipmentName("+ all bikes +")

        val options = vm.equipmentNames.value ?: emptyList()
        assertTrue(options.contains("- none -"))
        assertTrue(options.contains("Active Canyon"))
        assertTrue("Assigned retired bike must be present after showAllBikes()", options.contains("Retired Trek"))
        assertFalse("Unassigned retired bike must remain excluded after showAllBikes()", options.contains("Retired Pinarello"))
    }

    @Test
    fun testPicker_runningWorkout_excludesRetiredShoes() {
        val runningWorkout = createTestWorkout(
            id = 2L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            sportId = 200L,
            equipmentId = -1L,
            equipmentName = null
        )
        val vm = createViewModel(runningWorkout)

        val options = vm.equipmentNames.value ?: emptyList()
        assertTrue(options.contains("- none -"))
        assertTrue(options.contains("Active Pegasus"))
        assertFalse("Retired shoe must be excluded when not assigned", options.contains("Retired Kayano"))
    }
}
