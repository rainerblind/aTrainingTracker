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

package com.atrainingtracker.trainingtracker.ui.tracking

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Unit test verifying 100% 9-language localization parity for all tracking tab WYSIWYG
 * strings introduced in ATT-2360 (REQ-UI-275, TST-UI-235.4).
 */
class TrackingTabLocalizationTest {

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
        "config_tracking__show_navigation_hints",
        "config_tracking__show_live_climbs",
        "config_tracking__wysiwyg_active",
        "config_tracking__wysiwyg_hidden"
    )

    @Test
    fun testAllRequiredKeysExistAcrossAll9Locales() {
        for (locale in locales) {
            val file = File("src/main/res/$locale/strings.xml")
            assertTrue("Locale file $file must exist", file.exists())
            val content = file.readText()

            for (key in requiredKeys) {
                val pattern = Pattern.compile("<string\\s+name=\"$key\">(.*?)</string>", Pattern.DOTALL)
                val matcher = pattern.matcher(content)
                assertTrue("Key '$key' must exist in $locale/strings.xml", matcher.find())
                val value = matcher.group(1)?.trim().orEmpty()
                assertTrue("Value for '$key' in $locale/strings.xml must not be empty", value.isNotEmpty())
            }
        }
    }
}
