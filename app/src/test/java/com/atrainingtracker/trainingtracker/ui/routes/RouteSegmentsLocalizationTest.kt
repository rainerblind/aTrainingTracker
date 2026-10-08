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

package com.atrainingtracker.trainingtracker.ui.routes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TST-UI-262.3: 9-Language Localization Audit verifying all route segments strings
 * exist, are non-empty, and preserve format specifiers across all supported locales (REQ-UI-302, ATT-2583).
 */
class RouteSegmentsLocalizationTest {

    private val locales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private val expectedKeys = listOf(
        "routes_segments_section_title",
        "routes_segments_tab_title",
        "routes_segment_start_at",
        "routes_segment_pr",
        "routes_segment_counter"
    )

    @Test
    fun testRouteSegmentsStringsParityAcrossAll9Locales() {
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

            // Verify format specifiers
            val sectionTitle = stringMap["routes_segments_section_title"]!!
            assertTrue("routes_segments_section_title in $dirName must contain %d", sectionTitle.contains("%d"))

            val tabTitle = stringMap["routes_segments_tab_title"]!!
            assertTrue("routes_segments_tab_title in $dirName must contain %d", tabTitle.contains("%d"))

            val startAt = stringMap["routes_segment_start_at"]!!
            assertTrue("routes_segment_start_at in $dirName must contain %s", startAt.contains("%s"))

            val pr = stringMap["routes_segment_pr"]!!
            assertTrue("routes_segment_pr in $dirName must contain %s", pr.contains("%s"))

            val counter = stringMap["routes_segment_counter"]!!
            assertTrue("routes_segment_counter in $dirName must contain %1\$d", counter.contains("%1\$d"))
            assertTrue("routes_segment_counter in $dirName must contain %2\$d", counter.contains("%2\$d"))
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
