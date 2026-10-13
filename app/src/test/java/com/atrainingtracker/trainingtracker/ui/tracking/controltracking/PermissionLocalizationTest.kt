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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit test verifying 9-language localization parity and non-empty content
 * for precise location explanation (REQ-PRI-004, TST-PRI-003, ATT-2357).
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

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testPreciseLocationExplanationExistsInAllNineLocales() {
        val targetKey = "permission_rationale_precise_location_explanation"
        val regex = Regex("""<string name="$targetKey">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

        for (locale in locales) {
            val file = findFile("src/main/res/$locale/strings.xml")
            assertTrue("strings.xml must exist for locale $locale", file.exists())
            val text = file.readText()

            val match = regex.find(text)
            assertTrue("Key '$targetKey' must exist in $locale/strings.xml", match != null)

            val value = match?.groupValues?.get(1)?.trim() ?: ""
            assertFalse("String value for '$targetKey' in $locale must not be blank", value.isBlank())
        }
    }
}
