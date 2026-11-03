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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TST-PER-035.7: 9-Language Localization Parity Audit for Daily Activity Strip (REQ-PER-027.8, ATT-5195).
 */
class DailyActivityStripLocalizationTest {

    private val supportedLocales = listOf(
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
        "workout_periods__daily_activity_strip",
        "workout_periods__no_activity"
    )

    @Test
    fun testDailyActivityStripStringsExistAcrossAll9Locales() {
        var baseDir = File(System.getProperty("user.dir") ?: ".")
        while (!File(baseDir, "app").exists() && baseDir.parentFile != null) {
            baseDir = baseDir.parentFile!!
        }
        val resDir = File(baseDir, "app/src/main/res")

        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        for (locale in supportedLocales) {
            val stringsFile = File(resDir, "$locale/strings.xml")
            assertTrue("strings.xml must exist for locale $locale", stringsFile.exists())

            val doc = docBuilder.parse(stringsFile)
            val stringNodes = doc.getElementsByTagName("string")
            val stringKeys = mutableSetOf<String>()

            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                val nameAttr = node.attributes.getNamedItem("name")?.nodeValue
                if (nameAttr != null) {
                    stringKeys.add(nameAttr)
                }
            }

            for (key in requiredKeys) {
                assertTrue("Locale '$locale' must contain string key '$key'", stringKeys.contains(key))
            }
        }
    }
}
