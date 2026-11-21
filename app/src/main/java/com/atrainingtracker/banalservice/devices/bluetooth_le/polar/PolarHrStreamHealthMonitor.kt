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

/**
 * Pure-JVM stateful health monitor for BLE Heart Rate streams (`REQ-SET-126.1`, `REQ-SET-126.2`, `ATT-9345`).
 *
 * Responsibilities:
 * - Rejects `hr <= 0` or lost skin contact (`contactSupported && !contactStatus`) as invalid so
 *   `BleHrmSilentConnectionWatchdog` is never disarmed by invalid packets, and clears latched HR
 *   to `null` once invalid packets persist `>= invalidHrClearDelayMs` (`10 s`).
 * - Clears latched HR and RR sensors to `null` when HR packet silence exceeds `staleHrTimeoutMs` (`15 s`).
 * - Clears latched RR sensor to `null` when only `ARTIFACT_GAP` PPI/RR beats arrive for `>= artifactOnlyRrClearDelayMs` (`10 s`).
 * - Detects constant-value HR flatlines requiring BOTH `>= flatlineDurationMs` (`60 s`) elapsed
 *   AND `>= flatlineMinConsecutiveSamples` (`30`) consecutive identical valid HR samples — suppressing
 *   resting `READY`/`IDLE` flatline recovery unless a workout is active (`TRACKING`/`PAUSED`) or
 *   rolling optical HRV artifact rate is `>= 95%`. Recovers immediately and non-stickily to
 *   [HrHealthTier.HEALTHY] on the very first changing valid `hr > 0` sample.
 * - Exposes a strictly lock-free `@Volatile` snapshot via [getHrHealthTier] so `MySensorManager`
 *   never acquires monitor locks inside `synchronized (MySensorManager.this)` (`REQ-SEN-021.3`).
 */
class PolarHrStreamHealthMonitor @JvmOverloads constructor(
    private val isOpticalSensorProvider: () -> Boolean,
    private val staleHrTimeoutMs: Long = DEFAULT_STALE_HR_TIMEOUT_MS,
    private val invalidHrClearDelayMs: Long = DEFAULT_INVALID_HR_CLEAR_DELAY_MS,
    private val artifactOnlyRrClearDelayMs: Long = DEFAULT_ARTIFACT_ONLY_RR_CLEAR_DELAY_MS,
    private val flatlineDurationMs: Long = DEFAULT_FLATLINE_DURATION_MS,
    private val flatlineMinConsecutiveSamples: Int = DEFAULT_FLATLINE_MIN_SAMPLES,
    private val recoveryCooldownMs: Long = DEFAULT_RECOVERY_COOLDOWN_MS,
    private val opticalArtifactSaturationPercent: Double = DEFAULT_OPTICAL_ARTIFACT_SATURATION_PERCENT
) {

    @JvmOverloads
    constructor(
        isOpticalSensor: Boolean,
        staleHrTimeoutMs: Long = DEFAULT_STALE_HR_TIMEOUT_MS,
        invalidHrClearDelayMs: Long = DEFAULT_INVALID_HR_CLEAR_DELAY_MS,
        artifactOnlyRrClearDelayMs: Long = DEFAULT_ARTIFACT_ONLY_RR_CLEAR_DELAY_MS,
        flatlineDurationMs: Long = DEFAULT_FLATLINE_DURATION_MS,
        flatlineMinConsecutiveSamples: Int = DEFAULT_FLATLINE_MIN_SAMPLES,
        recoveryCooldownMs: Long = DEFAULT_RECOVERY_COOLDOWN_MS,
        opticalArtifactSaturationPercent: Double = DEFAULT_OPTICAL_ARTIFACT_SATURATION_PERCENT
    ) : this(
        isOpticalSensorProvider = { isOpticalSensor },
        staleHrTimeoutMs = staleHrTimeoutMs,
        invalidHrClearDelayMs = invalidHrClearDelayMs,
        artifactOnlyRrClearDelayMs = artifactOnlyRrClearDelayMs,
        flatlineDurationMs = flatlineDurationMs,
        flatlineMinConsecutiveSamples = flatlineMinConsecutiveSamples,
        recoveryCooldownMs = recoveryCooldownMs,
        opticalArtifactSaturationPercent = opticalArtifactSaturationPercent
    )

    private val isOpticalSensor: Boolean
        get() = isOpticalSensorProvider()

    data class HrSampleDecision(
        val acceptForSensor: Boolean,
        val shouldClearHr: Boolean,
        val shouldClearRr: Boolean,
        val shouldTriggerRecovery: Boolean,
        val healthTierChanged: Boolean
    )

    data class RrSampleDecision(
        val shouldClearRr: Boolean,
        val healthTierChanged: Boolean
    )

    data class StalenessTickDecision(
        val shouldClearHr: Boolean,
        val shouldClearRr: Boolean,
        val shouldTriggerRecovery: Boolean,
        val healthTierChanged: Boolean
    )

    companion object {
        const val DEFAULT_STALE_HR_TIMEOUT_MS: Long = 15_000L
        const val DEFAULT_INVALID_HR_CLEAR_DELAY_MS: Long = 10_000L
        const val DEFAULT_ARTIFACT_ONLY_RR_CLEAR_DELAY_MS: Long = 10_000L
        const val DEFAULT_FLATLINE_DURATION_MS: Long = 60_000L
        const val DEFAULT_FLATLINE_MIN_SAMPLES: Int = 30
        const val DEFAULT_RECOVERY_COOLDOWN_MS: Long = 60_000L
        const val DEFAULT_OPTICAL_ARTIFACT_SATURATION_PERCENT: Double = 95.0
        const val MIN_CHECK_DELAY_MS: Long = 250L

        /**
         * Gates Polar Verity Sense optical PPI streaming strictly to non-workout resting modes
         * (`READY` / `IDLE`) so active `TRACKING`/`PAUSED` workouts never trigger optical PPG
         * pipeline freeze (`REQ-SET-126.1`).
         */
        @JvmStatic
        fun shouldStreamVerityPpi(isVeritySense: Boolean, trackingMode: TrackingMode): Boolean =
            isVeritySense && trackingMode != TrackingMode.TRACKING && trackingMode != TrackingMode.PAUSED

        @JvmStatic
        fun isWorkoutActive(trackingMode: TrackingMode): Boolean =
            trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED
    }

    // Lock-free @Volatile fields read by MySensorManager inside synchronized (MySensorManager.this).
    @Volatile private var lastValidHrMs: Long = 0L
    @Volatile private var flatlineDegraded: Boolean = false
    @Volatile private var artifactDegraded: Boolean = false
    @Volatile private var hrClearedAsInvalidOrStale: Boolean = false

    // Mutable tracking state mutated exclusively under synchronized methods.
    private var lastValidHrValue: Int = -1
    private var sameHrSinceMs: Long = 0L
    private var consecutiveSameHrCount: Int = 0
    private var invalidHrSinceMs: Long = 0L
    private var artifactOnlyRrSinceMs: Long = 0L
    private var rrClearedAsStale: Boolean = false
    private var lastRecoveryMs: Long = 0L
    private var lastPublishedTier: HrHealthTier = HrHealthTier.STALE_OR_NULL

    /**
     * Strictly lock-free health snapshot for [com.atrainingtracker.banalservice.sensor.HrSensorHealthProvider].
     */
    fun getHrHealthTier(nowMs: Long): HrHealthTier {
        if (hrClearedAsInvalidOrStale) return HrHealthTier.STALE_OR_NULL
        val lastValid = lastValidHrMs
        if (lastValid <= 0L || (nowMs - lastValid) > staleHrTimeoutMs) {
            return HrHealthTier.STALE_OR_NULL
        }
        return if (flatlineDegraded || artifactDegraded) HrHealthTier.DEGRADED else HrHealthTier.HEALTHY
    }

    @Synchronized
    fun onHrSample(
        hrBpm: Int,
        contactSupported: Boolean,
        contactStatus: Boolean,
        nowMs: Long,
        isWorkoutActive: Boolean,
        opticalArtifactRatePercent: Double?
    ): HrSampleDecision {
        val prevTier = lastPublishedTier
        val opticalSaturated = isOpticalSensor &&
            opticalArtifactRatePercent != null &&
            opticalArtifactRatePercent >= opticalArtifactSaturationPercent
        artifactDegraded = opticalSaturated

        val isValidSample = hrBpm > 0 && (!contactSupported || contactStatus)
        if (!isValidSample) {
            if (invalidHrSinceMs == 0L) {
                invalidHrSinceMs = nowMs
            }
            var shouldClearHr = false
            var shouldClearRr = false
            if (!hrClearedAsInvalidOrStale && (nowMs - invalidHrSinceMs) >= invalidHrClearDelayMs) {
                hrClearedAsInvalidOrStale = true
                flatlineDegraded = false
                shouldClearHr = true
                if (!rrClearedAsStale) {
                    rrClearedAsStale = true
                    shouldClearRr = true
                }
            }
            val newTier = getHrHealthTier(nowMs)
            lastPublishedTier = newTier
            return HrSampleDecision(
                acceptForSensor = false,
                shouldClearHr = shouldClearHr,
                shouldClearRr = shouldClearRr,
                shouldTriggerRecovery = false,
                healthTierChanged = newTier != prevTier
            )
        }

        // Valid HR sample received.
        invalidHrSinceMs = 0L
        hrClearedAsInvalidOrStale = false
        lastValidHrMs = nowMs

        var shouldTriggerRecovery = false
        if (hrBpm != lastValidHrValue) {
            lastValidHrValue = hrBpm
            sameHrSinceMs = nowMs
            consecutiveSameHrCount = 1
            // Non-sticky immediate recovery on the very first changing valid HR sample.
            flatlineDegraded = false
        } else {
            consecutiveSameHrCount++
            val elapsedFlatMs = nowMs - sameHrSinceMs
            val meetsFlatlineCriteria = elapsedFlatMs >= flatlineDurationMs &&
                consecutiveSameHrCount >= flatlineMinConsecutiveSamples
            if (meetsFlatlineCriteria && (isWorkoutActive || opticalSaturated)) {
                flatlineDegraded = true
                if (canTriggerRecovery(nowMs)) {
                    lastRecoveryMs = nowMs
                    shouldTriggerRecovery = true
                }
            }
        }

        val newTier = getHrHealthTier(nowMs)
        lastPublishedTier = newTier
        return HrSampleDecision(
            acceptForSensor = true,
            shouldClearHr = false,
            shouldClearRr = false,
            shouldTriggerRecovery = shouldTriggerRecovery,
            healthTierChanged = newTier != prevTier
        )
    }

    @JvmOverloads
    @Synchronized
    fun onRrSample(
        isCleanBeat: Boolean,
        nowMs: Long,
        opticalArtifactRatePercent: Double? = null
    ): RrSampleDecision {
        val prevTier = lastPublishedTier
        if (opticalArtifactRatePercent != null) {
            artifactDegraded = isOpticalSensor && opticalArtifactRatePercent >= opticalArtifactSaturationPercent
        }
        if (isCleanBeat) {
            artifactOnlyRrSinceMs = 0L
            rrClearedAsStale = false
            val newTier = getHrHealthTier(nowMs)
            lastPublishedTier = newTier
            return RrSampleDecision(shouldClearRr = false, healthTierChanged = newTier != prevTier)
        }
        if (artifactOnlyRrSinceMs == 0L) {
            artifactOnlyRrSinceMs = nowMs
        }
        val shouldClearRr = if (!rrClearedAsStale && (nowMs - artifactOnlyRrSinceMs) >= artifactOnlyRrClearDelayMs) {
            rrClearedAsStale = true
            true
        } else {
            false
        }
        val newTier = getHrHealthTier(nowMs)
        lastPublishedTier = newTier
        return RrSampleDecision(shouldClearRr = shouldClearRr, healthTierChanged = newTier != prevTier)
    }

    @Synchronized
    fun evaluateStaleness(
        nowMs: Long,
        isWorkoutActive: Boolean,
        opticalArtifactRatePercent: Double?
    ): StalenessTickDecision {
        val prevTier = lastPublishedTier
        val opticalSaturated = isOpticalSensor &&
            opticalArtifactRatePercent != null &&
            opticalArtifactRatePercent >= opticalArtifactSaturationPercent
        artifactDegraded = opticalSaturated

        var shouldClearHr = false
        var shouldClearRr = false
        var shouldTriggerRecovery = false

        val lastValid = lastValidHrMs
        val hrTimedOut = lastValid > 0L && (nowMs - lastValid) > staleHrTimeoutMs
        val invalidTimedOut = invalidHrSinceMs > 0L && (nowMs - invalidHrSinceMs) >= invalidHrClearDelayMs

        if (hrTimedOut || invalidTimedOut) {
            if (!hrClearedAsInvalidOrStale) {
                hrClearedAsInvalidOrStale = true
                flatlineDegraded = false
                shouldClearHr = true
            }
            if (!rrClearedAsStale) {
                rrClearedAsStale = true
                shouldClearRr = true
            }
            if (hrTimedOut && canTriggerRecovery(nowMs)) {
                lastRecoveryMs = nowMs
                shouldTriggerRecovery = true
            }
        } else if (opticalSaturated && isWorkoutActive && canTriggerRecovery(nowMs)) {
            lastRecoveryMs = nowMs
            shouldTriggerRecovery = true
        }

        if (!rrClearedAsStale && artifactOnlyRrSinceMs > 0L &&
            (nowMs - artifactOnlyRrSinceMs) >= artifactOnlyRrClearDelayMs
        ) {
            rrClearedAsStale = true
            shouldClearRr = true
        }

        val newTier = getHrHealthTier(nowMs)
        lastPublishedTier = newTier
        return StalenessTickDecision(
            shouldClearHr = shouldClearHr,
            shouldClearRr = shouldClearRr,
            shouldTriggerRecovery = shouldTriggerRecovery,
            healthTierChanged = newTier != prevTier
        )
    }

    /**
     * Computes the exact remaining delay (in ms, floored at [MIN_CHECK_DELAY_MS]) until the
     * earliest upcoming invalid-clear, HR-silence, artifact-RR-clear, or recovery-cooldown
     * deadline, or `null` when no health timer is needed (`REQ-SET-126.2`).
     */
    @Synchronized
    fun nextCheckDelayMs(nowMs: Long): Long? {
        var earliestRemainingMs: Long? = null
        fun consider(remainingMs: Long) {
            val current = earliestRemainingMs
            if (current == null || remainingMs < current) {
                earliestRemainingMs = remainingMs
            }
        }

        if (!hrClearedAsInvalidOrStale) {
            if (invalidHrSinceMs > 0L) {
                consider((invalidHrSinceMs + invalidHrClearDelayMs) - nowMs)
            }
            if (lastValidHrMs > 0L) {
                consider((lastValidHrMs + staleHrTimeoutMs + 1_000L) - nowMs)
            }
        } else if (lastValidHrMs > 0L) {
            if (lastRecoveryMs == 0L) {
                consider((lastValidHrMs + staleHrTimeoutMs + 1_000L) - nowMs)
            } else {
                consider((lastRecoveryMs + recoveryCooldownMs) - nowMs)
            }
        }

        if (!rrClearedAsStale && artifactOnlyRrSinceMs > 0L) {
            consider((artifactOnlyRrSinceMs + artifactOnlyRrClearDelayMs) - nowMs)
        }

        return earliestRemainingMs?.coerceAtLeast(MIN_CHECK_DELAY_MS)
    }

    /**
     * Clears RR/optical-artifact state when Verity Sense stops optical PPI on workout entry so
     * pre-run resting PPI beats or artifact saturation never leak into the active workout, and
     * re-synchronizes [lastPublishedTier] (`REQ-SET-126.1`, `REQ-SET-126.2`).
     */
    @JvmOverloads
    @Synchronized
    fun resetRrAndOpticalArtifactState(nowMs: Long = System.currentTimeMillis()): Boolean {
        val prevTier = lastPublishedTier
        artifactOnlyRrSinceMs = 0L
        rrClearedAsStale = false
        artifactDegraded = false
        val newTier = getHrHealthTier(nowMs)
        lastPublishedTier = newTier
        return newTier != prevTier
    }

    @Synchronized
    fun reset() {
        lastValidHrMs = 0L
        flatlineDegraded = false
        artifactDegraded = false
        hrClearedAsInvalidOrStale = false
        lastValidHrValue = -1
        sameHrSinceMs = 0L
        consecutiveSameHrCount = 0
        invalidHrSinceMs = 0L
        artifactOnlyRrSinceMs = 0L
        rrClearedAsStale = false
        lastRecoveryMs = 0L
        lastPublishedTier = HrHealthTier.STALE_OR_NULL
    }

    private fun canTriggerRecovery(nowMs: Long): Boolean =
        lastRecoveryMs == 0L || (nowMs - lastRecoveryMs) >= recoveryCooldownMs
}
