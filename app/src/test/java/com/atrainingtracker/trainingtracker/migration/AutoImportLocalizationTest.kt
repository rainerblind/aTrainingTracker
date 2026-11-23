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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

/**
 * 9-language localization parity and format specifier consistency test for Auto-Import string resources
 * (REQ-MIG-052.7, TST-MIG-050.4, ATT-7748).
 */
class AutoImportLocalizationTest {

    private val requiredKeys = listOf(
        "tab_auto_import",
        "auto_import_title",
        "auto_import_master_switch",
        "auto_import_master_switch_summary",
        "auto_import_cloud_sources_title",
        "auto_import_dropbox_title",
        "auto_import_dropbox_summary",
        "auto_import_googledrive_title",
        "auto_import_googledrive_summary",
        "auto_import_format_title",
        "auto_import_relays_title",
        "auto_import_relay_health_connect",
        "auto_import_relay_health_connect_summary",
        "auto_import_relay_strava",
        "auto_import_relay_strava_summary",
        "auto_import_sync_now",
        "auto_import_syncing",
        "auto_import_last_sync",
        "auto_import_never_synced",
        "auto_import_status_success",
        "auto_import_status_error",
        "auto_import_cloud_not_connected",
        "auto_import_cloud_connected",
        "auto_import_format_fit",
        "auto_import_format_tcx",
        "auto_import_format_gpx",
        "auto_import_format_all"
    )

    private val formatSpecifierRegex = Pattern.compile("%(?:\\d+\\$)?[sdf]")

    @Test
    fun test9LocaleAutoImportStringsParity_TST_MIG_050_4() {
        val defaultStrings = LocalizationTestCache.getStrings("values", "strings_auto_import.xml")

        for (targetKey in requiredKeys) {
            assertTrue("Default locale (values) must define '$targetKey'", defaultStrings.containsKey(targetKey))
            val defaultValue = defaultStrings[targetKey]?.trim()
            assertNotNull("Default string for '$targetKey' must not be null", defaultValue)
            assertFalse("Default string for '$targetKey' must not be empty", defaultValue!!.isEmpty())

            val defaultSpecifiers = extractSpecifiers(defaultValue)

            for (locale in LocalizationTestCache.LOCALES) {
                val localeStrings = LocalizationTestCache.getStrings(locale, "strings_auto_import.xml")
                assertTrue("Locale '$locale' must define key '$targetKey'", localeStrings.containsKey(targetKey))

                val localizedValue = localeStrings[targetKey]?.trim()
                assertNotNull("Locale '$locale' key '$targetKey' must not be null", localizedValue)
                assertFalse("Locale '$locale' key '$targetKey' must not be empty", localizedValue!!.isEmpty())

                val localizedSpecifiers = extractSpecifiers(localizedValue)
                assertEquals(
                    "Locale '$locale' format specifiers for key '$targetKey' must match default locale",
                    defaultSpecifiers,
                    localizedSpecifiers
                )
            }
        }
    }

    @Test
    fun testAutoImportFormatSegmentLabels_areConciseSingleWordAcrossAllLocales() {
        val exactFormatLabels = mapOf(
            "auto_import_format_fit" to "FIT",
            "auto_import_format_tcx" to "TCX",
            "auto_import_format_gpx" to "GPX"
        )
        for (locale in LocalizationTestCache.LOCALES) {
            val localeStrings = LocalizationTestCache.getStrings(locale, "strings_auto_import.xml")
            for ((key, expected) in exactFormatLabels) {
                assertEquals(
                    "Locale '$locale' key '$key' must stay concise for 4-way SegmentedButtonRow",
                    expected,
                    localeStrings[key]?.trim()
                )
            }
            val allLabel = localeStrings["auto_import_format_all"]?.trim().orEmpty()
            assertTrue("Locale '$locale' auto_import_format_all must be non-empty", allLabel.isNotEmpty())
            assertFalse(
                "Locale '$locale' auto_import_format_all ('$allLabel') must be a single word without whitespace to prevent wrapping",
                allLabel.any { it.isWhitespace() }
            )
        }
    }

    private fun extractSpecifiers(text: String): List<String> {
        val matcher = formatSpecifierRegex.matcher(text)
        val result = mutableListOf<String>()
        while (matcher.find()) {
            result.add(matcher.group())
        }
        return result
    }
}
