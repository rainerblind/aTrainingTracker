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

package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Localization parity test for Aftermath profile tuning string resources (REQ-UI-201, TST-UI-155.5).
 * Validates presence and non-blank values across all 9 supported locales:
 * en (default), de, es, fr, it, ja, nl, pl, pt.
 * Refactored to LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class AftermathTuningLocalizationTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

    private val requiredKeys = listOf(
        "tuning_cat_aftermath",
        "tuning_profile_x_axis_title",
        "tuning_profile_x_axis_desc",
        "tuning_profile_x_axis_distance",
        "tuning_profile_x_axis_time",
        "tuning_telemetry_x_axis_title",
        "tuning_telemetry_x_axis_desc",
        "tuning_pace_ceiling_title",
        "tuning_pace_ceiling_desc",
        "tuning_elevation_smoothing_sigma_title",
        "tuning_elevation_smoothing_sigma_desc"
    )

    @Test
    fun testAftermathTuningStringsParityAcrossAllLocales() {
        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val strings = LocalizationTestCache.getStrings(dirName, "strings.xml")

            for (key in requiredKeys) {
                val value = strings[key]
                assertNotNull("Missing key '$key' in $dirName", value)
                assertTrue(
                    "Key '$key' in $dirName must not be blank",
                    value!!.isNotBlank()
                )
            }
        }
    }
}
