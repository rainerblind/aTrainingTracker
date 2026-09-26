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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests verifying typography weights, font sizes, and contrast specifications
 * for cockpit sensor fields (REQ-UI-171 / TST-UI-123).
 */
class SensorFieldTypographyTest {

    private val typography = Typography()

    @Test
    fun testAllSensorValueTextStyles_enforceSemiBoldFontWeight() {
        for (size in ViewSize.values()) {
            val style = getSensorValueTextStyle(size, typography)
            assertEquals(
                "Value style for $size must enforce FontWeight.SemiBold for sunlight readability",
                FontWeight.SemiBold,
                style.fontWeight
            )
        }
    }

    @Test
    fun testAllSensorUnitTextStyles_doNotEnforceBoldFontWeight() {
        for (size in ViewSize.values()) {
            val style = getSensorUnitTextStyle(size, typography)
            assertNotEquals(
                "Unit style for $size should not be bold to maintain visual hierarchy",
                FontWeight.Bold,
                style.fontWeight
            )
        }
    }

    @Test
    fun testExplicitFontSizes_forCustomViewSizes() {
        assertEquals(20.sp, getSensorValueTextStyle(ViewSize.XSMALL, typography).fontSize)
        assertEquals(10.sp, getSensorUnitTextStyle(ViewSize.XSMALL, typography).fontSize)

        assertEquals(50.sp, getSensorValueTextStyle(ViewSize.XLARGE, typography).fontSize)
        assertEquals(32.sp, getSensorUnitTextStyle(ViewSize.XLARGE, typography).fontSize)

        assertEquals(76.sp, getSensorValueTextStyle(ViewSize.HUGE, typography).fontSize)
        assertEquals(40.sp, getSensorUnitTextStyle(ViewSize.HUGE, typography).fontSize)

        assertEquals(100.sp, getSensorValueTextStyle(ViewSize.XHUGE, typography).fontSize)
        assertEquals(48.sp, getSensorUnitTextStyle(ViewSize.XHUGE, typography).fontSize)
    }
}
