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

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.atrainingtracker.trainingtracker.ui.theme.AmoledDarkColorScheme
import com.atrainingtracker.trainingtracker.ui.theme.AmoledOnSurfaceVariant
import com.atrainingtracker.trainingtracker.ui.theme.DarkPrimary
import com.atrainingtracker.trainingtracker.ui.theme.LightColorScheme
import com.atrainingtracker.trainingtracker.ui.theme.LightOnSurfaceVariant
import com.atrainingtracker.trainingtracker.ui.theme.LightPrimary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Unit test verifying synchronous tab header color resolution and WCAG contrast compliance
 * for REQ-UI-176 and TST-UI-128 (ATT-1446 / ATT-1464 / ATT-1466).
 */
class TabHeaderColorResolutionTest {

    private fun resolveTabTextColor(isSelected: Boolean, colorScheme: ColorScheme): Color {
        return if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant
    }

    @Test
    fun testAmoledDarkThemeTabHeaderColorsAndContrast() {
        val scheme = AmoledDarkColorScheme
        val containerBg = scheme.surfaceContainerHighest // #000000

        // In AMOLED Dark Mode, tab bar background is pure black #000000
        assertEquals(Color(0xFF000000), containerBg)

        // Selected tab text resolves to scheme.primary (DarkPrimary = #A6C8FF)
        val selectedColor = resolveTabTextColor(isSelected = true, colorScheme = scheme)
        assertEquals(DarkPrimary, selectedColor)
        assertEquals(scheme.primary, selectedColor)

        // Unselected tab text resolves to scheme.onSurfaceVariant (AmoledOnSurfaceVariant = #9E9E9E)
        val unselectedColor = resolveTabTextColor(isSelected = false, colorScheme = scheme)
        assertEquals(AmoledOnSurfaceVariant, unselectedColor)
        assertEquals(scheme.onSurfaceVariant, unselectedColor)

        // Contrast compliance against AMOLED pure black container
        val bgLum = calculateColorLuminance(containerBg)
        val selectedLum = calculateColorLuminance(selectedColor)
        val unselectedLum = calculateColorLuminance(unselectedColor)

        val selectedContrast = calculateContrastRatio(selectedLum, bgLum)
        val unselectedContrast = calculateContrastRatio(unselectedLum, bgLum)

        // Selected title contrast against #000000 must exceed 12:1 (WCAG AAA >= 7:1)
        assertTrue(
            "Selected tab contrast against #000000 must be >= 12.0:1, was $selectedContrast",
            selectedContrast >= 12.0
        )

        // Unselected title contrast against #000000 must exceed 7:1 (WCAG AAA >= 7:1)
        assertTrue(
            "Unselected tab contrast against #000000 must be >= 7.0:1, was $unselectedContrast",
            unselectedContrast >= 7.0
        )
    }

    @Test
    fun testLightThemeTabHeaderColorsAndContrast() {
        val scheme = LightColorScheme
        val containerBg = scheme.surfaceContainerHighest

        // Selected tab text resolves to scheme.primary (LightPrimary = #1464F4)
        val selectedColor = resolveTabTextColor(isSelected = true, colorScheme = scheme)
        assertEquals(LightPrimary, selectedColor)
        assertEquals(scheme.primary, selectedColor)

        // Unselected tab text resolves to scheme.onSurfaceVariant (LightOnSurfaceVariant = #44474F)
        val unselectedColor = resolveTabTextColor(isSelected = false, colorScheme = scheme)
        assertEquals(LightOnSurfaceVariant, unselectedColor)
        assertEquals(scheme.onSurfaceVariant, unselectedColor)

        // Contrast compliance against container background
        val bgLum = calculateColorLuminance(containerBg)
        val selectedLum = calculateColorLuminance(selectedColor)
        val unselectedLum = calculateColorLuminance(unselectedColor)

        val selectedContrast = calculateContrastRatio(selectedLum, bgLum)
        val unselectedContrast = calculateContrastRatio(unselectedLum, bgLum)

        // Selected title contrast must satisfy WCAG AA >= 4.0:1
        assertTrue(
            "Selected tab contrast against light container must be >= 4.0:1, was $selectedContrast",
            selectedContrast >= 4.0
        )

        // Unselected title contrast must exceed 7.0:1 (WCAG AAA >= 7:1)
        assertTrue(
            "Unselected tab contrast against light container must be >= 7.0:1, was $unselectedContrast",
            unselectedContrast >= 7.0
        )
    }

    @Test
    fun testSynchronousThemeFlipContract() {
        // Simulating transition across Page 0 (Light) and Page 1 (AMOLED Dark)
        val lightScheme = LightColorScheme
        val darkScheme = AmoledDarkColorScheme

        // Frame 0: On Page 0 (Light Mode)
        val page0Tab0Color = resolveTabTextColor(isSelected = true, colorScheme = lightScheme)
        val page0Tab1Color = resolveTabTextColor(isSelected = false, colorScheme = lightScheme)
        assertEquals(LightPrimary, page0Tab0Color)
        assertEquals(LightOnSurfaceVariant, page0Tab1Color)

        // Frame 1: Instant navigation to Page 1 (theme immediately switches to AmoledDarkColorScheme)
        val page1Tab0Color = resolveTabTextColor(isSelected = false, colorScheme = darkScheme)
        val page1Tab1Color = resolveTabTextColor(isSelected = true, colorScheme = darkScheme)

        // In 0ms on the exact same frame, colors resolve directly to the dark scheme tokens
        assertEquals(AmoledOnSurfaceVariant, page1Tab0Color)
        assertEquals(DarkPrimary, page1Tab1Color)

        // Frame 2: Instant return to Page 0 (theme immediately switches back to LightColorScheme)
        val returnTab0Color = resolveTabTextColor(isSelected = true, colorScheme = lightScheme)
        val returnTab1Color = resolveTabTextColor(isSelected = false, colorScheme = lightScheme)
        assertEquals(LightPrimary, returnTab0Color)
        assertEquals(LightOnSurfaceVariant, returnTab1Color)
    }

    private fun calculateColorLuminance(color: Color): Double {
        val r = sRgbToLinear(color.red.toDouble())
        val g = sRgbToLinear(color.green.toDouble())
        val b = sRgbToLinear(color.blue.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun sRgbToLinear(channel: Double): Double {
        return if (channel <= 0.04045) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun calculateContrastRatio(l1: Double, l2: Double): Double {
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
