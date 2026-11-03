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

package com.atrainingtracker.trainingtracker.ui.settings.autopause

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test verifying retirement of duplicate auto-pause controls
 * from Advanced Tuning in accordance with Design Guidelines 3.2 (REQ-SET-080.4, TST-SET-080.3, ATT-4836).
 */
class AutoPauseRetirementContractTest {

    private fun resolveSourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        ).firstOrNull { it.exists() } ?: File(relativePath)
    }

    @Test
    fun testSensorsGpsFilterSection_doesNotDeclareAutoPauseControls() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorsGpsFilterSection.kt")
        assertTrue("SensorsGpsFilterSection.kt must exist", file.exists())
        val content = file.readText()

        assertFalse(
            "SensorsGpsFilterSection must not declare autoPauseBikeEnabled parameter",
            content.contains("autoPauseBikeEnabled")
        )
        assertFalse(
            "SensorsGpsFilterSection must not declare autoPauseRunEnabled parameter",
            content.contains("autoPauseRunEnabled")
        )
        assertFalse(
            "SensorsGpsFilterSection must not declare autoPauseOtherEnabled parameter",
            content.contains("autoPauseOtherEnabled")
        )
        assertFalse(
            "SensorsGpsFilterSection must not bind tuning_auto_pause_title",
            content.contains("R.string.tuning_auto_pause_title")
        )
    }

    @Test
    fun testAdvancedTuningScreenAndDialog_doNotPassAutoPauseToSensorsGpsFilterSection() {
        val screenFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt")
        val dialogFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt")

        assertTrue("AdvancedTuningScreen.kt must exist", screenFile.exists())
        assertTrue("AdvancedTuningDialog.kt must exist", dialogFile.exists())

        val screenContent = screenFile.readText()
        val dialogContent = dialogFile.readText()

        assertFalse(
            "AdvancedTuningScreen must not pass autoPauseBikeEnabled to SensorsGpsFilterSection",
            screenContent.contains("autoPauseBikeEnabled = autoPauseBikeEnabled")
        )
        assertFalse(
            "AdvancedTuningDialog must not pass autoPauseBikeEnabled to SensorsGpsFilterSection",
            dialogContent.contains("autoPauseBikeEnabled = autoPauseBikeEnabled")
        )
    }
}
