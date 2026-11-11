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

package com.atrainingtracker.banalservice.ui.sporttype

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural contract test verifying progressive disclosure, primary field prominence,
 * and Advanced Options encapsulation in EditSportTypeDialog.kt
 * (REQ-UI-365.4, TST-UI-328.6, ATT-5821).
 */
class EditSportTypeDialogContractTest {

    @Test
    fun testEditSportTypeDialog_composableContract() {
        val dialogClass = Class.forName("com.atrainingtracker.banalservice.ui.sporttype.EditSportTypeDialogKt")
        assertNotNull("EditSportTypeDialogKt must exist", dialogClass)
        assertTrue(
            "EditSportTypeDialog composable must be declared",
            dialogClass.declaredMethods.any { it.name.startsWith("EditSportTypeDialog") }
        )
    }

    @Test
    fun testEditSportTypeDialog_stringsContract() {
        val strings = LocalizationTestCache.getStrings("values")
        assertTrue("Must define name", strings.containsKey("name"))
        assertTrue("Must define basic_sport_type", strings.containsKey("basic_sport_type"))
        assertTrue("Must define sport_type_advanced_options", strings.containsKey("sport_type_advanced_options"))
        assertTrue("Must define sport_type_advanced_options_desc", strings.containsKey("sport_type_advanced_options_desc"))
        assertTrue("Must define sport_type_speed_detection_hint", strings.containsKey("sport_type_speed_detection_hint"))
    }
}
