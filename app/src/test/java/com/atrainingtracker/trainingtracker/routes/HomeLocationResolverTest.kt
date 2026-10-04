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
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLocationResolverTest {

    @Test
    fun resolveHome_withExplicitNameHausOrHome_returnsNamedLocation() {
        val manager = mockk<KnownLocationsDatabaseManager>()
        val loc1 = MyLocation(1L, 48.5, 9.2, "Parkplatz Trailhead", 350.0, 200, 15)
        val loc2 = MyLocation(2L, 48.6, 9.3, "Zu Hause", 420.0, 200, 5)
        val loc3 = MyLocation(3L, 48.7, 9.4, "Büro", 300.0, 200, 2)

        every { manager.allLocations } returns listOf(loc1, loc2, loc3)

        val resolved = HomeLocationResolver.resolveHomeLocation(manager)
        assertNotNull(resolved)
        assertEquals(2L, resolved?.id)
        assertEquals("Zu Hause", resolved?.name)
        assertEquals(420.0, resolved?.altitude ?: 0.0, 0.001)
    }

    @Test
    fun resolveHome_withEnglishHomeKeyword_returnsNamedLocation() {
        val manager = mockk<KnownLocationsDatabaseManager>()
        val loc1 = MyLocation(1L, 48.5, 9.2, "Coffee Shop", 350.0, 200, 10)
        val loc2 = MyLocation(2L, 48.6, 9.3, "My Home Base", 450.0, 200, 1)

        every { manager.allLocations } returns listOf(loc1, loc2)

        val resolved = HomeLocationResolver.resolveHomeLocation(manager)
        assertNotNull(resolved)
        assertEquals(2L, resolved?.id)
        assertEquals("My Home Base", resolved?.name)
    }

    @Test
    fun resolveHome_withoutExplicitName_returnsLocationWithHighestHitCount() {
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
}
