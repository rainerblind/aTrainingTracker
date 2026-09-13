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

package com.atrainingtracker.trainingtracker.ui.components.strava

import com.atrainingtracker.trainingtracker.ui.aftermath.StravaActivityParser
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaBestEffort
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaSegmentEffort
import com.atrainingtracker.trainingtracker.ui.aftermath.effectiveDistanceMeters
import com.atrainingtracker.trainingtracker.ui.aftermath.isHighlight
import com.atrainingtracker.trainingtracker.ui.aftermath.isMileEffort
import org.junit.Assert.*
import org.junit.Test

/**
 * Automated unit test suite verifying Strava segment effort parsing,
 * highlight classification, and accordion collapse eligibility (REQ-UI-131, TST-UI-084).
 */
class StravaActivitySectionTest {

    @Test
    fun testParserExtractsStarredAndSegmentIdFromNestedSegment() {
        val json = """
            {
              "id": 12345,
              "segment_efforts": [
                {
                  "name": "Alpe d'Huez",
                  "elapsed_time": 3600,
                  "pr_rank": 1,
                  "kom_rank": 1,
                  "segment": {
                    "id": 998877,
                    "name": "Alpe d'Huez",
                    "starred": true
                  }
                }
              ]
            }
        """.trimIndent()

        val activity = StravaActivityParser.parse(json)
        assertNotNull(activity)
        assertEquals(1, activity!!.segmentEfforts.size)

        val effort = activity.segmentEfforts[0]
        assertEquals("Alpe d'Huez", effort.name)
        assertEquals(3600, effort.elapsedTimeSec)
        assertEquals(1, effort.prRank)
        assertEquals(1, effort.komRank)
        assertTrue(effort.isStarred)
        assertEquals(998877L, effort.segmentId)
        assertTrue(effort.isHighlight)
    }

    @Test
    fun testParserExtractsStarredAndSegmentIdFromFlatJson() {
        val json = """
            {
              "id": 12345,
              "segment_efforts": [
                {
                  "name": "Flat Sprint",
                  "elapsed_time": 120,
                  "segment_id": 554433,
                  "starred": true
                },
                {
                  "name": "Local Hill",
                  "elapsed_time": 300,
                  "segment_id": 554434,
                  "is_starred": true
                },
                {
                  "name": "Standard Road",
                  "elapsed_time": 450
                }
              ]
            }
        """.trimIndent()

        val activity = StravaActivityParser.parse(json)
        assertNotNull(activity)
        assertEquals(3, activity!!.segmentEfforts.size)

        val effort1 = activity.segmentEfforts[0]
        assertTrue(effort1.isStarred)
        assertEquals(554433L, effort1.segmentId)
        assertTrue(effort1.isHighlight)

        val effort2 = activity.segmentEfforts[1]
        assertTrue(effort2.isStarred)
        assertEquals(554434L, effort2.segmentId)
        assertTrue(effort2.isHighlight)

        val effort3 = activity.segmentEfforts[2]
        assertFalse(effort3.isStarred)
        assertNull(effort3.segmentId)
        assertFalse(effort3.isHighlight)
    }

    @Test
    fun testHighlightClassification() {
        val prEffort = StravaSegmentEffort("PR Segment", 200, prRank = 2, komRank = null, isStarred = false)
        val komEffort = StravaSegmentEffort("KOM Segment", 150, prRank = null, komRank = 1, isStarred = false)
        val starredEffort = StravaSegmentEffort("Starred Segment", 180, prRank = null, komRank = null, isStarred = true)
        val standardEffort = StravaSegmentEffort("Normal Segment", 300, prRank = null, komRank = null, isStarred = false)

        assertTrue("PR effort should be a highlight", prEffort.isHighlight)
        assertTrue("KOM effort should be a highlight", komEffort.isHighlight)
        assertTrue("Starred effort should be a highlight", starredEffort.isHighlight)
        assertFalse("Standard effort without PR/KOM/Star should NOT be a highlight", standardEffort.isHighlight)
    }

    @Test
    fun testSmallSegmentCountDoesNotTriggerAccordion() {
        val efforts = listOf(
            StravaSegmentEffort("Seg 1", 100, prRank = null, komRank = null),
            StravaSegmentEffort("Seg 2", 100, prRank = null, komRank = null),
            StravaSegmentEffort("Seg 3", 100, prRank = null, komRank = null)
        )
        val totalSegments = efforts.size
        val highlightCount = efforts.count { it.isHighlight }

        val isCollapsible = totalSegments > 4 && totalSegments > highlightCount
        assertFalse("Total <= 4 segments must not be collapsible", isCollapsible)
    }

    @Test
    fun testAllHighlightsDoesNotTriggerAccordion() {
        val efforts = listOf(
            StravaSegmentEffort("Seg 1", 100, prRank = 1, komRank = null),
            StravaSegmentEffort("Seg 2", 100, prRank = 2, komRank = null),
            StravaSegmentEffort("Seg 3", 100, prRank = null, komRank = 1),
            StravaSegmentEffort("Seg 4", 100, prRank = null, komRank = null, isStarred = true),
            StravaSegmentEffort("Seg 5", 100, prRank = 3, komRank = null)
        )
        val totalSegments = efforts.size
        val highlightCount = efforts.count { it.isHighlight }

        assertEquals(5, totalSegments)
        assertEquals(5, highlightCount)

        val isCollapsible = totalSegments > 4 && totalSegments > highlightCount
        assertFalse("Total segments == highlights must not be collapsible even if count > 4", isCollapsible)
    }

    @Test
    fun testCollapsibleEligibilityAndFiltering() {
        val efforts = listOf(
            StravaSegmentEffort("PR 1", 100, prRank = 1, komRank = null),
            StravaSegmentEffort("Starred 1", 100, prRank = null, komRank = null, isStarred = true),
            StravaSegmentEffort("KOM 1", 100, prRank = null, komRank = 1),
            StravaSegmentEffort("PR 2", 100, prRank = 3, komRank = null),
            StravaSegmentEffort("Normal 1", 100, prRank = null, komRank = null),
            StravaSegmentEffort("Normal 2", 100, prRank = null, komRank = null),
            StravaSegmentEffort("Normal 3", 100, prRank = null, komRank = null),
            StravaSegmentEffort("Normal 4", 100, prRank = null, komRank = null),
            StravaSegmentEffort("Normal 5", 100, prRank = null, komRank = null),
            StravaSegmentEffort("Normal 6", 100, prRank = null, komRank = null)
        )
        val totalSegments = efforts.size
        val highlights = efforts.filter { it.isHighlight }
        val isCollapsible = totalSegments > 4 && totalSegments > highlights.size

        assertTrue("10 segments with 4 highlights should be collapsible", isCollapsible)
        assertEquals(10, totalSegments)
        assertEquals(4, highlights.size)

        // Default collapsed state: displays only highlights
        val displayedCollapsed = if (!isCollapsible) efforts else highlights
        assertEquals(4, displayedCollapsed.size)
        assertEquals(listOf("PR 1", "Starred 1", "KOM 1", "PR 2"), displayedCollapsed.map { it.name })

        // Expanded state: displays all 10
        val displayedExpanded = efforts
        assertEquals(10, displayedExpanded.size)
    }

    @Test
    fun testZeroHighlightsShowsEmptyInCollapsedView() {
        val efforts = (1..7).map {
            StravaSegmentEffort("Normal $it", 100, prRank = null, komRank = null)
        }
        val totalSegments = efforts.size
        val highlights = efforts.filter { it.isHighlight }
        val isCollapsible = totalSegments > 4 && totalSegments > highlights.size

        assertTrue("7 segments with 0 highlights should be collapsible", isCollapsible)
        assertEquals(0, highlights.size)

        // Collapsed state strictly displays only highlights (0 segments)
        val displayedCollapsed = if (!isCollapsible) efforts else highlights
        assertEquals(0, displayedCollapsed.size)
        assertTrue(displayedCollapsed.isEmpty())
    }

    @Test
    fun testParserExtractsPrAndKomFromAchievementsArray() {
        val json = """
            {
              "id": 99881,
              "segment_efforts": [
                {
                  "name": "Achievement PR",
                  "elapsed_time": 250,
                  "achievements": [
                    { "type": "pr", "rank": 1 }
                  ]
                },
                {
                  "name": "Achievement KOM",
                  "elapsed_time": 180,
                  "achievements": [
                    { "type": "overall", "rank": 1 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val activity = StravaActivityParser.parse(json)
        assertNotNull(activity)
        assertEquals(2, activity!!.segmentEfforts.size)

        val prEffort = activity.segmentEfforts[0]
        assertEquals(1, prEffort.prRank)
        assertTrue(prEffort.isHighlight)

        val komEffort = activity.segmentEfforts[1]
        assertEquals(1, komEffort.komRank)
        assertTrue(komEffort.isHighlight)
    }

    @Test
    fun testParserExtractsStarredDateFromSegment() {
        val json = """
            {
              "id": 99882,
              "segment_efforts": [
                {
                  "name": "Dated Starred Segment",
                  "elapsed_time": 400,
                  "segment": {
                    "id": 112233,
                    "name": "Dated Starred Segment",
                    "starred_date": "2024-05-01T12:00:00Z"
                  }
                }
              ]
            }
        """.trimIndent()

        val activity = StravaActivityParser.parse(json)
        assertNotNull(activity)
        assertEquals(1, activity!!.segmentEfforts.size)

        val effort = activity.segmentEfforts[0]
        assertTrue(effort.isStarred)
        assertEquals(112233L, effort.segmentId)
        assertTrue(effort.isHighlight)
    }

    // =========================================================================================
    // Best Efforts (Bestzeiten) Tests - ATT-883 / REQ-UI-144 / TST-UI-097
    // =========================================================================================

    @Test
    fun testBestEffortIsHighlight() {
        val pr1 = StravaBestEffort(name = "400m", elapsedTimeSec = 60, prRank = 1)
        val pr2 = StravaBestEffort(name = "1k", elapsedTimeSec = 180, prRank = 2)
        val pr3 = StravaBestEffort(name = "5k", elapsedTimeSec = 1200, prRank = 3)
        val pr4 = StravaBestEffort(name = "10k", elapsedTimeSec = 2600, prRank = 4)
        val noPr = StravaBestEffort(name = "Half-Marathon", elapsedTimeSec = 5400, prRank = null)

        assertTrue("PR rank 1 must be highlight", pr1.isHighlight)
        assertTrue("PR rank 2 must be highlight", pr2.isHighlight)
        assertTrue("PR rank 3 must be highlight", pr3.isHighlight)
        assertFalse("PR rank > 3 must NOT be highlight", pr4.isHighlight)
        assertFalse("null PR rank must NOT be highlight", noPr.isHighlight)
    }

    @Test
    fun testBestEffortIsMileEffort() {
        val halfMile = StravaBestEffort("1/2 mile", 120)
        val halfMileHyphen = StravaBestEffort("1/2-mile", 120)
        val oneMile = StravaBestEffort("1 mile", 300)
        val twoMiles = StravaBestEffort("2 miles", 620)
        val tenMiles = StravaBestEffort("10 miles", 3600)
        val tenMileHyphen = StravaBestEffort("10-mile", 3600)

        assertTrue("1/2 mile is mile effort", halfMile.isMileEffort)
        assertTrue("1/2-mile is mile effort", halfMileHyphen.isMileEffort)
        assertTrue("1 mile is mile effort", oneMile.isMileEffort)
        assertTrue("2 miles is mile effort", twoMiles.isMileEffort)
        assertTrue("10 miles is mile effort", tenMiles.isMileEffort)
        assertTrue("10-mile is mile effort", tenMileHyphen.isMileEffort)

        val fourHundredMeters = StravaBestEffort("400m", 60)
        val oneK = StravaBestEffort("1k", 180)
        val fiveK = StravaBestEffort("5k", 1200)
        val tenK = StravaBestEffort("10k", 2600)
        val halfMarathon = StravaBestEffort("Half-Marathon", 5400)
        val marathon = StravaBestEffort("Marathon", 12000)

        assertFalse("400m is not mile effort", fourHundredMeters.isMileEffort)
        assertFalse("1k is not mile effort", oneK.isMileEffort)
        assertFalse("5k is not mile effort", fiveK.isMileEffort)
        assertFalse("10k is not mile effort", tenK.isMileEffort)
        assertFalse("Half-Marathon is not mile effort", halfMarathon.isMileEffort)
        assertFalse("Marathon is not mile effort", marathon.isMileEffort)
    }

    @Test
    fun testBestEffortEffectiveDistanceMeters() {
        val explicitDistance = StravaBestEffort("Custom", 100, distanceMeters = 1500.0)
        assertEquals(1500.0, explicitDistance.effectiveDistanceMeters, 0.001)

        assertEquals(400.0, StravaBestEffort("400m", 60).effectiveDistanceMeters, 0.001)
        assertEquals(804.672, StravaBestEffort("1/2 mile", 120).effectiveDistanceMeters, 0.001)
        assertEquals(1000.0, StravaBestEffort("1k", 180).effectiveDistanceMeters, 0.001)
        assertEquals(1609.344, StravaBestEffort("1 mile", 300).effectiveDistanceMeters, 0.001)
        assertEquals(3218.688, StravaBestEffort("2 miles", 620).effectiveDistanceMeters, 0.001)
        assertEquals(5000.0, StravaBestEffort("5k", 1200).effectiveDistanceMeters, 0.001)
        assertEquals(10000.0, StravaBestEffort("10k", 2600).effectiveDistanceMeters, 0.001)
        assertEquals(16093.44, StravaBestEffort("10 miles", 3600).effectiveDistanceMeters, 0.001)
        assertEquals(21097.5, StravaBestEffort("Half-Marathon", 5400).effectiveDistanceMeters, 0.001)
        assertEquals(42195.0, StravaBestEffort("Marathon", 12000).effectiveDistanceMeters, 0.001)
    }

    @Test
    fun testParserExtractsBestEffortsWithDistanceAndAchievements() {
        val json = """
            {
              "id": 99883,
              "best_efforts": [
                {
                  "name": "1k",
                  "elapsed_time": 210,
                  "distance": 1000.0,
                  "pr_rank": 1
                },
                {
                  "name": "1 mile",
                  "elapsed_time": 350,
                  "distance": 1609.34,
                  "achievements": [
                    { "type": "pr", "rank": 2 }
                  ]
                },
                {
                  "name": "5k",
                  "elapsed_time": 1250,
                  "distance": 5000.0
                }
              ]
            }
        """.trimIndent()

        val activity = StravaActivityParser.parse(json)
        assertNotNull(activity)
        assertEquals(3, activity!!.bestEfforts.size)

        val effort1 = activity.bestEfforts[0]
        assertEquals("1k", effort1.name)
        assertEquals(210, effort1.elapsedTimeSec)
        assertEquals(1000.0, effort1.distanceMeters, 0.001)
        assertEquals(1, effort1.prRank)
        assertTrue(effort1.isHighlight)

        val effort2 = activity.bestEfforts[1]
        assertEquals("1 mile", effort2.name)
        assertEquals(350, effort2.elapsedTimeSec)
        assertEquals(1609.34, effort2.distanceMeters, 0.01)
        assertEquals(2, effort2.prRank)
        assertTrue(effort2.isHighlight)
        assertTrue(effort2.isMileEffort)

        val effort3 = activity.bestEfforts[2]
        assertEquals("5k", effort3.name)
        assertEquals(1250, effort3.elapsedTimeSec)
        assertEquals(5000.0, effort3.distanceMeters, 0.001)
        assertNull(effort3.prRank)
        assertFalse(effort3.isHighlight)
    }

    private fun computeDisplayedBestEfforts(
        bestEfforts: List<StravaBestEffort>,
        isMetric: Boolean,
        isExpanded: Boolean
    ): Pair<List<StravaBestEffort>, Boolean> {
        val totalBestEfforts = bestEfforts.size
        val eligibleEfforts = if (isMetric) {
            val nonMiles = bestEfforts.filter { !it.isMileEffort }
            if (nonMiles.isNotEmpty()) nonMiles else bestEfforts
        } else {
            bestEfforts
        }
        val longestEffort = eligibleEfforts.maxByOrNull { it.effectiveDistanceMeters } ?: eligibleEfforts.lastOrNull()
        val reducedBestEfforts = eligibleEfforts.filter { effort ->
            effort.isHighlight || effort == longestEffort
        }
        val isCollapsible = totalBestEfforts > reducedBestEfforts.size
        val displayed = if (!isCollapsible || isExpanded) bestEfforts else reducedBestEfforts
        return Pair(displayed, isCollapsible)
    }

    @Test
    fun testBestEffortsCollapsedViewMetricFiltersMiles() {
        val efforts = listOf(
            StravaBestEffort("400m", 60, prRank = null),
            StravaBestEffort("1/2 mile", 130, prRank = 1), // Mile with PR
            StravaBestEffort("1k", 180, prRank = null),
            StravaBestEffort("1 mile", 320, prRank = 2), // Mile with PR
            StravaBestEffort("2 miles", 680, prRank = null), // Mile no PR
            StravaBestEffort("5k", 1200, prRank = 3), // Metric with PR
            StravaBestEffort("10k", 2500, prRank = null) // Longest non-mile effort
        )

        // In metric mode (collapsed): miles must NOT be shown, even if they have PRs!
        // Reduced list should contain: "5k" (PR 3) and "10k" (longest eligible metric effort).
        val (collapsedMetric, isCollapsibleMetric) = computeDisplayedBestEfforts(efforts, isMetric = true, isExpanded = false)
        assertTrue("Efforts should be collapsible", isCollapsibleMetric)
        assertEquals(2, collapsedMetric.size)
        assertEquals(listOf("5k", "10k"), collapsedMetric.map { it.name })
        assertFalse("No mile efforts in collapsed metric view", collapsedMetric.any { it.isMileEffort })

        // In imperial mode (isMetric = false, collapsed): miles ARE eligible.
        // Highlights: "1/2 mile" (PR 1), "1 mile" (PR 2), "5k" (PR 3).
        // Longest effort: "10k" (10000m > 3218m).
        val (collapsedImperial, isCollapsibleImperial) = computeDisplayedBestEfforts(efforts, isMetric = false, isExpanded = false)
        assertTrue(isCollapsibleImperial)
        assertEquals(listOf("1/2 mile", "1 mile", "5k", "10k"), collapsedImperial.map { it.name })
    }

    @Test
    fun testBestEffortsLongestEffortAlwaysIncludedWhenReducedZeroPrs() {
        // Workout with 0 PRs
        val efforts = listOf(
            StravaBestEffort("400m", 70, prRank = null),
            StravaBestEffort("1/2 mile", 150, prRank = null),
            StravaBestEffort("1k", 200, prRank = null),
            StravaBestEffort("1 mile", 350, prRank = null),
            StravaBestEffort("5k", 1300, prRank = null),
            StravaBestEffort("10k", 2700, prRank = null)
        )

        val (collapsedMetric, isCollapsible) = computeDisplayedBestEfforts(efforts, isMetric = true, isExpanded = false)
        assertTrue("Should be collapsible since 6 > 1", isCollapsible)
        // Exactly 1 effort shown: the longest distance ("10k")
        assertEquals(1, collapsedMetric.size)
        assertEquals("10k", collapsedMetric[0].name)
    }

    @Test
    fun testBestEffortsLongestEffortIsPrDoesNotDuplicate() {
        val efforts = listOf(
            StravaBestEffort("400m", 60, prRank = null),
            StravaBestEffort("1k", 180, prRank = 1),
            StravaBestEffort("5k", 1100, prRank = 1) // Longest is also PR 1
        )

        val (collapsedMetric, isCollapsible) = computeDisplayedBestEfforts(efforts, isMetric = true, isExpanded = false)
        assertTrue(isCollapsible)
        // Shows 1k and 5k without duplicate 5k
        assertEquals(2, collapsedMetric.size)
        assertEquals(listOf("1k", "5k"), collapsedMetric.map { it.name })
    }

    @Test
    fun testBestEffortsExpansionShowsAllEffortsInOriginalOrder() {
        val efforts = listOf(
            StravaBestEffort("400m", 60, prRank = null),
            StravaBestEffort("1/2 mile", 130, prRank = 1),
            StravaBestEffort("1k", 180, prRank = null),
            StravaBestEffort("1 mile", 320, prRank = null),
            StravaBestEffort("5k", 1200, prRank = null)
        )

        val (displayedExpanded, isCollapsible) = computeDisplayedBestEfforts(efforts, isMetric = true, isExpanded = true)
        assertTrue(isCollapsible)
        // When expanded, all 5 efforts (including miles) are displayed
        assertEquals(5, displayedExpanded.size)
        assertEquals(listOf("400m", "1/2 mile", "1k", "1 mile", "5k"), displayedExpanded.map { it.name })
    }

    @Test
    fun testBestEffortsNotCollapsibleWhenAllEligibleAreHighlights() {
        // If all efforts are highlights and non-miles, collapsed == all -> no accordion
        val efforts = listOf(
            StravaBestEffort("1k", 180, prRank = 1),
            StravaBestEffort("5k", 1100, prRank = 2)
        )

        val (displayed, isCollapsible) = computeDisplayedBestEfforts(efforts, isMetric = true, isExpanded = false)
        assertFalse("Should NOT be collapsible when all efforts are shown in reduced view", isCollapsible)
        assertEquals(2, displayed.size)
    }

    @Test
    fun testBestEffortsAllMilesFallbackInMetricMode() {
        // If an activity only contains mile efforts (e.g. from an imperial runner), metric mode falls back to all miles
        val efforts = listOf(
            StravaBestEffort("1/2 mile", 130, prRank = null),
            StravaBestEffort("1 mile", 320, prRank = 1)
        )

        val (displayed, isCollapsible) = computeDisplayedBestEfforts(efforts, isMetric = true, isExpanded = false)
        // Longest is 1 mile, highlight is 1 mile -> reduced has 1 mile
        assertTrue(isCollapsible)
        assertEquals(1, displayed.size)
        assertEquals("1 mile", displayed[0].name)
    }
}
