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

import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Pure-JVM unit tests for [HrvRollingEngine] covering rolling unbiased `N - 1` `HRV_SDNN`,
 * `MIN_SDNN_VALID_BEATS = 10`, configurable artifact-rate gating, 120s drain-to-null, and
 * empty lookahead-trimmed active window parity (`REQ-HRV-050.2` / `TST-HRV-050.2`).
 */
class HrvRollingEngineTest {

    @Test
    fun testRollingSdnn_computesUnbiasedSampleStdDevOver120sWindow_TST_HRV_050_2() {
        // REQ-HRV-050.2 / TST-HRV-050.2
        val engine = HrvRollingEngine(context = null, artifactGateThresholdPercent = 5.0)
        assertNull("Rule 52: evaluate must return null before RR beats arrive", engine.evaluate(1_000_000L))

        val baseTs = 1_000_000L
        // 40 alternating beats (780 ms, 820 ms) -> exact mean = 800 ms, sample variance over N beats = 400 * N / (N - 1)
        val totalBeats = 40
        for (i in 0 until totalBeats) {
            val rr = if (i % 2 == 0) 780.0 else 820.0
            engine.onRrBeat(RrBeatEvent(baseTs + i * 800L, rr, i, ProtocolType.BLE))
        }

        val nowMs = baseTs + totalBeats * 800L
        val snapshot = engine.evaluate(nowMs)
        assertNotNull(snapshot)
        assertFalse(snapshot!!.isDrained)
        assertEquals(0.0, snapshot.artifactRatePercent!!, 0.001)
        assertEquals(0.0, engine.latestArtifactRatePercent!!, 0.001)
        assertNotNull("Rolling SDNN must be computed for >= 10 valid non-artifact beats", snapshot.sdnnMs)

        // Lookahead trims 5 trailing beats -> 35 alternating beats of +/-20 ms around mean (~800 ms)
        val n = 35
        val values = (0 until n).map { if (it % 2 == 0) 780.0 else 820.0 }
        val mean = values.average()
        val expectedSdnn = sqrt(values.sumOf { (it - mean) * (it - mean) } / (n - 1).toDouble())
        assertEquals(expectedSdnn, snapshot.sdnnMs!!, 0.05)
    }

    @Test
    fun testRollingSdnn_suppressedBelowMinBeatsAndAboveArtifactGateAndOnDrain_TST_HRV_050_2() {
        // REQ-HRV-050.2 / TST-HRV-050.2
        val baseTs = 2_000_000L
        val engine = HrvRollingEngine(context = null, artifactGateThresholdPercent = 4.0)

        // 1. Fewer than 10 valid filtered beats (12 beats minus 5 lookahead = 7 beats < 10) -> SDNN null
        for (i in 0 until 12) {
            engine.onRrBeat(RrBeatEvent(baseTs + i * 800L, 800.0 + (i % 2) * 15.0, i, ProtocolType.BLE))
        }
        val shortSnap = engine.evaluate(baseTs + 12 * 800L)
        assertNotNull(shortSnap)
        assertNull("SDNN must be null with < 10 valid beats", shortSnap!!.sdnnMs)

        // 2. High artifact rate (> 4.0% gate) -> both DFA alpha 1 and SDNN suppressed to null
        val noisyEngine = HrvRollingEngine(context = null, artifactGateThresholdPercent = 4.0)
        for (i in 0 until 80) {
            val rr = if (i % 10 == 5) 420.0 else 800.0 + (i % 2) * 10.0
            noisyEngine.onRrBeat(RrBeatEvent(baseTs + i * 800L, rr, i, ProtocolType.BLE))
        }
        val noisySnap = noisyEngine.evaluate(baseTs + 80 * 800L)
        assertNotNull(noisySnap)
        assertTrue("Artifact rate must exceed 4.0% gate", (noisySnap!!.artifactRatePercent ?: 0.0) > 4.0)
        assertNull("DFA alpha 1 must be gated to null", noisySnap.dfaAlpha1)
        assertNull("SDNN must be gated to null when artifact rate exceeds threshold", noisySnap.sdnnMs)

        // 3. +150s drain (> 120s active window, < 180s context) -> isDrained = true, all metrics null
        val drainedSnap = engine.evaluate(baseTs + 12 * 800L + 150_000L)
        assertNotNull(drainedSnap)
        assertTrue(drainedSnap!!.isDrained)
        assertNull(drainedSnap.rmssdMs)
        assertNull(drainedSnap.dfaAlpha1)
        assertNull(drainedSnap.artifactRatePercent)
        assertNull(drainedSnap.sdnnMs)
        assertNull(engine.latestArtifactRatePercent)
        assertEquals(HrvSignalQualityStatus.INSUFFICIENT_DATA, drainedSnap.signalQualityResult.status)
    }

    @Test
    fun testEmptyLookaheadTrimmedActiveWindow_emitsZeroArtifactRateAndNullSdnn_TST_HRV_050_2() {
        // REQ-HRV-050.2 / TST-HRV-050.2
        val baseTs = 3_000_000L
        val nowMs = baseTs + 180_000L
        val engine = HrvRollingEngine(context = null, artifactGateThresholdPercent = 5.0)

        for (i in 0 until 50) {
            engine.onRrBeat(RrBeatEvent(baseTs + 10_000L + i * 800L, 800.0, i, ProtocolType.BLE))
        }
        for (k in 0 until 4) {
            engine.onRrBeat(RrBeatEvent(nowMs - (4 - k) * 800L, 800.0, 50 + k, ProtocolType.BLE))
        }

        val snap = engine.evaluate(nowMs)
        assertNotNull(snap)
        assertFalse(snap!!.isDrained)
        assertEquals(0.0, snap.artifactRatePercent!!, 0.001)
        assertNull(snap.dfaAlpha1)
        assertNull(snap.rmssdMs)
        assertNull(snap.sdnnMs)
    }

    @Test
    fun testOpticalPpiSuppressesDfaAlpha1_preservesRmssdSdnnOpticalQuality_andRecoversNonStickilyOnEcg_TST_HRV_051_3() {
        // REQ-HRV-051.3 / TST-HRV-051.3
        val baseTs = 4_000_000L
        val engine = HrvRollingEngine(context = null, artifactGateThresholdPercent = 5.0)

        // 1. Feed 110 optical PPI beats (harmonic wave with smooth variability that would compute DFA a1 on ECG)
        for (i in 0 until 110) {
            val rr = 800.0 + 35.0 * kotlin.math.sin(2.0 * kotlin.math.PI * i / 16.0) +
                15.0 * kotlin.math.sin(2.0 * kotlin.math.PI * i / 64.0)
            engine.onRrBeat(
                RrBeatEvent(
                    beatTimestampMs = baseTs + i * 800L,
                    rrMs = rr,
                    rawCounter = i,
                    protocol = ProtocolType.BLE,
                    isOpticalPpi = true
                )
            )
        }
        val opticalNow = baseTs + 110 * 800L
        val opticalSnap = engine.evaluate(opticalNow)
        assertNotNull(opticalSnap)
        assertNull("Optical PPI must suppress DFA alpha 1 to null", opticalSnap!!.dfaAlpha1)
        assertNotNull("Optical PPI must still compute rolling rMSSD", opticalSnap.rmssdMs)
        assertNotNull("Optical PPI must still compute rolling SDNN", opticalSnap.sdnnMs)
        assertEquals(0.0, opticalSnap.artifactRatePercent!!, 0.001)
        assertEquals(
            "Optical signal quality must bypass ECG-only SMOOTHED_STREAM warnings",
            HrvSignalQualityStatus.VALID,
            opticalSnap.signalQualityResult.status
        )

        // 2. Non-sticky recovery: once ECG beats replace the active 120s window (+130s later), DFA alpha 1 resumes
        val ecgStart = opticalNow + 130_000L
        for (i in 0 until 110) {
            val rr = 800.0 + 35.0 * kotlin.math.sin(2.0 * kotlin.math.PI * i / 16.0) +
                15.0 * kotlin.math.sin(2.0 * kotlin.math.PI * i / 64.0)
            engine.onRrBeat(
                RrBeatEvent(
                    beatTimestampMs = ecgStart + i * 800L,
                    rrMs = rr,
                    rawCounter = 200 + i,
                    protocol = ProtocolType.BLE,
                    isOpticalPpi = false
                )
            )
        }
        val ecgSnap = engine.evaluate(ecgStart + 110 * 800L)
        assertNotNull(ecgSnap)
        assertNotNull("ECG stream must automatically resume DFA alpha 1 after optical beats age out", ecgSnap!!.dfaAlpha1)
        assertTrue(ecgSnap.dfaAlpha1!! > 0.0)
    }
}
