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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit test verifying 9-language localization parity and non-empty content
 * for precise location explanation (REQ-PRI-004, TST-PRI-003, ATT-2357).
 * Refactored to LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class PermissionLocalizationTest {

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
    fun testPreciseLocationExplanationExistsInAllNineLocales() {
        val targetKey = "permission_rationale_precise_location_explanation"

        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale)
            val value = strings[targetKey]
            assertNotNull("Key '$targetKey' must exist in $locale/strings.xml", value)
            assertFalse("String value for '$targetKey' in $locale must not be blank", value!!.isBlank())
        }
    }
}
