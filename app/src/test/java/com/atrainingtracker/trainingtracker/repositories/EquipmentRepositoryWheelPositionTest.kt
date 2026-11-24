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

    // REQ-SEN-035.3 / TST-SEN-035.3: addEquipment & updateEquipment with explicit wheelPositions persist swaps and preserve untouched sensors
    @Test
    fun testAddAndUpdateEquipmentWithWheelPositions_persistsSwapsAndPreservesUntouched_TST_SEN_035_3() {
        val frontSensorId = 101L
        val rearSensorId = 102L
        val hrmSensorId = 201L
        val powerSensorId = 202L

        // 1. Add bike with explicit FRONT / REAR positions + non-wheel HRM
        val bikeId = repository.addEquipment(
            name = "Gravel Bike",
            frameType = EquipmentDbHelper.CROSS,
            linkedDeviceIds = listOf(frontSensorId, rearSensorId, hrmSensorId),
            wheelPositions = mapOf(
                frontSensorId to WheelPosition.FRONT,
                rearSensorId to WheelPosition.REAR
            )
        )
        var positions = repository.getDeviceWheelPositionsForEquipment(bikeId)
        assertEquals(WheelPosition.FRONT, positions[frontSensorId])
        assertEquals(WheelPosition.REAR, positions[rearSensorId])
        assertEquals(WheelPosition.UNSPECIFIED, positions[hrmSensorId])

        // 2. Update bike swapping FRONT <-> REAR atomically
        repository.updateEquipment(
            id = bikeId,
            name = "Gravel Bike v2",
            frameType = EquipmentDbHelper.CROSS,
            linkedDeviceIds = listOf(frontSensorId, rearSensorId, hrmSensorId),
            isRetired = false,
            wheelPositions = mapOf(
                frontSensorId to WheelPosition.REAR,
                rearSensorId to WheelPosition.FRONT
            )
        )
        positions = repository.getDeviceWheelPositionsForEquipment(bikeId)
        assertEquals(WheelPosition.REAR, positions[frontSensorId])
        assertEquals(WheelPosition.FRONT, positions[rearSensorId])
        assertEquals(WheelPosition.UNSPECIFIED, positions[hrmSensorId])

        // 3. Partial map update adding power sensor preserves untouched wheel sensor positions
        repository.updateEquipment(
            id = bikeId,
            name = "Gravel Bike v3",
            frameType = EquipmentDbHelper.CROSS,
            linkedDeviceIds = listOf(frontSensorId, rearSensorId, hrmSensorId, powerSensorId),
            isRetired = false,
            wheelPositions = mapOf(powerSensorId to WheelPosition.UNSPECIFIED)
        )
        positions = repository.getDeviceWheelPositionsForEquipment(bikeId)
        assertEquals(WheelPosition.REAR, positions[frontSensorId])
        assertEquals(WheelPosition.FRONT, positions[rearSensorId])
        assertEquals(WheelPosition.UNSPECIFIED, positions[powerSensorId])

        // 4. Legacy 5-arg updateEquipment preserves existing wheel positions too
        repository.updateEquipment(
            id = bikeId,
            name = "Gravel Bike v4",
            frameType = EquipmentDbHelper.CROSS,
            linkedDeviceIds = listOf(frontSensorId, rearSensorId),
            isRetired = false
        )
        positions = repository.getDeviceWheelPositionsForEquipment(bikeId)
        assertEquals(WheelPosition.REAR, positions[frontSensorId])
        assertEquals(WheelPosition.FRONT, positions[rearSensorId])
        assertNull(positions[hrmSensorId])
    }
}
