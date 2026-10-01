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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [EditWorkoutFieldPreferences] data contract, default states,
 * and immutability invariants under REQ-UI-211 and TST-UI-165.1.
 */
class EditWorkoutFieldPreferencesTest {

    @Test
    fun defaultPreferences_haveAllFieldsEnabled() {
        val prefs = EditWorkoutFieldPreferences()

        assertTrue("Route / Cluster should be enabled by default", prefs.showCluster)
        assertTrue("Commute & Trainer should be enabled by default", prefs.showCommuteTrainer)
        assertTrue("Strava upload should be enabled by default", prefs.showStravaUpload)
        assertTrue("Description should be enabled by default", prefs.showDescription)
        assertTrue("Goal should be enabled by default", prefs.showGoal)
        assertTrue("Method should be enabled by default", prefs.showMethod)
    }

    @Test
    fun copyPreferences_modifiesOnlySpecifiedFields() {
        val original = EditWorkoutFieldPreferences()
        val modified = original.copy(
            showCluster = false,
            showGoal = false,
            showMethod = false
        )

        assertFalse(modified.showCluster)
        assertTrue(modified.showCommuteTrainer)
        assertTrue(modified.showStravaUpload)
        assertTrue(modified.showDescription)
        assertFalse(modified.showGoal)
        assertFalse(modified.showMethod)
    }

    @Test
    fun customAllDisabledConfiguration_maintainsFidelity() {
        val allDisabled = EditWorkoutFieldPreferences(
            showCluster = false,
            showCommuteTrainer = false,
            showStravaUpload = false,
            showDescription = false,
            showGoal = false,
            showMethod = false
        )

        assertFalse(allDisabled.showCluster)
        assertFalse(allDisabled.showCommuteTrainer)
        assertFalse(allDisabled.showStravaUpload)
        assertFalse(allDisabled.showDescription)
        assertFalse(allDisabled.showGoal)
        assertFalse(allDisabled.showMethod)
    }

    @Test
    fun equalityAndHashCode_workConsistently() {
        val prefs1 = EditWorkoutFieldPreferences(showCluster = false)
        val prefs2 = EditWorkoutFieldPreferences(showCluster = false)
        val prefs3 = EditWorkoutFieldPreferences(showCluster = true)

        assertEquals(prefs1, prefs2)
        assertEquals(prefs1.hashCode(), prefs2.hashCode())
        assertFalse(prefs1 == prefs3)
    }
}
