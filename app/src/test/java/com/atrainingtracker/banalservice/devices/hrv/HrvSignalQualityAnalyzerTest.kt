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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Unit tests validating [HrvSignalQualityAnalyzer] pursuant to REQ-TRK-067 and TST-TRK-067.
 */
class HrvSignalQualityAnalyzerTest {

    @Test
    fun testInsufficientDataReturnsInsufficientDataStatus() {
        val shortSeries = List(25) { 800.0 }
        val result = HrvSignalQualityAnalyzer.analyze(shortSeries)

        assertEquals(HrvSignalQualityStatus.INSUFFICIENT_DATA, result.status)
        assertEquals(25, result.beatCount)
        assertTrue(result.details?.contains("Insufficient data") == true)
    }

    @Test
    fun testHighArtifactRateReturnsHighArtifactsStatus() {
        val series = List(40) { 800.0 + (it % 2) * 20.0 }
        val result = HrvSignalQualityAnalyzer.analyze(series, artifactRatePercent = 6.5)

        assertEquals(HrvSignalQualityStatus.HIGH_ARTIFACTS, result.status)
        assertEquals(40, result.beatCount)
        assertTrue(result.details?.contains("High artifact rate") == true)
    }

    @Test
    fun testQuantizedSyntheticDetection() {
        // Stepped constant-BPM stream where 130 bpm -> RR = 461.53 ms
        // Many consecutive beats have identical integer-derived RR intervals (diff = 0.0 ms)
        val series = mutableListOf<Double>()
        for (step in 0 until 10) {
            val rr = 500.0 + step * 10.0
            // 6 identical beats per plateau (diff = 0)
            repeat(6) { series.add(rr) }
        }
        assertEquals(60, series.size)

        val result = HrvSignalQualityAnalyzer.analyze(series)
        assertEquals(HrvSignalQualityStatus.QUANTIZED_SYNTHETIC, result.status)
        assertNotNull(result.zeroDiffRate)
        assertTrue("Zero-diff rate must exceed 0.55", result.zeroDiffRate!! > 0.55)
    }

    @Test
    fun testEmpiricalSmoothedSeriesDiscordanceDetected() {
        // Magene-style moving average: rMSSD ~ 5.5 ms with DFA a1 = 1.45
        // Under human sinus rhythm, low rMSSD (<10 ms) with high a1 (>1.35) is physiologically discordant
        val series = List(64) { 800.0 + it * 2.0 }
        val result = HrvSignalQualityAnalyzer.analyze(
            rawRrIntervalsMs = series,
            rmssdMs = 5.5,
            dfaAlpha1 = 1.45,
            artifactRatePercent = 0.0
        )

        assertEquals(HrvSignalQualityStatus.SMOOTHED_STREAM, result.status)
        assertTrue(result.details?.contains("Smoothed stream") == true)
    }

    @Test
    fun testEmpiricalSmoothedSeriesLowTprDetected() {
        // Monotonic moving average ramp: TPR < 0.20 alongside DFA a1 > 1.30
        // E.g., slow 32-beat ramp up, 32-beat ramp down (only 1 turning point in 64 beats)
        val series = mutableListOf<Double>()
        for (i in 0 until 32) series.add(750.0 + i * 3.0)
        for (i in 0 until 32) series.add(846.0 - i * 3.0)

        val result = HrvSignalQualityAnalyzer.analyze(
            rawRrIntervalsMs = series,
            rmssdMs = 12.0, // above 10ms discordance threshold
            dfaAlpha1 = 1.38, // high a1
            artifactRatePercent = 0.0
        )

        assertEquals(HrvSignalQualityStatus.SMOOTHED_STREAM, result.status)
        assertNotNull(result.turningPointRatio)
        assertTrue("TPR must be < 0.20", result.turningPointRatio!! < 0.20)
    }

    @Test
    fun testAuthenticHighIntensityExerciseIsPreservedAsValid() {
        // REQ-TRK-067.5: Authentic exercise with rMSSD ~ 4.5 ms, a1 ~ 0.48, and stochastic noise (TPR ~ 0.60)
        // MUST NEVER be flagged as SMOOTHED_STREAM or QUANTIZED_SYNTHETIC
        val rng = Random(12345L)
        val series = mutableListOf<Double>()
        var currentRr = 380.0
        for (i in 0 until 64) {
            val step = (rng.nextDouble() - 0.5) * 8.0 // +/- 4 ms jitter
            currentRr = (currentRr + step).coerceIn(360.0, 400.0)
            series.add(currentRr)
        }

        val result = HrvSignalQualityAnalyzer.analyze(
            rawRrIntervalsMs = series,
            rmssdMs = 4.5,
            dfaAlpha1 = 0.48, // sympathetic white-noise regime
            artifactRatePercent = 0.0
        )

        assertEquals(HrvSignalQualityStatus.VALID, result.status)
    }

    @Test
    fun testAuthenticRestingSinusIsPreservedAsValid() {
        // REQ-TRK-067.5: Authentic rest with rMSSD ~ 42 ms, a1 ~ 1.12, normal RSA turning points
        val rng = Random(54321L)
        val series = mutableListOf<Double>()
        for (i in 0 until 80) {
            // Respiratory sinus oscillation + small noise
            val rsa = 40.0 * Math.sin(2.0 * Math.PI * i / 8.0)
            val noise = (rng.nextDouble() - 0.5) * 10.0
            series.add(900.0 + rsa + noise)
        }

        val result = HrvSignalQualityAnalyzer.analyze(
            rawRrIntervalsMs = series,
            rmssdMs = 42.0,
            dfaAlpha1 = 1.12,
            artifactRatePercent = 1.2
        )

        assertEquals(HrvSignalQualityStatus.VALID, result.status)
        assertNotNull(result.turningPointRatio)
        assertTrue("Resting RSA turning-point ratio must be >= 0.20", result.turningPointRatio!! >= 0.20)
    }

    @Test
    fun testAnalyzeBeatsConvenienceOverload() {
        val beats = List(40) { index ->
            FilteredRrBeat(
                beatTimestampMs = index * 800L,
                rawRrMs = 800.0 + (index % 4) * 5.0,
                correctedRrMs = 800.0 + (index % 4) * 5.0,
                artifactFlag = 0
            )
        }

        val result = HrvSignalQualityAnalyzer.analyzeBeats(beats)
        assertEquals(HrvSignalQualityStatus.VALID, result.status)
        assertEquals(40, result.beatCount)
    }

    @Test
    fun testAuthenticPolarH10RestingStreamPreservedAsValid() {
        // ATT-7533 / REQ-TRK-067.5: Authentic Polar H10 ECG resting stream in evening/sympathetic state
        // rMSSD = 4.5 ms (< 10 ms), DFA a1 = 1.52 (> 1.35), SDNN = 8.7 ms, TPR = 0.38 (>= 0.28).
        // Respiratory beat-to-beat sign reversals are authentic; must NOT be flagged as SMOOTHED_STREAM.
        val rng = Random(424242L)
        val series = mutableListOf<Double>()
        var current = 720.0
        for (i in 0 until 70) {
            val delta = (rng.nextDouble() - 0.5) * 6.0
            current = (current + delta).coerceIn(700.0, 750.0)
            series.add(current)
        }

        val tpr = HrvSignalQualityAnalyzer.computeTurningPointRatio(series)
        assertNotNull(tpr)
        assertTrue("Authentic ECG must retain turning-point ratio >= 0.28, actual=$tpr", tpr!! >= 0.28)

        val result = HrvSignalQualityAnalyzer.analyze(
            rawRrIntervalsMs = series,
            rmssdMs = 4.5,
            dfaAlpha1 = 1.52,
            artifactRatePercent = 0.0
        )

        assertEquals(HrvSignalQualityStatus.VALID, result.status)
    }
}
