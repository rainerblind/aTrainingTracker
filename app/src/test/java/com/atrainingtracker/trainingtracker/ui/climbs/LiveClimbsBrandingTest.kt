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

package com.atrainingtracker.trainingtracker.ui.climbs

import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
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
 * Trademark Absence and Brand Hygiene Contract Tests for Live Climbs (REQ-UI-461, TST-UI-461).
 * Uses XML DOM parsing pursuant to coding_standards.md §5.4 to verify removal of Garmin trademark
 * 'ClimbPro' from user-facing tuning strings and ensure contract invariants.
 */
class LiveClimbsBrandingTest {

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

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    /**
     * TST-UI-461.1: Verify tuning_show_live_climbs_title across all 9 locales
     * exists, is non-empty, and does not contain the ClimbPro trademark or parenthesized branding.
     * Uses XML DOM parsing per coding_standards.md §5.4.
     */
    @Test
    fun testTuningShowLiveClimbsTitle_noClimbProAcrossAll9Locales() {
        val resDir = File(projectRoot, "app/src/main/res")
        assertTrue("app/src/main/res must exist", resDir.exists())

        val expectedTitles = mapOf(
            "values" to "Live Climbs",
            "values-de" to "Live-Anstiege",
            "values-es" to "Subidas en vivo",
            "values-fr" to "Montées en direct",
            "values-it" to "Salite in tempo reale",
            "values-ja" to "ライブクライム",
            "values-nl" to "Live beklimmingen",
            "values-pl" to "Podjazdy na żywo",
            "values-pt" to "Subidas ao vivo"
        )

        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        for (locale in supportedLocales) {
            val stringsFile = File(resDir, "$locale/strings.xml")
            assertTrue("strings.xml must exist for locale $locale", stringsFile.exists())

            val doc = docBuilder.parse(stringsFile)
            val stringNodes = doc.getElementsByTagName("string")
            var titleText: String? = null

            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val elem = node as Element
                    if (elem.getAttribute("name") == "tuning_show_live_climbs_title") {
                        titleText = elem.textContent?.trim()
                        break
                    }
                }
            }

            assertNotNull("Locale '$locale' must define 'tuning_show_live_climbs_title'", titleText)
            assertTrue("Locale '$locale' title must not be empty", titleText!!.isNotEmpty())
            assertFalse(
                "Locale '$locale' title '$titleText' must NOT contain ClimbPro trademark (REQ-UI-461.1)",
                titleText.contains("ClimbPro", ignoreCase = true)
            )
            assertFalse(
                "Locale '$locale' title '$titleText' must NOT contain parentheses",
                titleText.contains("(") || titleText.contains(")")
            )

            val expected = expectedTitles[locale]
            if (expected != null) {
                assertEquals("Locale '$locale' title must match canonical string", expected, titleText)
            }
        }
    }

    /**
     * TST-UI-461.3: DataStore & Database Contract Invariants.
     * Asserts that preference keys and database column names remain unchanged.
     */
    @Test
    fun testContractInvariants_preferenceKeyAndDbColumnUnchanged() {
        assertEquals(
            "KEY_SHOW_LIVE_CLIMBS DataStore preference key must remain byte-identical (REQ-UI-461.3)",
            "tuning_show_live_climbs",
            TuningPreferencesDataStore.KEY_SHOW_LIVE_CLIMBS.name
        )
        assertEquals(
            "SHOW_LIVE_CLIMBS database column name must remain byte-identical (REQ-UI-461.3)",
            "ShowLiveClimbs",
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LIVE_CLIMBS
        )
    }
}
