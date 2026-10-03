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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying spatial departure location geofence filtering,
 * active filter counter calculation, and JSON serialization for [ClusterFilterCriteria]
 * (REQ-UI-186.2, TST-UI-139.2, ATT-1402).
 */
class ClusterFilterCriteriaSpatialTest {

    private fun createCluster(
        startLat: Double = 48.137154,
        startLng: Double = 11.576124
    ): WorkoutCluster {
        return WorkoutCluster(
            id = 1L,
            name = "Isar Loop",
            probableSportId = 1L,
            startLat = startLat,
            startLng = startLng,
            endLat = startLat,
            endLng = startLng,
            maxDispLat = startLat + 0.01,
            maxDispLng = startLng + 0.01,
            refDistance = 10000.0,
            hitCount = 12,
            bSportType = BSportType.RUN
        )
    }

    @Test
    fun `matches returns true when cluster start matches exact filter center`() {
        val cluster = createCluster(startLat = 48.137154, startLng = 11.576124)
        val criteria = ClusterFilterCriteria(
            startLocationName = "Zuhause",
            startLocationLat = 48.137154,
            startLocationLng = 11.576124
        )

        assertTrue(criteria.matches(cluster))
    }

    @Test
    fun `matches returns true when cluster start is within default 200m radius`() {
        // ~111m north (0.001 deg latitude ~ 111m)
        val cluster = createCluster(startLat = 48.138154, startLng = 11.576124)
        val criteria = ClusterFilterCriteria(
            startLocationName = "Zuhause",
            startLocationLat = 48.137154,
            startLocationLng = 11.576124
        )

        assertTrue(criteria.matches(cluster))
    }

    @Test
    fun `matches returns false when cluster start is outside default 200m radius`() {
        // ~555m north (0.005 deg latitude ~ 555m)
        val cluster = createCluster(startLat = 48.142154, startLng = 11.576124)
        val criteria = ClusterFilterCriteria(
            startLocationName = "Zuhause",
            startLocationLat = 48.137154,
            startLocationLng = 11.576124
        )

        assertFalse(criteria.matches(cluster))
    }

    @Test
    fun `matches honors custom radius threshold`() {
        // ~555m north
        val cluster = createCluster(startLat = 48.142154, startLng = 11.576124)

        // Custom radius 1000m accepts cluster
        val acceptingCriteria = ClusterFilterCriteria(
            startLocationName = "Zuhause",
            startLocationLat = 48.137154,
            startLocationLng = 11.576124,
            startLocationRadiusM = 1000.0
        )
        assertTrue(acceptingCriteria.matches(cluster))

        // Custom radius 300m rejects cluster
        val rejectingCriteria = ClusterFilterCriteria(
            startLocationName = "Zuhause",
            startLocationLat = 48.137154,
            startLocationLng = 11.576124,
            startLocationRadiusM = 300.0
        )
        assertFalse(rejectingCriteria.matches(cluster))
    }

    @Test
    fun `activeFilterCount increments by 1 when spatial criteria is active`() {
        val emptyCriteria = ClusterFilterCriteria()
        assertEquals(0, emptyCriteria.activeFilterCount)
        assertTrue(emptyCriteria.isEmpty)

        val spatialCriteria = ClusterFilterCriteria(
            startLocationName = "Zuhause",
            startLocationLat = 48.137,
            startLocationLng = 11.576
        )
        assertEquals(1, spatialCriteria.activeFilterCount)
        assertTrue(spatialCriteria.isNotEmpty)

        val multiCriteria = spatialCriteria.copy(
            query = "Isar",
            minHitCount = 5
        )
        assertEquals(3, multiCriteria.activeFilterCount)
    }

    @Test
    fun `lossless JSON serialization and deserialization via toJson and fromJson`() {
        val original = ClusterFilterCriteria(
            query = "Trail",
            startLocationName = "Bergstation",
            startLocationLat = 47.416,
            startLocationLng = 11.009,
            startLocationRadiusM = 350.0
        )

        val jsonStr = original.toJson()
        val reconstructed = ClusterFilterCriteria.fromJson(jsonStr)

        assertEquals("Trail", reconstructed.query)
        assertEquals("Bergstation", reconstructed.startLocationName)
        assertEquals(47.416, reconstructed.startLocationLat!!, 0.0001)
        assertEquals(11.009, reconstructed.startLocationLng!!, 0.0001)
        assertEquals(350.0, reconstructed.startLocationRadiusM!!, 0.1)
    }

    @Test
    fun `fromJson handles legacy json without spatial fields gracefully`() {
        val legacyJson = """{"query":"Loop","minHitCount":3}"""
        val parsed = ClusterFilterCriteria.fromJson(legacyJson)

        assertEquals("Loop", parsed.query)
        assertEquals(3, parsed.minHitCount)
        assertNull(parsed.startLocationName)
        assertNull(parsed.startLocationLat)
        assertNull(parsed.startLocationLng)
        assertNull(parsed.startLocationRadiusM)
    }
}
