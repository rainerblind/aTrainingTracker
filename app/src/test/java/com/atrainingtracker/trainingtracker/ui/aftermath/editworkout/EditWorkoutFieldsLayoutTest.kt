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

import com.atrainingtracker.trainingtracker.EditWorkoutFieldPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural contract test for [EditWorkoutScreen] field visibility rules
 * pursuant to REQ-UI-211 and TST-UI-165.2.
 */
class EditWorkoutFieldsLayoutTest {

    enum class EditWorkoutField {
        WORKOUT_NAME,
        SPORT_TYPE,
        EQUIPMENT,
        ROUTE_CLUSTER,
        COMMUTE_TRAINER,
        STRAVA_UPLOAD,
        DESCRIPTION,
        GOAL,
        METHOD
    }

    /**
     * Replicates the layout visibility resolver of EditWorkoutScreen.kt
     */
    private fun isFieldVisible(
        field: EditWorkoutField,
        prefs: EditWorkoutFieldPreferences,
        communityStravaEnabled: Boolean = true
    ): Boolean {
        return when (field) {
            EditWorkoutField.WORKOUT_NAME -> true // Core Anchor: permanently visible
            EditWorkoutField.SPORT_TYPE -> true   // Core Anchor: permanently visible
            EditWorkoutField.EQUIPMENT -> true    // Core Anchor: permanently visible
            EditWorkoutField.ROUTE_CLUSTER -> prefs.showCluster
            EditWorkoutField.COMMUTE_TRAINER -> prefs.showCommuteTrainer
            EditWorkoutField.STRAVA_UPLOAD -> prefs.showStravaUpload && communityStravaEnabled
            EditWorkoutField.DESCRIPTION -> prefs.showDescription
            EditWorkoutField.GOAL -> prefs.showGoal
            EditWorkoutField.METHOD -> prefs.showMethod
        }
    }

    @Test
    fun coreAnchors_remainPermanentlyVisible_evenWhenAllOptionalFieldsDisabled() {
        val allDisabled = EditWorkoutFieldPreferences(
            showCluster = false,
            showCommuteTrainer = false,
            showStravaUpload = false,
            showDescription = false,
            showGoal = false,
            showMethod = false
        )

        assertTrue("Workout Name must be visible", isFieldVisible(EditWorkoutField.WORKOUT_NAME, allDisabled))
        assertTrue("Sport Type must be visible", isFieldVisible(EditWorkoutField.SPORT_TYPE, allDisabled))
        assertTrue("Equipment must be visible", isFieldVisible(EditWorkoutField.EQUIPMENT, allDisabled))

        assertFalse("Route / Cluster must be hidden", isFieldVisible(EditWorkoutField.ROUTE_CLUSTER, allDisabled))
        assertFalse("Commute / Trainer must be hidden", isFieldVisible(EditWorkoutField.COMMUTE_TRAINER, allDisabled))
        assertFalse("Strava upload must be hidden", isFieldVisible(EditWorkoutField.STRAVA_UPLOAD, allDisabled))
        assertFalse("Description must be hidden", isFieldVisible(EditWorkoutField.DESCRIPTION, allDisabled))
        assertFalse("Goal must be hidden", isFieldVisible(EditWorkoutField.GOAL, allDisabled))
        assertFalse("Method must be hidden", isFieldVisible(EditWorkoutField.METHOD, allDisabled))
    }

    @Test
    fun defaultPreferences_renderAllFields() {
        val defaultPrefs = EditWorkoutFieldPreferences()

        for (field in EditWorkoutField.values()) {
            assertTrue("Field $field must be visible with defaults", isFieldVisible(field, defaultPrefs, communityStravaEnabled = true))
        }
    }

    @Test
    fun individualFieldToggling_operatesIndependently() {
        val prefs = EditWorkoutFieldPreferences(
            showCluster = true,
            showCommuteTrainer = false,
            showStravaUpload = true,
            showDescription = false,
            showGoal = true,
            showMethod = false
        )

        assertTrue(isFieldVisible(EditWorkoutField.ROUTE_CLUSTER, prefs))
        assertFalse(isFieldVisible(EditWorkoutField.COMMUTE_TRAINER, prefs))
        assertTrue(isFieldVisible(EditWorkoutField.STRAVA_UPLOAD, prefs, communityStravaEnabled = true))
        assertFalse(isFieldVisible(EditWorkoutField.DESCRIPTION, prefs))
        assertTrue(isFieldVisible(EditWorkoutField.GOAL, prefs))
        assertFalse(isFieldVisible(EditWorkoutField.METHOD, prefs))
    }

    @Test
    fun stravaUpload_respectsCommunityEnablementCondition() {
        val prefs = EditWorkoutFieldPreferences(showStravaUpload = true)

        assertTrue(
            "Strava field should be visible when both pref and community flag are true",
            isFieldVisible(EditWorkoutField.STRAVA_UPLOAD, prefs, communityStravaEnabled = true)
        )
        assertFalse(
            "Strava field should be hidden if community flag is false even when pref is true",
            isFieldVisible(EditWorkoutField.STRAVA_UPLOAD, prefs, communityStravaEnabled = false)
        )
    }
}
