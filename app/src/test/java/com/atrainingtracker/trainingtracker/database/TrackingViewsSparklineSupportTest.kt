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

import com.atrainingtracker.banalservice.sensor.SensorType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying numeric metric eligibility gating for live sparkline rendering (TST-TRK-059.2, REQ-TRK-059.2).
 */
class TrackingViewsSparklineSupportTest {

    @Test
    fun testIsGraphSupported_returnsTrueForNumericMetrics() {
        val supportedNumericMetrics = listOf(
            SensorType.HR,
            SensorType.SPEED_mps,
            SensorType.CADENCE,
            SensorType.POWER,
            SensorType.POWER_TO_WEIGHT,
            SensorType.NORMALIZED_POWER,
            SensorType.INTENSITY_FACTOR,
            SensorType.WORK_KJ,
            SensorType.TSS,
            SensorType.W_PRIME_BAL,
            SensorType.POWER_5S,
            SensorType.POWER_1M,
            SensorType.POWER_5M,
            SensorType.POWER_20M,
            SensorType.POWER_PEAK_5S,
            SensorType.POWER_PEAK_1M,
            SensorType.POWER_PEAK_5M,
            SensorType.POWER_PEAK_20M,
            SensorType.ALTITUDE,
            SensorType.ASCENT,
            SensorType.DESCENT,
            SensorType.SLOPE,
            SensorType.TEMPERATURE,
            SensorType.PHONE_BATTERY,
            SensorType.BATTERY_REMAINING_TIME
        )

        for (metric in supportedNumericMetrics) {
            assertTrue(
                "Metric ${metric.name} should support sparkline graph",
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.isGraphSupported(metric)
            )
        }
    }

    @Test
    fun testIsGraphSupported_returnsFalseForNonNumericAndCoordinateMetrics() {
        val unsupportedMetrics = listOf(
            SensorType.ACCUMULATED_SENSORS,
            SensorType.SENSORS,
            SensorType.TIME_OF_DAY,
            SensorType.TIME_TOTAL,
            SensorType.TIME_ACTIVE,
            SensorType.TIME_LAP,
            SensorType.LAP_NR,
            SensorType.LATITUDE,
            SensorType.LONGITUDE
        )

        for (metric in unsupportedMetrics) {
            assertFalse(
                "Metric ${metric.name} should NOT support sparkline graph",
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.isGraphSupported(metric)
            )
        }

        assertFalse(
            "Null sensor type must return false",
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.isGraphSupported(null)
        )
    }
}
