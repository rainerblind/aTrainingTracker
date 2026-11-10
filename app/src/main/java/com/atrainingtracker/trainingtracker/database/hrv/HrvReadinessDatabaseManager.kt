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
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import androidx.annotation.VisibleForTesting

/**
 * SQLiteOpenHelper managing the dedicated morning HRV readiness database `HrvReadiness.db` (`REQ-PER-030.1`).
 *
 * Traceability:
 * - REQ-PER-030.1: Dedicated SQLite persistence with symmetric DTO mapping.
 * - Architecture Rule 62: Direct DB_VERSION reference in DatabaseSchemaRegistry.
 * - Architecture Rule 64: Schema parity between onCreate and future onUpgrade.
 */
class HrvReadinessDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "HrvReadiness.db"
        const val DB_VERSION = 1

        const val TABLE_HRV_READINESS = "hrv_readiness_entries"

        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_TIMESTAMP_MS = "timestamp_ms"
        const val COLUMN_DATE_ISO = "date_iso"
        const val COLUMN_TIMEZONE_ID = "timezone_id"
        const val COLUMN_RESTING_HR_BPM = "resting_hr_bpm"
        const val COLUMN_RMSSD_MS = "rmssd_ms"
        const val COLUMN_LN_RMSSD = "ln_rmssd"
        const val COLUMN_HRV_SCORE_100 = "hrv_score_100"
        const val COLUMN_SDNN_MS = "sdnn_ms"
        const val COLUMN_ARTIFACT_RATE_PCT = "artifact_rate_pct"
        const val COLUMN_DURATION_SEC = "duration_sec"
        const val COLUMN_POSTURE = "posture"
        const val COLUMN_IS_PRIMARY_FOR_DAY = "is_primary_for_day"
        const val COLUMN_NOTES = "notes"

        const val CREATE_TABLE_HRV_READINESS = """
            CREATE TABLE IF NOT EXISTS $TABLE_HRV_READINESS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TIMESTAMP_MS INTEGER NOT NULL,
                $COLUMN_DATE_ISO TEXT NOT NULL,
                $COLUMN_TIMEZONE_ID TEXT NOT NULL,
                $COLUMN_RESTING_HR_BPM INTEGER NOT NULL,
                $COLUMN_RMSSD_MS REAL NOT NULL,
                $COLUMN_LN_RMSSD REAL NOT NULL,
                $COLUMN_HRV_SCORE_100 REAL NOT NULL,
                $COLUMN_SDNN_MS REAL,
                $COLUMN_ARTIFACT_RATE_PCT REAL,
                $COLUMN_DURATION_SEC INTEGER NOT NULL,
                $COLUMN_POSTURE TEXT NOT NULL,
                $COLUMN_IS_PRIMARY_FOR_DAY INTEGER NOT NULL DEFAULT 0,
                $COLUMN_NOTES TEXT
            );
        """

        const val CREATE_INDEX_DATE_POSTURE = """
            CREATE INDEX IF NOT EXISTS idx_hrv_readiness_date_posture
            ON $TABLE_HRV_READINESS ($COLUMN_DATE_ISO, $COLUMN_POSTURE);
        """

        const val CREATE_INDEX_TIMESTAMP = """
            CREATE INDEX IF NOT EXISTS idx_hrv_readiness_ts
            ON $TABLE_HRV_READINESS ($COLUMN_TIMESTAMP_MS);
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_HRV_READINESS)
        db.execSQL(CREATE_INDEX_DATE_POSTURE)
        db.execSQL(CREATE_INDEX_TIMESTAMP)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // V1 Initial creation. Schema parity guaranteed per Rule 64.
    }
}

/**
 * Thread-safe singleton manager for `HrvReadiness.db` (`REQ-PER-030.1`, `REQ-PER-030.3`).
 *
 * Implements transactional daily deduplication and primary reading maintenance per `(date_iso, posture)`
 * so that queries consistently evaluate the user-pinned or earliest valid morning measurement.
 */
class HrvReadinessDatabaseManager private constructor(context: Context) {

    private val dbHelper = HrvReadinessDbHelper(context.applicationContext)

    val database: SQLiteDatabase
        @Synchronized get() = dbHelper.writableDatabase

    companion object {
        const val MAX_VALID_ARTIFACT_RATE_PCT = 5.0

        @Volatile
        private var instance: HrvReadinessDatabaseManager? = null

        @JvmStatic
        fun getInstance(context: Context): HrvReadinessDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: HrvReadinessDatabaseManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        @JvmStatic
        @VisibleForTesting
        fun resetForTesting(newInstance: HrvReadinessDatabaseManager? = null) {
            instance = newInstance
        }
    }

    /**
     * Inserts an [entry] into `HrvReadiness.db` and transactionally recomputes the primary reading
     * flag for its `(date_iso, posture)` group (`REQ-PER-030.3`).
     */
    @Synchronized
    fun insertEntry(entry: HrvReadinessEntry): Long {
        val db = database
        db.beginTransaction()
        try {
            val cv = entry.toContentValues()
            val rowId = db.insert(HrvReadinessDbHelper.TABLE_HRV_READINESS, null, cv)
            if (rowId > 0) {
                recomputePrimaryForDayInternal(
                    db = db,
                    dateIso = entry.dateIso,
                    posture = entry.posture,
                    pinnedId = if (entry.isPrimaryForDay) rowId else null
                )
            }
            db.setTransactionSuccessful()
            return rowId
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Deletes the entry with [id] and recomputes the primary flag for its `(date_iso, posture)`.
     */
    @Synchronized
    fun deleteEntry(id: Long): Boolean {
        val existing = getEntryById(id) ?: return false
        val db = database
        db.beginTransaction()
        try {
            val rows = db.delete(
                HrvReadinessDbHelper.TABLE_HRV_READINESS,
                "${HrvReadinessDbHelper.COLUMN_ID} = ?",
                arrayOf(id.toString())
            )
            if (rows > 0) {
                recomputePrimaryForDayInternal(
                    db = db,
                    dateIso = existing.dateIso,
                    posture = existing.posture,
                    pinnedId = null
                )
            }
            db.setTransactionSuccessful()
            return rows > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Explicitly pins or unpins an entry as the primary reading for its `(date_iso, posture)` (`REQ-PER-030.3`).
     */
    @Synchronized
    fun setPrimaryReading(id: Long, isPrimary: Boolean = true): Boolean {
        val existing = getEntryById(id) ?: return false
        val db = database
        db.beginTransaction()
        try {
            if (isPrimary) {
                recomputePrimaryForDayInternal(
                    db = db,
                    dateIso = existing.dateIso,
                    posture = existing.posture,
                    pinnedId = id
                )
            } else {
                // Remove primary flag from this id, then recompute among remaining valid readings
                db.execSQL(
                    "UPDATE ${HrvReadinessDbHelper.TABLE_HRV_READINESS} SET ${HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY} = 0 WHERE ${HrvReadinessDbHelper.COLUMN_ID} = ?",
                    arrayOf(id.toString())
                )
                recomputePrimaryForDayInternal(
                    db = db,
                    dateIso = existing.dateIso,
                    posture = existing.posture,
                    pinnedId = null
                )
            }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Recomputes `is_primary_for_day` for [dateIso] and [posture] (`REQ-PER-030.3`).
     */
    @Synchronized
    fun recomputePrimaryForDay(dateIso: String, posture: HrvPosture) {
        val db = database
        db.beginTransaction()
        try {
            recomputePrimaryForDayInternal(db, dateIso, posture, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun recomputePrimaryForDayInternal(
        db: SQLiteDatabase,
        dateIso: String,
        posture: HrvPosture,
        pinnedId: Long?
    ) {
        if (pinnedId != null) {
            db.execSQL(
                """
                UPDATE ${HrvReadinessDbHelper.TABLE_HRV_READINESS}
                SET ${HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY} = CASE WHEN ${HrvReadinessDbHelper.COLUMN_ID} = ? THEN 1 ELSE 0 END
                WHERE ${HrvReadinessDbHelper.COLUMN_DATE_ISO} = ? AND ${HrvReadinessDbHelper.COLUMN_POSTURE} = ?
                """,
                arrayOf(pinnedId.toString(), dateIso, posture.name)
            )
            return
        }

        // Query all valid readings for (dateIso, posture) ordered by is_primary_for_day DESC, timestamp_ms ASC
        val query = """
            SELECT ${HrvReadinessDbHelper.COLUMN_ID}, ${HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY}
            FROM ${HrvReadinessDbHelper.TABLE_HRV_READINESS}
            WHERE ${HrvReadinessDbHelper.COLUMN_DATE_ISO} = ?
              AND ${HrvReadinessDbHelper.COLUMN_POSTURE} = ?
              AND (${HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT} IS NULL OR ${HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT} <= ?)
            ORDER BY ${HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY} DESC, ${HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS} ASC
        """

        var chosenPrimaryId: Long? = null
        db.rawQuery(query, arrayOf(dateIso, posture.name, MAX_VALID_ARTIFACT_RATE_PCT.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                chosenPrimaryId = cursor.getLong(0)
            }
        }

        if (chosenPrimaryId != null) {
            db.execSQL(
                """
                UPDATE ${HrvReadinessDbHelper.TABLE_HRV_READINESS}
                SET ${HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY} = CASE WHEN ${HrvReadinessDbHelper.COLUMN_ID} = ? THEN 1 ELSE 0 END
                WHERE ${HrvReadinessDbHelper.COLUMN_DATE_ISO} = ? AND ${HrvReadinessDbHelper.COLUMN_POSTURE} = ?
                """,
                arrayOf(chosenPrimaryId.toString(), dateIso, posture.name)
            )
        } else {
            // No valid readings: clear all primary flags for this date/posture
            db.execSQL(
                """
                UPDATE ${HrvReadinessDbHelper.TABLE_HRV_READINESS}
                SET ${HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY} = 0
                WHERE ${HrvReadinessDbHelper.COLUMN_DATE_ISO} = ? AND ${HrvReadinessDbHelper.COLUMN_POSTURE} = ?
                """,
                arrayOf(dateIso, posture.name)
            )
        }
    }

    /**
     * Retrieves an entry by its primary key [id].
     */
    @Synchronized
    fun getEntryById(id: Long): HrvReadinessEntry? {
        val db = database
        val query = "SELECT * FROM ${HrvReadinessDbHelper.TABLE_HRV_READINESS} WHERE ${HrvReadinessDbHelper.COLUMN_ID} = ?"
        db.rawQuery(query, arrayOf(id.toString())).use { cursor ->
            return if (cursor.moveToFirst()) {
                HrvReadinessEntry.fromCursor(cursor)
            } else {
                null
            }
        }
    }

    /**
     * Retrieves all entries for a specific [dateIso] and [posture] ordered by timestamp.
     */
    @Synchronized
    fun getEntriesForDate(dateIso: String, posture: HrvPosture): List<HrvReadinessEntry> {
        val db = database
        val query = """
            SELECT * FROM ${HrvReadinessDbHelper.TABLE_HRV_READINESS}
            WHERE ${HrvReadinessDbHelper.COLUMN_DATE_ISO} = ? AND ${HrvReadinessDbHelper.COLUMN_POSTURE} = ?
            ORDER BY ${HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS} ASC
        """
        db.rawQuery(query, arrayOf(dateIso, posture.name)).use { cursor ->
            return HrvReadinessEntry.fromCursorBatch(cursor)
        }
    }

    /**
     * Retrieves the primary valid reading for a specific [dateIso] and [posture] (`REQ-PER-030.3`).
     */
    @Synchronized
    fun getPrimaryEntryForDate(dateIso: String, posture: HrvPosture): HrvReadinessEntry? {
        val db = database
        val query = """
            SELECT * FROM ${HrvReadinessDbHelper.TABLE_HRV_READINESS}
            WHERE ${HrvReadinessDbHelper.COLUMN_DATE_ISO} = ?
              AND ${HrvReadinessDbHelper.COLUMN_POSTURE} = ?
              AND (${HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT} IS NULL OR ${HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT} <= ?)
            ORDER BY ${HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY} DESC, ${HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS} ASC
            LIMIT 1
        """
        db.rawQuery(query, arrayOf(dateIso, posture.name, MAX_VALID_ARTIFACT_RATE_PCT.toString())).use { cursor ->
            return if (cursor.moveToFirst()) {
                HrvReadinessEntry.fromCursor(cursor)
            } else {
                null
            }
        }
    }

    /**
     * Retrieves the list of primary valid entries across all distinct dates within `[startDateIso, endDateIso]`.
     */
    @Synchronized
    fun getPrimaryEntriesInWindow(
        posture: HrvPosture,
        startDateIso: String,
        endDateIso: String
    ): List<HrvReadinessEntry> {
        val db = database
        // First fetch all valid entries in date range for posture
        val query = """
            SELECT * FROM ${HrvReadinessDbHelper.TABLE_HRV_READINESS}
            WHERE ${HrvReadinessDbHelper.COLUMN_POSTURE} = ?
              AND ${HrvReadinessDbHelper.COLUMN_DATE_ISO} >= ?
              AND ${HrvReadinessDbHelper.COLUMN_DATE_ISO} <= ?
              AND (${HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT} IS NULL OR ${HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT} <= ?)
            ORDER BY ${HrvReadinessDbHelper.COLUMN_DATE_ISO} ASC, ${HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY} DESC, ${HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS} ASC
        """
        val allValid = ArrayList<HrvReadinessEntry>()
        db.rawQuery(query, arrayOf(posture.name, startDateIso, endDateIso, MAX_VALID_ARTIFACT_RATE_PCT.toString())).use { cursor ->
            allValid.addAll(HrvReadinessEntry.fromCursorBatch(cursor))
        }

        // Deduplicate by date_iso taking the first (which has is_primary_for_day DESC, timestamp ASC)
        val result = LinkedHashMap<String, HrvReadinessEntry>()
        for (entry in allValid) {
            if (!result.containsKey(entry.dateIso)) {
                result[entry.dateIso] = entry
            }
        }
        return result.values.toList()
    }

    /**
     * Retrieves all entries in the database, optionally filtered by [posture].
     */
    @Synchronized
    fun getAllEntries(posture: HrvPosture? = null): List<HrvReadinessEntry> {
        val db = database
        val query = if (posture != null) {
            "SELECT * FROM ${HrvReadinessDbHelper.TABLE_HRV_READINESS} WHERE ${HrvReadinessDbHelper.COLUMN_POSTURE} = ? ORDER BY ${HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS} ASC"
        } else {
            "SELECT * FROM ${HrvReadinessDbHelper.TABLE_HRV_READINESS} ORDER BY ${HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS} ASC"
        }
        val args = if (posture != null) arrayOf(posture.name) else null
        db.rawQuery(query, args).use { cursor ->
            return HrvReadinessEntry.fromCursorBatch(cursor)
        }
    }

    /**
     * Returns total number of records.
     */
    @Synchronized
    fun getEntriesCount(): Long {
        val db = database
        db.rawQuery("SELECT COUNT(*) FROM ${HrvReadinessDbHelper.TABLE_HRV_READINESS}", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    /**
     * Deletes all records.
     */
    @Synchronized
    fun clearDatabase(): Int {
        val db = database
        return db.delete(HrvReadinessDbHelper.TABLE_HRV_READINESS, null, null)
    }

    @Synchronized
    fun close() {
        dbHelper.close()
    }
}
