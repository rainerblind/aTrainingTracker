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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests verifying that [getSensorValueTextStyle] and [getSensorUnitTextStyle]
 * apply parameterized font family and weight across all 9 [ViewSize] steps (REQ-UI-212 / TST-UI-166).
 */
class SensorFieldViewTypographyTest {

    private val typography = Typography()

    @Test
    fun defaultInvocation_preservesSemiBoldAndBaseFontFamily() {
        for (size in ViewSize.values()) {
            val valueStyle = getSensorValueTextStyle(size, typography)
            assertEquals("Default value style must remain SemiBold for $size", FontWeight.SemiBold, valueStyle.fontWeight)

            val unitStyle = getSensorUnitTextStyle(size, typography)
            // Assert that calling with default null fontFamily preserves the underlying typography's font family
            val expectedUnitBase = when (size) {
                ViewSize.XSMALL -> typography.bodySmall.copy(fontSize = 10.sp)
                ViewSize.SMALL -> typography.bodySmall
                ViewSize.NORMAL -> typography.bodyLarge
                ViewSize.LARGE -> typography.headlineSmall
                ViewSize.XLARGE -> typography.headlineMedium.copy(fontSize = 32.sp)
                ViewSize.HUGE -> typography.headlineMedium.copy(fontSize = 40.sp)
                ViewSize.XHUGE -> typography.headlineLarge.copy(fontSize = 48.sp)
                ViewSize.XXHUGE -> typography.headlineLarge.copy(fontSize = 56.sp)
                ViewSize.XXXHUGE -> typography.headlineLarge.copy(fontSize = 64.sp)
            }
            assertEquals("Default unit font family should match base typography for $size", expectedUnitBase.fontFamily, unitStyle.fontFamily)
        }
    }

    @Test
    fun parameterizedInvocation_appliesConfiguredFontFamilyAndWeight() {
        val testWeights = listOf(FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold)
        val testFamily = FontFamily.Monospace

        for (size in ViewSize.values()) {
            for (weight in testWeights) {
                val valueStyle = getSensorValueTextStyle(
                    viewSize = size,
                    typography = typography,
                    fontFamily = testFamily,
                    fontWeight = weight
                )
                assertEquals("Configured weight must be applied for $size", weight, valueStyle.fontWeight)
                assertEquals("Configured font family must be applied for $size", testFamily, valueStyle.fontFamily)
            }

            val unitStyle = getSensorUnitTextStyle(
                viewSize = size,
                typography = typography,
                fontFamily = testFamily
            )
            assertEquals("Configured font family must be applied to unit for $size", testFamily, unitStyle.fontFamily)
        }
    }

    @Test
    fun domainTypographyConfig_appliesCleanlyAcrossAllFamiliesAndWeights() {
        for (family in CockpitFontFamily.values()) {
            val resolvedFamily = CockpitTypography.resolveFontFamily(family)
            for (weight in CockpitFontWeight.values()) {
                val style = getSensorValueTextStyle(
                    viewSize = ViewSize.NORMAL,
                    typography = typography,
                    fontFamily = resolvedFamily,
                    fontWeight = weight.asFontWeight()
                )
                assertEquals(weight.asFontWeight(), style.fontWeight)
                assertEquals(resolvedFamily, style.fontFamily)
            }
        }
    }
}
