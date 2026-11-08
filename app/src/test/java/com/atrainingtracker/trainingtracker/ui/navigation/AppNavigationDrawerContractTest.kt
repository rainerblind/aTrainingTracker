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

package com.atrainingtracker.trainingtracker.ui.navigation

import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Contract test suite verifying `REQ-UI-487` (`TST-UI-487`):
 * Navigation Drawer Units Disambiguation, Units Dialog Imperial Parity & Distinct Power Iconography.
 */
class AppNavigationDrawerContractTest {

    private fun resolveResDir(): File {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        return when {
            File(projectDir, "src/main/res").exists() -> File(projectDir, "src/main/res")
            File(projectDir, "app/src/main/res").exists() -> File(projectDir, "app/src/main/res")
            else -> File(projectDir, "../app/src/main/res")
        }
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

    /**
     * TST-UI-487.1: Verify German navigation drawer disambiguation between
     * measurement units (Maßeinheiten) and workout sessions (Einheiten).
     */
    @Test
    fun testGermanNavigationDrawerUnitsDisambiguation() {
        val resDir = resolveResDir()
        val deStrings = File(resDir, "values-de/strings.xml")
        assertTrue("values-de/strings.xml must exist", deStrings.exists())

        val prefsUnits = parseStringResource(deStrings, "prefsUnitsTitle")
        val tabWorkouts = parseStringResource(deStrings, "tab_workouts")

        assertNotNull("prefsUnitsTitle must exist in values-de", prefsUnits)
        assertNotNull("tab_workouts must exist in values-de", tabWorkouts)
        assertEquals("prefsUnitsTitle in German must be 'Maßeinheiten'", "Maßeinheiten", prefsUnits)
        assertEquals("tab_workouts in German must be 'Einheiten'", "Einheiten", tabWorkouts)
        assertNotEquals("prefsUnitsTitle must not collide with tab_workouts in German", prefsUnits, tabWorkouts)

        // Verify English base strings
        val enStrings = File(resDir, "values/strings.xml")
        assertEquals("prefsUnitsTitle in English must be 'Units'", "Units", parseStringResource(enStrings, "prefsUnitsTitle"))
        assertEquals("tab_workouts in English must be 'Workouts'", "Workouts", parseStringResource(enStrings, "tab_workouts"))
    }

    /**
     * TST-UI-487.2: Verify units dialog imperial option localization across locales.
     */
    @Test
    fun testUnitsSettingsDialogImperialLocalizationParity() {
        val resDir = resolveResDir()

        val deStrings = File(resDir, "values-de/strings.xml")
        assertEquals(
            "German imperial unit option must be 'Imperial (Meilen)'",
            "Imperial (Meilen)",
            parseStringResource(deStrings, "units_type_imperial")
        )

        val jaStrings = File(resDir, "values-ja/strings.xml")
        assertEquals(
            "Japanese imperial unit option must be 'ヤードポンド法 (マイル)'",
            "ヤードポンド法 (マイル)",
            parseStringResource(jaStrings, "units_type_imperial")
        )

        val enStrings = File(resDir, "values/strings.xml")
        assertEquals(
            "English imperial unit option must be 'imperial (mile)'",
            "imperial (mile)",
            parseStringResource(enStrings, "units_type_imperial")
        )
    }

    /**
     * TST-UI-487.3: Verify ActivityType BIKE_POWER maps to distinct vector drawable bsport_bike_power.
     */
    @Test
    fun testActivityTypeBikePowerIconDistinctness() {
        assertEquals(
            "BIKE_POWER must use distinct R.drawable.bsport_bike_power",
            R.drawable.bsport_bike_power,
            ActivityType.BIKE_POWER.logoId
        )
        assertEquals(
            "BIKE_SPEED_AND_CADENCE must use R.drawable.bsport_bike",
            R.drawable.bsport_bike,
            ActivityType.BIKE_SPEED_AND_CADENCE.logoId
        )
        assertNotEquals(
            "BIKE_POWER and BIKE_SPEED_AND_CADENCE icons must be distinct",
            ActivityType.BIKE_POWER.logoId,
            ActivityType.BIKE_SPEED_AND_CADENCE.logoId
        )
    }
}
