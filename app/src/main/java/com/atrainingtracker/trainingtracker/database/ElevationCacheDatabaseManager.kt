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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager
import kotlin.math.roundToLong

/**
 * Quantized geographic coordinate entity for topographic Digital Elevation Model (DEM)
 * and route-elevation caching (REQ-SEN-025.2).
 */
data class ElevationCacheItem(
    val id: Long = 0L,
    val latE5: Int,
    val lonE5: Int,
    val altitude: Double,
    val source: String,
    val updatedAt: Long
) {
    companion object {
        fun fromCursor(cursor: Cursor): ElevationCacheItem {
            return ElevationCacheItem(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(ElevationCacheDbHelper.COLUMN_ID)),
                latE5 = cursor.getInt(cursor.getColumnIndexOrThrow(ElevationCacheDbHelper.COLUMN_LAT_E5)),
                lonE5 = cursor.getInt(cursor.getColumnIndexOrThrow(ElevationCacheDbHelper.COLUMN_LON_E5)),
                altitude = cursor.getDouble(cursor.getColumnIndexOrThrow(ElevationCacheDbHelper.COLUMN_ALTITUDE)),
                source = cursor.getString(cursor.getColumnIndexOrThrow(ElevationCacheDbHelper.COLUMN_SOURCE)),
                updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(ElevationCacheDbHelper.COLUMN_UPDATED_AT))
            )
        }

        fun fromCursorBatch(cursor: Cursor): List<ElevationCacheItem> {
            val list = mutableListOf<ElevationCacheItem>()
            while (cursor.moveToNext()) {
                list.add(fromCursor(cursor))
            }
            return list
        }

        fun toContentValues(item: ElevationCacheItem): ContentValues {
            val cv = ContentValues()
            if (item.id > 0) {
                cv.put(ElevationCacheDbHelper.COLUMN_ID, item.id)
            }
            cv.put(ElevationCacheDbHelper.COLUMN_LAT_E5, item.latE5)
            cv.put(ElevationCacheDbHelper.COLUMN_LON_E5, item.lonE5)
            cv.put(ElevationCacheDbHelper.COLUMN_ALTITUDE, item.altitude)
            cv.put(ElevationCacheDbHelper.COLUMN_SOURCE, item.source)
            cv.put(ElevationCacheDbHelper.COLUMN_UPDATED_AT, item.updatedAt)
            return cv
        }
    }
}

/**
 * SQLiteOpenHelper managing the standalone spatial elevation cache database `ElevationCache.db`.
 *
 * Traceability:
 * - REQ-SEN-025.2: Dedicated spatial database with quantized (lat_e5, lon_e5) index.
 * - Architecture Rule 62: Direct DB_VERSION reference in DatabaseSchemaRegistry.
 * - Architecture Rule 64: Schema parity between onCreate and future onUpgrade.
 */
class ElevationCacheDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "ElevationCache.db"
        const val DB_VERSION = 1

        const val TABLE_ELEVATION_CACHE = "elevation_cache"
        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_LAT_E5 = "lat_e5"
        const val COLUMN_LON_E5 = "lon_e5"
        const val COLUMN_ALTITUDE = "altitude"
        const val COLUMN_SOURCE = "source"
        const val COLUMN_UPDATED_AT = "updated_at"

        const val CREATE_TABLE_ELEVATION_CACHE = """
            CREATE TABLE IF NOT EXISTS $TABLE_ELEVATION_CACHE (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_LAT_E5 INT NOT NULL,
                $COLUMN_LON_E5 INT NOT NULL,
                $COLUMN_ALTITUDE REAL NOT NULL,
                $COLUMN_SOURCE TEXT NOT NULL,
                $COLUMN_UPDATED_AT INT NOT NULL
            );
        """

        const val CREATE_INDEX_ELEV_CACHE_COORDS = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_elev_cache_coords 
            ON $TABLE_ELEVATION_CACHE ($COLUMN_LAT_E5, $COLUMN_LON_E5);
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_ELEVATION_CACHE)
        db.execSQL(CREATE_INDEX_ELEV_CACHE_COORDS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // V1 Initial creation. Schema parity guaranteed per Rule 64.
    }
}

/**
 * Standalone spatial elevation cache manager providing quantized (lat_e5, lon_e5) coordinate lookups,
 * nearest-neighbor fallback within spatial tolerance, batch insertions, and route/segment seeding.
 *
 * Traceability: REQ-SEN-025.2, REQ-SEN-025.3, REQ-SEN-025.4.
 */
class ElevationCacheDatabaseManager private constructor(context: Context) {

    private val dbHelper = ElevationCacheDbHelper(context.applicationContext)

    val database: SQLiteDatabase
        @Synchronized get() = dbHelper.writableDatabase

    companion object {
        @Volatile
        private var instance: ElevationCacheDatabaseManager? = null

        @JvmStatic
        fun getInstance(context: Context): ElevationCacheDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: ElevationCacheDatabaseManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        @JvmStatic
        @VisibleForTesting
        fun resetForTesting(newInstance: ElevationCacheDatabaseManager? = null) {
            instance = newInstance
        }

        @JvmStatic
        fun toE5(coord: Double): Int {
            return (coord * 100000.0).roundToLong().toInt()
        }
    }

    /**
     * Looks up cached elevation for the given latitude and longitude.
     *
     * @param latitude Target latitude in decimal degrees.
     * @param longitude Target longitude in decimal degrees.
     * @param toleranceE5 Maximum quantized spatial tolerance (default: 2 ≈ ±2.2m).
     * @return Elevation in meters, or null if no point is cached within tolerance.
     */
    @Synchronized
    fun getElevation(latitude: Double, longitude: Double, toleranceE5: Int = 2): Double? {
        val latE5 = toE5(latitude)
        val lonE5 = toE5(longitude)
        val db = database

        // Fast path: exact match on unique index
        val exactQuery = "${ElevationCacheDbHelper.COLUMN_LAT_E5} = ? AND ${ElevationCacheDbHelper.COLUMN_LON_E5} = ?"
        db.query(
            ElevationCacheDbHelper.TABLE_ELEVATION_CACHE,
            arrayOf(ElevationCacheDbHelper.COLUMN_ALTITUDE),
            exactQuery,
            arrayOf(latE5.toString(), lonE5.toString()),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getDouble(0)
            }
        }

        if (toleranceE5 <= 0) return null

        // Nearest-neighbor lookup within bounding box tolerance
        val minLat = latE5 - toleranceE5
        val maxLat = latE5 + toleranceE5
        val minLon = lonE5 - toleranceE5
        val maxLon = lonE5 + toleranceE5

        val rangeQuery = "${ElevationCacheDbHelper.COLUMN_LAT_E5} BETWEEN ? AND ? AND ${ElevationCacheDbHelper.COLUMN_LON_E5} BETWEEN ? AND ?"
        val rangeArgs = arrayOf(minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString())

        db.query(
            ElevationCacheDbHelper.TABLE_ELEVATION_CACHE,
            arrayOf(ElevationCacheDbHelper.COLUMN_LAT_E5, ElevationCacheDbHelper.COLUMN_LON_E5, ElevationCacheDbHelper.COLUMN_ALTITUDE),
            rangeQuery,
            rangeArgs,
            null, null, null
        ).use { cursor ->
            var nearestDistSq = Long.MAX_VALUE
            var nearestAltitude: Double? = null

            while (cursor.moveToNext()) {
                val pLat = cursor.getInt(0)
                val pLon = cursor.getInt(1)
                val pAlt = cursor.getDouble(2)

                val dLat = (pLat - latE5).toLong()
                val dLon = (pLon - lonE5).toLong()
                val distSq = dLat * dLat + dLon * dLon

                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq
                    nearestAltitude = pAlt
                }
            }
            return nearestAltitude
        }
    }

    /**
     * Stores or updates an elevation point in the cache with CONFLICT_REPLACE.
     */
    @Synchronized
    fun putElevation(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        source: String = "DEM_CACHE",
        updatedAt: Long = System.currentTimeMillis()
    ): Long {
        val latE5 = toE5(latitude)
        val lonE5 = toE5(longitude)
        val cv = ContentValues().apply {
            put(ElevationCacheDbHelper.COLUMN_LAT_E5, latE5)
            put(ElevationCacheDbHelper.COLUMN_LON_E5, lonE5)
            put(ElevationCacheDbHelper.COLUMN_ALTITUDE, altitude)
            put(ElevationCacheDbHelper.COLUMN_SOURCE, source)
            put(ElevationCacheDbHelper.COLUMN_UPDATED_AT, updatedAt)
        }
        return database.insertWithOnConflict(
            ElevationCacheDbHelper.TABLE_ELEVATION_CACHE,
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /**
     * Inserts multiple elevation records atomically in a transaction.
     */
    @Synchronized
    fun putBatchElevations(items: List<ElevationCacheItem>): Int {
        if (items.isEmpty()) return 0
        val db = database
        var insertedCount = 0

        db.beginTransaction()
        try {
            for (item in items) {
                val cv = ElevationCacheItem.toContentValues(item)
                val rowId = db.insertWithOnConflict(
                    ElevationCacheDbHelper.TABLE_ELEVATION_CACHE,
                    null,
                    cv,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                if (rowId != -1L) {
                    insertedCount++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return insertedCount
    }

    /**
     * Seeds the elevation cache from existing route trackpoints in [RoutesDatabaseManager] (REQ-SEN-025.3).
     * Strictly read-only against Routes.db; never mutates KnownLocations.db.
     */
    @Synchronized
    fun seedFromRoutes(routesDatabaseManager: RoutesDatabaseManager): Int {
        val dbRoutes = routesDatabaseManager.getDatabase()
        val items = mutableListOf<ElevationCacheItem>()
        val now = System.currentTimeMillis()

        val cursor = dbRoutes.query(
            "route_points",
            arrayOf("lat", "lng", "elevation"),
            "lat IS NOT NULL AND lng IS NOT NULL AND elevation IS NOT NULL",
            null, null, null, null
        )

        cursor.use { c ->
            val latIdx = c.getColumnIndexOrThrow("lat")
            val lngIdx = c.getColumnIndexOrThrow("lng")
            val elevIdx = c.getColumnIndexOrThrow("elevation")

            while (c.moveToNext()) {
                val lat = c.getDouble(latIdx)
                val lng = c.getDouble(lngIdx)
                val elev = c.getDouble(elevIdx)
                if (!lat.isNaN() && !lng.isNaN() && !elev.isNaN()) {
                    items.add(
                        ElevationCacheItem(
                            latE5 = toE5(lat),
                            lonE5 = toE5(lng),
                            altitude = elev,
                            source = "ROUTE_SEED",
                            updatedAt = now
                        )
                    )
                }
            }
        }

        return putBatchElevations(items)
    }

    /**
     * Seeds the elevation cache from existing segment streams in [SegmentsDatabaseManager] (REQ-SEN-025.3).
     * Strictly read-only against Segments.db; never mutates KnownLocations.db.
     */
    @Synchronized
    fun seedFromSegments(segmentsDatabaseManager: SegmentsDatabaseManager? = null): Int {
        if (segmentsDatabaseManager == null) return 0
        val dbSegments = segmentsDatabaseManager.database
        val items = mutableListOf<ElevationCacheItem>()
        val now = System.currentTimeMillis()

        val cursor = dbSegments.query(
            "SegmentStreams",
            arrayOf("Latitude", "Longitude", "Altitude"),
            "Latitude IS NOT NULL AND Longitude IS NOT NULL AND Altitude IS NOT NULL",
            null, null, null, null
        )

        cursor.use { c ->
            val latIdx = c.getColumnIndexOrThrow("Latitude")
            val lngIdx = c.getColumnIndexOrThrow("Longitude")
            val elevIdx = c.getColumnIndexOrThrow("Altitude")

            while (c.moveToNext()) {
                val lat = c.getDouble(latIdx)
                val lng = c.getDouble(lngIdx)
                val elev = c.getDouble(elevIdx)
                if (!lat.isNaN() && !lng.isNaN() && !elev.isNaN()) {
                    items.add(
                        ElevationCacheItem(
                            latE5 = toE5(lat),
                            lonE5 = toE5(lng),
                            altitude = elev,
                            source = "SEGMENT_SEED",
                            updatedAt = now
                        )
                    )
                }
            }
        }

        return putBatchElevations(items)
    }

    /**
     * Returns total number of cached elevation entries.
     */
    @Synchronized
    fun getCacheCount(): Long {
        val cursor = database.rawQuery("SELECT COUNT(*) FROM ${ElevationCacheDbHelper.TABLE_ELEVATION_CACHE}", null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        return 0L
    }

    /**
     * Clears all cached elevations.
     */
    @Synchronized
    fun clearCache(): Int {
        return database.delete(ElevationCacheDbHelper.TABLE_ELEVATION_CACHE, null, null)
    }

    @Synchronized
    fun close() {
        dbHelper.close()
    }
}
