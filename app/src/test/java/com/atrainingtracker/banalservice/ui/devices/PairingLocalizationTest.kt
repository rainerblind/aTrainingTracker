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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying 9-language localization parity and non-empty content
 * for sensor pairing strings (REQ-UI-278, TST-UI-238, ATT-2189).
 */
class PairingLocalizationTest {

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
        "devices_pair_sensor",
        "devices_pair_protocol_title",
        "devices_pair_protocol_ant",
        "devices_pair_protocol_ble"
    )

    @Test
    fun testPairingStringsExistInAllNineLocales() {
        for (targetKey in targetKeys) {
            for (locale in locales) {
                val strings = LocalizationTestCache.getStrings(locale)
                assertTrue("Target string key '$targetKey' missing in $locale", strings.containsKey(targetKey))

                val text = strings[targetKey]?.trim()
                assertNotNull("Target string key '$targetKey' must not be null in $locale", text)
                assertFalse("Target string key '$targetKey' is empty in $locale", text!!.isEmpty())
            }
        }
    }
}
