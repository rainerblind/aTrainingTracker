/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.map

import com.atrainingtracker.banalservice.BSportType
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit test verifying [MappablePath] coordinate projection memoization (REQ-MAP-007, TST-UNT-010).
 */
class MappablePathMemoizationTest {

    @Test
    fun mappablePathImplementations_preserveReferentialLatLngsIdentityAcrossAccesses_TST_UNT_010() {
        // TST-UNT-010
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.10, 11.50), altitude = 500.0),
            PathPoint(distance = 500.0, latLng = LatLng(48.11, 11.51), altitude = 520.0)
        )

        val mapTrack = MapTrack(id = 1L, type = TrackType.BEST, bSportType = BSportType.BIKE, path = points)
        val mapSegment = MapSegment(stravaId = 2L, name = "Climb", bSportType = BSportType.BIKE, path = points)
        val mapRoute = MapRoute(id = 3L, name = "Route", isSelected = false, bSportType = BSportType.RUN, path = points)
        val precomputed = points.map { it.latLng }
        val mapCluster = MapCluster(
            id = 4L,
            name = "Cluster",
            bSportType = BSportType.RUN,
            hitCount = 3,
            refDistance = 500.0,
            path = emptyList<PathPoint>(),
            latLngs = precomputed
        )

        // Repeated reads must return the exact cached List<LatLng> reference without re-projecting
        assertSame(mapTrack.latLngs, mapTrack.latLngs)
        assertSame(mapSegment.latLngs, mapSegment.latLngs)
        assertSame(mapRoute.latLngs, mapRoute.latLngs)
        assertSame(precomputed, mapCluster.latLngs)
        assertEquals(listOf(LatLng(48.10, 11.50), LatLng(48.11, 11.51)), mapTrack.latLngs)
    }
}
