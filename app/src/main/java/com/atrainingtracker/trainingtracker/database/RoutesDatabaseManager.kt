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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil


data class RouteSummary(
    val id: Long,
    val externalId: String,
    val name: String,
    val description: String,
    val isSelected: Boolean,
    val distance: Double,
    val elevationGain: Double,
    val bSportType: BSportType,
    val source: RouteSource
)

enum class RouteSource(
    val displayNameResId: Int
) {
    STRAVA(R.string.Strava),
    LOCAL_GPX(R.string.GPX);

    companion object {
        /**
         * Safe helper to convert a string (from DB) back to the enum.
         * Useful when reading from the 'source' column.
         */
        fun fromString(value: String?): RouteSource {
            return entries.find { it.name == value } ?: LOCAL_GPX
        }
    }
}

data class RouteWithPath(
    val summary: RouteSummary,
    val path: List<PathPoint>
)


class RoutesDatabaseManager private constructor(context: Context) {

    private val dbHelper = RoutesDbHelper(context)

    companion object {
        @Volatile
        private var instance: RoutesDatabaseManager? = null

        /**
         * Thread-safe Singleton access
         */
        fun getInstance(context: Context): RoutesDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: RoutesDatabaseManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * Inserts a new route into the database.
     */
    fun insertRoute(summary: RouteSummary, path: List<PathPoint>): Long {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            // 1. Insert Summary
            val values = ContentValues().apply {
                put(RouteContract.COLUMN_EXTERNAL_ID, summary.externalId)
                put(RouteContract.COLUMN_NAME, summary.name)
                put(RouteContract.COLUMN_DESCRIPTION, summary.description)
                put(RouteContract.COLUMN_DISTANCE, summary.distance)
                put(RouteContract.COLUMN_ELEVATION_GAIN, summary.elevationGain)
                put(RouteContract.COLUMN_SPORT_TYPE, summary.bSportType.name)
                put(RouteContract.COLUMN_SOURCE, summary.source.name)
            }
            val routeId = db.insert(RouteContract.TABLE_ROUTES, null, values)

            // 2. Insert the route points
            path.forEach { point ->
                val pValues = ContentValues().apply {
                    put(RouteContract.COLUMN_ROUTE_ID_FK, routeId)
                    put(RouteContract.COLUMN_LAT, point.latLng.latitude)
                    put(RouteContract.COLUMN_LNG, point.latLng.longitude)
                    put(RouteContract.COLUMN_DIST_FROM_START, point.distance)
                    put(RouteContract.COLUMN_ALTITUDE, point.altitude)
                }
                db.insert(RouteContract.TABLE_ROUTE_POINTS, null, pValues)
            }

            db.setTransactionSuccessful()
            routeId
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Updates the metadata of an existing route (Name, Description, SportType).
     * @return The number of rows affected.
     */
    fun updateRouteSummary(summary: RouteSummary): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(RouteContract.COLUMN_NAME, summary.name)
            put(RouteContract.COLUMN_DESCRIPTION, summary.description)
            put(RouteContract.COLUMN_SPORT_TYPE, summary.bSportType.name)
        }

        return db.update(
            RouteContract.TABLE_ROUTES,
            values,
            "${RouteContract.COLUMN_ID} = ?",
            arrayOf(summary.id.toString())
        )
    }

    /**
     * Retrieves all routes.
     */
    fun getAllRoutes(): List<RouteWithPath> {
        val routes = mutableListOf<RouteWithPath>()
        val db = dbHelper.readableDatabase

        // Index for the polyline column
        db.query(
            RouteContract.TABLE_ROUTES,
            null, // all columns
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->

            while (cursor.moveToNext()) {
                val routeSummary = mapCursorToRouteSummary(cursor)
                val pathPoints = getRoutePath(routeSummary.id)

                routes.add(RouteWithPath(routeSummary, pathPoints))
            }
        }
        return routes
    }

    /**
     * Toggles whether a route is marked for detailed display on the map.
     */
    fun setRouteSelected(routeId: Long, isSelected: Boolean): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(RouteContract.COLUMN_IS_SELECTED, if (isSelected) 1 else 0)
        }
        return db.update(
            RouteContract.TABLE_ROUTES,
            values,
            "${RouteContract.COLUMN_ID} = ?",
            arrayOf(routeId.toString())
        )
    }

    /**
     * Retrieves only the path points for a specific route ID.
     */
    fun getRoutePath(routeId: Long): List<PathPoint> {
        val pathPoints = mutableListOf<PathPoint>()
        val db = dbHelper.readableDatabase

        db.query(
            RouteContract.TABLE_ROUTE_POINTS,
            null,
            "${RouteContract.COLUMN_ROUTE_ID_FK} = ?",
            arrayOf(routeId.toString()),
            null,
            null,
            "${RouteContract.COLUMN_POINT_ID} ASC" // Maintain sequence
        ).use { cursor ->
            val latIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_LAT)
            val lngIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_LNG)
            val distIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_DIST_FROM_START)
            val altIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ALTITUDE)

            while (cursor.moveToNext()) {
                pathPoints.add(
                    PathPoint(
                        latLng = com.google.android.gms.maps.model.LatLng(
                            cursor.getDouble(latIdx),
                            cursor.getDouble(lngIdx)
                        ),
                        distance = cursor.getDouble(distIdx),
                        altitude = cursor.getDouble(altIdx)
                    )
                )
            }
        }
        return pathPoints
    }

    /**
     * Private helper to map a cursor row to a RouteSummary object.
     */
    private fun mapCursorToRouteSummary(cursor: android.database.Cursor): RouteSummary {
        val idIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ID)
        val extIdIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_EXTERNAL_ID)
        val nameIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_NAME)
        val descIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_DESCRIPTION)
        val isSelectedIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_IS_SELECTED)
        val distIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_DISTANCE)
        val elevIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ELEVATION_GAIN)
        val sportIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_SPORT_TYPE)
        val sourceIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_SOURCE)

        val sportString = cursor.getString(sportIdx)
        val sportType = try {
            BSportType.valueOf(sportString)
        } catch (e: Exception) {
            BSportType.UNKNOWN
        }

        return RouteSummary(
            id = cursor.getLong(idIdx),
            externalId = cursor.getString(extIdIdx) ?: "",
            name = cursor.getString(nameIdx) ?: "Unknown Route",
            description = cursor.getString(descIdx) ?: "",
            isSelected = cursor.getInt(isSelectedIdx) == 1,
            distance = cursor.getDouble(distIdx),
            elevationGain = cursor.getDouble(elevIdx),
            bSportType = sportType,
            source = RouteSource.fromString(cursor.getString(sourceIdx))
        )
    }

    /**
     * Deletes a route by its internal database ID.
     */
    fun deleteRoute(routeId: Long): Int {
        val db = dbHelper.writableDatabase
        return db.delete(RouteContract.TABLE_ROUTES,
            "${RouteContract.COLUMN_ID} = ?",
            arrayOf(routeId.toString())
        )
    }


    object RouteContract {

        /**
         * Step size for the encoded polyline string.
         * 1 means every point, 20 means every 20th point.
         */
        const val ENCODING_STEP_SIZE = 20


        const val TABLE_ROUTES = "routes"
        const val COLUMN_ID = "id"
        const val COLUMN_EXTERNAL_ID = "external_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_DESCRIPTION = "description"
        const val COLUMN_DISTANCE = "distance"
        const val COLUMN_ELEVATION_GAIN = "elevation_gain"
        const val COLUMN_SPORT_TYPE = "sport_type"
        const val COLUMN_SOURCE = "source"
        const val COLUMN_IS_SELECTED = "is_selected"
        // const val COLUMN_MAP_POLYLINE = "map_polyline"

        const val TABLE_ROUTE_POINTS = "route_points"
        const val COLUMN_POINT_ID = "id"
        const val COLUMN_ROUTE_ID_FK = "route_id"
        const val COLUMN_LAT = "lat"
        const val COLUMN_LNG = "lng"
        const val COLUMN_DIST_FROM_START = "distance_from_start"
        const val COLUMN_ALTITUDE = "elevation"

        const val CREATE_TABLE_ROUTES = """
        CREATE TABLE $TABLE_ROUTES (
            $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_EXTERNAL_ID TEXT,
            $COLUMN_NAME TEXT,
            $COLUMN_DESCRIPTION TEXT,
            $COLUMN_DISTANCE REAL,
            $COLUMN_ELEVATION_GAIN REAL,
            $COLUMN_SPORT_TYPE TEXT,
            $COLUMN_SOURCE TEXT,
            $COLUMN_IS_SELECTED INTEGER
        );
    """

        const val CREATE_TABLE_ROUTE_POINTS = """
        CREATE TABLE $TABLE_ROUTE_POINTS (
            $COLUMN_POINT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_ROUTE_ID_FK INTEGER,
            $COLUMN_LAT REAL,
            $COLUMN_LNG REAL,
            $COLUMN_DIST_FROM_START REAL,
            $COLUMN_ALTITUDE REAL,
            FOREIGN KEY($COLUMN_ROUTE_ID_FK) REFERENCES $TABLE_ROUTES($COLUMN_ID) ON DELETE CASCADE
        );
    """
    }

    class RoutesDbHelper(context: Context) : SQLiteOpenHelper(
        context,
        DB_NAME,
        null,
        DB_VERSION
    ) {

        companion object {
            const val DB_NAME = "Routes.db"
            // const val DB_VERSION = 2 // Storing BSportType as String.
            // const val DB_VERSION = 3    // No more storing the polyline.
            const val DB_VERSION = 5    // Added the description

            private const val TAG = "RoutesDbHelper"
            private val DEBUG = TrainingApplication.getDebug(true)

        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(RouteContract.CREATE_TABLE_ROUTES)
            db.execSQL(RouteContract.CREATE_TABLE_ROUTE_POINTS)
        }

        override fun onOpen(db: SQLiteDatabase) {
            super.onOpen(db)
            // Enable Foreign Keys for ON DELETE CASCADE
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            Log.i(TAG, "Upgrading Routes database from $oldVersion to $newVersion")

            if (oldVersion < 5) {
                db.execSQL("DROP TABLE IF EXISTS ${RouteContract.TABLE_ROUTE_POINTS}")
                db.execSQL("DROP TABLE IF EXISTS ${RouteContract.TABLE_ROUTES}")
                onCreate(db)
            }
        }
    }
}