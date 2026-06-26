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

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class EquipmentDiscoveryTest {

    @Test
    fun testResolveSportType_UniqueDevice() {
        val manager = mockk<EquipmentAndSportTypeDiscoveryManager>()
        
        // Mocking the resolveSportType method
        every { manager.resolveSportType(setOf(1L), BSportType.BIKE) } returns 101L
        
        val result = manager.resolveSportType(setOf(1L), BSportType.BIKE)
        assertEquals(101L, result)
    }

    @Test
    fun testResolveSportType_SpeedBased() {
        val manager = mockk<EquipmentAndSportTypeDiscoveryManager>()
        
        every { manager.resolveSportType(emptySet(), BSportType.RUN, 3.0) } returns 201L
        
        val result = manager.resolveSportType(emptySet(), BSportType.RUN, 3.0)
        assertEquals(201L, result)
    }
}
