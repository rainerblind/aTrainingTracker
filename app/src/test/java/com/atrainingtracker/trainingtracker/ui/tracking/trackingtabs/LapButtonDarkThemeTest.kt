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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Unit tests verifying theme-aware LapButton styling, XML color overrides, and WCAG contrast compliance
 * (REQ-UI-190, TST-UI-144.3, ATT-1585).
 */
class LapButtonDarkThemeTest {

    @Test
    fun testValuesNightColorXml_definesThemeAwareColors() {
        val file = File("src/main/res/values-night/color.xml")
        assertTrue("values-night/color.xml must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "values-night/color.xml must define lap_button_disabled_background as #22252C",
            content.contains("<color name=\"lap_button_disabled_background\">#22252C</color>")
        )
        assertTrue(
            "values-night/color.xml must define lap_button_disabled_text as #5A6270",
            content.contains("<color name=\"lap_button_disabled_text\">#5A6270</color>")
        )
        assertTrue(
            "values-night/color.xml must define color_primary as #A6C8FF",
            content.contains("<color name=\"color_primary\">#A6C8FF</color>")
        )
        assertTrue(
            "values-night/color.xml must define color_on_primary as #003060",
            content.contains("<color name=\"color_on_primary\">#003060</color>")
        )
    }

    @Test
    fun testLapButton_usesThemeColorsForActiveState() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/LapButton.kt")
        assertTrue("LapButton.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "LapButton must use MaterialTheme.colorScheme.primary for containerColor",
            content.contains("containerColor = MaterialTheme.colorScheme.primary")
        )
        assertTrue(
            "LapButton must use MaterialTheme.colorScheme.onPrimary for contentColor",
            content.contains("contentColor = MaterialTheme.colorScheme.onPrimary")
        )
    }

    @Test
    fun testPrimaryAndHeaderContrast_meetsWcagStandards() {
        // WCAG relative luminance calculation
        fun sRgbToLinear(c: Double): Double {
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        fun relativeLuminance(r: Int, g: Int, b: Int): Double {
            val rLin = sRgbToLinear(r / 255.0)
            val gLin = sRgbToLinear(g / 255.0)
            val bLin = sRgbToLinear(b / 255.0)
            return 0.2126 * rLin + 0.7152 * gLin + 0.0722 * bLin
        }

        fun contrastRatio(l1: Double, l2: Double): Double {
            val lighter = max(l1, l2)
            val darker = min(l1, l2)
            return (lighter + 0.05) / (darker + 0.05)
        }

        // 1. DarkPrimary (#A6C8FF) on DarkOnPrimary (#003060)
        val lPrimary = relativeLuminance(0xA6, 0xC8, 0xFF)
        val lOnPrimary = relativeLuminance(0x00, 0x30, 0x60)
        val primaryContrast = contrastRatio(lPrimary, lOnPrimary)
        assertTrue(
            "Primary contrast ratio ($primaryContrast) must exceed WCAG AAA (7.0:1)",
            primaryContrast >= 7.0
        )

        // 2. DarkOnPrimaryContainer (#D4E3FF) on DarkPrimaryContainer (#162032)
        val lContainerText = relativeLuminance(0xD4, 0xE3, 0xFF)
        val lContainerBg = relativeLuminance(0x16, 0x20, 0x32)
        val containerContrast = contrastRatio(lContainerText, lContainerBg)
        assertTrue(
            "Header container contrast ratio ($containerContrast) must exceed WCAG AAA (7.0:1)",
            containerContrast >= 7.0
        )
    }
}
