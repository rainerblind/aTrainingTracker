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

package com.atrainingtracker.trainingtracker.ui.aftermath.crop

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Localization parity test for Crop Workout string resources (REQ-UI-368, TST-UI-331).
 * Validates presence and non-blank values across all 9 canonical locales:
 * en (default), de, es, fr, it, ja, nl, pl, pt.
 * Refactored to LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class CropWorkoutLocalizationTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

    private val requiredKeys = listOf(
        "crop_workout_title",
        "crop_workout_action",
        "crop_workout_confirm_title",
        "crop_workout_confirm_message",
        "crop_workout_confirm_action",
        "crop_workout_progress",
        "crop_workout_success",
        "crop_insufficient_storage",
        "crop_export_failed_retry",
        "crop_force_reexport"
    )

    @Test
    fun testCropWorkoutStringsParityAcrossAllLocales() {
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
