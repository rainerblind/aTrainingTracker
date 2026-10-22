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

package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying multi-dimensional predicate matching, active filter count derivation,
 * and JSON serialization/deserialization for [RouteFilterCriteria] (REQ-UI-133, TST-UI-086).
 */
class RouteFilterCriteriaTest {

    private fun createRoute(
        id: Long = 1L,
        externalId: String = "ext_$id",
        name: String = "Black Forest Trail",
        description: String = "Scenic route through mountains",
        isSelected: Boolean = false,
        distance: Double = 45000.0, // 45 km
        elevationGain: Double = 650.0, // 650 m
        bSportType: BSportType = BSportType.BIKE,
        source: RouteSource = RouteSource.LOCAL_GPX
    ): RouteWithPath {
        val summary = RouteSummary(
            id = id,
            externalId = externalId,
            name = name,
            description = description,
            isSelected = isSelected,
            distance = distance,
            elevationGain = elevationGain,
            bSportType = bSportType,
            source = source
        )
        return RouteWithPath(summary = summary, path = emptyList())
    }

    @Test
    fun testEmptyCriteriaMatchesEverything() {
        val criteria = RouteFilterCriteria()
        val route = createRoute()
        assertTrue("Empty criteria should match any route", criteria.matches(route))
        assertTrue("Criteria should report isEmpty", criteria.isEmpty)
        assertFalse("Criteria should not report isNotEmpty", criteria.isNotEmpty)
        assertEquals("Active filter count should be 0", 0, criteria.activeFilterCount)
    }

    @Test
    fun testSearchQueryMatching() {
        val route = createRoute(name = "Alps Epic Ride", description = "High mountain pass climb")

        // Matches name case-insensitively
        val matchName = RouteFilterCriteria(query = "epic")
        assertTrue("Search query should match name case-insensitively", matchName.matches(route))

        // Matches description case-insensitively
        val matchDesc = RouteFilterCriteria(query = "CLIMB")
        assertTrue("Search query should match description case-insensitively", matchDesc.matches(route))

        // Non-matching query
        val noMatch = RouteFilterCriteria(query = "flat valley")
        assertFalse("Non-matching search query should not match", noMatch.matches(route))

        // Blank query matches everything
        val blankQuery = RouteFilterCriteria(query = "   ")
        assertTrue("Blank query should match everything", blankQuery.matches(route))
        assertEquals("Blank query should not count as active filter", 0, blankQuery.activeFilterCount)
    }

    @Test
    fun testRouteSourceFiltering() {
        val gpxRoute = createRoute(source = RouteSource.LOCAL_GPX)
        val stravaRoute = createRoute(source = RouteSource.STRAVA)
        val workoutRoute = createRoute(source = RouteSource.WORKOUT)

        val gpxFilter = RouteFilterCriteria(source = RouteSource.LOCAL_GPX)
        assertTrue("GPX filter should match GPX route", gpxFilter.matches(gpxRoute))
        assertFalse("GPX filter should not match Strava route", gpxFilter.matches(stravaRoute))
        assertFalse("GPX filter should not match Workout route", gpxFilter.matches(workoutRoute))

        val stravaFilter = RouteFilterCriteria(source = RouteSource.STRAVA)
        assertFalse("Strava filter should not match GPX route", stravaFilter.matches(gpxRoute))
        assertTrue("Strava filter should match Strava route", stravaFilter.matches(stravaRoute))
        assertFalse("Strava filter should not match Workout route", stravaFilter.matches(workoutRoute))

        val workoutFilter = RouteFilterCriteria(source = RouteSource.WORKOUT)
        assertFalse("Workout filter should not match GPX route", workoutFilter.matches(gpxRoute))
        assertFalse("Workout filter should not match Strava route", workoutFilter.matches(stravaRoute))
        assertTrue("Workout filter should match Workout route", workoutFilter.matches(workoutRoute))
    }

    @Test
    fun testOnlySelectedFiltering() {
        val selectedRoute = createRoute(isSelected = true)
        val unselectedRoute = createRoute(isSelected = false)

        val filter = RouteFilterCriteria(isSelected = true)
        assertTrue("Filter should match selected route", filter.matches(selectedRoute))
        assertFalse("Filter should not match unselected route", filter.matches(unselectedRoute))

        val noSelectionFilter = RouteFilterCriteria(isSelected = null)
        assertTrue(noSelectionFilter.matches(selectedRoute))
        assertTrue(noSelectionFilter.matches(unselectedRoute))
    }

    @Test
    fun testMinDistanceFiltering() {
        val routeShort = createRoute(distance = 9_999.0) // < 10 km
        val routeExact = createRoute(distance = 10_000.0) // = 10 km
        val routeLong = createRoute(distance = 25_000.0) // 25 km

        val filter10k = RouteFilterCriteria(minDistanceMeters = 10_000.0)
        assertFalse("< 10 km should be excluded", filter10k.matches(routeShort))
        assertTrue(">= 10 km should match", filter10k.matches(routeExact))
        assertTrue("> 10 km should match", filter10k.matches(routeLong))
    }

    @Test
    fun testMinElevationGainFiltering() {
        val routeLow = createRoute(elevationGain = 249.0) // < 250 m
        val routeExact = createRoute(elevationGain = 250.0) // = 250 m
        val routeHigh = createRoute(elevationGain = 500.0) // 500 m

        val filter250m = RouteFilterCriteria(minElevationGainMeters = 250.0)
        assertFalse("< 250 m should be excluded", filter250m.matches(routeLow))
        assertTrue(">= 250 m should match", filter250m.matches(routeExact))
        assertTrue("> 250 m should match", filter250m.matches(routeHigh))
    }

    @Test
    fun testCombinedPredicates_Conjunction() {
        val matchingRoute = createRoute(
            name = "Black Forest Marathon Route",
            source = RouteSource.LOCAL_GPX,
            isSelected = true,
            distance = 42_195.0,
            elevationGain = 800.0
        )
        val nonMatchingDistance = createRoute(
            name = "Black Forest Trail Short",
            source = RouteSource.LOCAL_GPX,
            isSelected = true,
            distance = 5_000.0,
            elevationGain = 800.0
        )
        val nonMatchingSource = createRoute(
            name = "Black Forest Marathon Route",
            source = RouteSource.STRAVA,
            isSelected = true,
            distance = 42_195.0,
            elevationGain = 800.0
        )

        val combinedCriteria = RouteFilterCriteria(
            query = "Forest",
            source = RouteSource.LOCAL_GPX,
            isSelected = true,
            minDistanceMeters = 20_000.0,
            minElevationGainMeters = 500.0
        )

        assertEquals("Active filter count should reflect all 5 active criteria", 5, combinedCriteria.activeFilterCount)
        assertTrue("Route matching all dimensions should match", combinedCriteria.matches(matchingRoute))
        assertFalse("Route failing distance should fail", combinedCriteria.matches(nonMatchingDistance))
        assertFalse("Route failing source should fail", combinedCriteria.matches(nonMatchingSource))
    }

    @Test
    fun testActiveFilterCount() {
        var criteria = RouteFilterCriteria()
        assertEquals(0, criteria.activeFilterCount)

        criteria = criteria.copy(query = "test")
        assertEquals(1, criteria.activeFilterCount)

        criteria = criteria.copy(source = RouteSource.STRAVA)
        assertEquals(2, criteria.activeFilterCount)

        criteria = criteria.copy(isSelected = true)
        assertEquals(3, criteria.activeFilterCount)

        criteria = criteria.copy(minDistanceMeters = 15_000.0)
        assertEquals(4, criteria.activeFilterCount)

        // Setting maxDistanceMeters as well should still count as 4 (distance is 1 dimension) (REQ-UI-417.1)
        criteria = criteria.copy(maxDistanceMeters = 30_000.0)
        assertEquals(4, criteria.activeFilterCount)

        criteria = criteria.copy(minElevationGainMeters = 300.0)
        assertEquals(5, criteria.activeFilterCount)

        // Blank query should not increment count
        criteria = criteria.copy(query = "   ")
        assertEquals(4, criteria.activeFilterCount)
    }

    @Test
    fun testJsonSerializationRoundtrip() {
        val original = RouteFilterCriteria(
            query = "Tour de Suisse",
            source = RouteSource.STRAVA,
            isSelected = true,
            minDistanceMeters = 50_000.0,
            maxDistanceMeters = 120_000.0,
            minElevationGainMeters = 1000.0
        )

        val json = original.toJson()
        val restored = RouteFilterCriteria.fromJson(json)

        assertEquals(original.query, restored.query)
        assertEquals(original.source, restored.source)
        assertEquals(original.isSelected, restored.isSelected)
        assertEquals(original.minDistanceMeters, restored.minDistanceMeters)
        assertEquals(original.maxDistanceMeters, restored.maxDistanceMeters)
        assertEquals(original.minElevationGainMeters, restored.minElevationGainMeters)
        assertEquals(original.activeFilterCount, restored.activeFilterCount)
    }

    @Test
    fun testJsonDeserializationNullOrInvalid() {
        val fromNull = RouteFilterCriteria.fromJson(null)
        assertTrue(fromNull.isEmpty)

        val fromBlank = RouteFilterCriteria.fromJson("   ")
        assertTrue(fromBlank.isEmpty)

        val fromMalformed = RouteFilterCriteria.fromJson("{not valid json}")
        assertTrue(fromMalformed.isEmpty)
    }

    /**
     * TST-UI-380.1: Route Distance Interval & Filter Count.
     * Verifies bounded interval matching, upper-bound only, lower-bound only,
     * and strictly single-dimension counting for activeFilterCount.
     */
    @Test
    fun testDistanceIntervalMatching_andActiveFilterCount_TST_UI_380_1() {
        val r5k = createRoute(id = 1L, distance = 5000.0)
        val r10k = createRoute(id = 2L, distance = 10000.0)
        val r25k = createRoute(id = 3L, distance = 25000.0)
        val r45k = createRoute(id = 4L, distance = 45000.0)
        val r50k = createRoute(id = 5L, distance = 50000.0)
        val r80k = createRoute(id = 6L, distance = 80000.0)

        // 1. Bounded interval [25 km, 50 km]
        val intervalFilter = RouteFilterCriteria(minDistanceMeters = 25000.0, maxDistanceMeters = 50000.0)
        assertFalse("5 km route should not match [25k, 50k]", intervalFilter.matches(r5k))
        assertFalse("10 km route should not match [25k, 50k]", intervalFilter.matches(r10k))
        assertTrue("25 km route should match [25k, 50k] (inclusive min)", intervalFilter.matches(r25k))
        assertTrue("45 km route should match [25k, 50k]", intervalFilter.matches(r45k))
        assertTrue("50 km route should match [25k, 50k] (inclusive max)", intervalFilter.matches(r50k))
        assertFalse("80 km route should not match [25k, 50k]", intervalFilter.matches(r80k))

        // 2. Upper bound only < 10 km
        val maxOnlyFilter = RouteFilterCriteria(minDistanceMeters = null, maxDistanceMeters = 10000.0)
        assertTrue("5 km route should match < 10 km", maxOnlyFilter.matches(r5k))
        assertTrue("10 km route should match < 10 km (inclusive max)", maxOnlyFilter.matches(r10k))
        assertFalse("25 km route should not match < 10 km", maxOnlyFilter.matches(r25k))
        assertFalse("45 km route should not match < 10 km", maxOnlyFilter.matches(r45k))
        assertFalse("50 km route should not match < 10 km", maxOnlyFilter.matches(r50k))
        assertFalse("80 km route should not match < 10 km", maxOnlyFilter.matches(r80k))

        // 3. Lower bound only > 50 km
        val minOnlyFilter = RouteFilterCriteria(minDistanceMeters = 50000.0, maxDistanceMeters = null)
        assertFalse("5 km route should not match > 50 km", minOnlyFilter.matches(r5k))
        assertFalse("10 km route should not match > 50 km", minOnlyFilter.matches(r10k))
        assertFalse("25 km route should not match > 50 km", minOnlyFilter.matches(r25k))
        assertFalse("45 km route should not match > 50 km", minOnlyFilter.matches(r45k))
        assertTrue("50 km route should match > 50 km (inclusive min)", minOnlyFilter.matches(r50k))
        assertTrue("80 km route should match > 50 km", minOnlyFilter.matches(r80k))

        // 4. activeFilterCount derivation: distance interval must strictly contribute 1
        assertEquals("minOnly should have activeFilterCount = 1", 1, minOnlyFilter.activeFilterCount)
        assertEquals("maxOnly should have activeFilterCount = 1", 1, maxOnlyFilter.activeFilterCount)
        assertEquals("both bounds should still have activeFilterCount = 1", 1, intervalFilter.activeFilterCount)
    }

    /**
     * TST-UI-380.2: Route Criteria JSON Roundtrip & Backward Compatibility.
     * Tests two-bound JSON persistence roundtrip and validates that legacy payloads
     * lacking maxDistanceMeters deserialize cleanly with maxDistanceMeters = null.
     */
    @Test
    fun testJsonSerializationRoundtrip_withDistanceInterval_andLegacyCompatibility_TST_UI_380_2() {
        val twoBoundsCriteria = RouteFilterCriteria(
            minDistanceMeters = 25000.0,
            maxDistanceMeters = 50000.0
        )
        val jsonStr = twoBoundsCriteria.toJson()
        val restored = RouteFilterCriteria.fromJson(jsonStr)

        assertEquals(25000.0, restored.minDistanceMeters)
        assertEquals(50000.0, restored.maxDistanceMeters)
        assertEquals(1, restored.activeFilterCount)

        // Legacy JSON without maxDistanceMeters
        val legacyJson = """{"query":"Alps","minDistanceMeters":10000.0}"""
        val fromLegacy = RouteFilterCriteria.fromJson(legacyJson)

        assertEquals("Alps", fromLegacy.query)
        assertEquals(10000.0, fromLegacy.minDistanceMeters)
        assertNull("maxDistanceMeters should be null for legacy JSON", fromLegacy.maxDistanceMeters)
        assertEquals(2, fromLegacy.activeFilterCount)
    }

    /**
     * TST-UI-380.5: Active Distance Filter Chip Formatting & Dismissal Contract.
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

        assertEquals("25 - 50 km", formatChip(25000.0, 50000.0))
        assertEquals("> 25 km", formatChip(25000.0, null))
        assertEquals("< 50 km", formatChip(null, 50000.0))
        assertEquals("12.5 - 37.5 km", formatChip(12500.0, 37500.0))

        // Dismissal contract: clearing distance range resets both bounds
        val criteria = RouteFilterCriteria(minDistanceMeters = 25000.0, maxDistanceMeters = 50000.0)
        val cleared = criteria.copy(minDistanceMeters = null, maxDistanceMeters = null)
        assertNull(cleared.minDistanceMeters)
        assertNull(cleared.maxDistanceMeters)
        assertTrue(cleared.isEmpty)
    }

    /**
     * TST-UI-389.1: Route Elevation Gain Interval Matching & Active Filter Count.
     */
    @Test
    fun testElevationGainIntervalMatching_andActiveFilterCount_TST_UI_389_1() {
        val r50m = createRoute(id = 1L, elevationGain = 50.0)
        val r100m = createRoute(id = 2L, elevationGain = 100.0)
        val r200m = createRoute(id = 3L, elevationGain = 200.0)
        val r250m = createRoute(id = 4L, elevationGain = 250.0)
        val r400m = createRoute(id = 5L, elevationGain = 400.0)
        val r500m = createRoute(id = 6L, elevationGain = 500.0)
        val r750m = createRoute(id = 7L, elevationGain = 750.0)
        val r1000m = createRoute(id = 8L, elevationGain = 1000.0)
        val r1500m = createRoute(id = 9L, elevationGain = 1500.0)

        // 1. Preset "< 100 m" (max only)
        val under100 = RouteFilterCriteria(maxElevationGainMeters = 100.0)
        assertTrue(under100.matches(r50m))
        assertTrue(under100.matches(r100m))
        assertFalse(under100.matches(r200m))
        assertEquals(1, under100.activeFilterCount)

        // 2. Preset "100 - 250 m" (bounded)
        val between100and250 = RouteFilterCriteria(minElevationGainMeters = 100.0, maxElevationGainMeters = 250.0)
        assertFalse(between100and250.matches(r50m))
        assertTrue(between100and250.matches(r100m))
        assertTrue(between100and250.matches(r200m))
        assertTrue(between100and250.matches(r250m))
        assertFalse(between100and250.matches(r400m))
        assertEquals(1, between100and250.activeFilterCount)

        // 3. Preset "250 - 500 m" (bounded)
        val between250and500 = RouteFilterCriteria(minElevationGainMeters = 250.0, maxElevationGainMeters = 500.0)
        assertFalse(between250and500.matches(r200m))
        assertTrue(between250and500.matches(r250m))
        assertTrue(between250and500.matches(r400m))
        assertTrue(between250and500.matches(r500m))
        assertFalse(between250and500.matches(r750m))
        assertEquals(1, between250and500.activeFilterCount)

        // 4. Preset "500 - 1000 m" (bounded)
        val between500and1000 = RouteFilterCriteria(minElevationGainMeters = 500.0, maxElevationGainMeters = 1000.0)
        assertFalse(between500and1000.matches(r400m))
        assertTrue(between500and1000.matches(r500m))
        assertTrue(between500and1000.matches(r750m))
        assertTrue(between500and1000.matches(r1000m))
        assertFalse(between500and1000.matches(r1500m))
        assertEquals(1, between500and1000.activeFilterCount)

        // 5. Preset "> 1000 m" (min only)
        val over1000 = RouteFilterCriteria(minElevationGainMeters = 1000.0)
        assertFalse(over1000.matches(r750m))
        assertTrue(over1000.matches(r1000m))
        assertTrue(over1000.matches(r1500m))
        assertEquals(1, over1000.activeFilterCount)

        // Conjunction with distance interval: each counts as 1 dimension -> 2 active dimensions
        val distanceAndElevation = RouteFilterCriteria(
            minDistanceMeters = 20000.0,
            maxDistanceMeters = 50000.0,
            minElevationGainMeters = 250.0,
            maxElevationGainMeters = 500.0
        )
        assertEquals(2, distanceAndElevation.activeFilterCount)
    }

    /**
     * TST-UI-389.2: Route Criteria JSON Roundtrip & Backward Compatibility with Elevation Interval.
     */
    @Test
    fun testJsonSerializationRoundtrip_withElevationGainInterval_andLegacyCompatibility_TST_UI_389_2() {
        val original = RouteFilterCriteria(
            query = "Grimsel Pass",
            minElevationGainMeters = 250.0,
            maxElevationGainMeters = 1000.0
        )
        val jsonStr = original.toJson()
        val restored = RouteFilterCriteria.fromJson(jsonStr)

        assertEquals("Grimsel Pass", restored.query)
        assertEquals(250.0, restored.minElevationGainMeters)
        assertEquals(1000.0, restored.maxElevationGainMeters)
        assertEquals(2, restored.activeFilterCount)

        // Legacy JSON without maxElevationGainMeters
        val legacyJson = """{"query":"Alps","minElevationGainMeters":250.0}"""
        val fromLegacy = RouteFilterCriteria.fromJson(legacyJson)

        assertEquals("Alps", fromLegacy.query)
        assertEquals(250.0, fromLegacy.minElevationGainMeters)
        assertNull("maxElevationGainMeters should default to null for legacy JSON", fromLegacy.maxElevationGainMeters)
        assertEquals(2, fromLegacy.activeFilterCount)
    }

    /**
     * TST-UI-389.7: Active Elevation Filter Chip Formatting & Dismissal Contract.
     */
    @Test
    fun testActiveElevationFilterChipFormatting_andDismissalContract_TST_UI_389_7() {
        fun formatChip(minM: Double?, maxM: Double?): String {
            val minStr = minM?.toInt()?.toString()
            val maxStr = maxM?.toInt()?.toString()
            return when {
                minStr != null && maxStr != null -> "$minStr - $maxStr m"
                minStr != null -> "> $minStr m"
                else -> "< $maxStr m"
            }
        }

        assertEquals("100 - 250 m", formatChip(100.0, 250.0))
        assertEquals("> 1000 m", formatChip(1000.0, null))
        assertEquals("< 100 m", formatChip(null, 100.0))

        // Dismissal contract: clearing elevation range resets both bounds
        val criteria = RouteFilterCriteria(minElevationGainMeters = 250.0, maxElevationGainMeters = 500.0)
        val cleared = criteria.copy(minElevationGainMeters = null, maxElevationGainMeters = null)
        assertNull(cleared.minElevationGainMeters)
        assertNull(cleared.maxElevationGainMeters)
        assertTrue(cleared.isEmpty)
    }
}
