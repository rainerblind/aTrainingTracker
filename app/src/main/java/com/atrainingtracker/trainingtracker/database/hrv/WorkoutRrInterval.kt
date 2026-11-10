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

import android.content.ContentValues
import android.database.Cursor

/**
 * Immutable entity representing a single beat-to-beat RR interval persisted for a workout session (REQ-PER-034.1).
 *
 * @property id SQLite row ID (null if not yet persisted).
 * @property workoutId The parent workout session ID in WorkoutSummaries.db.
 * @property beatIndex Monotonic 0-based sequence index within the workout.
 * @property beatTimestampMs Monotonically reconstructed UTC epoch timestamp in milliseconds.
 * @property rrMs Beat-to-beat RR interval in milliseconds.
 * @property artifactFlag Artifact flag (0 = NORMAL, 1 = ECTOPIC, 2 = SHORT, 3 = LONG, 4 = MISSED, 5 = GAP).
 * @property gapBeats Number of lost/dropped beats across an RF gap preceding this beat (0 for continuous).
 */
data class WorkoutRrInterval(
    val id: Long? = null,
    val workoutId: Long,
    val beatIndex: Int,
    val beatTimestampMs: Long,
    val rrMs: Float,
    val artifactFlag: Int = 0,
    val gapBeats: Int = 0
) {
    fun toContentValues(): ContentValues {
        return ContentValues().apply {
            if (id != null) put(WorkoutRrDbHelper.COLUMN_ID, id)
            put(WorkoutRrDbHelper.COLUMN_WORKOUT_ID, workoutId)
            put(WorkoutRrDbHelper.COLUMN_BEAT_INDEX, beatIndex)
            put(WorkoutRrDbHelper.COLUMN_BEAT_TIMESTAMP_MS, beatTimestampMs)
            put(WorkoutRrDbHelper.COLUMN_RR_MS, rrMs)
            put(WorkoutRrDbHelper.COLUMN_ARTIFACT_FLAG, artifactFlag)
            put(WorkoutRrDbHelper.COLUMN_GAP_BEATS, gapBeats)
        }
    }

    companion object {
        fun fromCursor(cursor: Cursor): WorkoutRrInterval {
            val idIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_ID)
            val workoutIdIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_WORKOUT_ID)
            val beatIndexIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_BEAT_INDEX)
            val beatTsIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_BEAT_TIMESTAMP_MS)
            val rrMsIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_RR_MS)
            val artifactFlagIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_ARTIFACT_FLAG)
            val gapBeatsIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_GAP_BEATS)

            return WorkoutRrInterval(
                id = cursor.getLong(idIdx),
                workoutId = cursor.getLong(workoutIdIdx),
                beatIndex = cursor.getInt(beatIndexIdx),
                beatTimestampMs = cursor.getLong(beatTsIdx),
                rrMs = cursor.getFloat(rrMsIdx),
                artifactFlag = cursor.getInt(artifactFlagIdx),
                gapBeats = cursor.getInt(gapBeatsIdx)
            )
        }

        fun fromCursorBatch(cursor: Cursor): List<WorkoutRrInterval> {
            val list = ArrayList<WorkoutRrInterval>(cursor.count.coerceAtLeast(0))
            if (!cursor.moveToFirst()) return list

            val idIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_ID)
            val workoutIdIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_WORKOUT_ID)
            val beatIndexIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_BEAT_INDEX)
            val beatTsIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_BEAT_TIMESTAMP_MS)
            val rrMsIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_RR_MS)
            val artifactFlagIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_ARTIFACT_FLAG)
            val gapBeatsIdx = cursor.getColumnIndexOrThrow(WorkoutRrDbHelper.COLUMN_GAP_BEATS)

            do {
                list.add(
                    WorkoutRrInterval(
                        id = cursor.getLong(idIdx),
                        workoutId = cursor.getLong(workoutIdIdx),
                        beatIndex = cursor.getInt(beatIndexIdx),
                        beatTimestampMs = cursor.getLong(beatTsIdx),
                        rrMs = cursor.getFloat(rrMsIdx),
                        artifactFlag = cursor.getInt(artifactFlagIdx),
                        gapBeats = cursor.getInt(gapBeatsIdx)
                    )
                )
            } while (cursor.moveToNext())
            return list
        }
    }
}
