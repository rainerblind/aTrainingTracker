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

/**
 * Unit tests verifying Dark Theme slate-blue tonal progression and graduated surface container hierarchy
 * (REQ-UI-190, TST-UI-144.1, ATT-1585).
 */
class DarkThemeTonalHierarchyTest {

    @Test
    fun testDarkColorScheme_slateBlueHeaderAndTabProgression() {
        assertEquals(
            "primaryContainer must be DarkPrimaryContainer (#162032)",
            Color(0xFF162032),
            DarkColorScheme.primaryContainer
        )
        assertEquals(
            "onPrimaryContainer must be DarkOnPrimaryContainer (#D4E3FF)",
            Color(0xFFD4E3FF),
            DarkColorScheme.onPrimaryContainer
        )
        assertEquals(
            "surfaceContainerHighest must be surfaceContainerHighestDark (#1B2436)",
            Color(0xFF1B2436),
            DarkColorScheme.surfaceContainerHighest
        )
        assertEquals(
            "primary must be DarkPrimary (#A6C8FF)",
            Color(0xFFA6C8FF),
            DarkColorScheme.primary
        )
    }

    @Test
    fun testDarkColorScheme_graduatedSurfaceContainerElevation() {
        assertEquals(
            "background must be DarkBackground (#121214)",
            Color(0xFF121214),
            DarkColorScheme.background
        )
        assertEquals(
            "surface must be DarkSurface (#16161A)",
            Color(0xFF16161A),
            DarkColorScheme.surface
        )
        assertEquals(
            "surfaceContainerLowest must be surfaceContainerLowestDark (#0E0E10)",
            Color(0xFF0E0E10),
            DarkColorScheme.surfaceContainerLowest
        )
        assertEquals(
            "surfaceContainerLow must be surfaceContainerLowDark (#1C2028)",
            Color(0xFF1C2028),
            DarkColorScheme.surfaceContainerLow
        )
        assertEquals(
            "surfaceContainer must be surfaceContainerDark (#20242E)",
            Color(0xFF20242E),
            DarkColorScheme.surfaceContainer
        )
        assertEquals(
            "surfaceContainerHigh must be surfaceContainerHighDark (#282F3D)",
            Color(0xFF282F3D),
            DarkColorScheme.surfaceContainerHigh
        )
        assertEquals(
            "outlineVariant must be DarkOutlineVariant (#2B3342)",
            Color(0xFF2B3342),
            DarkColorScheme.outlineVariant
        )
    }

    @Test
    fun testDarkColorScheme_cardSeparationFromBackground() {
        assertNotEquals(
            "Card surfaceContainerLow must not be identical to background canvas",
            DarkColorScheme.background,
            DarkColorScheme.surfaceContainerLow
        )
        assertNotEquals(
            "surfaceContainerLow must not be identical to base surface",
            DarkColorScheme.surface,
            DarkColorScheme.surfaceContainerLow
        )
    }
}
