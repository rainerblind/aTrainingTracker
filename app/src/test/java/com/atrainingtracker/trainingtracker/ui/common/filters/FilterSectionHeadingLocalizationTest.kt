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

package com.atrainingtracker.trainingtracker.ui.common.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization parity test for the filter section heading 'filter_section_start_at' (REQ-UI-208 / TST-UI-162).
 * Validates presence, non-blank values, and expected translations across all 9 supported application locales:
 * en (default), de, es, fr, it, ja, nl, pl, pt.
 */
class FilterSectionHeadingLocalizationTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

    private val expectedTranslations = mapOf(
        "" to "Start at",
        "-de" to "Startet bei",
        "-es" to "Comienza en",
        "-fr" to "Départ à",
        "-it" to "Partenza da",
        "-ja" to "開始地点",
        "-nl" to "Start bij",
        "-pl" to "Start w",
        "-pt" to "Início em"
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
    fun testFilterSectionStartAtExistsInAllLocalesWithExpectedValues() {
        val resDir = findResDir()

        for (locale in locales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values$locale"
            val stringsFile = File(resDir, "$valuesDir/strings.xml")

            assertTrue("File should exist: ${stringsFile.path}", stringsFile.exists())

            val value = parseStringResource(stringsFile, "filter_section_start_at")
            assertNotNull("Missing key 'filter_section_start_at' in $valuesDir/strings.xml", value)
            assertTrue("Key 'filter_section_start_at' is blank in $valuesDir/strings.xml", value!!.isNotBlank())

            val expected = expectedTranslations[locale]
            assertEquals(
                "Mismatched translation in $valuesDir/strings.xml",
                expected,
                value
            )
        }
    }
}
