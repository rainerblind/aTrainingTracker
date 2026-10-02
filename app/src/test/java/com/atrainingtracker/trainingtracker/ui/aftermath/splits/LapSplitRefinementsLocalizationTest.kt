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

package com.atrainingtracker.trainingtracker.ui.aftermath.splits

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization audit test verifying 100% 9-language translation parity
 * for Lap Split Visualizer truncation toggle keys (REQ-UI-228 / TST-UI-182).
 */
class LapSplitRefinementsLocalizationTest {

    private val resDir = File("src/main/res")

    private val locales = listOf(
        "de", "es", "fr", "it", "ja", "nl", "pl", "pt"
    )

    private val expectedKeys = listOf(
        "show_all_laps",
        "show_fewer_laps"
    )

    private fun parseStrings(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (!file.exists()) return map

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        doc.documentElement.normalize()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                val name = element.getAttribute("name")
                val text = element.textContent
                map[name] = text
            }
        }
        return map
    }

    @Test
    fun defaultLocale_containsToggleKeys() {
        val defaultFile = File(resDir, "values/strings.xml")
        assertTrue("Default strings.xml must exist at ${defaultFile.absolutePath}", defaultFile.exists())
        val defaultStrings = parseStrings(defaultFile)

        expectedKeys.forEach { key ->
            assertTrue("Default strings.xml missing key: $key", defaultStrings.containsKey(key))
            assertFalse("Default key '$key' must not be blank", defaultStrings[key].isNullOrBlank())
        }

        assertTrue("show_all_laps must contain %1\$d positional argument", defaultStrings["show_all_laps"]!!.contains("%1\$d"))
    }

    @Test
    fun allLocales_containToggleKeys_withPositionalArguments() {
        locales.forEach { locale ->
            val localeFile = File(resDir, "values-$locale/strings.xml")
            assertTrue("strings.xml for locale '$locale' must exist at ${localeFile.absolutePath}", localeFile.exists())
            val localeStrings = parseStrings(localeFile)

            expectedKeys.forEach { key ->
                assertTrue(
                    "Locale '$locale' is missing translation for key: $key",
                    localeStrings.containsKey(key)
                )
                val text = localeStrings[key]
                assertNotNull("Translation for '$key' in '$locale' is null", text)
                assertFalse("Translation for '$key' in '$locale' is empty or blank", text.isNullOrBlank())
            }

            assertTrue(
                "Locale '$locale' show_all_laps must contain %1\$d positional argument",
                localeStrings["show_all_laps"]!!.contains("%1\$d")
            )
        }
    }
}
