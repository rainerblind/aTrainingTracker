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

package com.atrainingtracker.banalservice.ui.devices

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit test verifying 9-language localization parity and non-empty content
 * for newly discovered sensor badge and notification strings (REQ-UI-334, TST-UI-294, ATT-3099).
 */
class DeviceDiscoveryLocalizationTest {

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

    private val targetKeys = listOf(
        "devices_newly_discovered_badge",
        "devices_new_sensor_discovered"
    )

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun verifyLocalizationKeysExistInAllLocales() {
        for (locale in locales) {
            val stringsFile = findFile("src/main/res/$locale/strings.xml")
            assertTrue("strings.xml must exist for locale $locale", stringsFile.exists())

            val content = stringsFile.readText()

            for (key in targetKeys) {
                val pattern = """<string\s+name="$key"[^>]*>(.*?)</string>""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val match = pattern.find(content)

                assertTrue(
                    "Key '$key' must exist in src/main/res/$locale/strings.xml",
                    match != null
                )

                val value = match!!.groupValues[1].trim()
                assertFalse(
                    "Key '$key' must not be empty in src/main/res/$locale/strings.xml",
                    value.isEmpty()
                )

                if (key == "devices_new_sensor_discovered") {
                    assertTrue(
                        "Key '$key' must contain '%1\$s' placeholder in src/main/res/$locale/strings.xml, found '$value'",
                        value.contains("%1\$s") || value.contains("%s")
                    )
                }
            }
        }
    }
}
