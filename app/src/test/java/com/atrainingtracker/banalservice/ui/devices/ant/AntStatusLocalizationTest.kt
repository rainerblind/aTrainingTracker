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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        "ant_service_installed",
        "ant_service_not_installed",
        "ant_service_install_button"
    )

    private val baseKeys = listOf(
        "ant_service_plugin_name",
        "ant_service_radio_name",
        "ant_service_usb_name"
    )

    @Test
    fun testAntStatusStringsExistInAllNineLocales() {
        for (targetKey in translatableKeys) {
            for (locale in locales) {
                val strings = LocalizationTestCache.getStrings(locale)
                assertTrue("Target string key '$targetKey' missing in $locale", strings.containsKey(targetKey))

                val text = strings[targetKey]?.trim()
                assertNotNull("Target string key '$targetKey' must not be null in $locale", text)
                assertFalse("Target string key '$targetKey' is empty in $locale", text!!.isEmpty())
            }
        }
    }

    @Test
    fun testBaseAntServiceNamesExistInDefaultLocale() {
        val defaultStrings = LocalizationTestCache.getStrings("values")

        for (targetKey in baseKeys) {
            assertTrue("Base string key '$targetKey' missing in default values/strings.xml", defaultStrings.containsKey(targetKey))
            val text = defaultStrings[targetKey]?.trim()
            assertNotNull("Base string key '$targetKey' must not be null", text)
            assertFalse("Base string key '$targetKey' is empty", text!!.isEmpty())
        }
    }

    @Test
    fun testBleAlternativeNoteOmittedFromAllLocales() {
        // REQ-UI-309 / ATT-2749: ant_status_ble_alternative_note must be absent from all 9 locales
        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale)
            assertFalse(
                "Locale $locale must not contain ant_status_ble_alternative_note (REQ-UI-309)",
                strings.containsKey("ant_status_ble_alternative_note")
            )
        }
    }
}
