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

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Contract test suite verifying `REQ-UI-497.1` and `REQ-UI-497.2` (`TST-UI-497.1`):
 * EditWorkoutScreen Hint Localization, Colon Removal & Label Capitalization across 9 locales.
 */
class EditWorkoutStringsParityContractTest {

    private val locales = listOf(
        "values",
        "values-de",
        "values-es",
        "values-fr",
        "values-it",
        "values-ja",
        "values-nl",
        "values-pl",
        "values-pt"
    )

    private fun resolveResDir(): File {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        return when {
            File(projectDir, "src/main/res").exists() -> File(projectDir, "src/main/res")
            File(projectDir, "app/src/main/res").exists() -> File(projectDir, "app/src/main/res")
            else -> File(projectDir, "../app/src/main/res")
        }
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

    /**
     * TST-UI-497.1: Verify German localization for workout metadata hint strings and Strava upload.
     */
    @Test
    fun testGermanWorkoutMetadataHintsAndStravaUpload() {
        val resDir = resolveResDir()
        val deStrings = File(resDir, "values-de/strings.xml")
        assertTrue("values-de/strings.xml must exist", deStrings.exists())

        assertEquals(
            "hint_workout_description in German must be 'Beschreibung'",
            "Beschreibung",
            parseStringResource(deStrings, "hint_workout_description")
        )
        assertEquals(
            "hint_workout_goal in German must be 'Ziel'",
            "Ziel",
            parseStringResource(deStrings, "hint_workout_goal")
        )
        assertEquals(
            "hint_workout_method in German must be 'Methode'",
            "Methode",
            parseStringResource(deStrings, "hint_workout_method")
        )
        assertEquals(
            "stravaUpload in German must be 'Zu Strava hochladen'",
            "Zu Strava hochladen",
            parseStringResource(deStrings, "stravaUpload")
        )
        assertEquals(
            "Sport in German must have no trailing colon",
            "Sport",
            parseStringResource(deStrings, "Sport")
        )
    }

    /**
     * TST-UI-497.1: Verify R.string.Sport has no trailing colon across all 9 locales (REQ-UI-497.2).
     */
    @Test
    fun testSportLabelHasNoTrailingColonAcrossAllLocales() {
        val resDir = resolveResDir()
        for (locale in locales) {
            val stringsFile = File(resDir, "$locale/strings.xml")
            assertTrue("$stringsFile must exist", stringsFile.exists())
            val sportValue = parseStringResource(stringsFile, "Sport")
            assertNotNull("Sport string must exist in $locale", sportValue)
            assertFalse(
                "Sport in $locale ('$sportValue') must not end with a colon",
                sportValue!!.endsWith(":")
            )
            assertFalse(
                "Sport in $locale ('$sportValue') must not contain a colon",
                sportValue.contains(":")
            )
        }
    }

    /**
     * TST-UI-497.1: Verify stravaUpload and hints are capitalized across all 9 locales (REQ-UI-497.1, REQ-UI-497.2).
     */
    @Test
    fun testStravaUploadAndHintsCapitalizationAcrossAllLocales() {
        val resDir = resolveResDir()
        for (locale in locales) {
            val stringsFile = File(resDir, "$locale/strings.xml")
            val stravaUpload = parseStringResource(stringsFile, "stravaUpload")
            assertNotNull("stravaUpload must exist in $locale", stravaUpload)
            val firstCharStrava = stravaUpload!!.first()
            assertTrue(
                "stravaUpload in $locale ('$stravaUpload') must start with an uppercase letter",
                !firstCharStrava.isLowerCase()
            )

            val keys = listOf("hint_workout_description", "hint_workout_goal", "hint_workout_method")
            for (key in keys) {
                val value = parseStringResource(stringsFile, key)
                assertNotNull("$key must exist in $locale", value)
                val firstChar = value!!.first()
                assertTrue(
                    "$key in $locale ('$value') must be capitalized",
                    !firstChar.isLowerCase()
                )
            }
        }
    }
}
