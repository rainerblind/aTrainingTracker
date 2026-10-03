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

package com.atrainingtracker.trainingtracker.ui.tracking.typography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Contract tests verifying font expansion assets, preloaded font declarations,
 * 9-language localization parity, and UI dropdown height constraints (REQ-UI-227 / TST-UI-181).
 */
class CockpitFontExpansionTest {

    private val resDir = File("src/main/res")

    private val locales = listOf(
        "de", "es", "fr", "it", "ja", "nl", "pl", "pt"
    )

    private val newFontDescriptors = listOf(
        "bebas_neue.xml",
        "teko.xml",
        "barlow_condensed.xml",
        "oswald.xml",
        "chakra_petch.xml",
        "oxanium.xml",
        "rajdhani.xml",
        "montserrat.xml"
    )

    private val newFontKeys = listOf(
        "tuning_font_bebas_neue",
        "tuning_font_teko",
        "tuning_font_barlow_condensed",
        "tuning_font_oswald",
        "tuning_font_chakra_petch",
        "tuning_font_oxanium",
        "tuning_font_rajdhani",
        "tuning_font_montserrat"
    )

    private fun parseStrings(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (!file.exists()) return map

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        doc.documentElement.normalize()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                val name = element.getAttribute("name")
                val text = element.textContent
                map[name] = text
            }
        }
        return map
    }

    @Test
    fun allNewFontXmlDescriptors_existAndAreValid() {
        val fontDir = File(resDir, "font")
        assertTrue("res/font directory must exist", fontDir.exists() && fontDir.isDirectory)

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        for (fontFileName in newFontDescriptors) {
            val xmlFile = File(fontDir, fontFileName)
            assertTrue("Font descriptor $fontFileName must exist", xmlFile.exists())

            val doc = builder.parse(xmlFile)
            val root = doc.documentElement
            assertEquals("font-family", root.nodeName)

            val authority = root.getAttribute("app:fontProviderAuthority")
            val pkg = root.getAttribute("app:fontProviderPackage")
            val query = root.getAttribute("app:fontProviderQuery")
            val certs = root.getAttribute("app:fontProviderCerts")

            assertEquals("com.google.android.gms.fonts", authority)
            assertEquals("com.google.android.gms", pkg)
            assertTrue("Query must not be blank for $fontFileName", query.isNotBlank())
            assertEquals("@array/com_google_android_gms_fonts_certs", certs)
        }
    }

    @Test
    fun preloadedFonts_declaresAllNewFonts() {
        val preloadedFile = File(resDir, "values/preloaded_fonts.xml")
        assertTrue("preloaded_fonts.xml must exist", preloadedFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(preloadedFile)
        val items = doc.getElementsByTagName("item")

        val registeredFonts = mutableSetOf<String>()
        for (i in 0 until items.length) {
            registeredFonts.add(items.item(i).textContent.trim())
        }

        for (descriptor in newFontDescriptors) {
            val fontName = descriptor.removeSuffix(".xml")
            val expectedEntry = "@font/$fontName"
            assertTrue(
                "preloaded_fonts.xml must contain entry for $expectedEntry",
                registeredFonts.contains(expectedEntry)
            )
        }
    }

    @Test
    fun allLocales_containAllNewFontKeys() {
        val defaultFile = File(resDir, "values/strings.xml")
        assertTrue("Default strings.xml must exist", defaultFile.exists())
        val defaultStrings = parseStrings(defaultFile)

        newFontKeys.forEach { key ->
            assertTrue("Default strings.xml missing key: $key", defaultStrings.containsKey(key))
            assertFalse("Default key '$key' must not be blank", defaultStrings[key].isNullOrBlank())
        }

        locales.forEach { locale ->
            val localeFile = File(resDir, "values-$locale/strings.xml")
            assertTrue("strings.xml for locale '$locale' must exist", localeFile.exists())
            val localeStrings = parseStrings(localeFile)

            newFontKeys.forEach { key ->
                assertTrue(
                    "Locale '$locale' is missing translation for key: $key",
                    localeStrings.containsKey(key)
                )
                val text = localeStrings[key]
                assertNotNull("Translation for '$key' in '$locale' is null", text)
                assertFalse("Translation for '$key' in '$locale' is empty or blank", text.isNullOrBlank())
            }
        }
    }

    @Test
    fun advancedTuningDialog_dropdownMenu_hasHeightConstraint() {
        val dialogFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt")
        assertTrue("AdvancedTuningDialog.kt must exist", dialogFile.exists())
        val content = dialogFile.readText()

        assertTrue(
            "AdvancedTuningDialog.kt ExposedDropdownMenu must have height constraint heightIn(max = 360.dp)",
            content.contains("heightIn(max = 360.dp)")
        )
    }
}
