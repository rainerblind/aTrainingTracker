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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail

import com.atrainingtracker.trainingtracker.ui.map.PathPoint

/**
 * Status of autonomic durability evaluation (REQ-PER-038.3).
 */
enum class HrvDurabilityStatus {
    VALID,
    INSUFFICIENT_DURATION,
    INSUFFICIENT_MATCHED_DATA,
    NO_HRV_DATA
}

/**
 * 15-minute slice of normalized DFA alpha 1 trajectory (REQ-PER-038.4).
 */
data class HrvDurabilitySlice(
    val sliceIndex: Int,
    val startMinute: Int,
    val endMinute: Int,
    val meanNormalizedAlpha1: Double?,
    val sampleCount: Int
)

/**
 * Result of post-workout autonomic durability and drift modeling (REQ-PER-038).
 */
data class HrvDurabilityResult(
    val status: HrvDurabilityStatus,
    val freshBaselineMean: Double?,
    val lateMean: Double?,
    val durabilityDriftPct: Double?,
    val referenceIntensity: Double?,
    val isPowerBased: Boolean,
    val freshMatchedSeconds: Long,
    val lateMatchedSeconds: Long,
    val slices: List<HrvDurabilitySlice>
)

/**
 * Models DFA a1 autonomic durability and drift across multi-hour workouts (REQ-PER-038, ATT-4970).
 *
 * Paired with ATT-4842 (Heart Efficiency / stroke volume drift), this calculator evaluates the
 * decline of DFA alpha 1 at matched aerobic intensity between a fresh baseline (15–45 min)
 * and late-workout fatigue segments (>60 min), quantifying autonomic resistance to fatigue
 * and vagal withdrawal (Rogers et al. 2021; Gronwald et al. 2021).
 */
object HrvDurabilityCalculator {

    const val FRESH_WINDOW_START_SEC = 900L // 15 min
    const val FRESH_WINDOW_END_SEC = 2700L // 45 min
    const val LATE_WINDOW_START_SEC = 3600L // 60 min
    const val MAX_ARTIFACT_RATE = 5.0 // 5.0%
    const val ISO_INTENSITY_TOLERANCE = 0.10 // +/- 10%
    const val MIN_MATCHED_DURATION_SEC = 600L // 10 minutes
    const val SLICE_DURATION_SEC = 900L // 15 minutes

    private data class InternalSample(
        val timeSec: Long,
        val intensity: Double,
        val alpha1: Double,
        val artifactRate: Double
    )

    /**
     * Calculates autonomic durability drift and 15-minute slice trajectory from post-workout trackpoints.
     */
    fun calculateDurability(points: List<PathPoint>): HrvDurabilityResult {
        val emptyResult = HrvDurabilityResult(
            status = HrvDurabilityStatus.NO_HRV_DATA,
            freshBaselineMean = null,
            lateMean = null,
            durabilityDriftPct = null,
            referenceIntensity = null,
            isPowerBased = true,
            freshMatchedSeconds = 0L,
            lateMatchedSeconds = 0L,
            slices = emptyList()
        )

        if (points.isEmpty()) return emptyResult

        val hasTimestamps = points.size >= 2 && points.any { it.timeSec > 0L }
        val stepSec = if (hasTimestamps && points.size >= 2) {
            val totalSpan = points.last().timeSec - points.first().timeSec
            if (totalSpan > 0) (totalSpan.toDouble() / (points.size - 1)).coerceIn(1.0, 5.0) else 5.0
        } else 5.0

        // Determine modality: power takes precedence over speed
        val hasPower = points.any { it.power != null && it.power > 0 }
        val isPowerBased = hasPower

        // Filter valid raw samples with centered DFA alpha 1 and artifact rate <= 5.0%
        val allValid = mutableListOf<InternalSample>()
        for (i in points.indices) {
            val pt = points[i]
            val tSec = if (hasTimestamps) pt.timeSec else i * 5L
            val a1 = pt.hrvDfaA1
            val art = pt.hrvArtifactRate ?: 0.0

            if (a1 != null && a1 > 0.0 && art <= MAX_ARTIFACT_RATE) {
                val intensityVal = if (isPowerBased) {
                    pt.power?.toDouble()?.takeIf { it > 0.0 }
                } else {
                    pt.speedMps?.takeIf { it > 0.5 }
                }
                if (intensityVal != null) {
                    allValid.add(InternalSample(tSec, intensityVal, a1, art))
                }
            }
        }

        if (allValid.isEmpty()) {
            return emptyResult.copy(
                status = if (points.any { it.hrvDfaA1 != null }) HrvDurabilityStatus.INSUFFICIENT_DURATION else HrvDurabilityStatus.NO_HRV_DATA,
                isPowerBased = isPowerBased
            )
        }

        // 1. Fresh Baseline Window (15–45 min / 900–2700 s)
        val freshSamples = allValid.filter { it.timeSec in FRESH_WINDOW_START_SEC..FRESH_WINDOW_END_SEC }
        if (freshSamples.isEmpty()) {
            return emptyResult.copy(
                status = HrvDurabilityStatus.INSUFFICIENT_DURATION,
                isPowerBased = isPowerBased
            )
        }

        // Median reference intensity over fresh baseline
        val sortedIntensities = freshSamples.map { it.intensity }.sorted()
        val refIntensity = if (sortedIntensities.size % 2 == 1) {
            sortedIntensities[sortedIntensities.size / 2]
        } else {
            (sortedIntensities[sortedIntensities.size / 2 - 1] + sortedIntensities[sortedIntensities.size / 2]) / 2.0
        }

        val minIntensity = refIntensity * (1.0 - ISO_INTENSITY_TOLERANCE)
        val maxIntensity = refIntensity * (1.0 + ISO_INTENSITY_TOLERANCE)

        // Matched samples in fresh baseline
        val freshMatched = freshSamples.filter { it.intensity in minIntensity..maxIntensity }
        val freshMatchedSec = (freshMatched.size * stepSec).toLong()

        // 2. Late Segment Window (>60 min / >3600 s)
        val lateSamples = allValid.filter { it.timeSec > LATE_WINDOW_START_SEC }
        val lateMatched = lateSamples.filter { it.intensity in minIntensity..maxIntensity }
        val lateMatchedSec = (lateMatched.size * stepSec).toLong()

        // 3. 15-Minute Slice Trajectories (starting at 15m / 900s)
        val maxTimeSec = allValid.maxOf { it.timeSec }
        val slices = mutableListOf<HrvDurabilitySlice>()
        var sliceIdx = 0
        var sliceStartSec = FRESH_WINDOW_START_SEC

        while (sliceStartSec < maxTimeSec) {
            val sliceEndSec = sliceStartSec + SLICE_DURATION_SEC
            val startMin = (sliceStartSec / 60).toInt()
            val endMin = (sliceEndSec / 60).toInt()

            val inSliceMatched = allValid.filter {
                it.timeSec >= sliceStartSec && it.timeSec < sliceEndSec && it.intensity in minIntensity..maxIntensity
            }

            val meanNorm = if (inSliceMatched.isNotEmpty()) {
                inSliceMatched.map { it.alpha1 / (it.intensity / refIntensity) }.average()
            } else null

            slices.add(HrvDurabilitySlice(sliceIdx, startMin, endMin, meanNorm, inSliceMatched.size))

            sliceIdx++
            sliceStartSec = sliceEndSec
        }

        // 4. Validate Duration and Matched Data Minimum Guards
        val maxWorkoutTimeSec = if (hasTimestamps) points.last().timeSec else (points.size - 1) * 5L
        if (maxWorkoutTimeSec <= LATE_WINDOW_START_SEC) {
            return HrvDurabilityResult(
                status = HrvDurabilityStatus.INSUFFICIENT_DURATION,
                freshBaselineMean = null,
                lateMean = null,
                durabilityDriftPct = null,
                referenceIntensity = refIntensity,
                isPowerBased = isPowerBased,
                freshMatchedSeconds = freshMatchedSec,
                lateMatchedSeconds = lateMatchedSec,
                slices = slices
            )
        }

        if (freshMatchedSec < MIN_MATCHED_DURATION_SEC || lateMatchedSec < MIN_MATCHED_DURATION_SEC) {
            return HrvDurabilityResult(
                status = HrvDurabilityStatus.INSUFFICIENT_MATCHED_DATA,
                freshBaselineMean = null,
                lateMean = null,
                durabilityDriftPct = null,
                referenceIntensity = refIntensity,
                isPowerBased = isPowerBased,
                freshMatchedSeconds = freshMatchedSec,
                lateMatchedSeconds = lateMatchedSec,
                slices = slices
            )
        }

        // 5. Compute Durability Drift Percentage
        val freshBaselineNormMean = freshMatched.map { it.alpha1 / (it.intensity / refIntensity) }.average()
        val lateNormMean = lateMatched.map { it.alpha1 / (it.intensity / refIntensity) }.average()
        val driftPct = ((lateNormMean - freshBaselineNormMean) / freshBaselineNormMean) * 100.0

        return HrvDurabilityResult(
            status = HrvDurabilityStatus.VALID,
            freshBaselineMean = freshBaselineNormMean,
            lateMean = lateNormMean,
            durabilityDriftPct = driftPct,
            referenceIntensity = refIntensity,
            isPowerBased = isPowerBased,
            freshMatchedSeconds = freshMatchedSec,
            lateMatchedSeconds = lateMatchedSec,
            slices = slices
        )
    }
}
