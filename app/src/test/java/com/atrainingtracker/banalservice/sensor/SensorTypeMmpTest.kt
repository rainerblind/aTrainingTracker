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

package com.atrainingtracker.banalservice.sensor

import com.atrainingtracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test validating enum properties, categories, and icon bindings for new Mean Maximal Power (MMP)
 * sensor types conforming to REQ-TRK-058.3 and TST-TRK-058.3.
 */
class SensorTypeMmpTest {

    private val rollingTypes = listOf(
        SensorType.POWER_5S,
        SensorType.POWER_1M,
        SensorType.POWER_5M,
        SensorType.POWER_20M
    )

    private val peakTypes = listOf(
        SensorType.POWER_PEAK_5S,
        SensorType.POWER_PEAK_1M,
        SensorType.POWER_PEAK_5M,
        SensorType.POWER_PEAK_20M
    )

    @Test
    fun mmpRollingSensorsConfigurationParity() {
        rollingTypes.forEach { type ->
            assertEquals(SensorCategory.POWER_AND_PEDALING, type.category)
            assertEquals(SensorValueType.DOUBLE, type.sensorValueType)
            assertTrue("${type.name} should allow filtering", type.filteringPossible)
            assertEquals(R.drawable.ic_power, type.iconResId)
        }
    }

    @Test
    fun mmpPeakSensorsConfigurationParity() {
        peakTypes.forEach { type ->
            assertEquals(SensorCategory.POWER_AND_PEDALING, type.category)
            assertEquals(SensorValueType.DOUBLE, type.sensorValueType)
            assertFalse("${type.name} peak should not allow smoothing filter", type.filteringPossible)
            assertEquals(R.drawable.ic_power, type.iconResId)
        }
    }
}
