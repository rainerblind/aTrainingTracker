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
     * Semantic segment colors for the 3-zone HRV DFA alpha 1 model:
     * - Zone 2 (Aerobic Base): [TTColor.Zone2] (Green)
     * - Zone 3 (Threshold Transition): [TTColor.Zone3] (Orange)
     * - Zone 4 (Anaerobic / High Intensity): [TTColor.Zone4] (Red)
     */
    val HRV_SEGMENT_COLORS: List<Color> = listOf(
        TTColor.Zone2,
        TTColor.Zone3,
        TTColor.Zone4
    )

    /**
     * Zone labels for under-segment active badge in [ZoneGaugeBar] (REQ-TRK-061.3, ATT-7520).
     */
    val HRV_ZONE_LABELS: List<String> = listOf("Z2", "Z3", "Z4")

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
     * Computes the [ZoneGaugeState] for DFA alpha 1 metrics based on active thresholds [t1] and [t2] (REQ-TRK-061.3, ATT-7520).
     *
     * Maps to a 3-zone HRV gauge bar with:
     * - Zone 2 (Aerobic Base, alpha1 > t1): activeZoneIndex = 1, badge "Z2"
     * - Zone 3 (Threshold Transition, t2 <= alpha1 <= t1): activeZoneIndex = 2, badge "Z3"
     * - Zone 4 (Anaerobic / High Intensity, alpha1 < t2): activeZoneIndex = 3, badge "Z4"
     * - Proportional segment weights reflecting spans (1.20 - t1), (t1 - t2), (t2 - 0.20).
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

        val s1 = (1.20f - t1).coerceAtLeast(0.05f)
        val s2 = (t1 - t2).coerceAtLeast(0.05f)
        val s3 = (t2 - 0.20f).coerceAtLeast(0.05f)
        val totalSpan = s1 + s2 + s3
        val w1 = s1 / totalSpan
        val w2 = s2 / totalSpan
        val w3 = s3 / totalSpan
        val segmentWeights = listOf(w1, w2, w3)

        val activeZoneIndex = when {
            alpha1 > t1 -> 1
            alpha1 >= t2 -> 2
            else -> 3
        }

        val fraction = when {
            alpha1 > t1 -> {
                val norm = ((1.20f - alpha1.toFloat()) / s1).coerceIn(0f, 1f)
                norm * w1
            }
            alpha1 >= t2 -> {
                val norm = ((t1 - alpha1.toFloat()) / s2).coerceIn(0f, 1f)
                w1 + norm * w2
            }
            else -> {
                val norm = ((t2 - alpha1.toFloat()) / s3).coerceIn(0f, 1f)
                (w1 + w2) + norm * w3
            }
        }
        val needleFraction = fraction.coerceIn(0.05f, 0.95f)

        return ZoneGaugeState(
            segmentColors = HRV_SEGMENT_COLORS,
            needleFraction = needleFraction,
            activeZoneIndex = activeZoneIndex,
            segmentWeights = segmentWeights,
            zoneLabels = HRV_ZONE_LABELS
        )
    }
}
