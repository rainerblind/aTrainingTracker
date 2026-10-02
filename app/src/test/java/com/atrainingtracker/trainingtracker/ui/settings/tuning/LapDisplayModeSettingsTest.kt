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

import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests verifying LapDisplayMode preference handling in Advanced Tuning dialog and
 * complete 9-language translation parity across all supported locales (REQ-UI-229, TST-UI-183, ATT-1870).
 */
class LapDisplayModeSettingsTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

    private val requiredKeys = listOf(
        "settings_lap_display_mode_title",
        "settings_lap_display_mode_table",
        "settings_lap_display_mode_visualizer",
        "settings_lap_display_mode_both"
    )

    private fun findResDir(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidates: $candidates")
    }

    private fun parseStringResource(file: File, stringName: String): String? {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val item = stringNodes.item(i)
            if (item is Element && item.getAttribute("name") == stringName) {
                return item.textContent
            }
        }
        return null
    }

    @Test
    fun testWorkoutCardSectionPreferences_lapDisplayModeDefaultAndMutation() {
        val defaultPrefs = WorkoutCardSectionPreferences()
        assertEquals(LapDisplayMode.VISUALIZER_ONLY, defaultPrefs.lapDisplayMode)

        val tableOnly = defaultPrefs.copy(lapDisplayMode = LapDisplayMode.TABLE_ONLY)
        assertEquals(LapDisplayMode.TABLE_ONLY, tableOnly.lapDisplayMode)

        val visualizerOnly = defaultPrefs.copy(lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY)
        assertEquals(LapDisplayMode.VISUALIZER_ONLY, visualizerOnly.lapDisplayMode)

        val resetPrefs = WorkoutCardSectionPreferences()
        assertEquals(LapDisplayMode.VISUALIZER_ONLY, resetPrefs.lapDisplayMode)
    }

    @Test
    fun testLapDisplayModeLocalizationParityAcrossAll9Locales() {
        val resDir = findResDir()

        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val stringsFile = File(resDir, "$dirName/strings.xml")
            assertTrue("Expected strings.xml to exist in $dirName", stringsFile.exists())

            for (key in requiredKeys) {
                val value = parseStringResource(stringsFile, key)
                assertNotNull("Missing key '$key' in $dirName", value)
                assertTrue(
                    "Key '$key' in $dirName must not be blank",
                    value!!.isNotBlank()
                )
            }
        }
    }
}
