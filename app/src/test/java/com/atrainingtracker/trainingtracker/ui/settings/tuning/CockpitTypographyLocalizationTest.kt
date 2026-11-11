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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Localization parity and naming contract test for tuning_cat_cockpit_typography (REQ-UI-277, TST-UI-237.2).
 * Verifies that all 9 supported languages include cockpit tile styling in the section title.
 * Refactored to LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class CockpitTypographyLocalizationTest {

    @Test
    fun testTuningCatCockpitTypography_containsTileKeywordAcrossAll9Locales() {
        val expectations = mapOf(
            "values" to "Cockpit &amp; Tiles",
            "values-de" to "Cockpit &amp; Kacheln",
            "values-es" to "Cabina y celdas",
            "values-fr" to "Cockpit et tuiles",
            "values-it" to "Cockpit e riquadri",
            "values-ja" to "コックピットとタイル",
            "values-nl" to "Cockpit en tegels",
            "values-pl" to "Kokpit i kafelki",
            "values-pt" to "Cockpit e blocos"
        )

        for ((folder, expectedValue) in expectations) {
            val strings = LocalizationTestCache.getStrings(folder, "strings.xml")
            val actualValue = strings["tuning_cat_cockpit_typography"]
            assertNotNull("tuning_cat_cockpit_typography must exist in $folder", actualValue)
            assertEquals("Locale $folder mismatch", expectedValue, actualValue)
        }
    }
}
