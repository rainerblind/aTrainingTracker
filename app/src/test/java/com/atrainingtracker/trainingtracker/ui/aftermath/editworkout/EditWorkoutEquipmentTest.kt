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
 * Automated unit test suite verifying sport-specific equipment selection,
 * 3-case linked equipment hierarchy (N > 1, N == 1, N == 0), in-place expansion,
 * and cross-sport switching invariants in accordance with REQ-UI-130 and TST-UI-083 (ATT-715).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditWorkoutEquipmentTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockApplication: Application
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockEquipmentRepository: EquipmentRepository
    private lateinit var mockSportTypesRepository: SportTypesRepository
    private lateinit var mockSportTypeDatabaseManager: SportTypeDatabaseManager
    private lateinit var mockDiscoveryManager: EquipmentAndSportTypeDiscoveryManager
    private lateinit var mockClusterEngine: WorkoutClusterEngine

    private val initialWorkoutLoadedLiveEvent = MutableLiveData<WorkoutData>()

    // Equipment Test Pool
    private val bike1 = EquipmentData(1L, "Canyon Ultimate", BSportType.BIKE, 3, "Canyon", "canyon1")
    private val bike2 = EquipmentData(2L, "Trek Emonda", BSportType.BIKE, 3, "Trek", "trek2")
    private val bike3 = EquipmentData(3L, "Pinarello Dogma", BSportType.BIKE, 3, "Pinarello", "pinarello3")
    private val shoe1 = EquipmentData(10L, "Nike Pegasus", BSportType.RUN, 0, "Nike", "nike10")
    private val shoe2 = EquipmentData(11L, "Asics Kayano", BSportType.RUN, 0, "Asics", "asics11")

    private val allEquipmentList = listOf(bike1, bike2, bike3, shoe1, shoe2)

    // Sport Types Test Pool
    private val sportRoadBike = SimpleSportTypeInfo(100L, "Road Cycling", BSportType.BIKE)
    private val sportMtb = SimpleSportTypeInfo(101L, "Mountain Biking", BSportType.BIKE)
    private val sportRunning = SimpleSportTypeInfo(200L, "Running", BSportType.RUN)
    private val sportTrailRun = SimpleSportTypeInfo(201L, "Trail Running", BSportType.RUN)
    private val sportSwimming = SimpleSportTypeInfo(300L, "Swimming", BSportType.UNKNOWN)

    private val allSportTypesList = listOf(sportRoadBike, sportMtb, sportRunning, sportTrailRun, sportSwimming)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Setup ArchTaskExecutor to execute LiveData updates synchronously
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

        // Configure sport type mappings
        every { mockSportTypeDatabaseManager.getBSportType(100L) } returns BSportType.BIKE
        every { mockSportTypeDatabaseManager.getBSportType(101L) } returns BSportType.BIKE
        every { mockSportTypeDatabaseManager.getBSportType(200L) } returns BSportType.RUN
        every { mockSportTypeDatabaseManager.getBSportType(201L) } returns BSportType.RUN
        every { mockSportTypeDatabaseManager.getBSportType(300L) } returns BSportType.UNKNOWN

        every { mockSportTypeDatabaseManager.getStravaName(any()) } returns "Ride"
        every { mockDiscoveryManager.getSpeedBasedSportTypeNames(any(), any()) } returns emptySet()
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
    fun testCase1_multipleLinkedBikes_showsNoneLinkedAndExpansionToken_zeroShoes() {
        // Given: Road Cycling has 2 linked bikes out of 3 bikes in DB
        every { mockDiscoveryManager.getEquipmentNamesForSport("Road Cycling") } returns setOf("Canyon Ultimate", "Trek Emonda")

        val workout = createTestWorkout(
            id = 1L,
            sportName = "Road Cycling",
            bSportType = BSportType.BIKE,
            sportId = 100L
        )

        val vm = createViewModel(workout)

        // Then: Options must be [- none -, Canyon Ultimate, Trek Emonda, + all bikes +]
        val options = vm.equipmentNames.value ?: emptyList()
        assertEquals(listOf("- none -", "Canyon Ultimate", "Trek Emonda", "+ all bikes +"), options)

        // Verify zero running shoes are present
        assertFalse(options.contains("Nike Pegasus"))
        assertFalse(options.contains("Asics Kayano"))
    }

    @Test
    fun testCase1_inPlaceExpansion_selectingAllBikes_expandsListToAllBikes() {
        // Given: Multiple linked bikes
        every { mockDiscoveryManager.getEquipmentNamesForSport("Road Cycling") } returns setOf("Canyon Ultimate", "Trek Emonda")

        val workout = createTestWorkout(
            id = 1L,
            sportName = "Road Cycling",
            bSportType = BSportType.BIKE,
            sportId = 100L
        )
        val vm = createViewModel(workout)

        // When: User taps "+ all bikes +"
        vm.updateEquipmentName("+ all bikes +")

        // Then: List expands in-place to [- none -, Canyon Ultimate, Trek Emonda, Pinarello Dogma]
        val options = vm.equipmentNames.value ?: emptyList()
        assertEquals(listOf("- none -", "Canyon Ultimate", "Trek Emonda", "Pinarello Dogma"), options)

        // Shoes must still not be present
        assertFalse(options.contains("Nike Pegasus"))
        assertFalse(options.contains("Asics Kayano"))
    }

    @Test
    fun testCase2_singleLinkedEquipment_autoPreselectsLinkedGearAndShowsAllGear() {
        // Given: Running has exactly 1 linked shoe (Nike Pegasus)
        every { mockDiscoveryManager.getEquipmentNamesForSport("Running") } returns setOf("Nike Pegasus")

        val workout = createTestWorkout(
            id = 2L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            sportId = 200L
        )

        val vm = createViewModel(workout)

        // Then: Dropdown presents [- none -, Nike Pegasus, Asics Kayano]
        val options = vm.equipmentNames.value ?: emptyList()
        assertEquals(listOf("- none -", "Nike Pegasus", "Asics Kayano"), options)

        // And: Uniquely linked shoe is automatically preselected
        assertEquals("Nike Pegasus", vm.suggestedEquipmentName)
        assertEquals("Nike Pegasus", vm.workoutData.value?.equipmentName)
        assertEquals(10L, vm.workoutData.value?.equipmentId)

        // Zero bikes in list
        assertFalse(options.contains("Canyon Ultimate"))
    }

    @Test
    fun testCase3_zeroLinkedEquipment_preselectsNoneAndShowsAllGear() {
        // Given: Mountain Biking has 0 linked equipment
        every { mockDiscoveryManager.getEquipmentNamesForSport("Mountain Biking") } returns emptySet()

        val workout = createTestWorkout(
            id = 3L,
            sportName = "Mountain Biking",
            bSportType = BSportType.BIKE,
            sportId = 101L
        )

        val vm = createViewModel(workout)

        // Then: Dropdown presents [- none -, Canyon Ultimate, Trek Emonda, Pinarello Dogma]
        val options = vm.equipmentNames.value ?: emptyList()
        assertEquals(listOf("- none -", "Canyon Ultimate", "Trek Emonda", "Pinarello Dogma"), options)

        // And: "- none -" is preselected (suggestedEquipmentName == null)
        assertNull(vm.suggestedEquipmentName)
        assertNull(vm.workoutData.value?.equipmentName)
        assertEquals(-1L, vm.workoutData.value?.equipmentId)
    }

    @Test
    fun testCrossSportSwitching_differentBaseSport_resetsEquipmentToNoneOrLinked() {
        // Given: Workout starts as Road Cycling with Canyon Ultimate selected
        every { mockDiscoveryManager.getEquipmentNamesForSport("Road Cycling") } returns setOf("Canyon Ultimate", "Trek Emonda")
        every { mockDiscoveryManager.getEquipmentNamesForSport("Running") } returns setOf("Nike Pegasus")
        every { mockDiscoveryManager.getEquipmentNamesForSport("Trail Running") } returns emptySet()

        val workout = createTestWorkout(
            id = 4L,
            sportName = "Road Cycling",
            bSportType = BSportType.BIKE,
            sportId = 100L,
            equipmentName = "Canyon Ultimate",
            equipmentId = 1L
        )
        val vm = createViewModel(workout)

        // When: Switching to Running (different BSportType, has N == 1 link: Nike Pegasus)
        vm.updateSportName("Running")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Equipment must switch to Nike Pegasus (bicycles cleared)
        assertEquals("Nike Pegasus", vm.suggestedEquipmentName)
        assertEquals("Nike Pegasus", vm.workoutData.value?.equipmentName)
        assertEquals(listOf("- none -", "Nike Pegasus", "Asics Kayano"), vm.equipmentNames.value)

        // When: Switching from Running back to Road Cycling (RUN -> BIKE, has N == 2 links)
        vm.updateSportName("Road Cycling")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Cross-sport gear (Nike Pegasus) is cleared, and because N > 1, equipment resets to null (- none -)
        assertNull(vm.suggestedEquipmentName)
        assertNull(vm.workoutData.value?.equipmentName)
        assertEquals(listOf("- none -", "Canyon Ultimate", "Trek Emonda", "+ all bikes +"), vm.equipmentNames.value)
    }

    @Test
    fun testCrossSportSwitching_sameBaseSport_retainsValidSelectedEquipment() {
        // Given: Workout starts as Road Cycling with Pinarello Dogma selected
        every { mockDiscoveryManager.getEquipmentNamesForSport("Road Cycling") } returns setOf("Canyon Ultimate", "Trek Emonda")
        every { mockDiscoveryManager.getEquipmentNamesForSport("Mountain Biking") } returns emptySet()

        val workout = createTestWorkout(
            id = 5L,
            sportName = "Road Cycling",
            bSportType = BSportType.BIKE,
            sportId = 100L,
            equipmentName = "Pinarello Dogma",
            equipmentId = 3L
        )
        val vm = createViewModel(workout)
        assertEquals("Pinarello Dogma", vm.suggestedEquipmentName)

        // When: Switching to Mountain Biking (same BSportType.BIKE, N == 0 links)
        vm.updateSportName("Mountain Biking")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Valid bicycle is retained!
        assertEquals("Pinarello Dogma", vm.suggestedEquipmentName)
        assertEquals("Pinarello Dogma", vm.workoutData.value?.equipmentName)
        assertEquals(3L, vm.workoutData.value?.equipmentId)
    }

    @Test
    fun testNonBikeNonRunSport_unknownBaseSport_showsOnlyNone() {
        // Given: Swimming has 0 linked equipment
        every { mockDiscoveryManager.getEquipmentNamesForSport("Swimming") } returns emptySet()

        val workout = createTestWorkout(
            id = 6L,
            sportName = "Swimming",
            bSportType = BSportType.UNKNOWN,
            sportId = 300L
        )
        val vm = createViewModel(workout)

        // Then: Options contain only [- none -]
        val options = vm.equipmentNames.value ?: emptyList()
        assertEquals(listOf("- none -"), options)
        assertNull(vm.suggestedEquipmentName)
    }
}
