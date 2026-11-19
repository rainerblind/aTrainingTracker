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

package com.atrainingtracker.trainingtracker.climbs

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManager
import com.atrainingtracker.trainingtracker.database.ElevationCacheDatabaseManager
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Coordinates climb detection, DEM ground-truth validation, and deduplicated persistence into
 * `Climbs.db` outside UI/Database layers (REQ-CLM-009.2, REQ-STB-021.3).
 *
 * Supports deferred [LiveClimbsRepository.refreshClimbs] notifications during bulk imports and
 * historical backfills so multi-workout batches pay at most one global reload pass.
 */
object WorkoutClimbIngestionCoordinator {
    private const val TAG = "WorkoutClimbIngestion"

    /**
     * Detects climbs from the track supplied by [trackPointsProvider] for [workoutId], validates
     * against cached DEM altitudes, and batch-persists deduplicated climbs into `Climbs.db`.
     *
     * When [refreshLiveClimbs] is false, skips per-workout [LiveClimbsRepository.refreshClimbs] so
     * bulk callers can reconcile once after the batch completes.
     */
    suspend fun extractAndPersistClimbs(
        context: Context,
        workoutId: Long,
        refreshLiveClimbs: Boolean = true,
        trackPointsProvider: suspend (Long) -> List<PathPoint>
    ): List<Long> = withContext(Dispatchers.IO) {
        try {
            val points = trackPointsProvider(workoutId)
            if (points.size >= 2) {
                val detected = ClimbDetector.detectClimbs(points, routeId = null, context = context)
                if (detected.isNotEmpty()) {
                    val elevationCache = ElevationCacheDatabaseManager.getInstance(context)
                    val validatedClimbs = ArrayList<Climb>(detected.size)

                    for (climb in detected) {
                        val startDem = elevationCache.getElevation(climb.startLat, climb.startLng, toleranceE5 = 20)
                        val endDem = elevationCache.getElevation(climb.endLat, climb.endLng, toleranceE5 = 20)

                        if (startDem != null && endDem != null) {
                            val demGain = endDem - startDem
                            val demAvgGrade = if (climb.distanceMeters > 0) (demGain / climb.distanceMeters) * 100.0 else 0.0

                            // REQ-CLM-009.2: Reject if DEM gain < 15m, DEM grade < 2.2%, or barometric drift exceeds threshold
                            if (demGain < 15.0 || demAvgGrade < 2.2 || climb.elevationGainMeters > max(demGain * 1.6 + 18.0, demGain + 35.0)) {
                                continue
                            }

                            // Calibrate gain and grade against DEM ground truth
                            validatedClimbs.add(
                                climb.copy(
                                    elevationGainMeters = demGain,
                                    avgGradePercent = demAvgGrade,
                                    category = ClimbCategory.fromScore(climb.distanceMeters * demAvgGrade)
                                )
                            )
                        } else {
                            // DEM cache miss: retain detected climb as-is (governed by ClimbDetector's spike guards)
                            validatedClimbs.add(climb)
                        }
                    }

                    if (validatedClimbs.isNotEmpty()) {
                        val ids = ClimbsDatabaseManager.getInstance(context)
                            .insertClimbsWithDeduplicationBatch(validatedClimbs, thresholdMeters = 50.0f)
                        if (refreshLiveClimbs) {
                            try {
                                LiveClimbsRepository.getInstance(context).refreshClimbs()
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to refresh LiveClimbsRepository after workout climb extraction", e)
                            }
                        }
                        return@withContext ids
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract and persist climbs for workout $workoutId", e)
        }
        emptyList()
    }
}
