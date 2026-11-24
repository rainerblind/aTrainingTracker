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

package com.atrainingtracker.banalservice.ui.sporttype

import android.app.Application
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutStatsSummary
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying that SportTypeViewModel.availableEquipment queries active equipment only
 * (REQ-UI-158, TST-UI-111, ATT-1118) and that sport type stats route through WorkoutRepository (REQ-ARC-009.1, TST-ARC-009.3).
 */
class SportTypeViewModelRetiredTest {

    private lateinit var mockApplication: Application
    private lateinit var mockEquipmentDbHelper: EquipmentDbHelper
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockSportTypeManager: SportTypeDatabaseManager

    @Before
    fun setUp() {
        mockApplication = mockk(relaxed = true)

        mockkStatic(SportTypeDatabaseManager::class)
        mockkObject(SportTypeEquipmentLinkManager.Companion)

        mockSportTypeManager = mockk(relaxed = true)
        val mockLinksHelper = mockk<SportTypeEquipmentLinkManager>(relaxed = true)

        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeManager
        every { SportTypeEquipmentLinkManager.getInstance(any()) } returns mockLinksHelper

        mockWorkoutRepository = mockk(relaxed = true)
        every { mockWorkoutRepository.workoutMutationEvents } returns kotlinx.coroutines.flow.MutableSharedFlow()
        WorkoutRepository.resetForTesting(mockWorkoutRepository)

        mockEquipmentDbHelper = mockk(relaxed = true)
        EquipmentDbHelper.resetForTesting(mockEquipmentDbHelper)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        EquipmentDbHelper.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testAvailableEquipment_callsGetEquipmentItemsWithActiveOnlyTrue() {
        val activeBike = EquipmentDbHelper.EquipmentData(
            1L, "Active Road Bike", BSportType.BIKE, 3, "RoadBike", "b1", false
        )

        every {
            mockEquipmentDbHelper.getEquipmentItems(BSportType.BIKE, true)
        } returns listOf(activeBike)

        val viewModel = SportTypeViewModel(mockApplication, mockWorkoutRepository)
        val result = viewModel.availableEquipment(BSportType.BIKE)

        assertEquals(listOf(activeBike), result)
        verify { mockEquipmentDbHelper.getEquipmentItems(BSportType.BIKE, true) }
    }

    @Test
    fun testGetDetailedStats_routesThroughWorkoutRepository() {
        val summary = WorkoutStatsSummary(
            totalDistanceM = 15000.0,
            totalActiveTimeS = 3600L,
            totalAscentM = 250,
            firstUsage = "2026-01-01 10:00:00",
            lastUsage = "2026-02-01 10:00:00",
            count = 5
        )
        every { mockWorkoutRepository.getSportTypeStatsForPeriod(1L, any(), any()) } returns summary

        val viewModel = SportTypeViewModel(mockApplication, mockWorkoutRepository)
        val statsList = viewModel.getDetailedStats("Cycling", 1L, "2026-01-01")

        verify(atLeast = 1) { mockWorkoutRepository.getSportTypeStatsForPeriod(1L, any(), any()) }
    }

    @Test
    fun testWorkoutDeletedMutationEvent_reloadsSportTypes() {
        val events = kotlinx.coroutines.flow.MutableSharedFlow<com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutMutationEvent>(extraBufferCapacity = 8)
        every { mockWorkoutRepository.workoutMutationEvents } returns events

        val viewModel = SportTypeViewModel(mockApplication, mockWorkoutRepository)
        verify(timeout = 2000, atLeast = 1) { mockSportTypeManager.database }

        events.tryEmit(com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutMutationEvent.Deleted(workoutId = 77L, sportId = 1L))
        verify(timeout = 2000, atLeast = 2) { mockSportTypeManager.database }
        assertEquals(emptyList<SportTypeItem>(), viewModel.sportTypes.value)
    }
}
