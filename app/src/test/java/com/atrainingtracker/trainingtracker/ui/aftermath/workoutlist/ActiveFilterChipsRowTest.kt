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
import java.io.File

/**
 * Unit & Contract test suite for [ActiveFilterChipsRow] functional transformer (REQ-UI-509.5, TST-UI-509.3).
 */
class ActiveFilterChipsRowTest {

    private fun loadSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("..", relativePath),
            File("../app", relativePath)
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    @Test
    fun testFunctionalTransformer_clearsQuery() {
        var criteria = WorkoutFilterCriteria(query = "Intervall")
        val onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit = { transform ->
            criteria = transform(criteria)
        }

        // Simulate query chip removal
        onUpdateCriteria { it.copy(query = "") }
        assertEquals("", criteria.query)
        assertTrue(criteria.isEmpty)
    }

    @Test
    fun testFunctionalTransformer_clearsDistanceRange() {
        var criteria = WorkoutFilterCriteria(
            minDistanceMeters = 10000.0,
            maxDistanceMeters = 25000.0
        )
        val onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit = { transform ->
            criteria = transform(criteria)
        }

        // Simulate distance range chip removal
        onUpdateCriteria { it.copy(minDistanceMeters = null, maxDistanceMeters = null) }
        assertNull(criteria.minDistanceMeters)
        assertNull(criteria.maxDistanceMeters)
        assertTrue(criteria.isEmpty)
    }

    @Test
    fun testFunctionalTransformer_clearsStartLocation() {
        var criteria = WorkoutFilterCriteria(
            startLocationName = "Home",
            startLocationLat = 48.1,
            startLocationLng = 11.5,
            startLocationRadiusM = 300.0
        )
        val onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit = { transform ->
            criteria = transform(criteria)
        }

        onUpdateCriteria {
            it.copy(
                startLocationName = null,
                startLocationLat = null,
                startLocationLng = null,
                startLocationRadiusM = null
            )
        }
        assertNull(criteria.startLocationName)
        assertNull(criteria.startLocationLat)
        assertNull(criteria.startLocationLng)
        assertNull(criteria.startLocationRadiusM)
        assertTrue(criteria.isEmpty)
    }

    @Test
    fun testFunctionalTransformer_clearsCluster() {
        var criteria = WorkoutFilterCriteria(
            clusterId = 12L,
            clusterName = "Isar Route"
        )
        val onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit = { transform ->
            criteria = transform(criteria)
        }

        onUpdateCriteria {
            it.copy(
                clusterId = null,
                clusterName = null
            )
        }
        assertNull(criteria.clusterId)
        assertNull(criteria.clusterName)
        assertTrue(criteria.isEmpty)
    }

    @Test
    fun testActiveFilterChipsRow_structuralContract_and_deadAliasPurge() {
        val file = loadSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRow.kt")
        assertTrue("ActiveFilterChipsRow source must exist", file.exists())
        val src = file.readText()

        // 1. Must expose onUpdateCriteria functional transformer overload
        assertTrue(
            "Must expose onUpdateCriteria transformer",
            src.contains("onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit")
        )

        // 2. Dead aliases must not exist in ActiveClusterFilterChipsRow or ActiveRouteFilterChipsRow
        val clusterFile = loadSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ActiveClusterFilterChipsRow.kt")
        assertTrue("ActiveClusterFilterChipsRow must exist", clusterFile.exists())
        assertFalse(
            "ActiveClusterFilterChipsRow must NOT have dead onRemoveMinDistance alias",
            clusterFile.readText().contains("onRemoveMinDistance")
        )

        val routeFile = loadSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ActiveRouteFilterChipsRow.kt")
        assertTrue("ActiveRouteFilterChipsRow must exist", routeFile.exists())
        assertFalse(
            "ActiveRouteFilterChipsRow must NOT have dead onRemoveMinDistance alias",
            routeFile.readText().contains("onRemoveMinDistance")
        )
        assertFalse(
            "ActiveRouteFilterChipsRow must NOT have dead onRemoveMinElevation alias",
            routeFile.readText().contains("onRemoveMinElevation")
        )
    }
}
