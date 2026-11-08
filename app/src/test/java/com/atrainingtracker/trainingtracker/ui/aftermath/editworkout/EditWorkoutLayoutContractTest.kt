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

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test for [EditWorkoutScreen] Strava row layout ordering
 * and non-destructive disabled alpha pursuant to REQ-UI-497.3 and TST-UI-497.2.
 */
class EditWorkoutLayoutContractTest {

    private fun findEditWorkoutScreenFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("EditWorkoutScreen.kt not found in candidates: $candidates")
    }

    /**
     * TST-UI-497.2: Verify Strava row child ordering (Checkbox precedes Icon) and
     * non-destructive alpha dimming with tint = Color.Unspecified (REQ-UI-497.3).
     */
    @Test
    fun testStravaRowLayoutOrderingAndDisabledAlpha() {
        val file = findEditWorkoutScreenFile()
        val content = file.readText()

        val stravaSectionStart = content.indexOf("uploadToCommunity(FileFormat.STRAVA)")
        assertTrue("Strava upload section must exist in EditWorkoutScreen.kt", stravaSectionStart > 0)
        val stravaSection = content.substring(stravaSectionStart, stravaSectionStart + 1500)

        val checkboxIdx = stravaSection.indexOf("Checkbox(")
        val iconIdx = stravaSection.indexOf("Icon(")
        val spacerIdx = stravaSection.indexOf("Spacer(Modifier.width(8.dp))")
        val textIdx = stravaSection.indexOf("Text(")

        assertTrue("Checkbox must be present in Strava row", checkboxIdx >= 0)
        assertTrue("Icon must be present in Strava row", iconIdx >= 0)
        assertTrue("Spacer(8.dp) must be present in Strava row", spacerIdx >= 0)
        assertTrue("Text must be present in Strava row", textIdx >= 0)

        // Verify Checkbox is first (left-aligned with Commute/Race), then Icon, then Spacer, then Text
        assertTrue("Checkbox must precede Icon in Strava row (REQ-UI-497.3)", checkboxIdx < iconIdx)
        assertTrue("Icon must precede Spacer in Strava row (REQ-UI-497.3)", iconIdx < spacerIdx)
        assertTrue("Spacer must precede Text in Strava row (REQ-UI-497.3)", spacerIdx < textIdx)

        // Verify non-destructive alpha dimming and Color.Unspecified
        assertTrue(
            "Strava logo Icon must specify tint = Color.Unspecified (REQ-UI-497.3)",
            stravaSection.contains("tint = Color.Unspecified")
        )
        assertTrue(
            "Strava logo Icon must use .alpha(if (stravaMappingAvailable) 1f else 0.38f) (REQ-UI-497.3)",
            stravaSection.contains(".alpha(if (stravaMappingAvailable) 1f else 0.38f)")
        )
        assertFalse(
            "Strava logo Icon must NOT use Color.Gray tint (which turns logo into solid grey square ■)",
            stravaSection.contains("Color.Gray")
        )
    }
}
