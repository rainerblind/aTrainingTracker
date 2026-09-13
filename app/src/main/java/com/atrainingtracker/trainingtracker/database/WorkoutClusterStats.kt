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

package com.atrainingtracker.trainingtracker.database

/**
 * Encapsulates performance, volume, and recency statistics for a Workout Cluster (REQ-SET-068 / REQ-SET-070).
 */
data class WorkoutClusterStats @JvmOverloads constructor(
    val clusterId: Long = 0L,
    val workoutCount: Int = 0,
    val lastHitTimestampS: Long? = null,
    val lastHitDateStr: String? = null,
    val avgSpeedMps: Double = 0.0,
    val avgDurationSec: Long = 0L,
    val bestDurationSec: Long? = null,
    val totalDistanceMeters: Double = 0.0,
    val totalAscentMeters: Long = 0L,
    val totalActiveTimeSec: Long = 0L
) {
    val avgSpeedKmh: Double
        get() = avgSpeedMps * 3.6

    val avgDistanceMeters: Double
        get() = if (workoutCount > 0) totalDistanceMeters / workoutCount else 0.0

    val avgAscentMeters: Long
        get() = if (workoutCount > 0) totalAscentMeters / workoutCount else 0L

    val avgPaceSecPerKm: Double
        get() = if (avgSpeedMps > 0.0) 1000.0 / avgSpeedMps else 0.0

    companion object {
        @JvmField
        val EMPTY = WorkoutClusterStats()

        /**
         * Formats a unix timestamp (seconds) into a localized medium date string with relative recency.
         * (e.g., "14.09.2026 (Today)", "13.09.2026 (Yesterday)", "10.09.2026 (4 days ago)", "24.08.2026 (2 weeks ago)")
         */
        @JvmStatic
        fun formatRelativeRecency(
            context: android.content.Context,
            timestampS: Long?,
            nowS: Long = System.currentTimeMillis() / 1000L
        ): String {
            if (timestampS == null || timestampS <= 0L) return ""
            val date = java.util.Date(timestampS * 1000L)
            val dateStr = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(date)
            val diffS = maxOf(0L, nowS - timestampS)
            val days = (diffS / 86400L).toInt()

            val relativeStr = when {
                days == 0 -> context.getString(com.atrainingtracker.R.string.cluster_stats_today)
                days == 1 -> context.getString(com.atrainingtracker.R.string.cluster_stats_yesterday)
                days in 2..6 -> context.getString(com.atrainingtracker.R.string.cluster_stats_days_ago, days)
                else -> {
                    val weeks = maxOf(1, days / 7)
                    context.getString(com.atrainingtracker.R.string.cluster_stats_weeks_ago, weeks)
                }
            }
            return "$dateStr ($relativeStr)"
        }
    }
}

