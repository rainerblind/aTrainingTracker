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

/**
 * Unit tests for [LiveSparklineBuffer] verifying temporal span tracking across warming buffers,
 * fixed horizon capping, and decimation invariance (TST-TRK-059.9a, REQ-TRK-059.9, ATT-6695).
 */
class LiveSparklineBufferSpanTest {

    @Test
    fun testEmptyAndSingleSampleSpan() {
        val buffer = LiveSparklineBuffer(horizonSeconds = 300)
        assertEquals(0, buffer.size)
        assertEquals(0, buffer.currentSpanSeconds)

        buffer.addSample(140f)
        assertEquals(1, buffer.size)
        assertEquals(0, buffer.currentSpanSeconds)
    }

    @Test
    fun testFixedHorizonWarmingAndCapping() {
        val buffer = LiveSparklineBuffer(horizonSeconds = 300)

        // 81 samples: span should be 80 seconds (representing t=0..80s)
        for (i in 0 until 81) {
            buffer.addSample(150f + i)
        }
        assertEquals(81, buffer.size)
        assertEquals(80, buffer.currentSpanSeconds)

        // Add up to 301 samples: span should be 300s
        for (i in 81 until 301) {
            buffer.addSample(160f)
        }
        assertEquals(300, buffer.size)
        assertEquals(300, buffer.currentSpanSeconds)

        // Exceed horizon: span remains capped at 300s
        for (i in 0 until 100) {
            buffer.addSample(170f)
        }
        assertEquals(300, buffer.size)
        assertEquals(300, buffer.currentSpanSeconds)
    }

    @Test
    fun testClearResetsSpanSeconds() {
        val buffer = LiveSparklineBuffer(horizonSeconds = 120)
        for (i in 0 until 60) {
            buffer.addSample(100f)
        }
        assertEquals(59, buffer.currentSpanSeconds)

        buffer.clear()
        assertEquals(0, buffer.size)
        assertEquals(0, buffer.currentSpanSeconds)
    }

    @Test
    fun testFullWorkoutDecimationPreservesCumulativeSpan() {
        // Full workout mode (horizonSeconds <= 0)
        val buffer = LiveSparklineBuffer(horizonSeconds = 0)
        assertEquals(0, buffer.currentSpanSeconds)

        // Feed 835 samples (835 ticks)
        for (i in 0 until 835) {
            buffer.addSample(120f + (i % 50))
        }

        // Buffer array size is bounded by decimation (<= 300)
        assertTrue("Buffer size must be <= 300 due to decimation", buffer.size <= LiveSparklineBuffer.MAX_FULL_WORKOUT_SAMPLES)
        // Authentic temporal span is preserved (835 - 1 = 834s)
        assertEquals(834, buffer.currentSpanSeconds)

        // Feed up to 7200 samples (2 hours)
        for (i in 835 until 7200) {
            buffer.addSample(130f)
        }
        assertTrue("Buffer size must remain <= 300", buffer.size <= LiveSparklineBuffer.MAX_FULL_WORKOUT_SAMPLES)
        assertEquals(7199, buffer.currentSpanSeconds)
    }
}
