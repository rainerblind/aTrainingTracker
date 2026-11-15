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

package com.atrainingtracker.trainingtracker.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying WCAG 2.1 non-text contrast (>= 3.0:1) and theme-aware resolution
 * for the centralized [TelemetryGraphColor] palette across Light, Dark, and AMOLED themes
 * (REQ-UI-530.1, TST-UI-530.1, ATT-7759).
 */
class TelemetryGraphColorContrastTest {

    @Test
    fun paletteColors_containExactlyEightDistinctFixedColors() {
        assertEquals(8, TelemetryGraphColor.PALETTE_COLORS.size)
        assertTrue(TelemetryGraphColor.THEME_DEFAULT !in TelemetryGraphColor.PALETTE_COLORS)
        assertEquals(9, TelemetryGraphColor.entries.size)
        assertEquals(8, TelemetryGraphColor.PALETTE_COLORS.map { it.lightColor }.distinct().size)
        assertEquals(8, TelemetryGraphColor.PALETTE_COLORS.map { it.darkColor }.distinct().size)
    }

    @Test
    fun allPaletteColors_meetWcagNonTextContrastOnLightDarkAndAmoled() {
        for (token in TelemetryGraphColor.PALETTE_COLORS) {
            val lightRatio = TTColor.calculateContrastRatio(token.lightColor, LightSurface)
            assertTrue(
                "${token.name} lightColor contrast on LightSurface ($lightRatio) must be >= 3.0",
                lightRatio >= 3.0
            )

            val darkSurfaceRatio = TTColor.calculateContrastRatio(token.darkColor, DarkSurface)
            assertTrue(
                "${token.name} darkColor contrast on DarkSurface ($darkSurfaceRatio) must be >= 3.0",
                darkSurfaceRatio >= 3.0
            )

            val darkBgRatio = TTColor.calculateContrastRatio(token.darkColor, DarkBackground)
            assertTrue(
                "${token.name} darkColor contrast on DarkBackground ($darkBgRatio) must be >= 3.0",
                darkBgRatio >= 3.0
            )

            val amoledRatio = TTColor.calculateContrastRatio(token.darkColor, AmoledSurface)
            assertTrue(
                "${token.name} darkColor contrast on AmoledSurface ($amoledRatio) must be >= 3.0",
                amoledRatio >= 3.0
            )
        }
    }

    @Test
    fun emeraldToken_fixesWPrimeBalLightContrastWhilePreservingDarkToken() {
        val rawWPrimeOnWhite = TTColor.calculateContrastRatio(TTColor.WPrimeBalGreen, LightSurface)
        assertTrue("Raw WPrimeBalGreen on white is below 3.0 ($rawWPrimeOnWhite)", rawWPrimeOnWhite < 3.0)

        val emeraldLightOnWhite = TTColor.calculateContrastRatio(TelemetryGraphColor.EMERALD.lightColor, LightSurface)
        assertTrue("EMERALD lightColor on white ($emeraldLightOnWhite) must be >= 3.0", emeraldLightOnWhite >= 3.0)
        assertEquals(TTColor.WPrimeBalGreen, TelemetryGraphColor.EMERALD.darkColor)
    }

    @Test
    fun resolveColor_respectsThemeDefaultAndSurfaceLuminance() {
        assertEquals(
            LightColorScheme.primary,
            TelemetryGraphColor.THEME_DEFAULT.resolveColor(LightColorScheme)
        )
        assertEquals(
            DarkColorScheme.primary,
            TelemetryGraphColor.THEME_DEFAULT.resolveColor(DarkColorScheme)
        )
        assertEquals(
            AmoledDarkColorScheme.primary,
            TelemetryGraphColor.THEME_DEFAULT.resolveColor(AmoledDarkColorScheme)
        )

        for (token in TelemetryGraphColor.PALETTE_COLORS) {
            assertEquals(token.lightColor, token.resolveColor(LightColorScheme))
            assertEquals(token.darkColor, token.resolveColor(DarkColorScheme))
            assertEquals(token.darkColor, token.resolveColor(AmoledDarkColorScheme))
        }
    }
}
