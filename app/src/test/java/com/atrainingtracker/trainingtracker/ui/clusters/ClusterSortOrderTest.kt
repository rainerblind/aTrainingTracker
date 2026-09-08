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
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ClusterSortOrder] and cluster sorting logic (ATT-761 / REQ-UI-136 / TST-UI-089).
 */
class ClusterSortOrderTest {

    private fun createCluster(
        id: Long,
        name: String,
        refDistance: Double = 10000.0,
        hitCount: Int = 5,
        startLat: Double = 48.0,
        startLng: Double = 11.0,
        bSportType: BSportType = BSportType.BIKE
    ) = WorkoutCluster(
        id = id,
        name = name,
        probableSportId = 1L,
        startLat = startLat,
        startLng = startLng,
        endLat = startLat + 0.01,
        endLng = startLng + 0.01,
        maxDispLat = startLat + 0.02,
        maxDispLng = startLng + 0.02,
        refDistance = refDistance,
        hitCount = hitCount,
        bSportType = bSportType
    )

    private fun sortClusters(
        clusters: List<WorkoutCluster>,
        order: ClusterSortOrder,
        location: LatLng?
    ): List<WorkoutCluster> {
        return when (order) {
            ClusterSortOrder.RECORDINGS ->
                clusters.sortedWith(
                    compareByDescending<WorkoutCluster> { it.hitCount }
                        .thenBy { it.name.lowercase() }
                )
            ClusterSortOrder.DISTANCE ->
                clusters.sortedWith(
                    compareByDescending<WorkoutCluster> { it.refDistance }
                        .thenBy { it.name.lowercase() }
                )
            ClusterSortOrder.NAME ->
                clusters.sortedBy { it.name.lowercase() }
            ClusterSortOrder.DISTANCE_TO_USER -> {
                if (location == null) {
                    clusters.sortedBy { it.name.lowercase() }
                } else {
                    clusters.sortedBy { cluster ->
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            location.latitude, location.longitude,
                            cluster.startLat, cluster.startLng,
                            results
                        )
                        results[0]
                    }
                }
            }
        }
    }

    @Test
    fun testSortByRecordings_mostRecordingsFirst_andTieBreaksByName() {
        val c1 = createCluster(id = 1, name = "Zebra Track", hitCount = 5)
        val c2 = createCluster(id = 2, name = "Alpha Loop", hitCount = 20)
        val c3 = createCluster(id = 3, name = "Beta Pass", hitCount = 20)
        val c4 = createCluster(id = 4, name = "Delta Ride", hitCount = 1)

        val sorted = sortClusters(listOf(c1, c2, c3, c4), ClusterSortOrder.RECORDINGS, null)

        assertEquals(listOf("Alpha Loop", "Beta Pass", "Zebra Track", "Delta Ride"), sorted.map { it.name })
    }

    @Test
    fun testSortByDistance_longestTrackFirst_andTieBreaksByName() {
        val c1 = createCluster(id = 1, name = "Short Hill", refDistance = 5000.0)
        val c2 = createCluster(id = 2, name = "Centennial Ride", refDistance = 100000.0)
        val c3 = createCluster(id = 3, name = "Medium Trail", refDistance = 30000.0)
        val c4 = createCluster(id = 4, name = "Another Medium", refDistance = 30000.0)

        val sorted = sortClusters(listOf(c1, c2, c3, c4), ClusterSortOrder.DISTANCE, null)

        assertEquals(listOf("Centennial Ride", "Another Medium", "Medium Trail", "Short Hill"), sorted.map { it.name })
    }

    @Test
    fun testSortByName_caseInsensitiveAlphabetical() {
        val c1 = createCluster(id = 1, name = "zebra route")
        val c2 = createCluster(id = 2, name = "Alpha loop")
        val c3 = createCluster(id = 3, name = "beta track")

        val sorted = sortClusters(listOf(c1, c2, c3), ClusterSortOrder.NAME, null)

        assertEquals(listOf("Alpha loop", "beta track", "zebra route"), sorted.map { it.name })
    }

    @Test
    fun testSortByDistanceToUser_whenLocationNull_fallsBackToName() {
        val c1 = createCluster(id = 1, name = "Zebra route")
        val c2 = createCluster(id = 2, name = "Alpha loop")

        val sorted = sortClusters(listOf(c1, c2), ClusterSortOrder.DISTANCE_TO_USER, null)

        assertEquals(listOf("Alpha loop", "Zebra route"), sorted.map { it.name })
    }
}
