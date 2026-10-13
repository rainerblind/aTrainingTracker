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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0>.
 */

package com.atrainingtracker.trainingtracker.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Unit tests verifying semantic warning color contrast ratios against WCAG 2.1 standards
 * for light and dark themes (REQ-UI-356 / TST-UI-316 / ATT-3451).
 */
class SensorBatteryAlertColorsTest {

    private fun calculateLuminance(color: Color): Double {
        fun componentLuminance(c: Float): Double {
            val cD = c.toDouble()
            return if (cD <= 0.04045) {
                cD / 12.92
            } else {
                ((cD + 0.055) / 1.055).pow(2.4)
            }
        }
        val r = componentLuminance(color.red)
        val g = componentLuminance(color.green)
        val b = componentLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun calculateContrastRatio(foreground: Color, background: Color): Double {
        val l1 = calculateLuminance(foreground)
        val l2 = calculateLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun lightMode_warningColors_satisfyWcagContrastRequirements() {
        // [TST-UI-316.4]
        val container = LightWarningContainer
        val content = LightOnWarningContainer
        val action = LightWarningAction

        val contentContrast = calculateContrastRatio(content, container)
        val actionContrast = calculateContrastRatio(action, container)

        // WCAG AA requires at least 4.5:1 for normal text; AAA requires 7.0:1
        assertTrue(
            "LightOnWarningContainer contrast on LightWarningContainer ($contentContrast) must be >= 7.0:1",
            contentContrast >= 7.0
        )
        assertTrue(
            "LightWarningAction contrast on LightWarningContainer ($actionContrast) must be >= 7.0:1",
            actionContrast >= 7.0
        )
    }

    @Test
    fun darkMode_warningColors_satisfyWcagContrastRequirements() {
        // [TST-UI-316.4]
        val container = DarkWarningContainer
        val content = DarkOnWarningContainer
        val action = DarkWarningAction

        val contentContrast = calculateContrastRatio(content, container)
        val actionContrast = calculateContrastRatio(action, container)

        assertTrue(
            "DarkOnWarningContainer contrast on DarkWarningContainer ($contentContrast) must be >= 7.0:1",
            contentContrast >= 7.0
        )
        assertTrue(
            "DarkWarningAction contrast on DarkWarningContainer ($actionContrast) must be >= 7.0:1",
            actionContrast >= 7.0
        )
    }
}
