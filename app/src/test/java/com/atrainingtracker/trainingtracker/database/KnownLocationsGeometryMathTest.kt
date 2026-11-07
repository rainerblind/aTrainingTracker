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

package com.atrainingtracker.trainingtracker.database

import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

/**
 * Unit tests verifying [KnownLocationsGeometryMath] hybrid geofence calculations (TST-SET-095.1).
 *
 * Traceability: REQ-SET-095.4, TST-SET-095.1.
 */
class KnownLocationsGeometryMathTest {

    @Test
    fun calculateDistanceMeters_identicalCoordinates_returnsZero() {
        val p = LatLng(48.137154, 11.576124)
        val dist = KnownLocationsGeometryMath.calculateDistanceMeters(p, p)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun calculateDistanceMeters_oneDegreeLatitude_approximately111km() {
        val p1 = LatLng(48.0, 11.0)
        val p2 = LatLng(49.0, 11.0)
        val dist = KnownLocationsGeometryMath.calculateDistanceMeters(p1, p2)
        assertEquals(111195.0, dist, 500.0)
    }

    @Test
    fun interpolateGeodesic_boundaryFractions_returnsExpectedEndpoints() {
        val from = LatLng(48.0, 11.0)
        val to = LatLng(48.1, 11.1)

        val atZero = KnownLocationsGeometryMath.interpolateGeodesic(from, to, 0.0)
        assertEquals(from.latitude, atZero.latitude, 1e-6)
        assertEquals(from.longitude, atZero.longitude, 1e-6)

        val atOne = KnownLocationsGeometryMath.interpolateGeodesic(from, to, 1.0)
        assertEquals(to.latitude, atOne.latitude, 1e-6)
        assertEquals(to.longitude, atOne.longitude, 1e-6)
    }

    @Test
    fun calculateMergedGeofence_ac1_minimumBoundingCircleMerge() {
        // AC 1: Two unlocked, non-home locations S and T with r_S = 200m, r_T = 200m at distance d
        val target = KnownLocationItem(
            id = 1L,
            name = "Target Location",
            altitude = 500.0,
            radius = 200,
            latLng = LatLng(48.0, 11.0),
            hitCount = 5,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED,
            isHome = false
        )
        // Position source ~200m north of target
        val source = KnownLocationItem(
            id = 2L,
            name = "Source Location",
            altitude = 505.0,
            radius = 200,
            latLng = LatLng(48.0018, 11.0),
            hitCount = 3,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED,
            isHome = false
        )

        val result = KnownLocationsGeometryMath.calculateMergedGeofence(source, target)
        assertFalse("MBC merge must not be pinned", result.isPinned)

        val d = result.distanceBetweenMeters
        assertTrue("Distance between centers should be ~200m", d in 190.0..210.0)
        val expectedRadius = ceil((d + 200.0 + 200.0) / 2.0).toInt()
        assertEquals("Merged radius must equal ceil((d + rT + rS)/2)", expectedRadius, result.radius)

        // Center must be closer to both target and source than the total distance
        val distToTarget = KnownLocationsGeometryMath.calculateDistanceMeters(result.center, target.latLng)
        val distToSource = KnownLocationsGeometryMath.calculateDistanceMeters(result.center, source.latLng)
        assertEquals("Center must be at midpoint when radii are equal", distToTarget, distToSource, 1.0)
    }

    @Test
    fun calculateMergedGeofence_ac2_pinnedTargetForHomeBase() {
        // AC 2: Target location T has isHome = true with r_T = 200m and source S (r_S = 200m) at distance ~150m
        val target = KnownLocationItem(
            id = 1L,
            name = "Heim-Basis",
            altitude = 520.0,
            radius = 200,
            latLng = LatLng(48.137, 11.576),
            hitCount = 10,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED,
            isHome = true
        )
        val source = KnownLocationItem(
            id = 2L,
            name = "Auffahrt",
            altitude = 519.0,
            radius = 200,
            latLng = LatLng(48.138, 11.576),
            hitCount = 2,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED,
            isHome = false
        )

        val result = KnownLocationsGeometryMath.calculateMergedGeofence(source, target)
        assertTrue("Target with isHome must remain pinned", result.isPinned)
        assertEquals("Target coordinates must remain exactly pinned", target.latLng.latitude, result.center.latitude, 1e-6)
        assertEquals("Target coordinates must remain exactly pinned", target.latLng.longitude, result.center.longitude, 1e-6)

        val d = result.distanceBetweenMeters
        val expectedRadius = maxOf(200, ceil(d + 200.0).toInt())
        assertEquals("Radius must expand to enclose source without moving target pin", expectedRadius, result.radius)
    }

    @Test
    fun calculateMergedGeofence_pinnedTargetForLockedAltitude() {
        val target = KnownLocationItem(
            id = 1L,
            name = "Calibrated Hill",
            altitude = 650.0,
            radius = 150,
            latLng = LatLng(48.200, 11.300),
            hitCount = 8,
            isLocked = true,
            source = ElevationSource.MANUAL_USER,
            isHome = false
        )
        val source = KnownLocationItem(
            id = 2L,
            name = "Trail Entrance",
            altitude = 648.0,
            radius = 100,
            latLng = LatLng(48.201, 11.301),
            hitCount = 4,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED,
            isHome = false
        )

        val result = KnownLocationsGeometryMath.calculateMergedGeofence(source, target)
        assertTrue("Target with isLocked must remain pinned", result.isPinned)
        assertEquals(target.latLng, result.center)
    }

    @Test
    fun calculateMergedGeofence_targetEnclosesSource_adoptsTargetGeofence() {
        val target = KnownLocationItem(
            id = 1L,
            name = "Large Campus",
            altitude = 500.0,
            radius = 600,
            latLng = LatLng(48.0, 11.0),
            hitCount = 10,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED,
            isHome = false
        )
        val source = KnownLocationItem(
            id = 2L,
            name = "Inner Courtyard",
            altitude = 500.0,
            radius = 100,
            latLng = LatLng(48.001, 11.0), // ~111m away: 111m + 100m = 211m <= 600m
            hitCount = 1,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED,
            isHome = false
        )

        val result = KnownLocationsGeometryMath.calculateMergedGeofence(source, target)
        assertFalse(result.isPinned)
        assertEquals("Target already encloses source, center must be target center", target.latLng, result.center)
        assertEquals("Radius must be target radius", 600, result.radius)
    }

    @Test
    fun calculateMergedGeofence_sourceEnclosesTarget_adoptsSourceGeofence() {
        val target = KnownLocationItem(
            id = 1L,
            name = "Front Door",
            altitude = 500.0,
            radius = 100,
            latLng = LatLng(48.001, 11.0),
            hitCount = 1,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED,
            isHome = false
        )
        val source = KnownLocationItem(
            id = 2L,
            name = "Whole Estate",
            altitude = 500.0,
            radius = 600,
            latLng = LatLng(48.0, 11.0), // ~111m away: 111m + 100m = 211m <= 600m
            hitCount = 10,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED,
            isHome = false
        )

        val result = KnownLocationsGeometryMath.calculateMergedGeofence(source, target)
        assertFalse(result.isPinned)
        assertEquals("Source encloses target, center must be source center", source.latLng, result.center)
        assertEquals("Radius must be source radius", 600, result.radius)
    }
}
