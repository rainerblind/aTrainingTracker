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

package com.atrainingtracker.trainingtracker.ui.components.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [SplitPaneMath] verifying split fraction boundary clamping,
 * available height calculations, and gesture delta updating (REQ-UI-223, TST-UI-177.1).
 */
class SplitPaneMathTest {

    @Test
    fun testDefaultSplitFraction_isFiftyPercent() {
        assertEquals(0.50f, SplitPaneMath.DEFAULT_SPLIT_FRACTION, 0.0001f)
    }

    @Test
    fun testCalculateAvailableHeight_subtractsDividerHeight() {
        val totalHeight = 1200f
        val dividerHeight = 48f
        val available = SplitPaneMath.calculateAvailableHeight(totalHeight, dividerHeight)
        assertEquals(1152f, available, 0.0001f)

        // Minimum floor protection when divider exceeds total height
        val degenerate = SplitPaneMath.calculateAvailableHeight(20f, 50f)
        assertEquals(1f, degenerate, 0.0001f)
    }

    @Test
    fun testCalculateBounds_returnsClampedFractions() {
        val availableHeight = 1000f
        val minTopPx = 200f
        val minBottomPx = 300f

        val minFraction = SplitPaneMath.calculateMinFraction(minTopPx, availableHeight)
        // 200 / 1000 = 0.20
        assertEquals(0.20f, minFraction, 0.0001f)

        val maxFraction = SplitPaneMath.calculateMaxFraction(minBottomPx, availableHeight, minFraction)
        // 1 - (300 / 1000) = 0.70
        assertEquals(0.70f, maxFraction, 0.0001f)

        // Extreme bounds clamping to [0.05, 0.95]
        val extremeMin = SplitPaneMath.calculateMinFraction(10f, 1000f)
        assertEquals(0.05f, extremeMin, 0.0001f) // clamped to 0.05f

        val extremeMax = SplitPaneMath.calculateMaxFraction(10f, 1000f, extremeMin)
        assertEquals(0.95f, extremeMax, 0.0001f) // clamped to 0.95f
    }

    @Test
    fun testUpdateFraction_normalDrag_adjustsFractionSmoothly() {
        val availableHeight = 1000f
        val minFraction = 0.15f
        val maxFraction = 0.85f
        val currentFraction = 0.50f

        // Drag downwards by 100px (+0.10)
        val updatedDown = SplitPaneMath.updateFraction(
            currentFraction = currentFraction,
            deltaPx = 100f,
            availableHeightPx = availableHeight,
            minFraction = minFraction,
            maxFraction = maxFraction
        )
        assertEquals(0.60f, updatedDown, 0.0001f)

        // Drag upwards by 150px (-0.15)
        val updatedUp = SplitPaneMath.updateFraction(
            currentFraction = currentFraction,
            deltaPx = -150f,
            availableHeightPx = availableHeight,
            minFraction = minFraction,
            maxFraction = maxFraction
        )
        assertEquals(0.35f, updatedUp, 0.0001f)
    }

    @Test
    fun testUpdateFraction_extremeDeltas_clampsToSafeBounds() {
        val availableHeight = 1000f
        val minFraction = 0.20f
        val maxFraction = 0.80f
        val currentFraction = 0.50f

        // Massive upward drag (-2000px)
        val clampedMin = SplitPaneMath.updateFraction(
            currentFraction = currentFraction,
            deltaPx = -2000f,
            availableHeightPx = availableHeight,
            minFraction = minFraction,
            maxFraction = maxFraction
        )
        assertEquals(minFraction, clampedMin, 0.0001f)

        // Massive downward drag (+3000px)
        val clampedMax = SplitPaneMath.updateFraction(
            currentFraction = currentFraction,
            deltaPx = 3000f,
            availableHeightPx = availableHeight,
            minFraction = minFraction,
            maxFraction = maxFraction
        )
        assertEquals(maxFraction, clampedMax, 0.0001f)
    }
}
