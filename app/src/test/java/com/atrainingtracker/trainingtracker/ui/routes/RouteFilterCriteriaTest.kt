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
            minElevationGainMeters = 1000.0
        )

        val json = original.toJson()
        val restored = RouteFilterCriteria.fromJson(json)

        assertEquals(original.query, restored.query)
        assertEquals(original.source, restored.source)
        assertEquals(original.isSelected, restored.isSelected)
        assertEquals(original.minDistanceMeters, restored.minDistanceMeters)
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
}
