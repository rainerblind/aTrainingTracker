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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Pure calculation engine for classifying workout telemetry into 5 training zones
 * and computing cumulative time-in-zones and percentages.
 */
object ZoneDistributionCalculator {

    private const val MAX_DELTA_T_SEC = 5L
    private const val DEFAULT_LAST_SAMPLE_DURATION_SEC = 1L

    private val ZONE_LABELS = intArrayOf(
        R.string.zone_1_label,
        R.string.zone_2_label,
        R.string.zone_3_label,
        R.string.zone_4_label,
        R.string.zone_5_label
    )

    private val ZONE_COLORS = arrayOf(
        TTColor.Zone1,
        TTColor.Zone2,
        TTColor.Zone3,
        TTColor.Zone4,
        TTColor.Zone5
    )

    /**
     * Calculates 5-zone distribution data from heart rate samples and athlete thresholds.
     *
     * @param samples List of active time and HR samples.
     * @param thresholds Athlete HR thresholds for Zones 1 through 4.
     * @return [ZoneDistributionData] containing exactly 5 entries, or null if telemetry is insufficient.
     */
    fun calculateHeartRateDistribution(
        samples: List<ZoneSample>,
        thresholds: HeartRateZoneThresholds
    ): ZoneDistributionData? {
        val validSamples = samples.filter { it.value > 0 }
        if (validSamples.isEmpty()) return null

        val zoneDurations = LongArray(5)

        val isDegenerate = validSamples.size > 1 && validSamples.first().timeActiveSec == validSamples.last().timeActiveSec

        for (i in validSamples.indices) {
            val sample = validSamples[i]
            val zoneIdx = determineHeartRateZone(sample.value, thresholds)

            val dt = if (isDegenerate) {
                1L
            } else if (i < validSamples.size - 1) {
                val nextSample = validSamples[i + 1]
                val rawDt = nextSample.timeActiveSec - sample.timeActiveSec
                when {
                    rawDt < 0L -> 0L
                    rawDt > MAX_DELTA_T_SEC -> MAX_DELTA_T_SEC
                    else -> rawDt
                }
            } else {
                DEFAULT_LAST_SAMPLE_DURATION_SEC
            }

            zoneDurations[zoneIdx] += dt
        }

        val totalDuration = zoneDurations.sum()
        if (totalDuration <= 0L) return null

        val entries = (0..4).map { idx ->
            val duration = zoneDurations[idx]
            val pct = if (totalDuration > 0L) (duration.toFloat() / totalDuration) * 100f else 0f
            ZoneTimeEntry(
                zoneIndex = idx + 1,
                zoneLabelResId = ZONE_LABELS[idx],
                durationSec = duration,
                percentage = pct,
                color = ZONE_COLORS[idx]
            )
        }

        return ZoneDistributionData(
            totalActiveTimeSec = totalDuration,
            entries = entries
        )
    }

    /**
     * Calculates 5-zone distribution data from power samples and athlete thresholds.
     *
     * @param samples List of active time and power (Watts) samples.
     * @param thresholds Athlete power thresholds for Zones 1 through 4.
     * @return [ZoneDistributionData] containing exactly 5 entries, or null if telemetry is insufficient.
     */
    fun calculatePowerDistribution(
        samples: List<ZoneSample>,
        thresholds: PowerZoneThresholds
    ): ZoneDistributionData? {
        val validSamples = samples.filter { it.value > 0 }
        if (validSamples.isEmpty()) return null

        val isDegenerate = validSamples.size > 1 && validSamples.first().timeActiveSec == validSamples.last().timeActiveSec
        val zoneDurations = LongArray(5)

        for (i in validSamples.indices) {
            val sample = validSamples[i]
            val zoneIdx = determinePowerZone(sample.value, thresholds)

            val dt = if (isDegenerate) {
                1L
            } else if (i < validSamples.size - 1) {
                val nextSample = validSamples[i + 1]
                val rawDt = nextSample.timeActiveSec - sample.timeActiveSec
                when {
                    rawDt < 0L -> 0L
                    rawDt > MAX_DELTA_T_SEC -> MAX_DELTA_T_SEC
                    else -> rawDt
                }
            } else {
                DEFAULT_LAST_SAMPLE_DURATION_SEC
            }

            zoneDurations[zoneIdx] += dt
        }

        val totalDuration = zoneDurations.sum()
        if (totalDuration <= 0L) return null

        val entries = (0..4).map { idx ->
            val duration = zoneDurations[idx]
            val pct = if (totalDuration > 0L) (duration.toFloat() / totalDuration) * 100f else 0f
            ZoneTimeEntry(
                zoneIndex = idx + 1,
                zoneLabelResId = ZONE_LABELS[idx],
                durationSec = duration,
                percentage = pct,
                color = ZONE_COLORS[idx]
            )
        }

        return ZoneDistributionData(
            totalActiveTimeSec = totalDuration,
            entries = entries
        )
    }

    /**
     * Maps an instantaneous heart rate value to a 0-indexed zone (0..4).
     */
    private fun determineHeartRateZone(hr: Int, thresholds: HeartRateZoneThresholds): Int {
        return when {
            hr <= thresholds.z1Max -> 0
            hr <= thresholds.z2Max -> 1
            hr <= thresholds.z3Max -> 2
            hr <= thresholds.z4Max -> 3
            else -> 4
        }
    }

    /**
     * Maps an instantaneous cycling power value (Watts) to a 0-indexed zone (0..4).
     */
    private fun determinePowerZone(pwr: Int, thresholds: PowerZoneThresholds): Int {
        return when {
            pwr <= thresholds.z1Max -> 0
            pwr <= thresholds.z2Max -> 1
            pwr <= thresholds.z3Max -> 2
            pwr <= thresholds.z4Max -> 3
            else -> 4
        }
    }
}
