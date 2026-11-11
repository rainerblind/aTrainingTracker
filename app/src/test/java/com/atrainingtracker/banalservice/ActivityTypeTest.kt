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

package com.atrainingtracker.banalservice

import com.atrainingtracker.banalservice.sensor.SensorType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ActivityType.getSensorTypeArray] catalog completeness conforming to REQ-TRK-061.1, REQ-TRK-070.3,
 * and TST-TRK-070.2.
 */
class ActivityTypeTest {

    @Test
    fun testGetSensorTypeArray_containsAllHrvMetricsAcrossActivities() {
        val activities = listOf(
            ActivityType.GENERIC_HR,
            ActivityType.RUN_SPEED_AND_CADENCE,
            ActivityType.BIKE_SPEED_AND_CADENCE,
            ActivityType.BIKE_POWER
        )

        activities.forEach { activity ->
            val sensors = ActivityType.getSensorTypeArray(activity, null).toList()
            assertTrue("${activity.name} must contain HRV_DFA_A1", sensors.contains(SensorType.HRV_DFA_A1))
            assertTrue("${activity.name} must contain HRV_RMSSD", sensors.contains(SensorType.HRV_RMSSD))
            assertTrue("${activity.name} must contain RR_INTERVAL", sensors.contains(SensorType.RR_INTERVAL))
            assertTrue("${activity.name} must contain HRV_ARTIFACT_RATE", sensors.contains(SensorType.HRV_ARTIFACT_RATE))
            assertTrue("${activity.name} must contain HRV_SDNN", sensors.contains(SensorType.HRV_SDNN))
            assertTrue("${activity.name} must contain PHONE_BATTERY", sensors.contains(SensorType.PHONE_BATTERY))
            assertTrue("${activity.name} must contain BATTERY_REMAINING_TIME", sensors.contains(SensorType.BATTERY_REMAINING_TIME))
        }
    }

    @Test
    fun testGetSensorTypeArray_bikePowerContainsAll8PowerDurationSensors() {
        val sensors = ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null).toList()
        val expectedPowerDuration = listOf(
            SensorType.POWER_5S,
            SensorType.POWER_1M,
            SensorType.POWER_5M,
            SensorType.POWER_20M,
            SensorType.POWER_PEAK_5S,
            SensorType.POWER_PEAK_1M,
            SensorType.POWER_PEAK_5M,
            SensorType.POWER_PEAK_20M
        )
        expectedPowerDuration.forEach { sensorType ->
            assertTrue("BIKE_POWER must contain ${sensorType.name}", sensors.contains(sensorType))
        }
    }

    @Test
    fun testGetSensorTypeArray_radarScopedToCyclingOnly() {
        val radarSensors = listOf(
            SensorType.RADAR_THREAT_COUNT,
            SensorType.RADAR_CLOSEST_DISTANCE_m,
            SensorType.RADAR_APPROACH_SPEED_mps,
            SensorType.RADAR_VEHICLE_COUNT,
            SensorType.RADAR_PASSING_DISTANCE_m,
            SensorType.RADAR_LARGE_VEHICLE_COUNT
        )

        // Cycling profiles must contain all 6 radar sensors
        val bikeActivities = listOf(
            ActivityType.BIKE_SPEED_AND_CADENCE,
            ActivityType.BIKE_POWER
        )
        bikeActivities.forEach { activity ->
            val sensors = ActivityType.getSensorTypeArray(activity, null).toList()
            radarSensors.forEach { radarType ->
                assertTrue("${activity.name} must contain ${radarType.name}", sensors.contains(radarType))
            }
        }

        // Non-cycling profiles must NOT contain radar sensors
        val nonBikeActivities = listOf(
            ActivityType.GENERIC_HR,
            ActivityType.RUN_SPEED_AND_CADENCE
        )
        nonBikeActivities.forEach { activity ->
            val sensors = ActivityType.getSensorTypeArray(activity, null).toList()
            radarSensors.forEach { radarType ->
                assertFalse("${activity.name} must NOT contain ${radarType.name}", sensors.contains(radarType))
            }
        }
    }

    @Test
    fun testGetSensorTypeArray_caloriesPresentAcrossAllActivities() {
        val activities = listOf(
            ActivityType.GENERIC_HR,
            ActivityType.RUN_SPEED_AND_CADENCE,
            ActivityType.BIKE_SPEED_AND_CADENCE,
            ActivityType.BIKE_POWER
        )
        activities.forEach { activity ->
            val sensors = ActivityType.getSensorTypeArray(activity, null).toList()
            assertTrue("${activity.name} must contain CALORIES", sensors.contains(SensorType.CALORIES))
        }
    }

    @Test
    fun testGetSensorTypeArray_athleticPriorityOrderingInBikePower() {
        val sensors = ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null).toList()
        val powerIndex = sensors.indexOf(SensorType.POWER)
        val balanceIndex = sensors.indexOf(SensorType.PEDAL_POWER_BALANCE)
        assertTrue("POWER must appear before PEDAL_POWER_BALANCE", powerIndex != -1 && balanceIndex != -1 && powerIndex < balanceIndex)

        val hrIndex = sensors.indexOf(SensorType.HR)
        val npIndex = sensors.indexOf(SensorType.NORMALIZED_POWER)
        assertTrue("HR must appear before NORMALIZED_POWER", hrIndex != -1 && npIndex != -1 && hrIndex < npIndex)
    }

    @Test
    fun testGetSensorTypeArray_liveHrDriftScopedStrictlyToBikePower() {
        val bikePowerSensors = ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null).toList()
        assertTrue("BIKE_POWER must contain LIVE_HR_DRIFT", bikePowerSensors.contains(SensorType.LIVE_HR_DRIFT))

        val nonPowerActivities = listOf(
            ActivityType.GENERIC_HR,
            ActivityType.RUN_SPEED_AND_CADENCE,
            ActivityType.BIKE_SPEED_AND_CADENCE
        )

        nonPowerActivities.forEach { activity ->
            val sensors = ActivityType.getSensorTypeArray(activity, null).toList()
            assertFalse("$activity must NOT contain LIVE_HR_DRIFT", sensors.contains(SensorType.LIVE_HR_DRIFT))
        }
    }
}
