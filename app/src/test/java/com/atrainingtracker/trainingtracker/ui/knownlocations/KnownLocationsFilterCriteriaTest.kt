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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying [KnownLocationsFilterCriteria] active filter count,
 * empty state semantics, and multi-dimensional predicate matching (REQ-UI-466, TST-UI-466.1).
 */
class KnownLocationsFilterCriteriaTest {

    private fun createLocation(
        id: Long = 1L,
        name: String = "Olympiazentrum",
        lat: Double = 48.175,
        lng: Double = 11.554,
        hitCount: Int = 10
    ): KnownLocationItem {
        return KnownLocationItem(
            id = id,
            name = name,
            altitude = 515.0,
            radius = 200,
            latLng = LatLng(lat, lng),
            hitCount = hitCount,
            isLocked = false,
            source = ElevationSource.INTERNET_DEM
        )
    }

    @Test
    fun testDefaultCriteria_hasMinStartsFive() {
        val criteria = KnownLocationsFilterCriteria()
        assertEquals(5, criteria.minStarts)
        assertEquals("", criteria.query)
        assertEquals(1, criteria.activeFilterCount)
        assertTrue(criteria.isNotEmpty)
        assertFalse(criteria.isEmpty)
    }

    @Test
    fun testEmptyCriteria_whenCleared() {
        val criteria = KnownLocationsFilterCriteria(query = "", minStarts = null)
        assertEquals(0, criteria.activeFilterCount)
        assertTrue(criteria.isEmpty)
        assertFalse(criteria.isNotEmpty)
    }

    @Test
    fun testActiveFilterCount_bothDimensions() {
        val criteria = KnownLocationsFilterCriteria(query = "Trail", minStarts = 10)
        assertEquals(2, criteria.activeFilterCount)
        assertTrue(criteria.isNotEmpty)
    }

    @Test
    fun testMatches_minStartsThreshold() {
        val criteria = KnownLocationsFilterCriteria(query = "", minStarts = 5)
        val item = createLocation()

        // Below threshold
        assertFalse(criteria.matches(item, effectiveStarts = 4))
        assertFalse(criteria.matches(item, effectiveStarts = 0))

        // Exact threshold
        assertTrue(criteria.matches(item, effectiveStarts = 5))

        // Above threshold
        assertTrue(criteria.matches(item, effectiveStarts = 6))
        assertTrue(criteria.matches(item, effectiveStarts = 100))
    }

    @Test
    fun testMatches_queryFiltering() {
        val criteria = KnownLocationsFilterCriteria(query = "Olymp", minStarts = null)
        val item1 = createLocation(name = "Olympiazentrum")
        val item2 = createLocation(name = "Englischer Garten")

        assertTrue(criteria.matches(item1, effectiveStarts = 1))
        assertFalse(criteria.matches(item2, effectiveStarts = 1))
    }

    @Test
    fun testMatches_coordinateSubstring() {
        val criteria = KnownLocationsFilterCriteria(query = "48.175", minStarts = null)
        val item = createLocation(lat = 48.175, lng = 11.554)

        assertTrue(criteria.matches(item, effectiveStarts = 0))
    }

    @Test
    fun testMatches_combinedDimensions() {
        val criteria = KnownLocationsFilterCriteria(query = "Olymp", minStarts = 5)
        val item = createLocation(name = "Olympiazentrum")

        // Matches name but fails starts
        assertFalse(criteria.matches(item, effectiveStarts = 3))

        // Matches both
        assertTrue(criteria.matches(item, effectiveStarts = 5))

        // Fails name even if starts pass
        val nonMatchingItem = createLocation(name = "Englischer Garten")
        assertFalse(criteria.matches(nonMatchingItem, effectiveStarts = 10))
    }
}
