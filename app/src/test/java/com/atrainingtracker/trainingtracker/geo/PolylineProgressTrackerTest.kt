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
import com.google.maps.android.SphericalUtil
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

    @Test
    fun testEquirectangularProjectionOnDiagonalSegmentAt50N() {
        // TST-GEO-002.1: At 50N, verify diagonal segment orthogonal projection parameter t
        // is immune to longitudinal scaling distortion under lateral offset.
        val cos50 = kotlin.math.cos(Math.toRadians(50.0))
        val p1 = PathPoint(distance = 0.0, latLng = LatLng(50.0, 10.0), altitude = 100.0, slope = 2.0)
        // 0.01 deg lat ~ 1111m, 0.01 / cos50 deg lng ~ 1111m in metric space
        val p2 = PathPoint(distance = 1572.0, latLng = LatLng(50.01, 10.0 + 0.01 / cos50), altitude = 120.0, slope = 2.0)
        val path = listOf(p1, p2)

        // Midpoint at t = 0.50 with a perpendicular lateral offset of ~55 meters
        // In metric space, segment is (dX, dY) = (0.01, 0.01). Perpendicular offset is (-0.0005, 0.0005 / cos50)
        val testLoc = LatLng(50.005 - 0.0005, 10.0 + 0.005 / cos50 + 0.0005 / cos50)
        val proj = PolylineProgressTracker.projectLocation(path, testLoc)

        assertNotNull(proj)
        assertEquals(0, proj!!.segmentIndex)
        // Parameter t must evaluate to 0.50 within tight tolerance
        assertEquals(0.50, proj.t, 0.01)
        // Along track distance should be ~786m (50% of 1572m)
        assertEquals(786.0, proj.alongTrackDistance, 15.0)
        // Cross-track error should capture the lateral offset (~50-70m)
        assertTrue("Cross-track error should be between 40m and 80m", proj.crossTrackDistanceMeters in 40.0..80.0)
    }

    @Test
    fun testGateOrthogonalityAt50N() {
        // TST-GEO-002.2: Verify virtual gate endpoints form true physical 90-degree angle
        // to diagonal track vector at 50 degrees North.
        val cos50 = kotlin.math.cos(Math.toRadians(50.0))
        val p1 = PathPoint(distance = 0.0, latLng = LatLng(50.0, 10.0), altitude = 100.0, slope = 0.0)
        val p2 = PathPoint(distance = 500.0, latLng = LatLng(50.005, 10.0 + 0.005 / cos50), altitude = 110.0, slope = 0.0)
        val path = listOf(p1, p2)

        val gate = PolylineProgressTracker.createGate(path, isStartGate = true, sampleOffset = 1)!!
        val trackHeading = SphericalUtil.computeHeading(p1.latLng, p2.latLng)
        val gateHeading = SphericalUtil.computeHeading(gate.gateStart, gate.gateEnd)

        // Compute angle between track and gate line
        var angleDiff = kotlin.math.abs(trackHeading - gateHeading)
        if (angleDiff > 180.0) angleDiff = 360.0 - angleDiff
        // Physical angle must be 90 degrees (+- 1.5 degrees due to spherical curvature over large delta)
        assertEquals(90.0, angleDiff, 1.5)
    }

    @Test
    fun testOutAndBackWindowedSegmentContinuityUnderLateralJitter() {
        // TST-GEO-002.3: On an out-and-back course with overlapping outbound and return legs,
        // windowed forward search maintains outbound segment continuity under lateral GPS jitter.
        val path = mutableListOf<PathPoint>()
        // Outbound leg (segments 0..9): moving North from 50.000 to 50.010 along longitude 10.000000
        for (i in 0..10) {
            path.add(PathPoint(distance = i * 100.0, latLng = LatLng(50.0 + i * 0.001, 10.0), altitude = 100.0, slope = 0.0))
        }
        // Turnaround / return leg (segments 10..20): moving South from 50.010 to 50.000 along parallel road 8m East
        // At 50N, 8m ~ 0.000112 deg longitude
        val returnLng = 10.000112
        for (i in 1..10) {
            path.add(PathPoint(distance = 1000.0 + i * 100.0, latLng = LatLng(50.010 - i * 0.001, returnLng), altitude = 100.0, slope = 0.0))
        }

        // Athlete is at latitude 50.0025 on the outbound leg (between segment 2 and 3).
        // GPS fix has 5m lateral jitter towards the return leg:
        // Outbound line is at 10.0, return line is at 10.000112 (~8m away).
        // Jitter puts fix at 10.000065 (~4.6m from outbound, ~3.4m from return leg).
        val jitteredFix = LatLng(50.0025, 10.000065)

        // Without windowing (global search), fix would jump to the return leg (segment 17) because 3.4m < 4.6m
        val unseededProj = PolylineProgressTracker.projectLocation(path, jitteredFix, lastSegmentIndex = null)
        assertNotNull(unseededProj)
        assertEquals("Global search snaps to closer return leg", 17, unseededProj!!.segmentIndex)
        assertTrue("Distance jumped to return leg", unseededProj.alongTrackDistance > 1500.0)

        // WITH windowed forward continuity (lastSegmentIndex = 2):
        // Tracker evaluates segments in [0, 12], keeping focus on outbound leg
        val windowedProj = PolylineProgressTracker.projectLocation(path, jitteredFix, lastSegmentIndex = 2)
        assertNotNull(windowedProj)
        assertEquals("Windowed search preserves outbound leg", 2, windowedProj!!.segmentIndex)
        assertEquals("Along track distance remains ~250m", 250.0, windowedProj.alongTrackDistance, 20.0)
    }

    @Test
    fun testCorridorExceedanceFallsBackToGlobalSearch() {
        // TST-GEO-002.4: When lateral distance exceeds maxCorridorMeters (50m),
        // tracker falls back to global search for off-route recovery and route rejoin.
        val path = mutableListOf<PathPoint>()
        for (i in 0..10) {
            path.add(PathPoint(distance = i * 100.0, latLng = LatLng(50.0 + i * 0.001, 10.0), altitude = 100.0, slope = 0.0))
        }
        for (i in 1..10) {
            // Return leg is 100m East (at 50N, 100m ~ 0.00140 deg longitude)
            path.add(PathPoint(distance = 1000.0 + i * 100.0, latLng = LatLng(50.010 - i * 0.001, 10.00140), altitude = 100.0, slope = 0.0))
        }

        // Athlete was on segment 1, but took a detour and rejoined directly on return leg at (50.0025, 10.00140)
        // Distance to outbound segment 1 is ~100m (> 50m corridor threshold)
        val rejoinFix = LatLng(50.0025, 10.00140)
        val fallbackProj = PolylineProgressTracker.projectLocation(
            path = path,
            location = rejoinFix,
            lastSegmentIndex = 1,
            maxCorridorMeters = 50.0
        )
        assertNotNull(fallbackProj)
        // Global fallback must engage and recover cleanly on the return leg (segment 17)
        assertEquals("Corridor exceedance triggers global search recovering on return leg", 17, fallbackProj!!.segmentIndex)
        assertTrue(fallbackProj.crossTrackDistanceMeters < 5.0)
    }

    @Test
    fun testStatelessCallerBackwardCompatibility() {
        // TST-GEO-002.5: Calling projectLocation without lastSegmentIndex performs global search
        val midLocation = LatLng(47.005, 10.0)
        val projDefault = PolylineProgressTracker.projectLocation(samplePath, midLocation)
        val projExplicitNull = PolylineProgressTracker.projectLocation(samplePath, midLocation, lastSegmentIndex = null)

        assertNotNull(projDefault)
        assertNotNull(projExplicitNull)
        assertEquals(projDefault!!.segmentIndex, projExplicitNull!!.segmentIndex)
        assertEquals(projDefault.alongTrackDistance, projExplicitNull.alongTrackDistance, 0.001)
        assertEquals(projDefault.crossTrackDistanceMeters, projExplicitNull.crossTrackDistanceMeters, 0.001)
        assertEquals(2, projDefault.segmentIndex)
    }
}
