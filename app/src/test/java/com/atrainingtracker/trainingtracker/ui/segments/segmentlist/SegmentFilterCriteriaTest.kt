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

package com.atrainingtracker.trainingtracker.ui.segments.segmentlist

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying multi-dimensional predicate matching, climb category filtering (including category 0 exclusion),
 * active filter count derivation, and JSON serialization/deserialization for [SegmentFilterCriteria] (REQ-UI-134, TST-UI-087).
 */
class SegmentFilterCriteriaTest {

    private fun createSegment(
        stravaId: Long = 1001L,
        name: String = "Alpe d'Huez",
        city: String = "Bourg d'Oisans",
        bSportType: BSportType = BSportType.BIKE,
        climbCategory_raw: Int = 5,
        climbCategory: String = "HC",
        prTime_raw: Int = 3120, // 52 minutes (has PR)
        prTime: String = "52:00",
        distance_raw: Double = 13800.0, // 13.8 km
        elevationGain_raw: Double = 1060.0 // 1060 m
    ): SegmentWithPath {
        val summary = SegmentSummary(
            stravaId = stravaId,
            name = name,
            bSportType = bSportType,
            climbCategory_raw = climbCategory_raw,
            climbCategory = climbCategory,
            prTime_raw = prTime_raw,
            prTime = prTime,
            city = city,
            distance = "${distance_raw / 1000.0} km",
            distance_raw = distance_raw,
            averageGrade_raw = 8.1,
            averageGrade = "8.1%",
            maxGrade = "13.0%",
            elevationGain_raw = elevationGain_raw,
            elevationGain = "${elevationGain_raw} m",
            elevationMin = "744 m",
            elevationMax = "1804 m",
            map_polyline = ""
        )
        return SegmentWithPath(summary = summary, path = emptyList())
    }

    @Test
    fun testEmptyCriteriaMatchesEverything() {
        val criteria = SegmentFilterCriteria()
        val segment = createSegment()
        assertTrue("Empty criteria should match any segment", criteria.matches(segment))
        assertTrue("Criteria should report isEmpty", criteria.isEmpty)
        assertFalse("Criteria should not report isNotEmpty", criteria.isNotEmpty)
        assertEquals("Active filter count should be 0", 0, criteria.activeFilterCount)
    }

    @Test
    fun testSearchQueryMatching() {
        val segment = createSegment(name = "Ventoux South", city = "Bedoin")

        // Matches name case-insensitively
        val matchName = SegmentFilterCriteria(query = "ventoux")
        assertTrue("Query should match segment name case-insensitively", matchName.matches(segment))

        // Matches city case-insensitively
        val matchCity = SegmentFilterCriteria(query = "BEDOIN")
        assertTrue("Query should match segment city case-insensitively", matchCity.matches(segment))

        // Non-matching query
        val noMatch = SegmentFilterCriteria(query = "Dolomites")
        assertFalse("Non-matching query should return false", noMatch.matches(segment))

        // Blank query matches everything
        val blankQuery = SegmentFilterCriteria(query = "   ")
        assertTrue("Blank query should match everything", blankQuery.matches(segment))
        assertEquals("Blank query should not count as active dimension", 0, blankQuery.activeFilterCount)
    }

    @Test
    fun testClimbCategoryFilteringAndCategoryZeroExclusion() {
        val hcSegment = createSegment(climbCategory_raw = 5) // HC
        val cat2Segment = createSegment(climbCategory_raw = 3) // Cat. 2
        val uncategorizedSegment = createSegment(climbCategory_raw = 0) // Cat. 0 (uncategorized)

        val cat3Filter = SegmentFilterCriteria(minClimbCategory = 2) // Cat. 3+ (raw >= 2)
        assertTrue("HC segment satisfies Cat. 3+ threshold", cat3Filter.matches(hcSegment))
        assertTrue("Cat. 2 segment satisfies Cat. 3+ threshold", cat3Filter.matches(cat2Segment))
        assertFalse(
            "Uncategorized segment (climbCategory_raw == 0) MUST be excluded when minClimbCategory is active",
            cat3Filter.matches(uncategorizedSegment)
        )

        val cat4Filter = SegmentFilterCriteria(minClimbCategory = 1) // Cat. 4+ (raw >= 1)
        assertFalse(
            "Uncategorized segment (climbCategory_raw == 0) MUST be excluded even for Cat. 4+ threshold",
            cat4Filter.matches(uncategorizedSegment)
        )

        val hcOnlyFilter = SegmentFilterCriteria(minClimbCategory = 5)
        assertTrue("HC segment satisfies HC threshold", hcOnlyFilter.matches(hcSegment))
        assertFalse("Cat. 2 segment does not satisfy HC threshold", hcOnlyFilter.matches(cat2Segment))
        assertFalse("Cat. 0 segment does not satisfy HC threshold", hcOnlyFilter.matches(uncategorizedSegment))
    }

    @Test
    fun testDistanceThresholdFiltering() {
        val shortSegment = createSegment(distance_raw = 3000.0) // 3 km
        val longSegment = createSegment(distance_raw = 15000.0) // 15 km

        val dist10kFilter = SegmentFilterCriteria(minDistanceMeters = 10000.0)
        assertFalse("3 km segment fails 10 km threshold", dist10kFilter.matches(shortSegment))
        assertTrue("15 km segment satisfies 10 km threshold", dist10kFilter.matches(longSegment))
    }

    @Test
    fun testElevationGainThresholdFiltering() {
        val flatSegment = createSegment(elevationGain_raw = 80.0) // 80 m
        val steepSegment = createSegment(elevationGain_raw = 750.0) // 750 m

        val elev500Filter = SegmentFilterCriteria(minElevationGainMeters = 500.0)
        assertFalse("80 m elevation gain fails 500 m threshold", elev500Filter.matches(flatSegment))
        assertTrue("750 m elevation gain satisfies 500 m threshold", elev500Filter.matches(steepSegment))
    }

    @Test
    fun testHasPRFiltering() {
        val segmentWithPR = createSegment(prTime_raw = 1800) // 30 min PR
        val segmentWithoutPR = createSegment(prTime_raw = 0) // No PR

        val prFilter = SegmentFilterCriteria(hasPR = true)
        assertTrue("Segment with PR matches hasPR filter", prFilter.matches(segmentWithPR))
        assertFalse("Segment with prTime_raw <= 0 fails hasPR filter", prFilter.matches(segmentWithoutPR))

        // When hasPR is null, both match
        val noFilter = SegmentFilterCriteria(hasPR = null)
        assertTrue("Null hasPR matches segment with PR", noFilter.matches(segmentWithPR))
        assertTrue("Null hasPR matches segment without PR", noFilter.matches(segmentWithoutPR))
    }

    @Test
    fun testCombinedCriteriaMatching() {
        val matchingSegment = createSegment(
            name = "Mount Baldy Hillclimb",
            city = "Claremont",
            climbCategory_raw = 4, // Cat. 1
            distance_raw = 12000.0,
            elevationGain_raw = 850.0,
            prTime_raw = 2700
        )

        val nonMatchingSegment = createSegment(
            name = "Mount Baldy Flat",
            city = "Claremont",
            climbCategory_raw = 0, // Uncategorized
            distance_raw = 12000.0,
            elevationGain_raw = 50.0,
            prTime_raw = 0
        )

        val criteria = SegmentFilterCriteria(
            query = "Baldy",
            minClimbCategory = 3, // Cat. 2+
            minDistanceMeters = 10000.0,
            minElevationGainMeters = 500.0,
            hasPR = true
        )

        assertEquals("Should report 5 active filter dimensions", 5, criteria.activeFilterCount)
        assertTrue("Matching segment satisfies all 5 dimensions", criteria.matches(matchingSegment))
        assertFalse("Non-matching segment fails combined filter", criteria.matches(nonMatchingSegment))
    }

    @Test
    fun testActiveFilterCount() {
        assertEquals(0, SegmentFilterCriteria().activeFilterCount)
        assertEquals(1, SegmentFilterCriteria(query = "test").activeFilterCount)
        assertEquals(1, SegmentFilterCriteria(minClimbCategory = 3).activeFilterCount)
        assertEquals(1, SegmentFilterCriteria(minDistanceMeters = 5000.0).activeFilterCount)
        assertEquals(1, SegmentFilterCriteria(minElevationGainMeters = 250.0).activeFilterCount)
        assertEquals(1, SegmentFilterCriteria(hasPR = true).activeFilterCount)

        val multi = SegmentFilterCriteria(
            query = "hill",
            minClimbCategory = 2,
            hasPR = true
        )
        assertEquals(3, multi.activeFilterCount)
    }

    @Test
    fun testJsonSerializationAndDeserialization() {
        val original = SegmentFilterCriteria(
            query = "stelvio",
            minClimbCategory = 5,
            minDistanceMeters = 20000.0,
            minElevationGainMeters = 1500.0,
            hasPR = true
        )

        val jsonString = original.toJson()
        val restored = SegmentFilterCriteria.fromJson(jsonString)

        assertEquals(original.query, restored.query)
        assertEquals(original.minClimbCategory, restored.minClimbCategory)
        assertEquals(original.minDistanceMeters, restored.minDistanceMeters)
        assertEquals(original.minElevationGainMeters, restored.minElevationGainMeters)
        assertEquals(original.hasPR, restored.hasPR)
        assertEquals(original.activeFilterCount, restored.activeFilterCount)
    }

    @Test
    fun testJsonDeserializationEmptyOrNull() {
        val emptyNull = SegmentFilterCriteria.fromJson(null)
        assertTrue(emptyNull.isEmpty)

        val emptyBlank = SegmentFilterCriteria.fromJson("   ")
        assertTrue(emptyBlank.isEmpty)

        val corrupted = SegmentFilterCriteria.fromJson("{ invalid json !!!")
        assertTrue(corrupted.isEmpty)
    }
}
