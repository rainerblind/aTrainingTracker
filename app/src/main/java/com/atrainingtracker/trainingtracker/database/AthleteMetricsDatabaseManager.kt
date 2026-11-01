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

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.zones.ZoneCalculationMath

/**
 * Supported physiological and body composition metric types persisted in [AthleteMetricsDatabaseManager] (`REQ-UI-391.2`).
 *
 * @property minMetricValue Minimum valid canonical metric value (`kg` for weight, `bpm` for resting HR, `W` for FTP).
 * @property maxMetricValue Maximum valid canonical metric value.
 */
enum class AthleteMetricType(
    val minMetricValue: Double,
    val maxMetricValue: Double
) {
    WEIGHT_KG(
        minMetricValue = SettingsDataStore.MIN_ATHLETE_WEIGHT_KG.toDouble(),
        maxMetricValue = SettingsDataStore.MAX_ATHLETE_WEIGHT_KG.toDouble()
    ),
    HR_REST_BPM(
        minMetricValue = ZoneCalculationMath.MIN_HR_REST.toDouble(),
        maxMetricValue = ZoneCalculationMath.MAX_HR_REST.toDouble()
    ),
    FTP_WATTS(
        minMetricValue = ZoneCalculationMath.MIN_FTP.toDouble(),
        maxMetricValue = ZoneCalculationMath.MAX_FTP.toDouble()
    ),
    CP_WATTS(
        minMetricValue = 50.0,
        maxMetricValue = 1000.0
    ),
    W_PRIME_KJ(
        minMetricValue = 5.0,
        maxMetricValue = 60.0
    );

    /**
     * Checks whether [value] lies within the allowed canonical range `[minMetricValue, maxMetricValue]`.
     */
    fun isValidMetricValue(value: Double): Boolean =
        value.isFinite() && value in minMetricValue..maxMetricValue

    companion object {
        fun fromNameOrNull(name: String?): AthleteMetricType? =
            entries.firstOrNull { it.name == name }
    }
}

/**
 * Immutable data transfer object representing a single timestamped athlete metric measurement (`REQ-UI-391.2`).
 *
 * Implements symmetric single-row (`fromCursor`) and batch (`fromCursorBatch`) SQLite cursor mappers per Rule 20.
 */
data class AthleteMetricEntry(
    val id: Long = 0L,
    val timestampMs: Long,
    val metricType: AthleteMetricType,
    val value: Double
) {
    companion object {
        /**
         * Maps the current row of [cursor] into an [AthleteMetricEntry] without closing [cursor] (Rule 20).
         */
        @JvmStatic
        fun fromCursor(cursor: Cursor): AthleteMetricEntry {
            val idIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.C_ID)
            val tsIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.TIMESTAMP_MS)
            val typeIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.METRIC_TYPE)
            val valIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.VALUE)

            val typeName = cursor.getString(typeIdx)
            val metricType = AthleteMetricType.fromNameOrNull(typeName)
                ?: throw IllegalArgumentException("Unknown AthleteMetricType in cursor: $typeName")

            return AthleteMetricEntry(
                id = cursor.getLong(idIdx),
                timestampMs = cursor.getLong(tsIdx),
                metricType = metricType,
                value = cursor.getDouble(valIdx)
            )
        }

        /**
         * Maps all rows of [cursor] into a list of [AthleteMetricEntry] using identical column resolution
         * as [fromCursor] and without closing [cursor] (Rule 20).
         */
        @JvmStatic
        fun fromCursorBatch(cursor: Cursor): List<AthleteMetricEntry> {
            val result = ArrayList<AthleteMetricEntry>(cursor.count.coerceAtLeast(0))
            if (!cursor.moveToFirst()) return result

            val idIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.C_ID)
            val tsIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.TIMESTAMP_MS)
            val typeIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.METRIC_TYPE)
            val valIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.VALUE)

            do {
                val typeName = cursor.getString(typeIdx)
                val metricType = AthleteMetricType.fromNameOrNull(typeName)
                if (metricType != null) {
                    result.add(
                        AthleteMetricEntry(
                            id = cursor.getLong(idIdx),
                            timestampMs = cursor.getLong(tsIdx),
                            metricType = metricType,
                            value = cursor.getDouble(valIdx)
                        )
                    )
                }
            } while (cursor.moveToNext())

            return result
        }
    }
}

/**
 * Immutable data transfer object representing a timestamped training zone boundary vector snapshot (`REQ-SET-082.1`).
 *
 * Implements symmetric single-row ([fromCursor]) and batch ([fromCursorBatch]) SQLite cursor mappers per Rule 20.
 */
data class ZoneSnapshotEntry(
    val id: Long = 0L,
    val timestampMs: Long,
    val zoneType: SettingsDataStore.ZoneType,
    val boundaries: List<Int>
) {
    fun isMonotonic(): Boolean = try {
        validateBoundaries(zoneType, boundaries)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    companion object {
        fun serializeBoundaries(boundaries: List<Int>): String =
            boundaries.joinToString(",")

        fun parseBoundaries(raw: String): List<Int> =
            if (raw.isBlank()) emptyList() else raw.split(",").mapNotNull { it.trim().toIntOrNull() }

        fun validateBoundaries(zoneType: SettingsDataStore.ZoneType, boundaries: List<Int>) {
            require(boundaries.size == zoneType.boundaryCount) {
                "Invalid boundary count ${boundaries.size} for $zoneType (expected ${zoneType.boundaryCount})"
            }
            require(boundaries.first() > 0) {
                "First boundary must be positive (> 0) for $zoneType"
            }
            for (i in 0 until boundaries.size - 1) {
                require(boundaries[i] < boundaries[i + 1]) {
                    "Non-monotonic boundaries for $zoneType: ${boundaries[i]} >= ${boundaries[i + 1]}"
                }
            }
        }

        @JvmStatic
        fun fromCursor(cursor: Cursor): ZoneSnapshotEntry {
            val idIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.C_ID)
            val tsIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.TIMESTAMP_MS)
            val typeIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.ZONE_TYPE)
            val boundsIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.BOUNDARIES)

            val typeName = cursor.getString(typeIdx)
            val zoneType = SettingsDataStore.ZoneType.entries.firstOrNull { it.name == typeName }
                ?: SettingsDataStore.ZoneType.HR_RUN

            return ZoneSnapshotEntry(
                id = cursor.getLong(idIdx),
                timestampMs = cursor.getLong(tsIdx),
                zoneType = zoneType,
                boundaries = parseBoundaries(cursor.getString(boundsIdx))
            )
        }

        @JvmStatic
        fun fromCursorBatch(cursor: Cursor): List<ZoneSnapshotEntry> {
            val result = ArrayList<ZoneSnapshotEntry>(cursor.count.coerceAtLeast(0))
            if (!cursor.moveToFirst()) return result

            val idIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.C_ID)
            val tsIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.TIMESTAMP_MS)
            val typeIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.ZONE_TYPE)
            val boundsIdx = cursor.getColumnIndexOrThrow(AthleteMetricsDbHelper.BOUNDARIES)

            do {
                val typeName = cursor.getString(typeIdx)
                val zoneType = SettingsDataStore.ZoneType.entries.firstOrNull { it.name == typeName }
                if (zoneType != null) {
                    result.add(
                        ZoneSnapshotEntry(
                            id = cursor.getLong(idIdx),
                            timestampMs = cursor.getLong(tsIdx),
                            zoneType = zoneType,
                            boundaries = parseBoundaries(cursor.getString(boundsIdx))
                        )
                    )
                }
            } while (cursor.moveToNext())

            return result
        }
    }
}

/**
 * SQLite helper managing `AthleteMetrics.db` (`REQ-UI-391.2`).
 */
class AthleteMetricsDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "AthleteMetrics.db"
        const val DB_VERSION = 2

        const val TABLE_HISTORY = "athlete_metrics_history"
        const val TABLE_ZONE_HISTORY = "zone_history"
        const val C_ID = BaseColumns._ID
        const val TIMESTAMP_MS = "timestamp_ms"
        const val METRIC_TYPE = "metric_type"
        const val VALUE = "value"
        const val ZONE_TYPE = "zone_type"
        const val BOUNDARIES = "boundaries"

        private const val CREATE_TABLE_V1 = """
            CREATE TABLE IF NOT EXISTS $TABLE_HISTORY (
                $C_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $TIMESTAMP_MS INTEGER NOT NULL,
                $METRIC_TYPE TEXT NOT NULL,
                $VALUE REAL NOT NULL CHECK($VALUE > 0)
            )
        """

        private const val CREATE_INDEX_V1 = """
            CREATE INDEX IF NOT EXISTS idx_athlete_metrics_type_time
            ON $TABLE_HISTORY ($METRIC_TYPE, $TIMESTAMP_MS DESC, $C_ID DESC)
        """

        private const val CREATE_TABLE_ZONE_HISTORY_V2 = """
            CREATE TABLE IF NOT EXISTS $TABLE_ZONE_HISTORY (
                $C_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $TIMESTAMP_MS INTEGER NOT NULL,
                $ZONE_TYPE TEXT NOT NULL,
                $BOUNDARIES TEXT NOT NULL
            )
        """

        private const val CREATE_INDEX_ZONE_HISTORY_V2 = """
            CREATE INDEX IF NOT EXISTS idx_zone_history_type_time
            ON $TABLE_ZONE_HISTORY ($ZONE_TYPE, $TIMESTAMP_MS DESC, $C_ID DESC)
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_V1)
        db.execSQL(CREATE_INDEX_V1)
        db.execSQL(CREATE_TABLE_ZONE_HISTORY_V2)
        db.execSQL(CREATE_INDEX_ZONE_HISTORY_V2)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(CREATE_TABLE_ZONE_HISTORY_V2)
            db.execSQL(CREATE_INDEX_ZONE_HISTORY_V2)
        }
    }
}

/**
 * Thread-safe database manager for timestamped athlete metrics (`REQ-UI-391.2`).
 */
class AthleteMetricsDatabaseManager private constructor(context: Context) {

    private val dbHelper = AthleteMetricsDbHelper(context.applicationContext)

    val database: SQLiteDatabase
        @Synchronized get() = dbHelper.writableDatabase

    /**
     * Returns all recorded athlete metric entries ordered by `timestamp_ms DESC, _id DESC`.
     */
    @Synchronized
    fun getAllEntries(): List<AthleteMetricEntry> {
        val db = database
        db.query(
            AthleteMetricsDbHelper.TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "${AthleteMetricsDbHelper.TIMESTAMP_MS} DESC, ${AthleteMetricsDbHelper.C_ID} DESC"
        ).use { cursor ->
            return AthleteMetricEntry.fromCursorBatch(cursor)
        }
    }

    /**
     * Returns all recorded entries for [metricType] ordered by `timestamp_ms DESC, _id DESC`.
     */
    @Synchronized
    fun getEntriesByType(metricType: AthleteMetricType): List<AthleteMetricEntry> {
        val db = database
        db.query(
            AthleteMetricsDbHelper.TABLE_HISTORY,
            null,
            "${AthleteMetricsDbHelper.METRIC_TYPE} = ?",
            arrayOf(metricType.name),
            null,
            null,
            "${AthleteMetricsDbHelper.TIMESTAMP_MS} DESC, ${AthleteMetricsDbHelper.C_ID} DESC"
        ).use { cursor ->
            return AthleteMetricEntry.fromCursorBatch(cursor)
        }
    }

    /**
     * Returns the chronologically latest entry for [metricType] (`ORDER BY timestamp_ms DESC, _id DESC LIMIT 1`),
     * or `null` if no entries exist for that metric.
     */
    @Synchronized
    fun getLatestEntry(metricType: AthleteMetricType): AthleteMetricEntry? {
        val db = database
        db.query(
            AthleteMetricsDbHelper.TABLE_HISTORY,
            null,
            "${AthleteMetricsDbHelper.METRIC_TYPE} = ?",
            arrayOf(metricType.name),
            null,
            null,
            "${AthleteMetricsDbHelper.TIMESTAMP_MS} DESC, ${AthleteMetricsDbHelper.C_ID} DESC",
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) AthleteMetricEntry.fromCursor(cursor) else null
        }
    }

    /**
     * Returns the entry for [metricType] effective at [timestampMs] (`REQ-MIG-039.2`):
     * queries the chronologically latest entry with `timestamp_ms <= timestampMs`
     * (`ORDER BY timestamp_ms DESC, _id DESC LIMIT 1`), falling back to [getLatestEntry]
     * if no historical entry exists at or before [timestampMs].
     */
    @Synchronized
    fun getEntryEffectiveAt(metricType: AthleteMetricType, timestampMs: Long): AthleteMetricEntry? {
        if (timestampMs > 0L) {
            val db = database
            db.query(
                AthleteMetricsDbHelper.TABLE_HISTORY,
                null,
                "${AthleteMetricsDbHelper.METRIC_TYPE} = ? AND ${AthleteMetricsDbHelper.TIMESTAMP_MS} <= ?",
                arrayOf(metricType.name, timestampMs.toString()),
                null,
                null,
                "${AthleteMetricsDbHelper.TIMESTAMP_MS} DESC, ${AthleteMetricsDbHelper.C_ID} DESC",
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return AthleteMetricEntry.fromCursor(cursor)
                }
            }
        }
        return getLatestEntry(metricType)
    }

    /**
     * Inserts a new measurement entry after validating canonical bounds.
     */
    @Synchronized
    fun insertEntry(timestampMs: Long, metricType: AthleteMetricType, value: Double): Long {
        require(metricType.isValidMetricValue(value)) {
            "Invalid value $value for $metricType (expected ${metricType.minMetricValue}..${metricType.maxMetricValue})"
        }
        val values = ContentValues().apply {
            put(AthleteMetricsDbHelper.TIMESTAMP_MS, timestampMs)
            put(AthleteMetricsDbHelper.METRIC_TYPE, metricType.name)
            put(AthleteMetricsDbHelper.VALUE, value)
        }
        return database.insertOrThrow(AthleteMetricsDbHelper.TABLE_HISTORY, null, values)
    }

    /**
     * Updates an existing measurement entry's timestamp and value after validating canonical bounds.
     */
    @Synchronized
    fun updateEntry(id: Long, metricType: AthleteMetricType, timestampMs: Long, value: Double): Int {
        require(metricType.isValidMetricValue(value)) {
            "Invalid value $value for $metricType (expected ${metricType.minMetricValue}..${metricType.maxMetricValue})"
        }
        val values = ContentValues().apply {
            put(AthleteMetricsDbHelper.TIMESTAMP_MS, timestampMs)
            put(AthleteMetricsDbHelper.METRIC_TYPE, metricType.name)
            put(AthleteMetricsDbHelper.VALUE, value)
        }
        return database.update(
            AthleteMetricsDbHelper.TABLE_HISTORY,
            values,
            "${AthleteMetricsDbHelper.C_ID} = ?",
            arrayOf(id.toString())
        )
    }

    /**
     * Deletes a measurement entry by its primary key [id].
     */
    @Synchronized
    fun deleteEntry(id: Long): Int {
        return database.delete(
            AthleteMetricsDbHelper.TABLE_HISTORY,
            "${AthleteMetricsDbHelper.C_ID} = ?",
            arrayOf(id.toString())
        )
    }

    /**
     * Atomically seeds initial entries inside a single SQLite transaction (`REQ-UI-391.3`),
     * inserting each entry only if no entries for its [AthleteMetricType] currently exist.
     */
    @Synchronized
    fun seedEntriesInTransaction(seedCandidates: List<AthleteMetricEntry>) {
        if (seedCandidates.isEmpty()) return
        val db = database
        db.beginTransaction()
        try {
            for (candidate in seedCandidates) {
                if (!candidate.metricType.isValidMetricValue(candidate.value)) continue
                val existing = getLatestEntry(candidate.metricType)
                if (existing == null) {
                    val values = ContentValues().apply {
                        put(AthleteMetricsDbHelper.TIMESTAMP_MS, candidate.timestampMs)
                        put(AthleteMetricsDbHelper.METRIC_TYPE, candidate.metricType.name)
                        put(AthleteMetricsDbHelper.VALUE, candidate.value)
                    }
                    db.insert(AthleteMetricsDbHelper.TABLE_HISTORY, null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --- ZONE HISTORY METHODS (REQ-SET-082.1) ---

    /**
     * Inserts a new timestamped zone boundary vector snapshot after validating monotonicity.
     */
    @Synchronized
    fun insertZoneSnapshot(
        timestampMs: Long,
        zoneType: SettingsDataStore.ZoneType,
        boundaries: List<Int>
    ): Long {
        ZoneSnapshotEntry.validateBoundaries(zoneType, boundaries)
        val values = ContentValues().apply {
            put(AthleteMetricsDbHelper.TIMESTAMP_MS, timestampMs)
            put(AthleteMetricsDbHelper.ZONE_TYPE, zoneType.name)
            put(AthleteMetricsDbHelper.BOUNDARIES, ZoneSnapshotEntry.serializeBoundaries(boundaries))
        }
        return database.insertOrThrow(AthleteMetricsDbHelper.TABLE_ZONE_HISTORY, null, values)
    }

    /**
     * Updates an existing zone boundary snapshot after validating monotonicity.
     */
    @Synchronized
    fun updateZoneSnapshot(
        id: Long,
        timestampMs: Long,
        zoneType: SettingsDataStore.ZoneType,
        boundaries: List<Int>
    ): Int {
        ZoneSnapshotEntry.validateBoundaries(zoneType, boundaries)
        val values = ContentValues().apply {
            put(AthleteMetricsDbHelper.TIMESTAMP_MS, timestampMs)
            put(AthleteMetricsDbHelper.ZONE_TYPE, zoneType.name)
            put(AthleteMetricsDbHelper.BOUNDARIES, ZoneSnapshotEntry.serializeBoundaries(boundaries))
        }
        return database.update(
            AthleteMetricsDbHelper.TABLE_ZONE_HISTORY,
            values,
            "${AthleteMetricsDbHelper.C_ID} = ?",
            arrayOf(id.toString())
        )
    }

    /**
     * Deletes a zone boundary snapshot by primary key [id].
     */
    @Synchronized
    fun deleteZoneSnapshot(id: Long): Int {
        return database.delete(
            AthleteMetricsDbHelper.TABLE_ZONE_HISTORY,
            "${AthleteMetricsDbHelper.C_ID} = ?",
            arrayOf(id.toString())
        )
    }

    /**
     * Returns the zone snapshot effective at [timestampMs] (`REQ-SET-082.3`):
     * queries the chronologically latest entry with `timestamp_ms <= timestampMs`,
     * falling back to the earliest recorded snapshot for [zoneType] if the workout
     * predates all entries, or null if no snapshots exist.
     */
    @Synchronized
    fun getEffectiveZoneSnapshot(
        zoneType: SettingsDataStore.ZoneType,
        timestampMs: Long
    ): ZoneSnapshotEntry? {
        val db = database
        if (timestampMs > 0L) {
            db.query(
                AthleteMetricsDbHelper.TABLE_ZONE_HISTORY,
                null,
                "${AthleteMetricsDbHelper.ZONE_TYPE} = ? AND ${AthleteMetricsDbHelper.TIMESTAMP_MS} <= ?",
                arrayOf(zoneType.name, timestampMs.toString()),
                null,
                null,
                "${AthleteMetricsDbHelper.TIMESTAMP_MS} DESC, ${AthleteMetricsDbHelper.C_ID} DESC",
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return ZoneSnapshotEntry.fromCursor(cursor)
                }
            }
        }
        // Fallback: earliest recorded snapshot for that zoneType
        db.query(
            AthleteMetricsDbHelper.TABLE_ZONE_HISTORY,
            null,
            "${AthleteMetricsDbHelper.ZONE_TYPE} = ?",
            arrayOf(zoneType.name),
            null,
            null,
            "${AthleteMetricsDbHelper.TIMESTAMP_MS} ASC, ${AthleteMetricsDbHelper.C_ID} ASC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return ZoneSnapshotEntry.fromCursor(cursor)
            }
        }
        return null
    }

    /**
     * Returns the chronologically latest zone snapshot for [zoneType].
     */
    @Synchronized
    fun getLatestZoneSnapshot(zoneType: SettingsDataStore.ZoneType): ZoneSnapshotEntry? {
        val db = database
        db.query(
            AthleteMetricsDbHelper.TABLE_ZONE_HISTORY,
            null,
            "${AthleteMetricsDbHelper.ZONE_TYPE} = ?",
            arrayOf(zoneType.name),
            null,
            null,
            "${AthleteMetricsDbHelper.TIMESTAMP_MS} DESC, ${AthleteMetricsDbHelper.C_ID} DESC",
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) ZoneSnapshotEntry.fromCursor(cursor) else null
        }
    }

    /**
     * Returns all recorded snapshots for [zoneType] ordered by `timestamp_ms DESC, _id DESC`.
     */
    @Synchronized
    fun getZoneHistory(zoneType: SettingsDataStore.ZoneType): List<ZoneSnapshotEntry> {
        val db = database
        db.query(
            AthleteMetricsDbHelper.TABLE_ZONE_HISTORY,
            null,
            "${AthleteMetricsDbHelper.ZONE_TYPE} = ?",
            arrayOf(zoneType.name),
            null,
            null,
            "${AthleteMetricsDbHelper.TIMESTAMP_MS} DESC, ${AthleteMetricsDbHelper.C_ID} DESC"
        ).use { cursor ->
            return ZoneSnapshotEntry.fromCursorBatch(cursor)
        }
    }

    /**
     * Returns all recorded zone snapshots across all zone types ordered by `timestamp_ms DESC, _id DESC`.
     */
    @Synchronized
    fun getAllZoneSnapshots(): List<ZoneSnapshotEntry> {
        val db = database
        db.query(
            AthleteMetricsDbHelper.TABLE_ZONE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "${AthleteMetricsDbHelper.TIMESTAMP_MS} DESC, ${AthleteMetricsDbHelper.C_ID} DESC"
        ).use { cursor ->
            return ZoneSnapshotEntry.fromCursorBatch(cursor)
        }
    }

    /**
     * Atomically seeds initial zone snapshots inside a single SQLite transaction (`REQ-SET-082.1`),
     * inserting each entry only if no entries for its [SettingsDataStore.ZoneType] currently exist.
     */
    @Synchronized
    fun seedZoneSnapshotsInTransaction(candidates: List<ZoneSnapshotEntry>) {
        if (candidates.isEmpty()) return
        val db = database
        db.beginTransaction()
        try {
            for (candidate in candidates) {
                try {
                    ZoneSnapshotEntry.validateBoundaries(candidate.zoneType, candidate.boundaries)
                } catch (e: Exception) {
                    continue
                }
                val existing = getLatestZoneSnapshot(candidate.zoneType)
                if (existing == null) {
                    val values = ContentValues().apply {
                        put(AthleteMetricsDbHelper.TIMESTAMP_MS, candidate.timestampMs)
                        put(AthleteMetricsDbHelper.ZONE_TYPE, candidate.zoneType.name)
                        put(AthleteMetricsDbHelper.BOUNDARIES, ZoneSnapshotEntry.serializeBoundaries(candidate.boundaries))
                    }
                    db.insert(AthleteMetricsDbHelper.TABLE_ZONE_HISTORY, null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AthleteMetricsDatabaseManager? = null

        @JvmStatic
        fun getInstance(context: Context): AthleteMetricsDatabaseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AthleteMetricsDatabaseManager(context).also { INSTANCE = it }
            }
        }
    }
}
