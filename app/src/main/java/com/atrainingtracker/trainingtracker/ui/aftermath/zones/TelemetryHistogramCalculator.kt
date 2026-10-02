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

import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Pure calculation engine for fine-grained telemetry frequency histograms.
 *
 * Divides continuous metric ranges into narrow bins (e.g. 2 bpm for Heart Rate, 10 W for Power),
 * accumulates active duration spent in each bin, and maps each bin's midpoint to the athlete's
 * active training zone thresholds with matching color shading.
 */
object TelemetryHistogramCalculator {

    const val DEFAULT_HR_BIN_WIDTH = 2
    const val DEFAULT_POWER_BIN_WIDTH = 10
    private const val MAX_DELTA_T_SEC = 5L
    private const val DEFAULT_LAST_SAMPLE_DURATION_SEC = 1L

    private val ZONE_COLORS = arrayOf(
        TTColor.Zone1,
        TTColor.Zone2,
        TTColor.Zone3,
        TTColor.Zone4,
        TTColor.Zone5
    )

    /**
     * Calculates a fine-grained frequency histogram for Heart Rate telemetry.
     *
     * @param samples Timestamped heart rate telemetry samples.
     * @param thresholds Athlete HR thresholds for Zones 1 through 4.
     * @param binWidth Width of each frequency bin in BPM (default: 2 BPM).
     * @return [TelemetryHistogramData] or null if insufficient valid samples.
     */
    fun calculateHeartRateHistogram(
        samples: List<ZoneSample>,
        thresholds: HeartRateZoneThresholds,
        binWidth: Int = DEFAULT_HR_BIN_WIDTH
    ): TelemetryHistogramData? {
        return calculateHistogram(
            samples = samples,
            binWidth = binWidth.coerceAtLeast(1)
        ) { hr ->
            when {
                hr <= thresholds.z1Max -> 0
                hr <= thresholds.z2Max -> 1
                hr <= thresholds.z3Max -> 2
                hr <= thresholds.z4Max -> 3
                else -> 4
            }
        }
    }

    /**
     * Calculates a fine-grained frequency histogram for Cycling Power telemetry.
     *
     * @param samples Timestamped power telemetry samples.
     * @param thresholds Athlete power thresholds for Zones 1 through 4.
     * @param binWidth Width of each frequency bin in Watts (default: 10 W).
     * @return [TelemetryHistogramData] or null if insufficient valid samples.
     */
    fun calculatePowerHistogram(
        samples: List<ZoneSample>,
        thresholds: PowerZoneThresholds,
        binWidth: Int = DEFAULT_POWER_BIN_WIDTH
    ): TelemetryHistogramData? {
        return calculateHistogram(
            samples = samples,
            binWidth = binWidth.coerceAtLeast(1)
        ) { pwr ->
            when {
                pwr <= thresholds.z1Max -> 0
                pwr <= thresholds.z2Max -> 1
                pwr <= thresholds.z3Max -> 2
                pwr <= thresholds.z4Max -> 3
                else -> 4
            }
        }
    }

    /**
     * Generic histogram calculation across numeric metric samples with duration accumulation.
     */
    private inline fun calculateHistogram(
        samples: List<ZoneSample>,
        binWidth: Int,
        getZoneForValue: (Int) -> Int
    ): TelemetryHistogramData? {
        val validSamples = samples.filter { it.value > 0 }
        if (validSamples.isEmpty()) return null

        val minVal = validSamples.minOf { it.value }
        val maxVal = validSamples.maxOf { it.value }

        val dataMin = (minVal / binWidth) * binWidth
        val dataMax = ((maxVal / binWidth) + 1) * binWidth
        val numBins = ((dataMax - dataMin) / binWidth).coerceAtLeast(1)

        val isDegenerate = validSamples.size > 1 &&
            validSamples.first().timeActiveSec == validSamples.last().timeActiveSec

        val binDurations = LongArray(numBins)

        for (i in validSamples.indices) {
            val sample = validSamples[i]
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

            val binIdx = ((sample.value - dataMin) / binWidth).coerceIn(0, numBins - 1)
            binDurations[binIdx] += dt
        }

        val totalDuration = binDurations.sum()
        if (totalDuration <= 0L) return null

        val bins = (0 until numBins).map { idx ->
            val rangeMin = dataMin + idx * binWidth
            val rangeMax = rangeMin + binWidth
            val midpoint = (rangeMin + rangeMax) / 2
            val zoneIdx = getZoneForValue(midpoint).coerceIn(0, 4)
            val duration = binDurations[idx]
            val percentage = if (totalDuration > 0L) (duration.toFloat() / totalDuration) * 100f else 0f

            TelemetryHistogramBin(
                binIndex = idx,
                rangeMin = rangeMin,
                rangeMax = rangeMax,
                durationSec = duration,
                percentage = percentage,
                zoneIndex = zoneIdx + 1,
                color = ZONE_COLORS[zoneIdx]
            )
        }

        return TelemetryHistogramData(
            binWidth = binWidth,
            dataMin = dataMin,
            dataMax = dataMax,
            totalActiveTimeSec = totalDuration,
            bins = bins
        )
    }
}
