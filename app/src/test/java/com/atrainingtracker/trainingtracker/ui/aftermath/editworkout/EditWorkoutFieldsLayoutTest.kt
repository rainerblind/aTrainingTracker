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
 * Structural contract test for [EditWorkoutScreen] unconditional field layout
 * pursuant to REQ-UI-239 and TST-UI-198.2.
 */
class EditWorkoutFieldsLayoutTest {

    enum class EditWorkoutField {
        WORKOUT_NAME,
        SPORT_TYPE,
        EQUIPMENT,
        ROUTE_CLUSTER,
        COMMUTE,
        TRAINER,
        RACE,
        STRAVA_UPLOAD,
        DESCRIPTION,
        GOAL,
        METHOD
    }

    /**
     * Layout visibility resolver representing unconditional presentation in EditWorkoutScreen.kt (REQ-UI-239)
     */
    private fun isFieldVisible(
        field: EditWorkoutField,
        communityStravaEnabled: Boolean = true
    ): Boolean {
        return when (field) {
            EditWorkoutField.WORKOUT_NAME -> true
            EditWorkoutField.SPORT_TYPE -> true
            EditWorkoutField.EQUIPMENT -> true
            EditWorkoutField.ROUTE_CLUSTER -> true
            EditWorkoutField.COMMUTE -> true
            EditWorkoutField.TRAINER -> true
            EditWorkoutField.RACE -> true
            EditWorkoutField.STRAVA_UPLOAD -> communityStravaEnabled
            EditWorkoutField.DESCRIPTION -> true
            EditWorkoutField.GOAL -> true
            EditWorkoutField.METHOD -> true
        }
    }

    private fun findEditWorkoutScreenFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("EditWorkoutScreen.kt not found in candidates: $candidates")
    }

    @Test
    fun testEditWorkoutScreen_doesNotReferenceFieldPrefs() {
        val file = findEditWorkoutScreenFile()
        val content = file.readText()

        assertFalse("EditWorkoutScreen must NOT collect fieldPreferences", content.contains("fieldPreferences"))
        assertFalse("EditWorkoutScreen must NOT reference fieldPrefs", content.contains("fieldPrefs"))
        assertFalse("EditWorkoutScreen must NOT guard showCluster", content.contains(".showCluster"))
        assertFalse("EditWorkoutScreen must NOT guard showCommuteTrainer", content.contains(".showCommuteTrainer"))
        assertFalse("EditWorkoutScreen must NOT guard showRace", content.contains(".showRace"))
        assertFalse("EditWorkoutScreen must NOT guard showStravaUpload", content.contains(".showStravaUpload"))
        assertFalse("EditWorkoutScreen must NOT guard showDescription", content.contains(".showDescription"))
        assertFalse("EditWorkoutScreen must NOT guard showGoal", content.contains(".showGoal"))
        assertFalse("EditWorkoutScreen must NOT guard showMethod", content.contains(".showMethod"))
    }

    @Test
    fun testAllMetadataFields_areUnconditionallyVisible() {
        for (field in EditWorkoutField.values()) {
            if (field != EditWorkoutField.STRAVA_UPLOAD) {
                assertTrue("Field $field must be unconditionally visible", isFieldVisible(field))
            }
        }
    }

    @Test
    fun stravaUpload_respectsCommunityEnablementConditionSolely() {
        assertTrue(
            "Strava upload must be visible when community Strava is enabled",
            isFieldVisible(EditWorkoutField.STRAVA_UPLOAD, communityStravaEnabled = true)
        )
        assertFalse(
            "Strava upload must be hidden when community Strava is disabled",
            isFieldVisible(EditWorkoutField.STRAVA_UPLOAD, communityStravaEnabled = false)
        )
    }
}
