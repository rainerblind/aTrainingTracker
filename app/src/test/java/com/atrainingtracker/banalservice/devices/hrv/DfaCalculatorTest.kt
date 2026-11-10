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

package com.atrainingtracker.banalservice.devices.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Unit tests for [DfaCalculator] validating DFA alpha 1 calculation across scales n in [4, 16],
 * sample size boundary conditions, and deterministic synthetic signal scaling pursuant to REQ-TRK-060.4.
 */
class DfaCalculatorTest {

    @Test
    fun testInsufficientSamplesReturnsNan() {
        val smallArray = DoubleArray(20) { 800.0 }
        val alpha1 = DfaCalculator.computeDfaAlpha1(smallArray)
        assertTrue(alpha1.isNaN())
    }

    @Test
    fun testWhiteNoiseLikeSignalYieldsAlphaNearPointFive() {
        // Pseudorandom uncorrelated signal has theoretical DFA alpha ~ 0.5
        val rng = java.util.Random(42L)
        val n = 256
        val whiteNoise = DoubleArray(n) { 800.0 + (rng.nextDouble() - 0.5) * 50.0 }

        val alpha1 = DfaCalculator.computeDfaAlpha1(whiteNoise)
        assertFalse(alpha1.isNaN())
        // For uncorrelated white noise, alpha is approximately 0.5 (+- 0.15 for finite sample)
        assertEquals(0.5, alpha1, 0.20)
    }

    @Test
    fun testSmoothSinusoidalWaveYieldsHigherAlpha() {
        // Highly correlated sinusoidal signal has DFA alpha > 1.0 (smooth deterministic trend)
        val n = 256
        val sineWave = DoubleArray(n) { i -> 800.0 + 50.0 * sin(2.0 * Math.PI * i / 64.0) }

        val alpha1 = DfaCalculator.computeDfaAlpha1(sineWave)
        assertFalse(alpha1.isNaN())
        assertTrue("Expected alpha1 > 1.0 for smooth harmonic wave, was $alpha1", alpha1 > 1.0)
    }

    @Test
    fun testConstantSignalDoesNotCrash() {
        val constantArray = DoubleArray(128) { 800.0 }
        val alpha1 = DfaCalculator.computeDfaAlpha1(constantArray)
        // With zero fluctuations across all boxes, slope is either NaN or 0.0, but must not crash
        assertTrue(alpha1.isNaN() || alpha1 == 0.0)
    }
}
