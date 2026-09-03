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

import android.content.Context
import com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice
import com.google.android.gms.maps.model.LatLng
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies geometric apex resolution and reset behavior for route clusters (REQ-SET-063, TST-SET-049, ATT-498).
 */
class WorkoutClusterApexTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val clusterEngine = WorkoutClusterEngine.getInstance(mockContext)

    @Test
    fun testFindApexFromPoints_CurvedRouteSelectsMaxDisplacementOnTrack() {
        // Topology modeled after "Run to work (Uni)" from ATT-498
        val rohrStart = LatLng(48.715, 9.105)
        val rohrerHoehe = LatLng(48.710, 9.080)
        val rosental = LatLng(48.725, 9.075)
        val lauchaecker = LatLng(48.735, 9.085)
        val nobelstrasse = LatLng(48.740, 9.095)
        val campusVaihingenEnd = LatLng(48.745, 9.100)
        val dachswaldOffTrack = LatLng(48.735, 9.115) // Erroneous centroid location

        val trackPoints = listOf(
            rohrStart,
            rohrerHoehe,
            rosental,
            lauchaecker,
            nobelstrasse,
            campusVaihingenEnd
        )

        val resolvedApex = clusterEngine.findApexFromPoints(rohrStart, trackPoints)

        // The apex MUST be on the track and specifically at Campus Vaihingen (furthest from Rohr)
        assertEquals(campusVaihingenEnd, resolvedApex)
        assertNotEquals(dachswaldOffTrack, resolvedApex)
        assertTrue("Resolved apex must be an existing point on the track", trackPoints.contains(resolvedApex))
    }

    @Test
    fun testFindApexFromPoints_OutAndBackRoute() {
        val start = LatLng(48.700, 9.100)
        val waypoint1 = LatLng(48.710, 9.105)
        val turnaroundApex = LatLng(48.730, 9.120) // Furthest point
        val waypoint2 = LatLng(48.715, 9.102)
        val end = LatLng(48.701, 9.101)

        val track = listOf(start, waypoint1, turnaroundApex, waypoint2, end)

        val resolvedApex = clusterEngine.findApexFromPoints(start, track)
        assertEquals(turnaroundApex, resolvedApex)
    }

    @Test
    fun testFindApexFromPoints_SinglePointOrEmptyFallback() {
        val start = LatLng(48.700, 9.100)
        assertEquals(start, clusterEngine.findApexFromPoints(start, emptyList()))

        val singlePoint = LatLng(48.750, 9.200)
        assertEquals(singlePoint, clusterEngine.findApexFromPoints(start, listOf(singlePoint)))
    }
}
