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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Localization parity test for Telemetry Frequency Histogram toggle resources (REQ-UI-231, TST-UI-185.3).
 * Validates presence and non-blank values across all 9 supported locales:
 * en (default), de, es, fr, it, ja, nl, pl, pt.
 * Refactored to LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class TelemetryHistogramLocalizationTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

    private val requiredKeys = listOf(
        "zone_mode_5_zones",
        "zone_mode_histogram",
        "power_histogram_overflow_label",
        "power_histogram_return_label",
        "power_histogram_overflow_desc",
        "power_histogram_return_desc"
    )

    @Test
    fun testTelemetryHistogramStringsParityAcrossAllLocales() {
        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val strings = LocalizationTestCache.getStrings(dirName, "strings.xml")

            for (key in requiredKeys) {
                val value = strings[key]
                assertNotNull("Missing key '$key' in $dirName/strings.xml", value)
                assertTrue("Value for '$key' in $dirName/strings.xml should not be blank", value!!.isNotBlank())
            }
        }
    }

    @Test
    fun testPowerHistogramStringsFormatSpecifierParity_tst_ui_382_6() {
        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val strings = LocalizationTestCache.getStrings(dirName, "strings.xml")

            val overflowLabel = strings["power_histogram_overflow_label"]
            assertNotNull("overflow_label missing in $dirName", overflowLabel)
            assertTrue("$dirName overflow_label must contain %1\$d", overflowLabel!!.contains("%1\$d"))
            assertTrue("$dirName overflow_label must contain %2\$s", overflowLabel.contains("%2\$s"))
            assertTrue("$dirName overflow_label must start with >", overflowLabel.startsWith(">") || overflowLabel.startsWith("&gt;"))

            val returnLabel = strings["power_histogram_return_label"]
            assertNotNull("return_label missing in $dirName", returnLabel)
            assertTrue("$dirName return_label must contain %1\$d", returnLabel!!.contains("%1\$d"))
            assertTrue("$dirName return_label must contain %2\$d", returnLabel.contains("%2\$d"))
            assertTrue("$dirName return_label must contain %3\$s", returnLabel.contains("%3\$s"))

            val overflowDesc = strings["power_histogram_overflow_desc"]
            assertNotNull("overflow_desc missing in $dirName", overflowDesc)
            assertTrue("$dirName overflow_desc must contain %1\$d", overflowDesc!!.contains("%1\$d"))
            assertTrue("$dirName overflow_desc must contain %2\$s", overflowDesc.contains("%2\$s"))

            val returnDesc = strings["power_histogram_return_desc"]
            assertNotNull("return_desc missing in $dirName", returnDesc)
            assertTrue("$dirName return_desc must contain %1\$d", returnDesc!!.contains("%1\$d"))
            assertTrue("$dirName return_desc must contain %2\$s", returnDesc.contains("%2\$s"))

            // Rule 49: Do not include leading ASCII arrow symbols in description strings
            assertFalse("$dirName overflow_desc must not start with arrow", overflowDesc.startsWith("→") || overflowDesc.startsWith("->"))
            assertFalse("$dirName return_desc must not start with arrow", returnDesc.startsWith("←") || returnDesc.startsWith("<-"))
        }
    }
}
