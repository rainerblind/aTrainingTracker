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

package com.atrainingtracker.trainingtracker.domain.laps

import com.atrainingtracker.trainingtracker.domain.crop.CropWorkoutMath
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropSamplePoint

/**
 * Result data class representing validation and preview split lap metrics (REQ-UI-449.2 / ATT-907).
 */
data class LapSplitValidationResult(
    val isValid: Boolean,
    val targetLap: LapData? = null,
    val splitSampleIndex: Int = 0,
    val previewLapBefore: LapData? = null,
    val previewLapAfter: LapData? = null,
    val errorMessage: String? = null
)

/**
 * Result data class representing validation and preview metrics for moving a lap boundary (REQ-UI-450.2 / ATT-908).
 */
data class LapBoundaryMoveValidationResult(
    val isValid: Boolean,
    val boundaryLapNr: Long = 1L,
    val clampedSampleIndex: Int = 0,
    val previewLapK: LapData? = null,
    val previewLapKPlus1: LapData? = null,
    val errorMessage: String? = null
)

/**
 * Pure calculation engine for interactive lap splitting and boundary moving on recorded workout maps.
 * (REQ-UI-449 / ATT-907, with synergistic architecture for ATT-908)
 */
object LapSplitAndMoveCalculator {

    /**
     * Resolves the target lap being split based on the sample's current lapNr.
     */
    fun findTargetLap(
        samples: List<CropSamplePoint>,
        splitSampleIndex: Int,
        existingLaps: List<LapData>
    ): LapData? {
        if (splitSampleIndex !in samples.indices) return null
        val targetLapNr = samples[splitSampleIndex].lapNr
        return existingLaps.firstOrNull { it.lapNr == targetLapNr }
            ?: LapData(workoutId = 0L, lapNr = targetLapNr, timeTotalS = 0, distanceTotalM = 0.0, speedAverageMps = 0.0)
    }

    /**
     * Validates whether [splitSampleIndex] can legally split the target lap,
     * ensuring both resulting laps have duration > 0 or distance > 0.0 (REQ-TRK-002, zero phantom laps).
     */
    fun validateSplit(
        samples: List<CropSamplePoint>,
        splitSampleIndex: Int,
        existingLaps: List<LapData> = emptyList()
    ): LapSplitValidationResult {
        if (samples.isEmpty()) {
            return LapSplitValidationResult(isValid = false, errorMessage = "No samples available")
        }
        if (splitSampleIndex !in samples.indices) {
            return LapSplitValidationResult(isValid = false, errorMessage = "Split index out of bounds")
        }

        val targetLapNr = samples[splitSampleIndex].lapNr
        val lapIndices = samples.indices.filter { samples[it].lapNr == targetLapNr }
        if (lapIndices.size < 2) {
            return LapSplitValidationResult(isValid = false, errorMessage = "Lap has insufficient samples to split")
        }

        val startIndex = lapIndices.first()
        val endIndex = lapIndices.last()

        // Split index must strictly be inside (startIndex, endIndex]
        if (splitSampleIndex <= startIndex || splitSampleIndex > endIndex) {
            return LapSplitValidationResult(isValid = false, errorMessage = "Split point must be strictly inside the target lap")
        }

        val targetLap = existingLaps.firstOrNull { it.lapNr == targetLapNr }

        // Part 1: [startIndex, splitSampleIndex - 1]
        val p1Start = samples[startIndex]
        val p1End = samples[splitSampleIndex - 1]
        val duration1S = (p1End.timeTotalS - p1Start.timeTotalS).coerceAtLeast(0L).toInt()
        val distance1M = (p1End.distanceTotalM - p1Start.distanceTotalM).coerceAtLeast(0.0)

        // Part 2: [splitSampleIndex, endIndex]
        val p2Start = samples[splitSampleIndex]
        val p2End = samples[endIndex]
        val duration2S = (p2End.timeTotalS - p2Start.timeTotalS).coerceAtLeast(0L).toInt()
        val distance2M = (p2End.distanceTotalM - p2Start.distanceTotalM).coerceAtLeast(0.0)

        // Zero phantom lap invariant (REQ-TRK-002)
        if (duration1S <= 0 && distance1M <= 0.0) {
            return LapSplitValidationResult(isValid = false, errorMessage = "First split lap would be empty")
        }
        if (duration2S <= 0 && distance2M <= 0.0) {
            return LapSplitValidationResult(isValid = false, errorMessage = "Second split lap would be empty")
        }

        val avgSpeed1 = CropWorkoutMath.calculateAverageSpeed(distance1M, duration1S)
        val avgSpeed2 = CropWorkoutMath.calculateAverageSpeed(distance2M, duration2S)

        val workoutId = targetLap?.workoutId ?: samples.first().rowId
        val lapBefore = LapData(
            id = 0L,
            workoutId = workoutId,
            lapNr = targetLapNr,
            timeStart = p1Start.timeStr ?: targetLap?.timeStart,
            timeTotalS = duration1S,
            distanceTotalM = distance1M,
            speedAverageMps = avgSpeed1,
            name = targetLap?.name,
            description = targetLap?.description
        )

        val lapAfter = LapData(
            id = 0L,
            workoutId = workoutId,
            lapNr = targetLapNr + 1L,
            timeStart = p2Start.timeStr,
            timeTotalS = duration2S,
            distanceTotalM = distance2M,
            speedAverageMps = avgSpeed2,
            name = null,
            description = null
        )

        return LapSplitValidationResult(
            isValid = true,
            targetLap = targetLap,
            splitSampleIndex = splitSampleIndex,
            previewLapBefore = lapBefore,
            previewLapAfter = lapAfter
        )
    }

    /**
     * Recalculates the complete sequence of laps after a split.
     * [updatedSamples] contains points with their updated lap numbers.
     */
    fun recalculateLapsAfterSplit(
        workoutId: Long,
        existingLaps: List<LapData>,
        updatedSamples: List<CropSamplePoint>
    ): List<LapData> {
        val groupedByLap = updatedSamples.groupBy { it.lapNr }
        val allLapNrs = groupedByLap.keys.sorted()

        val resultLaps = mutableListOf<LapData>()
        var newSequenceNr = 1L

        for (lapNr in allLapNrs) {
            val pts = groupedByLap[lapNr] ?: continue
            if (pts.isEmpty()) continue
            val firstPt = pts.first()
            val lastPt = pts.last()

            val durationS = (lastPt.timeTotalS - firstPt.timeTotalS).coerceAtLeast(0L).toInt()
            val distanceM = (lastPt.distanceTotalM - firstPt.distanceTotalM).coerceAtLeast(0.0)

            if (durationS <= 0 && distanceM <= 0.0) continue

            val avgSpeed = CropWorkoutMath.calculateAverageSpeed(distanceM, durationS)
            val matchedExisting = existingLaps.firstOrNull { it.lapNr == lapNr }

            resultLaps.add(
                LapData(
                    id = 0L,
                    workoutId = workoutId,
                    lapNr = newSequenceNr++,
                    timeStart = firstPt.timeStr ?: matchedExisting?.timeStart,
                    timeTotalS = durationS,
                    distanceTotalM = distanceM,
                    speedAverageMps = avgSpeed,
                    name = matchedExisting?.name,
                    description = matchedExisting?.description
                )
            )
        }

        if (resultLaps.isEmpty()) {
            resultLaps.add(
                LapData(
                    id = 0L,
                    workoutId = workoutId,
                    lapNr = 1L,
                    timeTotalS = 1,
                    distanceTotalM = 0.0,
                    speedAverageMps = 0.0
                )
            )
        }

        return resultLaps
    }

    /**
     * Validates shifting the boundary between [boundaryLapNr] (Lap k) and [boundaryLapNr] + 1 (Lap k+1)
     * to [newBoundaryIndex], enforcing boundary clamping within [startIndex(k) + 1, endIndex(k+1) - 1]
     * and zero-phantom lap invariants (REQ-UI-450.3 / ATT-908).
     */
    fun validateBoundaryMove(
        samples: List<CropSamplePoint>,
        boundaryLapNr: Long,
        newBoundaryIndex: Int,
        existingLaps: List<LapData> = emptyList()
    ): LapBoundaryMoveValidationResult {
        if (samples.isEmpty()) {
            return LapBoundaryMoveValidationResult(isValid = false, boundaryLapNr = boundaryLapNr, errorMessage = "No samples available")
        }

        val lapKIndices = samples.indices.filter { samples[it].lapNr == boundaryLapNr }
        val lapKPlus1Indices = samples.indices.filter { samples[it].lapNr == boundaryLapNr + 1L }

        if (lapKIndices.isEmpty() || lapKPlus1Indices.isEmpty()) {
            return LapBoundaryMoveValidationResult(
                isValid = false,
                boundaryLapNr = boundaryLapNr,
                errorMessage = "Adjacent laps $boundaryLapNr and ${boundaryLapNr + 1} not found"
            )
        }

        val startK = lapKIndices.first()
        val endKPlus1 = lapKPlus1Indices.last()

        val minBoundaryIndex = (startK + 1).coerceAtMost(endKPlus1 - 1)
        val maxBoundaryIndex = (endKPlus1 - 1).coerceAtLeast(startK + 1)
        val clampedIndex = newBoundaryIndex.coerceIn(minBoundaryIndex, maxBoundaryIndex)

        if (clampedIndex - 1 < startK || clampedIndex > endKPlus1) {
            return LapBoundaryMoveValidationResult(
                isValid = false,
                boundaryLapNr = boundaryLapNr,
                clampedSampleIndex = clampedIndex,
                errorMessage = "Boundary position out of legal lap range"
            )
        }

        val pKStart = samples[startK]
        val pKEnd = samples[clampedIndex - 1]
        val durationKS = (pKEnd.timeTotalS - pKStart.timeTotalS).coerceAtLeast(0L).toInt()
        val distanceKM = (pKEnd.distanceTotalM - pKStart.distanceTotalM).coerceAtLeast(0.0)

        val pKPlus1Start = samples[clampedIndex]
        val pKPlus1End = samples[endKPlus1]
        val durationKPlus1S = (pKPlus1End.timeTotalS - pKPlus1Start.timeTotalS).coerceAtLeast(0L).toInt()
        val distanceKPlus1M = (pKPlus1End.distanceTotalM - pKPlus1Start.distanceTotalM).coerceAtLeast(0.0)

        // Zero phantom lap invariant (REQ-TRK-002)
        if (durationKS <= 0 && distanceKM <= 0.0) {
            return LapBoundaryMoveValidationResult(
                isValid = false,
                boundaryLapNr = boundaryLapNr,
                clampedSampleIndex = clampedIndex,
                errorMessage = "First lap would be empty"
            )
        }
        if (durationKPlus1S <= 0 && distanceKPlus1M <= 0.0) {
            return LapBoundaryMoveValidationResult(
                isValid = false,
                boundaryLapNr = boundaryLapNr,
                clampedSampleIndex = clampedIndex,
                errorMessage = "Second lap would be empty"
            )
        }

        val avgSpeedK = CropWorkoutMath.calculateAverageSpeed(distanceKM, durationKS)
        val avgSpeedKPlus1 = CropWorkoutMath.calculateAverageSpeed(distanceKPlus1M, durationKPlus1S)

        val existingK = existingLaps.firstOrNull { it.lapNr == boundaryLapNr }
        val existingKPlus1 = existingLaps.firstOrNull { it.lapNr == boundaryLapNr + 1L }
        val workoutId = existingK?.workoutId ?: samples.first().rowId

        val previewLapK = LapData(
            id = existingK?.id ?: 0L,
            workoutId = workoutId,
            lapNr = boundaryLapNr,
            timeStart = pKStart.timeStr ?: existingK?.timeStart,
            timeTotalS = durationKS,
            distanceTotalM = distanceKM,
            speedAverageMps = avgSpeedK,
            name = existingK?.name,
            description = existingK?.description
        )

        val previewLapKPlus1 = LapData(
            id = existingKPlus1?.id ?: 0L,
            workoutId = workoutId,
            lapNr = boundaryLapNr + 1L,
            timeStart = pKPlus1Start.timeStr ?: existingKPlus1?.timeStart,
            timeTotalS = durationKPlus1S,
            distanceTotalM = distanceKPlus1M,
            speedAverageMps = avgSpeedKPlus1,
            name = existingKPlus1?.name,
            description = existingKPlus1?.description
        )

        return LapBoundaryMoveValidationResult(
            isValid = true,
            boundaryLapNr = boundaryLapNr,
            clampedSampleIndex = clampedIndex,
            previewLapK = previewLapK,
            previewLapKPlus1 = previewLapKPlus1
        )
    }

    /**
     * Recalculates the complete sequence of laps after shifting the boundary between
     * [boundaryLapNr] and [boundaryLapNr] + 1. Only these two adjacent laps are updated; all other
     * laps (1..k-1 and k+2..K) retain their exact existing properties and summaries.
     */
    fun recalculateLapsAfterBoundaryMove(
        workoutId: Long,
        existingLaps: List<LapData>,
        samples: List<CropSamplePoint>,
        boundaryLapNr: Long,
        newBoundarySampleIndex: Int
    ): List<LapData> {
        val validation = validateBoundaryMove(samples, boundaryLapNr, newBoundarySampleIndex, existingLaps)
        if (!validation.isValid || validation.previewLapK == null || validation.previewLapKPlus1 == null) {
            return existingLaps
        }

        return existingLaps.map { lap ->
            when (lap.lapNr) {
                boundaryLapNr -> validation.previewLapK
                boundaryLapNr + 1L -> validation.previewLapKPlus1
                else -> lap
            }
        }
    }
}
