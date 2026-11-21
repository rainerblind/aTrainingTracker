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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import com.atrainingtracker.banalservice.sensor.HrHealthTier
import com.atrainingtracker.trainingtracker.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [PolarHrStreamHealthMonitor] (`REQ-SET-126`, `TST-SET-128`, `ATT-9345`).
 */
class PolarHrStreamHealthMonitorTest {

    @Test
    fun shouldStreamVerityPpi_allowsRestingModesAndBlocksWorkoutModes() {
        // Verifies REQ-SET-126 / TST-SET-128
        assertTrue(PolarHrStreamHealthMonitor.shouldStreamVerityPpi(isVeritySense = true, TrackingMode.READY))
        assertTrue(PolarHrStreamHealthMonitor.shouldStreamVerityPpi(isVeritySense = true, TrackingMode.IDLE))
        assertFalse(PolarHrStreamHealthMonitor.shouldStreamVerityPpi(isVeritySense = true, TrackingMode.TRACKING))
        assertFalse(PolarHrStreamHealthMonitor.shouldStreamVerityPpi(isVeritySense = true, TrackingMode.PAUSED))
        assertFalse(PolarHrStreamHealthMonitor.shouldStreamVerityPpi(isVeritySense = false, TrackingMode.READY))
    }

    @Test
    fun onHrSample_zeroAndLostContactRejectedAndClearedAfterTenSeconds() {
        // Verifies REQ-SET-126 / TST-SET-128
        val monitor = PolarHrStreamHealthMonitor(isOpticalSensor = true)

        val valid = monitor.onHrSample(
            hrBpm = 142,
            contactSupported = true,
            contactStatus = true,
            nowMs = 1_000L,
            isWorkoutActive = true,
            opticalArtifactRatePercent = null
        )
        assertTrue(valid.acceptForSensor)
        assertEquals(HrHealthTier.HEALTHY, monitor.getHrHealthTier(1_000L))

        val zeroImmediate = monitor.onHrSample(
            hrBpm = 0,
            contactSupported = true,
            contactStatus = true,
            nowMs = 2_000L,
            isWorkoutActive = true,
            opticalArtifactRatePercent = null
        )
        assertFalse("hr == 0 must not be accepted (nor disarm watchdog)", zeroImmediate.acceptForSensor)
        assertFalse("hr == 0 must not clear before 10s", zeroImmediate.shouldClearHr)

        val lostContact9s = monitor.onHrSample(
            hrBpm = 142,
            contactSupported = true,
            contactStatus = false,
            nowMs = 11_999L,
            isWorkoutActive = true,
            opticalArtifactRatePercent = null
        )
        assertFalse("lost skin contact must be rejected", lostContact9s.acceptForSensor)
        assertFalse(lostContact9s.shouldClearHr)

        val lostContact10s = monitor.onHrSample(
            hrBpm = 142,
            contactSupported = true,
            contactStatus = false,
            nowMs = 12_000L,
            isWorkoutActive = true,
            opticalArtifactRatePercent = null
        )
        assertFalse(lostContact10s.acceptForSensor)
        assertTrue("10s of zero/lost-contact must clear HR to null", lostContact10s.shouldClearHr)
        assertTrue("10s of zero/lost-contact must also clear latched RR", lostContact10s.shouldClearRr)
        assertTrue(lostContact10s.healthTierChanged)
        assertEquals(HrHealthTier.STALE_OR_NULL, monitor.getHrHealthTier(12_000L))
    }

    @Test
    fun evaluateStaleness_clearsHrAndRrAfterFifteenSecondsSilenceWithBoundedRecovery() {
        // Verifies REQ-SET-126 / TST-SET-128
        val monitor = PolarHrStreamHealthMonitor(isOpticalSensor = true)
        monitor.onHrSample(135, true, true, nowMs = 10_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)

        val stillFresh = monitor.evaluateStaleness(nowMs = 25_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertFalse(stillFresh.shouldClearHr)
        assertFalse(stillFresh.shouldTriggerRecovery)
        assertEquals(HrHealthTier.HEALTHY, monitor.getHrHealthTier(25_000L))

        val timedOut = monitor.evaluateStaleness(nowMs = 25_001L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertTrue(timedOut.shouldClearHr)
        assertTrue(timedOut.shouldClearRr)
        assertTrue(timedOut.shouldTriggerRecovery)
        assertTrue(timedOut.healthTierChanged)
        assertEquals(HrHealthTier.STALE_OR_NULL, monitor.getHrHealthTier(25_001L))

        // Recovery must obey 60s cooldown.
        val insideCooldown = monitor.evaluateStaleness(nowMs = 50_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertFalse(insideCooldown.shouldClearHr)
        assertFalse(insideCooldown.shouldTriggerRecovery)

        val afterCooldown = monitor.evaluateStaleness(nowMs = 85_001L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertTrue(afterCooldown.shouldTriggerRecovery)
    }

    @Test
    fun onRrSample_clearsLatchedRrAfterTenSecondsOfArtifactOnlyBeatsAndResetsOnCleanBeat() {
        // Verifies REQ-SET-126 / TST-SET-128
        val monitor = PolarHrStreamHealthMonitor(isOpticalSensor = true)

        assertFalse(monitor.onRrSample(isCleanBeat = true, nowMs = 1_000L).shouldClearRr)
        assertFalse(monitor.onRrSample(isCleanBeat = false, nowMs = 2_000L).shouldClearRr)
        assertFalse(monitor.onRrSample(isCleanBeat = false, nowMs = 11_999L).shouldClearRr)

        val cleared = monitor.onRrSample(isCleanBeat = false, nowMs = 12_000L)
        assertTrue("10s of ARTIFACT_GAP-only beats must clear latched RR", cleared.shouldClearRr)

        // Subsequent clean beat resets timer so another 10s artifact burst clears again.
        assertFalse(monitor.onRrSample(isCleanBeat = true, nowMs = 13_000L).shouldClearRr)
        assertFalse(monitor.onRrSample(isCleanBeat = false, nowMs = 14_000L).shouldClearRr)
        assertTrue(monitor.onRrSample(isCleanBeat = false, nowMs = 24_000L).shouldClearRr)
    }

    @Test
    fun onHrSample_flatlineRequiresBothDurationAndMinSamplesAndSuppressesRestingSteadyHr() {
        // Verifies REQ-SET-126 / TST-SET-128
        val monitor = PolarHrStreamHealthMonitor(isOpticalSensor = true)

        // 29 identical samples spanning 65s during workout -> below 30-sample floor -> MUST NOT degrade.
        for (i in 0 until 29) {
            val t = 1_000L + i * 2_500L
            val decision = monitor.onHrSample(79, true, true, t, isWorkoutActive = true, opticalArtifactRatePercent = null)
            assertFalse("29 samples must not trigger flatline", decision.shouldTriggerRecovery)
            assertEquals(HrHealthTier.HEALTHY, monitor.getHrHealthTier(t))
        }

        // Reset and test 35 identical samples spanning only 59s -> below 60s floor -> MUST NOT degrade.
        monitor.reset()
        for (i in 0 until 35) {
            val t = 1_000L + i * 1_500L // max elapsed = 34 * 1500 = 51_000 ms < 60_000 ms
            val decision = monitor.onHrSample(79, true, true, t, isWorkoutActive = true, opticalArtifactRatePercent = null)
            assertFalse(decision.shouldTriggerRecovery)
            assertEquals(HrHealthTier.HEALTHY, monitor.getHrHealthTier(t))
        }

        // Reset and test steady resting HR (READY/IDLE, low optical artifact) for 70s / 71 samples -> MUST NOT degrade.
        monitor.reset()
        for (i in 0..70) {
            val t = 1_000L + i * 1_000L
            val decision = monitor.onHrSample(60, true, true, t, isWorkoutActive = false, opticalArtifactRatePercent = 5.0)
            assertFalse("Resting steady HR with low artifact rate must not trigger recovery", decision.shouldTriggerRecovery)
            assertEquals(HrHealthTier.HEALTHY, monitor.getHrHealthTier(t))
        }

        // Now repeat during active workout -> sample #61 at +60s triggers DEGRADED + recovery.
        monitor.reset()
        var triggeredRecoveryCount = 0
        for (i in 0..60) {
            val t = 1_000L + i * 1_000L
            val decision = monitor.onHrSample(79, true, true, t, isWorkoutActive = true, opticalArtifactRatePercent = null)
            if (decision.shouldTriggerRecovery) triggeredRecoveryCount++
        }
        assertEquals(1, triggeredRecoveryCount)
        assertEquals(HrHealthTier.DEGRADED, monitor.getHrHealthTier(61_000L))

        // Cooldown suppresses recovery at +90s, allows again at +121s.
        val insideCooldown = monitor.onHrSample(79, true, true, 90_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertFalse(insideCooldown.shouldTriggerRecovery)
        assertEquals(HrHealthTier.DEGRADED, monitor.getHrHealthTier(90_000L))

        val afterCooldown = monitor.onHrSample(79, true, true, 121_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertTrue(afterCooldown.shouldTriggerRecovery)

        // Immediate non-sticky recovery on the very first changing valid HR sample.
        val recovered = monitor.onHrSample(138, true, true, 122_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertTrue(recovered.acceptForSensor)
        assertTrue(recovered.healthTierChanged)
        assertFalse(recovered.shouldTriggerRecovery)
        assertEquals(HrHealthTier.HEALTHY, monitor.getHrHealthTier(122_000L))
    }

    @Test
    fun resetRrAndOpticalArtifactState_clearsPreRunOpticalSaturation() {
        // Verifies REQ-SET-126 / TST-SET-128
        val monitor = PolarHrStreamHealthMonitor(isOpticalSensor = true)
        monitor.onHrSample(75, true, true, nowMs = 1_000L, isWorkoutActive = false, opticalArtifactRatePercent = 100.0)
        assertEquals(HrHealthTier.DEGRADED, monitor.getHrHealthTier(1_000L))

        assertTrue("resetRrAndOpticalArtifactState must re-sync lastPublishedTier when tier changes", monitor.resetRrAndOpticalArtifactState(nowMs = 2_000L))
        assertEquals(HrHealthTier.HEALTHY, monitor.getHrHealthTier(2_000L))
        // Subsequent healthy sample must not falsely report healthTierChanged=true after tier was already re-synced.
        val nextHealthy = monitor.onHrSample(76, true, true, nowMs = 3_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertFalse(nextHealthy.healthTierChanged)
    }

    @Test
    fun nextCheckDelayMs_shrinksAcrossConsecutiveInvalidPacketsWithoutDrift() {
        // Verifies REQ-SET-126 / TST-SET-128
        val monitor = PolarHrStreamHealthMonitor(isOpticalSensor = true)
        monitor.onHrSample(140, true, true, nowMs = 1_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertEquals(16_000L, monitor.nextCheckDelayMs(1_000L))

        // 1-Hz invalid packets must shrink remaining invalid-clear deadline toward 0 (floored at 250 ms) instead of resetting to 10_500 ms.
        monitor.onHrSample(0, true, false, nowMs = 2_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertEquals(10_000L, monitor.nextCheckDelayMs(2_000L))

        monitor.onHrSample(0, true, false, nowMs = 6_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertEquals(6_000L, monitor.nextCheckDelayMs(6_000L))

        monitor.onHrSample(0, true, false, nowMs = 11_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertEquals(1_000L, monitor.nextCheckDelayMs(11_000L))

        monitor.onHrSample(0, true, false, nowMs = 11_900L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertEquals(PolarHrStreamHealthMonitor.MIN_CHECK_DELAY_MS, monitor.nextCheckDelayMs(11_900L))

        // After HR clears at 12_000L, next check targets silence recovery deadline (17_000L -> 5_000L), then 60s cooldown after recovery.
        monitor.onHrSample(0, true, false, nowMs = 12_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertEquals(5_000L, monitor.nextCheckDelayMs(12_000L))
        val recoveryTick = monitor.evaluateStaleness(nowMs = 17_000L, isWorkoutActive = true, opticalArtifactRatePercent = null)
        assertTrue(recoveryTick.shouldTriggerRecovery)
        assertEquals(PolarHrStreamHealthMonitor.DEFAULT_RECOVERY_COOLDOWN_MS, monitor.nextCheckDelayMs(17_000L))
    }
}
