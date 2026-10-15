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

package com.atrainingtracker.trainingtracker.ui.aftermath.crop

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization parity test for Crop Workout string resources (REQ-UI-368, TST-UI-331).
 * Validates presence and non-blank values across all 9 canonical locales:
 * en (default), de, es, fr, it, ja, nl, pl, pt.
 */
class CropWorkoutLocalizationTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

    private val requiredKeys = listOf(
        "crop_workout_title",
        "crop_workout_action",
        "crop_workout_confirm_title",
        "crop_workout_confirm_message",
        "crop_workout_confirm_action",
        "crop_workout_progress",
        "crop_workout_success",
        "crop_insufficient_storage",
        "crop_export_failed_retry",
        "crop_force_reexport"
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
            val node = stringNodes.item(i)
            if (node is Element && node.getAttribute("name") == stringName) {
                return node.textContent
            }
        }
        return null
    }

    @Test
    fun testAllCropStringsExistInAll9Locales() {
        val resDir = findResDir()
        val missingKeys = mutableListOf<String>()

        for (locale in locales) {
            val valuesDir = File(resDir, "values$locale")
            val stringsFile = File(valuesDir, "strings.xml")

            assertTrue("strings.xml does not exist for locale values$locale", stringsFile.exists())

            for (key in requiredKeys) {
                val value = parseStringResource(stringsFile, key)
                if (value.isNullOrBlank()) {
                    missingKeys.add("values$locale/strings.xml missing key: $key")
                }
            }
        }

        assertTrue(
            "Missing or blank localization keys for crop workout:\n" + missingKeys.joinToString("\n"),
            missingKeys.isEmpty()
        )
    }
}
