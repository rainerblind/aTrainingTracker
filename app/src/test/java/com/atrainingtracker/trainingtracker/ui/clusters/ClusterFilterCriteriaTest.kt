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

package com.atrainingtracker.trainingtracker.ui.clusters

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying multi-dimensional predicate matching, gear linking,
 * active filter count derivation, and JSON serialization/deserialization
 * for [ClusterFilterCriteria] (REQ-UI-135, TST-UI-088).
 */
class ClusterFilterCriteriaTest {

    private fun createCluster(
        id: Long = 101L,
        name: String = "Morning Isar Trail",
        probableSportId: Long = 1L,
        bSportType: BSportType = BSportType.RUN,
        refDistance: Double = 12500.0, // 12.5 km
        hitCount: Int = 8
    ): WorkoutCluster {
        return WorkoutCluster(
            id = id,
            name = name,
            probableSportId = probableSportId,
            startLat = 48.1351,
            startLng = 11.5820,
            endLat = 48.1351,
            endLng = 11.5820,
            maxDispLat = 48.1500,
            maxDispLng = 11.6000,
            refDistance = refDistance,
            hitCount = hitCount,
            bSportType = bSportType
        )
    }

    @Test
    fun testEmptyCriteriaMatchesEverything() {
        val criteria = ClusterFilterCriteria()
        val cluster = createCluster()
        assertTrue("Empty criteria should match any cluster", criteria.matches(cluster))
        assertTrue("Criteria should report isEmpty", criteria.isEmpty)
        assertFalse("Criteria should not report isNotEmpty", criteria.isNotEmpty)
        assertEquals("Active filter count should be 0", 0, criteria.activeFilterCount)
    }

    @Test
    fun testSearchQueryMatching() {
        val cluster = createCluster(name = "Lake Starnberg Loop")

        // Matches name case-insensitively
        val matchName = ClusterFilterCriteria(query = "starnberg")
        assertTrue("Query should match cluster name case-insensitively", matchName.matches(cluster))

        val matchUppercase = ClusterFilterCriteria(query = "LAKE")
        assertTrue("Query should match cluster name case-insensitively", matchUppercase.matches(cluster))

        // Non-matching query
        val noMatch = ClusterFilterCriteria(query = "Chiemsee")
        assertFalse("Non-matching query should return false", noMatch.matches(cluster))

        // Blank query matches everything
        val blankQuery = ClusterFilterCriteria(query = "   ")
        assertTrue("Blank query should match everything", blankQuery.matches(cluster))
        assertEquals("Blank query should not count as active dimension", 0, blankQuery.activeFilterCount)
    }

    @Test
    fun testEquipmentFiltering() {
        val cluster = createCluster()
        val linkedEquipment = setOf("Nike Pegasus", "Garmin HRM")

        // Matching equipment in linked set
        val matchFilter = ClusterFilterCriteria(equipmentName = "Nike Pegasus")
        assertTrue("Matches when target equipment is linked to cluster sport", matchFilter.matches(cluster, linkedEquipment))

        // Non-matching equipment
        val noMatchFilter = ClusterFilterCriteria(equipmentName = "Canyon Roadlite")
        assertFalse("Fails when target equipment is not in linked set", noMatchFilter.matches(cluster, linkedEquipment))

        // Empty linked equipment fails if filter active
        assertFalse("Fails when linked equipment set is empty", matchFilter.matches(cluster, emptySet()))
    }

    @Test
    fun testDistanceThresholdFiltering() {
        val shortCluster = createCluster(refDistance = 8000.0) // 8 km
        val longCluster = createCluster(refDistance = 35000.0) // 35 km

        val dist10kFilter = ClusterFilterCriteria(minDistanceMeters = 10000.0)
        assertFalse("8 km cluster fails 10 km threshold", dist10kFilter.matches(shortCluster))
        assertTrue("35 km cluster satisfies 10 km threshold", dist10kFilter.matches(longCluster))

        val dist25kFilter = ClusterFilterCriteria(minDistanceMeters = 25000.0)
        assertFalse("8 km cluster fails 25 km threshold", dist25kFilter.matches(shortCluster))
        assertTrue("35 km cluster satisfies 25 km threshold", dist25kFilter.matches(longCluster))
    }

    @Test
    fun testHitCountThresholdFiltering() {
        val lowRecordings = createCluster(hitCount = 2)
        val highRecordings = createCluster(hitCount = 12)

        val min3Filter = ClusterFilterCriteria(minHitCount = 3)
        assertFalse("2 recordings fail min 3 threshold", min3Filter.matches(lowRecordings))
        assertTrue("12 recordings satisfy min 3 threshold", min3Filter.matches(highRecordings))

        val min10Filter = ClusterFilterCriteria(minHitCount = 10)
        assertFalse("2 recordings fail min 10 threshold", min10Filter.matches(lowRecordings))
        assertTrue("12 recordings satisfy min 10 threshold", min10Filter.matches(highRecordings))
    }

    @Test
    fun testCombinedCriteriaMatching() {
        val matchingCluster = createCluster(
            name = "Englisher Garten Tempo Loop",
            refDistance = 15000.0, // 15 km
            hitCount = 10
        )
        val linkedEquipment = setOf("Asics Gel", "Polar H10")

        val criteria = ClusterFilterCriteria(
            query = "Garten",
            equipmentName = "Asics Gel",
            minDistanceMeters = 10000.0,
            minHitCount = 5
        )

        assertEquals("Should report 4 active filter dimensions", 4, criteria.activeFilterCount)
        assertTrue("Matching cluster satisfies all 4 dimensions", criteria.matches(matchingCluster, linkedEquipment))

        // Change equipment to unlinked
        val criteriaMismatchGear = criteria.copy(equipmentName = "Specialized Tarmac")
        assertFalse("Fails when gear does not match", criteriaMismatchGear.matches(matchingCluster, linkedEquipment))

        // Change distance to higher than cluster
        val criteriaMismatchDist = criteria.copy(minDistanceMeters = 25000.0)
        assertFalse("Fails when distance below threshold", criteriaMismatchDist.matches(matchingCluster, linkedEquipment))

        // Change hit count to higher than cluster
        val criteriaMismatchHits = criteria.copy(minHitCount = 25)
        assertFalse("Fails when recordings below threshold", criteriaMismatchHits.matches(matchingCluster, linkedEquipment))
    }

    @Test
    fun testActiveFilterCount() {
        assertEquals(0, ClusterFilterCriteria().activeFilterCount)
        assertEquals(1, ClusterFilterCriteria(query = "loop").activeFilterCount)
        assertEquals(1, ClusterFilterCriteria(equipmentName = "Bike").activeFilterCount)
        assertEquals(1, ClusterFilterCriteria(minDistanceMeters = 10000.0).activeFilterCount)
        assertEquals(1, ClusterFilterCriteria(minHitCount = 5).activeFilterCount)

        val multi = ClusterFilterCriteria(
            query = "loop",
            equipmentName = "Bike",
            minHitCount = 3
        )
        assertEquals(3, multi.activeFilterCount)
    }

    @Test
    fun testJsonSerializationAndDeserialization() {
        val original = ClusterFilterCriteria(
            query = "mountain pass",
            equipmentName = "BMC Teammachine",
            minDistanceMeters = 50000.0,
            minHitCount = 5
        )

        val jsonString = original.toJson()
        val restored = ClusterFilterCriteria.fromJson(jsonString)

        assertEquals(original.query, restored.query)
        assertEquals(original.equipmentName, restored.equipmentName)
        assertEquals(original.minDistanceMeters, restored.minDistanceMeters)
        assertEquals(original.minHitCount, restored.minHitCount)
        assertEquals(original.activeFilterCount, restored.activeFilterCount)
    }

    @Test
    fun testJsonDeserializationEmptyOrNull() {
        val emptyNull = ClusterFilterCriteria.fromJson(null)
        assertTrue(emptyNull.isEmpty)
        assertNull(emptyNull.equipmentName)
        assertNull(emptyNull.minDistanceMeters)
        assertNull(emptyNull.minHitCount)

        val emptyBlank = ClusterFilterCriteria.fromJson("   ")
        assertTrue(emptyBlank.isEmpty)

        val corrupted = ClusterFilterCriteria.fromJson("{ invalid json !!!")
        assertTrue(corrupted.isEmpty)
    }
}
