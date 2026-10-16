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

package com.atrainingtracker.trainingtracker.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization parity tests verifying all 15 obsolete map camera tuning string resources
 * are completely removed across all 9 supported locales (REQ-MAP-050 / ATT-3581).
 */
class MapCameraLocalizationTest {

    private val locales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private val obsoleteKeys = listOf(
        "tuning_map_camera_section_title",
        "tuning_map_base_zoom_title",
        "tuning_map_base_zoom_desc",
        "tuning_map_speed_zoom_enabled_title",
        "tuning_map_speed_zoom_enabled_desc",
        "tuning_map_cruising_zoom_title",
        "tuning_map_cruising_zoom_desc",
        "tuning_map_tilt_angle_title",
        "tuning_map_tilt_angle_desc",
        "tuning_map_lookahead_padding_title",
        "tuning_map_lookahead_padding_desc",
        "tuning_map_follow_me_speed_smoothing_title",
        "tuning_map_follow_me_speed_smoothing_desc",
        "tuning_map_follow_me_bearing_smoothing_title",
        "tuning_map_follow_me_bearing_smoothing_desc"
    )

    @Test
    fun testMapCameraStrings_purgedAcrossAll9Locales() {
        val resDir = findResDirectory()

        for (locale in locales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val file = File(resDir, "$dirName/strings.xml")
            assertTrue("strings.xml must exist for locale $dirName", file.exists())

            val stringMap = parseStringsFile(file)
            for (key in obsoleteKeys) {
                assertFalse(
                    "Obsolete key '$key' must NOT exist in $dirName/strings.xml per ATT-3581 / REQ-MAP-050",
                    stringMap.containsKey(key)
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
            val element = stringNodes.item(i) as Element
            val name = element.getAttribute("name")
            val text = element.textContent
            map[name] = text
        }
        return map
    }
}
