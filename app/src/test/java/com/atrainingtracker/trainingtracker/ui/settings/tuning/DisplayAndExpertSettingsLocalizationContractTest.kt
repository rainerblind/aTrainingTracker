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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and localization contract test verifying that Display and Expert Settings
 * terminology, subtitle formatting, and warning deduplication comply with REQ-UI-472 and TST-UI-472.
 */
class DisplayAndExpertSettingsLocalizationContractTest {

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file $relativePath not found in candidates: $candidates")
    }

    private val locales = listOf(
        "values", "values-de", "values-es", "values-fr",
        "values-it", "values-ja", "values-nl", "values-pl", "values-pt"
    )

    private fun extractString(xmlContent: String, stringName: String): String? {
        val pattern = Regex("""<string\s+name=["']$stringName["'][^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(xmlContent)?.groupValues?.get(1)?.trim()
    }

    @Test
    fun testCockpitThemeDescription_doesNotContainControlTrackingInAnyLocale() {
        for (locale in locales) {
            val file = findSourceFile("src/main/res/$locale/strings.xml")
            val content = file.readText()
            val text = extractString(content, "cockpit_theme_description")
                ?: error("cockpit_theme_description missing in $locale")
            assertFalse(
                "cockpit_theme_description in $locale must not contain 'Control Tracking'",
                text.contains("Control Tracking")
            )
        }
    }

    @Test
    fun testGermanStrings_properlyLocalized() {
        val deFile = findSourceFile("src/main/res/values-de/strings.xml")
        val deContent = deFile.readText()

        val aftermath = extractString(deContent, "tuning_cat_aftermath")
        assertEquals("Nachbereitung &amp; Profil-Analytik", aftermath)

        val warningTitle = extractString(deContent, "advanced_tuning_warning_title")
        assertEquals("Hinweis", warningTitle)

        val searchActive = extractString(deContent, "tuning_sensor_search_active")
        assertEquals("Aktive", searchActive)
    }

    @Test
    fun testAdvancedTuningWarningTitle_deduplicatedFromScreenTitle() {
        for (locale in locales) {
            val file = findSourceFile("src/main/res/$locale/strings.xml")
            val content = file.readText()
            val warningTitle = extractString(content, "advanced_tuning_warning_title")
                ?: error("advanced_tuning_warning_title missing in $locale")
            val screenTitle = extractString(content, "advanced_tuning_title")
                ?: error("advanced_tuning_title missing in $locale")

            assertFalse(
                "advanced_tuning_warning_title in $locale must not be identical to advanced_tuning_title ($screenTitle)",
                warningTitle.equals(screenTitle, ignoreCase = true)
            )
        }
    }

    @Test
    fun testAll9Locales_containNewSubtitleKeys() {
        val requiredKeys = listOf(
            "tuning_battery_saver_flat_short",
            "tuning_battery_saver_steep_short",
            "tuning_sensors_alt_short",
            "tuning_sensors_speed_short",
            "tuning_cluster_end_short",
            "tuning_cluster_apex_short",
            "tuning_cluster_dist_short",
            "tuning_cluster_alt_short"
        )

        for (locale in locales) {
            val file = findSourceFile("src/main/res/$locale/strings.xml")
            val content = file.readText()
            for (key in requiredKeys) {
                val value = extractString(content, key)
                assertTrue("Missing key $key in $locale", value != null && value.isNotEmpty())
            }
        }
    }

    @Test
    fun testTuningWarningNotice_preservesBinding() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningFormControls.kt")
        val content = file.readText()
        assertTrue(
            "TuningWarningNotice must bind R.string.advanced_tuning_warning_title",
            content.contains("R.string.advanced_tuning_warning_title")
        )
    }
}
