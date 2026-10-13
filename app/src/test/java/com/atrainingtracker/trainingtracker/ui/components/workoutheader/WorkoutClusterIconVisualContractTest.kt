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

package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Visual and architectural contract test enforcing favorite route icon disambiguation
 * for clusters in workout headers and editor (ATT-3277, REQ-SET-058, REQ-SET-059, TST-SET-064).
 *
 * Enforces:
 * 1. [WorkoutHeader.kt] renders [R.drawable.ic_favorite_route] for both clustered and unclustered cluster rows.
 * 2. [WorkoutHeader.kt] retains [R.drawable.my_locations] for start and destination endpoint rows.
 * 3. [EditWorkoutScreen.kt] renders [R.drawable.ic_favorite_route] as the leading icon for the selected route field.
 * 4. Vector asset `ic_favorite_route.xml` exists and contains valid path definitions.
 */
class WorkoutClusterIconVisualContractTest {

    private val headerSourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
        ).firstOrNull { it.exists() } ?: File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
    }

    private val editWorkoutSourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt")
        ).firstOrNull { it.exists() } ?: File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt")
    }

    private val favoriteRouteDrawableFile: File by lazy {
        listOf(
            File("src/main/res/drawable/ic_favorite_route.xml"),
            File("app/src/main/res/drawable/ic_favorite_route.xml"),
            File("../app/src/main/res/drawable/ic_favorite_route.xml")
        ).firstOrNull { it.exists() } ?: File("app/src/main/res/drawable/ic_favorite_route.xml")
    }

    @Test
    fun testVectorAsset_icFavoriteRouteExists() {
        assertTrue("ic_favorite_route.xml vector asset must exist", favoriteRouteDrawableFile.exists())
        val xmlContent = favoriteRouteDrawableFile.readText()
        assertTrue("ic_favorite_route.xml must be a vector drawable", xmlContent.contains("<vector"))
        assertTrue("ic_favorite_route.xml must contain path data", xmlContent.contains("pathData"))
    }

    @Test
    fun testWorkoutHeader_usesFavoriteRouteIconForClusters() {
        assertTrue("WorkoutHeader.kt source file must exist", headerSourceFile.exists())
        val content = headerSourceFile.readText()

        // Cluster button and unclustered row must use ic_favorite_route
        assertTrue(
            "WorkoutHeader.kt must use ic_favorite_route for cluster display",
            content.contains("R.drawable.ic_favorite_route")
        )

        // Cluster button specifically must reference ic_favorite_route
        val clusterButtonSnippet = content.substringAfter("Workout Cluster Info", "").substringBefore("// 2. BOTTOM CONTENT", "")
        assertTrue(
            "Cluster button section in WorkoutHeader.kt must use R.drawable.ic_favorite_route",
            clusterButtonSnippet.contains("R.drawable.ic_favorite_route")
        )
        assertFalse(
            "Cluster button section in WorkoutHeader.kt must NOT use R.drawable.my_locations",
            clusterButtonSnippet.contains("R.drawable.my_locations")
        )
    }

    @Test
    fun testWorkoutHeader_preservesMyLocationPinForEndpoints() {
        assertTrue("WorkoutHeader.kt source file must exist", headerSourceFile.exists())
        val content = headerSourceFile.readText()

        // Endpoint location rows must continue to use my_locations
        val locationSnippet = content.substringAfter("startLoc != null && endLoc != null", "").substringBefore("// 4. Action / Menu Button Area", "")
        assertTrue(
            "WorkoutHeader.kt start/destination endpoint rows must preserve R.drawable.my_locations",
            locationSnippet.contains("iconRes = R.drawable.my_locations")
        )
    }

    @Test
    fun testEditWorkoutScreen_usesFavoriteRouteIconForSelectedRouteField() {
        assertTrue("EditWorkoutScreen.kt source file must exist", editWorkoutSourceFile.exists())
        val content = editWorkoutSourceFile.readText()

        val routeFieldSnippet = content.substringAfter("cluster_naming__selected_route_label", "").substringBefore("trailingIcon", "")
        assertTrue(
            "EditWorkoutScreen.kt Selected Route field must use R.drawable.ic_favorite_route as leading icon",
            routeFieldSnippet.contains("R.drawable.ic_favorite_route")
        )
        assertFalse(
            "EditWorkoutScreen.kt Selected Route field must NOT use R.drawable.my_locations",
            routeFieldSnippet.contains("R.drawable.my_locations")
        )
    }
}
