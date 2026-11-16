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

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure-JVM unit tests for [PolarLiveRunningCadenceEstimator] (`ATT-8017`, `REQ-SET-114`, `TST-SET-116`).
 */
class PolarLiveRunningCadenceEstimatorTest {

    @Test
    fun testH10_170StepsPerMin_outputsCanonical85StridesPerMinAndMonotonicStrides() {
        // TST-SET-116.1
        // TST-SET-116.5
        val emittedCadences = mutableListOf<Double>()
        val emittedStrides = mutableListOf<Int>()
        val estimator = PolarLiveRunningCadenceEstimator(
            mode = PolarLiveRunningCadenceEstimator.Mode.H10_CHEST,
            onCadence = { emittedCadences.add(it) },
            onStride = { emittedStrides.add(it) }
        )

        val stepFreqHz = 170.0 / 60.0 // 2.8333 steps/s -> 85.0 strides/min
        feedH10Sine(estimator, startMs = 0L, durationMs = 12_000L, sampleRateHz = 100, stepFreqHz = stepFreqHz, amplitudeMg = 650.0)

        assertTrue("Expected >= 10 1-Hz emissions over 12s, got ${emittedCadences.size}", emittedCadences.size >= 10)
        val steadyCadence = emittedCadences.last()
        assertEquals("170 steps/min must publish 85.0 strides/min", 85.0, steadyCadence, 1.0)
        assertTrue("First stride must start at 1", emittedStrides.first() == 1)
        assertTrue("Expected strictly monotonic stride increments", emittedStrides.zipWithNext().all { (a, b) -> b == a + 1 })
        assertTrue("Expected ~15-18 strides over 12s at 85 spm, got ${emittedStrides.last()}", emittedStrides.last() in 14..18)
    }

    @Test
    fun testH10_bandPassAndAutocorrelation_rejectsDriftImpactNoiseAnd2fHarmonics() {
        // TST-SET-116.1
        val cadences = mutableListOf<Double>()
        val estimator = PolarLiveRunningCadenceEstimator(
            mode = PolarLiveRunningCadenceEstimator.Mode.H10_CHEST,
            onCadence = { cadences.add(it) }
        )

        // 140 steps/min (70 strides/min) + 0.2 Hz drift + 18 Hz impact chatter
        feedH10Adversarial(
            estimator = estimator,
            durationMs = 10_000L,
            sampleRateHz = 100,
            stepFreqHz = 140.0 / 60.0,
            harmonic2fRatio = 0.0,
            driftAmplitudeMg = 250.0,
            noiseAmplitudeMg = 220.0
        )
        assertEquals("140 steps/min with drift & noise -> 70.0 strides/min", 70.0, cadences.last(), 1.2)

        // Strong 2f step harmonic inside 1.2-4.0 Hz band (116 steps/min = 58 strides/min; 2f = 3.867 Hz)
        estimator.reset()
        cadences.clear()
        feedH10Adversarial(
            estimator = estimator,
            durationMs = 10_000L,
            sampleRateHz = 100,
            stepFreqHz = 116.0 / 60.0,
            harmonic2fRatio = 0.85,
            driftAmplitudeMg = 200.0,
            noiseAmplitudeMg = 150.0
        )
        assertEquals("Strong 2f harmonic must not double reported cadence", 58.0, cadences.last(), 1.5)

        // 200 Hz high-rate running dynamics at 190 steps/min -> 95.0 strides/min
        estimator.reset()
        cadences.clear()
        feedH10Sine(estimator, startMs = 0L, durationMs = 10_000L, sampleRateHz = 200, stepFreqHz = 190.0 / 60.0, amplitudeMg = 700.0)
        assertEquals("190 steps/min at 200 Hz -> 95.0 strides/min", 95.0, cadences.last(), 1.0)
    }

    @Test
    fun testVeritySense_52HzArmSwingGyroAccFusion_rotationInvariantAndAccurate() {
        // TST-SET-116.2
        val cadences = mutableListOf<Double>()
        val strides = mutableListOf<Int>()
        val estimator = PolarLiveRunningCadenceEstimator(
            mode = PolarLiveRunningCadenceEstimator.Mode.VERITY_SENSE_ARM,
            onCadence = { cadences.add(it) },
            onStride = { strides.add(it) }
        )

        // Arbitrary rotated 3D mounting axis on arm/wrist at 90.0 strides/min (1.50 Hz arm swing)
        val ax = 0.48
        val ay = -0.71
        val az = 0.51429563 // unit vector length ~ 1.0
        feedVerityInterleaved(
            estimator = estimator,
            startMs = 0L,
            durationMs = 10_000L,
            strideFreqHz = 1.50,
            axisX = ax,
            axisY = ay,
            axisZ = az,
            gyroPeakDps = 180.0
        )

        assertEquals("1.50 Hz arm swing -> 90.0 strides/min", 90.0, cadences.last(), 1.0)
        assertTrue("Verity must emit 1 stride per full arm cycle (~13-16), got ${strides.lastOrNull()}", (strides.lastOrNull() ?: 0) in 12..16)

        // Check slower running (70.0 strides/min) under different orientation
        estimator.reset()
        cadences.clear()
        feedVerityInterleaved(
            estimator = estimator,
            startMs = 0L,
            durationMs = 10_000L,
            strideFreqHz = 70.0 / 60.0,
            axisX = -0.35,
            axisY = 0.25,
            axisZ = 0.9027735,
            gyroPeakDps = 150.0
        )
        assertEquals("70.0 strides/min arm swing -> 70.0 strides/min", 70.0, cadences.last(), 1.2)
    }

    @Test
    fun testCadenceEmission_steady1HzUpdatesAndDeterministicZeroingWithin3sStandstill() {
        // TST-SET-116.3
        val timeline = mutableListOf<Pair<Long, Double>>()
        val strideTimestamps = mutableListOf<Long>()
        var currentTs = 0L
        val estimator = PolarLiveRunningCadenceEstimator(
            mode = PolarLiveRunningCadenceEstimator.Mode.H10_CHEST,
            onCadence = { cadence -> timeline.add(currentTs to cadence) },
            onStride = { strideTimestamps.add(currentTs) }
        )

        // Active run: 0..8000 ms at 85 strides/min
        val dtMs = 10L
        var tMs = 0L
        while (tMs <= 8_000L) {
            currentTs = tMs
            val tSec = tMs / 1000.0
            val zMg = (1000.0 + 650.0 * sin(2.0 * PI * (170.0 / 60.0) * tSec)).roundToInt()
            estimator.onAccSample(0, 0, zMg, tMs)
            tMs += dtMs
        }

        val activeEmissions = timeline.filter { it.first in 3_000L..8_000L }
        assertTrue("Expected ~5-6 1-Hz active emissions in 3..8s, got ${activeEmissions.size}", activeEmissions.size in 5..6)
        assertTrue("Active emissions must stay near 85.0 spm", activeEmissions.all { it.second in 83.0..87.0 })
        val stridesBeforeStop = estimator.getTotalStrides()

        // Standstill: 8010..13000 ms (flat gravity + tiny 3 mG noise)
        while (tMs <= 13_000L) {
            currentTs = tMs
            val jitter = ((tMs / 10L) % 3L - 1L).toInt() * 3
            estimator.onAccSample(jitter, -jitter, 1000 + jitter, tMs)
            tMs += dtMs
        }

        val zeroTick = timeline.firstOrNull { it.first > 8_000L && it.second == 0.0 }
        assertTrue("Standstill must zero cadence to 0.0", zeroTick != null)
        assertTrue(
            "Zeroing must happen within 3 s of standstill (got ${zeroTick!!.first - 8_000L} ms)",
            zeroTick.first - 8_000L <= 3_000L
        )
        assertEquals("Zero phantom strides allowed after standstill begins", stridesBeforeStop, estimator.getTotalStrides())
        assertEquals(0.0, estimator.latestCadenceStridesPerMin, 0.0)
    }

    @Test
    fun testBoundaryCases_slowWalkSyntheticTimestampsAndReset() {
        // TST-SET-116.1
        // TST-SET-116.3
        val cadences = mutableListOf<Double>()
        val estimator = PolarLiveRunningCadenceEstimator(
            mode = PolarLiveRunningCadenceEstimator.Mode.H10_CHEST,
            onCadence = { cadences.add(it) }
        )

        // Slow walk: 100 steps/min = 50.0 strides/min using synthetic timestamp=0L
        for (i in 0 until 1_000) {
            val tSec = i * 0.01
            val zMg = (1000.0 + 350.0 * sin(2.0 * PI * (100.0 / 60.0) * tSec)).roundToInt()
            estimator.onAccSample(0, 0, zMg, 0L)
        }
        assertEquals("Slow walk 100 steps/min -> 50.0 strides/min", 50.0, cadences.last(), 1.5)

        estimator.reset()
        assertEquals(0, estimator.getTotalStrides())
        assertEquals(0.0, estimator.latestCadenceStridesPerMin, 0.0)
    }

    private fun feedH10Sine(
        estimator: PolarLiveRunningCadenceEstimator,
        startMs: Long,
        durationMs: Long,
        sampleRateHz: Int,
        stepFreqHz: Double,
        amplitudeMg: Double
    ) {
        val stepMs = 1000L / sampleRateHz
        var tMs = startMs
        while (tMs <= startMs + durationMs) {
            val tSec = tMs / 1000.0
            val zMg = (1000.0 + amplitudeMg * sin(2.0 * PI * stepFreqHz * tSec)).roundToInt()
            estimator.onAccSample(0, 0, zMg, tMs)
            tMs += stepMs
        }
    }

    private fun feedH10Adversarial(
        estimator: PolarLiveRunningCadenceEstimator,
        durationMs: Long,
        sampleRateHz: Int,
        stepFreqHz: Double,
        harmonic2fRatio: Double,
        driftAmplitudeMg: Double,
        noiseAmplitudeMg: Double
    ) {
        val stepMs = 1000L / sampleRateHz
        var tMs = 0L
        while (tMs <= durationMs) {
            val tSec = tMs / 1000.0
            val fundamental = 600.0 * sin(2.0 * PI * stepFreqHz * tSec)
            val harmonic2f = 600.0 * harmonic2fRatio * sin(2.0 * PI * (2.0 * stepFreqHz) * tSec)
            val drift = driftAmplitudeMg * sin(2.0 * PI * 0.2 * tSec)
            val highFreqNoise = noiseAmplitudeMg * sin(2.0 * PI * 18.0 * tSec)
            val zMg = (1000.0 + fundamental + harmonic2f + drift + highFreqNoise).roundToInt()
            estimator.onAccSample(0, 0, zMg, tMs)
            tMs += stepMs
        }
    }

    private fun feedVerityInterleaved(
        estimator: PolarLiveRunningCadenceEstimator,
        startMs: Long,
        durationMs: Long,
        strideFreqHz: Double,
        axisX: Double,
        axisY: Double,
        axisZ: Double,
        gyroPeakDps: Double
    ) {
        val periodNsStepMs = 1000.0 / 52.0
        var idx = 0
        while (true) {
            val tMs = startMs + (idx * periodNsStepMs).roundToInt()
            if (tMs > startMs + durationMs) break
            val tSec = tMs / 1000.0
            val phase = 2.0 * PI * strideFreqHz * tSec
            val wave = sin(phase)
            val accGravityX = (1000.0 * axisX * cos(phase) * 0.3).roundToInt()
            val accGravityY = (1000.0 * axisY * cos(phase) * 0.3).roundToInt()
            val accGravityZ = (1000.0 + 400.0 * abs(wave) * axisZ * 0.2).roundToInt()
            estimator.onAccSample(accGravityX, accGravityY, accGravityZ, tMs)
            estimator.onGyroSample(
                (gyroPeakDps * axisX * wave).toFloat(),
                (gyroPeakDps * axisY * wave).toFloat(),
                (gyroPeakDps * axisZ * wave).toFloat(),
                tMs
            )
            idx++
        }
    }

    private fun abs(value: Double): Double = if (value < 0) -value else value
}
