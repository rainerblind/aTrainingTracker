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

package com.atrainingtracker.trainingtracker.ui.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit and architectural contract tests for [HrvInfoBottomSheet] conforming to REQ-TRK-061.5,
 * Rule 72 (zero nested verticalScroll), and 9-language translation parity in strings_hrv.xml (TST-TRK-061.5).
 */
class HrvInfoBottomSheetTest {

    private fun resolveProjectRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "app/src/main").exists()) cwd else cwd.parentFile ?: cwd
    }

    @Test
    fun testBottomSheetSourceContract_noNestedVerticalScrollOrLazyColumn() {
        val root = resolveProjectRoot()
        val sheetFile = File(root, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/HrvInfoBottomSheet.kt")
        assertTrue("HrvInfoBottomSheet.kt must exist", sheetFile.exists())
        val source = sheetFile.readText()

        assertTrue("Must use AppModalBottomSheet", source.contains("AppModalBottomSheet("))
        assertFalse(
            "Rule 72 violation: Must NOT nest verticalScroll inside AppModalBottomSheet",
            source.contains("verticalScroll(")
        )
        assertFalse(
            "Rule 72 violation: Must NOT nest LazyColumn inside AppModalBottomSheet",
            source.contains("LazyColumn(")
        )
    }

    @Test
    fun testNineLocaleLocalizationParity_allHrvInfoStringsPresentInStringsHrvXml() {
        val root = resolveProjectRoot()
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

        val dbf = DocumentBuilderFactory.newInstance()
        locales.forEach { localeDir ->
            val stringsFile = File(root, "app/src/main/res/$localeDir/strings_hrv.xml")
            assertTrue("Missing $localeDir/strings_hrv.xml", stringsFile.exists())
            val doc = dbf.newDocumentBuilder().parse(stringsFile)
            val nodes = doc.getElementsByTagName("string")
            val entries = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i)
                val name = elem.attributes?.getNamedItem("name")?.nodeValue
                if (name != null) {
                    entries[name] = elem.textContent ?: ""
                }
            }

            requiredKeys.forEach { key ->
                val value = entries[key]
                assertNotNull("Missing string '$key' in $localeDir/strings_hrv.xml", value)
                assertTrue("Blank string '$key' in $localeDir/strings_hrv.xml", value!!.isNotBlank())
            }
        }
    }
}
