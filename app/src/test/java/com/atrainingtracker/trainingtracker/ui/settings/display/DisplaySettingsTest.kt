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

package com.atrainingtracker.trainingtracker.ui.settings.display

import android.content.SharedPreferences
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.CockpitThemeMode
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit test suite verifying Display Settings state parity, default synchronization,
 * and preference updates (REQ-SET-052, TST-NAV-004, ATT-1016).
 */
class DisplaySettingsTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private val prefStorage = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        prefStorage.clear()

        every { mockPrefs.getStringSet(any(), any()) } answers {
            val key = firstArg<String>()
            val def = secondArg<Set<String>?>()
            @Suppress("UNCHECKED_CAST")
            (prefStorage[key] as? Set<String>) ?: def
        }
        every { mockPrefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val def = secondArg<String?>()
            (prefStorage[key] as? String) ?: def
        }

        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putStringSet(any(), any()) } answers {
            val key = firstArg<String>()
            val set = secondArg<Set<String>>()
            prefStorage[key] = HashSet(set)
            mockEditor
        }
        every { mockEditor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            prefStorage[key] = value
            mockEditor
        }
        every { mockEditor.apply() } just Runs

        setStaticField(TrainingApplication::class.java, "cSharedPreferences", mockPrefs)
    }

    @After
    fun tearDown() {
        unmockkAll()
        prefStorage.clear()
    }

    @Test
    fun testDefaultDisplayOptionsContainsAllRequiredKeys() {
        val defaults = TrainingApplication.DEFAULT_DISPLAY_OPTIONS
        assertEquals(3, defaults.size)
        assertTrue(defaults.contains("forcePortrait"))
        assertTrue(defaults.contains("keepScreenOn"))
        assertTrue(defaults.contains("noUnlocking"))
    }

    @Test
    fun testDefaultFallbackWhenPreferencesUnset() {
        // Given no preferences stored in SharedPreferences
        assertTrue(prefStorage.isEmpty())

        // Then getDisplayOptions() returns default display options
        val options = TrainingApplication.getDisplayOptions()
        assertEquals(TrainingApplication.DEFAULT_DISPLAY_OPTIONS, options)

        // And all three boolean getters evaluate to true
        assertTrue(TrainingApplication.forcePortrait())
        assertTrue(TrainingApplication.keepScreenOn())
        assertTrue(TrainingApplication.NoUnlocking())
    }

    @Test
    fun testUpdatingDisplayOptionsUpdatesGetters() {
        // Given updated options without "forcePortrait"
        val customOptions = setOf("keepScreenOn", "noUnlocking")
        TrainingApplication.setDisplayOptions(customOptions)

        // Then getDisplayOptions() returns updated set
        val options = TrainingApplication.getDisplayOptions()
        assertEquals(customOptions, options)

        // And individual getters reflect the change
        assertFalse(TrainingApplication.forcePortrait())
        assertTrue(TrainingApplication.keepScreenOn())
        assertTrue(TrainingApplication.NoUnlocking())
    }

    @Test
    fun testDisablingMultipleOptionsAtomically() {
        // Initial state
        var current = TrainingApplication.getDisplayOptions().toMutableSet()

        // Turn off keepScreenOn
        current.remove("keepScreenOn")
        TrainingApplication.setDisplayOptions(current)

        assertFalse(TrainingApplication.keepScreenOn())
        assertTrue(TrainingApplication.forcePortrait())
        assertTrue(TrainingApplication.NoUnlocking())

        // Next, turn off noUnlocking from the updated set
        current = TrainingApplication.getDisplayOptions().toMutableSet()
        current.remove("noUnlocking")
        TrainingApplication.setDisplayOptions(current)

        assertFalse(TrainingApplication.keepScreenOn())
        assertFalse(TrainingApplication.NoUnlocking())
        assertTrue(TrainingApplication.forcePortrait())
        assertEquals(setOf("forcePortrait"), TrainingApplication.getDisplayOptions())
    }

    @Test
    fun testReEnablingAllOptions() {
        // Start from empty
        TrainingApplication.setDisplayOptions(emptySet())
        assertFalse(TrainingApplication.forcePortrait())
        assertFalse(TrainingApplication.keepScreenOn())
        assertFalse(TrainingApplication.NoUnlocking())

        // Re-enable defaults
        TrainingApplication.setDisplayOptions(TrainingApplication.DEFAULT_DISPLAY_OPTIONS)
        assertTrue(TrainingApplication.forcePortrait())
        assertTrue(TrainingApplication.keepScreenOn())
        assertTrue(TrainingApplication.NoUnlocking())
    }

    @Test
    fun testDefaultCockpitThemeModeIsSystem() {
        assertTrue(prefStorage.isEmpty())
        val mode = TrainingApplication.getCockpitThemeMode()
        assertEquals(CockpitThemeMode.SYSTEM, mode)
    }

    @Test
    fun testUpdateCockpitThemeModeToAlwaysDark() {
        TrainingApplication.setCockpitThemeMode(CockpitThemeMode.ALWAYS_DARK)
        assertEquals(CockpitThemeMode.ALWAYS_DARK, TrainingApplication.getCockpitThemeMode())
        assertEquals("always_dark", prefStorage[TrainingApplication.SP_COCKPIT_THEME_MODE])
    }

    @Test
    fun testUpdateCockpitThemeModeBackToSystem() {
        TrainingApplication.setCockpitThemeMode(CockpitThemeMode.ALWAYS_DARK)
        assertEquals(CockpitThemeMode.ALWAYS_DARK, TrainingApplication.getCockpitThemeMode())

        TrainingApplication.setCockpitThemeMode(CockpitThemeMode.SYSTEM)
        assertEquals(CockpitThemeMode.SYSTEM, TrainingApplication.getCockpitThemeMode())
        assertEquals("system", prefStorage[TrainingApplication.SP_COCKPIT_THEME_MODE])
    }

    @Test
    fun testInvalidCockpitThemeModeFallsBackToSystem() {
        prefStorage[TrainingApplication.SP_COCKPIT_THEME_MODE] = "invalid_or_legacy_value"
        assertEquals(CockpitThemeMode.SYSTEM, TrainingApplication.getCockpitThemeMode())
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }
}
