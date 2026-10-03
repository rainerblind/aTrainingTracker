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

package com.atrainingtracker.trainingtracker.ui.aftermath.splits

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure calculation engine for lap and interval split analytics.
 * (REQ-UI-204 / ATT-1392)
 */
object LapSplitCalculator {

    private const val SPEED_EPSILON = 1e-5
    private const val MIN_VALID_SPEED = 0.001

    /**
     * Calculates split chart data from a list of workout laps.
     *
     * Returns null if the session contains fewer than 2 laps or has no valid metrics.
     *
     * @param laps The ordered list of laps recorded or imported for the workout.
     * @param bSportType The sport type of the workout session.
     * @param paceFormatter Formatter converting seconds per distance unit into user-facing text (e.g. "4:12 /km").
     * @param speedFormatter Formatter converting m/s into user-facing text (e.g. "32.4 km/h").
     * @return [LapSplitChartData] or null if session is ineligible (< 2 laps).
     */
    fun calculateSplitData(
        laps: List<LapData>,
        bSportType: BSportType = BSportType.UNKNOWN,
        paceFormatter: (Double) -> String = ::defaultPaceFormatter,
        speedFormatter: (Double) -> String = ::defaultSpeedFormatter
    ): LapSplitChartData? {
        if (laps.size < 2) return null

        val validSpeeds = laps.map { it.speedAverageMps }.filter { it > MIN_VALID_SPEED }
        val maxSpeed = validSpeeds.maxOrNull() ?: 0.0
        val minSpeed = validSpeeds.minOrNull() ?: 0.0
        val hasDifferentSpeeds = validSpeeds.isNotEmpty() && (maxSpeed - minSpeed) > SPEED_EPSILON

        val fastestSpeed = if (hasDifferentSpeeds) maxSpeed else null
        val slowestSpeed = if (hasDifferentSpeeds) minSpeed else null

        val isRunning = (bSportType == BSportType.RUN)

        var fastestLapNr: Long? = null
        var slowestLapNr: Long? = null

        val splitItems = laps.mapIndexed { index, lap ->
            val lapIndex = index + 1
            val displayName = lap.getDisplayName(lapIndex)
            val speed = lap.speedAverageMps

            val isFastest = hasDifferentSpeeds && fastestSpeed != null && abs(speed - fastestSpeed) < SPEED_EPSILON
            val isSlowest = hasDifferentSpeeds && slowestSpeed != null && abs(speed - slowestSpeed) < SPEED_EPSILON

            if (isFastest && fastestLapNr == null) {
                fastestLapNr = lap.lapNr
            }
            if (isSlowest && slowestLapNr == null) {
                slowestLapNr = lap.lapNr
            }

            // Relative ratio in [0.25f, 1.0f]
            val ratio = if (hasDifferentSpeeds && speed > MIN_VALID_SPEED) {
                val normalized = ((speed - minSpeed) / (maxSpeed - minSpeed)).toFloat()
                (0.25f + 0.75f * normalized).coerceIn(0.25f, 1.0f)
            } else {
                1.0f
            }

            val color = resolveIntensityColor(ratio)

            val formattedMetric = if (isRunning) {
                if (speed > MIN_VALID_SPEED) {
                    paceFormatter(1.0 / speed)
                } else {
                    "--"
                }
            } else {
                speedFormatter(speed)
            }

            LapSplitItem(
                lapNr = lap.lapNr,
                displayName = displayName,
                durationSec = lap.timeTotalS,
                distanceMeters = lap.distanceTotalM,
                speedMps = speed,
                formattedPaceOrSpeed = formattedMetric,
                relativeRatio = ratio,
                isFastest = isFastest,
                isSlowest = isSlowest,
                color = color
            )
        }

        return LapSplitChartData(
            splits = splitItems,
            bSportType = bSportType,
            fastestLapNr = fastestLapNr,
            slowestLapNr = slowestLapNr
        )
    }

    /**
     * Resolves the 5-tier intensity color based on relative ratio.
     */
    fun resolveIntensityColor(ratio: Float): Color {
        return when {
            ratio < 0.40f -> TTColor.Zone1
            ratio < 0.55f -> TTColor.Zone2
            ratio < 0.70f -> TTColor.Zone3
            ratio < 0.85f -> TTColor.Zone4
            else -> TTColor.Zone5
        }
    }

    private fun defaultPaceFormatter(paceSecPerMeter: Double): String {
        // Assume metric (seconds per km = paceSecPerMeter * 1000)
        val totalSecPerKm = (paceSecPerMeter * 1000.0).roundToInt()
        val minutes = totalSecPerKm / 60
        val seconds = totalSecPerKm % 60
        return String.format(Locale.US, "%d:%02d /km", minutes, seconds)
    }

    private fun defaultSpeedFormatter(speedMps: Double): String {
        val speedKmh = speedMps * 3.6
        return String.format(Locale.US, "%.1f km/h", speedKmh)
    }
}
