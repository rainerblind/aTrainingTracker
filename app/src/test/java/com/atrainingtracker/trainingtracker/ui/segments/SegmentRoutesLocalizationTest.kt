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

package com.atrainingtracker.trainingtracker.ui.segments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TST-UI-265.3: 9-Language Localization Audit verifying all segment routes strings
 * exist, are non-empty, and preserve format specifiers across all supported locales (REQ-UI-305, ATT-2585).
 */
class SegmentRoutesLocalizationTest {

    private val locales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private val expectedKeys = listOf(
        "segment_routes_containing_title"
    )

    @Test
    fun testSegmentRoutesStringsParityAcrossAll9Locales() {
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
                val value = stringMap[key] ?: ""
                assertTrue(
                    "Key '$key' in $dirName/strings.xml must contain format specifier (%d)",
                    value.contains("%d") || value.contains("%1\$d")
                )
            }
        }
    }

    private fun findResDirectory(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        val resDir = File(dir, "app/src/main/res")
        if (resDir.exists()) return resDir
        val fallback = File("src/main/res")
        if (fallback.exists()) return fallback
        throw IllegalStateException("Could not find app/src/main/res directory")
    }

    private fun parseStringsFile(file: File): Map<String, String> {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val stringNodes = doc.getElementsByTagName("string")
        val map = mutableMapOf<String, String>()

        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            if (node is Element) {
                val name = node.getAttribute("name")
                val content = node.textContent
                map[name] = content
            }
        }
        return map
    }
}
