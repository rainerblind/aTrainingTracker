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
}
