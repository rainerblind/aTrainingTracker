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

package com.atrainingtracker.trainingtracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying [FastScrollbar] scroll calculations and index targeting (ATT-861, REQ-UI-139, TST-UI-092).
 */
class FastScrollbarTest {

    @Test
    fun calculateScrollProgress_zeroOrInvalidItems_returnsZero() {
        assertEquals(0f, calculateScrollProgress(firstVisibleIndex = 0, firstVisibleOffset = 0, itemSize = 100, totalItems = 0), 0.001f)
        assertEquals(0f, calculateScrollProgress(firstVisibleIndex = 0, firstVisibleOffset = 0, itemSize = 0, totalItems = 10), 0.001f)
        assertEquals(0f, calculateScrollProgress(firstVisibleIndex = -1, firstVisibleOffset = 0, itemSize = -10, totalItems = -5), 0.001f)
    }

    @Test
    fun calculateScrollProgress_atStartOfList_returnsZero() {
        val progress = calculateScrollProgress(
            firstVisibleIndex = 0,
            firstVisibleOffset = 0,
            itemSize = 200,
            totalItems = 20
        )
        assertEquals(0f, progress, 0.001f)
    }

    @Test
    fun calculateScrollProgress_midwayThroughList_calculatesAccurateFraction() {
        // 10 items total. At index 5 with 0 offset -> 5/10 = 0.5f
        val progress = calculateScrollProgress(
            firstVisibleIndex = 5,
            firstVisibleOffset = 0,
            itemSize = 200,
            totalItems = 10
        )
        assertEquals(0.5f, progress, 0.001f)
    }

    @Test
    fun calculateScrollProgress_partialItemOffset_addsFractionalProgress() {
        // Index 2, offset 100px of 200px itemSize = 2.5 items. Total 10 -> 2.5 / 10 = 0.25f
        val progress = calculateScrollProgress(
            firstVisibleIndex = 2,
            firstVisibleOffset = 100,
            itemSize = 200,
            totalItems = 10
        )
        assertEquals(0.25f, progress, 0.001f)
    }

    @Test
    fun calculateScrollProgress_exceedingBounds_isCoercedBetweenZeroAndOne() {
        val negativeProgress = calculateScrollProgress(
            firstVisibleIndex = -5,
            firstVisibleOffset = -100,
            itemSize = 100,
            totalItems = 10
        )
        assertEquals(0f, negativeProgress, 0.001f)

        val overflowProgress = calculateScrollProgress(
            firstVisibleIndex = 15,
            firstVisibleOffset = 50,
            itemSize = 100,
            totalItems = 10
        )
        assertEquals(1f, overflowProgress, 0.001f)
    }

    @Test
    fun calculateTargetIndex_zeroTotalItems_returnsZero() {
        assertEquals(0, calculateTargetIndex(currentProgress = 0.5f, deltaProgress = 0.1f, totalItems = 0))
    }

    @Test
    fun calculateTargetIndex_dragDownwards_increasesTargetIndex() {
        // Start at progress 0.2 (index 2 out of 10), drag downwards by +0.3 delta -> new progress 0.5 -> target index 5
        val target = calculateTargetIndex(
            currentProgress = 0.2f,
            deltaProgress = 0.3f,
            totalItems = 10
        )
        assertEquals(5, target)
    }

    @Test
    fun calculateTargetIndex_dragUpwards_decreasesTargetIndex() {
        // Start at progress 0.8, drag upwards by -0.4 delta -> new progress 0.4 -> target index 4
        val target = calculateTargetIndex(
            currentProgress = 0.8f,
            deltaProgress = -0.4f,
            totalItems = 10
        )
        assertEquals(4, target)
    }

    @Test
    fun calculateTargetIndex_dragPastTopBoundary_clampsToZero() {
        val target = calculateTargetIndex(
            currentProgress = 0.1f,
            deltaProgress = -0.5f,
            totalItems = 20
        )
        assertEquals(0, target)
    }

    @Test
    fun calculateTargetIndex_dragPastBottomBoundary_clampsToTotalMinusOne() {
        val target = calculateTargetIndex(
            currentProgress = 0.8f,
            deltaProgress = 0.5f,
            totalItems = 20
        )
        assertEquals(19, target)
    }
}
