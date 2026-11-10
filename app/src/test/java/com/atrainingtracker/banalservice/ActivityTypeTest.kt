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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ActivityType.getSensorTypeArray] catalog completeness conforming to REQ-TRK-061.1 and TST-TRK-061.1.
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
            assertTrue("$activity must contain HRV_DFA_A1", sensors.contains(SensorType.HRV_DFA_A1))
            assertTrue("$activity must contain HRV_RMSSD", sensors.contains(SensorType.HRV_RMSSD))
            assertTrue("$activity must contain RR_INTERVAL", sensors.contains(SensorType.RR_INTERVAL))
            assertTrue("$activity must contain HRV_ARTIFACT_RATE", sensors.contains(SensorType.HRV_ARTIFACT_RATE))
        }
    }
}
