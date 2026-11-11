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

package com.atrainingtracker.trainingtracker.database.hrv

import android.content.Context
import android.database.Cursor
import android.util.Log
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.hrv.DfaCalculator
import com.atrainingtracker.banalservice.devices.hrv.FilteredRrBeat
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityAnalyzer
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityResult
import com.atrainingtracker.banalservice.devices.hrv.RrArtifactFilter
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.sqrt

/**
 * Centered-window post-workout HRV recomputation engine (REQ-PER-034.3).
 *
 * Re-evaluates post-workout HRV metrics over persisted raw beat intervals using
 * non-causal bidirectional filtering:
 * - Centered DFA alpha 1 over 120s window (t ± 60s) with artifact gate suppression.
 * - Centered bias-free rMSSD over 60s window (t ± 30s).
 * - Eliminates the ~60s phase lag of live causal trailing filters.
 * - Updates `WorkoutSamples.db` trackpoint columns and `WorkoutSummaries.db` session extrema.
 */
object HrvPostWorkoutRecomputer {

    private const val TAG = "HrvPostWorkoutRecomp"
    const val WINDOW_DFA_HALF_MS = 60000L   // ±60s = 120s centered window
    const val WINDOW_RMSSD_HALF_MS = 30000L // ±30s = 60s centered window
    const val ARTIFACT_GATE_THRESHOLD_PERCENT = 5.0
    const val MIN_DFA_BEATS = 32
    const val MIN_RMSSD_PAIRS = 3

    @JvmStatic
    fun recomputeWorkoutBlocking(context: Context, workoutId: Long): Boolean {
        return runBlocking(Dispatchers.IO) {
            recomputeWorkout(context, workoutId)
        }
    }

    suspend fun recomputeWorkout(context: Context, workoutId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val summariesDbManager = WorkoutSummariesDatabaseManager.getInstance(context)
            val baseFileName = summariesDbManager.getBaseFileName(workoutId) ?: return@withContext false

            val rrDbManager = WorkoutRrDatabaseManager.getInstance(context)
            val intervals = rrDbManager.getIntervalsForWorkout(workoutId)
            if (intervals.isEmpty() || intervals.size < 10) {
                return@withContext false
            }

            // Convert to RrBeatEvents
            val beats = intervals.map { interval ->
                RrBeatEvent(
                    beatTimestampMs = interval.beatTimestampMs,
                    rrMs = interval.rrMs.toDouble(),
                    rawCounter = interval.beatIndex,
                    protocol = ProtocolType.BLE,
                    gapBeats = interval.gapBeats,
                    artifactFlag = interval.artifactFlag
                )
            }

            // Run post-hoc bidirectional centered artifact filter
            val artifactFilter = RrArtifactFilter()
            val filterResult = artifactFilter.filterBidirectionalBeats(beats)
            val filteredBeats = filterResult.beats

            val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
            val samplesDb = samplesDbManager.database
            val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

            val sampleRows = mutableListOf<SampleTimeRow>()
            try {
                samplesDb.query(
                    tableName,
                    arrayOf(WorkoutSamplesDbHelper.C_ID, WorkoutSamplesDbHelper.TIME),
                    null, null, null, null,
                    "${WorkoutSamplesDbHelper.C_ID} ASC"
                ).use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.C_ID)
                    val timeCol = cursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME)
                    var prevTs = 0L
                    while (cursor.moveToNext()) {
                        val rowId = cursor.getLong(idCol)
                        val timeStr = cursor.getString(timeCol)
                        val parsedTs = parseTimestampMs(timeStr) ?: (if (prevTs > 0) prevTs + 1000L else beats.first().beatTimestampMs)
                        prevTs = parsedTs
                        sampleRows.add(SampleTimeRow(rowId, parsedTs))
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Table $tableName not available or query failed: ${t.message}")
                return@withContext false
            }

            if (sampleRows.isEmpty()) return@withContext false

            val rmssdValues = mutableListOf<Double>()
            val dfaValues = mutableListOf<Double>()

            samplesDb.beginTransaction()
            try {
                val updateSql = """
                    UPDATE $tableName SET 
                        ${SensorType.HRV_DFA_A1.name} = ?, 
                        ${SensorType.HRV_RMSSD.name} = ?, 
                        ${SensorType.HRV_ARTIFACT_RATE.name} = ? 
                    WHERE ${WorkoutSamplesDbHelper.C_ID} = ?
                """.trimIndent()
                val stmt = samplesDb.compileStatement(updateSql)

                for (row in sampleRows) {
                    val t = row.timestampMs

                    // 1. Artifact rate & DFA alpha 1 in centered window [t - 60s, t + 60s]
                    val dfaMinTs = t - WINDOW_DFA_HALF_MS
                    val dfaMaxTs = t + WINDOW_DFA_HALF_MS
                    val dfaBeats = filteredBeats.filter { it.beatTimestampMs in dfaMinTs..dfaMaxTs }

                    val artifactCount = dfaBeats.count { it.isArtifact }
                    val artifactRate = if (dfaBeats.isNotEmpty()) {
                        (artifactCount.toDouble() / dfaBeats.size) * 100.0
                    } else 0.0

                    val dfaAlpha1: Double? = if (artifactRate <= ARTIFACT_GATE_THRESHOLD_PERCENT && dfaBeats.size >= MIN_DFA_BEATS) {
                        val nnIntervals = dfaBeats.map { it.correctedRrMs }.toDoubleArray()
                        val calculated = DfaCalculator.computeDfaAlpha1(nnIntervals)
                        if (calculated.isFinite() && !calculated.isNaN()) calculated else null
                    } else null

                    // 2. Bias-free rMSSD in centered window [t - 30s, t + 30s]
                    val rmssdMinTs = t - WINDOW_RMSSD_HALF_MS
                    val rmssdMaxTs = t + WINDOW_RMSSD_HALF_MS
                    val rmssdBeats = filteredBeats.filter { it.beatTimestampMs in rmssdMinTs..rmssdMaxTs }
                    val rmssd = computeBiasFreeRmssd(rmssdBeats)

                    if (dfaAlpha1 != null) {
                        stmt.bindDouble(1, dfaAlpha1)
                        dfaValues.add(dfaAlpha1)
                    } else {
                        stmt.bindNull(1)
                    }

                    if (rmssd != null) {
                        stmt.bindDouble(2, rmssd)
                        rmssdValues.add(rmssd)
                    } else {
                        stmt.bindNull(2)
                    }

                    stmt.bindDouble(3, artifactRate)
                    stmt.bindLong(4, row.rowId)
                    stmt.executeUpdateDelete()
                }

                samplesDb.setTransactionSuccessful()
            } finally {
                samplesDb.endTransaction()
            }

            // 3. Update session extrema in WorkoutSummaries.db
            val summariesDb = summariesDbManager.database
            summariesDb.beginTransaction()
            try {
                if (rmssdValues.isNotEmpty()) {
                    val minRmssd = rmssdValues.minOrNull() ?: 0.0
                    val maxRmssd = rmssdValues.maxOrNull() ?: 0.0
                    val avgRmssd = rmssdValues.average()
                    summariesDbManager.updateExtremaValue(summariesDb, workoutId, SensorType.HRV_RMSSD, ExtremaType.MIN, minRmssd, null)
                    summariesDbManager.updateExtremaValue(summariesDb, workoutId, SensorType.HRV_RMSSD, ExtremaType.MAX, maxRmssd, null)
                    summariesDbManager.updateExtremaValue(summariesDb, workoutId, SensorType.HRV_RMSSD, ExtremaType.AVG, avgRmssd, null)
                }
                if (dfaValues.isNotEmpty()) {
                    val minDfa = dfaValues.minOrNull() ?: 0.0
                    val maxDfa = dfaValues.maxOrNull() ?: 0.0
                    val avgDfa = dfaValues.average()
                    summariesDbManager.updateExtremaValue(summariesDb, workoutId, SensorType.HRV_DFA_A1, ExtremaType.MIN, minDfa, null)
                    summariesDbManager.updateExtremaValue(summariesDb, workoutId, SensorType.HRV_DFA_A1, ExtremaType.MAX, maxDfa, null)
                    summariesDbManager.updateExtremaValue(summariesDb, workoutId, SensorType.HRV_DFA_A1, ExtremaType.AVG, avgDfa, null)
                }
                summariesDb.setTransactionSuccessful()
            } finally {
                summariesDb.endTransaction()
            }

            Log.i(TAG, "Recomputed centered HRV streams for workout $workoutId ($baseFileName): ${dfaValues.size} DFA, ${rmssdValues.size} rMSSD samples")
            return@withContext true
        } catch (t: Throwable) {
            Log.e(TAG, "Error recomputing centered HRV streams for workout $workoutId: ${t.message}", t)
            return@withContext false
        }
    }

    /**
     * Evaluates bias-free rMSSD from successive valid (non-artifact) beat intervals.
     */
    fun computeBiasFreeRmssd(beats: List<FilteredRrBeat>): Double? {
        if (beats.size < 2) return null
        var sumSquaredDiffs = 0.0
        var validPairCount = 0
        for (i in 0 until beats.size - 1) {
            val b1 = beats[i]
            val b2 = beats[i + 1]
            if (!b1.isArtifact && !b2.isArtifact) {
                val diff = b2.rawRrMs - b1.rawRrMs
                sumSquaredDiffs += diff * diff
                validPairCount++
            }
        }
        if (validPairCount < MIN_RMSSD_PAIRS) {
            return null
        }
        return sqrt(sumSquaredDiffs / validPairCount.toDouble())
    }

    /**
     * Evaluates signal quality over a collection of workout RR beats pursuant to REQ-TRK-067.6.
     */
    @JvmStatic
    fun analyzeSignalQuality(beats: List<FilteredRrBeat>): HrvSignalQualityResult {
        return HrvSignalQualityAnalyzer.analyzeBeats(beats)
    }

    private fun parseTimestampMs(timeStr: String?): Long? {
        if (timeStr.isNullOrBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd_HHmmss"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(timeStr)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private data class SampleTimeRow(
        val rowId: Long,
        val timestampMs: Long
    )
}
