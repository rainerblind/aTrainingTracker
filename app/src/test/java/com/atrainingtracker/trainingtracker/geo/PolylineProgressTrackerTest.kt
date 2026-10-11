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

package com.atrainingtracker.trainingtracker.geo

import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sign

/**
 * Unit tests for [PolylineProgressTracker] (REQ-GEO-001, TST-GEO-001).
 * Validates virtual gate generation, directional crossing via 2D vector cross-products,
 * orthogonal coordinate projection, 3D altitude/grade interpolation, and smooth finish blending.
 */
class PolylineProgressTrackerTest {

    private val samplePath = listOf(
        PathPoint(distance = 0.0, latLng = LatLng(47.0, 10.0), altitude = 500.0, slope = 5.0),
        PathPoint(distance = 250.0, latLng = LatLng(47.002, 10.0), altitude = 525.0, slope = 10.0),
        PathPoint(distance = 500.0, latLng = LatLng(47.004, 10.0), altitude = 550.0, slope = 10.0),
        PathPoint(distance = 750.0, latLng = LatLng(47.006, 10.0), altitude = 575.0, slope = 10.0),
        PathPoint(distance = 1000.0, latLng = LatLng(47.008, 10.0), altitude = 600.0, slope = 10.0)
    )

    @Test
    fun testCrossProduct() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(1.0, 0.0)
        val cLeft = LatLng(0.5, 1.0)
        val cRight = LatLng(0.5, -1.0)

        val cpLeft = PolylineProgressTracker.crossProduct(a, b, cLeft)
        val cpRight = PolylineProgressTracker.crossProduct(a, b, cRight)

        assertTrue(cpLeft > 0.0)
        assertTrue(cpRight < 0.0)
        assertEquals(-1.0, cpLeft.sign * cpRight.sign, 0.001)
    }

    @Test
    fun testCreateStartGateAndFinishGate() {
        val startGate = PolylineProgressTracker.createGate(samplePath, isStartGate = true, gateThresholdMeters = 50.0, sampleOffset = 1)
        assertNotNull(startGate)
        assertEquals(50.0, startGate!!.gateThresholdMeters, 0.001)
        assertTrue("Reference cross product should be non-zero", kotlin.math.abs(startGate.referenceCrossProduct) > 1e-9)

        val finishGate = PolylineProgressTracker.createGate(samplePath, isStartGate = false, gateThresholdMeters = 50.0, sampleOffset = 1)
        assertNotNull(finishGate)
        assertEquals(50.0, finishGate!!.gateThresholdMeters, 0.001)
        assertTrue("Reference cross product should be non-zero", kotlin.math.abs(finishGate.referenceCrossProduct) > 1e-9)
    }

    @Test
    fun testForwardStartGateCrossingDetection() {
        val startGate = PolylineProgressTracker.createGate(samplePath, isStartGate = true, gateThresholdMeters = 50.0, sampleOffset = 1)!!

        // Before start line (negative latitude side relative to start)
        val locBefore = LatLng(46.9999, 10.0)
        val cpBefore = PolylineProgressTracker.crossProduct(startGate.gateStart, startGate.gateEnd, locBefore)

        // After start line (positive latitude side)
        val locAfter = LatLng(47.0001, 10.0)
        val cpAfter = PolylineProgressTracker.crossProduct(startGate.gateStart, startGate.gateEnd, locAfter)

        // Ensure sign flipped and locAfter matches reference cross-product direction
        assertNotEquals(cpBefore.sign, cpAfter.sign)
        assertEquals(startGate.referenceCrossProduct.sign, cpAfter.sign, 0.001)

        val distToGate = PolylineProgressTracker.distanceToGate(locAfter, startGate)
        val crossed = PolylineProgressTracker.checkGateCrossing(
            gate = startGate,
            prevCrossProduct = cpBefore,
            currCrossProduct = cpAfter,
            distanceToGate = distToGate,
            isStartGate = true
        )
        assertTrue("Forward crossing of start gate must trigger true", crossed)
    }

    @Test
    fun testReverseStartGateCrossingRejection() {
        val startGate = PolylineProgressTracker.createGate(samplePath, isStartGate = true, gateThresholdMeters = 50.0, sampleOffset = 1)!!

        val locOnTrack = LatLng(47.0001, 10.0)
        val cpOnTrack = PolylineProgressTracker.crossProduct(startGate.gateStart, startGate.gateEnd, locOnTrack)

        val locOutside = LatLng(46.9999, 10.0)
        val cpOutside = PolylineProgressTracker.crossProduct(startGate.gateStart, startGate.gateEnd, locOutside)

        // Traveling backwards across the gate (out of the segment)
        val distToGate = PolylineProgressTracker.distanceToGate(locOutside, startGate)
        val crossed = PolylineProgressTracker.checkGateCrossing(
            gate = startGate,
            prevCrossProduct = cpOnTrack,
            currCrossProduct = cpOutside,
            distanceToGate = distToGate,
            isStartGate = true
        )
        assertFalse("Reverse crossing must be rejected", crossed)
    }

    @Test
    fun testParallelPassWithoutSignFlipRejection() {
        val startGate = PolylineProgressTracker.createGate(samplePath, isStartGate = true, gateThresholdMeters = 50.0, sampleOffset = 1)!!

        // Rider rides parallel to start line without crossing it
        val loc1 = LatLng(46.9999, 10.0001)
        val cp1 = PolylineProgressTracker.crossProduct(startGate.gateStart, startGate.gateEnd, loc1)

        val loc2 = LatLng(46.9999, 10.0002)
        val cp2 = PolylineProgressTracker.crossProduct(startGate.gateStart, startGate.gateEnd, loc2)

        val distToGate = PolylineProgressTracker.distanceToGate(loc2, startGate)
        val crossed = PolylineProgressTracker.checkGateCrossing(
            gate = startGate,
            prevCrossProduct = cp1,
            currCrossProduct = cp2,
            distanceToGate = distToGate,
            isStartGate = true
        )
        assertFalse("Parallel riding without sign flip must not trigger gate crossing", crossed)
    }

    @Test
    fun testGateCrossingOutsideDistanceThresholdRejection() {
        val startGate = PolylineProgressTracker.createGate(samplePath, isStartGate = true, gateThresholdMeters = 30.0, sampleOffset = 1)!!

        val cpBefore = -1.0
        val cpAfter = startGate.referenceCrossProduct.sign // Correct sign

        // Distance exceeds 30m
        val crossed = PolylineProgressTracker.checkGateCrossing(
            gate = startGate,
            prevCrossProduct = cpBefore,
            currCrossProduct = cpAfter,
            distanceToGate = 45.0,
            isStartGate = true
        )
        assertFalse("Crossing beyond gateThresholdMeters must be rejected", crossed)
    }

    @Test
    fun testFinishGateCrossingDetection() {
        val finishGate = PolylineProgressTracker.createGate(samplePath, isStartGate = false, gateThresholdMeters = 50.0, sampleOffset = 1)!!

        // Before finish line (inside track, same side as prevToEnd)
        val locBefore = LatLng(47.0079, 10.0)
        val cpBefore = PolylineProgressTracker.crossProduct(finishGate.gateStart, finishGate.gateEnd, locBefore)

        // After finish line (past summit/exit, opposite side of prevToEnd)
        val locAfter = LatLng(47.0081, 10.0)
        val cpAfter = PolylineProgressTracker.crossProduct(finishGate.gateStart, finishGate.gateEnd, locAfter)

        assertNotEquals(cpBefore.sign, cpAfter.sign)
        assertNotEquals(finishGate.referenceCrossProduct.sign, cpAfter.sign)

        val distToGate = PolylineProgressTracker.distanceToGate(locAfter, finishGate)
        val crossed = PolylineProgressTracker.checkGateCrossing(
            gate = finishGate,
            prevCrossProduct = cpBefore,
            currCrossProduct = cpAfter,
            distanceToGate = distToGate,
            isStartGate = false
        )
        assertTrue("Forward crossing of finish gate must trigger true", crossed)
    }

    @Test
    fun testOrthogonalSegmentProjectionExactMidpoint() {
        // Point halfway between samplePath[0] (47.0) and samplePath[1] (47.002)
        val midLocation = LatLng(47.001, 10.0)
        val proj = PolylineProgressTracker.projectLocation(samplePath, midLocation)

        assertNotNull(proj)
        assertEquals(0, proj!!.segmentIndex)
        assertEquals(0.5, proj.t, 0.05)
        assertEquals(125.0, proj.alongTrackDistance, 15.0)
        assertEquals(0.125f, proj.progressFraction, 0.02f)
        assertEquals(512.5, proj.interpolatedAltitude!!, 2.0)
        assertTrue("Cross-track error should be near 0", proj.crossTrackDistanceMeters < 1.0)
    }

    @Test
    fun testOrthogonalSegmentProjectionWithLateralOffset() {
        // Lateral offset 0.0003 deg longitude (~22m at lat 47)
        val offsetLocation = LatLng(47.001, 10.0003)
        val proj = PolylineProgressTracker.projectLocation(samplePath, offsetLocation)

        assertNotNull(proj)
        assertEquals(0, proj!!.segmentIndex)
        // Along-track distance should remain ~125m despite lateral offset
        assertEquals(125.0, proj.alongTrackDistance, 15.0)
        assertTrue("Cross-track error should capture offset (>15m)", proj.crossTrackDistanceMeters > 15.0)
    }

    @Test
    fun testSinglePointPolylineProjection() {
        val singleList = listOf(samplePath.first())
        val proj = PolylineProgressTracker.projectLocation(singleList, LatLng(47.001, 10.0))

        assertNotNull(proj)
        assertEquals(0, proj!!.segmentIndex)
        assertEquals(0.0, proj.alongTrackDistance, 0.001)
        assertTrue(proj.crossTrackDistanceMeters > 50.0)
    }

    @Test
    fun testBlendedRemainingDistanceConvergence() {
        val totalDistance = 1000.0

        // Far from finish (distanceToEnd = 600m > threshold 500m) -> returns raw remaining
        val farRem = PolylineProgressTracker.calculateBlendedRemainingDistance(
            totalDistance = totalDistance,
            completedDistance = 400.0,
            distanceToEnd = 600.0,
            finishThresholdMeters = 500.0
        )
        assertEquals(600.0, farRem, 0.001)

        // Close to finish (distanceToEnd = 100m, completed = 900m)
        val closeRem = PolylineProgressTracker.calculateBlendedRemainingDistance(
            totalDistance = totalDistance,
            completedDistance = 900.0,
            distanceToEnd = 100.0,
            finishThresholdMeters = 500.0
        )
        // lambda = 100 / 500 = 0.2 -> (100 * 0.2 + 0.8 * 100) = 100.0
        assertEquals(100.0, closeRem, 0.001)

        // Exactly at finish line (distanceToEnd = 0m, completed = 1000m)
        val zeroRem = PolylineProgressTracker.calculateBlendedRemainingDistance(
            totalDistance = totalDistance,
            completedDistance = 1000.0,
            distanceToEnd = 0.0,
            finishThresholdMeters = 500.0
        )
        assertEquals(0.0, zeroRem, 0.001)
    }

    @Test
    fun testHeadingAlignment() {
        assertTrue(PolylineProgressTracker.isHeadingAligned(0.0, 30.0, 45.0))
        assertTrue(PolylineProgressTracker.isHeadingAligned(350.0, 10.0, 45.0)) // Wrap-around 20 deg diff
        assertFalse(PolylineProgressTracker.isHeadingAligned(0.0, 90.0, 45.0)) // 90 deg diff
        assertFalse(PolylineProgressTracker.isHeadingAligned(0.0, 180.0, 45.0)) // Opposite heading
    }
}
