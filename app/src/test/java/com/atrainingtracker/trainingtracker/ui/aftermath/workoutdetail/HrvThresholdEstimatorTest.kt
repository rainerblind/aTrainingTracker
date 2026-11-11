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

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification suite for lag-aligned, confidence-gated HRVT1/HRVT2 estimation
 * pursuant to REQ-PER-036 (TST-PER-044.1 to TST-PER-044.4).
 */
class HrvThresholdEstimatorTest {

    private fun createPoint(
        timeSec: Long,
        hr: Int? = 140,
        power: Int? = 200,
        speedMps: Double? = null,
        a1: Double? = 0.75,
        art: Double? = 1.0
    ): PathPoint {
        return PathPoint(
            distance = timeSec * 5.0,
            latLng = LatLng(48.0, 11.0),
            altitude = 500.0,
            timeSec = timeSec,
            hr = hr,
            power = power,
            speedMps = speedMps,
            slope = 0.0,
            hrvDfaA1 = a1,
            hrvRmssd = 40.0,
            hrvArtifactRate = art
        )
    }

    @Test
    fun `empty points or no HRV returns insufficient span without crashing (REQ-PER-036_1)`() {
        val result = HrvThresholdEstimator.estimateThresholds(emptyList())
        assertEquals(ThresholdConfidence.INSUFFICIENT_SPAN, result.overallConfidence)
        assertNull(result.hrvt1.intensity)
        assertNull(result.hrvt2.intensity)
        assertEquals(0, result.effectiveSampleSize)
    }

    @Test
    fun `fresh window filtering excludes warmup and late drift for free workouts (TST-PER-044_1)`() {
        val points = mutableListOf<PathPoint>()
        // Warmup: t in 0..595s (should be ignored for free workouts)
        for (t in 0L..595L step 5L) {
            points.add(createPoint(timeSec = t, hr = 110, a1 = 1.00))
        }
        // Fresh Window: t in 600..3600s (should be included)
        // Linear drop: from hr = 130 (a1 = 0.95) to hr = 180 (a1 = 0.40)
        val freshCount = ((3600L - 600L) / 5L).toInt()
        for (i in 0..freshCount) {
            val t = 600L + i * 5L
            val fraction = i.toDouble() / freshCount.toDouble()
            val hr = (130 + fraction * 50).toInt()
            val a1 = 0.95 - fraction * 0.55 // 0.95 down to 0.40
            points.add(createPoint(timeSec = t, hr = hr, a1 = a1))
        }
        // Late fatigue drift: t in 3605..7200s (should be ignored)
        for (t in 3605L..7200L step 5L) {
            points.add(createPoint(timeSec = t, hr = 175, a1 = 0.38))
        }

        val result = HrvThresholdEstimator.estimateThresholds(
            points = points,
            mode = HrvScatterIntensityMode.HEART_RATE,
            protocolType = HrvProtocolType.FREE_WORKOUT
        )

        // Effective sample size should be ~51 bins (600s to 3600s = 50-51 minutes)
        assertTrue("Expected >= 50 decimated bins, got ${result.effectiveSampleSize}", result.effectiveSampleSize >= 50)
        assertTrue(result.hrvt1.spanSatisfied)
        assertTrue(result.hrvt2.spanSatisfied)
        assertNotNull(result.hrvt1.intensity)
        assertNotNull(result.hrvt2.intensity)
        assertEquals(ThresholdConfidence.HIGH, result.overallConfidence)
    }

    @Test
    fun `points with high artifact rate greater than 5 percent are excluded (TST-PER-044_1)`() {
        val points = mutableListOf<PathPoint>()
        // Valid fresh window with 6.0% artifact rate -> all should be filtered out
        for (t in 600L..1200L step 5L) {
            points.add(createPoint(timeSec = t, hr = 140, a1 = 0.75, art = 6.0))
        }

        val result = HrvThresholdEstimator.estimateThresholds(points)
        assertEquals(0, result.effectiveSampleSize)
        assertNull(result.hrvt1.intensity)
        assertEquals(ThresholdConfidence.INSUFFICIENT_SPAN, result.overallConfidence)
    }

    @Test
    fun `decimated grid regression calculates valid R2 and decorrelates serial samples (TST-PER-044_2)`() {
        val points = mutableListOf<PathPoint>()
        // 15 minutes of data (t = 600..1500s) with clean linear response
        for (t in 600L..1500L step 5L) {
            val fraction = (t - 600L).toDouble() / 900.0
            val power = 150.0 + fraction * 150.0 // 150W to 300W
            val a1 = 0.90 - fraction * 0.50 // 0.90 down to 0.40
            points.add(createPoint(timeSec = t, power = power.toInt(), a1 = a1, art = 1.5))
        }

        val result = HrvThresholdEstimator.estimateThresholds(
            points = points,
            mode = HrvScatterIntensityMode.POWER,
            protocolType = HrvProtocolType.FREE_WORKOUT
        )

        // 900s / 60s = 15-16 decimated bins
        assertTrue("N_eff should be ~15, got ${result.effectiveSampleSize}", result.effectiveSampleSize in 15..16)
        assertNotNull(result.rSquared)
        assertTrue("R² should be >= 0.95, got ${result.rSquared}", result.rSquared!! >= 0.95)
        assertEquals(ThresholdConfidence.HIGH, result.overallConfidence)
    }

    @Test
    fun `continuous ramp protocol applies MRT lag correction of 45 seconds (TST-PER-044_2)`() {
        val points = mutableListOf<PathPoint>()
        // Monotonic continuous ramp from t = 600 to 2400s (+15 W/min = 0.25 W/s)
        for (t in 600L..2400L step 5L) {
            val power = 100.0 + (t - 600L) * 0.15 // starts at 100W, reaches 370W
            val fraction = (t - 600L).toDouble() / 1800.0
            val a1 = 1.00 - fraction * 0.60 // 1.00 to 0.40
            points.add(createPoint(timeSec = t, power = power.toInt(), a1 = a1, art = 1.0))
        }

        val freeResult = HrvThresholdEstimator.estimateThresholds(
            points = points,
            mode = HrvScatterIntensityMode.POWER,
            protocolType = HrvProtocolType.FREE_WORKOUT
        )

        val rampResult = HrvThresholdEstimator.estimateThresholds(
            points = points,
            mode = HrvScatterIntensityMode.POWER,
            protocolType = HrvProtocolType.GUIDED_RAMP
        )

        assertNotNull(freeResult.hrvt1.intensity)
        assertNotNull(rampResult.hrvt1.intensity)

        // At MRT = 45s with slope ~0.15 W/s, power is lag-aligned by ~6.75 W
        val diff = freeResult.hrvt1.intensity!! - rampResult.hrvt1.intensity!!
        assertTrue("Ramp MRT adjustment should shift estimated power by ~6-8 W, got $diff", diff in 5.0..10.0)
    }

    @Test
    fun `tempo ride with alpha1 between 0_60 and 1_05 emits valid HRVT1 and null HRVT2 without extrapolation (TST-PER-044_3)`() {
        val points = mutableListOf<PathPoint>()
        // 20 minutes (t = 600..1800s): a1 starts at 1.00 and only drops to 0.62 (never reaches 0.45)
        for (t in 600L..1800L step 5L) {
            val fraction = (t - 600L).toDouble() / 1200.0
            val hr = 130 + (fraction * 30).toInt() // 130 to 160 bpm
            val a1 = 1.00 - fraction * 0.38 // 1.00 down to 0.62
            points.add(createPoint(timeSec = t, hr = hr, a1 = a1, art = 2.0))
        }

        val result = HrvThresholdEstimator.estimateThresholds(
            points = points,
            mode = HrvScatterIntensityMode.HEART_RATE
        )

        // HRVT1 span satisfied (>= 0.85 and <= 0.65)
        assertTrue(result.hrvt1.spanSatisfied)
        assertNotNull(result.hrvt1.intensity)
        assertEquals(ThresholdConfidence.HIGH, result.hrvt1.confidence)

        // HRVT2 span NOT satisfied (did not reach <= 0.45) -> MUST NOT extrapolate!
        assertFalse(result.hrvt2.spanSatisfied)
        assertNull(result.hrvt2.intensity)
        assertEquals(ThresholdConfidence.INSUFFICIENT_SPAN, result.hrvt2.confidence)
        assertEquals(R.string.hrv_threshold_diag_steady, result.hrvt2.diagnosticReasonRes)
    }

    @Test
    fun `anaerobic session with alpha1 between 0_40 and 0_58 emits valid HRVT2 and null HRVT1 (TST-PER-044_3)`() {
        val points = mutableListOf<PathPoint>()
        // a1 spans 0.40 to 0.58 (never reaches >= 0.85)
        for (t in 600L..1500L step 5L) {
            val fraction = (t - 600L).toDouble() / 900.0
            val hr = 165 + (fraction * 20).toInt()
            val a1 = 0.58 - fraction * 0.18 // 0.58 down to 0.40
            points.add(createPoint(timeSec = t, hr = hr, a1 = a1, art = 1.0))
        }

        val result = HrvThresholdEstimator.estimateThresholds(
            points = points,
            mode = HrvScatterIntensityMode.HEART_RATE
        )

        assertFalse(result.hrvt1.spanSatisfied)
        assertNull(result.hrvt1.intensity)
        assertEquals(ThresholdConfidence.INSUFFICIENT_SPAN, result.hrvt1.confidence)

        // HRVT2 span satisfied (>= 0.60 is NOT reached here: 0.58 < 0.60)
        assertFalse(result.hrvt2.spanSatisfied)
        assertNull(result.hrvt2.intensity)
    }

    @Test
    fun `three-tier confidence classification assigns HIGH MEDIUM and LOW correctly (TST-PER-044_4)`() {
        // 1. High Confidence: N >= 10, R² >= 0.70, Art <= 3.0%
        val highPoints = mutableListOf<PathPoint>()
        for (t in 600L..1800L step 5L) {
            val frac = (t - 600L).toDouble() / 1200.0
            highPoints.add(createPoint(timeSec = t, hr = (120 + frac * 60).toInt(), a1 = 0.95 - frac * 0.55, art = 2.0))
        }
        val highResult = HrvThresholdEstimator.estimateThresholds(highPoints)
        assertEquals(ThresholdConfidence.HIGH, highResult.hrvt1.confidence)
        assertNull(highResult.hrvt1.diagnosticReasonRes)

        // 2. Medium Confidence: N = 8, Art = 4.0%
        val medPoints = mutableListOf<PathPoint>()
        for (t in 600L..1080L step 5L) { // 8 minutes
            val frac = (t - 600L).toDouble() / 480.0
            medPoints.add(createPoint(timeSec = t, hr = (120 + frac * 60).toInt(), a1 = 0.95 - frac * 0.55, art = 4.0))
        }
        val medResult = HrvThresholdEstimator.estimateThresholds(medPoints)
        assertEquals(ThresholdConfidence.MEDIUM, medResult.hrvt1.confidence)

        // 3. Low Confidence due to short duration: N = 5 (< 8)
        val shortPoints = mutableListOf<PathPoint>()
        for (t in 600L..900L step 5L) { // 5 minutes
            val frac = (t - 600L).toDouble() / 300.0
            shortPoints.add(createPoint(timeSec = t, hr = (120 + frac * 60).toInt(), a1 = 0.95 - frac * 0.55, art = 2.0))
        }
        val shortResult = HrvThresholdEstimator.estimateThresholds(shortPoints)
        assertEquals(ThresholdConfidence.LOW, shortResult.hrvt1.confidence)
        assertEquals(R.string.hrv_threshold_diag_short_duration, shortResult.hrvt1.diagnosticReasonRes)
    }
}
