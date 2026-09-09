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

package com.atrainingtracker.trainingtracker.migration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit and resource verification tests for [PreImportTuningBottomSheet] (ATT-793 / REQ-MIG-012 / TST-MIG-009).
 */
class PreImportTuningBottomSheetTest {

    private val requiredStringKeys = listOf(
        "cluster_tuning_title",
        "cluster_tuning_pre_import_desc",
        "cluster_info_title",
        "OK",
        "cancel"
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
    fun testAllRequiredPreImportTuningKeysExistAcrossAll9Locales() {
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
    fun testPreImportDescriptionTranslationsAreDistinctAndLocalized() {
        val resDir = findResDirectory()
        val enStrings = parseStringsFile(File(resDir, "values/strings.xml"))
        val deStrings = parseStringsFile(File(resDir, "values-de/strings.xml"))
        val esStrings = parseStringsFile(File(resDir, "values-es/strings.xml"))
        val frStrings = parseStringsFile(File(resDir, "values-fr/strings.xml"))
        val itStrings = parseStringsFile(File(resDir, "values-it/strings.xml"))
        val jaStrings = parseStringsFile(File(resDir, "values-ja/strings.xml"))
        val nlStrings = parseStringsFile(File(resDir, "values-nl/strings.xml"))
        val plStrings = parseStringsFile(File(resDir, "values-pl/strings.xml"))
        val ptStrings = parseStringsFile(File(resDir, "values-pt/strings.xml"))

        val enDesc = enStrings["cluster_tuning_pre_import_desc"] ?: ""
        val deDesc = deStrings["cluster_tuning_pre_import_desc"] ?: ""
        val esDesc = esStrings["cluster_tuning_pre_import_desc"] ?: ""
        val frDesc = frStrings["cluster_tuning_pre_import_desc"] ?: ""
        val itDesc = itStrings["cluster_tuning_pre_import_desc"] ?: ""
        val jaDesc = jaStrings["cluster_tuning_pre_import_desc"] ?: ""
        val nlDesc = nlStrings["cluster_tuning_pre_import_desc"] ?: ""
        val plDesc = plStrings["cluster_tuning_pre_import_desc"] ?: ""
        val ptDesc = ptStrings["cluster_tuning_pre_import_desc"] ?: ""

        assertTrue("DE description should be localized", deDesc.contains("Sensitivität"))
        assertTrue("ES description should be localized", esDesc.contains("sensibilidad"))
        assertTrue("FR description should be localized", frDesc.contains("sensibilité"))
        assertTrue("IT description should be localized", itDesc.contains("sensibilità"))
        assertTrue("JA description should be localized", jaDesc.contains("クラスタリング"))
        assertTrue("NL description should be localized", nlDesc.contains("gevoeligheid"))
        assertTrue("PL description should be localized", plDesc.contains("czułość"))
        assertTrue("PT description should be localized", ptDesc.contains("sensibilidade"))
        assertTrue("EN description should be localized", enDesc.contains("sensitivity"))
    }
}
