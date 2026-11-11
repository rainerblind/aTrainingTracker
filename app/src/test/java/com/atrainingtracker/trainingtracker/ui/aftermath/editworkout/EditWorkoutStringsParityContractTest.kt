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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test suite verifying `REQ-UI-497.1` and `REQ-UI-497.2` (`TST-UI-497.1`):
 * EditWorkoutScreen Hint Localization, Colon Removal & Label Capitalization across 9 locales.
 * Refactored to LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
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

    @Test
    fun testGermanWorkoutMetadataHintsAndStravaUpload() {
        val deStrings = LocalizationTestCache.getStrings("values-de", "strings.xml")

        assertEquals(
            "hint_workout_description in German must be 'Beschreibung'",
            "Beschreibung",
            deStrings["hint_workout_description"]
        )
        assertEquals(
            "hint_workout_goal in German must be 'Ziel'",
            "Ziel",
            deStrings["hint_workout_goal"]
        )
        assertEquals(
            "hint_workout_method in German must be 'Methode'",
            "Methode",
            deStrings["hint_workout_method"]
        )
        assertEquals(
            "stravaUpload in German must be 'Zu Strava hochladen'",
            "Zu Strava hochladen",
            deStrings["stravaUpload"]
        )
        assertEquals(
            "Sport in German must have no trailing colon",
            "Sport",
            deStrings["Sport"]
        )
    }

    @Test
    fun testSportLabelHasNoTrailingColonAcrossAllLocales() {
        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale, "strings.xml")
            val sportValue = strings["Sport"]
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

    @Test
    fun testStravaUploadAndHintsCapitalizationAcrossAllLocales() {
        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale, "strings.xml")
            val stravaUpload = strings["stravaUpload"]
            assertNotNull("stravaUpload must exist in $locale", stravaUpload)
            val firstCharStrava = stravaUpload!!.first()
            assertTrue(
                "stravaUpload in $locale ('$stravaUpload') must start with an uppercase letter",
                !firstCharStrava.isLowerCase()
            )

            val keys = listOf("hint_workout_description", "hint_workout_goal", "hint_workout_method")
            for (key in keys) {
                val value = strings[key]
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
