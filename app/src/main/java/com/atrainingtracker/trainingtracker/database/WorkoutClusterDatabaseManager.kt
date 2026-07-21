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
    val routePolyline: String? = null // Encoded polyline of the linked authoritative route
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
     */
    fun findCandidates(startLat: Double, startLng: Double, distance: Double): List<WorkoutCluster> {
        val latTolerance = 0.002 // Approx 220m
        val distTolerance = 1000.0 // 500m either way

        val selection = "${WorkoutClusterContract.COLUMN_START_LAT} BETWEEN ? AND ? AND " +
                "${WorkoutClusterContract.COLUMN_REF_DISTANCE} BETWEEN ? AND ?"
        val args = arrayOf(
            (startLat - latTolerance).toString(), (startLat + latTolerance).toString(),
            (distance - distTolerance).toString(), (distance + distTolerance).toString()
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

    fun deleteAllClusters() {
        getDatabase().delete(WorkoutClusterContract.TABLE_NAME, null, null)
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
            bSportType = BSportType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_SPORT_TYPE)))
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
                $COLUMN_SPORT_TYPE TEXT
            )
        """
    }

    private class WorkoutClusterDbHelper(context: Context) : SQLiteOpenHelper(
        context, "RouteClusters.db", null, 2
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(WorkoutClusterContract.CREATE_TABLE)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE ${WorkoutClusterContract.TABLE_NAME} ADD COLUMN ${WorkoutClusterContract.COLUMN_SPORT_TYPE} TEXT DEFAULT 'UNKNOWN'")
            }
        }
    }
}
