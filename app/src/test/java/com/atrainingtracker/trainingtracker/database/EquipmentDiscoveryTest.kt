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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying real [EquipmentAndSportTypeDiscoveryManager.resolveIdentity]
 * logic across active device ID sets and speed brackets (TST-UNT-003, REQ-TRK-004, REQ-SET-003).
 */
class EquipmentDiscoveryTest {

    @After
    fun tearDown() {
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testResolveIdentity_UniqueDeviceResolvesLinkedEquipmentAndSport_TST_UNT_003() {
        // TST-UNT-003
        val mockContext = mockk<Context>(relaxed = true)
        val mockSportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
        val mockActiveDevices = mockk<ActiveDevicesDbHelper>(relaxed = true)
        val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
        val mockLinkMgr = mockk<SportTypeEquipmentLinkManager>(relaxed = true)
        val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)

        every { mockEquipmentDb.getLinkedEquipmentIdsFromDeviceId(1L) } returns listOf(77L)
        every { mockEquipmentDb.isEquipmentRetired(77L) } returns false
        every { mockLinkMgr.getSportTypeIdsForEquipment(77L) } returns listOf(101L)
        every { mockSportDb.getStravaName(101L) } returns "Ride"
        every { mockSportDb.getBSportType(101L) } returns BSportType.BIKE

        val manager = EquipmentAndSportTypeDiscoveryManager(
            context = mockContext,
            sportTypeManager = mockSportDb,
            activeDevicesHelper = mockActiveDevices,
            equipmentDbHelper = mockEquipmentDb,
            sportTypeEquipmentLinkHelper = mockLinkMgr,
            workoutSummariesManager = mockSummariesDb
        )

        val result = manager.resolveIdentity(setOf(1L), BSportType.BIKE, 8.0)

        assertEquals(77L, result.equipmentId)
        assertEquals(101L, result.sportId)
        assertEquals(BSportType.BIKE, result.bSportType)
        assertEquals("Ride", result.stravaSportName)
        assertEquals(1, result.uploadToStrava)
        assertTrue(result.isHighConfidence)
    }

    @Test
    fun testResolveIdentity_NoDeviceUsesSpeedBasedFallbackAndUnlinkedEquipment_TST_UNT_003() {
        // TST-UNT-003
        val mockContext = mockk<Context>(relaxed = true)
        val mockSportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
        val mockActiveDevices = mockk<ActiveDevicesDbHelper>(relaxed = true)
        val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
        val mockLinkMgr = mockk<SportTypeEquipmentLinkManager>(relaxed = true)
        val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)

        every { mockSportDb.getSportTypesIdList(BSportType.RUN, 3.0) } returns listOf(202L)
        every { mockSportDb.getSportTypesIdList(BSportType.RUN) } returns listOf(201L, 202L)
        every { mockSportDb.getStravaName(202L) } returns null
        every { mockSportDb.getBSportType(202L) } returns BSportType.RUN
        every { mockLinkMgr.getEquipmentIdsForSport(202L) } returns emptyList()

        val manager = EquipmentAndSportTypeDiscoveryManager(
            context = mockContext,
            sportTypeManager = mockSportDb,
            activeDevicesHelper = mockActiveDevices,
            equipmentDbHelper = mockEquipmentDb,
            sportTypeEquipmentLinkHelper = mockLinkMgr,
            workoutSummariesManager = mockSummariesDb
        )

        val result = manager.resolveIdentity(emptySet(), BSportType.RUN, 3.0)

        assertEquals(202L, result.sportId)
        assertEquals(-1L, result.equipmentId)
        assertEquals(BSportType.RUN, result.bSportType)
        assertEquals(null, result.stravaSportName)
        assertEquals(0, result.uploadToStrava)
        assertFalse(result.isHighConfidence)
    }
}
