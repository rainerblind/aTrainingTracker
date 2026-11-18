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

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared pure-Kotlin normalization, monotonic timestamp reconstruction, and physiological
 * gap-detection utilities for RR-interval streams (`REQ-TRK-085.1`, `REQ-TRK-085.2`).
 */
object RrBeatNormalization {
    const val MIN_RR_MS = 250.0
    const val MAX_RR_MS = 2500.0
    const val DRIFT_THRESHOLD_MS = 2000.0
    const val DEFAULT_REFERENCE_RR_MS = 800.0

    /**
     * Normalized output for a bundled RR packet.
     */
    data class NormalizedBundle(
        val events: List<RrBeatEvent>,
        val lastBeatTimestampMs: Double,
        val lastNormalRrMs: Double
    )

    /**
     * Returns `true` when [rrMs] is non-finite or outside plausible physiological bounds `[250, 2500] ms`.
     */
    @JvmStatic
    fun isOutOfPhysiologicalRange(rrMs: Double): Boolean {
        return !rrMs.isFinite() || rrMs < MIN_RR_MS || rrMs > MAX_RR_MS
    }

    /**
     * Estimates missed beat count for an implausibly long RR interval (`> 2500 ms`), returning `0`
     * when [rrMs] does not exceed [MAX_RR_MS].
     */
    @JvmStatic
    @JvmOverloads
    fun estimatePhysiologicalGapBeats(
        rrMs: Double,
        referenceRrMs: Double = DEFAULT_REFERENCE_RR_MS
    ): Int {
        if (!rrMs.isFinite() || rrMs <= MAX_RR_MS) {
            return 0
        }
        val safeRef = if (referenceRrMs.isFinite() && referenceRrMs in MIN_RR_MS..MAX_RR_MS) {
            referenceRrMs
        } else {
            DEFAULT_REFERENCE_RR_MS
        }
        return max(1, (rrMs / safeRef).roundToInt() - 1)
    }

    /**
     * Reconstructs monotonic non-decreasing beat timestamps and explicit `ARTIFACT_GAP` flags
     * across bundled RR intervals (`BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`).
     */
    @JvmStatic
    fun reconstructBundle(
        rrListMs: List<Double>,
        packetArrivalMs: Long,
        previousBeatTimestampMs: Double,
        previousNormalRrMs: Double,
        packetCounter: Int,
        protocol: ProtocolType
    ): NormalizedBundle {
        val count = rrListMs.size
        if (count == 0) {
            return NormalizedBundle(emptyList(), previousBeatTimestampMs, previousNormalRrMs)
        }

        var refRrMs = if (previousNormalRrMs.isFinite() && previousNormalRrMs in MIN_RR_MS..MAX_RR_MS) {
            previousNormalRrMs
        } else {
            DEFAULT_REFERENCE_RR_MS
        }

        val totalBundleDurationMs = rrListMs.sumOf { it }
        val isInitial = previousBeatTimestampMs == 0.0
        val isDrifted = !isInitial &&
            abs(packetArrivalMs - (previousBeatTimestampMs + totalBundleDurationMs)) > DRIFT_THRESHOLD_MS

        val events = ArrayList<RrBeatEvent>(count)
        val finalLastBeatTimestampMs: Double

        if (isInitial || isDrifted) {
            val driftGapBeats = if (isDrifted) {
                val elapsedSinceLast = packetArrivalMs - previousBeatTimestampMs
                val avgRrMs = totalBundleDurationMs / count
                if (avgRrMs > 0.0 && elapsedSinceLast > avgRrMs) {
                    max(0L, Math.round(elapsedSinceLast / avgRrMs) - count).toInt()
                } else {
                    0
                }
            } else {
                0
            }

            val timestamps = DoubleArray(count)
            timestamps[count - 1] = packetArrivalMs.toDouble()
            for (i in count - 2 downTo 0) {
                timestamps[i] = timestamps[i + 1] - rrListMs[i + 1]
            }

            var floorMs = previousBeatTimestampMs
            for (i in 0 until count) {
                val rawTs = timestamps[i]
                val clampedTs = if (!isInitial || i > 0) max(rawTs, floorMs) else rawTs
                timestamps[i] = clampedTs
                floorMs = clampedTs

                val rrMs = rrListMs[i]
                val outOfRange = isOutOfPhysiologicalRange(rrMs)
                val physGap = estimatePhysiologicalGapBeats(rrMs, refRrMs)
                val beatGap = max(if (i == 0) driftGapBeats else 0, physGap)
                val artifactFlag = if (beatGap > 0 || outOfRange) {
                    RrBeatEvent.ARTIFACT_GAP
                } else {
                    RrBeatEvent.ARTIFACT_NORMAL
                }
                if (!outOfRange) {
                    refRrMs = rrMs
                }

                events.add(
                    RrBeatEvent(
                        beatTimestampMs = Math.round(clampedTs),
                        rrMs = rrMs,
                        rawCounter = packetCounter,
                        protocol = protocol,
                        gapBeats = beatGap,
                        artifactFlag = artifactFlag
                    )
                )
            }
            finalLastBeatTimestampMs = timestamps[count - 1]
        } else {
            var currentTs = previousBeatTimestampMs
            for (i in 0 until count) {
                val rrMs = rrListMs[i]
                val stepMs = if (rrMs.isFinite() && rrMs > 0.0) rrMs else 0.0
                currentTs = max(currentTs + stepMs, currentTs)

                val outOfRange = isOutOfPhysiologicalRange(rrMs)
                val physGap = estimatePhysiologicalGapBeats(rrMs, refRrMs)
                val artifactFlag = if (outOfRange) {
                    RrBeatEvent.ARTIFACT_GAP
                } else {
                    RrBeatEvent.ARTIFACT_NORMAL
                }
                if (!outOfRange) {
                    refRrMs = rrMs
                }

                events.add(
                    RrBeatEvent(
                        beatTimestampMs = Math.round(currentTs),
                        rrMs = rrMs,
                        rawCounter = packetCounter,
                        protocol = protocol,
                        gapBeats = physGap,
                        artifactFlag = artifactFlag
                    )
                )
            }
            finalLastBeatTimestampMs = currentTs
        }

        return NormalizedBundle(
            events = events,
            lastBeatTimestampMs = finalLastBeatTimestampMs,
            lastNormalRrMs = refRrMs
        )
    }

    /**
     * Normalizes a list of [RrBeatEvent]s into chronological order (`beatTimestampMs`, preserving
     * original index/arrival order on timestamp ties) with monotonic non-decreasing timestamps
     * prior to artifact filtering (`REQ-TRK-085.3`).
     */
    @JvmStatic
    fun sortAndNormalizeChronologically(beats: List<RrBeatEvent>): List<RrBeatEvent> {
        if (beats.size <= 1) return beats
        val sorted = beats.sortedBy { it.beatTimestampMs }
        var lastTs = sorted[0].beatTimestampMs
        val normalized = ArrayList<RrBeatEvent>(sorted.size)
        for (beat in sorted) {
            val monotonicTs = max(beat.beatTimestampMs, lastTs)
            lastTs = monotonicTs
            normalized.add(
                if (monotonicTs == beat.beatTimestampMs) beat else beat.copy(beatTimestampMs = monotonicTs)
            )
        }
        return normalized
    }
}
