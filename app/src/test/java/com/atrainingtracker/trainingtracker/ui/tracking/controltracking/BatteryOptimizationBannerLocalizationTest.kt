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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Localization parity tests verifying all 9 locales contain required battery optimization
 * warning banner strings (REQ-PRI-005, TST-PRI-004, ATT-2621).
 */
class BatteryOptimizationBannerLocalizationTest {

    private val locales = LocalizationTestCache.LOCALES

    private val requiredKeys = listOf(
        "battery_optimization_warning_banner_title",
        "battery_optimization_warning_banner_desc",
        "battery_optimization_action_fix",
        "battery_optimization_dismiss_banner"
    )

    @Test
    fun testBatteryOptimizationWarningBannerStrings_haveCompleteParityAcrossAll9Locales() {
        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale)

            for (key in requiredKeys) {
                assertTrue("String key '$key' must exist in locale $locale", strings.containsKey(key))
                val value = strings[key]
                assertNotNull("String value for '$key' in $locale must not be null", value)
                assertTrue("String value for '$key' in $locale must not be blank", value!!.isNotBlank())
            }
        }
    }
}
