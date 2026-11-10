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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test validating SensorType.RR_INTERVAL registration, category, unit, and value type (REQ-CON-027.1, TST-CON-027.1).
 */
class SensorTypeRrIntervalTest {

    @Test
    fun testRrIntervalProperties() {
        val rr = SensorType.RR_INTERVAL
        assertEquals(SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS, rr.category)
        assertEquals(SensorValueType.INTEGER, rr.sensorValueType)
        assertEquals(R.string.rr_interval, rr.fullNameId)
        assertEquals(R.string.rr_interval_short, rr.shortNameId)
        assertEquals(R.string.units_time_ms, rr.unitId)
        assertEquals(R.drawable.ic_heart_rate, rr.iconResId)
        assertTrue(rr.filteringPossible)
    }
}
