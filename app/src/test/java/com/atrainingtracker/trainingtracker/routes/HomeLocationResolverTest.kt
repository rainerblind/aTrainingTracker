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

package com.atrainingtracker.trainingtracker.routes

import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.MyLocation
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit test verifying HomeLocationResolver behavior and false positive prevention (REQ-MAP-034, TST-MAP-036.2).
 */
class HomeLocationResolverTest {

    @Test
    fun resolveHome_withDesignatedHomeLocation_returnsDesignatedLocation() {
        val manager = mockk<KnownLocationsDatabaseManager>()
        val loc1 = MyLocation(1L, 48.5, 9.2, "Gasthaus Hirsch", 350.0, 200, 50, false, ElevationSource.LEGACY_RAW, false)
        val loc2 = MyLocation(2L, 48.6, 9.3, "Mein Apartment", 420.0, 200, 5, true, ElevationSource.MANUAL_USER, true)
        val loc3 = MyLocation(3L, 48.7, 9.4, "Büro", 300.0, 200, 10, false, ElevationSource.LEGACY_RAW, false)

        every { manager.allLocations } returns listOf(loc1, loc2, loc3)

        val resolved = HomeLocationResolver.resolveHomeLocation(manager)
        assertNotNull(resolved)
        assertEquals(2L, resolved?.id)
        assertEquals("Mein Apartment", resolved?.name)
        assertEquals(420.0, resolved?.altitude ?: 0.0, 0.001)
    }

    @Test
    fun resolveHome_withoutDesignatedHome_eliminatesSubstringFalsePositives_returnsHighestHitCount() {
        val manager = mockk<KnownLocationsDatabaseManager>()
        val loc1 = MyLocation(1L, 48.5, 9.2, "Rathausplatz", 350.0, 200, 5, false, ElevationSource.LEGACY_RAW, false)
        val loc2 = MyLocation(2L, 48.6, 9.3, "Trailhead Parkplatz", 450.0, 200, 28, false, ElevationSource.LEGACY_RAW, false)
        val loc3 = MyLocation(3L, 48.7, 9.4, "Gasthaus Krone", 400.0, 200, 2, false, ElevationSource.LEGACY_RAW, false)

        every { manager.allLocations } returns listOf(loc1, loc2, loc3)

        val resolved = HomeLocationResolver.resolveHomeLocation(manager)
        assertNotNull(resolved)
        assertEquals(2L, resolved?.id)
        assertEquals("Trailhead Parkplatz", resolved?.name)
    }

    @Test
    fun resolveHome_withoutDesignatedHome_returnsLocationWithHighestHitCount() {
        val manager = mockk<KnownLocationsDatabaseManager>()
        val loc1 = MyLocation(10L, 48.5, 9.2, "Start Spot A", 350.0, 200, 4)
        val loc2 = MyLocation(20L, 48.6, 9.3, "Start Spot B", 420.0, 200, 42)
        val loc3 = MyLocation(30L, 48.7, 9.4, "Start Spot C", 300.0, 200, 12)

        every { manager.allLocations } returns listOf(loc1, loc2, loc3)

        val resolved = HomeLocationResolver.resolveHomeLocation(manager)
        assertNotNull(resolved)
        assertEquals(20L, resolved?.id)
        assertEquals("Start Spot B", resolved?.name)
    }

    @Test
    fun resolveHome_withoutDesignatedHome_whenHitCountsAreZero_fallsBackToFirstLocation() {
        val manager = mockk<KnownLocationsDatabaseManager>()
        val loc1 = MyLocation(100L, 48.5, 9.2, "First Fallback", 350.0, 200, 0)
        val loc2 = MyLocation(200L, 48.6, 9.3, "Second Fallback", 420.0, 200, 0)

        every { manager.allLocations } returns listOf(loc1, loc2)

        val resolved = HomeLocationResolver.resolveHomeLocation(manager)
        assertNotNull(resolved)
        assertEquals(100L, resolved?.id)
        assertEquals("First Fallback", resolved?.name)
    }

    @Test
    fun resolveHome_withEmptyDatabase_returnsNull() {
        val manager = mockk<KnownLocationsDatabaseManager>()
        every { manager.allLocations } returns emptyList()

        val resolved = HomeLocationResolver.resolveHomeLocation(manager)
        assertNull(resolved)
    }

    @Test
    fun resolveHome_whenExceptionThrown_returnsNullSafely() {
        val manager = mockk<KnownLocationsDatabaseManager>()
        every { manager.allLocations } throws RuntimeException("DB locked")

        val resolved = HomeLocationResolver.resolveHomeLocation(manager)
        assertNull(resolved)
    }

    /**
     * TST-MAP-038.1: Unit tests for resolveHomeLocationId hierarchy.
     */
    @Test
    fun resolveHomeLocationId_withExplicitHome_returnsExplicitId() {
        val loc1 = MyLocation(10L, 48.5, 9.2, "Spot A", 300.0, 200, 100, false, ElevationSource.LEGACY_RAW, false)
        val loc2 = MyLocation(20L, 48.6, 9.3, "Spot B (Home)", 400.0, 200, 2, true, ElevationSource.MANUAL_USER, true)
        val loc3 = MyLocation(30L, 48.7, 9.4, "Spot C", 500.0, 200, 50, false, ElevationSource.LEGACY_RAW, false)

        val resolvedId = HomeLocationResolver.resolveHomeLocationId(listOf(loc1, loc2, loc3))
        assertEquals(20L, resolvedId)
    }

    @Test
    fun resolveHomeLocationId_withoutExplicitHome_returnsHighestHitCountId() {
        val loc1 = MyLocation(10L, 48.5, 9.2, "Spot A", 300.0, 200, 15)
        val loc2 = MyLocation(20L, 48.6, 9.3, "Spot B", 400.0, 200, 85)
        val loc3 = MyLocation(30L, 48.7, 9.4, "Spot C", 500.0, 200, 40)

        val resolvedId = HomeLocationResolver.resolveHomeLocationId(listOf(loc1, loc2, loc3))
        assertEquals(20L, resolvedId)
    }

    @Test
    fun resolveHomeLocationId_withoutExplicitHome_whenAllHitCountsZero_returnsFirstId() {
        val loc1 = MyLocation(101L, 48.5, 9.2, "Spot First", 300.0, 200, 0)
        val loc2 = MyLocation(102L, 48.6, 9.3, "Spot Second", 400.0, 200, 0)

        val resolvedId = HomeLocationResolver.resolveHomeLocationId(listOf(loc1, loc2))
        assertEquals(101L, resolvedId)
    }

    @Test
    fun resolveHomeLocationId_withEmptyList_returnsNull() {
        val resolvedId = HomeLocationResolver.resolveHomeLocationId(emptyList())
        assertNull(resolvedId)
    }
}
