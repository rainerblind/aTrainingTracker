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

package com.atrainingtracker.trainingtracker.ui.settings.supporter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Architectural and copy contract test for supporter and jubilee support appeals
 * (REQ-SET-086, REQ-REV-003, TST-SET-086, TST-REV-003, TST-REV-005 / ATT-5659).
 *
 * Verifies across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT):
 * 1. 5 required string keys exist with 100% parity.
 * 2. Zero dynamic format arguments in all 5 keys.
 * 3. Zero decorative emojis (ATT-5400).
 * 4. Elimination of buzzwords ("15+ Jahre", "Indie", "seit 2011", "Datenschutz", "unabhängig", etc.).
 * 5. Appreciative development support copy matches 1:1 in DE and EN.
 */
class SupporterCopyContractTest {

    private val supportedLocales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private val targetStringKeys = listOf(
        "supporter_screen_subtitle",
        "supporter_active_tier_thank_you",
        "supporter_core_invariant_body",
        "jubilee_supporter_appeal_title",
        "jubilee_supporter_appeal_message"
    )

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidate paths: $candidates")
    }

    private fun extractStringTagRawContent(stringsFile: File, stringKey: String): String? {
        val content = stringsFile.readText()
        val regex = Pattern.compile("<string\\s+name=[\"']$stringKey[\"'][^>]*>(.*?)</string>", Pattern.DOTALL)
        val matcher = regex.matcher(content)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun unescapeXml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("%%", "%")
            .trim()
    }

    @Test
    fun testAllFiveKeysExistInAllNineLocalesWithZeroFormatArguments() {
        val resDir = findResDirectory()

        for (locale in supportedLocales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$dirName/strings.xml")
            assertTrue("strings.xml must exist for locale '$locale' at ${stringsFile.path}", stringsFile.exists())

            for (key in targetStringKeys) {
                val rawValue = extractStringTagRawContent(stringsFile, key)
                assertNotNull("String key '$key' must exist in locale '$locale'", rawValue)
                assertFalse("String key '$key' must not be blank in locale '$locale'", rawValue!!.trim().isEmpty())

                // Invariant: Zero dynamic format arguments in all 5 keys
                val formatArgPattern = Pattern.compile("%(?:\\d+\\$)?[sdfoxXeEgGaA]")
                val matcher = formatArgPattern.matcher(rawValue)
                assertFalse(
                    "String key '$key' in locale '$locale' must not contain format specifiers (found in '$rawValue')",
                    matcher.find()
                )
            }
        }
    }

    @Test
    fun testZeroDecorativeEmojisInAllNineLocales() {
        val resDir = findResDirectory()
        // Emoji pattern forbidding inline decorative Unicode emojis (ATT-5400)
        val emojiPattern = Pattern.compile("[\\x{1F300}-\\x{1F64F}\\x{1F680}-\\x{1F6FF}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}]")

        for (locale in supportedLocales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$dirName/strings.xml")

            for (key in targetStringKeys) {
                val rawValue = extractStringTagRawContent(stringsFile, key) ?: continue
                val matcher = emojiPattern.matcher(rawValue)
                assertFalse(
                    "String key '$key' in locale '$locale' must not contain decorative emojis per ATT-5400 (found in '$rawValue')",
                    matcher.find()
                )
            }
        }
    }

    @Test
    fun testForbiddenMarketingBuzzwordsEliminatedFromAllNineLocales() {
        val resDir = findResDirectory()
        val forbiddenBuzzwords = listOf(
            "15+ Jahre", "15+ years", "15+ ans", "15+ jaar", "15+ lat", "15+ anos",
            "15年以上", "oltre 15 anni", "más de 15 años",
            "indie", "seit 2011", "since 2011", "depuis 2011", "dal 2011", "sinds 2011", "od 2011", "2011年",
            "datenschutz", "privacy", "privacité", "privacidad", "プライバシー",
            "unabhängig", "independent", "indépendan", "indipendente", "onafhankelijk", "niezależn", "independente"
        )

        for (locale in supportedLocales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$dirName/strings.xml")

            for (key in targetStringKeys) {
                val rawValue = extractStringTagRawContent(stringsFile, key) ?: continue
                val lower = rawValue.lowercase()

                for (buzzword in forbiddenBuzzwords) {
                    assertFalse(
                        "String key '$key' in locale '$locale' must NOT contain buzzword '$buzzword' (content: '$rawValue')",
                        lower.contains(buzzword.lowercase())
                    )
                }
            }
        }
    }

    @Test
    fun testGermanAppreciativeCopyExactMatch() {
        val resDir = findResDirectory()
        val stringsFile = File(resDir, "values-de/strings.xml")

        assertEquals(
            "Unterstütze die Weiterentwicklung von aTrainingTracker.",
            unescapeXml(extractStringTagRawContent(stringsFile, "supporter_screen_subtitle")!!)
        )
        assertEquals(
            "Danke, dass du die Entwicklung von aTrainingTracker unterstützt!",
            unescapeXml(extractStringTagRawContent(stringsFile, "supporter_active_tier_thank_you")!!)
        )
        assertEquals(
            "Trainingsaufzeichnung, Kartenanzeige, Sensordaten und Offline-GPX/TCX/FIT-Exporte bleiben dauerhaft 100% kostenlos und werbefrei. Abonnements finanzieren die kontinuierliche Weiterentwicklung der App.",
            unescapeXml(extractStringTagRawContent(stringsFile, "supporter_core_invariant_body")!!)
        )
        assertEquals(
            "Weiterentwicklung unterstützen",
            unescapeXml(extractStringTagRawContent(stringsFile, "jubilee_supporter_appeal_title")!!)
        )
        assertEquals(
            "Außerdem kannst du die Weiterentwicklung von aTrainingTracker auch finanziell unterstützen:",
            unescapeXml(extractStringTagRawContent(stringsFile, "jubilee_supporter_appeal_message")!!)
        )
    }

    @Test
    fun testEnglishAppreciativeCopyExactMatch() {
        val resDir = findResDirectory()
        val stringsFile = File(resDir, "values/strings.xml")

        assertEquals(
            "Support the ongoing development of aTrainingTracker.",
            unescapeXml(extractStringTagRawContent(stringsFile, "supporter_screen_subtitle")!!)
        )
        assertEquals(
            "Thank you for supporting the development of aTrainingTracker!",
            unescapeXml(extractStringTagRawContent(stringsFile, "supporter_active_tier_thank_you")!!)
        )
        assertEquals(
            "Core workout recording, map display, sensor telemetry, and offline GPX/TCX/FIT export remain 100% free and unmonetized forever. Subscriptions fund continuous development of the app.",
            unescapeXml(extractStringTagRawContent(stringsFile, "supporter_core_invariant_body")!!)
        )
        assertEquals(
            "Support Development",
            unescapeXml(extractStringTagRawContent(stringsFile, "jubilee_supporter_appeal_title")!!)
        )
        assertEquals(
            "You can also financially support the ongoing development of aTrainingTracker:",
            unescapeXml(extractStringTagRawContent(stringsFile, "jubilee_supporter_appeal_message")!!)
        )
    }
}
