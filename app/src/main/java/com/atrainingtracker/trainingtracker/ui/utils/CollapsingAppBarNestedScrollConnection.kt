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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.roundToInt

/**
 * Coordinated collapsing app bar nested scroll connection (REQ-UI-250 / ATT-2150, REQ-UI-254 / ATT-2178, REQ-UI-266 / ATT-2304).
 *
 * Implements non-blocking, sub-pixel precision scroll dispatch:
 * 1. Upward gestures (`available.y < 0`): Consumed in [onPreScroll] to smoothly collapse the app bar
 *    from `0` to `-appBarMaxHeight`. Once fully collapsed, unconsumed deltas pass to child containers.
 * 2. Downward gestures (`available.y > 0`): When [quickReturn] is enabled (default), consumed immediately
 *    in [onPreScroll] while `rawOffset < 0` to smoothly expand the app bar back towards `0` (Quick Return).
 *    Once fully expanded (`rawOffset == 0`), deltas pass unconsumed to child containers.
 * 3. Sub-pixel float precision: Accumulates fractional offsets in `rawOffset` to eliminate integer
 *    truncation micro-stutters.
 *
 * @param initialAppBarMaxHeight Initial maximum height in pixels the app bar can collapse.
 * @param quickReturn Whether downward gestures immediately expand the app bar in [onPreScroll] (true, default)
 *                    or defer expansion to [onPostScroll] when child views reach their top edge (false).
 */
class CollapsingAppBarNestedScrollConnection(
    initialAppBarMaxHeight: Int = 0,
    val quickReturn: Boolean = true
) : NestedScrollConnection {

    var appBarMaxHeight by mutableIntStateOf(initialAppBarMaxHeight)

    // This holds the current translation of the bar in Pixels (rounded from rawOffset for 100% caller compatibility)
    var appBarOffset by mutableIntStateOf(0)
        private set

    private var rawOffset = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        if (quickReturn) {
            // Immediate bidirectional scroll dispatch (Quick Return - REQ-UI-266 / ATT-2304):
            // 1. Upward gestures (delta < 0f): smoothly collapse the app bar while rawOffset > -appBarMaxHeight.
            // 2. Downward gestures (delta > 0f): immediately expand the app bar while rawOffset < 0f.
            if ((delta < 0f && rawOffset > -appBarMaxHeight) || (delta > 0f && rawOffset < 0f)) {
                val newRaw = (rawOffset + delta).coerceIn(-appBarMaxHeight.toFloat(), 0f)
                val consumed = newRaw - rawOffset
                rawOffset = newRaw
                appBarOffset = newRaw.roundToInt()
                return Offset(0f, consumed)
            }
        } else {
            // Only consume upward scroll (collapse) in onPreScroll when quick return is disabled
            if (delta < 0f && rawOffset > -appBarMaxHeight) {
                val newRaw = (rawOffset + delta).coerceIn(-appBarMaxHeight.toFloat(), 0f)
                val consumed = newRaw - rawOffset
                rawOffset = newRaw
                appBarOffset = newRaw.roundToInt()
                return Offset(0f, consumed)
            }
        }
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        val delta = available.y
        // Consume downward scroll in onPostScroll when child container is at top boundary (or if quickReturn is disabled)
        if (delta > 0f && rawOffset < 0f) {
            val newRaw = (rawOffset + delta).coerceIn(-appBarMaxHeight.toFloat(), 0f)
            val consumedY = newRaw - rawOffset
            rawOffset = newRaw
            appBarOffset = newRaw.roundToInt()
            return Offset(0f, consumedY)
        }
        return Offset.Zero
    }
}