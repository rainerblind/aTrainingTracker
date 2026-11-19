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
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import java.time.Instant
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

    private var currentLapStartAnchor: MultisportTelemetryAnchor =
        MultisportTelemetryAnchor(0L, 0, 0, 0.0)
    private var activeLegStartTimeUtc: String? = null
    private var activeLegName: String? = null

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
    }

    @Synchronized
    fun getCurrentLapStartAnchor(): MultisportTelemetryAnchor = currentLapStartAnchor

    @Synchronized
    fun getActiveLapStartTimeUtc(): String? = activeLegStartTimeUtc

    @Synchronized
    fun getActiveLapName(): String? = activeLegName

    /**
     * Handles `BANALService.LAP_SUMMARY` (manual / pause lap broadcasts), preserving existing
     * `REQ-TRK-002` validation, `onDiscreteEdge` notification, and updating
     * [currentLapStartAnchor] while clearing any multisport active-leg metadata on non-pause laps.
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
        if (!isPause) {
            synchronized(this) {
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
            }
        }

        // ATT-896 / REQ-TRK-002: Discard empty split summaries
        if (lapTime <= 0 && lapDistance <= 0.0) {
            return
        }

        var lapSpeed = intent.getDoubleExtra(BANALService.PREV_LAP_SPEED_mps, 0.0)
        if (lapSpeed.isNaN() || lapSpeed.isInfinite()) {
            lapSpeed = safeAverageSpeed(lapDistance, lapTime)
        }
        val prevLapNr = intent.getIntExtra(BANALService.PREV_LAP_NR, 0).toLong()
        if (closingStartUtc != null || closingName != null) {
            workoutRepository.saveLap(
                workoutId, prevLapNr, closingStartUtc, lapTime, lapDistance, lapSpeed, closingName, null
            )
            if (!isStopping) {
                onDiscreteEdge(workoutId)
            }
        } else {
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
        val plan = planAndCommitAnchorTransition(context, event, oldLapNr)

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
     * Pure deterministic planner updating [currentLapStartAnchor], [activeLegStartTimeUtc], and
     * [activeLegName] atomically under lock.
     */
    @Synchronized
    fun planAndCommitAnchorTransition(
        context: Context?,
        event: MultisportTransitionEvent,
        oldLapNr: Int
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

        return RetroactiveLapPlan(
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
    }

    /**
     * Retroactively updates `LAP_NR` in `WorkoutSamples.db` without editing
     * `WorkoutSamplesDatabaseManager.java` (`1,533 / 1,533` LoC ceiling):
     * - Checks `PRAGMA table_info(table)` for `LAP_NR` (adding `INTEGER DEFAULT 1` if missing) and `TIME_TOTAL`.
     * - Tags half-open `[exitTotalS, enterTotalS)` for `Transition` and `>= enterTotalS AND (LAP_NR = oldLapNr OR LAP_NR IS NULL)`
     *   for the new leg (falling back to `CAST(strftime('%s', time) AS INTEGER)` if `TIME_TOTAL` is absent).
     */
    fun backfillWorkoutSamplesLapNumbers(
        context: Context,
        baseFileName: String,
        plan: RetroactiveLapPlan
    ) {
        try {
            val manager = WorkoutSamplesDatabaseManager.getInstance(context)
            if (!manager.existsTable(baseFileName)) return
            val db = manager.database ?: return
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
        val columns = readTableColumns(db, table)
        if (columns.isEmpty()) return

        val lapNrCol = SensorType.LAP_NR.name
        val timeTotalCol = SensorType.TIME_TOTAL.name
        val timeCol = WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME
        val hasLapNr = columns.contains(lapNrCol)
        val hasTimeTotal = columns.contains(timeTotalCol)
        val hasTimeCol = columns.contains(timeCol)

        db.beginTransaction()
        try {
            if (!hasLapNr) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $lapNrCol INTEGER DEFAULT 1")
            }

            if (hasTimeTotal) {
                val exitS = plan.effectiveExitAnchor.totalTimeS.toLong()
                val enterS = plan.effectiveEnterAnchor.totalTimeS.toLong()
                if (plan.hasTransition && enterS > exitS) {
                    db.execSQL(
                        "UPDATE $table SET $lapNrCol = ? WHERE $timeTotalCol >= ? AND $timeTotalCol < ?",
                        arrayOf<Any>(plan.transitionLapNr, exitS, enterS)
                    )
                }
                db.execSQL(
                    "UPDATE $table SET $lapNrCol = ? WHERE $timeTotalCol >= ? AND ($lapNrCol = ? OR $lapNrCol IS NULL)",
                    arrayOf<Any>(plan.newLegLapNr, enterS, plan.oldLapNr)
                )
            } else if (hasTimeCol) {
                val exitEpochS = plan.effectiveExitAnchor.timestampMs / 1000L
                val enterEpochS = plan.effectiveEnterAnchor.timestampMs / 1000L
                if (plan.hasTransition && enterEpochS > exitEpochS) {
                    db.execSQL(
                        "UPDATE $table SET $lapNrCol = ? WHERE CAST(strftime('%s', $timeCol) AS INTEGER) >= ? AND CAST(strftime('%s', $timeCol) AS INTEGER) < ?",
                        arrayOf<Any>(plan.transitionLapNr, exitEpochS, enterEpochS)
                    )
                }
                db.execSQL(
                    "UPDATE $table SET $lapNrCol = ? WHERE CAST(strftime('%s', $timeCol) AS INTEGER) >= ? AND ($lapNrCol = ? OR $lapNrCol IS NULL)",
                    arrayOf<Any>(plan.newLegLapNr, enterEpochS, plan.oldLapNr)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun readTableColumns(db: SQLiteDatabase, table: String): Set<String> {
        val result = HashSet<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0) {
                    result.add(cursor.getString(nameIdx))
                }
            }
        }
        return result
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
