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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [ChartGestureDisambiguator] verifying directional classification,
 * touch-slop boundary handling, and quadrant invariance (REQ-UI-226 / TST-UI-180.1).
 */
class ChartGestureDisambiguatorTest {

    private val touchSlop = 16.0f

    @Test
    fun testDominantVertical_exceedingSlop_returnsTrue() {
        // Clear vertical drag downwards
        assertTrue(ChartGestureDisambiguator.isDominantVertical(diffX = 4f, diffY = 25f, touchSlop = touchSlop))
        assertFalse(ChartGestureDisambiguator.isDominantHorizontal(diffX = 4f, diffY = 25f, touchSlop = touchSlop))

        // Clear vertical drag upwards (negative delta)
        assertTrue(ChartGestureDisambiguator.isDominantVertical(diffX = -5f, diffY = -30f, touchSlop = touchSlop))
        assertFalse(ChartGestureDisambiguator.isDominantHorizontal(diffX = -5f, diffY = -30f, touchSlop = touchSlop))
    }

    @Test
    fun testDominantHorizontal_exceedingSlop_returnsTrue() {
        // Clear horizontal drag to the right
        assertTrue(ChartGestureDisambiguator.isDominantHorizontal(diffX = 30f, diffY = 6f, touchSlop = touchSlop))
        assertFalse(ChartGestureDisambiguator.isDominantVertical(diffX = 30f, diffY = 6f, touchSlop = touchSlop))

        // Clear horizontal drag to the left (negative delta)
        assertTrue(ChartGestureDisambiguator.isDominantHorizontal(diffX = -28f, diffY = -7f, touchSlop = touchSlop))
        assertFalse(ChartGestureDisambiguator.isDominantVertical(diffX = -28f, diffY = -7f, touchSlop = touchSlop))
    }

    @Test
    fun testSubSlop_returnsFalseForBoth() {
        // Small jitter below touch slop
        assertFalse(ChartGestureDisambiguator.isDominantVertical(diffX = 6f, diffY = 8f, touchSlop = touchSlop))
        assertFalse(ChartGestureDisambiguator.isDominantHorizontal(diffX = 6f, diffY = 8f, touchSlop = touchSlop))
        assertTrue(ChartGestureDisambiguator.isSubSlop(diffX = 6f, diffY = 8f, touchSlop = touchSlop))

        // Negative sub-slop jitter
        assertFalse(ChartGestureDisambiguator.isDominantVertical(diffX = -10f, diffY = -12f, touchSlop = touchSlop))
        assertFalse(ChartGestureDisambiguator.isDominantHorizontal(diffX = -10f, diffY = -12f, touchSlop = touchSlop))
        assertTrue(ChartGestureDisambiguator.isSubSlop(diffX = -10f, diffY = -12f, touchSlop = touchSlop))
    }

    @Test
    fun testEqualDeltaBoundary_at45Degrees() {
        // Exactly equal deltas above slop favor horizontal or vertical deterministically
        val diff = 24f
        assertTrue("Equal deltas above slop classify as dominant horizontal",
            ChartGestureDisambiguator.isDominantHorizontal(diffX = diff, diffY = diff, touchSlop = touchSlop)
        )
        assertFalse("Equal deltas above slop do not classify as dominant vertical",
            ChartGestureDisambiguator.isDominantVertical(diffX = diff, diffY = diff, touchSlop = touchSlop)
        )
    }

    @Test
    fun testAllFourQuadrants_symmetricEvaluation() {
        val dx = 25f
        val dy = 8f

        // (+, +)
        assertTrue(ChartGestureDisambiguator.isDominantHorizontal(dx, dy, touchSlop))
        // (-, +)
        assertTrue(ChartGestureDisambiguator.isDominantHorizontal(-dx, dy, touchSlop))
        // (+, -)
        assertTrue(ChartGestureDisambiguator.isDominantHorizontal(dx, -dy, touchSlop))
        // (-, -)
        assertTrue(ChartGestureDisambiguator.isDominantHorizontal(-dx, -dy, touchSlop))

        // Vertical dominance in all 4 quadrants
        val vx = 6f
        val vy = 35f
        assertTrue(ChartGestureDisambiguator.isDominantVertical(vx, vy, touchSlop))
        assertTrue(ChartGestureDisambiguator.isDominantVertical(-vx, vy, touchSlop))
        assertTrue(ChartGestureDisambiguator.isDominantVertical(vx, -vy, touchSlop))
        assertTrue(ChartGestureDisambiguator.isDominantVertical(-vx, -vy, touchSlop))
    }

    @Test
    fun testZeroDelta_returnsSubSlop() {
        assertFalse(ChartGestureDisambiguator.isDominantVertical(0f, 0f, touchSlop))
        assertFalse(ChartGestureDisambiguator.isDominantHorizontal(0f, 0f, touchSlop))
        assertTrue(ChartGestureDisambiguator.isSubSlop(0f, 0f, touchSlop))
    }

    @Test
    fun testExactSlopBoundary_requiresStrictlyGreater() {
        // When delta exactly equals touchSlop, it must not trigger drag (must strictly exceed)
        assertFalse(ChartGestureDisambiguator.isDominantHorizontal(touchSlop, 0f, touchSlop))
        assertFalse(ChartGestureDisambiguator.isDominantVertical(0f, touchSlop, touchSlop))
        assertTrue(ChartGestureDisambiguator.isSubSlop(touchSlop, 0f, touchSlop))
        assertTrue(ChartGestureDisambiguator.isSubSlop(0f, touchSlop, touchSlop))

        // When delta is strictly greater by 0.01f, it triggers
        assertTrue(ChartGestureDisambiguator.isDominantHorizontal(touchSlop + 0.01f, 0f, touchSlop))
        assertTrue(ChartGestureDisambiguator.isDominantVertical(0f, touchSlop + 0.01f, touchSlop))
    }
}

