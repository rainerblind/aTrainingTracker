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

import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Filtered RR beat representation with artifact classification and corrected interval.
 */
data class FilteredRrBeat(
    val beatTimestampMs: Long,
    val rawRrMs: Double,
    val correctedRrMs: Double,
    val artifactFlag: Int,
    val isArtifact: Boolean = artifactFlag != RrBeatEvent.ARTIFACT_NORMAL
)

/**
 * Summary container for a batch filtered RR series.
 */
data class FilterResult(
    val beats: List<FilteredRrBeat>,
    val artifactCount: Int,
    val totalCount: Int,
    val artifactRatePercent: Double
)

/**
 * Robust RR interval artifact detection and correction engine implementing the Lipponen & Tarvainen (2019)
 * quartile subspace algorithm pursuant to REQ-TRK-060.2.
 *
 * Provides:
 * - Mode A: Live causal streaming (5-beat look-ahead buffer + 90-beat trailing quartile window).
 * - Mode B: Post-hoc bidirectional centered window (91-beat centered window for offline / post-workout analysis).
 */
class RrArtifactFilter(
    private val c1: Double = DEFAULT_C1,
    private val c2: Double = DEFAULT_C2,
    private val minThresholdMs: Double = DEFAULT_MIN_THRESHOLD_MS
) {

    companion object {
        const val DEFAULT_C1 = 5.2
        const val DEFAULT_C2 = 5.2
        const val DEFAULT_MIN_THRESHOLD_MS = 20.0
        const val LOOKAHEAD_BEATS = 5
        const val MEDIAN_HALF_WINDOW = 5 // 11-beat window: [-5, +5]
        const val QD_WINDOW_SIZE = 91
    }

    /**
     * Mode B: Post-hoc centered filtering for a series of RR intervals in milliseconds.
     */
    fun filterBidirectional(series: List<Double>): FilterResult {
        if (series.isEmpty()) {
            return FilterResult(emptyList(), 0, 0, 0.0)
        }
        val dummyEvents = series.mapIndexed { index, rr ->
            RrBeatEvent(
                beatTimestampMs = index * 1000L,
                rrMs = rr,
                rawCounter = index,
                protocol = ProtocolType.SIMULATED
            )
        }
        return filterBidirectionalBeats(dummyEvents)
    }

    /**
     * Mode B: Post-hoc centered filtering for a series of [RrBeatEvent]s.
     */
    fun filterBidirectionalBeats(beats: List<RrBeatEvent>): FilterResult {
        val n = beats.size
        if (n == 0) {
            return FilterResult(emptyList(), 0, 0, 0.0)
        }

        val rawRrs = beats.map { it.rrMs }
        val dRrs = computeDifferences(rawRrs)
        val mRrs = computeMedianDeviationsCentered(rawRrs)

        val classifications = IntArray(n)
        val th2List = DoubleArray(n)

        for (i in 0 until n) {
            val event = beats[i]
            if (event.artifactFlag == RrBeatEvent.ARTIFACT_GAP) {
                classifications[i] = RrBeatEvent.ARTIFACT_GAP
                continue
            }

            // 91-beat centered QD window
            val qdStart = max(0, i - QD_WINDOW_SIZE / 2)
            val qdEnd = min(n, i + QD_WINDOW_SIZE / 2 + 1)
            val subD = dRrs.subList(qdStart, qdEnd)
            val subM = mRrs.subList(qdStart, qdEnd)

            val th1 = max(c1 * computeQuartileDeviation(subD), minThresholdMs)
            val th2 = max(c2 * computeQuartileDeviation(subM), minThresholdMs)
            th2List[i] = th2

            val dRR = dRrs[i]
            val mRR = mRrs[i]
            val nextDRR = if (i + 1 < n) dRrs[i + 1] else 0.0
            val prevDRR = if (i > 0) dRrs[i - 1] else 0.0
            val med = computeLocalMedianCentered(rawRrs, i)

            val nextRR = if (i + 1 < n) rawRrs[i + 1] else null
            val prevRR = if (i > 0) rawRrs[i - 1] else null

            classifications[i] = classifyBeat(event.rrMs, dRR, mRR, nextDRR, prevDRR, th1, th2, med, nextRR, prevRR)
        }

        return processBeats(beats, rawRrs, n, classifications, th2List)
    }

    /**
     * Mode A: Causal streaming filter evaluating a buffer with [LOOKAHEAD_BEATS] future look-ahead.
     * Evaluates beats from index 0 up to (buffer.size - 1 - LOOKAHEAD_BEATS).
     */
    fun filterLiveCausal(buffer: List<RrBeatEvent>): FilterResult {
        val n = buffer.size
        if (n <= LOOKAHEAD_BEATS) {
            return FilterResult(emptyList(), 0, 0, 0.0)
        }

        val rawRrs = buffer.map { it.rrMs }
        val dRrs = computeDifferences(rawRrs)
        val evaluateUntil = n - LOOKAHEAD_BEATS

        val classifications = IntArray(evaluateUntil)
        val th2List = DoubleArray(evaluateUntil)

        for (i in 0 until evaluateUntil) {
            val event = buffer[i]
            if (event.artifactFlag == RrBeatEvent.ARTIFACT_GAP) {
                classifications[i] = RrBeatEvent.ARTIFACT_GAP
                continue
            }

            // Trailing quartile window: up to 90 beats back + 5 beats lookahead
            val qdStart = max(0, i - (QD_WINDOW_SIZE - LOOKAHEAD_BEATS))
            val qdEnd = min(n, i + LOOKAHEAD_BEATS + 1)
            val subD = dRrs.subList(qdStart, qdEnd)

            val mRrs = (qdStart until qdEnd).map { idx ->
                rawRrs[idx] - computeLocalMedianCentered(rawRrs, idx)
            }

            val th1 = max(c1 * computeQuartileDeviation(subD), minThresholdMs)
            val th2 = max(c2 * computeQuartileDeviation(mRrs), minThresholdMs)
            th2List[i] = th2

            val dRR = dRrs[i]
            val mRR = rawRrs[i] - computeLocalMedianCentered(rawRrs, i)
            val nextDRR = if (i + 1 < n) dRrs[i + 1] else 0.0
            val prevDRR = if (i > 0) dRrs[i - 1] else 0.0
            val med = computeLocalMedianCentered(rawRrs, i)

            val nextRR = if (i + 1 < n) rawRrs[i + 1] else null
            val prevRR = if (i > 0) rawRrs[i - 1] else null

            classifications[i] = classifyBeat(event.rrMs, dRR, mRR, nextDRR, prevDRR, th1, th2, med, nextRR, prevRR)
        }

        return processBeats(buffer, rawRrs, evaluateUntil, classifications, th2List)
    }

    private fun processBeats(
        beats: List<RrBeatEvent>,
        rawRrs: List<Double>,
        limit: Int,
        classifications: IntArray,
        th2List: DoubleArray
    ): FilterResult {
        val filteredList = ArrayList<FilteredRrBeat>(limit)
        var artifactCount = 0
        var i = 0

        while (i < limit) {
            val event = beats[i]
            if (event.artifactFlag == RrBeatEvent.ARTIFACT_GAP) {
                val med = computeLocalMedianCentered(rawRrs, i)
                val gapCount = max(1, event.gapBeats + 1)
                val splitRaw = event.rrMs / gapCount
                for (k in 0 until gapCount) {
                    val beatTime = event.beatTimestampMs - ((gapCount - 1 - k) * med).toLong()
                    filteredList.add(FilteredRrBeat(beatTime, splitRaw, med, RrBeatEvent.ARTIFACT_GAP))
                    artifactCount++
                }
                i++
                continue
            }

            val classification = classifications[i]
            val med = computeLocalMedianCentered(rawRrs, i)

            if (classification == RrBeatEvent.ARTIFACT_SHORT) {
                val nextIndex = i + 1
                if (nextIndex < limit) {
                    val nextEvent = beats[nextIndex]
                    val nextClass = classifications[nextIndex]
                    val mergedRr = event.rrMs + nextEvent.rrMs
                    if (nextClass == RrBeatEvent.ARTIFACT_SHORT || abs(mergedRr - med) <= 0.3 * med) {
                        val corrected = if (abs(mergedRr - med) <= th2List[i]) mergedRr else med
                        filteredList.add(
                            FilteredRrBeat(
                                nextEvent.beatTimestampMs,
                                mergedRr,
                                corrected,
                                RrBeatEvent.ARTIFACT_SHORT
                            )
                        )
                        artifactCount++
                        i += 2
                        continue
                    }
                }
                filteredList.add(FilteredRrBeat(event.beatTimestampMs, event.rrMs, med, RrBeatEvent.ARTIFACT_SHORT))
                artifactCount++
                i++
                continue
            }

            if (classification == RrBeatEvent.ARTIFACT_MISSED ||
                (classification == RrBeatEvent.ARTIFACT_LONG && event.rrMs >= 1.5 * med)
            ) {
                val k = max(2, (event.rrMs / med).roundToInt())
                val splitRr = event.rrMs / k
                for (j in 0 until k) {
                    val beatTime = event.beatTimestampMs - ((k - 1 - j) * splitRr).toLong()
                    filteredList.add(FilteredRrBeat(beatTime, splitRr, splitRr, classification))
                    artifactCount++
                }
                i++
                continue
            }

            if (classification != RrBeatEvent.ARTIFACT_NORMAL) {
                filteredList.add(FilteredRrBeat(event.beatTimestampMs, event.rrMs, med, classification))
                artifactCount++
            } else {
                filteredList.add(FilteredRrBeat(event.beatTimestampMs, event.rrMs, event.rrMs, RrBeatEvent.ARTIFACT_NORMAL))
            }
            i++
        }

        val total = filteredList.size
        val rate = if (total > 0) (artifactCount.toDouble() / total.toDouble()) * 100.0 else 0.0
        return FilterResult(filteredList, artifactCount, total, rate)
    }

    private fun classifyBeat(
        rr: Double,
        dRR: Double,
        mRR: Double,
        nextDRR: Double,
        prevDRR: Double,
        th1: Double,
        th2: Double,
        med: Double,
        nextRR: Double? = null,
        prevRR: Double? = null
    ): Int {
        // 1. Ectopic beat: premature contraction followed by compensatory pause
        val hasCompensatoryPause = nextRR != null && ((nextRR - med) > 0.3 * th2 || (rr + nextRR) > 1.5 * med)
        val isPrematureWithCompensatoryPause = hasCompensatoryPause && ((dRR < -th1 && nextDRR > th1) || (dRR < -0.8 * th1 && nextDRR > 0.8 * th1 && abs(mRR) > 0.5 * th2))
        val isCompensatoryPause = (dRR > th1 && prevDRR < -th1) && (rr - med > 0.3 * th2)
        if (isPrematureWithCompensatoryPause || isCompensatoryPause) {
            return RrBeatEvent.ARTIFACT_ECTOPIC
        }

        // 2. Missed beat: abnormally long interval (~2x median)
        if (dRR > th1 && mRR > th2) {
            return RrBeatEvent.ARTIFACT_MISSED
        }

        // 3. Extra / short beat: abnormally short interval
        if (dRR < -th1 && mRR < -th2) {
            return RrBeatEvent.ARTIFACT_SHORT
        }

        // 4. General outliers based on median deviation
        if (mRR > th2) {
            return RrBeatEvent.ARTIFACT_LONG
        }
        if (mRR < -th2) {
            return RrBeatEvent.ARTIFACT_SHORT
        }

        return RrBeatEvent.ARTIFACT_NORMAL
    }

    private fun computeDifferences(series: List<Double>): List<Double> {
        val diffs = ArrayList<Double>(series.size)
        for (i in series.indices) {
            if (i == 0) {
                diffs.add(0.0)
            } else {
                diffs.add(series[i] - series[i - 1])
            }
        }
        return diffs
    }

    private fun computeMedianDeviationsCentered(series: List<Double>): List<Double> {
        return series.indices.map { i ->
            series[i] - computeLocalMedianCentered(series, i)
        }
    }

    private fun computeLocalMedianCentered(series: List<Double>, index: Int): Double {
        val start = max(0, index - MEDIAN_HALF_WINDOW)
        val end = min(series.size, index + MEDIAN_HALF_WINDOW + 1)
        val window = series.subList(start, end).sorted()
        val size = window.size
        return if (size % 2 == 1) {
            window[size / 2]
        } else {
            (window[size / 2 - 1] + window[size / 2]) / 2.0
        }
    }

    internal fun computeQuartileDeviation(values: List<Double>): Double {
        if (values.size < 4) {
            return minThresholdMs / c1
        }
        val sorted = values.sorted()
        val q1 = percentile(sorted, 0.25)
        val q3 = percentile(sorted, 0.75)
        return (q3 - q1) / 2.0
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        val pos = p * (sorted.size - 1)
        val idx = pos.toInt()
        val frac = pos - idx
        return if (idx + 1 < sorted.size) {
            sorted[idx] + frac * (sorted[idx + 1] - sorted[idx])
        } else {
            sorted[idx]
        }
    }
}
