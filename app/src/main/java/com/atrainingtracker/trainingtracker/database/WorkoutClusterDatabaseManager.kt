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
import com.atrainingtracker.banalservice.BSportType

data class WorkoutCluster(
    val id: Long = 0,
    val name: String,
    val probableSportId: Long,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val maxDispLat: Double,
    val maxDispLng: Double,
    val refDistance: Double,
    val hitCount: Int,
    val bSportType: BSportType = BSportType.UNKNOWN,
    val previewPaths: List<String> = emptyList(), // List of encoded polylines for preview
    val routePolyline: String? = null, // Encoded polyline of the linked authoritative route
    // Persisted spatial bounds and anchors (ATT-354)
    val minLat: Double? = null,
    val minLng: Double? = null,
    val maxLat: Double? = null,
    val maxLng: Double? = null
)

class WorkoutClusterDatabaseManager private constructor(context: Context) {

    private val dbHelper = WorkoutClusterDbHelper(context)
    private var mDatabase: SQLiteDatabase? = null

    companion object {
        @Volatile
        private var instance: WorkoutClusterDatabaseManager? = null

        @JvmStatic
        fun getInstance(context: Context): WorkoutClusterDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: WorkoutClusterDatabaseManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Returns a writable database instance and ensures it remains open.
     * Re-opens if closed (e.g., by a backup process) to prevent IllegalStateException (ATT-289).
     */
    fun getDatabase(): SQLiteDatabase {
        val db = mDatabase
        if (db != null && db.isOpen) {
            return db
        }
        return synchronized(this) {
            val db2 = mDatabase
            if (db2 != null && db2.isOpen) {
                db2
            } else {
                // If the database was closed, ensure the helper clears its reference
                if (mDatabase != null) {
                    dbHelper.close()
                }
                dbHelper.writableDatabase.also { mDatabase = it }
            }
        }
    }

    fun getAllClusters(): List<WorkoutCluster> {
        val clusters = mutableListOf<WorkoutCluster>()
        getDatabase().query(
            WorkoutClusterContract.TABLE_NAME, null, null, null, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                clusters.add(mapCursorToCluster(cursor))
            }
        }
        return clusters
    }

    /**
     * Finds candidate clusters based on rough spatial and distance filtering.
     * @param latToleranceDegrees The maximum latitude drift allowed for the start point.
     * @param distToleranceMeters The maximum distance drift allowed for the total length.
     */
    fun findCandidates(
        startLat: Double, 
        startLng: Double, 
        distance: Double,
        latToleranceDegrees: Double,
        distToleranceMeters: Double
    ): List<WorkoutCluster> {
        val selection = "${WorkoutClusterContract.COLUMN_START_LAT} BETWEEN ? AND ? AND " +
                "${WorkoutClusterContract.COLUMN_REF_DISTANCE} BETWEEN ? AND ?"
        val args = arrayOf(
            (startLat - latToleranceDegrees).toString(), (startLat + latToleranceDegrees).toString(),
            (distance - distToleranceMeters).toString(), (distance + distToleranceMeters).toString()
        )

        val candidates = mutableListOf<WorkoutCluster>()
        getDatabase().query(
            WorkoutClusterContract.TABLE_NAME, null, selection, args, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                candidates.add(mapCursorToCluster(cursor))
            }
        }
        return candidates
    }

    fun insertCluster(cluster: WorkoutCluster): Long {
        val values = createContentValues(cluster)
        return getDatabase().insert(WorkoutClusterContract.TABLE_NAME, null, values)
    }

    fun updateCluster(cluster: WorkoutCluster) {
        val values = createContentValues(cluster)
        getDatabase().update(
            WorkoutClusterContract.TABLE_NAME, values,
            "${BaseColumns._ID} = ?", arrayOf(cluster.id.toString())
        )
    }

    fun deleteCluster(id: Long) {
        getDatabase().delete(
            WorkoutClusterContract.TABLE_NAME,
            "${BaseColumns._ID} = ?",
            arrayOf(id.toString())
        )
    }

    fun getClusterById(id: Long): WorkoutCluster? {
        val selection = "${BaseColumns._ID} = ?"
        val args = arrayOf(id.toString())
        getDatabase().query(
            WorkoutClusterContract.TABLE_NAME, null, selection, args, null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return mapCursorToCluster(cursor)
            }
        }
        return null
    }

    /**
     * Fast lookup of a cluster name by ID (ATT-388).
     */
    fun getClusterNameById(id: Long): String? {
        if (id == -1L) return null
        val selection = "${BaseColumns._ID} = ?"
        val args = arrayOf(id.toString())
        getDatabase().query(
            WorkoutClusterContract.TABLE_NAME, arrayOf(WorkoutClusterContract.COLUMN_NAME), 
            selection, args, null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    fun deleteAllClusters() {
        getDatabase().delete(WorkoutClusterContract.TABLE_NAME, null, null)
    }

    /**
     * Fast lookup of cluster names for a batch of IDs (ATT-388).
     */
    fun getClusterNamesForIds(ids: Collection<Long>): Map<Long, String> {
        val results = mutableMapOf<Long, String>()
        if (ids.isEmpty()) return results

        val inClause = ids.joinToString(",")
        val selection = "${BaseColumns._ID} IN ($inClause)"
        
        getDatabase().query(
            WorkoutClusterContract.TABLE_NAME, 
            arrayOf(BaseColumns._ID, WorkoutClusterContract.COLUMN_NAME), 
            selection, null, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results[cursor.getLong(0)] = cursor.getString(1)
            }
        }
        return results
    }

    fun isNameTaken(name: String, excludeId: Long = -1): Boolean {
        var selection = "${WorkoutClusterContract.COLUMN_NAME} = ?"
        var args = arrayOf(name)
        
        if (excludeId != -1L) {
            selection += " AND ${BaseColumns._ID} != ?"
            args = arrayOf(name, excludeId.toString())
        }

        getDatabase().query(
            WorkoutClusterContract.TABLE_NAME, arrayOf(BaseColumns._ID), selection, args, null, null, null
        ).use { cursor ->
            return cursor.count > 0
        }
    }

    private fun mapCursorToCluster(cursor: Cursor): WorkoutCluster {
        val minLatIdx = cursor.getColumnIndex(WorkoutClusterContract.COLUMN_BOUND_MIN_LAT)
        val minLngIdx = cursor.getColumnIndex(WorkoutClusterContract.COLUMN_BOUND_MIN_LNG)
        val maxLatIdx = cursor.getColumnIndex(WorkoutClusterContract.COLUMN_BOUND_MAX_LAT)
        val maxLngIdx = cursor.getColumnIndex(WorkoutClusterContract.COLUMN_BOUND_MAX_LNG)

        return WorkoutCluster(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_NAME)),
            probableSportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_PROBABLE_SPORT_ID)),
            startLat = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_START_LAT)),
            startLng = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_START_LNG)),
            endLat = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_END_LAT)),
            endLng = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_END_LNG)),
            maxDispLat = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_MAX_DISP_LAT)),
            maxDispLng = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_MAX_DISP_LNG)),
            refDistance = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_REF_DISTANCE)),
            hitCount = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_HIT_COUNT)),
            bSportType = BSportType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_SPORT_TYPE))),
            minLat = if (minLatIdx != -1 && !cursor.isNull(minLatIdx)) cursor.getDouble(minLatIdx) else null,
            minLng = if (minLngIdx != -1 && !cursor.isNull(minLngIdx)) cursor.getDouble(minLngIdx) else null,
            maxLat = if (maxLatIdx != -1 && !cursor.isNull(maxLatIdx)) cursor.getDouble(maxLatIdx) else null,
            maxLng = if (maxLngIdx != -1 && !cursor.isNull(maxLngIdx)) cursor.getDouble(maxLngIdx) else null
        )
    }

    private fun createContentValues(cluster: WorkoutCluster) = ContentValues().apply {
        put(WorkoutClusterContract.COLUMN_NAME, cluster.name)
        put(WorkoutClusterContract.COLUMN_PROBABLE_SPORT_ID, cluster.probableSportId)
        put(WorkoutClusterContract.COLUMN_START_LAT, cluster.startLat)
        put(WorkoutClusterContract.COLUMN_START_LNG, cluster.startLng)
        put(WorkoutClusterContract.COLUMN_END_LAT, cluster.endLat)
        put(WorkoutClusterContract.COLUMN_END_LNG, cluster.endLng)
        put(WorkoutClusterContract.COLUMN_MAX_DISP_LAT, cluster.maxDispLat)
        put(WorkoutClusterContract.COLUMN_MAX_DISP_LNG, cluster.maxDispLng)
        put(WorkoutClusterContract.COLUMN_REF_DISTANCE, cluster.refDistance)
        put(WorkoutClusterContract.COLUMN_HIT_COUNT, cluster.hitCount)
        put(WorkoutClusterContract.COLUMN_SPORT_TYPE, cluster.bSportType.name)
        // ATT-354
        put(WorkoutClusterContract.COLUMN_BOUND_MIN_LAT, cluster.minLat)
        put(WorkoutClusterContract.COLUMN_BOUND_MIN_LNG, cluster.minLng)
        put(WorkoutClusterContract.COLUMN_BOUND_MAX_LAT, cluster.maxLat)
        put(WorkoutClusterContract.COLUMN_BOUND_MAX_LNG, cluster.maxLng)
    }

    object WorkoutClusterContract {
        const val TABLE_NAME = "RouteClusters" // Keep existing table name to avoid migration for now
        const val COLUMN_NAME = "name"
        const val COLUMN_PROBABLE_SPORT_ID = "probable_sport_id"
        const val COLUMN_START_LAT = "start_lat"
        const val COLUMN_START_LNG = "start_lng"
        const val COLUMN_END_LAT = "end_lat"
        const val COLUMN_END_LNG = "end_lng"
        const val COLUMN_MAX_DISP_LAT = "max_disp_lat"
        const val COLUMN_MAX_DISP_LNG = "max_disp_lng"
        const val COLUMN_REF_DISTANCE = "ref_distance"
        const val COLUMN_HIT_COUNT = "hit_count"
        const val COLUMN_SPORT_TYPE = "b_sport_type"
        const val COLUMN_BOUND_MIN_LAT = "bound_min_lat"
        const val COLUMN_BOUND_MIN_LNG = "bound_min_lng"
        const val COLUMN_BOUND_MAX_LAT = "bound_max_lat"
        const val COLUMN_BOUND_MAX_LNG = "bound_max_lng"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT,
                $COLUMN_PROBABLE_SPORT_ID INTEGER,
                $COLUMN_START_LAT REAL,
                $COLUMN_START_LNG REAL,
                $COLUMN_END_LAT REAL,
                $COLUMN_END_LNG REAL,
                $COLUMN_MAX_DISP_LAT REAL,
                $COLUMN_MAX_DISP_LNG REAL,
                $COLUMN_REF_DISTANCE REAL,
                $COLUMN_HIT_COUNT INTEGER,
                $COLUMN_SPORT_TYPE TEXT,
                $COLUMN_BOUND_MIN_LAT REAL,
                $COLUMN_BOUND_MIN_LNG REAL,
                $COLUMN_BOUND_MAX_LAT REAL,
                $COLUMN_BOUND_MAX_LNG REAL
            )
        """
    }

    private class WorkoutClusterDbHelper(context: Context) : SQLiteOpenHelper(
        context, "RouteClusters.db", null, 7
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(WorkoutClusterContract.CREATE_TABLE)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Relational Integrity Migration (v7: Ensuring consistent schema after v4 drop)
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_SPORT_TYPE} TEXT DEFAULT 'UNKNOWN'")
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_BOUND_MIN_LAT} REAL")
                db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_BOUND_MIN_LNG} REAL")
                db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_BOUND_MAX_LAT} REAL")
                db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_BOUND_MAX_LNG} REAL")
            }
            // v4 was destructive (dropped table). 
            // v5/v6 tried to reconstruct.
            // v7 ensures all columns exist and triggers a repository-led re-aggregation if empty.
            if (oldVersion < 7) {
                // Final safety check: ensure all spatial columns exist without dropping
                try { db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_BOUND_MIN_LAT} REAL") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_BOUND_MIN_LNG} REAL") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_BOUND_MAX_LAT} REAL") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_BOUND_MAX_LNG} REAL") } catch (e: Exception) {}
            }
        }
    }
}
