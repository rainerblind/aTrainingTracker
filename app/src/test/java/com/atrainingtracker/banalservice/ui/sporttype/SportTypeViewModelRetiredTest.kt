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
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying that SportTypeViewModel.availableEquipment queries active equipment only
 * (REQ-UI-158, TST-UI-111, ATT-1118).
 */
class SportTypeViewModelRetiredTest {

    private lateinit var mockApplication: Application
    private lateinit var mockEquipmentDbHelper: EquipmentDbHelper

    @Before
    fun setUp() {
        mockApplication = mockk(relaxed = true)

        mockkStatic(SportTypeDatabaseManager::class)
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        mockkObject(SportTypeEquipmentLinkManager.Companion)

        val mockSportTypeManager = mockk<SportTypeDatabaseManager>(relaxed = true)
        val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        val mockLinksHelper = mockk<SportTypeEquipmentLinkManager>(relaxed = true)

        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeManager
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager
        every { SportTypeEquipmentLinkManager.getInstance(any()) } returns mockLinksHelper

        mockkConstructor(EquipmentDbHelper::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testAvailableEquipment_callsGetEquipmentItemsWithActiveOnlyTrue() {
        val activeBike = EquipmentDbHelper.EquipmentData(
            1L, "Active Road Bike", BSportType.BIKE, 3, "RoadBike", "b1", false
        )

        every {
            anyConstructed<EquipmentDbHelper>().getEquipmentItems(BSportType.BIKE, true)
        } returns listOf(activeBike)

        val viewModel = SportTypeViewModel(mockApplication)
        val result = viewModel.availableEquipment(BSportType.BIKE)

        assertEquals(listOf(activeBike), result)
        verify { anyConstructed<EquipmentDbHelper>().getEquipmentItems(BSportType.BIKE, true) }
    }
}
