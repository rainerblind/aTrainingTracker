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

package com.atrainingtracker.trainingtracker.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Audit tests verifying 100% 9-language localization parity for file picker strings (ATT-2622 / REQ-UI-294 / TST-UI-254).
 */
class ImportFormatFilePickerLocalizationTest {

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
    fun testInvalidWorkoutFileFormatString_hasCompleteParityAcrossAll9Locales() {
        val stringKey = "invalid_workout_file_format"

        for (locale in locales) {
            val file = File("src/main/res/$locale/strings.xml")
            assertTrue("Locale file $file must exist", file.exists())
            val content = file.readText()

            val pattern = Pattern.compile("<string name=\"$stringKey\">(.*?)</string>")
            val matcher = pattern.matcher(content)
            assertTrue("String key '$stringKey' must exist in locale $locale", matcher.find())

            val value = matcher.group(1)
            assertNotNull("String value for '$stringKey' in $locale must not be null", value)
            assertTrue("String value for '$stringKey' in $locale must not be blank", value!!.isNotBlank())

            // Assert exactly 1 format argument: %1$s or %s
            val formatArgCount = value.split("%1\$s").size - 1 + (value.split("%s").size - 1)
            assertEquals("String '$stringKey' in locale $locale must contain exactly one string format specifier", 1, formatArgCount)
        }
    }
}
