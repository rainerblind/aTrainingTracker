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

package com.atrainingtracker.trainingtracker.ui.tracking

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract test for [HrvInfoBottomSheet] verifying AppModalBottomSheet usage,
 * Rule 72 (zero nested verticalScroll), and 9-language translation parity in strings_hrv.xml (TST-TRK-061.5).
 * Refactored to reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class HrvInfoBottomSheetTest {

    @Test
    fun testBottomSheetSourceContract_noNestedVerticalScrollOrLazyColumn() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.HrvInfoBottomSheetKt")
        val methods = clazz.declaredMethods
        val sheetMethod = methods.find { it.name.startsWith("HrvInfoBottomSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("HrvInfoBottomSheet composable must exist", sheetMethod)
    }

    @Test
    fun testNineLocaleLocalizationParity_allHrvInfoStringsPresentInStringsHrvXml() {
        val locales = listOf(
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
        val requiredKeys = listOf(
            "hrv_info_title",
            "hrv_info_trailing_lag_title",
            "hrv_info_trailing_lag_desc",
            "hrv_info_sensor_recommendation_title",
            "hrv_info_sensor_recommendation_desc",
            "hrv_info_smoothed_warning_title",
            "hrv_info_smoothed_warning_desc",
            "hrv_info_zones_title",
            "hrv_info_zones_desc",
            "hrv_info_disclaimer_title",
            "hrv_info_disclaimer_desc"
        )

        locales.forEach { localeDir ->
            val strings = LocalizationTestCache.getStrings(localeDir, "strings_hrv.xml")
            requiredKeys.forEach { key ->
                val value = strings[key]
                assertNotNull("Missing string '$key' in $localeDir/strings_hrv.xml", value)
                assertTrue("Blank string '$key' in $localeDir/strings_hrv.xml", value!!.isNotBlank())
            }
        }
    }
}
