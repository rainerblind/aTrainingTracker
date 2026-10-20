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
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 9-language localization parity and format specifier safety test suite
 * for first-install permission onboarding flow (REQ-PRI-007 / TST-PRI-006 / ATT-4455).
 */
class PermissionOnboardingLocalizationTest {

    private val locales = listOf("values", "values-de", "values-es", "values-fr", "values-it", "values-ja", "values-nl", "values-pl", "values-pt")

    private val expectedKeys = listOf(
        "permission_onboarding_title",
        "permission_onboarding_step_format",
        "permission_onboarding_skip_setup",
        "permission_onboarding_skip_step",
        "permission_onboarding_grant",
        "permission_onboarding_continue",
        "permission_onboarding_finish",
        "permission_onboarding_location_title",
        "permission_onboarding_location_desc",
        "permission_onboarding_bluetooth_title",
        "permission_onboarding_bluetooth_desc",
        "permission_onboarding_notification_title",
        "permission_onboarding_notification_desc",
        "permission_onboarding_battery_title",
        "permission_onboarding_battery_desc"
    )

    private lateinit var resDir: File

    @Before
    fun setUp() {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        resDir = File(dir, "app/src/main/res")
        assertTrue("res directory must exist", resDir.exists())
    }

    private fun parseStringsXml(localeFolder: String): Map<String, String> {
        val file = File(resDir, "$localeFolder/strings.xml")
        assertTrue("File must exist: ${file.absolutePath}", file.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        doc.documentElement.normalize()

        val map = mutableMapOf<String, String>()
        val nodeList = doc.getElementsByTagName("string")
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
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
    fun allExpectedKeys_presentInAll9Locales() {
        for (locale in locales) {
            val map = parseStringsXml(locale)
            for (key in expectedKeys) {
                assertTrue("Missing key '$key' in locale folder '$locale'", map.containsKey(key))
                val value = map[key]
                assertNotNull("Value for '$key' in '$locale' must not be null", value)
                assertTrue("Value for '$key' in '$locale' must not be blank", value!!.isNotBlank())
            }
        }
    }

    @Test
    fun stepFormat_containsIndexedPositionalSpecifiersInAll9Locales() {
        for (locale in locales) {
            val map = parseStringsXml(locale)
            val formatStr = map["permission_onboarding_step_format"]
            assertNotNull(formatStr)
            assertTrue(
                "permission_onboarding_step_format in '$locale' must contain '%1\$d'",
                formatStr!!.contains("%1\$d")
            )
            assertTrue(
                "permission_onboarding_step_format in '$locale' must contain '%2\$d'",
                formatStr.contains("%2\$d")
            )
        }
    }
}
