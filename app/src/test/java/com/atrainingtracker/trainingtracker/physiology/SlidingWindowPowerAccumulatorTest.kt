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

package com.atrainingtracker.trainingtracker.physiology

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite for [SlidingWindowPowerAccumulator] validating O(1) buffer sliding,
 * growing-window warm-up phase (Rule 40 / REQ-TRK-023), session peak tracking,
 * and session reset parity (Rule 51) conforming to TST-TRK-058.1.
 */
class SlidingWindowPowerAccumulatorTest {

    @Test
    fun emptyAccumulatorReturnsNullAverageAndPeak() {
        val accumulator = SlidingWindowPowerAccumulator(5)
        assertEquals(0, accumulator.count)
        assertFalse(accumulator.isFull())
        assertNull(accumulator.getAverage())
        assertNull(accumulator.getPeak())
    }

    @Test
    fun growingWindowWarmupReturnsImmediateValidAverages_Rule40() {
        val accumulator = SlidingWindowPowerAccumulator(5)

        // Sample 1 (t = 1s)
        accumulator.addSample(100.0)
        assertEquals(1, accumulator.count)
        assertFalse(accumulator.isFull())
        assertEquals(100.0, accumulator.getAverage()!!, 0.001)
        assertEquals(100.0, accumulator.getPeak()!!, 0.001)

        // Sample 2 (t = 2s)
        accumulator.addSample(200.0)
        assertEquals(2, accumulator.count)
        assertEquals(150.0, accumulator.getAverage()!!, 0.001)
        assertEquals(150.0, accumulator.getPeak()!!, 0.001)

        // Sample 3 (t = 3s)
        accumulator.addSample(300.0)
        assertEquals(3, accumulator.count)
        assertEquals(200.0, accumulator.getAverage()!!, 0.001)
        assertEquals(200.0, accumulator.getPeak()!!, 0.001)

        // Sample 4 (t = 4s)
        accumulator.addSample(400.0)
        assertEquals(4, accumulator.count)
        assertEquals(250.0, accumulator.getAverage()!!, 0.001)
        assertEquals(250.0, accumulator.getPeak()!!, 0.001)

        // Sample 5 (t = 5s, full buffer)
        accumulator.addSample(500.0)
        assertEquals(5, accumulator.count)
        assertTrue(accumulator.isFull())
        assertEquals(300.0, accumulator.getAverage()!!, 0.001)
        assertEquals(300.0, accumulator.getPeak()!!, 0.001)
    }

    @Test
    fun slidingWindowEvictionAndMonotonicPeakTracking() {
        val accumulator = SlidingWindowPowerAccumulator(5)
        // Add 5 samples: 200, 200, 200, 200, 200 -> avg = 200
        repeat(5) { accumulator.addSample(200.0) }
        assertEquals(200.0, accumulator.getAverage()!!, 0.001)
        assertEquals(200.0, accumulator.getPeak()!!, 0.001)

        // Sprint surge: replace one 200 with 500 -> sum = 1000 - 200 + 500 = 1300 -> avg = 260
        accumulator.addSample(500.0)
        assertEquals(5, accumulator.count)
        assertEquals(260.0, accumulator.getAverage()!!, 0.001)
        assertEquals(260.0, accumulator.getPeak()!!, 0.001)

        // Recovery coast: add 0W -> sum = 1300 - 200 + 0 = 1100 -> avg = 220
        accumulator.addSample(0.0)
        assertEquals(220.0, accumulator.getAverage()!!, 0.001)
        // Peak remains at 260 (monotonic session peak)
        assertEquals(260.0, accumulator.getPeak()!!, 0.001)
    }

    @Test
    fun resetClearsAllState_Rule51() {
        val accumulator = SlidingWindowPowerAccumulator(60)
        repeat(30) { accumulator.addSample(300.0) }
        assertEquals(30, accumulator.count)
        assertEquals(300.0, accumulator.getAverage()!!, 0.001)
        assertEquals(300.0, accumulator.getPeak()!!, 0.001)

        accumulator.reset()
        assertEquals(0, accumulator.count)
        assertFalse(accumulator.isFull())
        assertNull(accumulator.getAverage())
        assertNull(accumulator.getPeak())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidWindowSizeThrowsException() {
        SlidingWindowPowerAccumulator(0)
    }
}
