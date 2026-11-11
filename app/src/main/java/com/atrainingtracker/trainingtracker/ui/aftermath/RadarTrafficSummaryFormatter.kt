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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import com.atrainingtracker.R
import java.util.Locale

/**
 * Formats a [RadarTrafficSummary] into a concise, standardized single-line string
 * suitable for Strava activity descriptions and workout share texts (REQ-RAD-007 / ATT-5122).
 */
object RadarTrafficSummaryFormatter {

    /**
     * Builds the localized Radar Traffic & Close-Pass Summary single line.
     *
     * @param summary The post-workout radar traffic aggregation model, or null.
     * @param context Application context used for localized string resources.
     * @return Formatted single-line string, or null if summary is null or has zero vehicle passes.
     */
    fun format(summary: RadarTrafficSummary?, context: Context): String? {
        if (summary == null || summary.totalVehicles <= 0) {
            return null
        }

        val densityStr = String.format(Locale.getDefault(), "%.1f", summary.vehiclesPerKm)
        val header = context.getString(
            R.string.radar_summary_header,
            summary.totalVehicles,
            densityStr
        )

        val parts = mutableListOf<String>()
        parts.add(header)

        val hasV2Metrics = summary.closePassCount > 0 ||
                summary.heavyVehicleCount > 0 ||
                summary.minPassingDistanceM != null

        if (hasV2Metrics) {
            if (summary.closePassCount > 0) {
                val minDist = summary.minPassingDistanceM ?: 1.5
                val minDistStr = String.format(Locale.getDefault(), "%.2f", minDist)
                val closePassStr = if (summary.closePassCount == 1) {
                    context.getString(R.string.radar_summary_close_passes_singular, minDistStr)
                } else {
                    context.getString(R.string.radar_summary_close_passes, summary.closePassCount, minDistStr)
                }
                parts.add(closePassStr)
            }

            if (summary.heavyVehicleCount > 0) {
                val heavyVehStr = if (summary.heavyVehicleCount == 1) {
                    context.getString(R.string.radar_summary_heavy_vehicles_singular)
                } else {
                    context.getString(R.string.radar_summary_heavy_vehicles, summary.heavyVehicleCount)
                }
                parts.add(heavyVehStr)
            }

            val maxVehKmh = Math.round(summary.maxVehicleSpeedMps * 3.6).toInt()
            parts.add(context.getString(R.string.radar_summary_max_speed_simple, maxVehKmh.toString()))
        } else {
            val maxVehKmh = Math.round(summary.maxVehicleSpeedMps * 3.6).toInt()
            val maxAppKmh = Math.round(summary.maxApproachSpeedMps * 3.6).toInt()
            parts.add(context.getString(R.string.radar_summary_max_speed, maxVehKmh.toString(), maxAppKmh.toString()))
        }

        return parts.joinToString(" • ")
    }
}
