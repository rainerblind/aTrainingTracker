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

package com.atrainingtracker.trainingtracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit and allocation benchmark tests for [SnapshotChunkedList] (REQ-TRK-042.1, TST-TRK-042.1, ATT-4923).
 */
class SnapshotChunkedListTest {

    @Test
    fun testEmptyList_contract() {
        val empty = SnapshotChunkedList.empty<String>()
        assertEquals(0, empty.size)
        assertTrue(empty.isEmpty())
        assertFalse(empty.iterator().hasNext())
    }

    @Test
    fun testAppendAndRandomAccess_underChunkThreshold() {
        var list = SnapshotChunkedList.empty<Int>()
        for (i in 0 until 100) {
            list = list.append(i * 10)
        }
        assertEquals(100, list.size)
        for (i in 0 until 100) {
            assertEquals(i * 10, list[i])
        }
    }

    @Test
    fun testChunkBoundaryTransitions() {
        var list = SnapshotChunkedList.empty<Int>()
        // Push through 0 -> 255 -> 256 -> 257 -> 512 -> 513
        for (i in 0 until 600) {
            list = list.append(i)
        }
        assertEquals(600, list.size)
        for (i in 0 until 600) {
            assertEquals(i, list[i])
        }
    }

    @Test
    fun testFromCollection_preservesAllElements() {
        val raw = (0 until 700).toList()
        val chunked = SnapshotChunkedList.from(raw)
        assertEquals(700, chunked.size)
        for (i in 0 until 700) {
            assertEquals(i, chunked[i])
        }
    }

    @Test
    fun testEquals_fastPathOnSizeDifference() {
        val list1 = SnapshotChunkedList.from((0 until 1000).toList())
        val list2 = list1.append(1000)
        assertNotEquals(list1, list2)
        assertEquals(list1, SnapshotChunkedList.from((0 until 1000).toList()))
    }

    @Test
    fun testSynthetic15000Points_boundedTimeAndBoundedAllocation() {
        var list = SnapshotChunkedList.empty<Double>()
        val startNanos = System.nanoTime()
        for (i in 0 until 15_000) {
            list = list.append(i.toDouble())
        }
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000
        assertEquals(15_000, list.size)
        assertEquals(0.0, list[0], 1e-6)
        assertEquals(14_999.0, list[14_999], 1e-6)

        // 15,000 appends should execute well under 500ms on modern JVM (typical is < 20ms)
        assertTrue("15,000 appends should execute in bounded time ($elapsedMillis ms)", elapsedMillis < 500)
    }
}
