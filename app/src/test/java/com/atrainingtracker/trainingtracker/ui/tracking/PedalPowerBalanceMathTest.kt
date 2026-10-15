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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PedalPowerBalanceMath] verifying Left/Right computation, deviation magnitude,
 * physiological asymmetry tiers, and formatting (REQ-UI-377, TST-UI-340.1, TST-UI-340.2).
 */
class PedalPowerBalanceMathTest {

    @Test
    fun testPerfectBalance_50_50() {
        val state = PedalPowerBalanceMath.calculateBalanceState(50)
        assertFalse(state.isStale)
        assertEquals(50, state.leftPercent)
        assertEquals(50, state.rightPercent)
        assertEquals(0, state.deviationPercent)
        assertEquals(DominantSide.BALANCED, state.dominantSide)
        assertEquals(PedalBalanceTier.OPTIMAL, state.tier)
        assertEquals(PedalPowerBalanceMath.OPTIMAL_COLOR, state.tierColor)
        assertNotNull(state.indicatorFraction)
        assertEquals(0.50f, state.indicatorFraction!!, 0.001f)
        assertEquals("50 | 50", PedalPowerBalanceMath.formatBalanceString(state))
    }

    @Test
    fun testOptimalSymmetry_withinTwoPercent() {
        // 48% Right -> 52% Left (Left dominant by 2%)
        val leftOptimal = PedalPowerBalanceMath.calculateBalanceState(48)
        assertFalse(leftOptimal.isStale)
        assertEquals(52, leftOptimal.leftPercent)
        assertEquals(48, leftOptimal.rightPercent)
        assertEquals(2, leftOptimal.deviationPercent)
        assertEquals(DominantSide.LEFT, leftOptimal.dominantSide)
        assertEquals(PedalBalanceTier.OPTIMAL, leftOptimal.tier)
        assertEquals(PedalPowerBalanceMath.OPTIMAL_COLOR, leftOptimal.tierColor)
        assertEquals(0.48f, leftOptimal.indicatorFraction!!, 0.001f)
        assertEquals("52 | 48", PedalPowerBalanceMath.formatBalanceString(leftOptimal))

        // 52% Right -> 48% Left (Right dominant by 2%)
        val rightOptimal = PedalPowerBalanceMath.calculateBalanceState(52)
        assertFalse(rightOptimal.isStale)
        assertEquals(48, rightOptimal.leftPercent)
        assertEquals(52, rightOptimal.rightPercent)
        assertEquals(2, rightOptimal.deviationPercent)
        assertEquals(DominantSide.RIGHT, rightOptimal.dominantSide)
        assertEquals(PedalBalanceTier.OPTIMAL, rightOptimal.tier)
        assertEquals(0.52f, rightOptimal.indicatorFraction!!, 0.001f)
        assertEquals("48 | 52", PedalPowerBalanceMath.formatBalanceString(rightOptimal))
    }

    @Test
    fun testMildAsymmetry_threeToFivePercent() {
        // 46% Right -> 54% Left (|Delta| = 4%)
        val mildLeft = PedalPowerBalanceMath.calculateBalanceState(46)
        assertFalse(mildLeft.isStale)
        assertEquals(54, mildLeft.leftPercent)
        assertEquals(46, mildLeft.rightPercent)
        assertEquals(4, mildLeft.deviationPercent)
        assertEquals(DominantSide.LEFT, mildLeft.dominantSide)
        assertEquals(PedalBalanceTier.MILD, mildLeft.tier)
        assertEquals(PedalPowerBalanceMath.MILD_COLOR, mildLeft.tierColor)
        assertEquals(0.46f, mildLeft.indicatorFraction!!, 0.001f)
        assertEquals("54 | 46", PedalPowerBalanceMath.formatBalanceString(mildLeft))

        // 55% Right -> 45% Left (|Delta| = 5%)
        val mildRight = PedalPowerBalanceMath.calculateBalanceState(55)
        assertFalse(mildRight.isStale)
        assertEquals(45, mildRight.leftPercent)
        assertEquals(55, mildRight.rightPercent)
        assertEquals(5, mildRight.deviationPercent)
        assertEquals(DominantSide.RIGHT, mildRight.dominantSide)
        assertEquals(PedalBalanceTier.MILD, mildRight.tier)
        assertEquals(0.55f, mildRight.indicatorFraction!!, 0.001f)
        assertEquals("45 | 55", PedalPowerBalanceMath.formatBalanceString(mildRight))
    }

    @Test
    fun testSevereAsymmetry_beyondFivePercent() {
        // 40% Right -> 60% Left (|Delta| = 10%)
        val severeLeft = PedalPowerBalanceMath.calculateBalanceState(40)
        assertFalse(severeLeft.isStale)
        assertEquals(60, severeLeft.leftPercent)
        assertEquals(40, severeLeft.rightPercent)
        assertEquals(10, severeLeft.deviationPercent)
        assertEquals(DominantSide.LEFT, severeLeft.dominantSide)
        assertEquals(PedalBalanceTier.SEVERE, severeLeft.tier)
        assertEquals(PedalPowerBalanceMath.SEVERE_COLOR, severeLeft.tierColor)
        assertEquals(0.40f, severeLeft.indicatorFraction!!, 0.001f)
        assertEquals("60 | 40", PedalPowerBalanceMath.formatBalanceString(severeLeft))

        // 62% Right -> 38% Left (|Delta| = 12%)
        val severeRight = PedalPowerBalanceMath.calculateBalanceState(62)
        assertFalse(severeRight.isStale)
        assertEquals(38, severeRight.leftPercent)
        assertEquals(62, severeRight.rightPercent)
        assertEquals(12, severeRight.deviationPercent)
        assertEquals(DominantSide.RIGHT, severeRight.dominantSide)
        assertEquals(PedalBalanceTier.SEVERE, severeRight.tier)
        assertEquals(0.62f, severeRight.indicatorFraction!!, 0.001f)
        assertEquals("38 | 62", PedalPowerBalanceMath.formatBalanceString(severeRight))
    }

    @Test
    fun testBoundaryClamping() {
        val negative = PedalPowerBalanceMath.calculateBalanceState(-10)
        assertEquals(100, negative.leftPercent)
        assertEquals(0, negative.rightPercent)
        assertEquals(0.0f, negative.indicatorFraction!!, 0.001f)

        val over100 = PedalPowerBalanceMath.calculateBalanceState(125)
        assertEquals(0, over100.leftPercent)
        assertEquals(100, over100.rightPercent)
        assertEquals(1.0f, over100.indicatorFraction!!, 0.001f)
    }

    @Test
    fun testNullAndNonFiniteInputs_returnsStale() {
        val nullState = PedalPowerBalanceMath.calculateBalanceState(null)
        assertTrue(nullState.isStale)
        assertNull(nullState.indicatorFraction)
        assertEquals("-- | --", PedalPowerBalanceMath.formatBalanceString(nullState))

        val nanState = PedalPowerBalanceMath.calculateBalanceState(Double.NaN)
        assertTrue(nanState.isStale)
        assertNull(nanState.indicatorFraction)

        val infState = PedalPowerBalanceMath.calculateBalanceState(Double.POSITIVE_INFINITY)
        assertTrue(infState.isStale)
        assertNull(infState.indicatorFraction)
    }
}
