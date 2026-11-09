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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for non-degenerate elliptical apex calculation on point-to-point (A -> B) routes
 * and loop backward compatibility (REQ-SET-097.1, REQ-SET-097.4, TST-SET-097.1, ATT-5899).
 */
class WorkoutClusterPointToPointApexTest {

    private lateinit var mockContext: Context
    private lateinit var clusterEngine: WorkoutClusterEngine

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        WorkoutClusterEngine.resetForTesting(null)
        clusterEngine = WorkoutClusterEngine.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        WorkoutClusterEngine.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testPointToPointRouteWithLateralArc_selectsEllipticalApexAwayFromEnd() {
        // Sindelfingen start (48.7083, 9.0028) to Schönaich end (48.6570, 9.0640)
        // Direct distance is ~7.3 km
        val start = LatLng(48.7083, 9.0028)
        val end = LatLng(48.6570, 9.0640)

        // Track with a prominent eastern detour through the forest (lateral deflection > 1.5 km)
        val forestApex = LatLng(48.6950, 9.0450)
        val points = listOf(
            start,
            LatLng(48.7050, 9.0150),
            LatLng(48.7000, 9.0300),
            forestApex, // Point of maximum elliptical sum d(start, P) + d(P, end)
            LatLng(48.6750, 9.0550),
            LatLng(48.6650, 9.0600),
            end
        )

        val resolvedApex = clusterEngine.findApexFromPoints(start, points, end)

        // Must select forestApex and NOT degenerate onto end
        assertEquals(forestApex.latitude, resolvedApex.latitude, 1e-6)
        assertEquals(forestApex.longitude, resolvedApex.longitude, 1e-6)

        val distApexToEnd = WorkoutClusterEngine.distanceBetween(resolvedApex, end).toDouble()
        assertTrue("Apex must be separated from end by > 1.5 km", distApexToEnd > 1500.0)
    }

    @Test
    fun testCollinearStraightPointToPointRoute_selectsMidpoint() {
        // Straight line from West (48.7000, 9.0000) to East (48.7000, 9.1000)
        val start = LatLng(48.7000, 9.0000)
        val end = LatLng(48.7000, 9.1000)

        val midPoint = LatLng(48.7000, 9.0500)
        val points = listOf(
            start,
            LatLng(48.7000, 9.0250),
            midPoint,
            LatLng(48.7000, 9.0750),
            end
        )

        val resolvedApex = clusterEngine.findApexFromPoints(start, points, end)

        // For straight collinear lines, the 50% midpoint should be selected
        assertEquals(midPoint.latitude, resolvedApex.latitude, 1e-6)
        assertEquals(midPoint.longitude, resolvedApex.longitude, 1e-6)
        assertNotEquals("Apex must not collapse onto destination end", end, resolvedApex)
    }

    @Test
    fun testClosedLoopCourse_preservesLegacyStartDisplacement100Percent() {
        // Closed loop starting and ending at the same parking lot (d(start, end) < 150m)
        val start = LatLng(48.7000, 9.0000)
        val end = LatLng(48.7005, 9.0005) // ~60m away from start

        val loopTurnaround = LatLng(48.7300, 9.0500) // ~5 km away
        val points = listOf(
            start,
            LatLng(48.7100, 9.0150),
            LatLng(48.7200, 9.0300),
            loopTurnaround, // Farthest point from start
            LatLng(48.7150, 9.0100),
            end
        )

        // 1. With end provided
        val apexWithEnd = clusterEngine.findApexFromPoints(start, points, end)

        // 2. Legacy overload without end
        val apexLegacy = clusterEngine.findApexFromPoints(start, points)

        assertEquals("Closed-loop with end must match legacy apex", loopTurnaround, apexWithEnd)
        assertEquals("Legacy overload must identify turnaround", loopTurnaround, apexLegacy)
        assertEquals("Both overloads must produce identical coordinates", apexLegacy, apexWithEnd)
    }

    @Test
    fun testEdgeCases_emptyListAndSinglePoint() {
        val start = LatLng(48.7000, 9.0000)
        val end = LatLng(48.7000, 9.1000)
        val singlePoint = LatLng(48.7050, 9.0500)

        assertEquals(start, clusterEngine.findApexFromPoints(start, emptyList(), end))
        assertEquals(singlePoint, clusterEngine.findApexFromPoints(start, listOf(singlePoint), end))
    }
}
