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

package com.atrainingtracker.banalservice.sensor.formater

import com.atrainingtracker.banalservice.sensor.SensorCategory
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.sensor.SensorValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying [SignedIntegerFormatter] formatting contracts and [SensorType.LIVE_HR_DRIFT]
 * metadata (REQ-REV-012, TST-REV-013.3).
 */
class SignedIntegerFormatterTest {

    private val formatter = SignedIntegerFormatter()

    @Test
    fun testFormat_positiveInteger_includesPlusSign() {
        assertEquals("+5", formatter.format(5))
        assertEquals("+1", formatter.format(1.2))
        assertEquals("+18", formatter.format(17.8))
    }

    @Test
    fun testFormat_zero_returnsZeroWithoutSign() {
        assertEquals("0", formatter.format(0))
        assertEquals("0", formatter.format(0.2))
        assertEquals("0", formatter.format(-0.2))
    }

    @Test
    fun testFormat_negativeInteger_includesStandardMinus() {
        assertEquals("-2", formatter.format(-2))
        assertEquals("-1", formatter.format(-1.4))
        assertEquals("-10", formatter.format(-9.8))
    }

    @Test
    fun testFormat_nullAndNonFinite_returnsDashes() {
        assertEquals("--", formatter.format(null))
        assertEquals("--", formatter.format(Double.NaN))
        assertEquals("--", formatter.format(Double.POSITIVE_INFINITY))
        assertEquals("--", formatter.format(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun testSensorType_liveHrDrift_metadataParity() {
        val type = SensorType.LIVE_HR_DRIFT
        assertEquals(SensorCategory.HEART_RATE_AND_ENERGY, type.category)
        assertEquals(SensorValueType.INTEGER, type.sensorValueType)
        assertNotNull(type.myFormatter)
        assertEquals(true, type.filteringPossible)
    }
}
