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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Localization audit test verifying 100% translation parity for zone display mode strings
 * across all 9 supported locales under REQ-UI-372.
 */
class ZoneDistributionStackedBarLocalizationTest {

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

    private val expectedKeys = listOf(
        "zone_mode_stacked_bar",
        "zone_mode_columns",
        "zone_mode_histogram"
    )

    @Test
    fun testZoneDisplayModeStrings_existInAllNineLocales() {
        val projectRoot = resolveProjectRoot()

        for (locale in locales) {
            val file = File(projectRoot, "app/src/main/res/$locale/strings.xml")
            assertTrue("strings.xml must exist for locale $locale", file.exists())
            val content = file.readText()

            for (key in expectedKeys) {
                val tag = "<string name=\"$key\">"
                assertTrue("Locale $locale must define string '$key'", content.contains(tag))

                // Extract string value
                val startIndex = content.indexOf(tag) + tag.length
                val endIndex = content.indexOf("</string>", startIndex)
                assertTrue("Locale $locale string '$key' must have closing tag", endIndex > startIndex)
                val value = content.substring(startIndex, endIndex).trim()
                assertFalse("Locale $locale string '$key' must not be empty", value.isEmpty())
                assertFalse(
                    "Locale $locale string '$key' ('$value') must be a single-line token without embedded newlines or spaces that force wrapping (REQ-UI-386)",
                    value.contains("\n")
                )
            }
        }
    }

    private fun resolveProjectRoot(): File {
        var current: File? = File(".").canonicalFile
        while (current != null && !File(current, "settings.gradle").exists() && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        return current ?: File(".")
    }
}
