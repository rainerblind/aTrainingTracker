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
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaSegmentEffort
import com.atrainingtracker.trainingtracker.ui.aftermath.isHighlight
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
}
