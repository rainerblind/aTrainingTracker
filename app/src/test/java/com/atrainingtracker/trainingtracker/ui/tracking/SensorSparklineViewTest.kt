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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [SensorSparklineView] helper functions verifying relative time offset
 * formatting and container height resolution (TST-TRK-059.9b, REQ-TRK-059.9, ATT-6695).
 */
class SensorSparklineViewTest {

    @Test
    fun testFormatSparklineTimeOffset_subHour() {
        assertEquals("-0:00", formatSparklineTimeOffset(0))
        assertEquals("-0:00", formatSparklineTimeOffset(-5))
        assertEquals("-0:40", formatSparklineTimeOffset(40))
        assertEquals("-1:20", formatSparklineTimeOffset(80))
        assertEquals("-2:30", formatSparklineTimeOffset(150))
        assertEquals("-5:00", formatSparklineTimeOffset(300))
        assertEquals("-6:57", formatSparklineTimeOffset(417))
        assertEquals("-13:54", formatSparklineTimeOffset(834))
        assertEquals("-59:59", formatSparklineTimeOffset(3599))
    }

    @Test
    fun testFormatSparklineTimeOffset_multiHour() {
        assertEquals("-1:00:00", formatSparklineTimeOffset(3600))
        assertEquals("-1:00:01", formatSparklineTimeOffset(3601))
        assertEquals("-1:24:30", formatSparklineTimeOffset(5070))
        assertEquals("-2:00:00", formatSparklineTimeOffset(7200))
        assertEquals("-10:10:15", formatSparklineTimeOffset(36615))
    }

    @Test
    fun testResolveSparklineHeight() {
        assertEquals(26.dp, resolveSparklineHeight(ViewSize.XSMALL, isGraphOnly = false))
        assertEquals(26.dp, resolveSparklineHeight(ViewSize.SMALL, isGraphOnly = false))
        assertEquals(34.dp, resolveSparklineHeight(ViewSize.NORMAL, isGraphOnly = false))
        assertEquals(44.dp, resolveSparklineHeight(ViewSize.LARGE, isGraphOnly = false))
        assertEquals(52.dp, resolveSparklineHeight(ViewSize.XLARGE, isGraphOnly = false))

        assertEquals(24.dp, resolveSparklineHeight(ViewSize.SMALL, isGraphOnly = true))
        assertEquals(36.dp, resolveSparklineHeight(ViewSize.NORMAL, isGraphOnly = true))
        assertEquals(52.dp, resolveSparklineHeight(ViewSize.LARGE, isGraphOnly = true))
        assertEquals(68.dp, resolveSparklineHeight(ViewSize.XLARGE, isGraphOnly = true))
    }
}
