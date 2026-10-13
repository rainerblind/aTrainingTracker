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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.*
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying that active and approaching live segments are sorted strictly
 * by raw Double meter distances rather than formatted display strings (REQ-LIV-005, TST-LIV-001).
 */
class LiveSegmentSortingTest {

    private val liveSegmentComparator = compareByDescending<LiveSegment> {
        when (it.liveData.segmentStatus) {
            LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> 4
            LiveSegmentStatus.ON_SEGMENT -> 3
            LiveSegmentStatus.APPROACHING -> 2
            LiveSegmentStatus.FINISHED -> 1
            else -> 0
        }
    }.thenBy {
        when (it.liveData.segmentStatus) {
            LiveSegmentStatus.ON_SEGMENT,
            LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> it.liveData.remainingDistance_raw
            LiveSegmentStatus.APPROACHING -> it.liveData.distanceToStart_raw
            else -> it.liveData.segmentOffset_raw
        }
    }

    private fun createLiveSegment(
        id: Long,
        name: String,
        status: LiveSegmentStatus,
        remainingDistance: String = "--",
        remainingDistance_raw: Double = Double.MAX_VALUE,
        distanceToStart: String = "--",
        distanceToStart_raw: Double = Double.MAX_VALUE,
        segmentOffset: String = "--",
        segmentOffset_raw: Double = 0.0
    ): LiveSegment {
        val summary = SegmentSummary(
            stravaId = id,
            name = name,
            bSportType = BSportType.BIKE,
            climbCategory_raw = 0,
            climbCategory = "NC",
            prTime_raw = 120,
            prTime = "2:00",
            city = "TestCity",
            distance = "2.0 km",
            distance_raw = 2000.0,
            averageGrade_raw = 4.0,
            averageGrade = "4%",
            maxGrade = "6%",
            elevationGain_raw = 40.0,
            elevationGain = "40 m",
            elevationMin = "400 m",
            elevationMax = "440 m",
            map_polyline = ""
        )
        val path = emptyList<com.atrainingtracker.trainingtracker.ui.map.PathPoint>()
        val staticData = SegmentWithPath(summary = summary, path = path)
        val liveData = LiveSegmentData(
            segmentStatus = status,
            distanceToStart = distanceToStart,
            distanceToStart_raw = distanceToStart_raw,
            remainingDistance = remainingDistance,
            remainingDistance_raw = remainingDistance_raw,
            segmentOffset = segmentOffset,
            segmentOffset_raw = segmentOffset_raw
        )
        val math = LiveSegmentMath(
            start = LatLng(0.0, 0.0),
            start_a = LatLng(0.0, 0.0),
            start_b = LatLng(0.0, 0.0),
            segmentStartBearing = 0.0,
            start_cross_n = 0.0,
            end = LatLng(0.0, 0.0),
            end_a = LatLng(0.0, 0.0),
            end_b = LatLng(0.0, 0.0),
            end_cross_p = 0.0
        )
        return LiveSegment(staticData = staticData, liveData = liveData, math = math)
    }

    /**
     * TST-LIV-001.1: Verify on-segment candidate prioritization across the 1,000m unit boundary.
     * Demonstrates that Segment A (995 m) correctly sorts before Segment B (2,400 m),
     * whereas legacy string comparison ("2.40 km" < "995 m") inverted the order.
     */
    @Test
    fun testOnSegment_sortingAcrossOneKmBoundary_prioritizesShorterSegmentNumerically() {
        val segmentA = createLiveSegment(
            id = 101L,
            name = "Dachtel 2 Deckenpfronn Climb",
            status = LiveSegmentStatus.ON_SEGMENT,
            remainingDistance = "995 m",
            remainingDistance_raw = 995.0
        )
        val segmentB = createLiveSegment(
            id = 102L,
            name = "Dachtel2Deckenpronn complete",
            status = LiveSegmentStatus.ON_SEGMENT,
            remainingDistance = "2.40 km",
            remainingDistance_raw = 2400.0
        )

        // Verify string comparison flaw: ASCII '2' (50) < '9' (57)
        assertTrue("String comparison would incorrectly rank '2.40 km' ahead of '995 m'",
            segmentB.liveData.remainingDistance < segmentA.liveData.remainingDistance
        )

        // Verify numeric sorting evaluates strictly 995.0 < 2400.0
        val sorted = listOf(segmentB, segmentA).sortedWith(liveSegmentComparator)
        assertEquals(segmentA.staticData.summary.stravaId, sorted[0].staticData.summary.stravaId)
        assertEquals(segmentB.staticData.summary.stravaId, sorted[1].staticData.summary.stravaId)
    }

    /**
     * TST-LIV-001.2: Verify approaching candidate prioritization across the 1,000m unit boundary.
     * Demonstrates that Segment A (950 m to start) correctly sorts before Segment B (1,200 m to start),
     * whereas legacy string comparison ("1.20 km" < "950 m") inverted the order.
     */
    @Test
    fun testApproaching_sortingAcrossOneKmBoundary_prioritizesCloserStartNumerically() {
        val segmentA = createLiveSegment(
            id = 201L,
            name = "Approaching Short",
            status = LiveSegmentStatus.APPROACHING,
            distanceToStart = "950 m",
            distanceToStart_raw = 950.0
        )
        val segmentB = createLiveSegment(
            id = 202L,
            name = "Approaching Long",
            status = LiveSegmentStatus.APPROACHING,
            distanceToStart = "1.20 km",
            distanceToStart_raw = 1200.0
        )

        // Verify string comparison flaw
        assertTrue("String comparison would incorrectly rank '1.20 km' ahead of '950 m'",
            segmentB.liveData.distanceToStart < segmentA.liveData.distanceToStart
        )

        // Verify numeric sorting
        val sorted = listOf(segmentB, segmentA).sortedWith(liveSegmentComparator)
        assertEquals(segmentA.staticData.summary.stravaId, sorted[0].staticData.summary.stravaId)
        assertEquals(segmentB.staticData.summary.stravaId, sorted[1].staticData.summary.stravaId)
    }

    /**
     * TST-LIV-001.3: Verify that primary status weight outranks secondary raw distance tie-breaker.
     * ON_SEGMENT_CLOSE_TO_FINISH (weight 4) must prioritize ahead of ON_SEGMENT (weight 3),
     * even if ON_SEGMENT has a shorter remaining distance.
     */
    @Test
    fun testStatusWeight_preservesPriorityOverRawDistance() {
        val segmentClose = createLiveSegment(
            id = 301L,
            name = "Close to finish",
            status = LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH,
            remainingDistance = "450 m",
            remainingDistance_raw = 450.0
        )
        val segmentOn = createLiveSegment(
            id = 302L,
            name = "On segment",
            status = LiveSegmentStatus.ON_SEGMENT,
            remainingDistance = "100 m",
            remainingDistance_raw = 100.0
        )

        val sorted = listOf(segmentOn, segmentClose).sortedWith(liveSegmentComparator)
        assertEquals(segmentClose.staticData.summary.stravaId, sorted[0].staticData.summary.stravaId)
        assertEquals(segmentOn.staticData.summary.stravaId, sorted[1].staticData.summary.stravaId)
    }

    /**
     * TST-LIV-001.4: Verify imperial units formatting independence.
     * Raw meter distances guarantee consistent sorting regardless of imperial formatting
     * (e.g. "0.62 mi" vs "900 yd").
     */
    @Test
    fun testImperialUnits_sortsNumericallyWithoutStringArtifacts() {
        val segmentA = createLiveSegment(
            id = 401L,
            name = "Segment 800m",
            status = LiveSegmentStatus.ON_SEGMENT,
            remainingDistance = "875 yd",
            remainingDistance_raw = 800.0
        )
        val segmentB = createLiveSegment(
            id = 402L,
            name = "Segment 1200m",
            status = LiveSegmentStatus.ON_SEGMENT,
            remainingDistance = "0.75 mi",
            remainingDistance_raw = 1200.0
        )

        // In string comparison, "0.75 mi" < "875 yd"
        assertTrue(segmentB.liveData.remainingDistance < segmentA.liveData.remainingDistance)

        // In numeric comparison, 800.0 < 1200.0
        val sorted = listOf(segmentB, segmentA).sortedWith(liveSegmentComparator)
        assertEquals(segmentA.staticData.summary.stravaId, sorted[0].staticData.summary.stravaId)
        assertEquals(segmentB.staticData.summary.stravaId, sorted[1].staticData.summary.stravaId)
    }

    /**
     * Verify finished status fallback sorts by segmentOffset_raw.
     */
    @Test
    fun testFinishedStatus_sortsBySegmentOffsetRaw() {
        val segmentA = createLiveSegment(
            id = 501L,
            name = "Finished Closer",
            status = LiveSegmentStatus.FINISHED,
            segmentOffset = "45 m",
            segmentOffset_raw = 45.0
        )
        val segmentB = createLiveSegment(
            id = 502L,
            name = "Finished Farther",
            status = LiveSegmentStatus.FINISHED,
            segmentOffset = "150 m",
            segmentOffset_raw = 150.0
        )

        val sorted = listOf(segmentB, segmentA).sortedWith(liveSegmentComparator)
        assertEquals(segmentA.staticData.summary.stravaId, sorted[0].staticData.summary.stravaId)
        assertEquals(segmentB.staticData.summary.stravaId, sorted[1].staticData.summary.stravaId)
    }
}
