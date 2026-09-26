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

package com.atrainingtracker.trainingtracker.ui.tracking

import com.atrainingtracker.trainingtracker.ui.theme.CockpitThemeMode
import com.atrainingtracker.trainingtracker.ui.theme.CockpitThemeState
import com.atrainingtracker.trainingtracker.ui.theme.resolveEffectiveCockpitThemeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying pure state resolution for the comprehensive cockpit dark theme (REQ-UI-170 / TST-UI-122).
 */
class TrackingThemeResolutionTest {

    @Test
    fun testPage0InTrackingModeWithAlwaysDarkAndSystemLight_remainsLight() {
        // Test 1.1: Page 0 (Control Tracking) in TRACKING mode with ALWAYS_DARK and host OS in Light Mode
        // Must remain in ambient light mode per user mandate.
        val state = resolveEffectiveCockpitThemeState(
            screenMode = ScreenMode.TRACKING,
            currentPage = 0,
            cockpitThemeMode = CockpitThemeMode.ALWAYS_DARK,
            isSystemDark = false
        )
        assertFalse(state.darkTheme)
        assertFalse(state.amoled)
    }

    @Test
    fun testPage1InTrackingModeWithAlwaysDarkAndSystemLight_rendersAmoledDark() {
        // Test 1.2: Page 1 (Telemetry) in TRACKING mode with ALWAYS_DARK and host OS in Light Mode
        // Must render in AMOLED Pure Black (#000000).
        val state = resolveEffectiveCockpitThemeState(
            screenMode = ScreenMode.TRACKING,
            currentPage = 1,
            cockpitThemeMode = CockpitThemeMode.ALWAYS_DARK,
            isSystemDark = false
        )
        assertTrue(state.darkTheme)
        assertTrue(state.amoled)
    }

    @Test
    fun testPage0InTrackingModeWithAlwaysDarkAndSystemDark_rendersStandardDark() {
        // Test 1.3: Page 0 in TRACKING mode when host OS is in Dark Mode
        // Renders in dark theme following host OS, but not AMOLED pure black.
        val state = resolveEffectiveCockpitThemeState(
            screenMode = ScreenMode.TRACKING,
            currentPage = 0,
            cockpitThemeMode = CockpitThemeMode.ALWAYS_DARK,
            isSystemDark = true
        )
        assertTrue(state.darkTheme)
        assertFalse(state.amoled)
    }

    @Test
    fun testPage1InTrackingModeWithSystemModeAndSystemLight_remainsLight() {
        // Test 1.4: Page 1 in TRACKING mode with SYSTEM mode when host OS is in Light Mode
        val state = resolveEffectiveCockpitThemeState(
            screenMode = ScreenMode.TRACKING,
            currentPage = 1,
            cockpitThemeMode = CockpitThemeMode.SYSTEM,
            isSystemDark = false
        )
        assertFalse(state.darkTheme)
        assertFalse(state.amoled)
    }

    @Test
    fun testPage1InTrackingModeWithSystemModeAndSystemDark_rendersAmoledDark() {
        // Test 1.5: Page 1 in TRACKING mode with SYSTEM mode when host OS is in Dark Mode
        val state = resolveEffectiveCockpitThemeState(
            screenMode = ScreenMode.TRACKING,
            currentPage = 1,
            cockpitThemeMode = CockpitThemeMode.SYSTEM,
            isSystemDark = true
        )
        assertTrue(state.darkTheme)
        assertTrue(state.amoled)
    }

    @Test
    fun testPage0InConfigurationModeWithAlwaysDark_rendersAmoledDark() {
        // Test 1.6: In CONFIGURATION mode, all pages (including index 0) are telemetry configuration views
        val state = resolveEffectiveCockpitThemeState(
            screenMode = ScreenMode.CONFIGURATION,
            currentPage = 0,
            cockpitThemeMode = CockpitThemeMode.ALWAYS_DARK,
            isSystemDark = false
        )
        assertTrue(state.darkTheme)
        assertTrue(state.amoled)
    }

    @Test
    fun testPage0InPreviewModeWithAlwaysDark_rendersAmoledDark() {
        // Test 1.7: In PREVIEW mode, all pages (including index 0) are telemetry preview views
        val state = resolveEffectiveCockpitThemeState(
            screenMode = ScreenMode.PREVIEW,
            currentPage = 0,
            cockpitThemeMode = CockpitThemeMode.ALWAYS_DARK,
            isSystemDark = false
        )
        assertTrue(state.darkTheme)
        assertTrue(state.amoled)
    }

    @Test
    fun testRapidPageTogglingSequence_resolvesDeterministically() {
        // Test 1.8: Switching rapidly between Page 0 and Telemetry pages
        // Sequence: 0 -> 1 -> 0 -> 2
        val state0 = resolveEffectiveCockpitThemeState(
            screenMode = ScreenMode.TRACKING,
            currentPage = 0,
            cockpitThemeMode = CockpitThemeMode.ALWAYS_DARK,
            isSystemDark = false
        )
        assertEquals(CockpitThemeState(darkTheme = false, amoled = false), state0)

        val state1 = resolveEffectiveCockpitThemeState(
            screenMode = ScreenMode.TRACKING,
            currentPage = 1,
            cockpitThemeMode = CockpitThemeMode.ALWAYS_DARK,
            isSystemDark = false
        )
        assertEquals(CockpitThemeState(darkTheme = true, amoled = true), state1)

        val state0Again = resolveEffectiveCockpitThemeState(
            screenMode = ScreenMode.TRACKING,
            currentPage = 0,
            cockpitThemeMode = CockpitThemeMode.ALWAYS_DARK,
            isSystemDark = false
        )
        assertEquals(CockpitThemeState(darkTheme = false, amoled = false), state0Again)

        val state2 = resolveEffectiveCockpitThemeState(
            screenMode = ScreenMode.TRACKING,
            currentPage = 2,
            cockpitThemeMode = CockpitThemeMode.ALWAYS_DARK,
            isSystemDark = false
        )
        assertEquals(CockpitThemeState(darkTheme = true, amoled = true), state2)
    }
}
