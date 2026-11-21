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

package com.atrainingtracker.trainingtracker.migration.parsers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Direct unit tests for [WorkoutTimestampParser] concurrency, DST-gap invariance,
 * ISO-8601 offset/fractional variants, duration computation, and fallback handling.
 */
// Linked test specs: TST-MIG-055, TST-MIG-055.1, TST-MIG-055.2
class WorkoutTimestampParserTest {

    @Test
    fun formatIsoToUtcDbString_handlesOffsetsFractionalBareIsoAndDstGaps() {
        val originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        try {
            // DST spring-forward gap in Europe/Berlin (02:00..02:59 skipped) -> zero +1h shift
            assertEquals("2026-03-29 02:30:00", WorkoutTimestampParser.formatIsoToUtcDbString("2026-03-29T02:30:00Z"))
            assertEquals("2026-10-25 02:30:00", WorkoutTimestampParser.formatIsoToUtcDbString("2026-10-25T02:30:00Z"))
            // Offsets & fractional seconds normalize to UTC wall clock
            assertEquals("2026-10-15 14:00:00", WorkoutTimestampParser.formatIsoToUtcDbString("2026-10-15T14:00:00.999Z"))
            assertEquals("2026-10-15 14:00:00", WorkoutTimestampParser.formatIsoToUtcDbString("2026-10-15T16:00:00+02:00"))
            assertEquals("2026-10-15 14:00:00", WorkoutTimestampParser.formatIsoToUtcDbString("2026-10-15T09:00:00-05:00"))
            assertEquals("2026-10-15 14:00:00", WorkoutTimestampParser.formatIsoToUtcDbString("2026-10-15T14:00:00"))
            // Malformed or short strings return raw input safely
            assertEquals("short", WorkoutTimestampParser.formatIsoToUtcDbString("short"))
            assertEquals("", WorkoutTimestampParser.formatIsoToUtcDbString(""))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun computeDurationSeconds_handlesValidBoundsAndMalformedInputs() {
        assertEquals(300.0, WorkoutTimestampParser.computeDurationSeconds("2026-03-29 01:55:00", "2026-03-29 02:00:00")!!, 0.001)
        assertEquals(0.0, WorkoutTimestampParser.computeDurationSeconds("2026-03-29 02:10:00", "2026-03-29 02:00:00")!!, 0.001)
        assertNull(WorkoutTimestampParser.computeDurationSeconds("bad", "2026-03-29 02:00:00"))
    }

    @Test
    fun formatIsoToUtcDbString_concurrentContentionRemainsDeterministic() {
        val threadCount = 8
        val pool = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val go = CountDownLatch(1)
        try {
            val futures = (0 until threadCount).map { idx ->
                val raw = "2026-07-1${idx}T12:34:56.123Z"
                val expected = "2026-07-1${idx} 12:34:56"
                pool.submit(Callable {
                    ready.countDown()
                    assertTrue(go.await(5, TimeUnit.SECONDS))
                    repeat(500) {
                        assertEquals(expected, WorkoutTimestampParser.formatIsoToUtcDbString(raw))
                    }
                })
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
