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
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.MilestoneAutoNamingManager
import com.atrainingtracker.trainingtracker.database.WorkoutAutoNamingHelper
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.util.StreamSummaryCalculator
import java.util.EnumSet

/**
 * Stateless post-workout finalizer encapsulating the 7 independently fault-isolated live-session
 * finalization stages and commute/trainer classification previously hosted inline in
 * [TrackerService] (REQ-TRK-094.2, REQ-TRK-030.4, ATT-6034).
 *
 * Threading: Invoked off the Android Main Looper on `TrackerService.mDbExecutor`.
 * Persistence: Routes strictly through domain repositories (`WorkoutRepository`,
 * `KnownLocationsRepository`, `BANALServiceRepository`) with zero direct `*DatabaseManager` or
 * `Cursor` handles (`coding_standards.md` §2.5).
 */
object PostWorkoutFinalizer {
    private const val TAG = "PostWorkoutFinalizer"

    /**
     * Immutable input bundle for [finalizeLiveSession].
     *
     * Providers [knownLocationsRepositoryProvider], [activeSessionDeviceIdsProvider], and
     * [fallbackSportTypeIdProvider] are evaluated strictly lazily inside their respective
     * Stage 5 / Stage 6 `try/catch` blocks so eager setup never triggers SQLite, concurrency,
     * or binder errors outside fault isolation.
     */
    data class FinalizationInput(
        val context: Context,
        val repository: WorkoutRepository,
        val knownLocationsRepositoryProvider: () -> KnownLocationsRepository,
        val banalService: BANALServiceComm?,
        val diagnosticCollector: WorkoutRecordingErrorCollector,
        val snapshot: TrackerService.BanalFinalizationSnapshot?,
        val workoutId: Long,
        val baseFileName: String?,
        val liveSession: LiveWorkoutSession,
        val effectiveAvgSpeed: Double,
        val effectiveAvgSpeedCalculable: Boolean,
        val effectiveDistanceTotalM: Double,
        val effectiveTimeActiveS: Int,
        val deferHrPhysiologyToPostProcessing: Boolean,
        val activeSessionDeviceIdsProvider: () -> Set<Long>,
        val fallbackSportTypeIdProvider: () -> Long,
        val importantSensorTypes: Set<SensorType>,
        val guessCommuteAndTrainerCallback: (Long) -> Unit,
        val debugLogging: Boolean = false
    )

    @JvmStatic
    fun finalizeLiveSession(
        input: FinalizationInput
    ): MilestoneAutoNamingManager.MilestoneCelebrationData? {
        val context = input.context
        val repository = input.repository
        val banalService = input.banalService
        val diagnosticCollector = input.diagnosticCollector
        val snapshot = input.snapshot
        val workoutId = input.workoutId
        val baseFileName = input.baseFileName
        val liveSession = input.liveSession

        if (input.debugLogging) Log.i(TAG, "finalizeLiveSession for workout $workoutId")

        // 1. Finalize Map and Streams first so route geometry and profile streams are never lost if downstream steps fail (REQ-TRK-030.4)
        try {
            val rawPoints = liveSession.rawLatLngs
            val pointsToSimplify = if (rawPoints == null || rawPoints.isEmpty()) {
                liveSession.sampledLatLngs
            } else {
                rawPoints
            }
            val encodedPolyline = StreamSummaryCalculator.encodePolyline(
                pointsToSimplify,
                StreamSummaryCalculator.DEFAULT_POLYLINE_SIMPLIFY_TOLERANCE_M
            )
            val polyline = encodedPolyline ?: ""
            val altStream = StreamSummaryCalculator.encodeDoubleStreamOrEmpty(liveSession.sampledAltitudes)
            val distStream = StreamSummaryCalculator.encodeDoubleStreamOrEmpty(liveSession.sampledDistances)
            repository.updateMapAndStreams(workoutId, polyline, altStream, distStream)
        } catch (t: Throwable) {
            Log.e(TAG, "finalizeLiveSession: Error persisting map polyline and elevation/distance streams", t)
            diagnosticCollector.record(
                DiagnosticStage.FINALIZE,
                t,
                "Error persisting map polyline and elevation/distance streams"
            )
        }

        val finalizedPowerAndLineSensors = EnumSet.noneOf(SensorType::class.java)

        // 2. Save START and END locations, Sensor Extrema, and Max Line Distance (REQ-TRK-030.4)
        try {
            val startPos = liveSession.startLatLng
            val endPos = liveSession.lastLatLng

            if (startPos != null) {
                repository.updateExtremaValue(workoutId, SensorType.LATITUDE, ExtremaType.START, startPos.latitude, startPos)
                repository.updateExtremaValue(workoutId, SensorType.LONGITUDE, ExtremaType.START, startPos.longitude, startPos)
            }

            if (endPos != null) {
                repository.updateExtremaValue(workoutId, SensorType.LATITUDE, ExtremaType.END, endPos.latitude, endPos)
                repository.updateExtremaValue(workoutId, SensorType.LONGITUDE, ExtremaType.END, endPos.longitude, endPos)
            }

            for ((sensor, stats) in liveSession.sensorStats) {
                if (stats == null) continue

                if (stats.min.isFinite() && stats.min != Double.MAX_VALUE) {
                    repository.updateExtremaValue(workoutId, sensor, ExtremaType.MIN, stats.min, stats.minPos)
                }
                if (stats.max.isFinite() && stats.max != -Double.MAX_VALUE) {
                    repository.updateExtremaValue(workoutId, sensor, ExtremaType.MAX, stats.max, stats.maxPos)
                }
                if (input.importantSensorTypes.contains(sensor)) {
                    var avgValue = stats.average
                    // Special case: for Speed, use the authoritative distance/time average (SCRUM-108)
                    if (sensor == SensorType.SPEED_mps && input.effectiveAvgSpeedCalculable) {
                        avgValue = input.effectiveAvgSpeed
                    }
                    if (avgValue.isFinite()) {
                        val avgCount = if (stats.count > 0) stats.count else null
                        repository.updateExtremaValue(workoutId, sensor, ExtremaType.AVG, avgValue, null, avgCount)
                    }
                }
                if (sensor == SensorType.W_PRIME_BAL && stats.count > 0 && stats.last.isFinite()) {
                    repository.updateExtremaValue(workoutId, sensor, ExtremaType.END, stats.last, endPos)
                }
            }

            // REQ-MAP-020 (ATT-528), REQ-SET-097 (ATT-5899): Non-degenerate elliptical apex calculation from recorded track
            val sampledPoints = liveSession.sampledLatLngs
            if (startPos != null && sampledPoints != null && sampledPoints.isNotEmpty()) {
                val calculatedApex = WorkoutClusterEngine.getInstance(context)
                    .findApexFromPoints(startPos, sampledPoints, endPos)
                val calculatedMaxDisp = WorkoutClusterEngine.distanceBetween(startPos, calculatedApex).toDouble()
                repository.updateExtremaValue(
                    workoutId,
                    SensorType.LINE_DISTANCE_m,
                    ExtremaType.MAX,
                    calculatedMaxDisp,
                    calculatedApex
                )
                finalizedPowerAndLineSensors.add(SensorType.LINE_DISTANCE_m)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "finalizeLiveSession: Error persisting start/end locations and sensor extrema", t)
            diagnosticCollector.record(
                DiagnosticStage.FINALIZE,
                t,
                "Error persisting start/end locations and sensor extrema"
            )
        }

        // 3. Calculate and persist Efficiency Factor (EF), Aerobic Decoupling, and Power Training Metrics from recorded samples (REQ-UI-400.2, REQ-MIG-039.4)
        try {
            if (!baseFileName.isNullOrEmpty()) {
                val samples = repository.getPowerSpeedHrSamples(baseFileName)
                val powerSamples = samples.powerSamples
                val speedSamples = samples.speedSamples
                val hrSamples = samples.hrSamples
                if (powerSamples.isNotEmpty() || speedSamples.isNotEmpty() || hrSamples.isNotEmpty()) {
                    if (!input.deferHrPhysiologyToPostProcessing) {
                        val liveSport = if (snapshot != null && snapshot.bSportType != BSportType.UNKNOWN) {
                            snapshot.bSportType
                        } else {
                            banalService?.bSportType ?: BSportType.UNKNOWN
                        }
                        val aerobicResult = PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(
                            powerSamples,
                            speedSamples,
                            hrSamples,
                            liveSport
                        )
                        val targetSensor = if (aerobicResult.isPowerBased) SensorType.POWER else SensorType.SPEED_mps
                        aerobicResult.efficiencyFactor?.let { ef ->
                            repository.updateExtremaValue(workoutId, targetSensor, ExtremaType.EFFICIENCY_FACTOR, ef, null)
                        }
                        aerobicResult.decouplingPct?.let { dec ->
                            repository.updateExtremaValue(workoutId, targetSensor, ExtremaType.AEROBIC_DECOUPLING, dec, null)
                        }

                        // REQ-REV-009.2 (ATT-3904): 1st-order dynamical Power-HR identification
                        val dynamics = PowerHrDynamicsCalculator.calculate(
                            powerSamples,
                            hrSamples,
                            workoutId,
                            1.0
                        )
                        if (dynamics != null) {
                            repository.saveCardioPowerDynamics(workoutId, dynamics)
                        }
                    }

                    // REQ-MIG-039.4 (ATT-3860 / ATT-3858): Calculate and persist session power training metrics (NP, IF, TSS, Work kJ, W/kg)
                    val sessionTimeMs = System.currentTimeMillis()
                    val effectiveFtp = SettingsDataStoreJavaHelper.resolveEffectiveFtpAt(context, sessionTimeMs)
                    val effectiveWeightKg = SettingsDataStoreJavaHelper.resolveEffectiveWeightKgAt(context, sessionTimeMs)
                    val ftpWattsObj = if (effectiveFtp != null && effectiveFtp > 0) effectiveFtp.toDouble() else null
                    val weightKgObj = if (effectiveWeightKg > 0.0f) effectiveWeightKg.toDouble() else null
                    val powerMetrics = PowerPhysiologyCalculator.computePowerTrainingMetrics(
                        powerSamples,
                        input.effectiveTimeActiveS,
                        ftpWattsObj,
                        weightKgObj
                    )
                    powerMetrics.normalizedPowerWatts?.let { np ->
                        repository.updateExtremaValue(workoutId, SensorType.NORMALIZED_POWER, ExtremaType.AVG, np, null)
                        repository.updateExtremaValue(workoutId, SensorType.NORMALIZED_POWER, ExtremaType.END, np, null)
                        finalizedPowerAndLineSensors.add(SensorType.NORMALIZED_POWER)
                    }
                    powerMetrics.intensityFactor?.let { ifVal ->
                        repository.updateExtremaValue(workoutId, SensorType.INTENSITY_FACTOR, ExtremaType.AVG, ifVal, null)
                        repository.updateExtremaValue(workoutId, SensorType.INTENSITY_FACTOR, ExtremaType.END, ifVal, null)
                        finalizedPowerAndLineSensors.add(SensorType.INTENSITY_FACTOR)
                    }
                    powerMetrics.tss?.let { tss ->
                        repository.updateExtremaValue(workoutId, SensorType.TSS, ExtremaType.AVG, tss, null)
                        repository.updateExtremaValue(workoutId, SensorType.TSS, ExtremaType.MAX, tss, null)
                        repository.updateExtremaValue(workoutId, SensorType.TSS, ExtremaType.END, tss, null)
                        finalizedPowerAndLineSensors.add(SensorType.TSS)
                    }
                    powerMetrics.workKj?.let { workKj ->
                        repository.updateExtremaValue(workoutId, SensorType.WORK_KJ, ExtremaType.AVG, workKj, null)
                        repository.updateExtremaValue(workoutId, SensorType.WORK_KJ, ExtremaType.MAX, workKj, null)
                        repository.updateExtremaValue(workoutId, SensorType.WORK_KJ, ExtremaType.END, workKj, null)
                        finalizedPowerAndLineSensors.add(SensorType.WORK_KJ)
                    }
                    powerMetrics.avgPowerToWeight?.let { avgWPerKg ->
                        repository.updateExtremaValue(workoutId, SensorType.POWER_TO_WEIGHT, ExtremaType.AVG, avgWPerKg, null)
                        finalizedPowerAndLineSensors.add(SensorType.POWER_TO_WEIGHT)
                    }
                    powerMetrics.maxPowerToWeight?.let { maxWPerKg ->
                        repository.updateExtremaValue(workoutId, SensorType.POWER_TO_WEIGHT, ExtremaType.MAX, maxWPerKg, null)
                        finalizedPowerAndLineSensors.add(SensorType.POWER_TO_WEIGHT)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "finalizeLiveSession: Could not compute Aerobic Efficiency / Decoupling or Power Training extrema", t)
            diagnosticCollector.record(
                DiagnosticStage.FINALIZE,
                t,
                "Could not compute Aerobic Efficiency / Decoupling or Power Training extrema"
            )
        }

        if (finalizedPowerAndLineSensors.isNotEmpty()) {
            try {
                repository.addAccumulatedSensorTypes(workoutId, finalizedPowerAndLineSensors)
            } catch (t: Throwable) {
                Log.w(TAG, "finalizeLiveSession: Could not upsert finalized sensor types", t)
            }
        }

        // 4. Guess Commute and Trainer (REQ-TRK-030.4)
        try {
            input.guessCommuteAndTrainerCallback(workoutId)
        } catch (t: Throwable) {
            Log.e(TAG, "finalizeLiveSession: Error in guessCommuteAndTrainer", t)
            diagnosticCollector.record(DiagnosticStage.FINALIZE, t, "Error in guessCommuteAndTrainer")
        }

        var identity: EquipmentAndSportTypeDiscoveryManager.InferredIdentity? = null
        var celebrationData: MilestoneAutoNamingManager.MilestoneCelebrationData? = null

        // 5. Identity Determination, Route Clustering & Auto-Naming (SCRUM-200, ATT-1398, REQ-TRK-030.4)
        try {
            val discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(context)
            val activeRemoteDeviceIds: Set<Long> = if (snapshot != null && snapshot.activeRemoteDeviceIds.isNotEmpty()) {
                snapshot.activeRemoteDeviceIds
            } else if (banalService != null) {
                HashSet(banalService.databaseIdsOfActiveRemoteDevices)
            } else {
                HashSet(input.activeSessionDeviceIdsProvider())
            }
            val banalSportType = if (snapshot != null && snapshot.bSportType != BSportType.UNKNOWN) {
                snapshot.bSportType
            } else {
                banalService?.bSportType ?: BSportType.UNKNOWN
            }

            val effectiveMovingSummary = if (snapshot != null && snapshot.movingSpeedSummary != null) {
                snapshot.movingSpeedSummary
            } else {
                liveSession.movingSpeedSummary
            }
            val identitySpeed = EquipmentAndSportTypeDiscoveryManager.resolveEffectiveSpeedForIdentity(
                banalSportType,
                input.effectiveAvgSpeed,
                effectiveMovingSummary
            )

            // Initial arbitration using currently active devices in memory (more reliable than waiting for DB sync)
            val resolvedIdentity = discoveryManager.resolveIdentity(activeRemoteDeviceIds, banalSportType, identitySpeed)
            identity = resolvedIdentity

            val startPosRaw = liveSession.startLatLng
            val endPosRaw = liveSession.lastLatLng
            val maxDispPos = repository.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX)

            if (startPosRaw != null && endPosRaw != null && maxDispPos != null) {
                val engine = WorkoutClusterEngine.getInstance(context)

                // Determine candidate sport types for clustering (ATT-773, REQ-SET-064)
                val userSelectedSport = if (snapshot != null) {
                    snapshot.userSelectedSportType
                } else {
                    banalService?.userSelectedBSportType
                }
                val candidateSports: Set<BSportType> = if (userSelectedSport != null && userSelectedSport != BSportType.UNKNOWN) {
                    // Tier 1: User Pre-Selection Sovereignty (Option A)
                    setOf(userSelectedSport)
                } else if (resolvedIdentity.isHighConfidence) {
                    // Tier 2: Dedicated Hardware Sensor Sovereignty (REQ-SET-030)
                    setOf(resolvedIdentity.bSportType)
                } else {
                    // Tier 3: Speed-Based Multi-Sport Candidate Set
                    discoveryManager.getCandidateBSportTypes(banalSportType, identitySpeed)
                }

                val minAltPos = repository.getExtremaPosition(workoutId, SensorType.ALTITUDE, ExtremaType.MIN)
                val maxAltPos = repository.getExtremaPosition(workoutId, SensorType.ALTITUDE, ExtremaType.MAX)
                val suggestion = engine.suggestCluster(
                    startPosRaw,
                    endPosRaw,
                    maxDispPos,
                    input.effectiveDistanceTotalM,
                    null,
                    candidateSports,
                    minAltPos,
                    maxAltPos,
                    null,
                    1.2,
                    null
                )
                val workoutMethod = repository.getWorkoutMethod(workoutId)
                val isGuidedHrvTest = "GUIDED_STEP" == workoutMethod || "GUIDED_RAMP" == workoutMethod
                if (suggestion != null) {
                    // If hardware confidence is high, we only take the name from the cluster
                    if (resolvedIdentity.isHighConfidence) {
                        if (!isGuidedHrvTest) {
                            val displayCount = suggestion.hitCount + 1
                            val autoName = WorkoutClusterEngine.formatClusterWorkoutName(
                                context,
                                suggestion.name,
                                displayCount,
                                suggestion.hasCounter
                            )
                            repository.updateWorkoutName(workoutId, autoName)
                        }
                        repository.updateWorkoutClusterId(workoutId, suggestion.id)

                        // Apply hardware-based identity (Sport, Gear, Strava)
                        repository.applyInferredIdentity(workoutId, resolvedIdentity)
                    } else {
                        // Low hardware confidence -> Workout Cluster wins everything and resolves ambiguous sport type
                        engine.assignClusterToWorkout(context, workoutId, suggestion.id)
                    }
                } else {
                    // No cluster match -> use hardware identity
                    repository.applyInferredIdentity(workoutId, resolvedIdentity)

                    // ATT-1398: Intelligent Workout Auto-Naming based on recognized start/destination Lieblingsorte
                    if (!isGuidedHrvTest) {
                        val knownLocationsRepo = input.knownLocationsRepositoryProvider()
                        val startLoc = knownLocationsRepo.getMyLocation(startPosRaw)
                        val endLoc = knownLocationsRepo.getMyLocation(endPosRaw)
                        val endpointDist = engine.distanceBetween(startPosRaw, endPosRaw)
                        val resolvedSport = if (resolvedIdentity.bSportType != BSportType.UNKNOWN) {
                            resolvedIdentity.bSportType
                        } else {
                            banalSportType
                        }
                        val autoName = WorkoutAutoNamingHelper.generateWorkoutName(
                            context,
                            resolvedSport,
                            startLoc,
                            endLoc,
                            endpointDist,
                            input.effectiveDistanceTotalM
                        )
                        if (!autoName.isNullOrBlank()) {
                            repository.updateWorkoutName(workoutId, autoName)
                        }
                    }
                }
            } else {
                // No spatial data -> use hardware identity
                repository.applyInferredIdentity(workoutId, resolvedIdentity)
            }

            // 6. Milestone Workout Auto-Naming & First Jubilee Evaluation (ATT-2308 / REQ-REV-001, REQ-REV-002, REQ-REV-004)
            try {
                val finalSportId = if (resolvedIdentity.sportId > 0) {
                    resolvedIdentity.sportId
                } else {
                    snapshot?.sportTypeId ?: input.fallbackSportTypeIdProvider()
                }
                val finalSportType = if (resolvedIdentity.bSportType != BSportType.UNKNOWN) {
                    resolvedIdentity.bSportType
                } else if (snapshot != null && snapshot.bSportType != BSportType.UNKNOWN) {
                    snapshot.bSportType
                } else {
                    banalSportType
                }
                celebrationData = MilestoneAutoNamingManager.evaluateAndApplyMilestone(
                    context,
                    workoutId,
                    finalSportId,
                    finalSportType
                )
            } catch (t: Throwable) {
                Log.e(TAG, "finalizeLiveSession: Error evaluating milestone auto-naming", t)
                diagnosticCollector.record(DiagnosticStage.FINALIZE, t, "Error evaluating milestone auto-naming")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "finalizeLiveSession: Error in identity determination, clustering, or auto-naming", t)
            diagnosticCollector.record(
                DiagnosticStage.FINALIZE,
                t,
                "Error in identity determination, clustering, or auto-naming"
            )
        }

        // 7a. Persist Converged Wheel Circumferences for Bike Equipment (REQ-SEN-030.3, ATT-7753)
        try {
            val targetEquipmentId = if (identity != null && identity.equipmentId > 0) {
                identity.equipmentId
            } else {
                -1L
            }
            BANALServiceRepository.getInstance(context).persistConvergedCircumferences(
                if (targetEquipmentId > 0) targetEquipmentId else null,
                System.currentTimeMillis()
            )
        } catch (t: Throwable) {
            Log.e(TAG, "finalizeLiveSession: Error persisting converged wheel circumferences", t)
            diagnosticCollector.record(
                DiagnosticStage.FINALIZE,
                t,
                "Error persisting converged wheel circumferences"
            )
        }

        // 7b. Persist final diagnostic recording errors summary (REQ-TRK-053.2)
        try {
            val finalErrorJson = diagnosticCollector.toJson()
            if (finalErrorJson != null && workoutId > 0) {
                repository.updateRecordingErrors(workoutId, finalErrorJson)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "finalizeLiveSession: Error persisting diagnostic errors payload", t)
        }

        return celebrationData
    }

    @JvmStatic
    @JvmOverloads
    fun guessCommuteAndTrainer(
        repository: WorkoutRepository,
        workoutId: Long,
        debugLogging: Boolean = false
    ) {
        if (debugLogging) Log.i(TAG, "guessCommuteAndTrainer")

        val distance = repository.getWorkoutDistance(workoutId)
        val maxLineDistance = repository.getExtremaValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX)
        val endLineDistance = repository.getExtremaValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.END)

        var commute = false
        var trainer = false
        if (maxLineDistance != null) {
            if (distance != null && maxLineDistance < TrainingApplication.DISTANCE_TO_MAX_THRESHOLD_FOR_TRAINER) {
                trainer = true
            }
            if (endLineDistance != null && maxLineDistance < endLineDistance * TrainingApplication.DISTANCE_TO_MAX_RATIO_FOR_COMMUTE) {
                commute = true
            }
        } else {
            trainer = true
        }

        if (commute xor trainer) {
            repository.setCommuteAndTrainer(workoutId, commute, trainer)
        }
    }
}
