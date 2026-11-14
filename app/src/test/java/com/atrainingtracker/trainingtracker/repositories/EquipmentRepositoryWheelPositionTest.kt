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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.repositories

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WheelPosition
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit test suite verifying EquipmentRepository wheel position assignment, conflict resolution,
 * and bike duplicate guardrails (REQ-SEN-029, TST-SEN-029.2).
 */
@RunWith(RobolectricTestRunner::class)
class EquipmentRepositoryWheelPositionTest {

    private lateinit var context: Context
    private lateinit var dbHelper: EquipmentDbHelper
    private lateinit var repository: EquipmentRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbHelper = EquipmentDbHelper(context)
        repository = EquipmentRepository(context, dbHelper)
    }

    @After
    fun tearDown() {
        EquipmentRepository.resetForTesting(null)
    }

    @Test
    fun testWheelSensorLinkAndConflictResolution() {
        val bikeId = dbHelper.addEquipment("Aero Road Bike", EquipmentDbHelper.ROAD, emptyList())

        val sensor1Id = 101L
        val sensor2Id = 102L
        val sensor3Id = 103L

        // 1. Assign sensor 1 to FRONT
        repository.setWheelSensorLink(bikeId, sensor1Id, WheelPosition.FRONT)
        var positions = repository.getDeviceWheelPositionsForEquipment(bikeId)
        assertEquals(WheelPosition.FRONT, positions[sensor1Id])

        // 2. Assign sensor 2 to FRONT -> Conflict! Sensor 1 was FRONT and REAR is open, so Sensor 1 swaps to REAR
        repository.setWheelSensorLink(bikeId, sensor2Id, WheelPosition.FRONT)
        positions = repository.getDeviceWheelPositionsForEquipment(bikeId)
        assertEquals(WheelPosition.FRONT, positions[sensor2Id])
        assertEquals(WheelPosition.REAR, positions[sensor1Id])

        // 3. Assign sensor 3 to REAR -> Conflict with sensor 1 (which was REAR). Sensor 2 is already FRONT, so Sensor 1 becomes UNSPECIFIED
        repository.setWheelSensorLink(bikeId, sensor3Id, WheelPosition.REAR)
        positions = repository.getDeviceWheelPositionsForEquipment(bikeId)
        assertEquals(WheelPosition.FRONT, positions[sensor2Id])
        assertEquals(WheelPosition.REAR, positions[sensor3Id])
        assertEquals(WheelPosition.UNSPECIFIED, positions[sensor1Id])

        // 4. Update sensor position directly
        repository.updateDeviceLinkWheelPosition(bikeId, sensor1Id, WheelPosition.UNSPECIFIED)
        assertEquals(WheelPosition.UNSPECIFIED, repository.getWheelPosition(bikeId, sensor1Id))

        // 5. Unlink sensor
        repository.setDeviceLink(bikeId, sensor1Id, false)
        assertNull(repository.getWheelPosition(bikeId, sensor1Id))
    }
}
