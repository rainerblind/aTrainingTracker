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

package com.atrainingtracker.trainingtracker.ui.sportingjourney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Localization contract tests for "The Trophy Vault — My Sporting Journey" (REQ-SPT-005.1, REQ-SPT-005.2, TST-SPT-006.1).
 *
 * Verifies:
 * 1. Zero decorative inline Unicode emojis across all 9 supported locales (ATT-5400 / REQ-UI-458.3).
 * 2. Elimination of Strava trademark "Local Legend" and developer jargon "SQLite".
 * 3. 100% key parity across all 9 locale directories.
 */
class SportingJourneyLocalizationContractTest {

    private val localeDirs = listOf(
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

    // ATT-5400 regex pattern forbidding decorative emojis in string resources
    private val emojiPattern = Pattern.compile("[\\x{1F300}-\\x{1F64F}\\x{1F680}-\\x{1F6FF}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}]")

    private fun findResDir(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("Cannot find res directory among candidates: $candidates")
    }

    @Test
    fun testAllLocales_zeroEmojisAndZeroJargon() {
        val resDir = findResDir()
        for (dirName in localeDirs) {
            val file = File(resDir, "$dirName/strings_sporting_journey.xml")
            assertTrue("File must exist: ${file.path}", file.exists())
            val content = file.readText()

            // 1. Emoji check (ATT-5400)
            val matcher = emojiPattern.matcher(content)
            val foundEmojis = mutableListOf<String>()
            while (matcher.find()) {
                foundEmojis.add(matcher.group())
            }
            assertTrue(
                "Locale '$dirName' must contain zero decorative emojis per ATT-5400, but found: $foundEmojis",
                foundEmojis.isEmpty()
            )

            // 2. Strava trademark check
            assertFalse(
                "Locale '$dirName' must not contain Strava trademark 'Local Legend'",
                content.contains("Local Legend")
            )

            // 3. Raw SQLite developer jargon check
            assertFalse(
                "Locale '$dirName' must not contain raw developer jargon 'SQLite'",
                content.contains("SQLite")
            )

            // 4. Prototype phrasing check
            assertFalse(
                "Locale '$dirName' must not contain prototype phrase 'Season Wrapped'",
                content.contains("Season Wrapped")
            )
        }
    }

    @Test
    fun testAllLocales_keyParity() {
        val resDir = findResDir()
        val keyPattern = Pattern.compile("""name="([^"]+)"""")

        val baseFile = File(resDir, "values/strings_sporting_journey.xml")
        val baseKeys = mutableSetOf<String>()
        val baseMatcher = keyPattern.matcher(baseFile.readText())
        while (baseMatcher.find()) {
            baseMatcher.group(1)?.let { baseKeys.add(it) }
        }

        assertTrue("Base file must declare keys", baseKeys.isNotEmpty())

        for (dirName in localeDirs.filter { it != "values" }) {
            val file = File(resDir, "$dirName/strings_sporting_journey.xml")
            val localeKeys = mutableSetOf<String>()
            val matcher = keyPattern.matcher(file.readText())
            while (matcher.find()) {
                matcher.group(1)?.let { localeKeys.add(it) }
            }

            val missing = baseKeys - localeKeys
            val extra = localeKeys - baseKeys
            assertEquals(
                "Locale '$dirName' must have 100% key parity with base. Missing: $missing, Extra: $extra",
                baseKeys,
                localeKeys
            )
        }
    }
}
