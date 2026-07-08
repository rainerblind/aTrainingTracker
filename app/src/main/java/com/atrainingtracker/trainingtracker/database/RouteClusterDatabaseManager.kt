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
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication

data class RouteCluster(
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
    val hitCount: Int
)

class RouteClusterDatabaseManager private constructor(context: Context) {

    private val dbHelper = RouteClusterDbHelper(context)

    companion object {
        private const val TAG = "RouteClusterDbManager"
        private val DEBUG = TrainingApplication.getDebug(true)

        @Volatile
        private var instance: RouteClusterDatabaseManager? = null

        fun getInstance(context: Context): RouteClusterDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: RouteClusterDatabaseManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getAllClusters(): List<RouteCluster> {
        val clusters = mutableListOf<RouteCluster>()
        dbHelper.readableDatabase.query(
            RouteClusterContract.TABLE_NAME, null, null, null, null, null, null
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
    fun findCandidates(startLat: Double, startLng: Double, distance: Double): List<RouteCluster> {
        val latTolerance = 0.002 // Approx 220m
        val distTolerance = 1000.0 // 500m either way

        val selection = "${RouteClusterContract.COLUMN_START_LAT} BETWEEN ? AND ? AND " +
                "${RouteClusterContract.COLUMN_REF_DISTANCE} BETWEEN ? AND ?"
        val args = arrayOf(
            (startLat - latTolerance).toString(), (startLat + latTolerance).toString(),
            (distance - distTolerance).toString(), (distance + distTolerance).toString()
        )

        val candidates = mutableListOf<RouteCluster>()
        dbHelper.readableDatabase.query(
            RouteClusterContract.TABLE_NAME, null, selection, args, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                candidates.add(mapCursorToCluster(cursor))
            }
        }
        return candidates
    }

    fun insertCluster(cluster: RouteCluster): Long {
        val values = createContentValues(cluster)
        return dbHelper.writableDatabase.insert(RouteClusterContract.TABLE_NAME, null, values)
    }

    fun updateCluster(cluster: RouteCluster) {
        val values = createContentValues(cluster)
        dbHelper.writableDatabase.update(
            RouteClusterContract.TABLE_NAME, values,
            "${BaseColumns._ID} = ?", arrayOf(cluster.id.toString())
        )
    }

    fun deleteAllClusters() {
        dbHelper.writableDatabase.delete(RouteClusterContract.TABLE_NAME, null, null)
    }

    fun isNameTaken(name: String, excludeId: Long = -1): Boolean {
        var selection = "${RouteClusterContract.COLUMN_NAME} = ?"
        var args = arrayOf(name)
        
        if (excludeId != -1L) {
            selection += " AND ${BaseColumns._ID} != ?"
            args = arrayOf(name, excludeId.toString())
        }

        dbHelper.readableDatabase.query(
            RouteClusterContract.TABLE_NAME, arrayOf(BaseColumns._ID), selection, args, null, null, null
        ).use { cursor ->
            return cursor.count > 0
        }
    }

    private fun mapCursorToCluster(cursor: Cursor): RouteCluster {
        return RouteCluster(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(RouteClusterContract.COLUMN_NAME)),
            probableSportId = cursor.getLong(cursor.getColumnIndexOrThrow(RouteClusterContract.COLUMN_PROBABLE_SPORT_ID)),
            startLat = cursor.getDouble(cursor.getColumnIndexOrThrow(RouteClusterContract.COLUMN_START_LAT)),
            startLng = cursor.getDouble(cursor.getColumnIndexOrThrow(RouteClusterContract.COLUMN_START_LNG)),
            endLat = cursor.getDouble(cursor.getColumnIndexOrThrow(RouteClusterContract.COLUMN_END_LAT)),
            endLng = cursor.getDouble(cursor.getColumnIndexOrThrow(RouteClusterContract.COLUMN_END_LNG)),
            maxDispLat = cursor.getDouble(cursor.getColumnIndexOrThrow(RouteClusterContract.COLUMN_MAX_DISP_LAT)),
            maxDispLng = cursor.getDouble(cursor.getColumnIndexOrThrow(RouteClusterContract.COLUMN_MAX_DISP_LNG)),
            refDistance = cursor.getDouble(cursor.getColumnIndexOrThrow(RouteClusterContract.COLUMN_REF_DISTANCE)),
            hitCount = cursor.getInt(cursor.getColumnIndexOrThrow(RouteClusterContract.COLUMN_HIT_COUNT))
        )
    }

    private fun createContentValues(cluster: RouteCluster) = ContentValues().apply {
        put(RouteClusterContract.COLUMN_NAME, cluster.name)
        put(RouteClusterContract.COLUMN_PROBABLE_SPORT_ID, cluster.probableSportId)
        put(RouteClusterContract.COLUMN_START_LAT, cluster.startLat)
        put(RouteClusterContract.COLUMN_START_LNG, cluster.startLng)
        put(RouteClusterContract.COLUMN_END_LAT, cluster.endLat)
        put(RouteClusterContract.COLUMN_END_LNG, cluster.endLng)
        put(RouteClusterContract.COLUMN_MAX_DISP_LAT, cluster.maxDispLat)
        put(RouteClusterContract.COLUMN_MAX_DISP_LNG, cluster.maxDispLng)
        put(RouteClusterContract.COLUMN_REF_DISTANCE, cluster.refDistance)
        put(RouteClusterContract.COLUMN_HIT_COUNT, cluster.hitCount)
    }

    object RouteClusterContract {
        const val TABLE_NAME = "RouteClusters"
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
                $COLUMN_HIT_COUNT INTEGER
            )
        """
    }

    private class RouteClusterDbHelper(context: Context) : SQLiteOpenHelper(
        context, "RouteClusters.db", null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(RouteClusterContract.CREATE_TABLE)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Initial version
        }
    }
}
