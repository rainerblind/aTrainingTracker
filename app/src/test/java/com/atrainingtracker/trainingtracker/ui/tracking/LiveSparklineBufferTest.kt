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

package com.atrainingtracker.trainingtracker.ui.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [LiveSparklineBuffer] verifying circular ring buffer eviction,
 * Full Workout pairwise decimation, and bounded memory invariants (TST-TRK-059.3, REQ-TRK-059.4).
 */
class LiveSparklineBufferTest {

    @Test
    fun testFixedCapacitySlidingWindowEviction_1mHorizon() {
        val buffer = LiveSparklineBuffer(horizonSeconds = 60)
        assertEquals(0, buffer.size)

        // Add 60 samples: 1.0f .. 60.0f
        for (i in 1..60) {
            buffer.addSample(i.toFloat())
        }
        assertEquals(60, buffer.size)
        val samples = buffer.getSamples()
        assertEquals(1.0f, samples.first(), 0.001f)
        assertEquals(60.0f, samples.last(), 0.001f)

        // Add 5 more samples: oldest 5 should be evicted
        for (i in 61..65) {
            buffer.addSample(i.toFloat())
        }
        assertEquals(60, buffer.size)
        val updated = buffer.getSamples()
        assertEquals(6.0f, updated.first(), 0.001f)
        assertEquals(65.0f, updated.last(), 0.001f)
    }

    @Test
    fun testFixedCapacitySlidingWindowEviction_5mHorizon() {
        val buffer = LiveSparklineBuffer(horizonSeconds = 300)
        for (i in 1..350) {
            buffer.addSample(i.toFloat())
        }
        assertEquals(300, buffer.size)
        val samples = buffer.getSamples()
        assertEquals(51.0f, samples.first(), 0.001f)
        assertEquals(350.0f, samples.last(), 0.001f)
    }

    @Test
    fun testFullWorkoutDecimation_preservesBoundedMemory() {
        // Horizon 0 signals Full Workout
        val buffer = LiveSparklineBuffer(horizonSeconds = 0)

        // Add exactly 300 samples
        for (i in 1..300) {
            buffer.addSample(i.toFloat())
        }
        assertEquals(300, buffer.size)

        // Add 301st sample: triggers pairwise decimation
        buffer.addSample(301.0f)
        // 300 samples decimated pairwise -> 150 + 1 (the 301st odd item) = 151
        assertEquals(151, buffer.size)

        val decimated = buffer.getSamples()
        // Pair 1: (1 + 2) / 2 = 1.5
        assertEquals(1.5f, decimated[0], 0.001f)
        // Pair 2: (3 + 4) / 2 = 3.5
        assertEquals(3.5f, decimated[1], 0.001f)
        // Last element: 301.0f
        assertEquals(301.0f, decimated.last(), 0.001f)

        // Add 10,000 more samples (simulating a multi-hour workout at 1Hz)
        for (i in 302..10000) {
            buffer.addSample(i.toFloat())
            assertTrue("Buffer size must never exceed 300 samples", buffer.size <= 300)
        }
        assertTrue("Final sample count must be bounded <= 300", buffer.size <= 300)
        assertEquals(10000.0f, buffer.getSamples().last(), 0.001f)
    }

    @Test
    fun testClear_resetsBuffer() {
        val buffer = LiveSparklineBuffer(horizonSeconds = 120)
        for (i in 1..50) {
            buffer.addSample(i.toFloat())
        }
        assertEquals(50, buffer.size)
        buffer.clear()
        assertEquals(0, buffer.size)
        assertTrue(buffer.getSamples().isEmpty())
    }

    @Test
    fun testConcurrentAdditions_threadSafe() {
        val buffer = LiveSparklineBuffer(horizonSeconds = 300)
        val executor = Executors.newFixedThreadPool(4)
        for (i in 1..1000) {
            executor.submit {
                buffer.addSample(i.toFloat())
            }
        }
        executor.shutdown()
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS))
        assertEquals(300, buffer.size)
    }
}
