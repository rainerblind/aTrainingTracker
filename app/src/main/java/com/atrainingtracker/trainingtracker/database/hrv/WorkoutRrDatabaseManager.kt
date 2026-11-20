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
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.VisibleForTesting

/**
 * Beat-level artifact classification and gap-count update keyed by persisted [beatIndex] (`REQ-HRV-048.2`).
 */
data class BeatQualityFlagUpdate(
    val beatIndex: Int,
    val artifactFlag: Int,
    val gapBeats: Int
)

/**
 * Thread-safe singleton manager for `WorkoutRr.db` (`REQ-PER-034.1`).
 *
 * Persists raw beat-to-beat intervals with high performance batch insertion, indexed
 * workout lookup, and cascading deletion.
 */
class WorkoutRrDatabaseManager private constructor(context: Context) {

    private val dbHelper = WorkoutRrDbHelper(context.applicationContext)

    val database: SQLiteDatabase
        @Synchronized get() = dbHelper.writableDatabase

    companion object {
        @Volatile
        private var instance: WorkoutRrDatabaseManager? = null

        @JvmStatic
        fun getInstance(context: Context): WorkoutRrDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: WorkoutRrDatabaseManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        @JvmStatic
        @VisibleForTesting
        fun resetForTesting(newInstance: WorkoutRrDatabaseManager? = null) {
            instance = newInstance
        }
    }

    /**
     * Batch inserts a list of [WorkoutRrInterval] records into `workout_rr_intervals`.
     * Uses a single transaction with pre-compiled SQLiteStatement for optimal insertion speed.
     */
    @Synchronized
    fun insertIntervals(intervals: List<WorkoutRrInterval>): Int {
        if (intervals.isEmpty()) return 0
        val db = database
        db.beginTransaction()
        try {
            val sql = """
                INSERT OR REPLACE INTO ${WorkoutRrDbHelper.TABLE_WORKOUT_RR} (
                    ${WorkoutRrDbHelper.COLUMN_WORKOUT_ID},
                    ${WorkoutRrDbHelper.COLUMN_BEAT_INDEX},
                    ${WorkoutRrDbHelper.COLUMN_BEAT_TIMESTAMP_MS},
                    ${WorkoutRrDbHelper.COLUMN_RR_MS},
                    ${WorkoutRrDbHelper.COLUMN_ARTIFACT_FLAG},
                    ${WorkoutRrDbHelper.COLUMN_GAP_BEATS}
                ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
            val stmt = db.compileStatement(sql)
            var count = 0
            for (interval in intervals) {
                stmt.bindLong(1, interval.workoutId)
                stmt.bindLong(2, interval.beatIndex.toLong())
                stmt.bindLong(3, interval.beatTimestampMs)
                stmt.bindDouble(4, interval.rrMs.toDouble())
                stmt.bindLong(5, interval.artifactFlag.toLong())
                stmt.bindLong(6, interval.gapBeats.toLong())
                stmt.executeInsert()
                count++
            }
            db.setTransactionSuccessful()
            return count
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Batch-updates `(artifact_flag, gap_beats)` for persisted beats of [workoutId] keyed by
     * `(workout_id, beat_index)` in a single SQLite transaction (`REQ-HRV-048.2` / `ATT-9160`).
     *
     * @return Total number of rows actually modified in `workout_rr_intervals`.
     */
    @Synchronized
    fun updateBeatQualityFlags(workoutId: Long, updates: List<BeatQualityFlagUpdate>): Int {
        if (updates.isEmpty()) return 0
        val db = database
        db.beginTransaction()
        try {
            val sql = """
                UPDATE ${WorkoutRrDbHelper.TABLE_WORKOUT_RR}
                SET ${WorkoutRrDbHelper.COLUMN_ARTIFACT_FLAG} = ?,
                    ${WorkoutRrDbHelper.COLUMN_GAP_BEATS} = ?
                WHERE ${WorkoutRrDbHelper.COLUMN_WORKOUT_ID} = ?
                  AND ${WorkoutRrDbHelper.COLUMN_BEAT_INDEX} = ?
            """.trimIndent()
            var updatedRows = 0
            db.compileStatement(sql).use { stmt ->
                for (update in updates) {
                    stmt.bindLong(1, update.artifactFlag.toLong())
                    stmt.bindLong(2, update.gapBeats.toLong())
                    stmt.bindLong(3, workoutId)
                    stmt.bindLong(4, update.beatIndex.toLong())
                    updatedRows += stmt.executeUpdateDelete()
                }
            }
            db.setTransactionSuccessful()
            return updatedRows
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Returns all intervals for a specific workout sorted chronologically by [beatIndex].
     */
    @Synchronized
    fun getIntervalsForWorkout(workoutId: Long): List<WorkoutRrInterval> {
        val db = database
        val cursor: Cursor = db.query(
            WorkoutRrDbHelper.TABLE_WORKOUT_RR,
            null,
            "${WorkoutRrDbHelper.COLUMN_WORKOUT_ID} = ?",
            arrayOf(workoutId.toString()),
            null,
            null,
            "${WorkoutRrDbHelper.COLUMN_BEAT_INDEX} ASC"
        )
        return cursor.use { WorkoutRrInterval.fromCursorBatch(it) }
    }

    /**
     * Returns true if at least one RR interval exists for the workout.
     */
    @Synchronized
    fun hasIntervalsForWorkout(workoutId: Long): Boolean {
        val db = database
        val cursor = db.rawQuery(
            "SELECT 1 FROM ${WorkoutRrDbHelper.TABLE_WORKOUT_RR} WHERE ${WorkoutRrDbHelper.COLUMN_WORKOUT_ID} = ? LIMIT 1",
            arrayOf(workoutId.toString())
        )
        return cursor.use { it.moveToFirst() }
    }

    /**
     * Returns the maximum beat index for the workout, or -1 if no beats exist.
     */
    @Synchronized
    fun getMaxBeatIndex(workoutId: Long): Int {
        val db = database
        val cursor = db.rawQuery(
            "SELECT MAX(${WorkoutRrDbHelper.COLUMN_BEAT_INDEX}) FROM ${WorkoutRrDbHelper.TABLE_WORKOUT_RR} WHERE ${WorkoutRrDbHelper.COLUMN_WORKOUT_ID} = ?",
            arrayOf(workoutId.toString())
        )
        return cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                it.getInt(0)
            } else {
                -1
            }
        }
    }

    /**
     * Deletes all intervals associated with a given [workoutId].
     */
    @Synchronized
    fun deleteWorkout(workoutId: Long): Int {
        val db = database
        return db.delete(
            WorkoutRrDbHelper.TABLE_WORKOUT_RR,
            "${WorkoutRrDbHelper.COLUMN_WORKOUT_ID} = ?",
            arrayOf(workoutId.toString())
        )
    }

    /**
     * Deletes all intervals in the database.
     */
    @Synchronized
    fun deleteAll(): Int {
        val db = database
        return db.delete(WorkoutRrDbHelper.TABLE_WORKOUT_RR, null, null)
    }
}
