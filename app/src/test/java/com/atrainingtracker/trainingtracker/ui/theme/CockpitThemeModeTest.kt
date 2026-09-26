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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CockpitThemeMode] resolution logic (TST-UI-120).
 */
class CockpitThemeModeTest {

    @Test
    fun testFromId_resolvesExpectedModes() {
        assertEquals(CockpitThemeMode.SYSTEM, CockpitThemeMode.fromId("system"))
        assertEquals(CockpitThemeMode.ALWAYS_DARK, CockpitThemeMode.fromId("always_dark"))
        assertEquals(CockpitThemeMode.SYSTEM, CockpitThemeMode.fromId(null))
        assertEquals(CockpitThemeMode.SYSTEM, CockpitThemeMode.fromId("unknown_value"))
    }

    @Test
    fun testResolveEffectiveCockpitDarkTheme_systemMode_followsSystem() {
        assertFalse(resolveEffectiveCockpitDarkTheme(CockpitThemeMode.SYSTEM, isSystemDark = false))
        assertTrue(resolveEffectiveCockpitDarkTheme(CockpitThemeMode.SYSTEM, isSystemDark = true))
    }

    @Test
    fun testResolveEffectiveCockpitDarkTheme_alwaysDark_alwaysTrue() {
        assertTrue(resolveEffectiveCockpitDarkTheme(CockpitThemeMode.ALWAYS_DARK, isSystemDark = false))
        assertTrue(resolveEffectiveCockpitDarkTheme(CockpitThemeMode.ALWAYS_DARK, isSystemDark = true))
    }
}
