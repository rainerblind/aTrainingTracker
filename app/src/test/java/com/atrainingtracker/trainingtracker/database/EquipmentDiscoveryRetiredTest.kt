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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying that EquipmentAndSportTypeDiscoveryManager strictly excludes retired equipment
 * from hardware sensor linked equipment queries and identity auto-inference (REQ-UI-158, TST-UI-111, ATT-1118).
 */
class EquipmentDiscoveryRetiredTest {

    private lateinit var mockContext: Context
    private lateinit var mockSportTypeManager: SportTypeDatabaseManager
    private lateinit var mockActiveDevicesHelper: ActiveDevicesDbHelper
    private lateinit var mockEquipmentDbHelper: EquipmentDbHelper
    private lateinit var mockSportTypeEquipmentLinkHelper: SportTypeEquipmentLinkManager
    private lateinit var mockWorkoutSummariesManager: WorkoutSummariesDatabaseManager

    private lateinit var manager: EquipmentAndSportTypeDiscoveryManager

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockSportTypeManager = mockk(relaxed = true)
        mockActiveDevicesHelper = mockk(relaxed = true)
        mockEquipmentDbHelper = mockk(relaxed = true)
        mockSportTypeEquipmentLinkHelper = mockk(relaxed = true)
        mockWorkoutSummariesManager = mockk(relaxed = true)

        manager = EquipmentAndSportTypeDiscoveryManager(
            context = mockContext,
            sportTypeManager = mockSportTypeManager,
            activeDevicesHelper = mockActiveDevicesHelper,
            equipmentDbHelper = mockEquipmentDbHelper,
            sportTypeEquipmentLinkHelper = mockSportTypeEquipmentLinkHelper,
            workoutSummariesManager = mockWorkoutSummariesManager
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetLinkedEquipmentIds_excludesRetiredEquipment() {
        val deviceId = 101L
        every { mockEquipmentDbHelper.getLinkedEquipmentIdsFromDeviceId(deviceId) } returns listOf(1L, 2L)
        every { mockEquipmentDbHelper.isEquipmentRetired(1L) } returns false
        every { mockEquipmentDbHelper.isEquipmentRetired(2L) } returns true

        val linkedIds = manager.getLinkedEquipmentIds(setOf(deviceId))

        assertEquals(setOf(1L), linkedIds)
        assertFalse("Retired equipment ID must be excluded", linkedIds.contains(2L))
    }

    @Test
    fun testGetEquipmentNamesForSport_excludesRetiredEquipment() {
        val sportName = "Cycling"
        val sportId = 10L
        every { mockSportTypeManager.getSportTypeIdFromUIName(sportName) } returns sportId
        every { mockSportTypeEquipmentLinkHelper.getEquipmentIdsForSport(sportId) } returns listOf(1L, 2L)
        every { mockEquipmentDbHelper.isEquipmentRetired(1L) } returns false
        every { mockEquipmentDbHelper.isEquipmentRetired(2L) } returns true
        every { mockEquipmentDbHelper.getEquipmentNameFromId(1L) } returns "Active Canyon"

        val names = manager.getEquipmentNamesForSport(sportName)

        assertEquals(setOf("Active Canyon"), names)
        verify(exactly = 0) { mockEquipmentDbHelper.getEquipmentNameFromId(2L) }
    }

    @Test
    fun testResolveIdentity_whenCandidateIsRetired_doesNotInclineRetiredEquipment() {
        val deviceIds = emptySet<Long>()
        val sportId = 10L

        every { mockSportTypeManager.getSportTypesIdList(BSportType.BIKE) } returns listOf(sportId)
        every { mockSportTypeEquipmentLinkHelper.getEquipmentIdsForSport(sportId) } returns listOf(99L)
        every { mockEquipmentDbHelper.isEquipmentRetired(99L) } returns true
        every { mockSportTypeManager.getStravaName(sportId) } returns "Ride"
        every { mockSportTypeManager.getBSportType(sportId) } returns BSportType.BIKE

        val result = manager.resolveIdentity(deviceIds, BSportType.BIKE, 0.0)

        assertEquals(-1L, result.equipmentId)
    }
}
