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

package com.atrainingtracker.trainingtracker.ui.aftermath

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization parity test for Aftermath profile tuning string resources (REQ-UI-201, TST-UI-155.5).
 * Validates presence and non-blank values across all 9 supported locales:
 * en (default), de, es, fr, it, ja, nl, pl, pt.
 */
class AftermathTuningLocalizationTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

    private val requiredKeys = listOf(
        "tuning_cat_aftermath",
        "tuning_profile_x_axis_title",
        "tuning_profile_x_axis_desc",
        "tuning_profile_x_axis_distance",
        "tuning_profile_x_axis_time",
        "tuning_telemetry_x_axis_title",
        "tuning_telemetry_x_axis_desc"
    )

    private fun findResDir(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidates: $candidates")
    }

    private fun parseStringResource(file: File, stringName: String): String? {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val item = stringNodes.item(i)
            if (item is Element && item.getAttribute("name") == stringName) {
                return item.textContent
            }
        }
        return null
    }

    @Test
    fun testAftermathTuningStringsParityAcrossAllLocales() {
        val resDir = findResDir()

        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val stringsFile = File(resDir, "$dirName/strings.xml")
            assertTrue("Expected strings.xml to exist in $dirName", stringsFile.exists())

            for (key in requiredKeys) {
                val value = parseStringResource(stringsFile, key)
                assertNotNull("Missing key '$key' in $dirName", value)
                assertTrue(
                    "Key '$key' in $dirName must not be blank",
                    value!!.isNotBlank()
                )
            }
        }
    }
}
