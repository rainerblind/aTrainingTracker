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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Automated verification test (TST-STR-018) enforcing translation coverage, format specifier safety,
 * string array depth consistency, and resource hierarchy hygiene across all 9 supported locales
 * pursuant to REQ-LOC-001 and REQ-UI-122.
 */
class TranslationParityTest {

    private val locales = listOf("de", "es", "fr", "it", "ja", "nl", "pl", "pt")
    private val resXmlFiles = listOf("strings.xml", "strings_devices.xml", "strings_filters.xml", "arrays.xml")

    private val formatSpecifierPattern = Pattern.compile("%(\\d+\\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])")

    private lateinit var resDir: File

    data class StringResource(
        val name: String,
        val text: String,
        val isTranslatable: Boolean,
        val sourceFile: String
    )

    data class ArrayResource(
        val name: String,
        val itemCount: Int,
        val isTranslatable: Boolean,
        val sourceFile: String
    )

    data class PluralResource(
        val name: String,
        val quantities: Map<String, String>,
        val sourceFile: String
    )

    @Before
    fun setUp() {
        resDir = File("src/main/res").takeIf { it.exists() }
            ?: File("app/src/main/res").takeIf { it.exists() }
            ?: File("../app/src/main/res").takeIf { it.exists() }
            ?: File(System.getProperty("user.dir"), "app/src/main/res").takeIf { it.exists() }
            ?: error("Unable to locate res directory from ${System.getProperty("user.dir")}")
    }

    private fun parseStrings(dir: File): Map<String, StringResource> {
        val result = mutableMapOf<String, StringResource>()
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        for (fileName in resXmlFiles) {
            val file = File(dir, fileName)
            if (!file.exists()) continue

            val doc = docBuilder.parse(file)
            val stringNodes = doc.getElementsByTagName("string")
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val elem = node as Element
                    val name = elem.getAttribute("name")
                    val translatableAttr = elem.getAttribute("translatable")
                    val isTranslatable = translatableAttr != "false"
                    val text = elem.textContent
                    result[name] = StringResource(name, text, isTranslatable, fileName)
                }
            }
        }
        return result
    }

    private fun parseArrays(dir: File): Map<String, ArrayResource> {
        val result = mutableMapOf<String, ArrayResource>()
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        for (fileName in resXmlFiles) {
            val file = File(dir, fileName)
            if (!file.exists()) continue

            val doc = docBuilder.parse(file)
            val arrayNodes = doc.getElementsByTagName("string-array")
            for (i in 0 until arrayNodes.length) {
                val node = arrayNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val elem = node as Element
                    val name = elem.getAttribute("name")
                    val translatableAttr = elem.getAttribute("translatable")
                    val isTranslatable = translatableAttr != "false"

                    val itemNodes = elem.getElementsByTagName("item")
                    result[name] = ArrayResource(name, itemNodes.length, isTranslatable, fileName)
                }
            }
        }
        return result
    }

    private fun parsePlurals(dir: File): Map<String, PluralResource> {
        val result = mutableMapOf<String, PluralResource>()
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        for (fileName in resXmlFiles) {
            val file = File(dir, fileName)
            if (!file.exists()) continue

            val doc = docBuilder.parse(file)
            val pluralNodes = doc.getElementsByTagName("plurals")
            for (i in 0 until pluralNodes.length) {
                val node = pluralNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val elem = node as Element
                    val name = elem.getAttribute("name")
                    val quantities = mutableMapOf<String, String>()

                    val itemNodes = elem.getElementsByTagName("item")
                    for (j in 0 until itemNodes.length) {
                        val itemNode = itemNodes.item(j) as Element
                        quantities[itemNode.getAttribute("quantity")] = itemNode.textContent
                    }
                    result[name] = PluralResource(name, quantities, fileName)
                }
            }
        }
        return result
    }

    private fun extractSpecifiers(text: String): List<String> {
        val specifiers = mutableListOf<String>()
        val matcher = formatSpecifierPattern.matcher(text)
        while (matcher.find()) {
            val spec = matcher.group()
            if (spec != "%%") {
                specifiers.add(spec)
            }
        }
        return specifiers
    }

    @Test
    fun testStringKeyParity_allTranslatableKeysPresentInAllLocales() {
        val baseStrings = parseStrings(File(resDir, "values"))
        val translatableBaseKeys = baseStrings.filter { it.value.isTranslatable }.keys

        for (locale in locales) {
            val localeStrings = parseStrings(File(resDir, "values-$locale"))
            val missingKeys = translatableBaseKeys.filter { it !in localeStrings }

            assertTrue(
                "Locale '$locale' is missing translatable keys: $missingKeys",
                missingKeys.isEmpty()
            )
        }
    }

    @Test
    fun testFormatSpecifierParity_allLocalesMatchBaseSpecifiers() {
        val baseStrings = parseStrings(File(resDir, "values"))

        for (locale in locales) {
            val localeStrings = parseStrings(File(resDir, "values-$locale"))

            for ((key, baseRes) in baseStrings) {
                if (!baseRes.isTranslatable) continue
                val locRes = localeStrings[key] ?: continue

                val baseSpecs = extractSpecifiers(baseRes.text)
                val locSpecs = extractSpecifiers(locRes.text)

                // 1. Same number of format specifiers
                assertEquals(
                    "Format specifier count mismatch for '$key' in locale '$locale': base=${baseSpecs}, loc=${locSpecs}",
                    baseSpecs.size,
                    locSpecs.size
                )

                if (baseSpecs.size > 1) {
                    // Multi-argument strings must use positional specifiers (%1$s, %2$d, etc.)
                    for (spec in locSpecs) {
                        assertTrue(
                            "Multi-arg format specifier '$spec' in '$key' (locale '$locale') must be positional (e.g. %1\$s)",
                            spec.matches(Regex("%\\d+\\\$.*"))
                        )
                    }

                    // Check that the set of referenced parameter positions matches
                    val basePositions = baseSpecs.mapNotNull { Regex("%(\\d+)\\\$").find(it)?.groupValues?.get(1) }.toSet()
                    val locPositions = locSpecs.mapNotNull { Regex("%(\\d+)\\\$").find(it)?.groupValues?.get(1) }.toSet()
                    assertEquals(
                        "Positional indices mismatch for '$key' in locale '$locale': base=$basePositions, loc=$locPositions",
                        basePositions,
                        locPositions
                    )
                } else if (baseSpecs.size == 1 && baseSpecs[0].matches(Regex("%\\d+\\\$.*"))) {
                    // If single-arg was made positional in base, locale should also be positional
                    assertTrue(
                        "Single-arg format specifier in '$key' (locale '$locale') was positional in base (${baseSpecs[0]}) but not in locale (${locSpecs.firstOrNull()})",
                        locSpecs[0].matches(Regex("%\\d+\\\$.*"))
                    )
                }
            }
        }
    }

    @Test
    fun testStringArrayParity_arrayLengthsMatchBase() {
        val baseArrays = parseArrays(File(resDir, "values"))

        for (locale in locales) {
            val localeArrays = parseArrays(File(resDir, "values-$locale"))

            for ((name, baseArray) in baseArrays) {
                val locArray = localeArrays[name] ?: continue
                assertEquals(
                    "Array length mismatch for array '$name' in locale '$locale'",
                    baseArray.itemCount,
                    locArray.itemCount
                )
            }
        }
    }

    @Test
    fun testPluralsParity_quantitiesAndSpecifiersMatchBase() {
        val basePlurals = parsePlurals(File(resDir, "values"))

        for (locale in locales) {
            val localePlurals = parsePlurals(File(resDir, "values-$locale"))

            for ((name, basePlural) in basePlurals) {
                val locPlural = localePlurals[name]
                assertNotNull("Plural '$name' missing in locale '$locale'", locPlural)

                for ((quantity, locText) in locPlural!!.quantities) {
                    val locSpecs = extractSpecifiers(locText)
                    val baseSampleText = basePlural.quantities["other"] ?: basePlural.quantities.values.first()
                    val baseSpecs = extractSpecifiers(baseSampleText)

                    assertEquals(
                        "Format specifier count mismatch in plural '$name' quantity '$quantity' in locale '$locale'",
                        baseSpecs.size,
                        locSpecs.size
                    )
                }
            }
        }
    }

    @Test
    fun testXmlHygiene_resourceHierarchyAndPlacement() {
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        // 1. Verify no <string> tags inside any arrays.xml
        val allValuesDirs = listOf(File(resDir, "values")) + locales.map { File(resDir, "values-$it") }
        for (dir in allValuesDirs) {
            val arraysFile = File(dir, "arrays.xml")
            if (arraysFile.exists()) {
                val doc = docBuilder.parse(arraysFile)
                val stringNodes = doc.getElementsByTagName("string")
                assertEquals(
                    "Found unexpected <string> tag in ${arraysFile.path}",
                    0,
                    stringNodes.length
                )
            }
        }

        // 2. Verify filter_max is present in strings_filters.xml in all 9 locales
        for (dir in allValuesDirs) {
            val filtersFile = File(dir, "strings_filters.xml")
            assertTrue("File ${filtersFile.path} does not exist", filtersFile.exists())
            val doc = docBuilder.parse(filtersFile)
            val stringNodes = doc.getElementsByTagName("string")
            var hasFilterMax = false
            for (i in 0 until stringNodes.length) {
                val elem = stringNodes.item(i) as Element
                if (elem.getAttribute("name") == "filter_max") {
                    hasFilterMax = true
                    break
                }
            }
            assertTrue("filter_max missing from ${filtersFile.path}", hasFilterMax)
        }

        // 3. Verify devices_sensors_title is in strings_devices.xml in Spanish
        val esDevicesFile = File(resDir, "values-es/strings_devices.xml")
        val esDoc = docBuilder.parse(esDevicesFile)
        val esStringNodes = esDoc.getElementsByTagName("string")
        var hasSensorsTitle = false
        for (i in 0 until esStringNodes.length) {
            val elem = esStringNodes.item(i) as Element
            if (elem.getAttribute("name") == "devices_sensors_title") {
                hasSensorsTitle = true
                break
            }
        }
        assertTrue("devices_sensors_title missing from values-es/strings_devices.xml", hasSensorsTitle)

        // 4. Verify backup_interval_values in values/arrays.xml has translatable="false"
        val baseArraysFile = File(resDir, "values/arrays.xml")
        val baseDoc = docBuilder.parse(baseArraysFile)
        val arrayNodes = baseDoc.getElementsByTagName("string-array")
        var backupIntervalIsNonTranslatable = false
        for (i in 0 until arrayNodes.length) {
            val elem = arrayNodes.item(i) as Element
            if (elem.getAttribute("name") == "backup_interval_values") {
                backupIntervalIsNonTranslatable = elem.getAttribute("translatable") == "false"
                break
            }
        }
        assertTrue("backup_interval_values must have translatable=\"false\"", backupIntervalIsNonTranslatable)
    }
}
