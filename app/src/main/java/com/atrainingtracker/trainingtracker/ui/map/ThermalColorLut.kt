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

package com.atrainingtracker.trainingtracker.ui.map

import android.graphics.Color
import kotlin.math.roundToInt

/**
 * Precomputed 256-entry color lookup table (LUT) for line-density heatmaps.
 * Maps 8-bit accumulated pass intensity to the canonical 3-stop sequential blue gradient:
 * Light Cyan (#00E5FF) -> Pure Blue (#0000FF) -> Deep Indigo (#311B92) (ATT-4769 / REQ-MAP-056.2).
 *
 * @param opacity The global layer opacity scaling (0.0 to 1.0).
 */
class ThermalColorLut(
    val opacity: Double = 1.0
) {
    val table: IntArray = IntArray(256)

    init {
        table[0] = 0 // Index 0 is fully transparent

        // Color stops: (position 0.0..1.0, intArrayOf(R, G, B, baseAlpha))
        // Aligned with MapUtils.kt / ATT-500 / ATT-504 sequential blue heatmap palette
        // Single-pass paths remain clearly legible (~55% alpha), while frequently traveled corridors reach ~85-100% alpha (REQ-PER-024.4)
        val stops = arrayOf(
            Pair(0.00f, intArrayOf(0, 229, 255, 140)),   // Light Cyan (#00E5FF, ~55% base alpha)
            Pair(0.50f, intArrayOf(0, 0, 255, 204)),     // Pure Blue (#0000FF, ~80% base alpha)
            Pair(1.00f, intArrayOf(49, 27, 146, 255))   // Deep Indigo (#311B92, 100% base alpha)
        )

        for (i in 1..255) {
            val t = i / 255.0f
            var lower = stops[0]
            var upper = stops.last()
            for (s in 0 until stops.size - 1) {
                if (t >= stops[s].first && t <= stops[s + 1].first) {
                    lower = stops[s]
                    upper = stops[s + 1]
                    break
                }
            }

            val range = upper.first - lower.first
            val frac = if (range > 0f) ((t - lower.first) / range).coerceIn(0f, 1f) else 0f

            val r = (lower.second[0] + frac * (upper.second[0] - lower.second[0])).roundToInt().coerceIn(0, 255)
            val g = (lower.second[1] + frac * (upper.second[1] - lower.second[1])).roundToInt().coerceIn(0, 255)
            val b = (lower.second[2] + frac * (upper.second[2] - lower.second[2])).roundToInt().coerceIn(0, 255)
            val baseAlpha = (lower.second[3] + frac * (upper.second[3] - lower.second[3])).roundToInt().coerceIn(0, 255)
            val a = (baseAlpha * opacity).roundToInt().coerceIn(0, 255)

            table[i] = ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
        }
    }

    /**
     * Resolves the 32-bit ARGB color for an accumulated intensity [0..255].
     */
    fun getColor(intensity: Int): Int {
        val clamped = intensity.coerceIn(0, 255)
        return table[clamped]
    }

    companion object {
        val DEFAULT = ThermalColorLut(opacity = 1.0)
    }
}
