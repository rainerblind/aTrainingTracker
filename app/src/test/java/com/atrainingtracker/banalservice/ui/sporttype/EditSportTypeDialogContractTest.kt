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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test verifying progressive disclosure, primary field prominence,
 * and Advanced Options encapsulation in EditSportTypeDialog.kt
 * (REQ-UI-365.4, TST-UI-328.6, ATT-5821).
 */
class EditSportTypeDialogContractTest {

    private fun resolveSourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        ).firstOrNull { it.exists() } ?: File(relativePath)
    }

    @Test
    fun testEditSportTypeDialog_primaryFieldOrder() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/EditSportTypeDialog.kt")
        assertTrue("EditSportTypeDialog.kt must exist", file.exists())
        val content = file.readText()
        val body = content.substringAfter("fun EditSportTypeDialog(")

        val nameIdx = body.indexOf("R.string.name")
        val baseSportIdx = body.indexOf("R.string.basic_sport_type")
        val equipIdx = body.indexOf("MultiSelectEquipmentSpinner")
        val stravaIdx = body.indexOf("\"Strava Sport Name\"")
        val advancedIdx = body.indexOf("TuningAccordionSection(")

        assertTrue("Name must precede basic sport type", nameIdx in 0..<baseSportIdx)
        assertTrue("Basic sport type must precede equipment", baseSportIdx in 0..<equipIdx)
        assertTrue("Equipment must precede Strava sport mapping", equipIdx in 0..<stravaIdx)
        assertTrue("Strava sport mapping must precede Advanced Options accordion", stravaIdx in 0..<advancedIdx)
    }

    @Test
    fun testEditSportTypeDialog_advancedOptionsEncapsulatesSpeedAndExportFields() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/EditSportTypeDialog.kt")
        assertTrue("EditSportTypeDialog.kt must exist", file.exists())
        val content = file.readText()

        // Asserts default collapsed state
        assertTrue(
            "EditSportTypeDialog must declare isAdvancedExpanded initialized to false via rememberSaveable",
            content.contains("var isAdvancedExpanded by rememberSaveable { mutableStateOf(false) }")
        )

        // Asserts TuningAccordionSection presence and string bindings
        assertTrue(
            "EditSportTypeDialog must bind R.string.sport_type_advanced_options",
            content.contains("R.string.sport_type_advanced_options")
        )
        assertTrue(
            "EditSportTypeDialog must bind R.string.sport_type_advanced_options_desc",
            content.contains("R.string.sport_type_advanced_options_desc")
        )
        assertTrue(
            "EditSportTypeDialog must bind R.string.sport_type_speed_detection_hint",
            content.contains("R.string.sport_type_speed_detection_hint")
        )

        // Asserts that speed detection hint, min/max speed, TCX, and GC are all inside TuningAccordionSection
        val accordionContent = content.substringAfter("TuningAccordionSection(")
            .substringBefore("Spacer(Modifier.height(8.dp))")

        assertTrue(
            "Accordion must contain sport_type_speed_detection_hint",
            accordionContent.contains("R.string.sport_type_speed_detection_hint")
        )
        assertTrue(
            "Accordion must contain min and max speed inputs",
            accordionContent.contains("minSpeed") && accordionContent.contains("maxSpeed")
        )
        assertTrue(
            "Accordion must contain TCX Sport Name dropdown",
            accordionContent.contains("\"TCX Sport Name\"")
        )
        assertTrue(
            "Accordion must contain GoldenCheetah Name dropdown",
            accordionContent.contains("\"GoldenCheetah Name\"")
        )
    }
}
