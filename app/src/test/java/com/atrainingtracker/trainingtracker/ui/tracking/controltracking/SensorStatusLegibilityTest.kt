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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Unit tests verifying sensor status inactive indicator legibility and contrast compliance (REQ-UI-173 / TST-UI-125).
 */
class SensorStatusLegibilityTest {

    @Test
    fun testInactiveSensorAlphaConstant_alignsWithMaterial3Standard() {
        // Material Design 3 disabled content standard is 0.38f (38% opacity)
        assertEquals(0.38f, INACTIVE_SENSOR_ALPHA, 0.001f)
    }

    @Test
    fun testContrastRatioInAmoledDarkMode_exceedsWcagNonTextThreshold() {
        // TST-UI-125.2: Inactive sensor icons in AMOLED dark mode (#000000)
        // Background: #000000 -> L2 = 0.0
        val backgroundLuminance = 0.0

        // Active sensor: #FFFFFF at alpha 1.0f -> L1 = 1.0
        val activeLuminance = 1.0
        val activeContrast = calculateContrastRatio(activeLuminance, backgroundLuminance)
        assertEquals(21.0, activeContrast, 0.1)
        assertTrue("Active icon must achieve at least 7:1 contrast (WCAG AAA)", activeContrast >= 7.0)

        // Inactive sensor: #FFFFFF at alpha 0.38f composited over #000000 in sRGB space
        val rSrgb = compositeChannel(foreground = 1.0, background = 0.0, alpha = INACTIVE_SENSOR_ALPHA.toDouble())
        val rLinear = sRgbToLinear(rSrgb)
        val inactiveLuminance = calculateLinearLuminance(rLinear, rLinear, rLinear)
        val inactiveContrast = calculateContrastRatio(inactiveLuminance, backgroundLuminance)

        // WCAG 2.1 Non-text Contrast SC 1.4.11 requires >= 3.0:1
        assertTrue(
            "Inactive sensor contrast against #000000 must be >= 3.0:1, was $inactiveContrast",
            inactiveContrast >= 3.0
        )

        // Contrast ratio differential between active and inactive should be >= 5.0 to guarantee clear distinction
        val differential = activeContrast / inactiveContrast
        assertTrue(
            "Contrast differential between active and inactive must be >= 5.0, was $differential",
            differential >= 5.0
        )
    }

    @Test
    fun testContrastRatioInStandardDarkTheme() {
        // Standard Dark Mode surface #1B1B1F (rgb: 27, 27, 31)
        val bgR = 27.0 / 255.0
        val bgG = 27.0 / 255.0
        val bgB = 31.0 / 255.0
        val backgroundLuminance = calculateLinearLuminance(sRgbToLinear(bgR), sRgbToLinear(bgG), sRgbToLinear(bgB))

        // Inactive sensor #FFFFFF at alpha 0.38f over #1B1B1F in sRGB space
        val compR = compositeChannel(1.0, bgR, INACTIVE_SENSOR_ALPHA.toDouble())
        val compG = compositeChannel(1.0, bgG, INACTIVE_SENSOR_ALPHA.toDouble())
        val compB = compositeChannel(1.0, bgB, INACTIVE_SENSOR_ALPHA.toDouble())
        val inactiveLuminance = calculateLinearLuminance(sRgbToLinear(compR), sRgbToLinear(compG), sRgbToLinear(compB))

        val inactiveContrast = calculateContrastRatio(inactiveLuminance, backgroundLuminance)
        assertTrue(
            "Inactive sensor contrast against #1B1B1F must be >= 3.0:1, was $inactiveContrast",
            inactiveContrast >= 3.0
        )
    }

    private fun compositeChannel(foreground: Double, background: Double, alpha: Double): Double {
        return foreground * alpha + background * (1.0 - alpha)
    }

    private fun sRgbToLinear(channel: Double): Double {
        return if (channel <= 0.04045) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun calculateLinearLuminance(r: Double, g: Double, b: Double): Double {
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun calculateContrastRatio(l1: Double, l2: Double): Double {
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
