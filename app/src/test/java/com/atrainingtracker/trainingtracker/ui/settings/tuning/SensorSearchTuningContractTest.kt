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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorSearchPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract and persistence unit tests for Sensor Search tuning (ATT-2780).
 */
class SensorSearchTuningContractTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(mockContext) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putInt(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
    }

    @After
    fun tearDown() {
        unmockkStatic(PreferenceManager::class)
    }

    @Test
    fun testDefaultPreferences_matchFactoryDefaults() {
        val defaults = SensorSearchPreferences()
        assertEquals(3, defaults.numberOfSearchTries)
        assertTrue(defaults.startSearchWhenAppStarts)
        assertTrue(defaults.startSearchWhenResumeFromPaused)
        assertTrue(defaults.startSearchWhenUserChangesSport)
        assertFalse(defaults.startSearchWhenTrackingStarts)
        assertTrue(defaults.searchOnlyForSportSpecificDevices)
        assertTrue(defaults.changeSportWhenDeviceGetsLost)
    }

    @Test
    fun testLoadPreferences_readsFromSharedPreferences() {
        every { mockPrefs.getInt(TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT, 3) } returns 4
        every { mockPrefs.getBoolean("startSearchWhenAppStarts", true) } returns false
        every { mockPrefs.getBoolean("startSearchWhenResumeFromPaused", true) } returns true
        every { mockPrefs.getBoolean("startSearchWhenUserChangesSport", true) } returns false
        every { mockPrefs.getBoolean("startSearchWhenTrackingStarts", false) } returns true
        every { mockPrefs.getBoolean("searchOnlyForSportSpecificDevices", true) } returns false
        every { mockPrefs.getBoolean("changeSportWhenDeviceGetsLost", true) } returns false

        val loaded = SensorSearchPreferences.load(mockContext)
        assertEquals(4, loaded.numberOfSearchTries)
        assertFalse(loaded.startSearchWhenAppStarts)
        assertTrue(loaded.startSearchWhenResumeFromPaused)
        assertFalse(loaded.startSearchWhenUserChangesSport)
        assertTrue(loaded.startSearchWhenTrackingStarts)
        assertFalse(loaded.searchOnlyForSportSpecificDevices)
        assertFalse(loaded.changeSportWhenDeviceGetsLost)
    }

    @Test
    fun testSavePreferences_persistsToSharedPreferences() {
        val prefsToSave = SensorSearchPreferences(
            numberOfSearchTries = 5,
            startSearchWhenAppStarts = false,
            startSearchWhenResumeFromPaused = false,
            startSearchWhenUserChangesSport = false,
            startSearchWhenTrackingStarts = true,
            searchOnlyForSportSpecificDevices = false,
            changeSportWhenDeviceGetsLost = false
        )

        SensorSearchPreferences.save(mockContext, prefsToSave)

        verify { mockEditor.putInt(TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT, 5) }
        verify { mockEditor.putBoolean("startSearchWhenAppStarts", false) }
        verify { mockEditor.putBoolean("startSearchWhenResumeFromPaused", false) }
        verify { mockEditor.putBoolean("startSearchWhenUserChangesSport", false) }
        verify { mockEditor.putBoolean("startSearchWhenTrackingStarts", true) }
        verify { mockEditor.putBoolean("searchOnlyForSportSpecificDevices", false) }
        verify { mockEditor.putBoolean("changeSportWhenDeviceGetsLost", false) }
        verify { mockEditor.apply() }
    }

    @Test
    fun testResetPreferences_restoresDefaults() {
        SensorSearchPreferences.reset(mockContext)

        verify { mockEditor.putInt(TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT, 3) }
        verify { mockEditor.putBoolean("startSearchWhenAppStarts", true) }
        verify { mockEditor.putBoolean("startSearchWhenResumeFromPaused", true) }
        verify { mockEditor.putBoolean("startSearchWhenUserChangesSport", true) }
        verify { mockEditor.putBoolean("startSearchWhenTrackingStarts", false) }
        verify { mockEditor.putBoolean("searchOnlyForSportSpecificDevices", true) }
        verify { mockEditor.putBoolean("changeSportWhenDeviceGetsLost", true) }
        verify { mockEditor.apply() }
    }

    @Test
    fun testTuningSection_containsSensorSearch() {
        val sections = TuningSection.values()
        assertTrue(sections.contains(TuningSection.SENSOR_SEARCH))
    }
}
