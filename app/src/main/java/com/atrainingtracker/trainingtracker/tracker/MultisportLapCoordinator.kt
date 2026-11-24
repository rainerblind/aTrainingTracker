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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import com.atrainingtracker.database.SqliteSchemaUtils
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.odometry.MultisportTelemetryAnchor
import com.atrainingtracker.banalservice.odometry.MultisportTransitionBridge
import com.atrainingtracker.banalservice.odometry.MultisportTransitionDetector
import com.atrainingtracker.banalservice.odometry.MultisportTransitionEvent
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Coordinates retroactive multisport lap persistence, manual-lap anchor clamping, live
 * accumulator advancement, final-leg anchored naming, and asynchronous `WorkoutSamples.db`
 * `LAP_NR` backfilling for [TrackerService] (`REQ-FIL-031.3`, `ATT-8917`).
 *
 * Guarantees:
 * - Zero main-thread SQLite writes (all `Laps.db` and `WorkoutSamples.db` operations run on
 *   `dbExecutor` chained after `tableInitFuture`).
 * - Pre-increment `REQ-TRK-002` persistability evaluation (`hasLeg1`, `hasTransition`) before
 *   advancing `LAP_NR`, preventing `1 -> 3` `LAP_NR` gaps and orphaned sample rows.
 * - Zero edits to `DeviceManager.java` and `WorkoutSamplesDatabaseManager.java`.
 */
@Suppress("MagicNumber", "LongParameterList")
class MultisportLapCoordinator(
    private val advanceLapsFn: (Int, Int, Double) -> Unit = { count, timeLapS, distLapM ->
        BANALService.advanceLapsForMultisport(count, timeLapS, distLapM)
    }
) {
    companion object {
        private const val TAG = "MultisportLapCoord"
        private const val FALLBACK_NAME_BIKE = "Bike"
        private const val FALLBACK_NAME_TRANSITION = "Transition"
        private const val FALLBACK_NAME_RUN = "Run"
        private const val MAX_PENDING_RECONCILIATION_SESSIONS = 4

        private val UTC_TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

        /**
         * Formats an epoch millisecond timestamp into canonical UTC `"yyyy-MM-dd HH:mm:ss"`
         * matching `FitFileWriter.parseUtcTimestamp()` (`REQ-FIL-031.3`).
         */
        @JvmStatic
        fun formatUtcTimestamp(epochMs: Long): String {
            return Instant.ofEpochMilli(epochMs.coerceAtLeast(0L))
                .atOffset(ZoneOffset.UTC)
                .format(UTC_TIMESTAMP_FORMATTER)
        }

        private fun parseUtcTimestampEpochSeconds(utcText: String?): Long? {
            val trimmed = utcText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return try {
                LocalDateTime.parse(trimmed, UTC_TIMESTAMP_FORMATTER).toEpochSecond(ZoneOffset.UTC)
            } catch (_: Throwable) {
                null
            }
        }

        @JvmStatic
        fun resolveSportLapName(context: Context?, sport: BSportType): String? {
            val resId = when (sport) {
                BSportType.BIKE -> R.string.lap_name_bike
                BSportType.RUN -> R.string.lap_name_run
                else -> return null
            }
            val fallback = if (sport == BSportType.BIKE) FALLBACK_NAME_BIKE else FALLBACK_NAME_RUN
            return resolveStringSafe(context, resId, fallback)
        }

        @JvmStatic
        fun resolveTransitionLapName(context: Context?): String =
            resolveStringSafe(context, R.string.lap_name_transition, FALLBACK_NAME_TRANSITION)

        private fun resolveStringSafe(context: Context?, resId: Int, fallback: String): String {
            if (context == null) return fallback
            return try {
                context.getString(resId).takeIf { it.isNotBlank() } ?: fallback
            } catch (_: Throwable) {
                fallback
            }
        }

        private fun safeAverageSpeed(distanceM: Double, timeS: Int): Double {
            if (!distanceM.isFinite() || distanceM <= 0.0 || timeS <= 0) return 0.0
            val speed = distanceM / timeS.toDouble()
            return if (speed.isFinite()) speed else 0.0
        }
    }

    /**
     * Computed retroactive transition plan returned by [planTransition] for unit verification and
     * asynchronous persistence.
     */
    data class RetroactiveLapPlan(
        val oldLapNr: Int,
        val hasLeg1: Boolean,
        val leg1LapNr: Int,
        val leg1StartTimeUtc: String,
        val leg1ActiveTimeS: Int,
        val leg1DistanceM: Double,
        val leg1AvgSpeedMps: Double,
        val leg1Name: String?,
        val hasTransition: Boolean,
        val transitionLapNr: Int,
        val transitionStartTimeUtc: String,
        val transitionActiveTimeS: Int,
        val transitionDistanceM: Double,
        val transitionAvgSpeedMps: Double,
        val transitionName: String,
        val lapAdvanceCount: Int,
        val newLegLapNr: Int,
        val newLegStartTimeUtc: String,
        val newLegName: String?,
        val newLegTimeLapS: Int,
        val newLegDistanceLapM: Double,
        val effectiveExitAnchor: MultisportTelemetryAnchor,
        val effectiveEnterAnchor: MultisportTelemetryAnchor
    )

    /**
     * Immutable committed transition boundary recorded atomically before live lap advancement
     * so concurrent 1-Hz `recordLiveSample` writes and finalization reconciliation enforce
     * strictly monotonic `LAP_NR` across `WorkoutSamples.db` (`REQ-TRK-096`).
     */
    internal data class CommittedTransitionWindow(
        val baseFileName: String?,
        val exitTotalS: Long,
        val enterTotalS: Long,
        val exitEpochS: Long,
        val enterEpochS: Long,
        val hasTransition: Boolean,
        val transitionLapNr: Int,
        val newLegLapNr: Int
    )

    private var currentLapStartAnchor: MultisportTelemetryAnchor =
        MultisportTelemetryAnchor(0L, 0, 0, 0.0)
    private var activeLegStartTimeUtc: String? = null
    private var activeLegName: String? = null
    private var pendingUnnamedLapStartUtc: String? = null

    @Volatile
    private var hasCommittedTransitions: Boolean = false
    private val committedTransitions: MutableList<CommittedTransitionWindow> = mutableListOf()
    private val pendingReconciliationByFileName: LinkedHashMap<String, MutableList<CommittedTransitionWindow>> =
        LinkedHashMap()

    @Synchronized
    fun resetForSession(
        startTimestampMs: Long,
        activeTimeS: Int = 0,
        totalTimeS: Int = 0,
        totalDistanceM: Double = 0.0
    ) {
        val safeDist = if (totalDistanceM.isFinite() && totalDistanceM >= 0.0) totalDistanceM else 0.0
        currentLapStartAnchor = MultisportTelemetryAnchor(
            timestampMs = startTimestampMs.coerceAtLeast(0L),
            activeTimeS = activeTimeS.coerceAtLeast(0),
            totalTimeS = totalTimeS.coerceAtLeast(0),
            totalDistanceM = safeDist
        )
        activeLegStartTimeUtc = null
        activeLegName = null
        pendingUnnamedLapStartUtc = null
        committedTransitions.clear()
        hasCommittedTransitions = false
    }

    @Synchronized
    fun getCurrentLapStartAnchor(): MultisportTelemetryAnchor = currentLapStartAnchor

    @Synchronized
    fun getActiveLapStartTimeUtc(): String? = activeLegStartTimeUtc

    @Synchronized
    fun getActiveLapName(): String? = activeLegName

    /**
     * Consumes the captured pre-mutation UTC start timestamp for a closing unnamed split if
     * present; otherwise resolves the active [currentLapStartAnchor] timestamp (or UTC
     * `now - lapTimeS` fallback when `lapTimeS > 0`) (`REQ-TRK-090`).
     */
    @Synchronized
    fun consumeLapStartTimeUtc(lapTimeS: Int): String? {
        val pending = pendingUnnamedLapStartUtc
        if (pending != null) {
            pendingUnnamedLapStartUtc = null
            return pending
        }
        val anchorMs = currentLapStartAnchor.timestampMs
        if (anchorMs > 0L) {
            return formatUtcTimestamp(anchorMs)
        }
        if (lapTimeS > 0) {
            return formatUtcTimestamp(System.currentTimeMillis() - lapTimeS * 1000L)
        }
        return null
    }

    /**
     * Handles `BANALService.LAP_SUMMARY` (manual / pause lap broadcasts), preserving existing
     * `REQ-TRK-002` validation, `onDiscreteEdge` notification, and updating
     * [currentLapStartAnchor] while clearing any multisport active-leg metadata on non-pause laps.
     * Captures the closing lap's pre-mutation UTC start timestamp for unnamed laps (`REQ-TRK-090.1`).
     */
    fun onLapSummaryIntent(
        intent: Intent,
        workoutId: Long,
        currentTimestampMs: Long,
        currentActiveTimeS: Int,
        currentTotalTimeS: Int,
        currentDistanceM: Double,
        isStopping: Boolean,
        workoutRepository: WorkoutRepository,
        saveUnnamedLap: (Long, Int, Double, Double) -> Unit,
        onDiscreteEdge: (Long) -> Unit
    ) {
        val isPause = intent.getBooleanExtra(BANALService.IS_PAUSE, false)
        val lapTime = intent.getIntExtra(BANALService.PREV_LAP_TIME_S, 0)
        val lapDistance = intent.getDoubleExtra(BANALService.PREV_LAP_DISTANCE_m, 0.0)

        var closingStartUtc: String? = null
        var closingName: String? = null
        var previousLapStartMs = 0L
        if (!isPause) {
            synchronized(this) {
                previousLapStartMs = currentLapStartAnchor.timestampMs
                closingStartUtc = activeLegStartTimeUtc
                closingName = activeLegName
                val safeDist = if (currentDistanceM.isFinite() && currentDistanceM >= 0.0) currentDistanceM else 0.0
                currentLapStartAnchor = MultisportTelemetryAnchor(
                    timestampMs = currentTimestampMs.coerceAtLeast(0L),
                    activeTimeS = currentActiveTimeS.coerceAtLeast(0),
                    totalTimeS = currentTotalTimeS.coerceAtLeast(0),
                    totalDistanceM = safeDist
                )
                activeLegStartTimeUtc = null
                activeLegName = null
                pendingUnnamedLapStartUtc = null
            }
        }

        // ATT-896 / REQ-TRK-002: Discard empty split summaries
        if (lapTime <= 0 && lapDistance <= 0.0) {
            synchronized(this) {
                pendingUnnamedLapStartUtc = null
            }
            return
        }

        var lapSpeed = intent.getDoubleExtra(BANALService.PREV_LAP_SPEED_mps, 0.0)
        if (lapSpeed.isNaN() || lapSpeed.isInfinite()) {
            lapSpeed = safeAverageSpeed(lapDistance, lapTime)
        }
        val prevLapNr = intent.getIntExtra(BANALService.PREV_LAP_NR, 0).toLong()
        if (closingStartUtc != null || closingName != null) {
            synchronized(this) {
                pendingUnnamedLapStartUtc = null
            }
            workoutRepository.saveLap(
                workoutId, prevLapNr, closingStartUtc, lapTime, lapDistance, lapSpeed, closingName, null
            )
            if (!isStopping) {
                onDiscreteEdge(workoutId)
            }
        } else {
            synchronized(this) {
                pendingUnnamedLapStartUtc = if (!isPause && previousLapStartMs > 0L) {
                    formatUtcTimestamp(previousLapStartMs)
                } else {
                    null
                }
            }
            saveUnnamedLap(prevLapNr, lapTime, lapDistance, lapSpeed)
        }
    }

    /**
     * Persists the final active lap at workout stop, applying multisport-anchored UTC `timeStart`
     * and `name` when present, and clearing active-leg state afterward.
     */
    fun saveFinalLap(
        workoutRepository: WorkoutRepository,
        workoutId: Long,
        lapNr: Long,
        lapTimeS: Int,
        lapDistanceM: Double,
        lapSpeedMps: Double,
        saveUnnamedLap: (Long, Long, Int, Double, Double) -> Unit
    ) {
        val startUtc: String?
        val lapName: String?
        synchronized(this) {
            startUtc = activeLegStartTimeUtc
            lapName = activeLegName
            activeLegStartTimeUtc = null
            activeLegName = null
            pendingUnnamedLapStartUtc = null
        }
        if (startUtc != null || lapName != null) {
            workoutRepository.saveLap(
                workoutId, lapNr, startUtc, lapTimeS, lapDistanceM, lapSpeedMps, lapName, null
            )
        } else {
            saveUnnamedLap(workoutId, lapNr, lapTimeS, lapDistanceM, lapSpeedMps)
        }
    }

    /**
     * Handles `BANALService.MULTISPORT_TRANSITION_INTENT`:
     * 1. Snapshots `oldLapNr` BEFORE advancing live accumulators,
     * 2. Computes clamped retroactive leg metrics & pre-increment `REQ-TRK-002` `lapAdvanceCount`,
     * 3. Synchronously advances live device lap accumulators and re-seeds `TIME_LAP`/`DISTANCE_m_LAP`,
     * 4. Offloads `Laps.db` inserts and `WorkoutSamples.db` `LAP_NR` backfilling to `dbExecutor`
     *    chained after `tableInitFuture` (zero main-thread SQLite).
     */
    fun onMultisportTransitionIntent(
        context: Context,
        intent: Intent,
        banalService: BANALService.BANALServiceComm?,
        workoutIdProvider: () -> Long,
        baseFileNameProvider: () -> String?,
        isStoppingProvider: () -> Boolean,
        workoutRepository: WorkoutRepository,
        tableInitFuture: CompletableFuture<Void>?,
        dbExecutor: Executor,
        onDiscreteEdge: (Long) -> Unit
    ): RetroactiveLapPlan? {
        val event = MultisportTransitionBridge.fromIntent(intent) ?: return null
        val oldLapNr = readCurrentLapNr(banalService)
        val initialBaseFileName = baseFileNameProvider()
        val plan = planAndCommitAnchorTransition(context, event, oldLapNr, initialBaseFileName)

        // Synchronously advance live accumulators BEFORE enqueueing async DB persistence.
        if (plan.lapAdvanceCount > 0) {
            advanceLapsFn(plan.lapAdvanceCount, plan.newLegTimeLapS, plan.newLegDistanceLapM)
        }

        val persistTask = Runnable {
            val workoutId = workoutIdProvider()
            if (workoutId <= 0L) return@Runnable
            if (plan.hasLeg1) {
                workoutRepository.saveLap(
                    workoutId,
                    plan.leg1LapNr.toLong(),
                    plan.leg1StartTimeUtc,
                    plan.leg1ActiveTimeS,
                    plan.leg1DistanceM,
                    plan.leg1AvgSpeedMps,
                    plan.leg1Name,
                    null
                )
            }
            if (plan.hasTransition) {
                workoutRepository.saveLap(
                    workoutId,
                    plan.transitionLapNr.toLong(),
                    plan.transitionStartTimeUtc,
                    plan.transitionActiveTimeS,
                    plan.transitionDistanceM,
                    plan.transitionAvgSpeedMps,
                    plan.transitionName,
                    null
                )
            }
            val baseFileName = baseFileNameProvider()
            if (!baseFileName.isNullOrBlank() && plan.lapAdvanceCount > 0) {
                backfillWorkoutSamplesLapNumbers(context, baseFileName, plan)
            }
            if (!isStoppingProvider()) {
                onDiscreteEdge(workoutId)
            }
        }

        if (tableInitFuture != null) {
            tableInitFuture.thenRunAsync(persistTask, dbExecutor).exceptionally { ex ->
                Log.e(TAG, "Multisport lap persistence failed: ${ex.message}", ex)
                null
            }
        } else {
            dbExecutor.execute(persistTask)
        }
        return plan
    }

    /**
     * Pure deterministic planner updating [currentLapStartAnchor], [activeLegStartTimeUtc],
     * [activeLegName], and committed transition windows atomically under the leaf-only lock
     * (`REQ-FIL-031.3`, `REQ-TRK-096.1`).
     */
    @Synchronized
    @JvmOverloads
    fun planAndCommitAnchorTransition(
        context: Context?,
        event: MultisportTransitionEvent,
        oldLapNr: Int,
        baseFileName: String? = null
    ): RetroactiveLapPlan {
        val start = currentLapStartAnchor
        val effectiveExit = clampAnchor(event.exitAnchor, start)
        val effectiveEnter = clampAnchor(event.enterAnchor, effectiveExit)
        val effectiveConfirm = clampAnchor(event.confirmAnchor, effectiveEnter)

        val transWallMs = (effectiveEnter.timestampMs - effectiveExit.timestampMs).coerceAtLeast(0L)
        val transActiveS = (effectiveEnter.activeTimeS - effectiveExit.activeTimeS).coerceAtLeast(0)
        val transDistM = (effectiveEnter.totalDistanceM - effectiveExit.totalDistanceM).coerceAtLeast(0.0)
        val hasTransition = transWallMs >= MultisportTransitionDetector.MIN_TRANSITION_LAP_DURATION_MS &&
            (transActiveS > 0 || transDistM > 0.0)

        // When no Transition lap is emitted (< 10 s direct switch or 100% auto-paused stationary gap),
        // close Leg 1 at effectiveEnter so total time & distance remain 100% conserved.
        val leg1End = if (hasTransition) effectiveExit else effectiveEnter
        val leg1ActiveS = (leg1End.activeTimeS - start.activeTimeS).coerceAtLeast(0)
        val leg1DistM = (leg1End.totalDistanceM - start.totalDistanceM).coerceAtLeast(0.0)
        val hasLeg1 = leg1ActiveS > 0 || leg1DistM > 0.0

        val lapAdvanceCount = (if (hasLeg1) 1 else 0) + (if (hasTransition) 1 else 0)
        val leg1LapNr = oldLapNr
        val transitionLapNr = if (hasLeg1) oldLapNr + 1 else oldLapNr
        val newLegLapNr = oldLapNr + lapAdvanceCount

        val newLegTimeLapS = (effectiveConfirm.activeTimeS - effectiveEnter.activeTimeS).coerceAtLeast(0)
        val newLegDistLapM = (effectiveConfirm.totalDistanceM - effectiveEnter.totalDistanceM).coerceAtLeast(0.0)

        val leg1StartUtc = activeLegStartTimeUtc ?: formatUtcTimestamp(start.timestampMs)
        val newLegStartUtc = formatUtcTimestamp(effectiveEnter.timestampMs)
        val leg1Name = activeLegName ?: resolveSportLapName(context, event.fromSport)
        val transitionName = resolveTransitionLapName(context)
        val newLegName = resolveSportLapName(context, event.toSport)

        // Advance coordinator state to the newly anchored ongoing leg.
        currentLapStartAnchor = effectiveEnter
        activeLegStartTimeUtc = newLegStartUtc
        activeLegName = newLegName
        pendingUnnamedLapStartUtc = null

        val plan = RetroactiveLapPlan(
            oldLapNr = oldLapNr,
            hasLeg1 = hasLeg1,
            leg1LapNr = leg1LapNr,
            leg1StartTimeUtc = leg1StartUtc,
            leg1ActiveTimeS = leg1ActiveS,
            leg1DistanceM = leg1DistM,
            leg1AvgSpeedMps = safeAverageSpeed(leg1DistM, leg1ActiveS),
            leg1Name = leg1Name,
            hasTransition = hasTransition,
            transitionLapNr = transitionLapNr,
            transitionStartTimeUtc = formatUtcTimestamp(effectiveExit.timestampMs),
            transitionActiveTimeS = transActiveS,
            transitionDistanceM = transDistM,
            transitionAvgSpeedMps = safeAverageSpeed(transDistM, transActiveS),
            transitionName = transitionName,
            lapAdvanceCount = lapAdvanceCount,
            newLegLapNr = newLegLapNr,
            newLegStartTimeUtc = newLegStartUtc,
            newLegName = newLegName,
            newLegTimeLapS = newLegTimeLapS,
            newLegDistanceLapM = newLegDistLapM,
            effectiveExitAnchor = effectiveExit,
            effectiveEnterAnchor = effectiveEnter
        )
        if (plan.lapAdvanceCount > 0) {
            val normalizedFileName = baseFileName?.takeIf { it.isNotBlank() }
            val window = plan.toCommittedWindow(normalizedFileName)
            committedTransitions.add(window)
            hasCommittedTransitions = true
            if (normalizedFileName != null) {
                val list = pendingReconciliationByFileName.getOrPut(normalizedFileName) { mutableListOf() }
                list.add(window)
                while (pendingReconciliationByFileName.size > MAX_PENDING_RECONCILIATION_SESSIONS) {
                    val oldestKey = pendingReconciliationByFileName.keys.firstOrNull() ?: break
                    pendingReconciliationByFileName.remove(oldestKey)
                }
            }
        }
        return plan
    }

    /**
     * Enforces monotonic per-sample `LAP_NR` and decoupled workout-wide `WorkoutSummaries.LAPS`
     * floors on `mDbExecutor` immediately before `WorkoutRepository.recordLiveSample()`
     * (`REQ-TRK-096.1`). O(1) lock-free no-op for single-sport workouts.
     */
    @JvmOverloads
    fun enforceMonotonicLapNr(
        baseFileName: String?,
        samplingValues: ContentValues,
        summaryValues: ContentValues? = null,
        sampleEpochS: Long = System.currentTimeMillis() / 1000L
    ) {
        if (!hasCommittedTransitions) return
        val normalizedFileName = baseFileName?.takeIf { it.isNotBlank() }
        val matchingWindows: List<CommittedTransitionWindow> = synchronized(this) {
            if (committedTransitions.isEmpty()) return
            committedTransitions.filter { w ->
                w.baseFileName == null || normalizedFileName == null || w.baseFileName == normalizedFileName
            }
        }
        if (matchingWindows.isEmpty()) return

        val timeTotalS = samplingValues.getAsLong(SensorType.TIME_TOTAL.name)
        val requiredSampleLapNr = if (timeTotalS != null) {
            resolveRequiredLapNrByTotalTime(matchingWindows, timeTotalS)
        } else {
            val parsedEpochS = parseUtcTimestampEpochSeconds(
                samplingValues.getAsString(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME)
            ) ?: sampleEpochS
            resolveRequiredLapNrByEpoch(matchingWindows, parsedEpochS)
        }
        if (requiredSampleLapNr != null) {
            val currentLap = samplingValues.getAsInteger(SensorType.LAP_NR.name)
            if (currentLap == null || currentLap < requiredSampleLapNr) {
                samplingValues.put(SensorType.LAP_NR.name, requiredSampleLapNr)
            }
        }

        if (summaryValues != null && summaryValues.containsKey(WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS)) {
            val maxCommittedNewLegLapNr = matchingWindows.maxOf { it.newLegLapNr }
            val currentSummaryLaps = summaryValues.getAsInteger(WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS)
            if (currentSummaryLaps == null || currentSummaryLaps < maxCommittedNewLegLapNr) {
                summaryValues.put(WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS, maxCommittedNewLegLapNr)
            }
        }
    }

    /**
     * Idempotently reconciles `WorkoutSamples.db` `LAP_NR` at workout finalization on `mDbExecutor`
     * strictly before post-processing/export (`REQ-TRK-096.2`). Performs zero SQLite work for
     * single-sport workouts or blank `baseFileName`, and never throws out of finalization.
     */
    fun reconcileWorkoutSamplesOnFinalization(context: Context, baseFileName: String?) {
        val normalizedFileName = baseFileName?.takeIf { it.isNotBlank() } ?: return
        val windowsToApply: List<CommittedTransitionWindow> = synchronized(this) {
            val merged = LinkedHashSet<CommittedTransitionWindow>()
            for (w in committedTransitions) {
                if (w.baseFileName == null || w.baseFileName == normalizedFileName) {
                    merged.add(w)
                }
            }
            pendingReconciliationByFileName.remove(normalizedFileName)?.let { merged.addAll(it) }
            merged.toList()
        }
        if (windowsToApply.isEmpty()) return
        try {
            val manager = WorkoutSamplesDatabaseManager.getInstance(context)
            if (!manager.existsTable(normalizedFileName)) return
            val db = manager.database
            val table = WorkoutSamplesDatabaseManager.getTableName(normalizedFileName)
            reconcileWorkoutSamplesTable(db, table, windowsToApply)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed reconciling WorkoutSamples LAP_NR on finalization for $normalizedFileName: ${t.message}", t)
        }
    }

    private fun resolveRequiredLapNrByTotalTime(
        windows: List<CommittedTransitionWindow>,
        timeTotalS: Long
    ): Int? {
        var required: Int? = null
        for (w in windows) {
            val candidate = when {
                timeTotalS >= w.enterTotalS -> w.newLegLapNr
                w.hasTransition && w.enterTotalS > w.exitTotalS && timeTotalS >= w.exitTotalS -> w.transitionLapNr
                else -> null
            }
            if (candidate != null && (required == null || candidate > required)) {
                required = candidate
            }
        }
        return required
    }

    private fun resolveRequiredLapNrByEpoch(
        windows: List<CommittedTransitionWindow>,
        epochS: Long
    ): Int? {
        var required: Int? = null
        for (w in windows) {
            val candidate = when {
                epochS >= w.enterEpochS -> w.newLegLapNr
                w.hasTransition && w.enterEpochS > w.exitEpochS && epochS >= w.exitEpochS -> w.transitionLapNr
                else -> null
            }
            if (candidate != null && (required == null || candidate > required)) {
                required = candidate
            }
        }
        return required
    }

    private fun RetroactiveLapPlan.toCommittedWindow(baseFileName: String?): CommittedTransitionWindow =
        CommittedTransitionWindow(
            baseFileName = baseFileName?.takeIf { it.isNotBlank() },
            exitTotalS = effectiveExitAnchor.totalTimeS.toLong(),
            enterTotalS = effectiveEnterAnchor.totalTimeS.toLong(),
            exitEpochS = effectiveExitAnchor.timestampMs / 1000L,
            enterEpochS = effectiveEnterAnchor.timestampMs / 1000L,
            hasTransition = hasTransition,
            transitionLapNr = transitionLapNr,
            newLegLapNr = newLegLapNr
        )

    /**
     * Retroactively updates `LAP_NR` in `WorkoutSamples.db` without editing
     * `WorkoutSamplesDatabaseManager.java` (`1,533 / 1,533` LoC ceiling) via
     * [applyTransitionWindowsToTable]:
     * - Checks `PRAGMA table_info(table)` for `LAP_NR` (adding `INTEGER DEFAULT 1` if missing), `TIME_TOTAL`, and `time`.
     * - Uses strict monotonic predicates `(LAP_NR < targetLapNr OR LAP_NR IS NULL)` across half-open
     *   `[exit, enter)` (`transitionLapNr`) and `>= enter` (`newLegLapNr`) so stale `oldLapNr`, torn
     *   intermediate `oldLapNr + 1`, and `NULL` rows upgrade without ever downgrading higher manual laps.
     * - Uses combined `TIME_TOTAL IS NULL` + `CAST(strftime('%s', time) AS INTEGER)` fallback predicates
     *   when both `TIME_TOTAL` and `time` columns exist (and preserves single-column branches).
     */
    fun backfillWorkoutSamplesLapNumbers(
        context: Context,
        baseFileName: String,
        plan: RetroactiveLapPlan
    ) {
        try {
            val manager = WorkoutSamplesDatabaseManager.getInstance(context)
            if (!manager.existsTable(baseFileName)) return
            val db = manager.database
            val table = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
            backfillWorkoutSamplesTable(db, table, plan)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed backfilling WorkoutSamples LAP_NR for $baseFileName: ${t.message}", t)
        }
    }

    internal fun backfillWorkoutSamplesTable(
        db: SQLiteDatabase,
        table: String,
        plan: RetroactiveLapPlan
    ) {
        applyTransitionWindowsToTable(db, table, listOf(plan.toCommittedWindow(null)))
    }

    internal fun reconcileWorkoutSamplesTable(
        db: SQLiteDatabase,
        table: String,
        windows: List<CommittedTransitionWindow>
    ) {
        if (windows.isEmpty()) return
        applyTransitionWindowsToTable(db, table, windows)
    }

    private fun applyTransitionWindowsToTable(
        db: SQLiteDatabase,
        table: String,
        windows: List<CommittedTransitionWindow>
    ) {
        if (windows.isEmpty()) return
        val columns = readTableColumns(db, table)
        if (columns.isEmpty()) return

        val lapNrCol = SensorType.LAP_NR.name
        val timeTotalCol = SensorType.TIME_TOTAL.name
        val timeCol = WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME
        val hasLapNr = columns.contains(lapNrCol)
        val hasTimeTotal = columns.contains(timeTotalCol)
        val hasTimeCol = columns.contains(timeCol)
        if (!hasTimeTotal && !hasTimeCol) return

        val epochExpr = "CAST(strftime('%s', $timeCol) AS INTEGER)"

        db.beginTransaction()
        try {
            if (!hasLapNr) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $lapNrCol INTEGER DEFAULT 1")
            }

            for (w in windows) {
                if (hasTimeTotal && hasTimeCol) {
                    if (w.hasTransition) {
                        val hasTotalSpan = w.enterTotalS > w.exitTotalS
                        val hasEpochSpan = w.enterEpochS > w.exitEpochS
                        when {
                            hasTotalSpan && hasEpochSpan -> db.execSQL(
                                "UPDATE $table SET $lapNrCol = ? WHERE (($timeTotalCol >= ? AND $timeTotalCol < ?) OR ($timeTotalCol IS NULL AND $epochExpr >= ? AND $epochExpr < ?)) AND ($lapNrCol < ? OR $lapNrCol IS NULL)",
                                arrayOf<Any>(w.transitionLapNr, w.exitTotalS, w.enterTotalS, w.exitEpochS, w.enterEpochS, w.transitionLapNr)
                            )
                            hasTotalSpan -> db.execSQL(
                                "UPDATE $table SET $lapNrCol = ? WHERE $timeTotalCol >= ? AND $timeTotalCol < ? AND ($lapNrCol < ? OR $lapNrCol IS NULL)",
                                arrayOf<Any>(w.transitionLapNr, w.exitTotalS, w.enterTotalS, w.transitionLapNr)
                            )
                            hasEpochSpan -> db.execSQL(
                                "UPDATE $table SET $lapNrCol = ? WHERE $timeTotalCol IS NULL AND $epochExpr >= ? AND $epochExpr < ? AND ($lapNrCol < ? OR $lapNrCol IS NULL)",
                                arrayOf<Any>(w.transitionLapNr, w.exitEpochS, w.enterEpochS, w.transitionLapNr)
                            )
                        }
                    }
                    db.execSQL(
                        "UPDATE $table SET $lapNrCol = ? WHERE ($timeTotalCol >= ? OR ($timeTotalCol IS NULL AND $epochExpr >= ?)) AND ($lapNrCol < ? OR $lapNrCol IS NULL)",
                        arrayOf<Any>(w.newLegLapNr, w.enterTotalS, w.enterEpochS, w.newLegLapNr)
                    )
                } else if (hasTimeTotal) {
                    if (w.hasTransition && w.enterTotalS > w.exitTotalS) {
                        db.execSQL(
                            "UPDATE $table SET $lapNrCol = ? WHERE $timeTotalCol >= ? AND $timeTotalCol < ? AND ($lapNrCol < ? OR $lapNrCol IS NULL)",
                            arrayOf<Any>(w.transitionLapNr, w.exitTotalS, w.enterTotalS, w.transitionLapNr)
                        )
                    }
                    db.execSQL(
                        "UPDATE $table SET $lapNrCol = ? WHERE $timeTotalCol >= ? AND ($lapNrCol < ? OR $lapNrCol IS NULL)",
                        arrayOf<Any>(w.newLegLapNr, w.enterTotalS, w.newLegLapNr)
                    )
                } else {
                    if (w.hasTransition && w.enterEpochS > w.exitEpochS) {
                        db.execSQL(
                            "UPDATE $table SET $lapNrCol = ? WHERE $epochExpr >= ? AND $epochExpr < ? AND ($lapNrCol < ? OR $lapNrCol IS NULL)",
                            arrayOf<Any>(w.transitionLapNr, w.exitEpochS, w.enterEpochS, w.transitionLapNr)
                        )
                    }
                    db.execSQL(
                        "UPDATE $table SET $lapNrCol = ? WHERE $epochExpr >= ? AND ($lapNrCol < ? OR $lapNrCol IS NULL)",
                        arrayOf<Any>(w.newLegLapNr, w.enterEpochS, w.newLegLapNr)
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun readTableColumns(db: SQLiteDatabase, table: String): Set<String> {
        return SqliteSchemaUtils.getTableColumnNames(db, table)
    }

    private fun clampAnchor(
        candidate: MultisportTelemetryAnchor,
        floor: MultisportTelemetryAnchor
    ): MultisportTelemetryAnchor {
        val safeCandidateDist = if (candidate.totalDistanceM.isFinite() && candidate.totalDistanceM >= 0.0) {
            candidate.totalDistanceM
        } else {
            floor.totalDistanceM
        }
        return MultisportTelemetryAnchor(
            timestampMs = maxOf(candidate.timestampMs, floor.timestampMs),
            activeTimeS = maxOf(candidate.activeTimeS, floor.activeTimeS),
            totalTimeS = maxOf(candidate.totalTimeS, floor.totalTimeS),
            totalDistanceM = maxOf(safeCandidateDist, floor.totalDistanceM)
        )
    }

    private fun readCurrentLapNr(banalService: BANALService.BANALServiceComm?): Int {
        val raw = banalService?.getBestSensorData(SensorType.LAP_NR)?.value
        return (raw as? Number)?.toInt()?.coerceAtLeast(BANALService.INIT_LAP_NR)
            ?: BANALService.INIT_LAP_NR
    }
}
