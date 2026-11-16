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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure-JVM unit tests for [PolarH10RunningDynamicsEstimator] (`ATT-8018`, `REQ-SET-116.2`, `TST-SET-118.1`, `TST-SET-118.2`).
 */
class PolarH10RunningDynamicsEstimatorTest {

    @Test
    fun testRunningPaces_150To195Spm_yieldDriftFreeVoAnd5MsGctBounds() {
        // TST-SET-118.1
        val voHistory = mutableListOf<Double?>()
        val gctHistory = mutableListOf<Double?>()
        val estimator = PolarH10RunningDynamicsEstimator(
            onVerticalOscillation = { voHistory.add(it) },
            onGroundContactTime = { gctHistory.add(it) }
        )

        // Pace 1: 150 steps/min, ~9.5 cm vertical oscillation -> sustained 12s run verifying zero drift
        feedRunningStream(
            estimator = estimator,
            startMs = 0L,
            durationMs = 12_000L,
            cadenceStepsPerMin = 150.0,
            trueVoCm = 9.5,
            tiltX = 0.0,
            tiltY = 0.0,
            tiltZ = 1.0
        )

        val vo150 = estimator.latestVerticalOscillationCm
        val gct150 = estimator.latestGroundContactTimeMs
        assertNotNull("VO must be populated at 150 spm", vo150)
        assertNotNull("GCT must be populated at 150 spm", gct150)
        assertTrue("VO at 150 spm must be within 5.0..13.0 cm (got $vo150)", vo150!! in 5.0..13.0)
        assertTrue("VO at 150 spm should track ~9.5 cm within tolerance (got $vo150)", abs(vo150 - 9.5) <= 1.8)
        assertTrue("GCT at 150 spm must be within 140..400 ms (got $gct150)", gct150!! in 140.0..400.0)

        // Verify drift-free stability across early vs late rolling estimates
        val nonNullVos = voHistory.filterNotNull()
        assertTrue("Must emit multiple VO updates during 12s run", nonNullVos.size >= 20)
        assertTrue(
            "Rolling VO must remain drift-free over sustained run (early=${nonNullVos[5]}, late=${nonNullVos.last()})",
            abs(nonNullVos.last() - nonNullVos[5]) <= 1.5
        )

        // Pace 2: 195 steps/min, ~7.2 cm vertical oscillation -> faster stride stays within physiological bounds
        estimator.reset()
        voHistory.clear()
        gctHistory.clear()
        feedRunningStream(
            estimator = estimator,
            startMs = 0L,
            durationMs = 10_000L,
            cadenceStepsPerMin = 195.0,
            trueVoCm = 7.2,
            tiltX = 0.0,
            tiltY = 0.0,
            tiltZ = 1.0
        )

        val vo195 = estimator.latestVerticalOscillationCm
        val gct195 = estimator.latestGroundContactTimeMs
        assertNotNull(vo195)
        assertNotNull(gct195)
        assertTrue("VO at 195 spm must be within 5.0..13.0 cm (got $vo195)", vo195!! in 5.0..13.0)
        assertTrue("VO at 195 spm should track ~7.2 cm within tolerance (got $vo195)", abs(vo195 - 7.2) <= 1.8)
        assertTrue("GCT at 195 spm must be within 140..400 ms (got $gct195)", gct195!! in 140.0..400.0)
        assertTrue("Higher cadence (195 spm) GCT ($gct195) should be <= slower cadence (150 spm) GCT ($gct150)", gct195 <= gct150)
    }

    @Test
    fun testTiltedVerticalAlignment_recoversConsistentVoAndGctUnderChestRotation() {
        // TST-SET-118.1
        val uprightEstimator = PolarH10RunningDynamicsEstimator()
        feedRunningStream(
            estimator = uprightEstimator,
            startMs = 0L,
            durationMs = 10_000L,
            cadenceStepsPerMin = 172.0,
            trueVoCm = 8.4,
            tiltX = 0.0,
            tiltY = 0.0,
            tiltZ = 1.0
        )

        // Rotated / leaned chest strap orientation (~35 deg lean + inversion)
        val tiltedEstimator = PolarH10RunningDynamicsEstimator()
        val gx = 0.42
        val gy = -0.35
        val gz = -0.8371977 // unit vector length = 1.0
        feedRunningStream(
            estimator = tiltedEstimator,
            startMs = 0L,
            durationMs = 10_000L,
            cadenceStepsPerMin = 172.0,
            trueVoCm = 8.4,
            tiltX = gx,
            tiltY = gy,
            tiltZ = gz
        )

        val uprightVo = uprightEstimator.latestVerticalOscillationCm!!
        val tiltedVo = tiltedEstimator.latestVerticalOscillationCm!!
        val uprightGct = uprightEstimator.latestGroundContactTimeMs!!
        val tiltedGct = tiltedEstimator.latestGroundContactTimeMs!!

        assertTrue("Tilted VO ($tiltedVo) must match upright VO ($uprightVo)", abs(tiltedVo - uprightVo) <= 0.5)
        assertTrue("Tilted GCT ($tiltedGct) must match upright GCT ($uprightGct)", abs(tiltedGct - uprightGct) <= 10.0)
    }

    @Test
    fun testWalkingBelow130Spm_suppressesGctCleanly() {
        // TST-SET-118.2
        val gctEvents = mutableListOf<Double?>()
        val estimator = PolarH10RunningDynamicsEstimator(
            onGroundContactTime = { gctEvents.add(it) }
        )

        // Start running at 170 spm -> produces valid GCT
        feedRunningStream(
            estimator = estimator,
            startMs = 0L,
            durationMs = 6_000L,
            cadenceStepsPerMin = 170.0,
            trueVoCm = 8.5,
            tiltX = 0.0,
            tiltY = 0.0,
            tiltZ = 1.0
        )
        assertNotNull("Active running must emit valid GCT", estimator.latestGroundContactTimeMs)

        // Slow to walking (< 130 spm = 108 spm) -> must cleanly suppress GCT to null
        estimator.setCurrentCadenceStepsPerMin(108.0)
        feedRunningStream(
            estimator = estimator,
            startMs = 6_005L,
            durationMs = 5_000L,
            cadenceStepsPerMin = 108.0,
            trueVoCm = 5.2,
            tiltX = 0.0,
            tiltY = 0.0,
            tiltZ = 1.0
        )

        assertNull("Walking (< 130 spm) must suppress GCT to null", estimator.latestGroundContactTimeMs)
        assertTrue("Suppression callback must emit null", gctEvents.lastOrNull() == null)
    }

    @Test
    fun testStandstillAndLowRateFallback_clearsDynamicsWithoutPhantoms() {
        // TST-SET-118.2
        val estimator = PolarH10RunningDynamicsEstimator()
        feedRunningStream(
            estimator = estimator,
            startMs = 0L,
            durationMs = 6_000L,
            cadenceStepsPerMin = 175.0,
            trueVoCm = 8.0,
            tiltX = 0.0,
            tiltY = 0.0,
            tiltZ = 1.0
        )
        assertNotNull(estimator.latestVerticalOscillationCm)
        assertNotNull(estimator.latestGroundContactTimeMs)

        // 4 seconds of standstill -> clears VO and GCT cleanly
        var tMs = 6_005L
        while (tMs <= 10_000L) {
            estimator.onAccSample(0, 0, 1000, tMs)
            tMs += 5L
        }
        assertNull("Standstill must clear VO to null", estimator.latestVerticalOscillationCm)
        assertNull("Standstill must clear GCT to null", estimator.latestGroundContactTimeMs)

        // Lower sample rate (50 Hz = 20 ms dt) must not emit 200 Hz dynamics
        val lowRateEstimator = PolarH10RunningDynamicsEstimator()
        for (i in 0 until 400) {
            val t = i * 20L
            val aMg = (1000.0 + 700.0 * sin(2.0 * PI * (170.0 / 60.0) * (t / 1000.0))).roundToInt()
            lowRateEstimator.onAccSample(0, 0, aMg, t)
        }
        assertNull("50 Hz fallback stream must suppress 200 Hz VO", lowRateEstimator.latestVerticalOscillationCm)
        assertNull("50 Hz fallback stream must suppress 200 Hz GCT", lowRateEstimator.latestGroundContactTimeMs)
    }

    private fun feedRunningStream(
        estimator: PolarH10RunningDynamicsEstimator,
        startMs: Long,
        durationMs: Long,
        cadenceStepsPerMin: Double,
        trueVoCm: Double,
        tiltX: Double,
        tiltY: Double,
        tiltZ: Double
    ) {
        val fStepHz = cadenceStepsPerMin / 60.0
        val omega = 2.0 * PI * fStepHz
        val amplitudeMps2 = (trueVoCm / 100.0 / 2.0) * omega * omega
        val amplitudeMg = (amplitudeMps2 / 9.80665) * 1000.0

        var tMs = startMs
        while (tMs <= startMs + durationMs) {
            val tSec = tMs / 1000.0
            val aTotalMg = 1000.0 + amplitudeMg * cos(omega * tSec)
            estimator.onAccSample(
                (aTotalMg * tiltX).roundToInt(),
                (aTotalMg * tiltY).roundToInt(),
                (aTotalMg * tiltZ).roundToInt(),
                tMs
            )
            tMs += 5L // 200 Hz
        }
    }
}
