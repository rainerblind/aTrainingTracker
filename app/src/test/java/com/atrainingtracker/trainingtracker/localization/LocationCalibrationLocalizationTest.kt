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

package com.atrainingtracker.trainingtracker.localization

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization verification test for location calibration strings (REQ-UI-199, TST-UI-153.5).
 * Validates 9-locale parity for location_detected_format and format specifier integrity.
 */
class LocationCalibrationLocalizationTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

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
    fun testLocationDetectedFormatParityAcrossAllLocales() {
        val resDir = findResDir()

        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val stringsFile = File(resDir, "$dirName/strings.xml")
            assertTrue("Expected strings.xml to exist in $dirName", stringsFile.exists())

            val detectedFormat = parseStringResource(stringsFile, "location_detected_format")
            assertNotNull("Missing location_detected_format in $dirName", detectedFormat)
            assertTrue(
                "location_detected_format in $dirName must contain %1\$s or %s (was: $detectedFormat)",
                detectedFormat!!.contains("%1\$s") || detectedFormat.contains("%s")
            )
        }
    }

    @Test
    fun testLocationCalibratedFormatParityAcrossAllLocales() {
        val resDir = findResDir()

        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val stringsFile = File(resDir, "$dirName/strings.xml")
            assertTrue("Expected strings.xml to exist in $dirName", stringsFile.exists())

            val calibratedFormat = parseStringResource(stringsFile, "location_calibrated_format")
            assertNotNull("Missing location_calibrated_format in $dirName", calibratedFormat)
            assertTrue(
                "location_calibrated_format in $dirName must contain %1\$s and %2\$s",
                calibratedFormat!!.contains("%1\$s") && calibratedFormat.contains("%2\$s")
            )
        }
    }
}
