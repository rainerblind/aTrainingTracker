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

package com.atrainingtracker.trainingtracker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization audit test validating 9-language translation parity and XML entity escaping
 * for navigation drawer strings, specifically [equipment_management_title] (REQ-UI-422.3, TST-UI-385.3).
 */
class NavigationDrawerLocalizationTest {

    private val expectedTranslations = mapOf(
        "" to "Bikes & Shoes",
        "-de" to "Räder & Schuhe",
        "-es" to "Bicicletas y zapatillas",
        "-fr" to "Vélos et chaussures",
        "-it" to "Bici e scarpe",
        "-ja" to "バイク＆シューズ",
        "-nl" to "Fietsen & schoenen",
        "-pl" to "Rowery i buty",
        "-pt" to "Bicicletas e calçado"
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
    fun testEquipmentManagementTitle_allLocalesParityAndEscaping() {
        val resDir = findResDir()

        expectedTranslations.forEach { (localeSuffix, expectedValue) ->
            val stringsFile = File(resDir, "values$localeSuffix/strings.xml")
            assertTrue("strings.xml must exist for locale values$localeSuffix", stringsFile.exists())

            // 1. Raw XML escaping check: ensure literal ampersands are escaped as &amp;
            val rawContent = stringsFile.readText()
            val line = rawContent.lines().firstOrNull { it.contains("name=\"equipment_management_title\"") }
            assertNotNull("equipment_management_title must exist in values$localeSuffix/strings.xml", line)

            // If an ampersand is present in the line, it must be escaped as &amp;
            if (line!!.contains("&")) {
                assertTrue(
                    "Ampersand must be escaped as &amp; in values$localeSuffix/strings.xml: $line",
                    line.contains("&amp;")
                )
                assertFalse(
                    "Raw unescaped ampersand forbidden in values$localeSuffix/strings.xml: $line",
                    line.replace("&amp;", "").contains("&")
                )
            }

            // 2. Parsed XML DOM value check
            val parsedValue = parseStringResource(stringsFile, "equipment_management_title")
            assertNotNull("Parsed value must not be null for locale values$localeSuffix", parsedValue)
            assertEquals(
                "Localized text mismatch for values$localeSuffix/strings.xml",
                expectedValue,
                parsedValue
            )
        }
    }
}
