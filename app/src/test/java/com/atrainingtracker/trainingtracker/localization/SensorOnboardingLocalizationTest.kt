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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization verification test for Sensor Onboarding and Empty State strings (REQ-SEN-020, TST-SEN-020.5, TST-SEN-020.12).
 * Validates 9-locale parity for onboarding_pair_sensor_action, devices_empty_state_search_hint, and devices_action_search_sensors,
 * and asserts that action buttons do not embed literal '+' symbol prefixes (Rule 49 & Design Guidelines Sec 5.5).
 */
class SensorOnboardingLocalizationTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

    private val requiredKeys = listOf(
        "onboarding_pair_sensor_action",
        "devices_empty_state_search_hint",
        "devices_action_search_sensors"
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
    fun testAllRequiredKeysExistAndAreNonEmptyAcrossAllLocales() {
        val resDir = findResDir()

        for (locale in locales) {
            val valuesDir = File(resDir, "values$locale")
            assertTrue("Directory values$locale does not exist", valuesDir.exists())
            val stringsFile = File(valuesDir, "strings.xml")
            assertTrue("File strings.xml does not exist in values$locale", stringsFile.exists())

            for (key in requiredKeys) {
                val value = parseStringResource(stringsFile, key)
                assertNotNull("Missing key '$key' in values$locale/strings.xml", value)
                assertTrue("Key '$key' in values$locale/strings.xml is blank", value!!.isNotBlank())
            }
        }
    }

    /**
     * TST-SEN-020.12: Action buttons with Icons.Default.Add vector icons must not embed
     * literal '+' prefixes in strings.xml (Rule 49 & Design Guidelines Sec 5.5).
     */
    @Test
    fun testActionStringsDoNotContainPlusPrefixAcrossAllLocales() {
        val resDir = findResDir()
        val actionKeys = listOf(
            "onboarding_pair_sensor_action",
            "devices_action_search_sensors"
        )

        for (locale in locales) {
            val valuesDir = File(resDir, "values$locale")
            val stringsFile = File(valuesDir, "strings.xml")

            for (key in actionKeys) {
                val value = parseStringResource(stringsFile, key)
                assertNotNull("Missing key '$key' in values$locale/strings.xml", value)
                assertFalse(
                    "Key '$key' in values$locale/strings.xml should not start with '+' (Rule 49 / Sec 5.5 violation): '$value'",
                    value!!.trim().startsWith("+")
                )
            }
        }
    }
}

