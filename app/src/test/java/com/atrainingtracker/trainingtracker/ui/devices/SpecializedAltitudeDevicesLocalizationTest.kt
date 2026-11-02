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

package com.atrainingtracker.trainingtracker.ui.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TST-SEN-026.1 / TST-SEN-026.4: 9-Language Localization Audit verifying that
 * `devices_altitude_from_pressure` is renamed to altitude terminology and all
 * specialized altitude device explanation strings exist with non-empty values
 * across all 9 supported application locales (REQ-SEN-026, ATT-5073).
 */
class SpecializedAltitudeDevicesLocalizationTest {

    private val locales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private val expectedRenamedAltitudeValues = mapOf(
        "" to "Altitude from Pressure",
        "de" to "Höhe aus Luftdruck",
        "es" to "Altitud por presión",
        "fr" to "Altitude par pression",
        "it" to "Altitudine da pressione",
        "ja" to "気圧からの高度",
        "nl" to "Hoogte uit luchtdruk",
        "pl" to "Wysokość z ciśnienia",
        "pt" to "Altitude por pressão"
    )

    private val expectedExplanationKeys = listOf(
        "device_altitude_explanation_title",
        "device_altitude_how_it_works_header",
        "device_altitude_pros_cons_header",
        "device_altitude_pressure_how_it_works",
        "device_altitude_pressure_pros_cons",
        "device_altitude_position_how_it_works",
        "device_altitude_position_pros_cons"
    )

    @Test
    fun testDevicesAltitudeFromPressure_renamedAcrossAll9Locales() {
        val resDir = findResDirectory()

        for (locale in locales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val file = File(resDir, "$dirName/strings_devices.xml")
            assertTrue("strings_devices.xml must exist for locale $dirName", file.exists())

            val stringMap = parseStringsFile(file)
            val actual = stringMap["devices_altitude_from_pressure"]
            val expected = expectedRenamedAltitudeValues[locale]

            assertEquals(
                "devices_altitude_from_pressure in $dirName must match expected altitude name",
                expected,
                actual
            )
            // Explicitly assert legacy raw pressure names are replaced
            assertNotEquals("Luftdruck", actual)
            assertNotEquals("Air Pressure", actual)
        }
    }

    @Test
    fun testAltitudeExplanationKeys_existAndNonEmptyAcrossAll9Locales() {
        val resDir = findResDirectory()

        for (locale in locales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val file = File(resDir, "$dirName/strings_devices.xml")
            assertTrue("strings_devices.xml must exist for locale $dirName", file.exists())

            val stringMap = parseStringsFile(file)
            for (key in expectedExplanationKeys) {
                assertTrue(
                    "Key '$key' must exist in $dirName/strings_devices.xml",
                    stringMap.containsKey(key)
                )
                assertFalse(
                    "Value for '$key' in $dirName/strings_devices.xml must not be blank",
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
