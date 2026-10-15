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

package com.atrainingtracker.trainingtracker.ui.segments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Localization parity and correctness test for live segment details across all 9 supported locales
 * (REQ-UI-379, TST-UI-342, Rule 29).
 */
class SegmentLiveDetailsLocalizationTest {

    private val locales = listOf(
        "",      // default (EN)
        "-de",   // German
        "-es",   // Spanish
        "-fr",   // French
        "-it",   // Italian
        "-ja",   // Japanese
        "-nl",   // Dutch
        "-pl",   // Polish
        "-pt"    // Portuguese
    )

    private fun getProjectRootDir(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        return dir
    }

    private fun getStringsFile(localeSuffix: String): File {
        val rootDir = getProjectRootDir()
        return File(rootDir, "app/src/main/res/values$localeSuffix/strings.xml")
    }

    private fun extractStringResource(content: String, name: String): String? {
        val regex = Pattern.compile("<string\\s+name=\"$name\"[^>]*>(.*?)</string>", Pattern.DOTALL)
        val matcher = regex.matcher(content)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    @Test
    fun testPastFinishString_existsAndHasSpecifierInAllNineLocales() {
        for (suffix in locales) {
            val file = getStringsFile(suffix)
            assertTrue("Strings file must exist for locale values$suffix", file.exists())
            val content = file.readText()
            val value = extractStringResource(content, "segment_status_past_finish")
            assertTrue(
                "segment_status_past_finish must exist and not be empty in values$suffix",
                !value.isNullOrBlank()
            )
            assertTrue(
                "segment_status_past_finish in values$suffix must contain %1\$s placeholder, got: '$value'",
                value!!.contains("%1\$s")
            )
        }
    }

    @Test
    fun testPrDeltaString_existsAndHasSpecifierInAllNineLocales() {
        for (suffix in locales) {
            val file = getStringsFile(suffix)
            assertTrue("Strings file must exist for locale values$suffix", file.exists())
            val content = file.readText()
            val value = extractStringResource(content, "segment_status_pr_delta")
            assertTrue(
                "segment_status_pr_delta must exist and not be empty in values$suffix",
                !value.isNullOrBlank()
            )
            assertTrue(
                "segment_status_pr_delta in values$suffix must contain %1\$s placeholder, got: '$value'",
                value!!.contains("%1\$s")
            )
        }
    }

    @Test
    fun testSegmentOffset_japaneseMistranslationFixed() {
        val jaFile = getStringsFile("-ja")
        assertTrue("values-ja/strings.xml must exist", jaFile.exists())
        val content = jaFile.readText()
        val value = extractStringResource(content, "segment_status_offset")
        assertTrue("segment_status_offset must exist in values-ja", !value.isNullOrBlank())
        assertFalse(
            "values-ja segment_status_offset must NOT use misleading 'タイム差' (time difference)",
            value!!.contains("タイム差")
        )
        assertTrue(
            "values-ja segment_status_offset must use accurate lateral course offset term 'コースずれ', got: '$value'",
            value.contains("コースずれ")
        )
    }

    @Test
    fun testSegmentOffset_refinedAcrossAllLocales() {
        for (suffix in locales) {
            val file = getStringsFile(suffix)
            val content = file.readText()
            val value = extractStringResource(content, "segment_status_offset")
            assertTrue("segment_status_offset must exist in values$suffix", !value.isNullOrBlank())
            assertTrue(
                "segment_status_offset in values$suffix must contain %1\$s placeholder",
                value!!.contains("%1\$s")
            )
        }
    }
}
