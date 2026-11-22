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
import com.atrainingtracker.banalservice.devices.RrBeatNormalization
import java.util.Arrays
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
 *
 * @property inputArtifactFlags Optional 1:1 per-input-beat artifact classification aligned with the
 * input beat sequence before split/merge expansion (`REQ-HRV-048.1`).
 * @property inputGapBeats Optional 1:1 per-input-beat missed/gap beat count aligned with the input
 * beat sequence (`REQ-HRV-048.1`).
 */
data class FilterResult(
    val beats: List<FilteredRrBeat>,
    val artifactCount: Int,
    val totalCount: Int,
    val artifactRatePercent: Double,
    val inputArtifactFlags: List<Int> = emptyList(),
    val inputGapBeats: List<Int> = emptyList()
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

        val rawRrs = DoubleArray(n) { beats[it].rrMs }
        val dRrs = computeDifferences(rawRrs)
        val localMedians = computeLocalMediansCentered(rawRrs)
        val mRrs = DoubleArray(n) { rawRrs[it] - localMedians[it] }
        val qdScratch = DoubleArray(QD_WINDOW_SIZE + 1)

        val classifications = IntArray(n)
        val th2List = DoubleArray(n)

        for (i in 0 until n) {
            val event = beats[i]
            if (isGapOrOutOfPhysiologicalRange(event)) {
                classifications[i] = RrBeatEvent.ARTIFACT_GAP
                continue
            }

            // 91-beat centered QD window
            val qdStart = max(0, i - QD_WINDOW_SIZE / 2)
            val qdEnd = min(n, i + QD_WINDOW_SIZE / 2 + 1)

            val th1 = max(c1 * computeQuartileDeviationWindow(dRrs, qdStart, qdEnd, qdScratch), minThresholdMs)
            val th2 = max(c2 * computeQuartileDeviationWindow(mRrs, qdStart, qdEnd, qdScratch), minThresholdMs)
            th2List[i] = th2

            val dRR = dRrs[i]
            val mRR = mRrs[i]
            val nextDRR = if (i + 1 < n) dRrs[i + 1] else 0.0
            val prevDRR = if (i > 0) dRrs[i - 1] else 0.0
            val med = localMedians[i]

            val nextRR = if (i + 1 < n) rawRrs[i + 1] else null
            val prevRR = if (i > 0) rawRrs[i - 1] else null

            classifications[i] = classifyBeat(event.rrMs, dRR, mRR, nextDRR, prevDRR, th1, th2, med, nextRR, prevRR)
        }

        return processBeats(beats, localMedians, n, classifications, th2List)
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

        val rawRrs = DoubleArray(n) { buffer[it].rrMs }
        val dRrs = computeDifferences(rawRrs)
        val localMedians = computeLocalMediansCentered(rawRrs)
        val allMRrs = DoubleArray(n) { rawRrs[it] - localMedians[it] }
        val qdScratch = DoubleArray(QD_WINDOW_SIZE + 1) // Capacity 92 covers [i - 86, i + 6) (REQ-UI-2800.2)
        val evaluateUntil = n - LOOKAHEAD_BEATS

        val classifications = IntArray(evaluateUntil)
        val th2List = DoubleArray(evaluateUntil)

        for (i in 0 until evaluateUntil) {
            val event = buffer[i]
            if (isGapOrOutOfPhysiologicalRange(event)) {
                classifications[i] = RrBeatEvent.ARTIFACT_GAP
                continue
            }

            // Trailing quartile window: up to 86 beats back + current + 5 beats lookahead (up to 92 beats)
            val qdStart = max(0, i - (QD_WINDOW_SIZE - LOOKAHEAD_BEATS))
            val qdEnd = min(n, i + LOOKAHEAD_BEATS + 1)

            val th1 = max(c1 * computeQuartileDeviationWindow(dRrs, qdStart, qdEnd, qdScratch), minThresholdMs)
            val th2 = max(c2 * computeQuartileDeviationWindow(allMRrs, qdStart, qdEnd, qdScratch), minThresholdMs)
            th2List[i] = th2

            val dRR = dRrs[i]
            val mRR = allMRrs[i]
            val nextDRR = if (i + 1 < n) dRrs[i + 1] else 0.0
            val prevDRR = if (i > 0) dRrs[i - 1] else 0.0
            val med = localMedians[i]

            val nextRR = if (i + 1 < n) rawRrs[i + 1] else null
            val prevRR = if (i > 0) rawRrs[i - 1] else null

            classifications[i] = classifyBeat(event.rrMs, dRR, mRR, nextDRR, prevDRR, th1, th2, med, nextRR, prevRR)
        }

        return processBeats(buffer, localMedians, evaluateUntil, classifications, th2List)
    }

    private fun processBeats(
        beats: List<RrBeatEvent>,
        localMedians: DoubleArray,
        limit: Int,
        classifications: IntArray,
        th2List: DoubleArray
    ): FilterResult {
        val filteredList = ArrayList<FilteredRrBeat>(limit)
        val inputFlags = IntArray(limit)
        val inputGaps = IntArray(limit)
        var artifactCount = 0
        var i = 0

        while (i < limit) {
            val event = beats[i]
            if (classifications[i] == RrBeatEvent.ARTIFACT_GAP || isGapOrOutOfPhysiologicalRange(event)) {
                val rawMed = localMedians[i]
                val safeMed = if (rawMed.isFinite() &&
                    rawMed in RrBeatNormalization.MIN_RR_MS..RrBeatNormalization.MAX_RR_MS
                ) {
                    rawMed
                } else {
                    RrBeatNormalization.DEFAULT_REFERENCE_RR_MS
                }
                val gapCount = when {
                    event.gapBeats > 0 -> event.gapBeats + 1
                    event.rrMs > RrBeatNormalization.MAX_RR_MS -> max(2, (event.rrMs / safeMed).roundToInt())
                    else -> 1
                }
                inputFlags[i] = RrBeatEvent.ARTIFACT_GAP
                inputGaps[i] = max(event.gapBeats, gapCount - 1)
                val splitRaw = event.rrMs / gapCount
                for (k in 0 until gapCount) {
                    val beatTime = event.beatTimestampMs - ((gapCount - 1 - k) * safeMed).toLong()
                    filteredList.add(FilteredRrBeat(beatTime, splitRaw, safeMed, RrBeatEvent.ARTIFACT_GAP))
                    artifactCount++
                }
                i++
                continue
            }

            val classification = classifications[i]
            val med = localMedians[i]

            if (classification == RrBeatEvent.ARTIFACT_SHORT) {
                val nextIndex = i + 1
                if (nextIndex < limit) {
                    val nextEvent = beats[nextIndex]
                    val nextClass = classifications[nextIndex]
                    val mergedRr = event.rrMs + nextEvent.rrMs
                    if (nextClass == RrBeatEvent.ARTIFACT_SHORT || abs(mergedRr - med) <= 0.3 * med) {
                        val corrected = if (abs(mergedRr - med) <= th2List[i]) mergedRr else med
                        inputFlags[i] = RrBeatEvent.ARTIFACT_SHORT
                        inputGaps[i] = 0
                        inputFlags[nextIndex] = RrBeatEvent.ARTIFACT_SHORT
                        inputGaps[nextIndex] = 0
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
                inputFlags[i] = RrBeatEvent.ARTIFACT_SHORT
                inputGaps[i] = 0
                filteredList.add(FilteredRrBeat(event.beatTimestampMs, event.rrMs, med, RrBeatEvent.ARTIFACT_SHORT))
                artifactCount++
                i++
                continue
            }

            if (classification == RrBeatEvent.ARTIFACT_MISSED ||
                (classification == RrBeatEvent.ARTIFACT_LONG && event.rrMs >= 1.5 * med)
            ) {
                val k = max(2, (event.rrMs / med).roundToInt())
                inputFlags[i] = classification
                inputGaps[i] = k - 1
                val splitRr = event.rrMs / k
                for (j in 0 until k) {
                    val beatTime = event.beatTimestampMs - ((k - 1 - j) * splitRr).toLong()
                    filteredList.add(FilteredRrBeat(beatTime, splitRr, splitRr, classification))
                    artifactCount++
                }
                i++
                continue
            }

            inputFlags[i] = classification
            inputGaps[i] = 0
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
        return FilterResult(
            beats = filteredList,
            artifactCount = artifactCount,
            totalCount = total,
            artifactRatePercent = rate,
            inputArtifactFlags = inputFlags.toList(),
            inputGapBeats = inputGaps.toList()
        )
    }

    private fun isGapOrOutOfPhysiologicalRange(event: RrBeatEvent): Boolean {
        return event.artifactFlag == RrBeatEvent.ARTIFACT_GAP ||
            RrBeatNormalization.isOutOfPhysiologicalRange(event.rrMs)
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

    private fun computeDifferences(series: DoubleArray): DoubleArray {
        val diffs = DoubleArray(series.size)
        for (i in 1 until series.size) {
            diffs[i] = series[i] - series[i - 1]
        }
        return diffs
    }

    private fun computeLocalMediansCentered(series: DoubleArray): DoubleArray {
        val n = series.size
        val medians = DoubleArray(n)
        val scratch = DoubleArray(2 * MEDIAN_HALF_WINDOW + 1)
        for (i in 0 until n) {
            val start = max(0, i - MEDIAN_HALF_WINDOW)
            val end = min(n, i + MEDIAN_HALF_WINDOW + 1)
            val len = end - start
            System.arraycopy(series, start, scratch, 0, len)
            Arrays.sort(scratch, 0, len)
            medians[i] = if (len % 2 == 1) {
                scratch[len / 2]
            } else {
                (scratch[len / 2 - 1] + scratch[len / 2]) / 2.0
            }
        }
        return medians
    }

    private fun computeQuartileDeviationWindow(
        values: DoubleArray,
        start: Int,
        end: Int,
        scratch: DoubleArray
    ): Double {
        val len = end - start
        if (len < 4) {
            return minThresholdMs / c1
        }
        val buf = if (len <= scratch.size) scratch else DoubleArray(len)
        System.arraycopy(values, start, buf, 0, len)
        Arrays.sort(buf, 0, len)
        val q1 = percentileArray(buf, len, 0.25)
        val q3 = percentileArray(buf, len, 0.75)
        return (q3 - q1) / 2.0
    }

    internal fun computeQuartileDeviation(values: List<Double>): Double {
        if (values.size < 4) {
            return minThresholdMs / c1
        }
        val arr = DoubleArray(values.size) { values[it] }
        return computeQuartileDeviationWindow(arr, 0, arr.size, arr)
    }

    private fun percentileArray(sorted: DoubleArray, len: Int, p: Double): Double {
        val pos = p * (len - 1)
        val idx = pos.toInt()
        val frac = pos - idx
        return if (idx + 1 < len) {
            sorted[idx] + frac * (sorted[idx + 1] - sorted[idx])
        } else {
            sorted[idx]
        }
    }
}
