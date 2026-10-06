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

package com.atrainingtracker.trainingtracker.ui.equipment

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.SimpleSensorInfo
import com.atrainingtracker.banalservice.devices.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit verification suite for [EquipmentSensorOrdering] (REQ-UI-283, TST-UI-243.1).
 */
class EquipmentSensorOrderingTest {

    @Test
    fun testBikeSensorOrdering_sportSpecificBeforeShared() {
        val sensors = listOf(
            SimpleSensorInfo(1L, "Garmin Tempe", DeviceType.ENVIRONMENT),
            SimpleSensorInfo(2L, "Garmin HRM-Dual", DeviceType.HRM),
            SimpleSensorInfo(3L, "Stages Power", DeviceType.BIKE_POWER),
            SimpleSensorInfo(4L, "Wahoo SPEED", DeviceType.BIKE_SPEED)
        )

        val sorted = EquipmentSensorOrdering.sortSensors(sensors, BSportType.BIKE)
        val sortedNames = sorted.map { it.name }

        assertEquals(
            listOf("Stages Power", "Wahoo SPEED", "Garmin HRM-Dual", "Garmin Tempe"),
            sortedNames
        )
    }

    @Test
    fun testBikeSensorOrdering_cyclingPriorityTiers() {
        val sensors = listOf(
            SimpleSensorInfo(1L, "Cadence Sensor", DeviceType.BIKE_CADENCE),
            SimpleSensorInfo(2L, "Speed Sensor", DeviceType.BIKE_SPEED),
            SimpleSensorInfo(3L, "Dual Speed & Cadence", DeviceType.BIKE_SPEED_AND_CADENCE),
            SimpleSensorInfo(4L, "Power Meter", DeviceType.BIKE_POWER)
        )

        val sorted = EquipmentSensorOrdering.sortSensors(sensors, BSportType.BIKE)
        val sortedTypes = sorted.map { it.deviceType }

        assertEquals(
            listOf(
                DeviceType.BIKE_POWER,
                DeviceType.BIKE_SPEED_AND_CADENCE,
                DeviceType.BIKE_SPEED,
                DeviceType.BIKE_CADENCE
            ),
            sortedTypes
        )
    }

    @Test
    fun testShoeSensorOrdering_sportSpecificBeforeShared() {
        val sensors = listOf(
            SimpleSensorInfo(1L, "Garmin Tempe", DeviceType.ENVIRONMENT),
            SimpleSensorInfo(2L, "Garmin HRM-Dual", DeviceType.HRM),
            SimpleSensorInfo(3L, "Stryd Footpod", DeviceType.RUN_SPEED)
        )

        val sorted = EquipmentSensorOrdering.sortSensors(sensors, BSportType.RUN)
        val sortedNames = sorted.map { it.name }

        assertEquals(
            listOf("Stryd Footpod", "Garmin HRM-Dual", "Garmin Tempe"),
            sortedNames
        )
    }

    @Test
    fun testSecondaryAlphabeticalSorting_caseInsensitive() {
        val sensors = listOf(
            SimpleSensorInfo(1L, "stages power meter", DeviceType.BIKE_POWER),
            SimpleSensorInfo(2L, "4iiii Precision", DeviceType.BIKE_POWER),
            SimpleSensorInfo(3L, "Favero Assioma", DeviceType.BIKE_POWER)
        )

        val sorted = EquipmentSensorOrdering.sortSensors(sensors, BSportType.BIKE)
        val sortedNames = sorted.map { it.name }

        assertEquals(
            listOf("4iiii Precision", "Favero Assioma", "stages power meter"),
            sortedNames
        )
    }

    @Test
    fun testTertiaryIdSorting() {
        val sensors = listOf(
            SimpleSensorInfo(50L, "Identical Name", DeviceType.HRM),
            SimpleSensorInfo(20L, "Identical Name", DeviceType.HRM),
            SimpleSensorInfo(30L, "Identical Name", DeviceType.HRM)
        )

        val sorted = EquipmentSensorOrdering.sortSensors(sensors, BSportType.BIKE)
        val sortedIds = sorted.map { it.id }

        assertEquals(listOf(20L, 30L, 50L), sortedIds)
    }

    @Test
    fun testNullDeviceType_placedAtEnd() {
        val sensors = listOf(
            SimpleSensorInfo(1L, "Unknown Sensor", null),
            SimpleSensorInfo(2L, "Stages Power", DeviceType.BIKE_POWER),
            SimpleSensorInfo(3L, "Polar H10", DeviceType.HRM)
        )

        val sorted = EquipmentSensorOrdering.sortSensors(sensors, BSportType.BIKE)
        val sortedNames = sorted.map { it.name }

        assertEquals(
            listOf("Stages Power", "Polar H10", "Unknown Sensor"),
            sortedNames
        )
    }

    @Test
    fun testPriorityRank_valuesMatchSpecification() {
        // Bike priority
        assertTrue(EquipmentSensorOrdering.getPriorityRank(DeviceType.BIKE_POWER, BSportType.BIKE) <
                EquipmentSensorOrdering.getPriorityRank(DeviceType.BIKE_SPEED_AND_CADENCE, BSportType.BIKE))
        assertTrue(EquipmentSensorOrdering.getPriorityRank(DeviceType.BIKE_SPEED_AND_CADENCE, BSportType.BIKE) <
                EquipmentSensorOrdering.getPriorityRank(DeviceType.BIKE_SPEED, BSportType.BIKE))
        assertTrue(EquipmentSensorOrdering.getPriorityRank(DeviceType.BIKE_SPEED, BSportType.BIKE) <
                EquipmentSensorOrdering.getPriorityRank(DeviceType.BIKE_CADENCE, BSportType.BIKE))
        assertTrue(EquipmentSensorOrdering.getPriorityRank(DeviceType.BIKE_CADENCE, BSportType.BIKE) <
                EquipmentSensorOrdering.getPriorityRank(DeviceType.HRM, BSportType.BIKE))
        assertTrue(EquipmentSensorOrdering.getPriorityRank(DeviceType.HRM, BSportType.BIKE) <
                EquipmentSensorOrdering.getPriorityRank(DeviceType.ENVIRONMENT, BSportType.BIKE))

        // Run priority
        assertTrue(EquipmentSensorOrdering.getPriorityRank(DeviceType.RUN_SPEED, BSportType.RUN) <
                EquipmentSensorOrdering.getPriorityRank(DeviceType.HRM, BSportType.RUN))
        assertTrue(EquipmentSensorOrdering.getPriorityRank(DeviceType.HRM, BSportType.RUN) <
                EquipmentSensorOrdering.getPriorityRank(DeviceType.ENVIRONMENT, BSportType.RUN))
    }
}
