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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Localization parity tests for the Zone Card Display Mode setting title across all 9 supported locales (TST-UI-335.11, REQ-UI-372.8).
 */
class ZoneCardDisplayModeLocalizationTest {

    private val expectedTranslations = mapOf(
        "values" to "Zone Card Display Mode",
        "values-de" to "Zonenkarten-Anzeigemodus",
        "values-es" to "Modo de visualización de tarjetas de zonas",
        "values-fr" to "Mode d\\'affichage des cartes de zones",
        "values-it" to "Modalità visualizzazione schede zone",
        "values-ja" to "ゾーンカード表示モード",
        "values-nl" to "Zonekaart-weergavemodus",
        "values-pl" to "Tryb wyświetlania kart stref",
        "values-pt" to "Modo de exibição dos cartões de zonas"
    )

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidates: $candidates")
    }

    @Test
    fun testZoneCardDisplayModeTitlePresentInAllNineLocales() {
        val resDir = findResDirectory()
        val regex = Regex("""<string\s+name="settings_zone_card_display_mode_title">(.*?)</string>""")

        for ((folder, expectedText) in expectedTranslations) {
            val stringsFile = File(resDir, "$folder/strings.xml")
            assertTrue("strings.xml must exist for locale $folder", stringsFile.exists())

            val content = stringsFile.readText()
            val match = regex.find(content)
            assertTrue(
                "settings_zone_card_display_mode_title missing in $folder/strings.xml",
                match != null
            )
            val extractedText = match!!.groupValues[1]
            assertEquals(
                "Translation mismatch in $folder/strings.xml",
                expectedText,
                extractedText
            )
        }
    }
}
