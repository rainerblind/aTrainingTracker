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

/**
 * Signal quality classification status for beat-to-beat RR intervals pursuant to REQ-TRK-067.1.
 */
enum class HrvSignalQualityStatus {
    /** Valid human sinus rhythm with physiological variance and normal autonomic dynamics. */
    VALID,

    /** Insufficient beats in evaluation window (< 30 beats) to reliably compute signal quality metrics. */
    INSUFFICIENT_DATA,

    /** Ectopic beats, missed triggers, or RF packet loss exceed reliable threshold (> 5.0% artifacts). */
    HIGH_ARTIFACTS,

    /** Sensor firmware applies moving-average smoothing, suppressing rMSSD while artificially inflating DFA a1. */
    SMOOTHED_STREAM,

    /** Synthesized or quantized RR intervals (e.g., derived from integer BPM) resulting in flat plateaus. */
    QUANTIZED_SYNTHETIC
}

/**
 * Detailed signal quality analysis result emitted by [HrvSignalQualityAnalyzer] pursuant to REQ-TRK-067.1.
 */
data class HrvSignalQualityResult(
    val status: HrvSignalQualityStatus,
    val details: String? = null,
    val rmssdMs: Double? = null,
    val dfaAlpha1: Double? = null,
    val turningPointRatio: Double? = null,
    val zeroDiffRate: Double? = null,
    val artifactRatePercent: Double = 0.0,
    val beatCount: Int = 0
)
