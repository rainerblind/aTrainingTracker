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

package com.atrainingtracker.trainingtracker.zones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Localization audit test verifying 100% translation parity and format specifier token parity
 * across all 9 supported locales for contextual zone onboarding strings (REQ-UI-374.5).
 *
 * Covers [TST-UI-337.5].
 */
class ZoneOnboardingLocalizationTest {

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

    private val expectedKeys = listOf(
        "zone_onboarding_hr_banner",
        "zone_onboarding_pwr_banner",
        "zone_onboarding_dismiss"
    )

    private val formatSpecifierPattern = Pattern.compile("%(\\d+\\$)?[\\d\\.]*[dfsu%]")

    @Test
    fun testZoneOnboardingStrings_existInAllNineLocales() {
        val projectRoot = resolveProjectRoot()

        for (locale in locales) {
            val file = File(projectRoot, "app/src/main/res/$locale/strings.xml")
            assertTrue("strings.xml must exist for locale $locale", file.exists())
            val content = file.readText()

            for (key in expectedKeys) {
                val tag = "<string name=\"$key\">"
                assertTrue("Locale $locale must define string '$key'", content.contains(tag))

                val startIndex = content.indexOf(tag) + tag.length
                val endIndex = content.indexOf("</string>", startIndex)
                assertTrue("Locale $locale string '$key' must have closing tag", endIndex > startIndex)
                val value = content.substring(startIndex, endIndex).trim()
                assertFalse("Locale $locale string '$key' must not be empty", value.isEmpty())
            }
        }
    }

    @Test
    fun testZoneOnboardingStrings_formatSpecifierParityAcrossLocales() {
        val projectRoot = resolveProjectRoot()

        val baseFile = File(projectRoot, "app/src/main/res/values/strings.xml")
        val baseContent = baseFile.readText()

        val baseTokensByKey = mutableMapOf<String, List<String>>()
        for (key in expectedKeys) {
            val value = extractStringValue(baseContent, key)
            val tokens = extractTokens(value)
            baseTokensByKey[key] = tokens
        }

        for (locale in locales.filter { it != "values" }) {
            val file = File(projectRoot, "app/src/main/res/$locale/strings.xml")
            val content = file.readText()

            for (key in expectedKeys) {
                val value = extractStringValue(content, key)
                val tokens = extractTokens(value)
                val expectedTokens = baseTokensByKey[key] ?: emptyList()

                assertEquals(
                    "Format token count mismatch for key '$key' in locale $locale",
                    expectedTokens.size,
                    tokens.size
                )

                assertEquals(
                    "Format tokens must match base string for key '$key' in locale $locale",
                    expectedTokens.sorted(),
                    tokens.sorted()
                )
            }
        }
    }

    private fun extractStringValue(content: String, key: String): String {
        val tag = "<string name=\"$key\">"
        val startIndex = content.indexOf(tag) + tag.length
        val endIndex = content.indexOf("</string>", startIndex)
        return content.substring(startIndex, endIndex).trim()
    }

    private fun extractTokens(text: String): List<String> {
        val matcher = formatSpecifierPattern.matcher(text)
        val tokens = mutableListOf<String>()
        while (matcher.find()) {
            tokens.add(matcher.group())
        }
        return tokens
    }

    private fun resolveProjectRoot(): File {
        var current: File? = File(".").canonicalFile
        while (current != null && !File(current, "settings.gradle").exists() && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        return current ?: File(".")
    }
}
