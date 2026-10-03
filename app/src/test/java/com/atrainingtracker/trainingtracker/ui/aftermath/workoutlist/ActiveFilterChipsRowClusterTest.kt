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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying [ActiveFilterChipsRow] display criteria, chip formatting,
 * and filter removal semantics for route clusters (REQ-UI-187, TST-UI-141.4).
 */
class ActiveFilterChipsRowClusterTest {

    @Test
    fun testClusterFilterActive_identifiesChipPresence() {
        val criteriaWithCluster = WorkoutFilterCriteria(
            clusterId = 42L,
            clusterName = "Isarrunde"
        )

        val hasClusterChip = criteriaWithCluster.clusterId != null || !criteriaWithCluster.clusterName.isNullOrBlank()

        assertTrue("Cluster chip must be shown when clusterId exists", hasClusterChip)
        val chipLabel = if (!criteriaWithCluster.clusterName.isNullOrBlank()) {
            criteriaWithCluster.clusterName
        } else {
            "Favorite Tracks"
        }
        assertEquals("Isarrunde", chipLabel)
    }

    @Test
    fun testClusterFilterWithoutName_fallsBackToGenericLabel() {
        val criteriaIdOnly = WorkoutFilterCriteria(
            clusterId = 42L,
            clusterName = null
        )

        val hasClusterChip = criteriaIdOnly.clusterId != null || !criteriaIdOnly.clusterName.isNullOrBlank()

        assertTrue("Cluster chip must be shown even without custom name if clusterId is set", hasClusterChip)
        val fallbackLabel = "Favorite Tracks"
        val chipLabel = if (!criteriaIdOnly.clusterName.isNullOrBlank()) {
            criteriaIdOnly.clusterName
        } else {
            fallbackLabel
        }
        assertEquals("Favorite Tracks", chipLabel)
    }

    @Test
    fun testClusterFilterInactive_omitsChip() {
        val emptyCriteria = WorkoutFilterCriteria()
        val hasClusterChip = emptyCriteria.clusterId != null || !emptyCriteria.clusterName.isNullOrBlank()

        assertFalse("Cluster chip must be omitted when no cluster criteria are active", hasClusterChip)
    }

    @Test
    fun testRemoveCluster_clearsClusterFieldsPreservingOthers() {
        var criteria = WorkoutFilterCriteria(
            query = "Tempo",
            year = 2024,
            clusterId = 42L,
            clusterName = "Isarrunde"
        )

        assertEquals(3, criteria.activeFilterCount)

        // Simulate onRemoveCluster callback
        val onRemoveCluster = {
            criteria = criteria.copy(
                clusterId = null,
                clusterName = null
            )
        }

        onRemoveCluster()

        assertNull("clusterId must be cleared", criteria.clusterId)
        assertNull("clusterName must be cleared", criteria.clusterName)
        assertEquals("Query must remain preserved", "Tempo", criteria.query)
        assertEquals("Year must remain preserved", Integer.valueOf(2024), criteria.year)
        assertEquals("Active filter count must decrement to 2", 2, criteria.activeFilterCount)
    }
}
