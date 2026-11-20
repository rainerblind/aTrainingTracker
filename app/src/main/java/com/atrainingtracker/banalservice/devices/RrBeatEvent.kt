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

/**
 * Immutable beat-to-beat RR event dispatched to registered listeners (REQ-CON-027.1, ATT-3540).
 *
 * @property beatTimestampMs Monotonically reconstructed UTC epoch timestamp of the heart beat in milliseconds.
 * @property rrMs Beat-to-beat RR interval duration in milliseconds.
 * @property rawCounter Raw packet or heart beat counter from the hardware sensor.
 * @property protocol Hardware protocol source ([ProtocolType.BLE], [ProtocolType.ANT_PLUS], or [ProtocolType.SIMULATED]).
 * @property gapBeats Number of dropped beats across an RF dropout preceding this beat (0 for continuous beats).
 * @property artifactFlag Artifact flag (0 = NORMAL, 1 = ECTOPIC, 2 = SHORT, 3 = LONG, 4 = MISSED, 5 = GAP).
 * @property isOpticalPpi True when the interval originates from optical PPG pulse-to-pulse intervals (e.g. Polar Verity Sense, `REQ-HRV-049.1`).
 */
data class RrBeatEvent @JvmOverloads constructor(
    val beatTimestampMs: Long,
    val rrMs: Double,
    val rawCounter: Int,
    val protocol: ProtocolType,
    val gapBeats: Int = 0,
    val artifactFlag: Int = ARTIFACT_NORMAL,
    val isOpticalPpi: Boolean = false
) {
    /**
     * 6-argument Java-compatible overload preserving [isOpticalPpi] when Java callers
     * (e.g. `DeviceManager.java`) adjust timestamps without modifying modality provenance.
     */
    fun copy(
        beatTimestampMs: Long,
        rrMs: Double,
        rawCounter: Int,
        protocol: ProtocolType,
        gapBeats: Int,
        artifactFlag: Int
    ): RrBeatEvent = copy(
        beatTimestampMs = beatTimestampMs,
        rrMs = rrMs,
        rawCounter = rawCounter,
        protocol = protocol,
        gapBeats = gapBeats,
        artifactFlag = artifactFlag,
        isOpticalPpi = this.isOpticalPpi
    )

    companion object {
        const val ARTIFACT_NORMAL = 0
        const val ARTIFACT_ECTOPIC = 1
        const val ARTIFACT_SHORT = 2
        const val ARTIFACT_LONG = 3
        const val ARTIFACT_MISSED = 4
        const val ARTIFACT_GAP = 5
    }
}
