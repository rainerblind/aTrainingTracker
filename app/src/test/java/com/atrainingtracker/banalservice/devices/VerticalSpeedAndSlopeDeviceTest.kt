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

package com.atrainingtracker.banalservice.devices

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying stabilized VAM calculation, linear regression smoothing,
 * stationary gating, flat road deadband, and outlier clamping (REQ-FIL-012, TST-FIL-004, ATT-1621).
 */
class VerticalSpeedAndSlopeDeviceTest {

    private val windowSize = VerticalSpeedAndSlopeDevice.VAM_WINDOW_SIZE // 15

    @Test
    fun testCalculateLinearRegressionSlope_withLinearRamp_computesExactSlope() {
        val history = DoubleArray(windowSize)
        val rateOfAscentMps = 0.2222 // ~800 m/h
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (i * rateOfAscentMps)
        }

        val slope = VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, windowSize, 0)
        assertEquals(rateOfAscentMps, slope, 0.001)
    }

    @Test
    fun testCalculateLinearRegressionSlope_insufficientSamples_returnsZero() {
        val history = DoubleArray(windowSize) { 100.0 }
        assertEquals(0.0, VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, 0, 0), 0.0001)
        assertEquals(0.0, VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, 1, 1), 0.0001)
        assertEquals(0.0, VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(null, 5, 0), 0.0001)
    }

    @Test
    fun testCalculateLinearRegressionSlope_warmup_computesImmediateDynamicSlope() {
        val history = DoubleArray(windowSize)
        history[0] = 100.0
        history[1] = 100.5 // 0.5 m/s delta across 2 samples

        val slope2 = VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, 2, 2)
        assertEquals(0.5, slope2, 0.001)

        history[2] = 101.0 // 0.5 m/s across 3 samples
        val slope3 = VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, 3, 3)
        assertEquals(0.5, slope3, 0.001)
    }

    @Test
    fun testCalculateVam_stationaryIndoorNoise_suppressedToZero() {
        val history = DoubleArray(windowSize)
        // Oscillating altitude representing stationary indoor/basement GPS noise (+-0.03 m/s = +-108 m/h)
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (if (i % 2 == 0) 0.15 else -0.15)
        }

        // Speed is 0.0 m/s (stationary), minSpeed is 0.5 m/s
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 0.0, 0.5)
        assertEquals("Stationary noise below 150 m/h must be clamped to 0", 0, vam)
    }

    @Test
    fun testCalculateVam_stationaryElevator_preservesGenuineAscent() {
        val history = DoubleArray(windowSize)
        val climbRateMps = 0.5 // 1800 m/h
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (i * climbRateMps)
        }

        // Horizontal speed is 0.0 m/s (e.g. elevator or ski lift), but vertical movement is genuine (1800 m/h >= 150 m/h)
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 0.0, 0.5)
        assertEquals(1800, vam)
    }

    @Test
    fun testCalculateVam_steadyClimb_convergesAccurately() {
        val history = DoubleArray(windowSize)
        val targetVam = 800.0 // 800 m/h
        val mps = targetVam / 3600.0
        for (i in 0 until windowSize) {
            history[i] = 250.0 + (i * mps)
        }

        // Moving at 5.0 m/s (> minSpeed 0.5 m/s)
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 5.0, 0.5)
        assertEquals(800, vam)
    }

    @Test
    fun testCalculateVam_flatRoadDeadband_clampsMicroFluctuationsToZero() {
        val history = DoubleArray(windowSize)
        // Tiny barometric drift of 0.005 m/s (~18 m/h < 35 m/h deadband)
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (i * 0.005)
        }

        // Moving at 8.0 m/s on flat road
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 8.0, 0.5)
        assertEquals("Micro-fluctuations below 35 m/h must clamp to 0", 0, vam)
    }

    @Test
    fun testCalculateVam_steadyDescent_convergesToNegativeVam() {
        val history = DoubleArray(windowSize)
        val targetVam = -600.0 // -600 m/h
        val mps = targetVam / 3600.0
        for (i in 0 until windowSize) {
            history[i] = 500.0 + (i * mps)
        }

        // Moving at 10.0 m/s downhill
        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 10.0, 0.5)
        assertEquals(-600, vam)
    }

    @Test
    fun testCalculateVam_glitchOutlierClamping_boundsExtremeJumps() {
        val history = DoubleArray(windowSize)
        // Extreme positive anomaly: +20 m/s = 72,000 m/h
        for (i in 0 until windowSize) {
            history[i] = 100.0 + (i * 20.0)
        }

        val vamMax = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 5.0, 0.5)
        assertEquals("Extreme jump must be clamped to +3000 m/h", 3000, vamMax)

        // Extreme negative anomaly: -20 m/s = -72,000 m/h
        for (i in 0 until windowSize) {
            history[i] = 500.0 - (i * 20.0)
        }
        val vamMin = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 5.0, 0.5)
        assertEquals("Extreme negative jump must be clamped to -3000 m/h", -3000, vamMin)
    }

    @Test
    fun testCircularBufferWrapping_maintainsCorrectChronologicalOrder() {
        val history = DoubleArray(windowSize)
        val rateMps = 0.25 // 900 m/h

        // Simulate 30 samples inserted into size 15 ring buffer
        var head = 0
        for (step in 0 until 30) {
            history[head] = step * rateMps
            head = (head + 1) % windowSize
        }

        // History count is full windowSize (15)
        val slope = VerticalSpeedAndSlopeDevice.calculateLinearRegressionSlope(history, windowSize, head)
        assertEquals(rateMps, slope, 0.001)

        val vam = VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, head, 6.0, 0.5)
        assertEquals(900, vam)
    }
}
