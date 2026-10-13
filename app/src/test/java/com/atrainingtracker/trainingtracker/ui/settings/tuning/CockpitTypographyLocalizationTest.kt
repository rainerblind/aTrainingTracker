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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Localization parity and naming contract test for tuning_cat_cockpit_typography (REQ-UI-277, TST-UI-237.2).
 * Verifies that all 9 supported languages include cockpit tile styling in the section title.
 */
class CockpitTypographyLocalizationTest {

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidates: $candidates")
    }

    private fun extractStringValue(xmlFile: File, stringName: String): String {
        assertTrue("String XML file must exist: ${xmlFile.absolutePath}", xmlFile.exists())
        val content = xmlFile.readText()
        val regex = Regex("""<string\s+name=["']$stringName["'][^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(content)
            ?: error("String resource '$stringName' not found in ${xmlFile.path}")
        return match.groupValues[1]
    }

    @Test
    fun testTuningCatCockpitTypography_containsTileKeywordAcrossAll9Locales() {
        val resDir = findResDirectory()

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
            val stringsFile = File(resDir, "$folder/strings.xml")
            val actualValue = extractStringValue(stringsFile, "tuning_cat_cockpit_typography")
            assertTrue(
                "Locale $folder should have value '$expectedValue', but found '$actualValue'",
                actualValue == expectedValue
            )
        }
    }
}
