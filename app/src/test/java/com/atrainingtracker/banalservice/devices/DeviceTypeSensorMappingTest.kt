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

package com.atrainingtracker.banalservice.devices

import com.atrainingtracker.banalservice.sensor.SensorType
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying DeviceType sensor attribution mappings (REQ-CON-030.5, TST-CON-030.5).
 */
class DeviceTypeSensorMappingTest {

    @Test
    fun testRadarSensorMappings_allRadarSensorsMapToBikeRadar() {
        val radarSensors = listOf(
            SensorType.RADAR_THREAT_COUNT,
            SensorType.RADAR_CLOSEST_DISTANCE_m,
            SensorType.RADAR_APPROACH_SPEED_mps,
            SensorType.RADAR_VEHICLE_COUNT,
            SensorType.RADAR_PASSING_DISTANCE_m,
            SensorType.RADAR_LARGE_VEHICLE_COUNT
        )

        for (sensor in radarSensors) {
            val deviceTypes = DeviceType.getDeviceTypeList(sensor)
            assertTrue(
                "Sensor $sensor must be mapped to DeviceType.BIKE_RADAR",
                deviceTypes != null && deviceTypes.contains(DeviceType.BIKE_RADAR)
            )
        }
    }
}
