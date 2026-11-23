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
import org.junit.Assert.assertFalse
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

    // TST-FIL-029.4: Rule 81 CADENCE/STRIDES reachability includes SPEED_AND_LOCATION_ATT_FUSED without leaking into CALORIES
    @Test
    fun testCadenceAndStridesMappings_includeAttFusedWithoutPollutingCalories_tstFil029_4() {
        val cadenceDevices = DeviceType.getDeviceTypeList(SensorType.CADENCE)
        assertTrue(
            "CADENCE mapping must include SPEED_AND_LOCATION_ATT_FUSED",
            cadenceDevices != null && cadenceDevices.contains(DeviceType.SPEED_AND_LOCATION_ATT_FUSED)
        )

        val stridesDevices = DeviceType.getDeviceTypeList(SensorType.STRIDES)
        assertTrue(
            "STRIDES mapping must include SPEED_AND_LOCATION_ATT_FUSED",
            stridesDevices != null && stridesDevices.contains(DeviceType.SPEED_AND_LOCATION_ATT_FUSED)
        )

        val caloriesDevices = DeviceType.getDeviceTypeList(SensorType.CALORIES)
        assertFalse(
            "CALORIES mapping must stay isolated to RUN_SPEED",
            caloriesDevices != null && caloriesDevices.contains(DeviceType.SPEED_AND_LOCATION_ATT_FUSED)
        )
    }

    // TST-CON-057.1: CADENCE/STRIDES include DeviceType.HRM (capability-gated in DevicesDatabaseManager),
    // VERTICAL_OSCILLATION/GROUND_CONTACT_TIME retain DeviceType.HRM, and SLOPE/VAM/ASCENT/DESCENT exclude BIKE_POWER
    @Test
    fun testPolarHrmCadenceStridesAndBikePowerRemovedFromSlopeVamAscentDescent_TST_CON_057_1() {
        val cadenceDevices = DeviceType.getDeviceTypeList(SensorType.CADENCE)
        assertTrue("CADENCE must include DeviceType.HRM", cadenceDevices != null && cadenceDevices.contains(DeviceType.HRM))
        assertTrue("CADENCE must retain BIKE_CADENCE", cadenceDevices != null && cadenceDevices.contains(DeviceType.BIKE_CADENCE))
        assertTrue("CADENCE must retain BIKE_POWER", cadenceDevices != null && cadenceDevices.contains(DeviceType.BIKE_POWER))
        assertTrue("CADENCE must retain RUN_SPEED", cadenceDevices != null && cadenceDevices.contains(DeviceType.RUN_SPEED))

        val stridesDevices = DeviceType.getDeviceTypeList(SensorType.STRIDES)
        assertTrue("STRIDES must include DeviceType.HRM", stridesDevices != null && stridesDevices.contains(DeviceType.HRM))
        assertTrue("STRIDES must retain RUN_SPEED", stridesDevices != null && stridesDevices.contains(DeviceType.RUN_SPEED))

        val voDevices = DeviceType.getDeviceTypeList(SensorType.VERTICAL_OSCILLATION)
        val gctDevices = DeviceType.getDeviceTypeList(SensorType.GROUND_CONTACT_TIME)
        assertTrue("VERTICAL_OSCILLATION must include DeviceType.HRM", voDevices != null && voDevices.contains(DeviceType.HRM))
        assertTrue("GROUND_CONTACT_TIME must include DeviceType.HRM", gctDevices != null && gctDevices.contains(DeviceType.HRM))

        val slopeAndVamMetrics = listOf(
            SensorType.SLOPE,
            SensorType.VERTICAL_SPEED,
            SensorType.ASCENT,
            SensorType.DESCENT
        )
        for (sensor in slopeAndVamMetrics) {
            val deviceTypes = DeviceType.getDeviceTypeList(sensor)
            assertFalse(
                "$sensor must NOT include DeviceType.BIKE_POWER (REQ-CON-057.1)",
                deviceTypes != null && deviceTypes.contains(DeviceType.BIKE_POWER)
            )
            assertTrue(
                "$sensor must retain VERTICAL_SPEED_AND_SLOPE",
                deviceTypes != null && deviceTypes.contains(DeviceType.VERTICAL_SPEED_AND_SLOPE)
            )
            assertTrue(
                "$sensor must retain ALTITUDE_AND_SLOPE_ATT_FUSED",
                deviceTypes != null && deviceTypes.contains(DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED)
            )
        }
    }
}
