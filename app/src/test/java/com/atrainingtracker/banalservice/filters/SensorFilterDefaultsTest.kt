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

package com.atrainingtracker.banalservice.filters

import com.atrainingtracker.banalservice.sensor.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying domain-specific default filter presets for sensor types
 * (ATT-1624, REQ-UI-198, TST-UI-152).
 */
class SensorFilterDefaultsTest {

    @Test
    fun testPowerSensor_defaultsToThreeSecondMovingAverage() {
        val config = SensorFilterDefaults.getDefaultFilterConfig(SensorType.POWER)
        assertEquals(FilterType.MOVING_AVERAGE_TIME, config.filterType)
        assertEquals(3.0, config.filterConstant, 0.001)
        assertEquals("sec", config.unit)

        assertEquals(FilterType.MOVING_AVERAGE_TIME, SensorFilterDefaults.getDefaultFilterType(SensorType.POWER))
        assertEquals(3.0, SensorFilterDefaults.getDefaultFilterConstant(SensorType.POWER), 0.001)
        assertEquals("sec", SensorFilterDefaults.getDefaultUnit(SensorType.POWER))
        assertEquals(config, SensorType.POWER.getDefaultFilterConfig())
    }

    @Test
    fun testPaceSensor_defaultsToFiveSecondMovingAverage() {
        val config = SensorFilterDefaults.getDefaultFilterConfig(SensorType.PACE_spm)
        assertEquals(FilterType.MOVING_AVERAGE_TIME, config.filterType)
        assertEquals(5.0, config.filterConstant, 0.001)
        assertEquals("sec", config.unit)

        assertEquals(FilterType.MOVING_AVERAGE_TIME, SensorFilterDefaults.getDefaultFilterType(SensorType.PACE_spm))
        assertEquals(5.0, SensorFilterDefaults.getDefaultFilterConstant(SensorType.PACE_spm), 0.001)
        assertEquals("sec", SensorFilterDefaults.getDefaultUnit(SensorType.PACE_spm))
        assertEquals(config, SensorType.PACE_spm.getDefaultFilterConfig())
    }

    @Test
    fun testVerticalSpeedSensor_defaultsToFifteenSecondMovingAverage() {
        val config = SensorFilterDefaults.getDefaultFilterConfig(SensorType.VERTICAL_SPEED)
        assertEquals(FilterType.MOVING_AVERAGE_TIME, config.filterType)
        assertEquals(15.0, config.filterConstant, 0.001)
        assertEquals("sec", config.unit)

        assertEquals(FilterType.MOVING_AVERAGE_TIME, SensorFilterDefaults.getDefaultFilterType(SensorType.VERTICAL_SPEED))
        assertEquals(15.0, SensorFilterDefaults.getDefaultFilterConstant(SensorType.VERTICAL_SPEED), 0.001)
        assertEquals("sec", SensorFilterDefaults.getDefaultUnit(SensorType.VERTICAL_SPEED))
        assertEquals(config, SensorType.VERTICAL_SPEED.getDefaultFilterConfig())
    }

    @Test
    fun testSlopeSensor_defaultsToFiveSecondMovingAverage() {
        val config = SensorFilterDefaults.getDefaultFilterConfig(SensorType.SLOPE)
        assertEquals(FilterType.MOVING_AVERAGE_TIME, config.filterType)
        assertEquals(5.0, config.filterConstant, 0.001)
        assertEquals("sec", config.unit)

        assertEquals(FilterType.MOVING_AVERAGE_TIME, SensorFilterDefaults.getDefaultFilterType(SensorType.SLOPE))
        assertEquals(5.0, SensorFilterDefaults.getDefaultFilterConstant(SensorType.SLOPE), 0.001)
        assertEquals("sec", SensorFilterDefaults.getDefaultUnit(SensorType.SLOPE))
        assertEquals(config, SensorType.SLOPE.getDefaultFilterConfig())
    }

    @Test
    fun testAccumulators_defaultToInstantaneous() {
        val accumulators = listOf(
            SensorType.DISTANCE_m,
            SensorType.TIME_ACTIVE,
            SensorType.CALORIES,
            SensorType.LAP_NR,
            SensorType.DISTANCE_m_LAP,
            SensorType.TIME_LAP,
            SensorType.TIME_TOTAL,
            SensorType.PHONE_BATTERY,
            SensorType.BATTERY_REMAINING_TIME
        )

        for (sensor in accumulators) {
            val config = SensorFilterDefaults.getDefaultFilterConfig(sensor)
            assertEquals("Accumulator ${sensor.name} must default to INSTANTANEOUS", FilterType.INSTANTANEOUS, config.filterType)
            assertEquals("Accumulator ${sensor.name} constant must be 1.0", 1.0, config.filterConstant, 0.001)
            assertEquals("sec", config.unit)
        }
    }

    @Test
    fun testContinuousMetrics_defaultToInstantaneous() {
        val continuousSensors = listOf(
            SensorType.HR,
            SensorType.SPEED_mps,
            SensorType.CADENCE,
            SensorType.ALTITUDE,
            SensorType.TEMPERATURE
        )

        for (sensor in continuousSensors) {
            val config = SensorFilterDefaults.getDefaultFilterConfig(sensor)
            assertEquals("Sensor ${sensor.name} must default to INSTANTANEOUS", FilterType.INSTANTANEOUS, config.filterType)
            assertEquals("Sensor ${sensor.name} constant must be 1.0", 1.0, config.filterConstant, 0.001)
            assertEquals("sec", config.unit)
        }
    }

    @Test
    fun testNullSensorType_returnsInstantaneousFallback() {
        val config = SensorFilterDefaults.getDefaultFilterConfig(null)
        assertEquals(FilterType.INSTANTANEOUS, config.filterType)
        assertEquals(1.0, config.filterConstant, 0.001)
        assertEquals("sec", config.unit)
        assertEquals(FilterType.INSTANTANEOUS, SensorFilterDefaults.getDefaultFilterType(null))
        assertEquals(1.0, SensorFilterDefaults.getDefaultFilterConstant(null), 0.001)
        assertEquals("sec", SensorFilterDefaults.getDefaultUnit(null))
    }

    @Test
    fun testAllSensorTypes_returnNonNullValidConfigurations() {
        for (sensor in SensorType.values()) {
            val config = SensorFilterDefaults.getDefaultFilterConfig(sensor)
            assertNotNull("Config must not be null for ${sensor.name}", config)
            assertNotNull("FilterType must not be null for ${sensor.name}", config.filterType)
            assertTrue("FilterConstant must be positive for ${sensor.name}", config.filterConstant > 0.0)
            assertEquals("Unit must be sec for ${sensor.name}", "sec", config.unit)
        }
    }
}
