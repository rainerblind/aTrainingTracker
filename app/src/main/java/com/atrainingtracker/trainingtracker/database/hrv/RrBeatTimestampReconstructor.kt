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

package com.atrainingtracker.trainingtracker.database.hrv

import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatNormalization
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Monotonic beat-to-beat timestamp reconstructor with wall-clock drift resynchronization (REQ-PER-034.2, REQ-TRK-085).
 *
 * Implements:
 * - Cumulative timestamp accumulation: t_k = t_{k-1} + round(rr_ms_k), clamped non-decreasing.
 * - Drift resynchronization when |t_pkt - t_beat| > 2000 ms.
 * - Gap beat estimation across RF dropouts and physiological RR bounds `[250, 2500] ms`.
 */
class RrBeatTimestampReconstructor(
    private var initialAnchorMs: Long = 0L,
    private val driftThresholdMs: Long = DEFAULT_DRIFT_THRESHOLD_MS
) {
    companion object {
        const val DEFAULT_DRIFT_THRESHOLD_MS = RrBeatNormalization.DRIFT_THRESHOLD_MS.toLong()
    }

    private var lastBeatTimestampMs: Long = initialAnchorMs
    private var lastNormalRrMs: Double = RrBeatNormalization.DEFAULT_REFERENCE_RR_MS

    @Synchronized
    fun reset(anchorMs: Long = 0L) {
        lastBeatTimestampMs = anchorMs
        lastNormalRrMs = RrBeatNormalization.DEFAULT_REFERENCE_RR_MS
    }

    /**
     * Reconstructs a monotonic non-decreasing UTC epoch timestamp for a beat interval [rrMs].
     *
     * @param packetArrivalMs Wall-clock packet arrival timestamp or session reference time.
     * @param rrMs Beat-to-beat RR interval duration in milliseconds.
     * @return [ReconstructedBeat] with timestamp, artifact flag, and gap beats count.
     */
    @Synchronized
    fun reconstruct(packetArrivalMs: Long, rrMs: Double): ReconstructedBeat {
        val roundedRrMs = if (rrMs.isFinite()) rrMs.roundToLong() else 0L
        val beatTimestampMs: Long
        var gapBeats = 0
        var artifactFlag = RrBeatEvent.ARTIFACT_NORMAL

        if (lastBeatTimestampMs == 0L) {
            beatTimestampMs = packetArrivalMs
        } else {
            val candidateTimestampMs = max(lastBeatTimestampMs, lastBeatTimestampMs + roundedRrMs)
            val drift = abs(packetArrivalMs - candidateTimestampMs)

            if (drift > driftThresholdMs) {
                val elapsedSinceLast = packetArrivalMs - lastBeatTimestampMs
                if (roundedRrMs > 0 && elapsedSinceLast > roundedRrMs) {
                    gapBeats = max(0, (elapsedSinceLast.toDouble() / rrMs).roundToLong().toInt() - 1)
                }
                beatTimestampMs = max(lastBeatTimestampMs, packetArrivalMs)
                if (gapBeats > 0) {
                    artifactFlag = RrBeatEvent.ARTIFACT_GAP
                }
            } else {
                beatTimestampMs = candidateTimestampMs
            }
        }

        if (RrBeatNormalization.isOutOfPhysiologicalRange(rrMs)) {
            artifactFlag = RrBeatEvent.ARTIFACT_GAP
            gapBeats = max(gapBeats, RrBeatNormalization.estimatePhysiologicalGapBeats(rrMs, lastNormalRrMs))
        } else {
            lastNormalRrMs = rrMs
        }

        lastBeatTimestampMs = beatTimestampMs

        return ReconstructedBeat(
            beatTimestampMs = beatTimestampMs,
            rrMs = rrMs,
            gapBeats = gapBeats,
            artifactFlag = artifactFlag
        )
    }

    data class ReconstructedBeat(
        val beatTimestampMs: Long,
        val rrMs: Double,
        val gapBeats: Int,
        val artifactFlag: Int
    )
}
