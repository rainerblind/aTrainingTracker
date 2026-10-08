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

package com.atrainingtracker.trainingtracker.ui.climbs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 9-Language Localization Parity Test for Climb Detail view strings (REQ-UI-300, TST-UI-260).
 */
class ClimbDetailLocalizationTest {

    private val supportedLocales = listOf(
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
        "routes_climb_max_grade",
        "routes_climb_avg_grade",
        "routes_climb_start_at",
        "routes_climbs_section_title",
        "climb_route_counter"
    )

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    @Test
    fun testClimbDetailLocalization_allKeysExistAcrossAll9Languages() {
        val resDir = File(projectRoot, "app/src/main/res")
        assertTrue("app/src/main/res must exist", resDir.exists())

        for (locale in supportedLocales) {
            val stringsFile = File(resDir, "$locale/strings.xml")
            assertTrue("strings.xml must exist for locale $locale at ${stringsFile.absolutePath}", stringsFile.exists())
            val content = stringsFile.readText()

            for (key in requiredKeys) {
                val pattern = Regex("<string\\s+name=\"$key\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
                val match = pattern.find(content)
                assertTrue("Locale '$locale' must define string resource '$key'", match != null)
                val text = match!!.groupValues[1].trim()
                assertTrue("Locale '$locale' key '$key' must not be empty", text.isNotEmpty())
            }
        }
    }
}
