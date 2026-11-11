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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Aerobic threshold zone indicator calculations for Detrended Fluctuation Analysis (DFA) alpha 1 metrics
 * conforming to REQ-TRK-061.3 and Rule 71 (zero hardcoded hex colors, strict use of [TTColor] tokens).
 */
object HrvZoneIndicator {

    /**
     * Resolves the semantic zone color for a DFA alpha 1 exponent:
     * - Aerobic Base (alpha1 > t1): [TTColor.Zone2] (Green)
     * - Threshold Transition (t2 <= alpha1 <= t1): [TTColor.Zone3] (Orange / Tempo)
     * - High Intensity / Anaerobic (alpha1 < t2): [TTColor.Zone4] (Red)
     * - Null / Negative / NaN / Gated / Smoothed: [defaultColor] ([Color.Transparent])
     */
    fun calculateZoneColor(
        alpha1: Double?,
        t1: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
        t2: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2,
        defaultColor: Color = Color.Transparent,
        isSmoothedStream: Boolean = false
    ): Color {
        if (isSmoothedStream) {
            return defaultColor
        }
        if (alpha1 == null || alpha1.isNaN() || alpha1.isInfinite() || alpha1 < 0.0) {
            return defaultColor
        }
        return when {
            alpha1 > t1 -> TTColor.Zone2
            alpha1 >= t2 -> TTColor.Zone3
            else -> TTColor.Zone4
        }
    }

    /**
     * Computes the [ZoneGaugeState] for DFA alpha 1 metrics based on active thresholds [t1] and [t2] (REQ-TRK-061.3).
     *
     * Maps to a 5-zone HR gauge bar with:
     * - Zone 2 (Aerobic Base, alpha1 > t1): activeZoneIndex = 2
     * - Zone 3 (Threshold Transition, t2 <= alpha1 <= t1): activeZoneIndex = 3
     * - Zone 4 (Anaerobic / High Intensity, alpha1 < t2): activeZoneIndex = 4
     * - Returns null if [alpha1] is null, NaN, non-finite, or negative.
     */
    fun calculateZoneGaugeState(
        alpha1: Double?,
        t1: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
        t2: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2
    ): ZoneGaugeState? {
        if (alpha1 == null || alpha1.isNaN() || alpha1.isInfinite() || alpha1 < 0.0) {
            return null
        }
        val activeZoneIndex = when {
            alpha1 > t1 -> 2
            alpha1 >= t2 -> 3
            else -> 4
        }
        val needleFraction = when {
            alpha1 > t1 -> {
                val norm = ((alpha1.toFloat() - t1) / (1.2f - t1).coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                0.40f - norm * 0.20f
            }
            alpha1 >= t2 -> {
                val norm = ((alpha1.toFloat() - t2) / (t1 - t2).coerceAtLeast(0.05f)).coerceIn(0f, 1f)
                0.60f - norm * 0.20f
            }
            else -> {
                val norm = ((alpha1.toFloat() - 0.20f) / (t2 - 0.20f).coerceAtLeast(0.05f)).coerceIn(0f, 1f)
                0.85f - norm * 0.25f
            }
        }.coerceIn(0.05f, 0.95f)

        return ZoneGaugeState(
            segmentColors = ZoneGaugeMath.HR_SEGMENT_COLORS,
            needleFraction = needleFraction,
            activeZoneIndex = activeZoneIndex
        )
    }
}
