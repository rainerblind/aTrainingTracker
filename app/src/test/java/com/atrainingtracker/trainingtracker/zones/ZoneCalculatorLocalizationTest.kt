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
 * across all 9 supported locales under REQ-UI-373.9.
 *
 * Covers [TST-UI-336.7].
 */
class ZoneCalculatorLocalizationTest {

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
        "zone_calc_button",
        "zone_calc_dialog_title_power",
        "zone_calc_dialog_title_hr",
        "zone_calc_ftp_label",
        "zone_calc_hr_max_label",
        "zone_calc_hr_rest_label",
        "zone_calc_method_label",
        "zone_calc_method_max_hr",
        "zone_calc_method_karvonen",
        "zone_calc_preview_title",
        "zone_calc_apply_button",
        "zone_calc_cancel_button",
        "zone_calc_status_calculated",
        "zone_calc_status_customized",
        "zone_calc_calibration_recommended",
        "zone_calc_last_updated",
        "zone_calc_overwrite_warning_title",
        "zone_calc_overwrite_warning_message",
        "zone_calc_error_ftp_range",
        "zone_calc_error_hr_max_range",
        "zone_calc_error_hr_rest_range",
        "zone_calc_error_hr_max_greater_rest",
        "zone_calc_error_karvonen_delta",
        "zone_calc_error_monotonicity"
    )

    private val formatSpecifierPattern = Pattern.compile("%(\\d+\\$)?[\\d\\.]*[dfsu%]")

    private fun readMergedLocaleStrings(projectRoot: File, locale: String): String {
        val dir = File(projectRoot, "app/src/main/res/$locale")
        assertTrue("Locale directory must exist for $locale", dir.exists() && dir.isDirectory)
        val xmlFiles = dir.listFiles { f -> f.isFile && f.name.startsWith("strings") && f.name.endsWith(".xml") }
            ?.sortedBy { it.name }
            .orEmpty()
        assertTrue("At least one strings*.xml must exist for locale $locale", xmlFiles.isNotEmpty())
        return xmlFiles.joinToString("\n") { it.readText() }
    }

    @Test
    fun testZoneCalculationStrings_existInAllNineLocales() {
        val projectRoot = resolveProjectRoot()

        for (locale in locales) {
            val content = readMergedLocaleStrings(projectRoot, locale)

            for (key in expectedKeys) {
                val value = extractStringValueOrNull(content, key)
                assertTrue("Locale $locale must define string '$key'", value != null)
                assertFalse("Locale $locale string '$key' must not be empty", value!!.isEmpty())
            }
        }
    }

    @Test
    fun testZoneCalculationStrings_formatSpecifierParityAcrossLocales() {
        val projectRoot = resolveProjectRoot()

        val baseContent = readMergedLocaleStrings(projectRoot, "values")

        val baseTokensByKey = mutableMapOf<String, List<String>>()
        for (key in expectedKeys) {
            val value = extractStringValue(baseContent, key)
            val tokens = extractTokens(value)
            baseTokensByKey[key] = tokens
        }

        for (locale in locales.filter { it != "values" }) {
            val content = readMergedLocaleStrings(projectRoot, locale)

            for (key in expectedKeys) {
                val value = extractStringValue(content, key)
                val tokens = extractTokens(value)
                val expectedTokens = baseTokensByKey[key] ?: emptyList()

                assertEquals(
                    "Format token count mismatch for key '$key' in locale $locale",
                    expectedTokens.size,
                    tokens.size
                )

                // If tokens use positional indexing (%1$d, %2$d), sort and verify exact match
                assertEquals(
                    "Format tokens must match base string for key '$key' in locale $locale",
                    expectedTokens.sorted(),
                    tokens.sorted()
                )
            }
        }
    }

    private fun extractStringValueOrNull(content: String, key: String): String? {
        val regex = Regex("""<string\s+[^>]*name="${Regex.escape(key)}"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(content)?.groupValues?.get(1)?.trim()
    }

    private fun extractStringValue(content: String, key: String): String {
        return extractStringValueOrNull(content, key)
            ?: throw AssertionError("String '$key' not found")
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
