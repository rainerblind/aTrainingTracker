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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Localization audit test verifying 100% translation parity for zone display mode strings
 * across all 9 supported locales under REQ-UI-372.
 * Refactored to LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
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
        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale, "strings.xml")

            for (key in expectedKeys) {
                val value = strings[key]
                assertNotNull("Locale $locale must define string '$key'", value)
                assertFalse("Locale $locale string '$key' must not be empty", value!!.isEmpty())
                assertFalse(
                    "Locale $locale string '$key' ('$value') must be a single-line token without embedded newlines or spaces that force wrapping (REQ-UI-386)",
                    value.contains("\n")
                )
            }
        }
    }
}
