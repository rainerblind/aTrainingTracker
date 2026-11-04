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

package com.atrainingtracker.trainingtracker.monetization

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Contract audit test (TST-SET-088.7) ensuring all Gold and Diamond N+1 garage upgrade
 * nudge strings across all 9 locales contain zero decorative inline Unicode emojis
 * (relying on vector header icons per REQ-SET-088.5 and design guidelines Section 5.5)
 * while preserving strict indexed positional format specifiers (%1$d..%5$s).
 */
class GarageUpgradeNudgeStringsAuditTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")
    private val targetKeys = listOf(
        "garage_detector_dialog_title_gold",
        "garage_detector_dialog_title_diamond",
        "garage_detector_dialog_message_gold",
        "garage_detector_dialog_message_diamond"
    )

    private lateinit var resDir: File

    @Before
    fun setUp() {
        resDir = File("src/main/res").takeIf { it.exists() }
            ?: File("app/src/main/res").takeIf { it.exists() }
            ?: File("../app/src/main/res").takeIf { it.exists() }
            ?: File(System.getProperty("user.dir"), "app/src/main/res").takeIf { it.exists() }
            ?: error("Unable to locate res directory from ${System.getProperty("user.dir")}")
    }

    private fun parseStrings(stringsFile: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.parse(stringsFile)
        val stringNodes = doc.getElementsByTagName("string")

        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as? Element ?: continue
            val name = element.getAttribute("name")
            val text = element.textContent
            result[name] = text
        }
        return result
    }

    fun containsEmoji(text: String): Boolean {
        return text.codePoints().anyMatch { cp ->
            (cp in 0x1F000..0x1FAFF) || (cp in 0x2600..0x27BF) || cp == 0xFE0F || cp == 0x200D
        }
    }

    @Test
    fun testGarageUpgradeNudgeStrings_zeroEmojisAcrossAllNineLocales() {
        for (localeSuffix in locales) {
            val valuesDir = File(resDir, "values$localeSuffix")
            val stringsFile = File(valuesDir, "strings.xml")
            assertTrue("strings.xml must exist for locale values$localeSuffix", stringsFile.exists())

            val stringsMap = parseStrings(stringsFile)
            for (key in targetKeys) {
                assertTrue("Missing key $key in values$localeSuffix/strings.xml", stringsMap.containsKey(key))
                val text = stringsMap[key] ?: ""
                assertFalse(
                    "String '$key' in locale 'values$localeSuffix' contains inline Unicode emojis: '$text'",
                    containsEmoji(text)
                )
            }
        }
    }

    @Test
    fun testGarageUpgradeNudgeMessages_preservePositionalFormatSpecifiersAcrossAllNineLocales() {
        val expectedSpecifiers = listOf("%1\$d", "%2\$d", "%3\$d", "%4\$d", "%5\$s")
        val messageKeys = listOf("garage_detector_dialog_message_gold", "garage_detector_dialog_message_diamond")

        for (localeSuffix in locales) {
            val stringsFile = File(resDir, "values$localeSuffix/strings.xml")
            val stringsMap = parseStrings(stringsFile)

            for (key in messageKeys) {
                val text = stringsMap[key] ?: ""
                for (specifier in expectedSpecifiers) {
                    assertTrue(
                        "String '$key' in locale 'values$localeSuffix' missing specifier '$specifier': '$text'",
                        text.contains(specifier)
                    )
                }
            }
        }
    }
}
