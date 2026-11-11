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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Localization parity tests for the Zone Card Display Mode setting title across all 9 supported locales (TST-UI-335.11, REQ-UI-372.8).
 * Refactored to LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
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

    @Test
    fun testZoneCardDisplayModeTitlePresentInAllNineLocales() {
        for ((folder, expectedText) in expectedTranslations) {
            val strings = LocalizationTestCache.getStrings(folder, "strings.xml")
            val extractedText = strings["settings_zone_card_display_mode_title"]
            assertNotNull("settings_zone_card_display_mode_title missing in $folder/strings.xml", extractedText)
            assertEquals(
                "Translation mismatch in $folder/strings.xml",
                expectedText,
                extractedText
            )
        }
    }
}
