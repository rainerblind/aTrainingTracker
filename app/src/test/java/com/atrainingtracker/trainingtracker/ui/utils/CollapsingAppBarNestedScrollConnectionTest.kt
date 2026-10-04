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

package com.atrainingtracker.trainingtracker.ui.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit and contract tests for [CollapsingAppBarNestedScrollConnection] verifying
 * non-blocking nested scroll dispatch, sub-pixel float precision accumulation,
 * and deferred downward expansion (REQ-UI-254 / TST-UI-213 / ATT-2178).
 */
class CollapsingAppBarNestedScrollConnectionTest {

    private lateinit var connection: CollapsingAppBarNestedScrollConnection

    @Before
    fun setUp() {
        connection = CollapsingAppBarNestedScrollConnection(initialAppBarMaxHeight = 300)
    }

    @Test
    fun testInitialState_isUncollapsed() {
        assertEquals(300, connection.appBarMaxHeight)
        assertEquals(0, connection.appBarOffset)
    }

    @Test
    fun testUpwardScroll_consumesInPreScroll_andCollapses() {
        val consumed = connection.onPreScroll(
            available = Offset(0f, -100f),
            source = NestedScrollSource.UserInput
        )

        assertEquals(0f, consumed.x, 0.001f)
        assertEquals(-100f, consumed.y, 0.001f)
        assertEquals(-100, connection.appBarOffset)
    }

    @Test
    fun testUpwardScroll_clampsAtMaxHeight() {
        val consumed = connection.onPreScroll(
            available = Offset(0f, -500f),
            source = NestedScrollSource.UserInput
        )

        assertEquals(-300f, consumed.y, 0.001f)
        assertEquals(-300, connection.appBarOffset)

        // Further upward scroll consumes zero
        val additionalConsumed = connection.onPreScroll(
            available = Offset(0f, -50f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(0f, additionalConsumed.y, 0.001f)
        assertEquals(-300, connection.appBarOffset)
    }

    @Test
    fun testDownwardScroll_consumesInPreScroll_andExpandsImmediately() {
        // First collapse partially
        connection.onPreScroll(Offset(0f, -200f), NestedScrollSource.UserInput)
        assertEquals(-200, connection.appBarOffset)

        // Downward scroll in onPreScroll must immediately consume and expand header (Quick Return - REQ-UI-266)
        val preConsumed = connection.onPreScroll(
            available = Offset(0f, 50f),
            source = NestedScrollSource.UserInput
        )

        assertEquals(50f, preConsumed.y, 0.001f)
        assertEquals(-150, connection.appBarOffset)
    }

    @Test
    fun testDownwardScroll_clampsAtZero_andDefersExcessToChild() {
        // Partially collapsed to -100
        connection.onPreScroll(Offset(0f, -100f), NestedScrollSource.UserInput)
        assertEquals(-100, connection.appBarOffset)

        // Downward scroll of 250f exceeds remaining range (100f)
        val preConsumed = connection.onPreScroll(
            available = Offset(0f, 250f),
            source = NestedScrollSource.UserInput
        )

        // Consumes exactly 100f to reach 0; leaves remaining 150f unconsumed for child
        assertEquals(100f, preConsumed.y, 0.001f)
        assertEquals(0, connection.appBarOffset)

        // Further downward scroll when fully expanded must return Offset.Zero
        val nextConsumed = connection.onPreScroll(
            available = Offset(0f, 150f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(Offset.Zero, nextConsumed)
        assertEquals(0, connection.appBarOffset)
    }

    @Test
    fun testDownwardScroll_whenQuickReturnDisabled_defersToPostScroll() {
        val nonQuickConnection = CollapsingAppBarNestedScrollConnection(
            initialAppBarMaxHeight = 300,
            quickReturn = false
        )
        // Collapse fully
        nonQuickConnection.onPreScroll(Offset(0f, -300f), NestedScrollSource.UserInput)
        assertEquals(-300, nonQuickConnection.appBarOffset)

        // Downward scroll in onPreScroll returns Zero when quickReturn is false
        val preConsumed = nonQuickConnection.onPreScroll(
            available = Offset(0f, 50f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(Offset.Zero, preConsumed)
        assertEquals(-300, nonQuickConnection.appBarOffset)

        // Child reaches top, unconsumed delta passed to onPostScroll
        val postConsumed = nonQuickConnection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, 120f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(120f, postConsumed.y, 0.001f)
        assertEquals(-180, nonQuickConnection.appBarOffset)
    }

    @Test
    fun testSubPixelPrecision_accumulatesWithoutIntegerTruncationJitter() {
        // Dispatch fractional sub-pixel deltas (< 1px)
        val delta1 = connection.onPreScroll(Offset(0f, -0.4f), NestedScrollSource.UserInput)
        val delta2 = connection.onPreScroll(Offset(0f, -0.4f), NestedScrollSource.UserInput)
        val delta3 = connection.onPreScroll(Offset(0f, -0.4f), NestedScrollSource.UserInput)

        // In the old implementation with .toInt(), each -0.4f became 0, losing all movement.
        // With float accumulation, all deltas must be consumed accurately.
        assertEquals(-0.4f, delta1.y, 0.001f)
        assertEquals(-0.4f, delta2.y, 0.001f)
        assertEquals(-0.4f, delta3.y, 0.001f)
        assertEquals(-1, connection.appBarOffset) // roundToInt(-1.2f) = -1
    }
}
