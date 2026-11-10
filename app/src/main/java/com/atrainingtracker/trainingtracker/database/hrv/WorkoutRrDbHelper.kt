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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

/**
 * SQLiteOpenHelper managing the dedicated beat-to-beat RR interval database `WorkoutRr.db` (`REQ-PER-034.1`).
 *
 * Traceability:
 * - REQ-PER-034.1: Dedicated SQLite persistence with symmetric DTO mapping.
 * - Architecture Rule 62: Direct DB_VERSION reference in DatabaseSchemaRegistry.
 * - Architecture Rule 64: Schema parity between onCreate and future onUpgrade.
 */
class WorkoutRrDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "WorkoutRr.db"
        const val DB_VERSION = 1

        const val TABLE_WORKOUT_RR = "workout_rr_intervals"

        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_WORKOUT_ID = "workout_id"
        const val COLUMN_BEAT_INDEX = "beat_index"
        const val COLUMN_BEAT_TIMESTAMP_MS = "beat_timestamp_ms"
        const val COLUMN_RR_MS = "rr_ms"
        const val COLUMN_ARTIFACT_FLAG = "artifact_flag"
        const val COLUMN_GAP_BEATS = "gap_beats"

        const val CREATE_TABLE_WORKOUT_RR = """
            CREATE TABLE IF NOT EXISTS $TABLE_WORKOUT_RR (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_WORKOUT_ID INTEGER NOT NULL,
                $COLUMN_BEAT_INDEX INTEGER NOT NULL,
                $COLUMN_BEAT_TIMESTAMP_MS INTEGER NOT NULL,
                $COLUMN_RR_MS REAL NOT NULL,
                $COLUMN_ARTIFACT_FLAG INTEGER NOT NULL DEFAULT 0,
                $COLUMN_GAP_BEATS INTEGER NOT NULL DEFAULT 0
            );
        """

        const val CREATE_INDEX_WORKOUT_BEAT = """
            CREATE INDEX IF NOT EXISTS idx_workout_rr_workout_beat
            ON $TABLE_WORKOUT_RR ($COLUMN_WORKOUT_ID, $COLUMN_BEAT_INDEX);
        """

        const val CREATE_INDEX_WORKOUT_TS = """
            CREATE INDEX IF NOT EXISTS idx_workout_rr_workout_ts
            ON $TABLE_WORKOUT_RR ($COLUMN_WORKOUT_ID, $COLUMN_BEAT_TIMESTAMP_MS);
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_WORKOUT_RR)
        db.execSQL(CREATE_INDEX_WORKOUT_BEAT)
        db.execSQL(CREATE_INDEX_WORKOUT_TS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // V1 Initial creation. Schema parity guaranteed per Rule 64.
    }
}
