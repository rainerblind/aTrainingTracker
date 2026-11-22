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

package com.atrainingtracker.trainingtracker.tracker

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import kotlinx.coroutines.CancellationException

/**
 * Recomputes HR summary extrema (MIN/MAX/AVG), Efficiency Factor (EF), Aerobic Decoupling
 * (Pw:Hr / Pa:Hr), and 1st-order Power-HR Dynamics strictly AFTER offline HR/RR backfill has
 * patched `WorkoutSamples.db` (`REQ-TRK-093.3`, `ATT-8124`).
 *
 * Encapsulates all persistence strictly via [WorkoutRepository] without direct `*DatabaseManager`
 * or `Cursor` access (`coding_standards.md` §2.5).
 */
object PostWorkoutPhysiologyRecomputer {

    private const val TAG = "PostWorkoutPhysRecomp"

    @JvmStatic
    fun recomputeAfterHrBackfill(
        context: Context,
        workoutId: Long,
        baseFileName: String?
    ): Boolean {
        if (workoutId <= 0L || baseFileName.isNullOrBlank()) {
            return false
        }
        return try {
            val appContext = context.applicationContext ?: context
            val repo = WorkoutRepository.getInstance(appContext)
            val samples = repo.getPowerSpeedHrSamples(baseFileName)
            val powerSamples = samples.powerSamples
            val speedSamples = samples.speedSamples
            val hrSamples = samples.hrSamples

            var updatedAny = false

            // 1. Recompute HR MIN/MAX/AVG from valid finite positive HR samples with exact valid sample count
            val validHrs = hrSamples.mapNotNull { value ->
                value?.takeIf { it.isFinite() && it > 0.0 }
            }
            if (validHrs.isNotEmpty()) {
                val validCount = validHrs.size
                repo.updateExtremaValue(workoutId, SensorType.HR, ExtremaType.MIN, validHrs.min(), null, validCount)
                repo.updateExtremaValue(workoutId, SensorType.HR, ExtremaType.MAX, validHrs.max(), null, validCount)
                repo.updateExtremaValue(workoutId, SensorType.HR, ExtremaType.AVG, validHrs.average(), null, validCount)
                updatedAny = true
            }

            // 2. Recompute Efficiency Factor, Aerobic Decoupling, and 1st-order Power-HR Dynamics
            if (powerSamples.isNotEmpty() || speedSamples.isNotEmpty() || hrSamples.isNotEmpty()) {
                val bSportType = repo.getWorkoutSummaryUpdateData(baseFileName)?.bSportType ?: BSportType.UNKNOWN
                val aerobicResult = PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(
                    powerSamples,
                    speedSamples,
                    hrSamples,
                    bSportType
                )
                val targetSensor = if (aerobicResult.isPowerBased) SensorType.POWER else SensorType.SPEED_mps
                aerobicResult.efficiencyFactor?.let { ef ->
                    repo.updateExtremaValue(workoutId, targetSensor, ExtremaType.EFFICIENCY_FACTOR, ef, null)
                    updatedAny = true
                }
                aerobicResult.decouplingPct?.let { dec ->
                    repo.updateExtremaValue(workoutId, targetSensor, ExtremaType.AEROBIC_DECOUPLING, dec, null)
                    updatedAny = true
                }

                val dynamics = PowerHrDynamicsCalculator.calculate(
                    powerSamples,
                    hrSamples,
                    workoutId,
                    1.0
                )
                if (dynamics != null) {
                    repo.saveCardioPowerDynamics(workoutId, dynamics)
                    updatedAny = true
                }
            }

            updatedAny
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "Error recomputing post-backfill physiology for workout $workoutId: ${t.message}", t)
            false
        }
    }
}
