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
    val distance: Double,
    val elevationGain: Double,
    val sportType: BSportType,
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
     * Uses PolyUtil to encode the List<LatLng> into a single string.
     */
    fun insertRoute(summary: RouteSummary, path: List<PathPoint>): Long {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            // 1. Calculate the encoded polyline string with a step size of ENCODING_STEP_SIZE
            val latLngsForEncoding = mutableListOf<LatLng>()
            for (i in path.indices step RouteContract.ENCODING_STEP_SIZE) {
                latLngsForEncoding.add(path[i].latLng)
            }
            // Ensure the last point is always included for visual closure
            if (path.isNotEmpty() && (path.size - 1) % RouteContract.ENCODING_STEP_SIZE != 0) {
                latLngsForEncoding.add(path.last().latLng)
            }
            // now, do the encoding
            val encodedPolyline = PolyUtil.encode(latLngsForEncoding)

            // 2. Insert Summary
            val values = ContentValues().apply {
                put(RouteContract.COLUMN_EXTERNAL_ID, summary.externalId)
                put(RouteContract.COLUMN_NAME, summary.name)
                put(RouteContract.COLUMN_DISTANCE, summary.distance)
                put(RouteContract.COLUMN_ELEVATION_GAIN, summary.elevationGain)
                put(RouteContract.COLUMN_SPORT_TYPE, summary.sportType.ordinal)
                put(RouteContract.COLUMN_SOURCE, summary.source.name)
                put(RouteContract.COLUMN_MAP_POLYLINE, encodedPolyline)
            }
            val routeId = db.insert(RouteContract.TABLE_ROUTES, null, values)

            // 3. Insert ALL granular points (Step size = 1)
            // We keep the high-resolution data in the points table for live navigation
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
     * Retrieves all routes, sorted by most recently used.
     * Decodes the polyline string into a list of PathPoints.
     */
    fun getAllRouteSummaries(): List<RouteSummary> {
        val routes = mutableListOf<RouteSummary>()
        val db = dbHelper.readableDatabase

        db.query(
            RouteContract.TABLE_ROUTES,
            null, // all columns
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            // Fetch indices once for performance
            val idIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ID)
            val extIdIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_EXTERNAL_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_NAME)
            val distIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_DISTANCE)
            val elevIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ELEVATION_GAIN)
            val sportIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_SPORT_TYPE)
            val sourceIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_SOURCE)
            val polyIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_MAP_POLYLINE)

            while (cursor.moveToNext()) {
                // 1. Reconstruct RouteSource and BSportType
                val sourceString = cursor.getString(sourceIdx)
                val sportOrdinal = cursor.getInt(sportIdx)

                val source = RouteSource.fromString(sourceString)
                val sportType = if (sportOrdinal in BSportType.entries.indices) {
                    BSportType.entries[sportOrdinal]
                } else {
                    BSportType.UNKNOWN // Fallback
                }

                // 2. Build the Summary
                val summary = RouteSummary(
                    id = cursor.getLong(idIdx),
                    externalId = cursor.getString(extIdIdx) ?: "",
                    name = cursor.getString(nameIdx) ?: "Unknown Route",
                    distance = cursor.getDouble(distIdx),
                    elevationGain = cursor.getDouble(elevIdx),
                    sportType = sportType,
                    source = source
                )

                routes.add(summary)
            }
        }
        return routes
    }

    /**
     * Retrieves a specific route with its full path.
     */
    fun getRouteWithPath(routeId: Long): RouteWithPath? {
        val db = dbHelper.readableDatabase
        var summary: RouteSummary? = null

        // 1. Fetch the Summary
        db.query(
            RouteContract.TABLE_ROUTES,
            null,
            "${RouteContract.COLUMN_ID} = ?",
            arrayOf(routeId.toString()),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                summary = mapCursorToRouteSummary(cursor)
            }
        }

        val actualSummary = summary ?: return null

        // 2. Fetch the Path Points
        val path = getRoutePath(routeId)

        return RouteWithPath(actualSummary, path)
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
     * Fetches only the routes that are currently marked as selected.
     * Useful for the LiveGuidanceRepository to load paths into memory.
     */
    fun getSelectedRoutes(): List<RouteWithPath> {
        val routes = mutableListOf<RouteWithPath>()
        val db = dbHelper.readableDatabase

        db.query(
            RouteContract.TABLE_ROUTES,
            null,
            "${RouteContract.COLUMN_IS_SELECTED} = 1",
            null, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val routeId = cursor.getLong(cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ID))
                val summary = mapCursorToRouteSummary(cursor)
                val path = getRoutePath(routeId)
                routes.add(RouteWithPath(summary, path))
            }
        }
        return routes
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
        val distIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_DISTANCE)
        val elevIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ELEVATION_GAIN)
        val sportIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_SPORT_TYPE)
        val sourceIdx = cursor.getColumnIndexOrThrow(RouteContract.COLUMN_SOURCE)

        val sportOrdinal = cursor.getInt(sportIdx)
        val sportType = if (sportOrdinal in BSportType.entries.indices) {
            BSportType.entries[sportOrdinal]
        } else {
            BSportType.UNKNOWN
        }

        return RouteSummary(
            id = cursor.getLong(idIdx),
            externalId = cursor.getString(extIdIdx) ?: "",
            name = cursor.getString(nameIdx) ?: "Unknown Route",
            distance = cursor.getDouble(distIdx),
            elevationGain = cursor.getDouble(elevIdx),
            sportType = sportType,
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
        const val COLUMN_DISTANCE = "distance"
        const val COLUMN_ELEVATION_GAIN = "elevation_gain"
        const val COLUMN_SPORT_TYPE = "sport_type"
        const val COLUMN_SOURCE = "source"
        const val COLUMN_IS_SELECTED = "is_selected"
        const val COLUMN_MAP_POLYLINE = "map_polyline"

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
            $COLUMN_DISTANCE REAL,
            $COLUMN_ELEVATION_GAIN REAL,
            $COLUMN_SPORT_TYPE INTEGER,
            $COLUMN_SOURCE TEXT,
            $COLUMN_IS_SELECTED INTEGER,
            $COLUMN_MAP_POLYLINE TEXT
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
            const val DB_VERSION = 1 // Starting version for the new separate DB

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

            // Future migrations will go here
        }
    }
}