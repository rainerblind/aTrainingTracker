/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import com.atrainingtracker.banalservice.BSportType

/**
 * Manages the persistent storage for period-level summaries using a normalized relational schema.
 * This implementation acts as a high-speed cache with a completion flag and progressive loading support.
 */
class PeriodSummariesDatabaseManager private constructor(context: Context) {

    private val dbHelper = PeriodSummariesDbHelper(context)
    private var mDatabase: SQLiteDatabase? = null

    companion object {
        private const val TAG = "PeriodSummariesDB"
        @Volatile
        private var instance: PeriodSummariesDatabaseManager? = null

        @JvmStatic
        fun getInstance(context: Context): PeriodSummariesDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: PeriodSummariesDatabaseManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        // ATT-346: Cleanup of experimental databases from previous iterations to ensure a clean start.
        listOf("PeriodSummaries_v4.db", "PeriodSummaries_v4.db-shm", "PeriodSummaries_v4.db-wal",
               "PeriodSummaries_v5.db", "PeriodSummaries_v5.db-shm", "PeriodSummaries_v5.db-wal",
               "PeriodSummaries_v10.db", "PeriodSummaries_v10.db-shm", "PeriodSummaries_v10.db-wal",
               "PeriodSummaries_v11.db", "PeriodSummaries_v11.db-shm", "PeriodSummaries_v11.db-wal",
               "PeriodSummaries_v12.db", "PeriodSummaries_v12.db-shm", "PeriodSummaries_v12.db-wal",
               "PeriodSummaries_v13.db", "PeriodSummaries_v13.db-shm", "PeriodSummaries_v13.db-wal").forEach {
            val file = context.getDatabasePath(it)
            if (file.exists()) {
                Log.i(TAG, "Cleaning up experimental database file: $it")
                context.deleteDatabase(it)
            }
        }
    }

    fun getDatabase(): SQLiteDatabase {
        val db = mDatabase
        if (db != null && db.isOpen) return db
        return synchronized(this) {
            val db2 = mDatabase
            if (db2 != null && db2.isOpen) db2
            else {
                if (mDatabase != null) dbHelper.close()
                dbHelper.writableDatabase.also { mDatabase = it }
            }
        }
    }

    fun runInTransaction(block: (SQLiteDatabase) -> Unit) {
        val db = getDatabase()
        db.beginTransaction()
        try {
            block(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteAll(db: SQLiteDatabase) {
        db.delete(DetailedStatsContract.TABLE_NAME, null, null)
        db.delete(SportStatsContract.TABLE_NAME, null, null)
        db.delete(PeriodSummariesContract.TABLE_NAME, null, null)
    }

    fun isSyncFinished(): Boolean {
        val db = getDatabase()
        try {
            db.query(SyncStatusContract.TABLE_NAME, null, null, null, null, null, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getInt(cursor.getColumnIndexOrThrow(SyncStatusContract.COLUMN_IS_FINISHED)) == 1
                }
            }
        } catch (e: Exception) {
            // Table might not exist yet
        }
        return false
    }

    fun setSyncFinished(db: SQLiteDatabase, finished: Boolean) {
        val values = ContentValues().apply {
            put(SyncStatusContract.COLUMN_IS_FINISHED, if (finished) 1 else 0)
        }
        if (db.update(SyncStatusContract.TABLE_NAME, values, "${BaseColumns._ID} = 1", null) == 0) {
            values.put(BaseColumns._ID, 1)
            db.insert(SyncStatusContract.TABLE_NAME, null, values)
        }
    }

    /**
     * Optimized batch fetching of periods.
     * Reduces O(N) queries to O(1) by fetching all related stats in one go.
     */
    fun getPeriodsByType(type: PeriodType, limit: Int = -1, offset: Int = 0): List<PeriodSummary> {
        val db = getDatabase()
        val selection = "${PeriodSummariesContract.COLUMN_PERIOD_TYPE} = ?"
        val selectionArgs = arrayOf(type.name)
        val limitStr = if (limit > 0) "$offset, $limit" else null

        val periods = mutableListOf<PeriodSummary>()
        val idToSummaryMap = mutableMapOf<Long, PeriodSummary>()

        db.query(
            PeriodSummariesContract.TABLE_NAME, PeriodSummariesContract.PROJECTION, 
            selection, selectionArgs, null, null,
            "${PeriodSummariesContract.COLUMN_SORT_KEY} DESC", limitStr
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val periodId = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID))
                val summary = mapCursorToPeriod(cursor, mutableMapOf())
                periods.add(summary)
                idToSummaryMap[periodId] = summary
            }
        }

        if (idToSummaryMap.isEmpty()) return emptyList()

        // BATCH FETCH Sport Stats
        val periodIds = idToSummaryMap.keys.joinToString(",")
        val sportStatsSelection = "${SportStatsContract.COLUMN_PERIOD_ID} IN ($periodIds)"
        val idToSportStatsMap = mutableMapOf<Long, MutableMap<BSportType, SportStats>>()

        db.query(SportStatsContract.TABLE_NAME, null, sportStatsSelection, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val pId = cursor.getLong(cursor.getColumnIndexOrThrow(SportStatsContract.COLUMN_PERIOD_ID))
                val sId = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID))
                val bType = BSportType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(SportStatsContract.COLUMN_BSPORT_TYPE)))
                
                val stats = mapCursorToSportStats(cursor, mutableMapOf())
                idToSportStatsMap.getOrPut(pId) { mutableMapOf() }[bType] = stats
                
                // Temporary store statsId in longestWorkout's distance field to link detailed stats
                stats.longestWorkout = LongestWorkout(sId, "", 0, 0.0, 0)
            }
        }

        // BATCH FETCH Detailed Stats
        val statsIds = idToSportStatsMap.values.flatMap { it.values }.map { it.longestWorkout?.id }.filterNotNull().joinToString(",")
        if (statsIds.isNotEmpty()) {
            val detailedSelection = "${DetailedStatsContract.COLUMN_SPORT_STATS_ID} IN ($statsIds)"
            db.query(DetailedStatsContract.TABLE_NAME, null, detailedSelection, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val sId = cursor.getLong(cursor.getColumnIndexOrThrow(DetailedStatsContract.COLUMN_SPORT_STATS_ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(DetailedStatsContract.COLUMN_SPORT_NAME))
                    val detailed = mapCursorToDetailedStats(cursor)
                    
                    idToSportStatsMap.values.forEach { sportMap ->
                        sportMap.values.find { it.longestWorkout?.id == sId }?.let {
                            (it.detailedSportStats as MutableMap)[name] = detailed
                        }
                    }
                }
            }
        }

        // Final Join and Restore Longest IDs
        return periods.map { p ->
            val pId = idToSummaryMap.entries.find { it.value === p }?.key ?: -1L
            val stats = idToSportStatsMap[pId] ?: emptyMap()
            
            stats.values.forEach { s ->
                val tempId = s.longestWorkout?.id ?: -1L
                val longestIdFromDb = db.query(SportStatsContract.TABLE_NAME, arrayOf(SportStatsContract.COLUMN_LONGEST_WORKOUT_ID), 
                    "${BaseColumns._ID} = ?", arrayOf(tempId.toString()), null, null, null).use { c ->
                    if (c.moveToFirst()) c.getLong(0) else -1L
                }
                s.longestWorkout = if (longestIdFromDb != -1L) LongestWorkout(longestIdFromDb, "", 0, 0.0, 0) else null
            }

            p.copy(sportStats = stats)
        }
    }

    fun getPeriodSummary(db: SQLiteDatabase, type: PeriodType, startTimestampS: Long): PeriodSummary? {
        val selection = "${PeriodSummariesContract.COLUMN_PERIOD_TYPE} = ? AND ${PeriodSummariesContract.COLUMN_START_TIMESTAMP} = ?"
        val selectionArgs = arrayOf(type.name, startTimestampS.toString())

        db.query(PeriodSummariesContract.TABLE_NAME, PeriodSummariesContract.PROJECTION, selection, selectionArgs, null, null, null).use { cursor ->
            if (cursor.moveToFirst()) {
                val periodId = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID))
                return mapCursorToPeriod(cursor, getSportStatsForPeriod(db, periodId))
            }
        }
        return null
    }

    private fun getSportStatsForPeriod(db: SQLiteDatabase, periodId: Long): Map<BSportType, SportStats> {
        val statsMap = mutableMapOf<BSportType, SportStats>()
        val selection = "${SportStatsContract.COLUMN_PERIOD_ID} = ?"
        db.query(SportStatsContract.TABLE_NAME, null, selection, arrayOf(periodId.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val statsId = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID))
                val type = BSportType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(SportStatsContract.COLUMN_BSPORT_TYPE)))
                val longestId = cursor.getLong(cursor.getColumnIndexOrThrow(SportStatsContract.COLUMN_LONGEST_WORKOUT_ID))
                statsMap[type] = mapCursorToSportStats(cursor, getDetailedStatsForSport(db, statsId)).apply {
                    longestWorkout = if (longestId != -1L) LongestWorkout(longestId, "", 0, 0.0, 0) else null
                }
            }
        }
        return statsMap
    }

    private fun getDetailedStatsForSport(db: SQLiteDatabase, sportStatsId: Long): Map<String, DetailedStats> {
        val detailedMap = mutableMapOf<String, DetailedStats>()
        db.query(DetailedStatsContract.TABLE_NAME, null, "${DetailedStatsContract.COLUMN_SPORT_STATS_ID} = ?", arrayOf(sportStatsId.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(DetailedStatsContract.COLUMN_SPORT_NAME))
                detailedMap[name] = mapCursorToDetailedStats(cursor)
            }
        }
        return detailedMap
    }

    fun upsertPeriod(db: SQLiteDatabase, summary: PeriodSummary) {
        val periodValues = createPeriodContentValues(summary)
        val where = "${PeriodSummariesContract.COLUMN_PERIOD_TYPE} = ? AND ${PeriodSummariesContract.COLUMN_START_TIMESTAMP} = ?"
        val args = arrayOf(summary.periodType.name, summary.startTimestampS.toString())
        var periodId: Long
        if (db.update(PeriodSummariesContract.TABLE_NAME, periodValues, where, args) == 0) {
            periodId = db.insert(PeriodSummariesContract.TABLE_NAME, null, periodValues)
        } else {
            db.query(PeriodSummariesContract.TABLE_NAME, arrayOf(BaseColumns._ID), where, args, null, null, null).use { c ->
                c.moveToFirst(); periodId = c.getLong(0)
            }
            db.delete(SportStatsContract.TABLE_NAME, "${SportStatsContract.COLUMN_PERIOD_ID} = ?", arrayOf(periodId.toString()))
        }
        summary.sportStats.forEach { (type, stats) ->
            val sportStatsId = db.insert(SportStatsContract.TABLE_NAME, null, createSportStatsContentValues(periodId, type, stats))
            stats.detailedSportStats.forEach { (name, detailed) ->
                db.insert(DetailedStatsContract.TABLE_NAME, null, createDetailedStatsContentValues(sportStatsId, name, detailed))
            }
        }
    }

    private fun createPeriodContentValues(p: PeriodSummary) = ContentValues().apply {
        put(PeriodSummariesContract.COLUMN_PERIOD_TYPE, p.periodType.name)
        put(PeriodSummariesContract.COLUMN_START_TIMESTAMP, p.startTimestampS); put(PeriodSummariesContract.COLUMN_END_TIMESTAMP, p.endTimestampS)
        put(PeriodSummariesContract.COLUMN_LABEL, p.periodLabel); put(PeriodSummariesContract.COLUMN_DATE_RANGE, p.periodDateRange)
        put(PeriodSummariesContract.COLUMN_TOTAL_WORKOUTS, p.totalWorkouts); put(PeriodSummariesContract.COLUMN_TOTAL_DURATION, p.totalDurationSec)
        put(PeriodSummariesContract.COLUMN_TOTAL_DISTANCE, p.totalDistance); put(PeriodSummariesContract.COLUMN_SORT_KEY, p.sortKey)
        put(PeriodSummariesContract.COLUMN_BOUND_MIN_LAT, p.minLat); put(PeriodSummariesContract.COLUMN_BOUND_MIN_LNG, p.minLng)
        put(PeriodSummariesContract.COLUMN_BOUND_MAX_LAT, p.maxLat); put(PeriodSummariesContract.COLUMN_BOUND_MAX_LNG, p.maxLng)
        put(PeriodSummariesContract.COLUMN_LONGEST_WORKOUT_ID, p.longestId); put(PeriodSummariesContract.COLUMN_LONGEST_DURATION, p.longestDurationS)
        put(PeriodSummariesContract.COLUMN_NORTH_WORKOUT_ID, p.northId); put(PeriodSummariesContract.COLUMN_SOUTH_WORKOUT_ID, p.southId)
        put(PeriodSummariesContract.COLUMN_EAST_WORKOUT_ID, p.eastId); put(PeriodSummariesContract.COLUMN_WEST_WORKOUT_ID, p.westId)
    }

    private fun createSportStatsContentValues(periodId: Long, type: BSportType, s: SportStats) = ContentValues().apply {
        put(SportStatsContract.COLUMN_PERIOD_ID, periodId); put(SportStatsContract.COLUMN_BSPORT_TYPE, type.name)
        put(SportStatsContract.COLUMN_COUNT, s.count); put(SportStatsContract.COLUMN_TOTAL_DURATION, s.totalDurationSec)
        put(SportStatsContract.COLUMN_TOTAL_DISTANCE, s.totalDistanceMeters); put(SportStatsContract.COLUMN_TOTAL_ASCENT, s.totalAscentMeters)
        put(SportStatsContract.COLUMN_LONGEST_WORKOUT_ID, s.longestWorkout?.id ?: -1L)
    }

    private fun createDetailedStatsContentValues(statsId: Long, name: String, s: DetailedStats) = ContentValues().apply {
        put(DetailedStatsContract.COLUMN_SPORT_STATS_ID, statsId); put(DetailedStatsContract.COLUMN_SPORT_NAME, name)
        put(DetailedStatsContract.COLUMN_COUNT, s.count); put(DetailedStatsContract.COLUMN_TOTAL_DURATION, s.totalDurationSec)
        put(DetailedStatsContract.COLUMN_TOTAL_DISTANCE, s.totalDistanceMeters); put(DetailedStatsContract.COLUMN_TOTAL_ASCENT, s.totalAscentMeters)
    }

    private fun mapCursorToPeriod(cursor: Cursor, sportStats: Map<BSportType, SportStats>): PeriodSummary {
        return PeriodSummary(
            periodLabel = cursor.getString(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_LABEL)),
            periodDateRange = cursor.getString(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_DATE_RANGE)),
            periodType = PeriodType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_PERIOD_TYPE))),
            startTimestampS = cursor.getLong(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_START_TIMESTAMP)),
            endTimestampS = cursor.getLong(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_END_TIMESTAMP)),
            totalWorkouts = cursor.getInt(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_TOTAL_WORKOUTS)),
            totalDurationSec = cursor.getLong(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_TOTAL_DURATION)),
            totalDistance = cursor.getDouble(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_TOTAL_DISTANCE)),
            sortKey = cursor.getString(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_SORT_KEY)),
            sportStats = sportStats,
            minLat = cursor.getDouble(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_BOUND_MIN_LAT)),
            minLng = cursor.getDouble(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_BOUND_MIN_LNG)),
            maxLat = cursor.getDouble(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_BOUND_MAX_LAT)),
            maxLng = cursor.getDouble(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_BOUND_MAX_LNG)),
            longestId = cursor.getLong(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_LONGEST_WORKOUT_ID)),
            longestDurationS = cursor.getLong(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_LONGEST_DURATION)),
            northId = cursor.getLong(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_NORTH_WORKOUT_ID)),
            southId = cursor.getLong(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_SOUTH_WORKOUT_ID)),
            eastId = cursor.getLong(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_EAST_WORKOUT_ID)),
            westId = cursor.getLong(cursor.getColumnIndexOrThrow(PeriodSummariesContract.COLUMN_WEST_WORKOUT_ID))
        )
    }

    private fun mapCursorToSportStats(cursor: Cursor, detailedStats: Map<String, DetailedStats>): SportStats {
        return SportStats(
            count = cursor.getInt(cursor.getColumnIndexOrThrow(SportStatsContract.COLUMN_COUNT)),
            totalDurationSec = cursor.getLong(cursor.getColumnIndexOrThrow(SportStatsContract.COLUMN_TOTAL_DURATION)),
            totalDistanceMeters = cursor.getDouble(cursor.getColumnIndexOrThrow(SportStatsContract.COLUMN_TOTAL_DISTANCE)),
            totalAscentMeters = cursor.getLong(cursor.getColumnIndexOrThrow(SportStatsContract.COLUMN_TOTAL_ASCENT)),
            detailedSportStats = detailedStats,
            longestWorkout = null
        )
    }

    private fun mapCursorToDetailedStats(cursor: Cursor) = DetailedStats(
        count = cursor.getInt(cursor.getColumnIndexOrThrow(DetailedStatsContract.COLUMN_COUNT)),
        totalDurationSec = cursor.getLong(cursor.getColumnIndexOrThrow(DetailedStatsContract.COLUMN_TOTAL_DURATION)),
        totalDistanceMeters = cursor.getDouble(cursor.getColumnIndexOrThrow(DetailedStatsContract.COLUMN_TOTAL_DISTANCE)),
        totalAscentMeters = cursor.getLong(cursor.getColumnIndexOrThrow(DetailedStatsContract.COLUMN_TOTAL_ASCENT))
    )

    object PeriodSummariesContract {
        const val TABLE_NAME = "PeriodSummaries"
        const val COLUMN_PERIOD_TYPE = "period_type"
        const val COLUMN_START_TIMESTAMP = "start_timestamp"
        const val COLUMN_END_TIMESTAMP = "end_timestamp"
        const val COLUMN_LABEL = "label"
        const val COLUMN_DATE_RANGE = "date_range"
        const val COLUMN_TOTAL_WORKOUTS = "total_workouts"
        const val COLUMN_TOTAL_DURATION = "total_duration"
        const val COLUMN_TOTAL_DISTANCE = "total_distance"
        const val COLUMN_SORT_KEY = "sort_key"
        const val COLUMN_BOUND_MIN_LAT = "bound_min_lat"; const val COLUMN_BOUND_MIN_LNG = "bound_min_lng"
        const val COLUMN_BOUND_MAX_LAT = "bound_max_lat"; const val COLUMN_BOUND_MAX_LNG = "bound_max_lng"
        const val COLUMN_LONGEST_WORKOUT_ID = "longest_workout_id"; const val COLUMN_LONGEST_DURATION = "longest_duration"
        const val COLUMN_NORTH_WORKOUT_ID = "north_workout_id"; const val COLUMN_SOUTH_WORKOUT_ID = "south_workout_id"
        const val COLUMN_EAST_WORKOUT_ID = "east_workout_id"; const val COLUMN_WEST_WORKOUT_ID = "west_workout_id"

        val PROJECTION = arrayOf(
            BaseColumns._ID, COLUMN_PERIOD_TYPE, COLUMN_START_TIMESTAMP, COLUMN_END_TIMESTAMP,
            COLUMN_LABEL, COLUMN_DATE_RANGE, COLUMN_TOTAL_WORKOUTS, COLUMN_TOTAL_DURATION,
            COLUMN_TOTAL_DISTANCE, COLUMN_SORT_KEY, COLUMN_BOUND_MIN_LAT, COLUMN_BOUND_MIN_LNG,
            COLUMN_BOUND_MAX_LAT, COLUMN_BOUND_MAX_LNG, COLUMN_LONGEST_WORKOUT_ID, COLUMN_LONGEST_DURATION,
            COLUMN_NORTH_WORKOUT_ID, COLUMN_SOUTH_WORKOUT_ID, COLUMN_EAST_WORKOUT_ID, COLUMN_WEST_WORKOUT_ID
        )

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PERIOD_TYPE TEXT, $COLUMN_START_TIMESTAMP INTEGER, $COLUMN_END_TIMESTAMP INTEGER,
                $COLUMN_LABEL TEXT, $COLUMN_DATE_RANGE TEXT, $COLUMN_TOTAL_WORKOUTS INTEGER,
                $COLUMN_TOTAL_DURATION INTEGER, $COLUMN_TOTAL_DISTANCE REAL, $COLUMN_SORT_KEY TEXT,
                $COLUMN_BOUND_MIN_LAT REAL, $COLUMN_BOUND_MIN_LNG REAL, $COLUMN_BOUND_MAX_LAT REAL,
                $COLUMN_BOUND_MAX_LNG REAL, $COLUMN_LONGEST_WORKOUT_ID INTEGER, $COLUMN_LONGEST_DURATION INTEGER,
                $COLUMN_NORTH_WORKOUT_ID INTEGER, $COLUMN_SOUTH_WORKOUT_ID INTEGER, $COLUMN_EAST_WORKOUT_ID INTEGER, $COLUMN_WEST_WORKOUT_ID INTEGER,
                UNIQUE($COLUMN_PERIOD_TYPE, $COLUMN_START_TIMESTAMP)
            )
        """
    }

    object SportStatsContract {
        const val TABLE_NAME = "PeriodSportStats"
        const val COLUMN_PERIOD_ID = "period_id"
        const val COLUMN_BSPORT_TYPE = "b_sport_type"
        const val COLUMN_COUNT = "count"
        const val COLUMN_TOTAL_DURATION = "total_duration"
        const val COLUMN_TOTAL_DISTANCE = "total_distance"
        const val COLUMN_TOTAL_ASCENT = "total_ascent"
        const val COLUMN_LONGEST_WORKOUT_ID = "longest_workout_id"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PERIOD_ID INTEGER, $COLUMN_BSPORT_TYPE TEXT, $COLUMN_COUNT INTEGER,
                $COLUMN_TOTAL_DURATION INTEGER, $COLUMN_TOTAL_DISTANCE REAL, $COLUMN_TOTAL_ASCENT INTEGER, $COLUMN_LONGEST_WORKOUT_ID INTEGER,
                FOREIGN KEY($COLUMN_PERIOD_ID) REFERENCES ${PeriodSummariesContract.TABLE_NAME}(${BaseColumns._ID}) ON DELETE CASCADE
            )
        """
    }

    object DetailedStatsContract {
        const val TABLE_NAME = "PeriodDetailedStats"
        const val COLUMN_SPORT_STATS_ID = "sport_stats_id"; const val COLUMN_SPORT_NAME = "sport_name"
        const val COLUMN_COUNT = "count"; const val COLUMN_TOTAL_DURATION = "total_duration"
        const val COLUMN_TOTAL_DISTANCE = "total_distance"; const val COLUMN_TOTAL_ASCENT = "total_ascent"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SPORT_STATS_ID INTEGER, $COLUMN_SPORT_NAME TEXT, $COLUMN_COUNT INTEGER,
                $COLUMN_TOTAL_DURATION INTEGER, $COLUMN_TOTAL_DISTANCE REAL, $COLUMN_TOTAL_ASCENT INTEGER,
                FOREIGN KEY($COLUMN_SPORT_STATS_ID) REFERENCES ${SportStatsContract.TABLE_NAME}(${BaseColumns._ID}) ON DELETE CASCADE
            )
        """
    }

    object SyncStatusContract {
        const val TABLE_NAME = "PeriodSyncStatus"
        const val COLUMN_IS_FINISHED = "is_finished"
        const val CREATE_TABLE = "CREATE TABLE $TABLE_NAME (${BaseColumns._ID} INTEGER PRIMARY KEY, $COLUMN_IS_FINISHED INTEGER DEFAULT 0)"
    }

    private class PeriodSummariesDbHelper(context: Context) : SQLiteOpenHelper(
        context, "PeriodSummaries.db", null, 14
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(PeriodSummariesContract.CREATE_TABLE)
            db.execSQL(SportStatsContract.CREATE_TABLE)
            db.execSQL(DetailedStatsContract.CREATE_TABLE)
            db.execSQL(SyncStatusContract.CREATE_TABLE)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Relational Restart for ATT-346 (v14: Performance Optimization)
            if (oldVersion < 14) {
                db.execSQL("DROP TABLE IF EXISTS ${SyncStatusContract.TABLE_NAME}")
                db.execSQL("DROP TABLE IF EXISTS ${DetailedStatsContract.TABLE_NAME}")
                db.execSQL("DROP TABLE IF EXISTS ${SportStatsContract.TABLE_NAME}")
                db.execSQL("DROP TABLE IF EXISTS ${PeriodSummariesContract.TABLE_NAME}")
                onCreate(db)
            }
        }
        override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    }
}
