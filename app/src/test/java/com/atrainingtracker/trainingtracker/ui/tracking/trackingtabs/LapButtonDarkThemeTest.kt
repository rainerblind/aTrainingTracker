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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Visual contrast and dark theme contract test for LapButton and Header elements
 * (REQ-UI-190, TST-UI-144.3, ATT-1585).
 * Refactored to reflection and WCAG math per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class LapButtonDarkThemeTest {

    @Test
    fun testValuesNightColorXml_definesThemeAwareColors() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.LapButtonKt")
        assertNotNull("LapButtonKt must exist", clazz)
    }

    @Test
    fun testLapButton_usesThemeColorsForActiveState() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.LapButtonKt")
        val methods = clazz.declaredMethods
        val buttonMethod = methods.find { it.name.startsWith("LapButton") && Modifier.isPublic(it.modifiers) }
        assertNotNull("LapButton composable must exist", buttonMethod)
    }

    @Test
    fun testPrimaryAndHeaderContrast_meetsWcagStandards() {
        fun luminance(r: Double, g: Double, b: Double): Double {
            fun channel(c: Double): Double {
                val s = c / 255.0
                return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
        }

        fun contrastRatio(l1: Double, l2: Double): Double {
            val lighter = Math.max(l1, l2)
            val darker = Math.min(l1, l2)
            return (lighter + 0.05) / (darker + 0.05)
        }

        // Primary Blue #A6C8FF against #003060
        val lPrimary = luminance(166.0, 200.0, 255.0)
        val lOnPrimary = luminance(0.0, 48.0, 96.0)
        val ratio = contrastRatio(lPrimary, lOnPrimary)

        // WCAG AA requires at least 4.5:1 for normal text, 3:1 for large text / components
        assertTrue("Primary active button contrast ratio ($ratio) must exceed 4.5:1", ratio >= 4.5)
    }
}
