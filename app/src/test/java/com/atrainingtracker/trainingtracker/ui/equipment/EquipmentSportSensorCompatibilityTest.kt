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

import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification suite for sport-specific sensor classification and compatibility helpers
 * (REQ-UI-256, TST-UI-230, ATT-2382).
 */
class EquipmentSportSensorCompatibilityTest {

    @Test
    fun testIsBikeSensor_cyclingSensorsClassification() {
        assertTrue("BIKE_POWER must be classified as bike sensor", DevicesDatabaseManager.isBikeSensor(DeviceType.BIKE_POWER))
        assertTrue("BIKE_SPEED must be classified as bike sensor", DevicesDatabaseManager.isBikeSensor(DeviceType.BIKE_SPEED))
        assertTrue("BIKE_CADENCE must be classified as bike sensor", DevicesDatabaseManager.isBikeSensor(DeviceType.BIKE_CADENCE))
        assertTrue("BIKE_SPEED_AND_CADENCE must be classified as bike sensor", DevicesDatabaseManager.isBikeSensor(DeviceType.BIKE_SPEED_AND_CADENCE))

        // Running sensors must not be classified as bike sensor
        assertFalse("RUN_SPEED must not be bike sensor", DevicesDatabaseManager.isBikeSensor(DeviceType.RUN_SPEED))
        assertFalse("HRM is not exclusively a bike sensor", DevicesDatabaseManager.isBikeSensor(DeviceType.HRM))
        assertFalse("Null must return false", DevicesDatabaseManager.isBikeSensor(null))
    }

    @Test
    fun testIsRunSensor_runningSensorsClassification() {
        assertTrue("RUN_SPEED must be classified as run sensor", DevicesDatabaseManager.isRunSensor(DeviceType.RUN_SPEED))

        // Cycling sensors must not be classified as run sensor
        assertFalse("BIKE_POWER must not be run sensor", DevicesDatabaseManager.isRunSensor(DeviceType.BIKE_POWER))
        assertFalse("BIKE_SPEED must not be run sensor", DevicesDatabaseManager.isRunSensor(DeviceType.BIKE_SPEED))
        assertFalse("BIKE_CADENCE must not be run sensor", DevicesDatabaseManager.isRunSensor(DeviceType.BIKE_CADENCE))
        assertFalse("HRM is not exclusively a run sensor", DevicesDatabaseManager.isRunSensor(DeviceType.HRM))
        assertFalse("Null must return false", DevicesDatabaseManager.isRunSensor(null))
    }

    @Test
    fun testIsSharedSensor_universalSensorsClassification() {
        assertTrue("HRM must be classified as shared sensor", DevicesDatabaseManager.isSharedSensor(DeviceType.HRM))
        assertTrue("ENVIRONMENT must be classified as shared sensor", DevicesDatabaseManager.isSharedSensor(DeviceType.ENVIRONMENT))

        // Single-sport sensors must not be classified as shared
        assertFalse("BIKE_POWER must not be shared sensor", DevicesDatabaseManager.isSharedSensor(DeviceType.BIKE_POWER))
        assertFalse("BIKE_SPEED must not be shared sensor", DevicesDatabaseManager.isSharedSensor(DeviceType.BIKE_SPEED))
        assertFalse("RUN_SPEED must not be shared sensor", DevicesDatabaseManager.isSharedSensor(DeviceType.RUN_SPEED))
        assertFalse("Null must return false", DevicesDatabaseManager.isSharedSensor(null))
    }

    @Test
    fun testSimpleSensorInfo_deviceTypeEncapsulation() {
        val sensorWithoutType = DevicesDatabaseManager.SimpleSensorInfo(1L, "Generic")
        val sensorWithType = DevicesDatabaseManager.SimpleSensorInfo(2L, "PowerMeter", DeviceType.BIKE_POWER)

        org.junit.Assert.assertNull(sensorWithoutType.deviceType)
        assertEquals(DeviceType.BIKE_POWER, sensorWithType.deviceType)
    }

    private fun assertEquals(expected: Any?, actual: Any?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
