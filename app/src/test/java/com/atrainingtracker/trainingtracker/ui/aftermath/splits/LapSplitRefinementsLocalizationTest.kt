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

package com.atrainingtracker.trainingtracker.ui.aftermath.splits

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Localization audit test verifying 100% 9-language translation parity
 * for Lap Split Visualizer truncation toggle keys (REQ-UI-228 / TST-UI-182).
 * Refactored to LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class LapSplitRefinementsLocalizationTest {

    private val locales = listOf(
        "de", "es", "fr", "it", "ja", "nl", "pl", "pt"
    )

    private val expectedKeys = listOf(
        "show_all_laps",
        "show_fewer_laps"
    )

    @Test
    fun testBaseValuesContainExpectedKeys() {
        val baseStrings = LocalizationTestCache.getStrings("values", "strings.xml")
        for (key in expectedKeys) {
            assertTrue("Base strings.xml must contain $key", baseStrings.containsKey(key))
            assertFalse("Base strings.xml must have non-blank $key", baseStrings[key].isNullOrBlank())
        }
    }

    @Test
    fun testAllLocalesContainExpectedKeys() {
        val baseStrings = LocalizationTestCache.getStrings("values", "strings.xml")

        for (locale in locales) {
            val localeStrings = LocalizationTestCache.getStrings("values-$locale", "strings.xml")

            for (key in expectedKeys) {
                assertTrue("Locale $locale must contain key $key", localeStrings.containsKey(key))
                val value = localeStrings[key]
                assertNotNull("Locale $locale key $key must not be null", value)
                assertFalse("Locale $locale key $key must not be blank", value.isNullOrBlank())

                // For non-English locales, verify key is actually translated (different from base)
                if (locale != "en") {
                    val baseValue = baseStrings[key]
                    assertTrue(
                        "Key $key in locale $locale should be translated (was identical to base: '$baseValue')",
                        value != baseValue
                    )
                }
            }
        }
    }
}
