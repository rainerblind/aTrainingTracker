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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization verification test enforcing 100% parity across all 9 supported locales
 * for Edit Workout settings strings pursuant to REQ-UI-211, REQ-LOC-001, and TST-UI-165.4.
 */
class EditWorkoutSettingsLocalizationTest {

    private val locales = listOf("de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private val requiredKeys = listOf(
        "settings_edit_workout_title",
        "settings_edit_workout_cluster",
        "settings_edit_workout_commute_trainer",
        "settings_edit_workout_strava",
        "settings_edit_workout_description",
        "settings_edit_workout_goal",
        "settings_edit_workout_method"
    )

    private lateinit var resDir: File

    @Before
    fun setUp() {
        resDir = File("src/main/res").takeIf { it.exists() }
            ?: File("app/src/main/res").takeIf { it.exists() }
            ?: File("../app/src/main/res").takeIf { it.exists() }
            ?: File(System.getProperty("user.dir"), "app/src/main/res").takeIf { it.exists() }
            ?: error("Unable to locate res directory from ${System.getProperty("user.dir")}")
    }

    private fun parseStringsFile(file: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!file.exists()) return result
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val elem = node as Element
                val name = elem.getAttribute("name")
                val text = elem.textContent
                result[name] = text
            }
        }
        return result
    }

    @Test
    fun defaultStrings_containsAllRequiredKeys() {
        val defaultFile = File(resDir, "values/strings.xml")
        assertTrue("Default strings.xml must exist", defaultFile.exists())
        val defaultStrings = parseStringsFile(defaultFile)

        for (key in requiredKeys) {
            assertTrue("Default strings.xml must contain key '$key'", defaultStrings.containsKey(key))
            assertFalse("Value for '$key' must not be blank", defaultStrings[key].isNullOrBlank())
        }
    }

    @Test
    fun localizedStrings_haveParityAcrossAll9Locales() {
        for (locale in locales) {
            val localeFile = File(resDir, "values-$locale/strings.xml")
            assertTrue("strings.xml for locale '$locale' must exist", localeFile.exists())
            val strings = parseStringsFile(localeFile)

            for (key in requiredKeys) {
                assertTrue(
                    "Locale '$locale' must contain localized string for '$key'",
                    strings.containsKey(key)
                )
                val value = strings[key]
                assertNotNull("Locale '$locale' must have non-null value for '$key'", value)
                assertFalse("Locale '$locale' must have non-blank value for '$key'", value!!.trim().isEmpty())
            }
        }
    }
}
