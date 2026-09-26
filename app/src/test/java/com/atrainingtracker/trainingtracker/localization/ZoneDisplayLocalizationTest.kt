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

package com.atrainingtracker.trainingtracker.localization

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verification test (TST-UI-124) enforcing complete localization parity for all Zone Display
 * configuration string resources across all 9 supported locales.
 */
class ZoneDisplayLocalizationTest {

    private val locales = listOf("de", "es", "fr", "it", "ja", "nl", "pl", "pt")
    private val expectedKeys = listOf(
        "zone_display_title",
        "zone_display_background",
        "zone_display_left_bar",
        "zone_display_right_bar",
        "zone_display_text_color",
        "zone_display_preview_title"
    )

    private lateinit var resDir: File

    @Before
    fun setUp() {
        var dir = File("app/src/main/res")
        if (!dir.exists()) {
            dir = File("src/main/res")
        }
        assertTrue("res directory must exist", dir.exists())
        resDir = dir
    }

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
    fun defaultLocale_containsAllZoneDisplayKeys() {
        val defaultFile = File(resDir, "values/strings.xml")
        assertTrue("Default strings.xml must exist", defaultFile.exists())
        val defaultStrings = parseStrings(defaultFile)

        expectedKeys.forEach { key ->
            assertTrue("Default strings.xml missing key: $key", defaultStrings.containsKey(key))
            assertFalse("Default key $key must not have empty text", defaultStrings[key].isNullOrBlank())
        }
    }

    @Test
    fun allLocales_containAllZoneDisplayKeys_withNonEmptyTranslations() {
        locales.forEach { locale ->
            val localeFile = File(resDir, "values-$locale/strings.xml")
            assertTrue("strings.xml for locale $locale must exist", localeFile.exists())
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
        }
    }
}
