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
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit and resource verification tests for [ClusterInfoDialog] (ATT-501 / REQ-UI-137 / TST-UI-090).
 */
class ClusterInfoDialogTest {

    private val requiredStringKeys = listOf(
        "cluster_info_title",
        "cluster_info_what_title",
        "cluster_info_what_desc",
        "cluster_info_fingerprint_title",
        "cluster_info_fingerprint_desc",
        "cluster_info_sports_title",
        "cluster_info_sports_desc",
        "cluster_info_tuning_title",
        "cluster_info_tuning_desc",
        "cluster_info_close"
    )

    private val supportedLocales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidate paths: $candidates")
    }

    private fun parseStringsFile(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (!file.exists()) return map

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")

        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as Element
            val name = element.getAttribute("name")
            val text = element.textContent
            map[name] = text
        }
        return map
    }

    @Test
    fun testAllRequiredClusterInfoKeysExistAcrossAll9Locales() {
        val resDir = findResDirectory()

        for (locale in supportedLocales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$valuesDir/strings.xml")
            assertTrue("File $stringsFile must exist", stringsFile.exists())

            val strings = parseStringsFile(stringsFile)

            for (key in requiredStringKeys) {
                assertTrue(
                    "Key '$key' must exist in $valuesDir/strings.xml",
                    strings.containsKey(key)
                )
                val content = strings[key]
                assertNotNull("Content for '$key' in $valuesDir must not be null", content)
                assertFalse("Content for '$key' in $valuesDir must not be blank", content!!.isBlank())
            }
        }
    }

    @Test
    fun testContentCompletenessForFingerprintSection() {
        val resDir = findResDirectory()
        val defaultStrings = parseStringsFile(File(resDir, "values/strings.xml"))

        val fingerprintDesc = defaultStrings["cluster_info_fingerprint_desc"]
        assertNotNull(fingerprintDesc)
        // Verify key 3D metrics are covered in the description
        assertTrue("Must mention Start & End", fingerprintDesc!!.contains("Start"))
        assertTrue("Must mention Apex", fingerprintDesc.contains("Apex"))
        assertTrue("Must mention Distance", fingerprintDesc.contains("Distance"))
        assertTrue("Must mention Altitude", fingerprintDesc.contains("Altitude"))
        assertTrue("Must mention simple but surprisingly good algorithm", fingerprintDesc.contains("simple but surprisingly good algorithm"))
        assertTrue("Must mention separating workouts when really different", fingerprintDesc.contains("separates the workouts when they are really different"))
    }

    @Test
    fun testContentCompletenessForWhatSection() {
        val resDir = findResDirectory()
        val defaultStrings = parseStringsFile(File(resDir, "values/strings.xml"))

        val whatDesc = defaultStrings["cluster_info_what_desc"]
        assertNotNull(whatDesc)
        assertTrue("Must mention naming", whatDesc!!.contains("naming"))
        assertTrue("Must mention comparison or performance", whatDesc.contains("performance"))
        assertTrue("Must use well-known course example like Lake Tahoe Loop", whatDesc.contains("Lake Tahoe Loop"))
        assertFalse("Must not use time-based naming like Morning Run", whatDesc.contains("Morning Run"))
    }

    @Test
    fun testCountrySpecificLakesAcrossLocales() {
        val resDir = findResDirectory()
        val expectedLakes = mapOf(
            "" to "Lake Tahoe Loop",
            "de" to "Chiemsee-Runde",
            "es" to "Albufera",
            "fr" to "Annecy",
            "it" to "Lago di Garda",
            "ja" to "琵琶湖一周",
            "nl" to "IJsselmeer",
            "pl" to "Jeziora Czorsztyńskiego",
            "pt" to "Lagoa de Óbidos"
        )

        for ((locale, expectedLake) in expectedLakes) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            val strings = parseStringsFile(File(resDir, "$valuesDir/strings.xml"))
            val desc = strings["cluster_info_what_desc"]
            assertNotNull("cluster_info_what_desc must exist in $valuesDir", desc)
            assertTrue(
                "Locale '$locale' in $valuesDir must mention iconic lake '$expectedLake', but was: $desc",
                desc!!.contains(expectedLake)
            )
        }
    }

    @Test
    fun testContentCompletenessForTuningSection() {
        val resDir = findResDirectory()
        val defaultStrings = parseStringsFile(File(resDir, "values/strings.xml"))

        val tuningDesc = defaultStrings["cluster_info_tuning_desc"]
        assertNotNull(tuningDesc)
        assertTrue("Must mention Slider or Strict", tuningDesc!!.contains("Strict"))
        assertTrue("Must mention Relaxed", tuningDesc.contains("Relaxed"))
    }
}
