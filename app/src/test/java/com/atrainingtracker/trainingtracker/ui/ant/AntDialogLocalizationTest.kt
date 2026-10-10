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

package com.atrainingtracker.trainingtracker.ui.ant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 9-language localization audit and typo regression test for ANT alert dialogs (REQ-UI-323 / TST-UI-283.4).
 */
class AntDialogLocalizationTest {

    private val locales = listOf(
        "values",
        "values-de",
        "values-es",
        "values-fr",
        "values-it",
        "values-ja",
        "values-nl",
        "values-pl",
        "values-pt"
    )

    private val requiredKeys = listOf(
        "ant_missing_adapter_title",
        "ant_missing_adapter_message",
        "ant_missing_dependency_title",
        "ant_missing_dependency_message",
        "ant_dialog_view_status",
        "ant_dialog_dismiss"
    )

    private fun resolveSourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromRepoRoot = File(System.getProperty("user.dir", "."), relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot
        val parent = File("..", relativePath)
        if (parent.exists()) return parent
        throw IllegalStateException("Cannot resolve source file: $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun testAntDialogStringsParityAcrossAll9Locales() {
        val factory = DocumentBuilderFactory.newInstance()

        for (locale in locales) {
            val file = resolveSourceFile("app/src/main/res/$locale/strings.xml")
            assertTrue("String resource file must exist for $locale", file.exists())

            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(file)
            val stringNodes = doc.getElementsByTagName("string")

            val foundMap = mutableMapOf<String, String>()
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                val nameAttr = node.attributes.getNamedItem("name")?.nodeValue
                if (nameAttr != null) {
                    foundMap[nameAttr] = node.textContent
                }
            }

            for (key in requiredKeys) {
                assertTrue(
                    "Missing string resource '$key' in locale '$locale'",
                    foundMap.containsKey(key)
                )
                val value = foundMap[key]
                assertTrue(
                    "String resource '$key' in locale '$locale' must not be blank",
                    !value.isNullOrBlank()
                )
            }
        }
    }

    @Test
    fun testGermanTypoCorrection_installiert() {
        val file = resolveSourceFile("app/src/main/res/values-de/strings.xml")
        val content = file.readText()

        assertTrue(
            "values-de/strings.xml must contain the correctly spelled 'installiert'",
            content.contains("installiert")
        )
        assertFalse(
            "values-de/strings.xml must NOT contain the typo 'intalliert'",
            content.contains("intalliert")
        )
    }
}
