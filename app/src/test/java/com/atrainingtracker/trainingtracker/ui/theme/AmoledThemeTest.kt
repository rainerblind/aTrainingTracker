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

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AmoledThemeTest {

    @Test
    fun testAmoledDarkColorSchemeTokens() {
        assertEquals(Color(0xFF000000), AmoledDarkColorScheme.background)
        assertEquals(Color(0xFF000000), AmoledDarkColorScheme.surface)
        assertEquals(Color(0xFF000000), AmoledDarkColorScheme.surfaceVariant)
        assertEquals(Color(0xFF000000), AmoledDarkColorScheme.surfaceDim)
        assertEquals(Color(0xFF000000), AmoledDarkColorScheme.surfaceContainer)
        assertEquals(Color(0xFF000000), AmoledDarkColorScheme.surfaceContainerLowest)
        assertEquals(Color(0xFF000000), AmoledDarkColorScheme.surfaceContainerLow)
        assertEquals(Color(0xFF121212), AmoledDarkColorScheme.surfaceContainerHigh)
        assertEquals(Color(0xFF000000), AmoledDarkColorScheme.surfaceContainerHighest)
        assertEquals(Color(0xFF000000), AmoledDarkColorScheme.primaryContainer)
        assertEquals(Color(0xFFFFFFFF), AmoledDarkColorScheme.onPrimaryContainer)
        assertEquals(Color(0xFF2C2C2E), AmoledDarkColorScheme.outlineVariant)
        assertEquals(Color(0xFF38383A), AmoledDarkColorScheme.outline)
        assertEquals(Color(0xFFFFFFFF), AmoledDarkColorScheme.onSurface)
        assertEquals(Color(0xFFFFFFFF), AmoledDarkColorScheme.onBackground)
        assertEquals(Color(0xFFC4C6D0), AmoledDarkColorScheme.onSurfaceVariant)
    }

    @Test
    fun testStandardDarkColorSchemeIsolation() {
        // Ensure standard app dark mode is unchanged (charcoal grey #1B1B1F)
        assertEquals(Color(0xFF1B1B1F), DarkBackground)
        assertEquals(Color(0xFF1B1B1F), DarkSurface)
        assertEquals(DarkBackground, DarkColorScheme.background)
        assertEquals(DarkSurface, DarkColorScheme.surface)
        assertNotEquals(DarkColorScheme.background, AmoledDarkColorScheme.background)
        assertNotEquals(DarkColorScheme.surface, AmoledDarkColorScheme.surface)
    }
}
