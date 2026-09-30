/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization parity test for continuous telemetry metric graphs and section headings (REQ-UI-206 / TST-UI-160).
 * Validates presence and non-blank values across all 9 supported locales:
 * en (default), de, es, fr, it, ja, nl, pl, pt.
 */
class TelemetryMetricLocalizationTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

    private val requiredKeys = listOf(
        "graph_heading_elevation",
        "graph_heading_heart_rate",
        "graph_heading_speed",
        "graph_heading_pace",
        "graph_heading_power"
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
    fun testAllRequiredTelemetryGraphStringsExistInAllLocales() {
        val resDir = findResDir()

        for (locale in locales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values$locale"
            val stringsFile = File(resDir, "$valuesDir/strings.xml")

            assertTrue("File should exist: ${stringsFile.path}", stringsFile.exists())

            for (key in requiredKeys) {
                val value = parseStringResource(stringsFile, key)
                assertNotNull("Missing key '$key' in $valuesDir/strings.xml", value)
                assertTrue("Key '$key' is blank in $valuesDir/strings.xml", value!!.isNotBlank())
            }
        }
    }
}
