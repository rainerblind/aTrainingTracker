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

import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatNormalization
import com.polar.sdk.api.model.PolarPpiData

/**
 * Pure-JVM stateful processor converting Polar BLE SDK optical [PolarPpiData.PolarPpiSample]
 * packets into monotonically timestamped [RrBeatEvent]s tagged with `isOpticalPpi = true`
 * (`REQ-HRV-051.1`).
 *
 * Hardware quality gating:
 * - Samples with `ppi <= 0` are dropped completely.
 * - Samples with `blockerBit == true` or `(skinContactSupported && !skinContactStatus)` preserve
 *   monotonic timeline spacing via [RrBeatNormalization], are stamped [RrBeatEvent.ARTIFACT_GAP]
 *   so [com.atrainingtracker.banalservice.devices.hrv.RrArtifactFilter] never overwrites them,
 *   and never mutate [lastNormalRrMs].
 */
class PolarPpiStreamProcessor {

    private var lastBeatTimestampMs: Double = 0.0
    private var lastNormalRrMs: Double = RrBeatNormalization.DEFAULT_REFERENCE_RR_MS
    private var packetCounter: Int = 0

    companion object {
        /**
         * Returns `true` when Polar Verity Sense hardware flags the optical PPI sample as invalid
         * due to motion (`blockerBit`) or lost skin contact (when supported by firmware).
         */
        @JvmStatic
        fun isHardwareArtifact(sample: PolarPpiData.PolarPpiSample): Boolean {
            return sample.blockerBit || (sample.skinContactSupported && !sample.skinContactStatus)
        }
    }

    /**
     * Converts a batch of Polar optical PPI samples arriving at [packetArrivalMs] into normalized
     * [RrBeatEvent]s tagged with `isOpticalPpi = true`.
     */
    @Synchronized
    fun processSamples(
        samples: List<PolarPpiData.PolarPpiSample>,
        packetArrivalMs: Long
    ): List<RrBeatEvent> {
        val positiveSamples = samples.filter { it.ppi > 0 }
        if (positiveSamples.isEmpty()) {
            return emptyList()
        }

        packetCounter++
        val bundle = RrBeatNormalization.reconstructBundle(
            rrListMs = positiveSamples.map { it.ppi.toDouble() },
            packetArrivalMs = packetArrivalMs,
            previousBeatTimestampMs = lastBeatTimestampMs,
            previousNormalRrMs = lastNormalRrMs,
            packetCounter = packetCounter,
            protocol = ProtocolType.BLE
        )

        var cleanRefRrMs = lastNormalRrMs
        val output = ArrayList<RrBeatEvent>(bundle.events.size)

        for (index in bundle.events.indices) {
            val baseEvent = bundle.events[index]
            val sample = positiveSamples[index]
            val hardwareArtifact = isHardwareArtifact(sample)
            val outOfRange = RrBeatNormalization.isOutOfPhysiologicalRange(baseEvent.rrMs)
            val physGap = RrBeatNormalization.estimatePhysiologicalGapBeats(baseEvent.rrMs, cleanRefRrMs)

            // Reserve maxOf for index == 0 (driftGapBeats); index > 0 uses physGap directly against cleanRefRrMs.
            val effectiveGapBeats = if (index == 0) maxOf(baseEvent.gapBeats, physGap) else physGap
            val effectiveArtifactFlag = if (hardwareArtifact || outOfRange || effectiveGapBeats > 0) {
                RrBeatEvent.ARTIFACT_GAP
            } else {
                RrBeatEvent.ARTIFACT_NORMAL
            }
            if (!hardwareArtifact && !outOfRange) cleanRefRrMs = baseEvent.rrMs

            output.add(
                RrBeatEvent(
                    beatTimestampMs = baseEvent.beatTimestampMs,
                    rrMs = baseEvent.rrMs,
                    rawCounter = baseEvent.rawCounter,
                    protocol = baseEvent.protocol,
                    gapBeats = effectiveGapBeats,
                    artifactFlag = effectiveArtifactFlag,
                    isOpticalPpi = true
                )
            )
        }

        lastBeatTimestampMs = bundle.lastBeatTimestampMs
        lastNormalRrMs = cleanRefRrMs
        return output
    }

    /**
     * Resets monotonic timestamp, clean reference RR, and packet counter state (`Rule 51`).
     */
    @Synchronized
    fun reset() {
        lastBeatTimestampMs = 0.0
        lastNormalRrMs = RrBeatNormalization.DEFAULT_REFERENCE_RR_MS
        packetCounter = 0
    }

    @VisibleForTesting
    @Synchronized
    fun getLastNormalRrMsForTesting(): Double = lastNormalRrMs
}
