/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying 9-language localization parity and non-empty content
 * for format-specific import blocks (FIT, TCX, GPX) and their action dyads (REQ-UI-293, REQ-UI-362, TST-UI-253, TST-UI-325, ATT-2623, ATT-3489).
 */
class ImportFormatLocalizationTest {

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

    private val requiredKeys = listOf(
        "import_fit_title",
        "import_fit_description",
        "import_fit_button",
        "import_fit_folder_button",
        "import_fit_dropbox_button",
        "import_tcx_title",
        "import_tcx_description",
        "import_tcx_button",
        "import_tcx_folder_button",
        "import_tcx_dropbox_button",
        "import_gpx_title",
        "import_gpx_description",
        "import_gpx_button",
        "import_gpx_folder_button",
        "import_gpx_dropbox_button",
        "import_directory_scanning",
        "import_directory_no_files_found",
        "import_strava_archive_title"
    )

    private val removedKeys = listOf(
        "import_fit_gdrive_button",
        "import_tcx_gdrive_button",
        "import_gpx_gdrive_button"
    )

    @Test
    fun testAllFormatImportStringsExistAndAreNonEmptyAcrossAllNineLocales() {
        for (targetKey in requiredKeys) {
            for (locale in locales) {
                val strings = LocalizationTestCache.getStrings(locale)
                assertTrue("Locale '$locale' must define key '$targetKey'", strings.containsKey(targetKey))
                val value = strings[targetKey]?.trim()
                assertNotNull("Locale '$locale' key '$targetKey' must not be null", value)
                assertFalse("Locale '$locale' key '$targetKey' must not be empty", value!!.isEmpty())
            }
        }
    }

    @Test
    fun testRemovedGoogleDriveScanStringsAreAbsentAcrossAllNineLocales() {
        for (removedKey in removedKeys) {
            for (locale in locales) {
                val strings = LocalizationTestCache.getStrings(locale)
                assertFalse(
                    "Locale '$locale' must NOT define removed key '$removedKey'",
                    strings.containsKey(removedKey)
                )
            }
        }
    }
}
