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

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.trainingtracker.ui.theme.DarkOutline
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Pure calculation engine for fine-grained telemetry frequency histograms.
 *
 * Divides continuous metric ranges into narrow bins (e.g. 2 bpm for Heart Rate, 10 W for Power),
 * accumulates active duration spent in each bin, and maps each bin's midpoint to the athlete's
 * active training zone thresholds with matching color shading.
 *
 * For Cycling Power histograms, automatically identifies heavy right-tailed distributions and
 * folds rare sprint spikes above a statistical 99th percentile cutoff (P99, floored at Zone 4
 * upper boundary and rounded to 50 W) into an overflow bar with a linked Sprint Tail dataset (REQ-UI-419).
 */
object TelemetryHistogramCalculator {

    const val DEFAULT_HR_BIN_WIDTH = 2
    const val DEFAULT_POWER_BIN_WIDTH = 10
    private const val MAX_DELTA_T_SEC = 5L
    private const val DEFAULT_LAST_SAMPLE_DURATION_SEC = 1L

    private const val MIN_TAIL_BINS = 10
    private const val MIN_TAIL_RATIO = 1.25f
    private const val CUTOFF_ROUNDING_W = 50
    private const val TAIL_PERCENTILE_RATIO = 0.985

    private val ZONE_COLORS = arrayOf(
        TTColor.Zone1,
        TTColor.Zone2,
        TTColor.Zone3,
        TTColor.Zone4,
        TTColor.Zone5,
        TTColor.Zone6,
        TTColor.Zone7
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
     * Calculates a fine-grained frequency histogram for Cycling Power telemetry across all 7
     * Coggan power zones (Z1–Z7, REQ-UI-420), with optional sprint tail folding (REQ-UI-419).
     *
     * If the workout exhibits an extended high-power tail (P99 <= tailCutoff and spikes >= 1.25 * cutoff),
     * rare sprint spikes are folded into a rightmost overflow bar in the Main View (dynamically reflecting
     * the peak sprint zone) and an interactive Sprint Tail View dataset is generated. Otherwise, returns
     * a standard uniform linear histogram.
     *
     * @param samples Timestamped power telemetry samples.
     * @param thresholds Athlete power thresholds for Zones 1 through 6.
     * @param binWidth Width of each frequency bin in Watts (default: 10 W).
     * @return [TelemetryHistogramData] or null if insufficient valid samples.
     */
    fun calculatePowerHistogram(
        samples: List<ZoneSample>,
        thresholds: PowerZoneThresholds,
        binWidth: Int = DEFAULT_POWER_BIN_WIDTH
    ): TelemetryHistogramData? {
        val linearData = calculateHistogram(
            samples = samples,
            binWidth = binWidth.coerceAtLeast(1)
        ) { pwr ->
            when {
                pwr <= thresholds.z1Max -> 0
                pwr <= thresholds.z2Max -> 1
                pwr <= thresholds.z3Max -> 2
                pwr <= thresholds.z4Max -> 3
                pwr <= thresholds.z5Max -> 4
                pwr <= thresholds.z6Max -> 5
                else -> 6
            }
        } ?: return null

        val tailCutoff = calculateTailCutoff(
            bins = linearData.bins,
            totalDuration = linearData.totalActiveTimeSec,
            thresholds = thresholds,
            binWidth = linearData.binWidth,
            dataMax = linearData.dataMax
        ) ?: return linearData

        val regularBins = linearData.bins.filter { it.rangeMax <= tailCutoff }
        val tailBins = linearData.bins.filter { it.rangeMin >= tailCutoff }
        val tailDuration = tailBins.sumOf { it.durationSec }
        val regularDuration = regularBins.sumOf { it.durationSec }
        val totalDuration = linearData.totalActiveTimeSec

        // Rightmost Overflow Bin for Main View
        val peakTailBin = tailBins.maxByOrNull { it.zoneIndex }
        val overflowBin = TelemetryHistogramBin(
            binIndex = regularBins.size,
            rangeMin = tailCutoff,
            rangeMax = linearData.dataMax,
            durationSec = tailDuration,
            percentage = if (totalDuration > 0L) (tailDuration.toFloat() / totalDuration) * 100f else 0f,
            zoneIndex = peakTailBin?.zoneIndex ?: 7,
            color = peakTailBin?.color ?: TTColor.Zone7,
            isOverflow = true,
            isReturn = false
        )

        // Leftmost Return Bin for Sprint Tail View
        val returnBin = TelemetryHistogramBin(
            binIndex = 0,
            rangeMin = linearData.dataMin,
            rangeMax = tailCutoff,
            durationSec = regularDuration,
            percentage = if (totalDuration > 0L) (regularDuration.toFloat() / totalDuration) * 100f else 0f,
            zoneIndex = 1,
            color = DarkOutline,
            isOverflow = false,
            isReturn = true
        )

        val sprintBins = tailBins.mapIndexed { idx, bin ->
            bin.copy(
                binIndex = idx + 1,
                isOverflow = false,
                isReturn = false
            )
        }

        val sprintTailData = TelemetryHistogramData(
            binWidth = linearData.binWidth,
            dataMin = tailCutoff,
            dataMax = linearData.dataMax,
            totalActiveTimeSec = totalDuration,
            bins = listOf(returnBin) + sprintBins,
            tailCutoff = tailCutoff,
            sprintTail = null
        )

        return linearData.copy(
            bins = regularBins + overflowBin,
            tailCutoff = tailCutoff,
            sprintTail = sprintTailData
        )
    }

    /**
     * Determines whether cycling power telemetry exhibits an extended sprint tail eligible for folding.
     *
     * Computes the 99th percentile active duration threshold (P99), enforces athlete Zone 4 upper boundary
     * ([PowerZoneThresholds.z4Max]) as a floor, and rounds up to the nearest 50 W boundary. Evaluates three gating
     * conditions:
     * 1. `dataMax - tailCutoff >= MIN_TAIL_BINS * binWidth` (at least 100 W in tail);
     * 2. `dataMax >= tailCutoff * MIN_TAIL_RATIO` (tail exceeds cutoff by at least 25%);
     * 3. Active duration in bins > tailCutoff is strictly > 0.
     *
     * @param bins Raw frequency bins covering the workout power range.
     * @param totalDuration Total active workout duration across all bins.
     * @param thresholds Athlete power zone thresholds.
     * @param binWidth Width of each bin in Watts.
     * @param dataMax Maximum power value recorded in the workout.
     * @return The 50 W aligned tail cutoff Wattage, or null if gating conditions are not met.
     */
    fun calculateTailCutoff(
        bins: List<TelemetryHistogramBin>,
        totalDuration: Long,
        thresholds: PowerZoneThresholds,
        binWidth: Int,
        dataMax: Int
    ): Int? {
        if (bins.isEmpty() || totalDuration <= 0L) return null

        val targetDuration = TAIL_PERCENTILE_RATIO * totalDuration.toDouble()
        var accumulatedDuration = 0L
        var p99Candidate = bins.first().rangeMin

        for (bin in bins) {
            accumulatedDuration += bin.durationSec
            if (accumulatedDuration.toDouble() >= targetDuration) {
                p99Candidate = bin.rangeMax
                break
            }
        }

        // Floor at athlete's Zone 4 upper boundary (thresholds.z4Max)
        val rawCutoff = maxOf(p99Candidate, thresholds.z4Max)

        // Round up to clean 50 W boundary
        val tailCutoff = ((rawCutoff + CUTOFF_ROUNDING_W - 1) / CUTOFF_ROUNDING_W) * CUTOFF_ROUNDING_W

        // Gating Condition 1: At least MIN_TAIL_BINS (10 bins = 100 W) in the tail
        val tailWidth = dataMax - tailCutoff
        if (tailWidth < MIN_TAIL_BINS * binWidth) {
            return null
        }

        // Gating Condition 2: dataMax >= tailCutoff * 1.25
        if (dataMax.toFloat() < tailCutoff * MIN_TAIL_RATIO) {
            return null
        }

        // Gating Condition 3: Active duration in tail is strictly > 0
        val tailDuration = bins.filter { it.rangeMin >= tailCutoff }.sumOf { it.durationSec }
        if (tailDuration <= 0L) {
            return null
        }

        return tailCutoff
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
            val zoneIdx = getZoneForValue(midpoint).coerceIn(0, ZONE_COLORS.lastIndex)
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
