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
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral equivalence, 92-element causal window boundary, multi-thread safety, and performance
 * verification for [RrArtifactFilter] (`ATT-8126`, `REQ-UI-2800.2`, `TST-UI-2800.2`).
 */
class RrArtifactFilterPerformanceEquivalenceTest {

    private fun buildRich300BeatSeries(): List<RrBeatEvent> {
        val list = ArrayList<RrBeatEvent>(300)
        var ts = 1_000_000L
        for (i in 0 until 300) {
            val baseRr = 820.0 + 35.0 * sin(i * 0.17) + ((i % 7) - 3) * 4.0
            val rr = when (i) {
                25 -> baseRr * 0.62 // ectopic premature
                26 -> baseRr * 1.42 // compensatory pause
                95 -> baseRr * 2.02 // missed beat (past QD_WINDOW_SIZE + 1 == 92!)
                140 -> baseRr * 0.55 // short beat
                141 -> baseRr * 0.52 // paired short beat (merge)
                190 -> 2600.0 // physiological gap (> MAX_RR_MS)
                245 -> baseRr * 1.85 // long beat
                else -> baseRr
            }
            ts += rr.toLong()
            list.add(
                RrBeatEvent(
                    beatTimestampMs = ts,
                    rrMs = rr,
                    rawCounter = i,
                    protocol = ProtocolType.SIMULATED
                )
            )
        }
        return list
    }

    /** Independent naive reference Lipponen & Tarvainen (2019) implementation using boxed List.sorted(). */
    private fun referenceFilter(beats: List<RrBeatEvent>, causal: Boolean): FilterResult {
        val c1 = RrArtifactFilter.DEFAULT_C1
        val c2 = RrArtifactFilter.DEFAULT_C2
        val minTh = RrArtifactFilter.DEFAULT_MIN_THRESHOLD_MS
        val n = beats.size
        val limit = if (causal) n - RrArtifactFilter.LOOKAHEAD_BEATS else n
        val rawRrs = beats.map { it.rrMs }
        val dRrs = rawRrs.indices.map { i -> if (i == 0) 0.0 else rawRrs[i] - rawRrs[i - 1] }

        fun refMed(idx: Int): Double {
            val s = max(0, idx - RrArtifactFilter.MEDIAN_HALF_WINDOW)
            val e = min(n, idx + RrArtifactFilter.MEDIAN_HALF_WINDOW + 1)
            val w = rawRrs.subList(s, e).sorted()
            return if (w.size % 2 == 1) w[w.size / 2] else (w[w.size / 2 - 1] + w[w.size / 2]) / 2.0
        }

        fun refQd(values: List<Double>): Double {
            if (values.size < 4) return minTh / c1
            val s = values.sorted()
            fun pct(p: Double): Double {
                val pos = p * (s.size - 1)
                val idx = pos.toInt()
                val frac = pos - idx
                return if (idx + 1 < s.size) s[idx] + frac * (s[idx + 1] - s[idx]) else s[idx]
            }
            return (pct(0.75) - pct(0.25)) / 2.0
        }

        val mRrs = rawRrs.indices.map { i -> rawRrs[i] - refMed(i) }
        val classifications = IntArray(limit)
        val th2List = DoubleArray(limit)

        for (i in 0 until limit) {
            val ev = beats[i]
            if (ev.artifactFlag == RrBeatEvent.ARTIFACT_GAP || RrBeatNormalization.isOutOfPhysiologicalRange(ev.rrMs)) {
                classifications[i] = RrBeatEvent.ARTIFACT_GAP
                continue
            }
            val qdStart = if (causal) {
                max(0, i - (RrArtifactFilter.QD_WINDOW_SIZE - RrArtifactFilter.LOOKAHEAD_BEATS))
            } else {
                max(0, i - RrArtifactFilter.QD_WINDOW_SIZE / 2)
            }
            val qdEnd = if (causal) {
                min(n, i + RrArtifactFilter.LOOKAHEAD_BEATS + 1)
            } else {
                min(n, i + RrArtifactFilter.QD_WINDOW_SIZE / 2 + 1)
            }
            val th1 = max(c1 * refQd(dRrs.subList(qdStart, qdEnd)), minTh)
            val th2 = max(c2 * refQd(mRrs.subList(qdStart, qdEnd)), minTh)
            th2List[i] = th2

            val dRR = dRrs[i]
            val mRR = mRrs[i]
            val nextDRR = if (i + 1 < n) dRrs[i + 1] else 0.0
            val prevDRR = if (i > 0) dRrs[i - 1] else 0.0
            val med = refMed(i)
            val nextRR = if (i + 1 < n) rawRrs[i + 1] else null

            val hasComp = nextRR != null && ((nextRR - med) > 0.3 * th2 || (ev.rrMs + nextRR) > 1.5 * med)
            val isPrem = hasComp && ((dRR < -th1 && nextDRR > th1) || (dRR < -0.8 * th1 && nextDRR > 0.8 * th1 && abs(mRR) > 0.5 * th2))
            val isComp = (dRR > th1 && prevDRR < -th1) && (ev.rrMs - med > 0.3 * th2)
            classifications[i] = when {
                isPrem || isComp -> RrBeatEvent.ARTIFACT_ECTOPIC
                dRR > th1 && mRR > th2 -> RrBeatEvent.ARTIFACT_MISSED
                dRR < -th1 && mRR < -th2 -> RrBeatEvent.ARTIFACT_SHORT
                mRR > th2 -> RrBeatEvent.ARTIFACT_LONG
                mRR < -th2 -> RrBeatEvent.ARTIFACT_SHORT
                else -> RrBeatEvent.ARTIFACT_NORMAL
            }
        }

        val out = ArrayList<FilteredRrBeat>(limit)
        val inFlags = IntArray(limit)
        val inGaps = IntArray(limit)
        var art = 0
        var i = 0
        while (i < limit) {
            val ev = beats[i]
            val med = refMed(i)
            if (classifications[i] == RrBeatEvent.ARTIFACT_GAP || RrBeatNormalization.isOutOfPhysiologicalRange(ev.rrMs)) {
                val safeMed = if (med.isFinite() && med in RrBeatNormalization.MIN_RR_MS..RrBeatNormalization.MAX_RR_MS) med else RrBeatNormalization.DEFAULT_REFERENCE_RR_MS
                val gapCount = when {
                    ev.gapBeats > 0 -> ev.gapBeats + 1
                    ev.rrMs > RrBeatNormalization.MAX_RR_MS -> max(2, (ev.rrMs / safeMed).roundToInt())
                    else -> 1
                }
                inFlags[i] = RrBeatEvent.ARTIFACT_GAP
                inGaps[i] = max(ev.gapBeats, gapCount - 1)
                val split = ev.rrMs / gapCount
                for (k in 0 until gapCount) {
                    val bt = ev.beatTimestampMs - ((gapCount - 1 - k) * safeMed).toLong()
                    out.add(FilteredRrBeat(bt, split, safeMed, RrBeatEvent.ARTIFACT_GAP))
                    art++
                }
                i++
                continue
            }
            val cls = classifications[i]
            if (cls == RrBeatEvent.ARTIFACT_SHORT) {
                if (i + 1 < limit) {
                    val ne = beats[i + 1]
                    val merged = ev.rrMs + ne.rrMs
                    if (classifications[i + 1] == RrBeatEvent.ARTIFACT_SHORT || abs(merged - med) <= 0.3 * med) {
                        val corr = if (abs(merged - med) <= th2List[i]) merged else med
                        inFlags[i] = RrBeatEvent.ARTIFACT_SHORT
                        inFlags[i + 1] = RrBeatEvent.ARTIFACT_SHORT
                        out.add(FilteredRrBeat(ne.beatTimestampMs, merged, corr, RrBeatEvent.ARTIFACT_SHORT))
                        art++
                        i += 2
                        continue
                    }
                }
                inFlags[i] = RrBeatEvent.ARTIFACT_SHORT
                out.add(FilteredRrBeat(ev.beatTimestampMs, ev.rrMs, med, RrBeatEvent.ARTIFACT_SHORT))
                art++
                i++
                continue
            }
            if (cls == RrBeatEvent.ARTIFACT_MISSED || (cls == RrBeatEvent.ARTIFACT_LONG && ev.rrMs >= 1.5 * med)) {
                val k = max(2, (ev.rrMs / med).roundToInt())
                inFlags[i] = cls
                inGaps[i] = k - 1
                val split = ev.rrMs / k
                for (j in 0 until k) {
                    val bt = ev.beatTimestampMs - ((k - 1 - j) * split).toLong()
                    out.add(FilteredRrBeat(bt, split, split, cls))
                    art++
                }
                i++
                continue
            }
            inFlags[i] = cls
            if (cls != RrBeatEvent.ARTIFACT_NORMAL) {
                out.add(FilteredRrBeat(ev.beatTimestampMs, ev.rrMs, med, cls))
                art++
            } else {
                out.add(FilteredRrBeat(ev.beatTimestampMs, ev.rrMs, ev.rrMs, RrBeatEvent.ARTIFACT_NORMAL))
            }
            i++
        }
        val total = out.size
        return FilterResult(out, art, total, if (total > 0) (art.toDouble() / total) * 100.0 else 0.0, inFlags.toList(), inGaps.toList())
    }

    @Test
    fun testFilterLiveCausalAndBidirectional_bitExactEquivalenceOver300Beats_tstUi2800_2() {
        val series = buildRich300BeatSeries()
        val filter = RrArtifactFilter()

        val actualCausal = filter.filterLiveCausal(series)
        val expectedCausal = referenceFilter(series, causal = true)
        assertEquals(expectedCausal, actualCausal)
        assertTrue("Rich 300-beat test fixture must detect artifacts", actualCausal.artifactCount > 0)

        val actualBidirectional = filter.filterBidirectionalBeats(series)
        val expectedBidirectional = referenceFilter(series, causal = false)
        assertEquals(expectedBidirectional, actualBidirectional)
    }

    @Test
    fun testSharedFilterInstance_concurrentThreadsHaveZeroScratchBufferCorruption_tstUi2800_2() {
        val series = buildRich300BeatSeries()
        val filter = RrArtifactFilter()
        val expectedCausal = filter.filterLiveCausal(series)
        val expectedBidirectional = filter.filterBidirectionalBeats(series)

        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = (0 until 8).map { tid ->
                Callable {
                    repeat(20) { iter ->
                        val res = if ((tid + iter) % 2 == 0) {
                            filter.filterLiveCausal(series)
                        } else {
                            filter.filterBidirectionalBeats(series)
                        }
                        val expected = if ((tid + iter) % 2 == 0) expectedCausal else expectedBidirectional
                        assertEquals(expected, res)
                    }
                }
            }
            pool.invokeAll(tasks).forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun testFilterLiveCausal_50RunsOf300BeatsWithinLatencyBudget_tstUi2800_2() {
        val series = buildRich300BeatSeries()
        val filter = RrArtifactFilter()
        // Warm up JIT
        repeat(5) { filter.filterLiveCausal(series) }

        val startNs = System.nanoTime()
        repeat(50) {
            val result = filter.filterLiveCausal(series)
            assertEquals(295, result.inputArtifactFlags.size)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        assertTrue("50x 300-beat causal evaluations took ${elapsedMs}ms (budget < 250ms)", elapsedMs < 250L)
    }
}
