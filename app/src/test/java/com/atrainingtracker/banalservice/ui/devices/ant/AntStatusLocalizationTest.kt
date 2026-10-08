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

package com.atrainingtracker.banalservice.ui.devices.ant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit test verifying 9-language localization parity and non-empty content
 * for contextual ANT+ status guidance strings (REQ-UI-296, TST-UI-256, ATT-2513).
 */
class AntStatusLocalizationTest {

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

    private val translatableKeys = listOf(
        "ant_status_sheet_title",
        "ant_status_card_title",
        "ant_status_card_desc",
        "ant_status_card_action",
        "ant_status_usb_dongle_note",
        "ant_status_ble_alternative_note",
        "ant_service_installed",
        "ant_service_not_installed",
        "ant_service_install_button"
    )

    private val baseKeys = listOf(
        "ant_service_plugin_name",
        "ant_service_radio_name",
        "ant_service_usb_name"
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
    fun testAntStatusStringsExistInAllNineLocales() {
        for (targetKey in translatableKeys) {
            val regex = Regex("""<string name="$targetKey"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

            for (locale in locales) {
                val file = findFile("src/main/res/$locale/strings.xml")
                assertTrue("strings.xml does not exist for $locale", file.exists())

                val content = file.readText()
                val match = regex.find(content)
                assertTrue("Target string key '$targetKey' missing in $locale", match != null)

                val text = match!!.groupValues[1].trim()
                assertFalse("Target string key '$targetKey' is empty in $locale", text.isEmpty())
            }
        }
    }

    @Test
    fun testBaseAntServiceNamesExistInDefaultLocale() {
        val defaultFile = findFile("src/main/res/values/strings.xml")
        val content = defaultFile.readText()

        for (targetKey in baseKeys) {
            val regex = Regex("""<string name="$targetKey"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(content)
            assertTrue("Base string key '$targetKey' missing in default values/strings.xml", match != null)
            val text = match!!.groupValues[1].trim()
            assertFalse("Base string key '$targetKey' is empty", text.isEmpty())
        }
    }
}
