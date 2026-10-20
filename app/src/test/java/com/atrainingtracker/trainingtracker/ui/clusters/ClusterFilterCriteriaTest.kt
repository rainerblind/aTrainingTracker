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
        assertEquals(1, ClusterFilterCriteria(maxDistanceMeters = 25000.0).activeFilterCount)
        assertEquals(1, ClusterFilterCriteria(minDistanceMeters = 10000.0, maxDistanceMeters = 25000.0).activeFilterCount)
        assertEquals(1, ClusterFilterCriteria(minHitCount = 5).activeFilterCount)

        val multi = ClusterFilterCriteria(
            query = "loop",
            equipmentName = "Bike",
            minDistanceMeters = 10000.0,
            maxDistanceMeters = 25000.0,
            minHitCount = 3
        )
        // query (1) + equipment (1) + distance interval (1) + hitCount (1) = 4
        assertEquals(4, multi.activeFilterCount)
    }

    @Test
    fun testJsonSerializationAndDeserialization() {
        val original = ClusterFilterCriteria(
            query = "mountain pass",
            equipmentName = "BMC Teammachine",
            minDistanceMeters = 50000.0,
            maxDistanceMeters = 90000.0,
            minHitCount = 5
        )

        val jsonString = original.toJson()
        val restored = ClusterFilterCriteria.fromJson(jsonString)

        assertEquals(original.query, restored.query)
        assertEquals(original.equipmentName, restored.equipmentName)
        assertEquals(original.minDistanceMeters, restored.minDistanceMeters)
        assertEquals(original.maxDistanceMeters, restored.maxDistanceMeters)
        assertEquals(original.minHitCount, restored.minHitCount)
        assertEquals(original.activeFilterCount, restored.activeFilterCount)
    }

    @Test
    fun testJsonDeserializationEmptyOrNull() {
        val emptyNull = ClusterFilterCriteria.fromJson(null)
        assertTrue(emptyNull.isEmpty)
        assertNull(emptyNull.equipmentName)
        assertNull(emptyNull.minDistanceMeters)
        assertNull(emptyNull.maxDistanceMeters)
        assertNull(emptyNull.minHitCount)

        val emptyBlank = ClusterFilterCriteria.fromJson("   ")
        assertTrue(emptyBlank.isEmpty)

        val corrupted = ClusterFilterCriteria.fromJson("{ invalid json !!!")
        assertTrue(corrupted.isEmpty)
    }

    /**
     * TST-UI-380.3: Favorite Tracks (Cluster) Distance Interval & Filter Count.
     * Verifies bounded interval matching, upper-bound only, lower-bound only,
     * and strictly single-dimension counting for activeFilterCount.
     */
    @Test
    fun testDistanceIntervalMatching_andActiveFilterCount_TST_UI_380_3() {
        val c8k = createCluster(id = 1L, refDistance = 8000.0)
        val c12_5k = createCluster(id = 2L, refDistance = 12500.0)
        val c25k = createCluster(id = 3L, refDistance = 25000.0)
        val c35k = createCluster(id = 4L, refDistance = 35000.0)
        val c50k = createCluster(id = 5L, refDistance = 50000.0)
        val c75k = createCluster(id = 6L, refDistance = 75000.0)

        // 1. Bounded interval [10 km, 35 km]
        val intervalFilter = ClusterFilterCriteria(minDistanceMeters = 10000.0, maxDistanceMeters = 35000.0)
        assertFalse("8 km cluster should not match [10k, 35k]", intervalFilter.matches(c8k))
        assertTrue("12.5 km cluster should match [10k, 35k]", intervalFilter.matches(c12_5k))
        assertTrue("25 km cluster should match [10k, 35k]", intervalFilter.matches(c25k))
        assertTrue("35 km cluster should match [10k, 35k] (inclusive max)", intervalFilter.matches(c35k))
        assertFalse("50 km cluster should not match [10k, 35k]", intervalFilter.matches(c50k))
        assertFalse("75 km cluster should not match [10k, 35k]", intervalFilter.matches(c75k))

        // 2. Upper bound only < 10 km
        val maxOnlyFilter = ClusterFilterCriteria(minDistanceMeters = null, maxDistanceMeters = 10000.0)
        assertTrue("8 km cluster should match < 10 km", maxOnlyFilter.matches(c8k))
        assertFalse("12.5 km cluster should not match < 10 km", maxOnlyFilter.matches(c12_5k))
        assertFalse("25 km cluster should not match < 10 km", maxOnlyFilter.matches(c25k))

        // 3. Lower bound only > 50 km
        val minOnlyFilter = ClusterFilterCriteria(minDistanceMeters = 50000.0, maxDistanceMeters = null)
        assertFalse("35 km cluster should not match > 50 km", minOnlyFilter.matches(c35k))
        assertTrue("50 km cluster should match > 50 km (inclusive min)", minOnlyFilter.matches(c50k))
        assertTrue("75 km cluster should match > 50 km", minOnlyFilter.matches(c75k))

        // 4. activeFilterCount derivation: distance interval must strictly contribute 1
        assertEquals("minOnly should have activeFilterCount = 1", 1, minOnlyFilter.activeFilterCount)
        assertEquals("maxOnly should have activeFilterCount = 1", 1, maxOnlyFilter.activeFilterCount)
        assertEquals("both bounds should still have activeFilterCount = 1", 1, intervalFilter.activeFilterCount)
    }

    /**
     * TST-UI-380.4: Cluster Criteria JSON Roundtrip & Backward Compatibility.
     */
    @Test
    fun testJsonSerializationRoundtrip_withDistanceInterval_andLegacyCompatibility_TST_UI_380_4() {
        val original = ClusterFilterCriteria(
            minDistanceMeters = 15000.0,
            maxDistanceMeters = 40000.0
        )
        val jsonStr = original.toJson()
        val restored = ClusterFilterCriteria.fromJson(jsonStr)

        assertEquals(15000.0, restored.minDistanceMeters)
        assertEquals(40000.0, restored.maxDistanceMeters)
        assertEquals(1, restored.activeFilterCount)

        // Legacy JSON without maxDistanceMeters
        val legacyJson = """{"query":"Isar","equipmentName":"Pegasus","minDistanceMeters":15000.0,"minHitCount":3}"""
        val fromLegacy = ClusterFilterCriteria.fromJson(legacyJson)

        assertEquals("Isar", fromLegacy.query)
        assertEquals("Pegasus", fromLegacy.equipmentName)
        assertEquals(15000.0, fromLegacy.minDistanceMeters)
        assertNull("maxDistanceMeters should be null for legacy JSON", fromLegacy.maxDistanceMeters)
        assertEquals(3, fromLegacy.minHitCount)
        assertEquals(4, fromLegacy.activeFilterCount)
    }

    /**
     * TST-UI-380.5: Active Distance Filter Chip Formatting & Dismissal Contract for Clusters.
     */
    @Test
    fun testActiveDistanceFilterChipFormatting_andDismissalContract_TST_UI_380_5() {
        fun formatChip(minM: Double?, maxM: Double?): String {
            val minKm = minM?.let { (it / 1000.0).let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } }
            val maxKm = maxM?.let { (it / 1000.0).let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } }
            return when {
                minKm != null && maxKm != null -> "$minKm - $maxKm km"
                minKm != null -> "> $minKm km"
                else -> "< $maxKm km"
            }
        }

        assertEquals("10 - 35 km", formatChip(10000.0, 35000.0))
        assertEquals("> 50 km", formatChip(50000.0, null))
        assertEquals("< 10 km", formatChip(null, 10000.0))
        assertEquals("12.5 - 25 km", formatChip(12500.0, 25000.0))

        // Dismissal contract: clearing distance range resets both bounds
        val criteria = ClusterFilterCriteria(minDistanceMeters = 10000.0, maxDistanceMeters = 35000.0)
        val cleared = criteria.copy(minDistanceMeters = null, maxDistanceMeters = null)
        assertNull(cleared.minDistanceMeters)
        assertNull(cleared.maxDistanceMeters)
        assertTrue(cleared.isEmpty)
    }
}
