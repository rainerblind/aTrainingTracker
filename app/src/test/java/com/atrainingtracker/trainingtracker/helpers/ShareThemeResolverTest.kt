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

package com.atrainingtracker.trainingtracker.helpers

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Unit tests verifying theme-aware share snapshot color resolution and WCAG AAA contrast compliance (ATT-1472 / REQ-UI-177 / TST-UI-129).
 */
class ShareThemeResolverTest {

    @Test
    fun testLightModeColors_matchHistoricalDesignConstants() {
        val lightColors = ShareThemeResolver.resolveColors(isDark = false)

        assertEquals("Light canvas must remain pure white", 0xFFFFFFFF.toInt(), lightColors.canvasBackground)
        assertEquals("Light footer background must remain #F5F5F5", 0xFFF5F5F5.toInt(), lightColors.footerBackground)
        assertEquals("Light footer text must remain Color.DKGRAY (#444444)", 0xFF444444.toInt(), lightColors.footerTextColor)
    }

    @Test
    fun testDarkModeColors_matchDarkThemeSpecification() {
        val darkColors = ShareThemeResolver.resolveColors(isDark = true)

        assertEquals("Dark canvas must evaluate to Color.BLACK", 0xFF000000.toInt(), darkColors.canvasBackground)
        assertEquals("Dark footer background must evaluate to #1E1E1E", 0xFF1E1E1E.toInt(), darkColors.footerBackground)
        assertEquals("Dark footer text must evaluate to #E0E0E0", 0xFFE0E0E0.toInt(), darkColors.footerTextColor)
    }

    @Test
    fun testImmutableStaticColorDefinitions() {
        assertEquals(ShareThemeResolver.Light, ShareThemeResolver.resolveColors(isDark = false))
        assertEquals(ShareThemeResolver.Dark, ShareThemeResolver.resolveColors(isDark = true))
    }

    @Test
    fun testWcagContrastRatios_exceedLevelAaaStandards() {
        // WCAG 2.1 Level AAA requires contrast ratio >= 7.0:1 for normal body text
        val minAaaContrast = 7.0

        // 1. Light Mode Contrast: DKGRAY (#444444) on #F5F5F5
        val lightBgLuminance = calculateRelativeLuminance(0xFFF5F5F5.toInt())
        val lightTextLuminance = calculateRelativeLuminance(0xFF444444.toInt())
        val lightContrast = calculateContrastRatio(lightBgLuminance, lightTextLuminance)

        assertTrue(
            "Light mode contrast ratio must exceed WCAG AAA ($minAaaContrast:1), actual was $lightContrast:1",
            lightContrast >= minAaaContrast
        )

        // 2. Dark Mode Contrast: #E0E0E0 on #1E1E1E
        val darkBgLuminance = calculateRelativeLuminance(0xFF1E1E1E.toInt())
        val darkTextLuminance = calculateRelativeLuminance(0xFFE0E0E0.toInt())
        val darkContrast = calculateContrastRatio(darkTextLuminance, darkBgLuminance)

        assertTrue(
            "Dark mode contrast ratio must exceed WCAG AAA ($minAaaContrast:1), actual was $darkContrast:1",
            darkContrast >= minAaaContrast
        )
    }

    @Test
    fun testNightModeDetection_evaluatesUiModeCorrectly() {
        val mockContext = mockk<Context>()
        val mockResources = mockk<Resources>()
        val configNightYes = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
        }
        val configNightNo = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
        }
        val configUndefined = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_UNDEFINED or Configuration.UI_MODE_TYPE_NORMAL
        }

        every { mockContext.resources } returns mockResources

        // Case 1: Night Mode Yes
        every { mockResources.configuration } returns configNightYes
        assertTrue(ShareThemeResolver.isNightMode(mockContext))

        // Case 2: Night Mode No
        every { mockResources.configuration } returns configNightNo
        assertFalse(ShareThemeResolver.isNightMode(mockContext))

        // Case 3: Undefined / Default
        every { mockResources.configuration } returns configUndefined
        assertFalse(ShareThemeResolver.isNightMode(mockContext))
    }

    // --- Mathematical Color & Contrast Helpers (WCAG 2.1 Standard) ---

    private fun calculateRelativeLuminance(colorInt: Int): Double {
        val r = sRgbToLinear(((colorInt shr 16) and 0xFF) / 255.0)
        val g = sRgbToLinear(((colorInt shr 8) and 0xFF) / 255.0)
        val b = sRgbToLinear((colorInt and 0xFF) / 255.0)
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
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
