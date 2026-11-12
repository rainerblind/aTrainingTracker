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
import android.location.Location
import android.provider.BaseColumns
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.climbs.ClimbDeduplicator
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLite helper for the persistent climbs database (REQ-MAP-027).
 */
class ClimbsDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "Climbs.db"
        const val DATABASE_VERSION = 1

        const val TABLE_CLIMBS = "climbs"
        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_NAME = "name"
        const val COLUMN_ROUTE_ID = "route_id"
        const val COLUMN_START_LAT = "start_lat"
        const val COLUMN_START_LNG = "start_lng"
        const val COLUMN_END_LAT = "end_lat"
        const val COLUMN_END_LNG = "end_lng"
        const val COLUMN_DISTANCE_M = "distance_m"
        const val COLUMN_ELEVATION_GAIN_M = "elevation_gain_m"
        const val COLUMN_AVG_GRADE = "avg_grade"
        const val COLUMN_MAX_GRADE = "max_grade"
        const val COLUMN_CATEGORY = "climb_category"
        const val COLUMN_PATH_POLYLINE = "path_polyline"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_CLIMBS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_ROUTE_ID INTEGER,
                $COLUMN_START_LAT REAL NOT NULL,
                $COLUMN_START_LNG REAL NOT NULL,
                $COLUMN_END_LAT REAL NOT NULL,
                $COLUMN_END_LNG REAL NOT NULL,
                $COLUMN_DISTANCE_M REAL NOT NULL,
                $COLUMN_ELEVATION_GAIN_M REAL NOT NULL,
                $COLUMN_AVG_GRADE REAL NOT NULL,
                $COLUMN_MAX_GRADE REAL NOT NULL,
                $COLUMN_CATEGORY TEXT NOT NULL,
                $COLUMN_PATH_POLYLINE TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_climbs_start ON $TABLE_CLIMBS ($COLUMN_START_LAT, $COLUMN_START_LNG)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_climbs_route ON $TABLE_CLIMBS ($COLUMN_ROUTE_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Initial version 1; future migrations will increment here.
    }
}

/**
 * Manages persistence, querying, and spatial deduplication of climbs in SQLite (REQ-MAP-027).
 */
class ClimbsDatabaseManager(
    context: Context,
    private val dbDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val dbHelper: ClimbsDbHelper = ClimbsDbHelper(context)
) {

    /**
     * Direct database handle for connection quiescence, WAL checkpointing, and backups (REQ-MAP-066.2).
     */
    val database: SQLiteDatabase
        get() = dbHelper.writableDatabase

    /**
     * Inserts a climb into the database.
     */
    suspend fun insertClimb(climb: Climb): Long = withContext(dbDispatcher) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ClimbsDbHelper.COLUMN_NAME, climb.name)
            if (climb.routeId != null) {
                put(ClimbsDbHelper.COLUMN_ROUTE_ID, climb.routeId)
            } else {
                putNull(ClimbsDbHelper.COLUMN_ROUTE_ID)
            }
            put(ClimbsDbHelper.COLUMN_START_LAT, climb.startLat)
            put(ClimbsDbHelper.COLUMN_START_LNG, climb.startLng)
            put(ClimbsDbHelper.COLUMN_END_LAT, climb.endLat)
            put(ClimbsDbHelper.COLUMN_END_LNG, climb.endLng)
            put(ClimbsDbHelper.COLUMN_DISTANCE_M, climb.distanceMeters)
            put(ClimbsDbHelper.COLUMN_ELEVATION_GAIN_M, climb.elevationGainMeters)
            put(ClimbsDbHelper.COLUMN_AVG_GRADE, climb.avgGradePercent)
            put(ClimbsDbHelper.COLUMN_MAX_GRADE, climb.maxGradePercent)
            put(ClimbsDbHelper.COLUMN_CATEGORY, climb.category.name)
            put(ClimbsDbHelper.COLUMN_PATH_POLYLINE, serializePathPoints(climb.pathPoints))
        }
        db.insert(ClimbsDbHelper.TABLE_CLIMBS, null, values)
    }

    /**
     * Inserts a climb with spatial deduplication on start and summit coordinates or corridor overlap (REQ-CLM-011.1).
     * If an existing climb matches within [thresholdMeters] or shares >= 70% corridor overlap, returns existing ID.
     */
    suspend fun insertClimbWithDeduplication(
        climb: Climb,
        thresholdMeters: Float = 50.0f
    ): Long = withContext(dbDispatcher) {
        val existingClimbs = getAllClimbsInternal()
        for (cand in existingClimbs) {
            if (ClimbDeduplicator.isDuplicateOrOverlapping(climb, cand, thresholdMeters)) {
                if (climb.routeId != null && cand.routeId == null) {
                    updateClimb(cand.copy(routeId = climb.routeId))
                }
                return@withContext cand.id
            }
        }

        insertClimb(climb)
    }

    /**
     * Inserts a collection of climbs in a single atomic SQLite transaction with spatial deduplication (REQ-MAP-027, REQ-CLM-011.1).
     * Eliminates transaction overhead and SQLite lock contention on batch route imports and workout backfills.
     */
    suspend fun insertClimbsWithDeduplicationBatch(
        climbs: List<Climb>,
        thresholdMeters: Float = 50.0f
    ): List<Long> = withContext(dbDispatcher) {
        if (climbs.isEmpty()) return@withContext emptyList()
        val db = dbHelper.writableDatabase
        val existingClimbs = getAllClimbsInternal().toMutableList()
        val resultIds = mutableListOf<Long>()

        db.beginTransaction()
        try {
            for (climb in climbs) {
                var matchedCand: Climb? = null
                for (cand in existingClimbs) {
                    if (ClimbDeduplicator.isDuplicateOrOverlapping(climb, cand, thresholdMeters)) {
                        matchedCand = cand
                        break
                    }
                }
                if (matchedCand != null) {
                    if (climb.routeId != null && matchedCand.routeId == null) {
                        val values = ContentValues().apply {
                            put(ClimbsDbHelper.COLUMN_ROUTE_ID, climb.routeId)
                        }
                        db.update(
                            ClimbsDbHelper.TABLE_CLIMBS,
                            values,
                            "${ClimbsDbHelper.COLUMN_ID} = ?",
                            arrayOf(matchedCand.id.toString())
                        )
                        val idx = existingClimbs.indexOfFirst { it.id == matchedCand.id }
                        if (idx != -1) {
                            existingClimbs[idx] = matchedCand.copy(routeId = climb.routeId)
                        }
                    }
                    resultIds.add(matchedCand.id)
                } else {
                    val values = ContentValues().apply {
                        put(ClimbsDbHelper.COLUMN_NAME, climb.name)
                        if (climb.routeId != null) {
                            put(ClimbsDbHelper.COLUMN_ROUTE_ID, climb.routeId)
                        } else {
                            putNull(ClimbsDbHelper.COLUMN_ROUTE_ID)
                        }
                        put(ClimbsDbHelper.COLUMN_START_LAT, climb.startLat)
                        put(ClimbsDbHelper.COLUMN_START_LNG, climb.startLng)
                        put(ClimbsDbHelper.COLUMN_END_LAT, climb.endLat)
                        put(ClimbsDbHelper.COLUMN_END_LNG, climb.endLng)
                        put(ClimbsDbHelper.COLUMN_DISTANCE_M, climb.distanceMeters)
                        put(ClimbsDbHelper.COLUMN_ELEVATION_GAIN_M, climb.elevationGainMeters)
                        put(ClimbsDbHelper.COLUMN_AVG_GRADE, climb.avgGradePercent)
                        put(ClimbsDbHelper.COLUMN_MAX_GRADE, climb.maxGradePercent)
                        put(ClimbsDbHelper.COLUMN_CATEGORY, climb.category.name)
                        put(ClimbsDbHelper.COLUMN_PATH_POLYLINE, serializePathPoints(climb.pathPoints))
                    }
                    val newId = db.insert(ClimbsDbHelper.TABLE_CLIMBS, null, values)
                    resultIds.add(newId)
                    existingClimbs.add(climb.copy(id = newId))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        resultIds
    }

    /**
     * Updates an existing climb in SQLite (REQ-CLM-011.1).
     */
    suspend fun updateClimb(climb: Climb): Int = withContext(dbDispatcher) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(ClimbsDbHelper.COLUMN_NAME, climb.name)
            if (climb.routeId != null) {
                put(ClimbsDbHelper.COLUMN_ROUTE_ID, climb.routeId)
            } else {
                putNull(ClimbsDbHelper.COLUMN_ROUTE_ID)
            }
            put(ClimbsDbHelper.COLUMN_START_LAT, climb.startLat)
            put(ClimbsDbHelper.COLUMN_START_LNG, climb.startLng)
            put(ClimbsDbHelper.COLUMN_END_LAT, climb.endLat)
            put(ClimbsDbHelper.COLUMN_END_LNG, climb.endLng)
            put(ClimbsDbHelper.COLUMN_DISTANCE_M, climb.distanceMeters)
            put(ClimbsDbHelper.COLUMN_ELEVATION_GAIN_M, climb.elevationGainMeters)
            put(ClimbsDbHelper.COLUMN_AVG_GRADE, climb.avgGradePercent)
            put(ClimbsDbHelper.COLUMN_MAX_GRADE, climb.maxGradePercent)
            put(ClimbsDbHelper.COLUMN_CATEGORY, climb.category.name)
            put(ClimbsDbHelper.COLUMN_PATH_POLYLINE, serializePathPoints(climb.pathPoints))
        }
        db.update(ClimbsDbHelper.TABLE_CLIMBS, values, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf(climb.id.toString()))
    }

    /**
     * Scans all stored climbs in SQLite, identifies duplicate or overlapping climbs,
     * consolidates metadata (preserving route associations), and deletes redundant rows
     * in a single atomic transaction (REQ-CLM-011.2).
     *
     * @return Number of redundant duplicate climbs purged from the database.
     */
    suspend fun deduplicateExistingClimbs(thresholdMeters: Float = 100.0f): Int = withContext(dbDispatcher) {
        val allClimbs = getAllClimbsInternal()
        if (allClimbs.size < 2) return@withContext 0

        val db = dbHelper.writableDatabase
        val idsToDelete = mutableSetOf<Long>()
        val retainedClimbs = mutableListOf<Climb>()

        for (cand in allClimbs) {
            if (cand.id in idsToDelete) continue
            var duplicateFound: Climb? = null
            for (retained in retainedClimbs) {
                if (ClimbDeduplicator.isDuplicateOrOverlapping(cand, retained, thresholdMeters)) {
                    duplicateFound = retained
                    break
                }
            }

            if (duplicateFound != null) {
                // If candidate has routeId and retained does not, transfer routeId
                if (cand.routeId != null && duplicateFound.routeId == null) {
                    val updated = duplicateFound.copy(routeId = cand.routeId)
                    val values = ContentValues().apply {
                        put(ClimbsDbHelper.COLUMN_ROUTE_ID, cand.routeId)
                    }
                    db.update(
                        ClimbsDbHelper.TABLE_CLIMBS,
                        values,
                        "${ClimbsDbHelper.COLUMN_ID} = ?",
                        arrayOf(duplicateFound.id.toString())
                    )
                    val idx = retainedClimbs.indexOfFirst { it.id == duplicateFound.id }
                    if (idx != -1) {
                        retainedClimbs[idx] = updated
                    }
                }
                idsToDelete.add(cand.id)
            } else {
                retainedClimbs.add(cand)
            }
        }

        if (idsToDelete.isEmpty()) return@withContext 0

        db.beginTransaction()
        try {
            for (id in idsToDelete) {
                db.delete(ClimbsDbHelper.TABLE_CLIMBS, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        idsToDelete.size
    }

    /**
     * Retrieves all stored climbs.
     */
    suspend fun getAllClimbs(): List<Climb> = withContext(dbDispatcher) {
        getAllClimbsInternal()
    }

    /**
     * Retrieves climbs belonging to a specific route.
     */
    suspend fun getClimbsForRoute(routeId: Long): List<Climb> = withContext(dbDispatcher) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Climb>()
        val cursor = db.query(
            ClimbsDbHelper.TABLE_CLIMBS,
            null,
            "${ClimbsDbHelper.COLUMN_ROUTE_ID} = ?",
            arrayOf(routeId.toString()),
            null,
            null,
            "${ClimbsDbHelper.COLUMN_ID} ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToClimb(it))
            }
        }
        list
    }

    /**
     * Retrieves a single climb by its unique ID.
     */
    suspend fun getClimbById(id: Long): Climb? = withContext(dbDispatcher) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ClimbsDbHelper.TABLE_CLIMBS,
            null,
            "${ClimbsDbHelper.COLUMN_ID} = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                cursorToClimb(it)
            } else {
                null
            }
        }
    }

    /**
     * Deletes a climb by ID.
     */
    suspend fun deleteClimb(id: Long): Int = withContext(dbDispatcher) {
        val db = dbHelper.writableDatabase
        db.delete(ClimbsDbHelper.TABLE_CLIMBS, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf(id.toString()))
    }

    /**
     * Deletes all climbs.
     */
    suspend fun deleteAll(): Int = withContext(dbDispatcher) {
        val db = dbHelper.writableDatabase
        db.delete(ClimbsDbHelper.TABLE_CLIMBS, null, null)
    }

    fun close() {
        dbHelper.close()
    }

    private fun getAllClimbsInternal(): List<Climb> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Climb>()
        val cursor = db.query(
            ClimbsDbHelper.TABLE_CLIMBS,
            null,
            null,
            null,
            null,
            null,
            "${ClimbsDbHelper.COLUMN_ID} ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToClimb(it))
            }
        }
        return list
    }

    private fun cursorToClimb(cursor: Cursor): Climb {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ID))
        val name = cursor.getString(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_NAME))
        val routeIdIdx = cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ROUTE_ID)
        val routeId = if (cursor.isNull(routeIdIdx)) null else cursor.getLong(routeIdIdx)
        val startLat = cursor.getDouble(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_START_LAT))
        val startLng = cursor.getDouble(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_START_LNG))
        val endLat = cursor.getDouble(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_END_LAT))
        val endLng = cursor.getDouble(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_END_LNG))
        val dist = cursor.getDouble(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_DISTANCE_M))
        val gain = cursor.getDouble(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ELEVATION_GAIN_M))
        val avgGrade = cursor.getDouble(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_AVG_GRADE))
        val maxGrade = cursor.getDouble(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_MAX_GRADE))
        val catStr = cursor.getString(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_CATEGORY))
        val pathPoly = cursor.getString(cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_PATH_POLYLINE))

        return Climb(
            id = id,
            name = name,
            routeId = routeId,
            startLat = startLat,
            startLng = startLng,
            endLat = endLat,
            endLng = endLng,
            distanceMeters = dist,
            elevationGainMeters = gain,
            avgGradePercent = avgGrade,
            maxGradePercent = maxGrade,
            category = ClimbCategory.fromCode(catStr),
            pathPoints = deserializePathPoints(pathPoly)
        )
    }

    companion object {
        fun serializePathPoints(points: List<PathPoint>): String {
            if (points.isEmpty()) return ""
            return points.joinToString(";") {
                "${it.latLng.latitude},${it.latLng.longitude},${it.altitude},${it.distance}"
            }
        }

        fun deserializePathPoints(str: String): List<PathPoint> {
            if (str.isBlank()) return emptyList()
            val items = str.split(";")
            return items.mapNotNull { item ->
                val tokens = item.split(",")
                if (tokens.size >= 4) {
                    val lat = tokens[0].toDoubleOrNull()
                    val lng = tokens[1].toDoubleOrNull()
                    val alt = tokens[2].toDoubleOrNull()
                    val dist = tokens[3].toDoubleOrNull()
                    if (lat != null && lng != null && dist != null) {
                        PathPoint(distance = dist, latLng = LatLng(lat, lng), altitude = alt)
                    } else null
                } else null
            }
        }

        @Volatile
        private var instance: ClimbsDatabaseManager? = null

        fun getInstance(context: Context): ClimbsDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: ClimbsDatabaseManager(context.applicationContext).also { instance = it }
            }
        }

        fun resetForTesting(newInstance: ClimbsDatabaseManager? = null) {
            runCatching { instance?.close() }
            instance = newInstance
        }
    }
}
