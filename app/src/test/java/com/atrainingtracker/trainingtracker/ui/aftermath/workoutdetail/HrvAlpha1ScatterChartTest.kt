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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail

import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying HrvAlpha1ScatterChart point filtering and ordinary least-squares linear regression
 * conforming to REQ-PER-035.4, REQ-PER-035.5, and TST-PER-043.4.
 */
class HrvAlpha1ScatterChartTest {

    @Test
    fun testHrvAlpha1Scatter_linearRegressionAndPointFiltering() {
        val testPoints = listOf(
            // Valid point 1
            PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), hr = 120, power = 150, hrvDfaA1 = 1.00, hrvArtifactRate = 0.0),
            // Valid point 2
            PathPoint(distance = 200.0, latLng = LatLng(0.0, 0.0), hr = 140, power = 190, hrvDfaA1 = 0.85, hrvArtifactRate = 1.2),
            // Valid point 3 (at HRVT1 0.75)
            PathPoint(distance = 300.0, latLng = LatLng(0.0, 0.0), hr = 150, power = 220, hrvDfaA1 = 0.75, hrvArtifactRate = 0.5),
            // Valid point 4 (at HRVT2 0.50)
            PathPoint(distance = 400.0, latLng = LatLng(0.0, 0.0), hr = 170, power = 270, hrvDfaA1 = 0.50, hrvArtifactRate = 2.0),
            // Invalid point: artifact rate > 5.0%
            PathPoint(distance = 500.0, latLng = LatLng(0.0, 0.0), hr = 180, power = 300, hrvDfaA1 = 0.45, hrvArtifactRate = 8.5),
            // Invalid point: alpha1 < 0.35 (below lower bound)
            PathPoint(distance = 600.0, latLng = LatLng(0.0, 0.0), hr = 190, power = 340, hrvDfaA1 = 0.25, hrvArtifactRate = 0.0),
            // Invalid point: alpha1 > 1.15 (above upper bound)
            PathPoint(distance = 700.0, latLng = LatLng(0.0, 0.0), hr = 100, power = 100, hrvDfaA1 = 1.30, hrvArtifactRate = 0.0),
            // Invalid point: null alpha1
            PathPoint(distance = 800.0, latLng = LatLng(0.0, 0.0), hr = 150, power = 210, hrvDfaA1 = null, hrvArtifactRate = 0.0)
        )

        // 1. Point Filtering for Heart Rate
        val filteredHr = HrvScatterMath.filterScatterPoints(testPoints, HrvScatterIntensityMode.HEART_RATE)
        assertEquals(4, filteredHr.size)
        assertEquals(120.0, filteredHr[0].intensity, 1e-4)
        assertEquals(1.00, filteredHr[0].alpha1, 1e-4)
        assertEquals(170.0, filteredHr[3].intensity, 1e-4)
        assertEquals(0.50, filteredHr[3].alpha1, 1e-4)

        // 2. Point Filtering for Power
        val filteredPower = HrvScatterMath.filterScatterPoints(testPoints, HrvScatterIntensityMode.POWER)
        assertEquals(4, filteredPower.size)
        assertEquals(150.0, filteredPower[0].intensity, 1e-4)
        assertEquals(270.0, filteredPower[3].intensity, 1e-4)

        // 3. Linear Regression Fit
        val regressionHr = HrvScatterMath.calculateLinearRegression(filteredHr)
        assertNotNull(regressionHr)
        assertTrue("Slope must be negative as HR increases", regressionHr!!.slope < 0.0)
        assertTrue("R-squared must be strong (> 0.90)", regressionHr.rSquared > 0.90)

        // Threshold crossing estimates
        assertNotNull(regressionHr.intensityAt075)
        assertNotNull(regressionHr.intensityAt050)
        assertTrue("HR at 0.75 must be between 140 and 160 bpm", regressionHr.intensityAt075!! in 140.0..160.0)
        assertTrue("HR at 0.50 must be between 165 and 180 bpm", regressionHr.intensityAt050!! in 165.0..180.0)
        assertTrue("HR at 0.50 must be greater than HR at 0.75", regressionHr.intensityAt050!! > regressionHr.intensityAt075!!)

        // 4. Empty / Insufficient points fallback
        val emptyResult = HrvScatterMath.calculateLinearRegression(emptyList())
        assertNull(emptyResult)

        val singlePointResult = HrvScatterMath.calculateLinearRegression(listOf(filteredHr[0]))
        assertNull(singlePointResult)
    }

    @Test
    fun testHrvAlpha1Scatter_fiveSecondDecimation() {
        val points1Hz = (0L..20L).map { sec ->
            PathPoint(
                distance = sec * 3.0,
                latLng = LatLng(0.0, 0.0),
                timeSec = sec,
                hr = 130 + sec.toInt(),
                speedMps = 3.0,
                hrvDfaA1 = 0.85 - sec * 0.01,
                hrvArtifactRate = 0.0
            )
        }

        val decimatedHr = HrvScatterMath.filterScatterPoints(points1Hz, HrvScatterIntensityMode.HEART_RATE)
        // With timestamps 0..20, keeping 0, 5, 10, 15, 20 -> 5 points
        assertEquals(5, decimatedHr.size)
        assertEquals(0L, decimatedHr[0].timeSec)
        assertEquals(5L, decimatedHr[1].timeSec)
        assertEquals(10L, decimatedHr[2].timeSec)
        assertEquals(15L, decimatedHr[3].timeSec)
        assertEquals(20L, decimatedHr[4].timeSec)
    }

    @Test
    fun testHrvAlpha1Scatter_paceIntensityModeAndConfiguredThresholds() {
        // Speed in m/s: 2.5 m/s (~6:40 min/km = 0.40 s/m), 3.0 m/s (~5:33 min/km = 0.333 s/m),
        // 3.5 m/s (~4:45 min/km = 0.286 s/m), 4.0 m/s (~4:10 min/km = 0.25 s/m)
        val runningPoints = listOf(
            PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), timeSec = 0L, speedMps = 2.5, hrvDfaA1 = 0.95, hrvArtifactRate = 0.0),
            PathPoint(distance = 200.0, latLng = LatLng(0.0, 0.0), timeSec = 10L, speedMps = 3.0, hrvDfaA1 = 0.82, hrvArtifactRate = 0.5),
            PathPoint(distance = 300.0, latLng = LatLng(0.0, 0.0), timeSec = 20L, speedMps = 3.5, hrvDfaA1 = 0.70, hrvArtifactRate = 1.0),
            PathPoint(distance = 400.0, latLng = LatLng(0.0, 0.0), timeSec = 30L, speedMps = 4.0, hrvDfaA1 = 0.55, hrvArtifactRate = 0.2),
            // Below minimum running threshold speed (< 0.55 m/s)
            PathPoint(distance = 500.0, latLng = LatLng(0.0, 0.0), timeSec = 40L, speedMps = 0.4, hrvDfaA1 = 0.75, hrvArtifactRate = 0.0)
        )

        val filteredPace = HrvScatterMath.filterScatterPoints(runningPoints, HrvScatterIntensityMode.PACE)
        assertEquals(4, filteredPace.size)
        assertEquals(1.0 / 2.5, filteredPace[0].intensity, 1e-4)
        assertEquals(1.0 / 4.0, filteredPace[3].intensity, 1e-4)

        // Test custom configured thresholds T1 = 0.80, T2 = 0.60
        val customRegression = HrvScatterMath.calculateLinearRegression(filteredPace, t1 = 0.80, t2 = 0.60)
        assertNotNull(customRegression)
        // With X = s/m: higher s/m is slower (lower intensity) -> higher a1 -> positive slope
        assertTrue("Slope must be positive when intensity is pace s/m", customRegression!!.slope > 0.0)
        assertNotNull(customRegression.intensityAtT1)
        assertNotNull(customRegression.intensityAtT2)
        assertEquals(customRegression.intensityAtT1, customRegression.intensityAt075)
        assertEquals(customRegression.intensityAtT2, customRegression.intensityAt050)
        assertTrue("T1 pace must be slower (larger s/m) than T2 pace", customRegression.intensityAtT1!! > customRegression.intensityAtT2!!)
    }
}
