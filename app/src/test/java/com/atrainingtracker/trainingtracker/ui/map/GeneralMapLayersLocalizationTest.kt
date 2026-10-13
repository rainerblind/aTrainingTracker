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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TST-UI-282.4: 9-Language Localization Audit verifying all general map layer toggle strings
 * exist and are non-empty across all supported locales (REQ-UI-322, ATT-2931).
 */
class GeneralMapLayersLocalizationTest {

    private val locales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private val expectedKeys = listOf(
        "map_layers",
        "map_layer_routes",
        "map_layer_segments",
        "map_layer_locations",
        "map_layer_track"
    )

    @Test
    fun testGeneralMapLayersStringsParityAcrossAll9Locales() {
        val resDir = findResDirectory()

        for (locale in locales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val file = File(resDir, "$dirName/strings.xml")
            assertTrue("strings.xml must exist for locale $dirName", file.exists())

            val stringMap = parseStringsFile(file)
            for (key in expectedKeys) {
                assertTrue(
                    "Key '$key' must exist in $dirName/strings.xml",
                    stringMap.containsKey(key)
                )
                assertFalse(
                    "Value for '$key' in $dirName/strings.xml must not be blank",
                    stringMap[key].isNullOrBlank()
                )
            }
        }
    }

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidates: $candidates")
    }

    private fun parseStringsFile(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            val text = node.textContent
            if (name.isNotEmpty()) {
                map[name] = text
            }
        }
        return map
    }
}
