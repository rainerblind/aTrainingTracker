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
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.sensor.formater

import android.content.Context
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PhoneCarriageModeFormatter] and [SensorType.ACTIVITY_AND_CARRIAGE_MODE]
 * metadata invariants (REQ-FIL-028.2, TST-FIL-028.2, ATT-8890).
 */
// Linked test specs: TST-FIL-028
class PhoneCarriageModeFormatterTest {

    @Test
    fun testFormat_handlesSentinelsAndAllModesWithFallback() {
        val formatter = PhoneCarriageModeFormatter()

        assertEquals("--", formatter.formatWithContext(null, null))
        assertEquals("--", formatter.formatWithContext(Double.NaN, null))
        assertEquals("--", formatter.formatWithContext(Double.POSITIVE_INFINITY, null))

        for (mode in PhoneCarriageMode.entries) {
            assertEquals(mode.defaultEnglishLabel, formatter.formatWithContext(mode.code, null))
        }
        assertEquals("Unknown", formatter.formatWithContext(99, null))
    }

    @Test
    fun testFormatWithContext_prefersLocalizedContextString() {
        val formatter = PhoneCarriageModeFormatter()
        val mockCtx = mockk<Context>()
        every { mockCtx.getString(PhoneCarriageMode.BIKE_FRONT_POCKET.labelResId) } returns "Rad (Hosentasche)"

        assertEquals(
            "Rad (Hosentasche)",
            formatter.formatWithContext(PhoneCarriageMode.BIKE_FRONT_POCKET.code, mockCtx)
        )
    }

    @Test
    fun testSensorTypeContract_appendedAtOrdinal67UnfilteredDimensionless() {
        val sensor = SensorType.ACTIVITY_AND_CARRIAGE_MODE
        assertEquals(67, sensor.ordinal)
        assertEquals("dimensionless", sensor.internalUnit)
        assertFalse(sensor.filteringPossible)
        assertTrue(sensor.myFormatter is PhoneCarriageModeFormatter)
    }
}
