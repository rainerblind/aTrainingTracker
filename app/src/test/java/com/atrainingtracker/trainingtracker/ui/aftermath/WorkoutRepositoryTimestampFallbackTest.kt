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

package com.atrainingtracker.trainingtracker.ui.aftermath

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit verification for [WorkoutRepository.parseTimestampOffset] (REQ-UI-202, REQ-UI-203, ATT-1811).
 */
class WorkoutRepositoryTimestampFallbackTest {

    @Test
    fun testParseTimestampOffset_nullOrEmptyString_returnsNullAndPreservesAnchor() {
        val (offsetNull, anchor1) = WorkoutRepository.parseTimestampOffset(null, 1000L)
        assertNull(offsetNull)
        assertEquals(1000L, anchor1)

        val (offsetEmpty, anchor2) = WorkoutRepository.parseTimestampOffset("", null)
        assertNull(offsetEmpty)
        assertNull(anchor2)

        val (offsetBlank, anchor3) = WorkoutRepository.parseTimestampOffset("   ", 500L)
        assertNull(offsetBlank)
        assertEquals(500L, anchor3)
    }

    @Test
    fun testParseTimestampOffset_initialSample_establishesAnchorAtZeroOffset() {
        // First sample in workout: "2014-03-25 09:59:51"
        val (offset, anchor) = WorkoutRepository.parseTimestampOffset("2014-03-25 09:59:51", null)
        assertNotNull(offset)
        assertNotNull(anchor)
        assertEquals(0L, offset)
        assertTrue(anchor!! > 0L)
    }

    @Test
    fun testParseTimestampOffset_subsequentSamples_computesRelativeElapsedSeconds() {
        val t0Str = "2014-03-25 09:59:51"
        val (offset0, anchor0) = WorkoutRepository.parseTimestampOffset(t0Str, null)
        assertEquals(0L, offset0)
        assertNotNull(anchor0)

        // 1 second later: "2014-03-25 09:59:52"
        val (offset1, anchor1) = WorkoutRepository.parseTimestampOffset("2014-03-25 09:59:52", anchor0)
        assertEquals(1L, offset1)
        assertEquals(anchor0, anchor1)

        // 10 seconds later: "2014-03-25 10:00:01"
        val (offset10, anchor10) = WorkoutRepository.parseTimestampOffset("2014-03-25 10:00:01", anchor0)
        assertEquals(10L, offset10)
        assertEquals(anchor0, anchor10)

        // 53 minutes later (~3187s): "2014-03-25 10:52:58"
        val (offsetEnd, anchorEnd) = WorkoutRepository.parseTimestampOffset("2014-03-25 10:52:58", anchor0)
        assertEquals(3187L, offsetEnd)
        assertEquals(anchor0, anchorEnd)
    }

    @Test
    fun testParseTimestampOffset_isoFormatWithT_parsesSuccessfully() {
        val (offset, anchor) = WorkoutRepository.parseTimestampOffset("2014-03-25T09:59:51", null)
        assertNotNull(offset)
        assertEquals(0L, offset)
        assertNotNull(anchor)
    }

    @Test
    fun testParseTimestampOffset_invalidFormat_returnsNullGracefully() {
        val (offset, anchor) = WorkoutRepository.parseTimestampOffset("not-a-date", 1234L)
        assertNull(offset)
        assertEquals(1234L, anchor)
    }
}
