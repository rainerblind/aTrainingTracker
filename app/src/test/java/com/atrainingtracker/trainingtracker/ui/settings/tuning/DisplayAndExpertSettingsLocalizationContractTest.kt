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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural and localization contract test verifying that Display and Expert Settings
 * terminology, subtitle formatting, and warning deduplication comply with REQ-UI-472 and TST-UI-472.
 * Refactored to LocalizationTestCache and JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class DisplayAndExpertSettingsLocalizationContractTest {

    private val locales = listOf(
        "values", "values-de", "values-es", "values-fr",
        "values-it", "values-ja", "values-nl", "values-pl", "values-pt"
    )

    @Test
    fun testCockpitThemeDescription_doesNotContainControlTrackingInAnyLocale() {
        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale, "strings.xml")
            val text = strings["cockpit_theme_description"]
            assertNotNull("cockpit_theme_description missing in $locale", text)
            assertFalse(
                "cockpit_theme_description in $locale must not contain 'Control Tracking'",
                text!!.contains("Control Tracking")
            )
        }
    }

    @Test
    fun testGermanStrings_properlyLocalized() {
        val strings = LocalizationTestCache.getStrings("values-de", "strings.xml")

        val aftermath = strings["tuning_cat_aftermath"]
        assertEquals("Nachbereitung &amp; Profil-Analytik", aftermath)

        val warningTitle = strings["advanced_tuning_warning_title"]
        assertEquals("Hinweis", warningTitle)

        val searchActive = strings["tuning_sensor_search_active"]
        assertEquals("Aktive", searchActive)
    }

    @Test
    fun testAdvancedTuningWarningTitle_deduplicatedFromScreenTitle() {
        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale, "strings.xml")
            val warningTitle = strings["advanced_tuning_warning_title"]
            assertNotNull("advanced_tuning_warning_title missing in $locale", warningTitle)
            val screenTitle = strings["advanced_tuning_title"]
            assertNotNull("advanced_tuning_title missing in $locale", screenTitle)

            assertFalse(
                "advanced_tuning_warning_title in $locale must not be identical to advanced_tuning_title ($screenTitle)",
                warningTitle!!.equals(screenTitle, ignoreCase = true)
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
            val strings = LocalizationTestCache.getStrings(locale, "strings.xml")
            for (key in requiredKeys) {
                val value = strings[key]
                assertTrue("Missing key $key in $locale", value != null && value.isNotEmpty())
            }
        }
    }

    @Test
    fun testTuningWarningNotice_preservesBinding() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("TuningWarningNotice") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TuningWarningNotice composable must exist", method)
    }
}
