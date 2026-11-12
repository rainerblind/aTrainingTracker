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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract and 9-language translation parity tests for [HrvReadinessTrendCard]
 * pursuant to REQ-PER-033, Rule 48, and Rule 81 (TST-PER-041).
 * Conforms to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4.
 */
class HrvReadinessTrendCardTest {

    @Test
    fun testHrvReadinessTrendCard_composableContractExists() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.HrvReadinessTrendCardKt")
        val methods = clazz.declaredMethods.filter { it.name == "HrvReadinessTrendCard" && Modifier.isPublic(it.modifiers) }
        assertTrue("HrvReadinessTrendCard composable must exist", methods.isNotEmpty())

        val method = methods.first()
        val paramTypeNames = method.parameterTypes.map { it.name }
        assertTrue(
            "Must accept HrvTrendSeries parameter",
            paramTypeNames.any { it.contains("HrvTrendSeries") }
        )
        assertTrue(
            "Must accept HrvPosture parameter",
            paramTypeNames.any { it.contains("HrvPosture") }
        )
        assertTrue(
            "Must accept HrvTrendMetricMode parameter",
            paramTypeNames.any { it.contains("HrvTrendMetricMode") }
        )
    }

    @Test
    fun testNineLocaleLocalizationParity_allHrvReadinessTrendStringsPresent() {
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
            "hrv_readiness_metric_ln_rmssd",
            "hrv_readiness_metric_rmssd",
            "hrv_readiness_no_entries_posture",
            "hrv_readiness_rolling_avg_label",
            "hrv_readiness_swc_baseline_label",
            "hrv_readiness_toggle_training_load",
            "hrv_readiness_trend_subtitle",
            "hrv_readiness_trend_title",
            "morning_hrv_posture_overnight_wearable",
            "morning_hrv_posture_seated",
            "morning_hrv_posture_standing",
            "morning_hrv_posture_supine"
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
