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
import com.atrainingtracker.database.TrackingSQLiteOpenHelper
import android.location.Location
import android.provider.BaseColumns
import com.atrainingtracker.database.inTransaction
import com.atrainingtracker.database.mapRows
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.climbs.ClimbDeduplicator
import com.atrainingtracker.trainingtracker.climbs.GeoBoundingBox
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLite helper for the persistent climbs database (REQ-MAP-027).
 */
class ClimbsDbHelper(context: Context) : TrackingSQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

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
        db.insert(ClimbsDbHelper.TABLE_CLIMBS, null, climbToContentValues(climb))
    }

    /**
     * Inserts a climb with spatial deduplication on start and summit coordinates or corridor overlap (REQ-CLM-011.1, REQ-STB-021.1).
     * If an existing climb matches within [thresholdMeters] or shares >= 70% corridor overlap, returns existing ID.
     */
    suspend fun insertClimbWithDeduplication(
        climb: Climb,
        thresholdMeters: Float = 50.0f
    ): Long = withContext(dbDispatcher) {
        val db = dbHelper.writableDatabase
        val existingMeta = getAllClimbsMetadataInternal(db)
        val pathCache = HashMap<Long, List<PathPoint>>()
        val matched = findDuplicateMatch(
            db = db,
            candMeta = climb.toMetadata(),
            candidates = existingMeta,
            thresholdMeters = thresholdMeters,
            pathCache = pathCache
        )
        if (matched != null) {
            if (climb.routeId != null && matched.routeId == null) {
                updateClimbRouteId(db, matched.id, climb.routeId)
            }
            return@withContext matched.id
        }

        db.insert(ClimbsDbHelper.TABLE_CLIMBS, null, climbToContentValues(climb))
    }

    /**
     * Inserts a collection of climbs in a single atomic SQLite transaction with spatial deduplication (REQ-MAP-027, REQ-CLM-011.1, REQ-STB-021.1).
     * Eliminates transaction overhead, SQLite lock contention, and eager polyline deserialization on batch imports.
     */
    suspend fun insertClimbsWithDeduplicationBatch(
        climbs: List<Climb>,
        thresholdMeters: Float = 50.0f
    ): List<Long> = withContext(dbDispatcher) {
        if (climbs.isEmpty()) return@withContext emptyList()
        val db = dbHelper.writableDatabase
        val existingMeta = getAllClimbsMetadataInternal(db).toMutableList()
        val pathCache = HashMap<Long, List<PathPoint>>()
        val resultIds = mutableListOf<Long>()

        db.inTransaction {
            for (climb in climbs) {
                val matched = findDuplicateMatch(
                    db = db,
                    candMeta = climb.toMetadata(),
                    candidates = existingMeta,
                    thresholdMeters = thresholdMeters,
                    pathCache = pathCache
                )
                if (matched != null) {
                    if (climb.routeId != null && matched.routeId == null) {
                        updateClimbRouteId(db, matched.id, climb.routeId)
                        val idx = existingMeta.indexOfFirst { it.id == matched.id }
                        if (idx != -1) {
                            existingMeta[idx] = matched.copy(
                                routeId = climb.routeId,
                                inMemoryClimb = matched.inMemoryClimb?.copy(routeId = climb.routeId)
                            )
                        }
                    }
                    resultIds.add(matched.id)
                } else {
                    val newId = db.insert(ClimbsDbHelper.TABLE_CLIMBS, null, climbToContentValues(climb))
                    resultIds.add(newId)
                    val inserted = climb.copy(id = newId)
                    existingMeta.add(inserted.toMetadata())
                }
            }
        }
        resultIds
    }

    /**
     * Updates an existing climb in SQLite (REQ-CLM-011.1).
     */
    suspend fun updateClimb(climb: Climb): Int = withContext(dbDispatcher) {
        val db = dbHelper.writableDatabase
        db.update(
            ClimbsDbHelper.TABLE_CLIMBS,
            climbToContentValues(climb),
            "${ClimbsDbHelper.COLUMN_ID} = ?",
            arrayOf(climb.id.toString())
        )
    }

    /**
     * Scans all stored climbs in SQLite, identifies duplicate or overlapping climbs,
     * consolidates metadata (preserving route associations), and deletes redundant rows
     * in a single atomic transaction (REQ-CLM-011.2, REQ-STB-021.1).
     *
     * @return Number of redundant duplicate climbs purged from the database.
     */
    suspend fun deduplicateExistingClimbs(thresholdMeters: Float = 100.0f): Int = withContext(dbDispatcher) {
        val db = dbHelper.writableDatabase
        val allMeta = getAllClimbsMetadataInternal(db)
        if (allMeta.size < 2) return@withContext 0

        val pathCache = HashMap<Long, List<PathPoint>>()
        val idsToDelete = mutableSetOf<Long>()
        val retainedMeta = mutableListOf<ClimbMetadata>()

        for (cand in allMeta) {
            if (cand.id in idsToDelete) continue
            val duplicateFound = findDuplicateMatch(
                db = db,
                candMeta = cand,
                candidates = retainedMeta,
                thresholdMeters = thresholdMeters,
                pathCache = pathCache
            )

            if (duplicateFound != null) {
                if (cand.routeId != null && duplicateFound.routeId == null) {
                    updateClimbRouteId(db, duplicateFound.id, cand.routeId)
                    val idx = retainedMeta.indexOfFirst { it.id == duplicateFound.id }
                    if (idx != -1) {
                        retainedMeta[idx] = duplicateFound.copy(
                            routeId = cand.routeId,
                            inMemoryClimb = duplicateFound.inMemoryClimb?.copy(routeId = cand.routeId)
                        )
                    }
                }
                idsToDelete.add(cand.id)
            } else {
                retainedMeta.add(cand)
            }
        }

        if (idsToDelete.isEmpty()) return@withContext 0

        db.inTransaction {
            for (id in idsToDelete) {
                db.delete(ClimbsDbHelper.TABLE_CLIMBS, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf(id.toString()))
            }
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
        db.query(
            ClimbsDbHelper.TABLE_CLIMBS,
            null,
            "${ClimbsDbHelper.COLUMN_ROUTE_ID} = ?",
            arrayOf(routeId.toString()),
            null,
            null,
            "${ClimbsDbHelper.COLUMN_ID} ASC"
        ).mapRows { cursorToClimb(it) }
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

    private data class ClimbMetadata(
        val id: Long,
        val routeId: Long?,
        val startLat: Double,
        val startLng: Double,
        val endLat: Double,
        val endLng: Double,
        val distanceMeters: Double,
        val elevationGainMeters: Double,
        val conservativeBoundingBox: GeoBoundingBox,
        val inMemoryClimb: Climb? = null
    ) {
        fun effectiveBoundingBox(): GeoBoundingBox = inMemoryClimb?.boundingBox ?: conservativeBoundingBox

        fun toClimb(pathPoints: List<PathPoint>): Climb = inMemoryClimb ?: Climb(
            id = id,
            name = "",
            routeId = routeId,
            startLat = startLat,
            startLng = startLng,
            endLat = endLat,
            endLng = endLng,
            distanceMeters = distanceMeters,
            elevationGainMeters = elevationGainMeters,
            avgGradePercent = 0.0,
            maxGradePercent = 0.0,
            category = ClimbCategory.UNCATEGORIZED,
            pathPoints = pathPoints
        )
    }

    private fun Climb.toMetadata(): ClimbMetadata = ClimbMetadata(
        id = id,
        routeId = routeId,
        startLat = startLat,
        startLng = startLng,
        endLat = endLat,
        endLng = endLng,
        distanceMeters = distanceMeters,
        elevationGainMeters = elevationGainMeters,
        conservativeBoundingBox = boundingBox,
        inMemoryClimb = this
    )

    private fun climbToContentValues(climb: Climb): ContentValues = ContentValues().apply {
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

    private fun updateClimbRouteId(db: SQLiteDatabase, climbId: Long, routeId: Long) {
        val values = ContentValues().apply {
            put(ClimbsDbHelper.COLUMN_ROUTE_ID, routeId)
        }
        db.update(
            ClimbsDbHelper.TABLE_CLIMBS,
            values,
            "${ClimbsDbHelper.COLUMN_ID} = ?",
            arrayOf(climbId.toString())
        )
    }

    private fun findDuplicateMatch(
        db: SQLiteDatabase,
        candMeta: ClimbMetadata,
        candidates: List<ClimbMetadata>,
        thresholdMeters: Float,
        pathCache: MutableMap<Long, List<PathPoint>>
    ): ClimbMetadata? {
        for (existing in candidates) {
            when (
                ClimbDeduplicator.preFilterMetadata(
                    idA = candMeta.id,
                    boxA = candMeta.effectiveBoundingBox(),
                    startLatA = candMeta.startLat,
                    startLngA = candMeta.startLng,
                    endLatA = candMeta.endLat,
                    endLngA = candMeta.endLng,
                    gainA = candMeta.elevationGainMeters,
                    idB = existing.id,
                    boxB = existing.effectiveBoundingBox(),
                    startLatB = existing.startLat,
                    startLngB = existing.startLng,
                    endLatB = existing.endLat,
                    endLngB = existing.endLng,
                    gainB = existing.elevationGainMeters,
                    thresholdMeters = thresholdMeters
                )
            ) {
                ClimbDeduplicator.MetadataPreFilterResult.NO_MATCH -> continue
                ClimbDeduplicator.MetadataPreFilterResult.ENDPOINT_MATCH -> return existing
                ClimbDeduplicator.MetadataPreFilterResult.NEEDS_CORRIDOR_CHECK -> {
                    val fullA = candMeta.inMemoryClimb
                        ?: candMeta.toClimb(getClimbPathPointsCached(db, candMeta.id, pathCache))
                    val fullB = existing.inMemoryClimb
                        ?: existing.toClimb(getClimbPathPointsCached(db, existing.id, pathCache))
                    if (ClimbDeduplicator.isDuplicateOrOverlapping(fullA, fullB, thresholdMeters)) {
                        return existing
                    }
                }
            }
        }
        return null
    }

    private fun getAllClimbsMetadataInternal(db: SQLiteDatabase): List<ClimbMetadata> {
        val list = mutableListOf<ClimbMetadata>()
        val cursor = db.query(
            ClimbsDbHelper.TABLE_CLIMBS,
            METADATA_COLUMNS,
            null,
            null,
            null,
            null,
            "${ClimbsDbHelper.COLUMN_ID} ASC"
        )
        cursor.use {
            var indicesResolved = false
            var idIdx = 0
            var routeIdIdx = 0
            var startLatIdx = 0
            var startLngIdx = 0
            var endLatIdx = 0
            var endLngIdx = 0
            var distIdx = 0
            var gainIdx = 0

            while (it.moveToNext()) {
                if (!indicesResolved) {
                    idIdx = it.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ID)
                    routeIdIdx = it.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ROUTE_ID)
                    startLatIdx = it.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_START_LAT)
                    startLngIdx = it.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_START_LNG)
                    endLatIdx = it.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_END_LAT)
                    endLngIdx = it.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_END_LNG)
                    distIdx = it.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_DISTANCE_M)
                    gainIdx = it.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ELEVATION_GAIN_M)
                    indicesResolved = true
                }
                val startLat = it.getDouble(startLatIdx)
                val startLng = it.getDouble(startLngIdx)
                val endLat = it.getDouble(endLatIdx)
                val endLng = it.getDouble(endLngIdx)
                val dist = it.getDouble(distIdx)
                list.add(
                    ClimbMetadata(
                        id = it.getLong(idIdx),
                        routeId = if (it.isNull(routeIdIdx)) null else it.getLong(routeIdIdx),
                        startLat = startLat,
                        startLng = startLng,
                        endLat = endLat,
                        endLng = endLng,
                        distanceMeters = dist,
                        elevationGainMeters = it.getDouble(gainIdx),
                        conservativeBoundingBox = ClimbDeduplicator.buildConservativeBoundingBox(
                            startLat = startLat,
                            startLng = startLng,
                            endLat = endLat,
                            endLng = endLng,
                            distanceMeters = dist
                        )
                    )
                )
            }
        }
        return list
    }

    private fun getClimbPathPointsCached(
        db: SQLiteDatabase,
        climbId: Long,
        cache: MutableMap<Long, List<PathPoint>>
    ): List<PathPoint> {
        cache[climbId]?.let { return it }
        val points = db.query(
            ClimbsDbHelper.TABLE_CLIMBS,
            POLYLINE_ONLY_COLUMN,
            "${ClimbsDbHelper.COLUMN_ID} = ?",
            arrayOf(climbId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToNext() && !cursor.isNull(0)) {
                deserializePathPoints(cursor.getString(0))
            } else {
                emptyList()
            }
        }
        cache[climbId] = points
        return points
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
        internal val METADATA_COLUMNS = arrayOf(
            ClimbsDbHelper.COLUMN_ID,
            ClimbsDbHelper.COLUMN_ROUTE_ID,
            ClimbsDbHelper.COLUMN_START_LAT,
            ClimbsDbHelper.COLUMN_START_LNG,
            ClimbsDbHelper.COLUMN_END_LAT,
            ClimbsDbHelper.COLUMN_END_LNG,
            ClimbsDbHelper.COLUMN_DISTANCE_M,
            ClimbsDbHelper.COLUMN_ELEVATION_GAIN_M
        )

        internal val POLYLINE_ONLY_COLUMN = arrayOf(ClimbsDbHelper.COLUMN_PATH_POLYLINE)

        private val POW10 = doubleArrayOf(
            1.0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15
        )

        fun serializePathPoints(points: List<PathPoint>): String {
            if (points.isEmpty()) return ""
            return points.joinToString(";") {
                "${it.latLng.latitude},${it.latLng.longitude},${it.altitude},${it.distance}"
            }
        }

        /**
         * Deserializes semicolon-delimited `lat,lng,alt,dist` tuples via zero-split index scanning (REQ-STB-021.1).
         */
        fun deserializePathPoints(str: String): List<PathPoint> {
            if (str.isBlank()) return emptyList()
            val len = str.length
            val estimatedCount = (len / 32).coerceAtLeast(4)
            val result = ArrayList<PathPoint>(estimatedCount)
            var itemStart = 0

            while (itemStart < len) {
                val semiIdx = str.indexOf(';', itemStart)
                val itemEnd = if (semiIdx == -1) len else semiIdx

                if (itemEnd > itemStart) {
                    val c1 = str.indexOf(',', itemStart)
                    if (c1 != -1 && c1 < itemEnd) {
                        val c2 = str.indexOf(',', c1 + 1)
                        if (c2 != -1 && c2 < itemEnd) {
                            val c3 = str.indexOf(',', c2 + 1)
                            if (c3 != -1 && c3 < itemEnd) {
                                val nextComma = str.indexOf(',', c3 + 1)
                                val distEnd = if (nextComma != -1 && nextComma < itemEnd) nextComma else itemEnd
                                val lat = parseDoubleRange(str, itemStart, c1)
                                val lng = parseDoubleRange(str, c1 + 1, c2)
                                val alt = parseDoubleRange(str, c2 + 1, c3)
                                val dist = parseDoubleRange(str, c3 + 1, distEnd)
                                if (lat != null && lng != null && dist != null) {
                                    result.add(PathPoint(distance = dist, latLng = LatLng(lat, lng), altitude = alt))
                                }
                            }
                        }
                    }
                }

                if (semiIdx == -1) break
                itemStart = semiIdx + 1
            }

            return result
        }

        private fun parseDoubleRange(str: String, start: Int, end: Int): Double? {
            var s = start
            var e = end
            while (s < e && str[s] <= ' ') s++
            while (e > s && str[e - 1] <= ' ') e--
            if (s >= e) return null

            if (e - s == 4 && str[s] == 'n' && str[s + 1] == 'u' && str[s + 2] == 'l' && str[s + 3] == 'l') {
                return null
            }

            var negative = false
            var idx = s
            val first = str[idx]
            if (first == '-') {
                negative = true
                idx++
            } else if (first == '+') {
                idx++
            }
            if (idx >= e) return null

            var mantissa = 0L
            var digits = 0
            var fracDigits = 0
            var seenDot = false
            var seenDigit = false

            while (idx < e) {
                val ch = str[idx]
                if (ch in '0'..'9') {
                    seenDigit = true
                    if (digits < 15 && fracDigits < 15) {
                        if (digits > 0 || ch != '0') {
                            mantissa = mantissa * 10L + (ch - '0')
                            digits++
                        }
                        if (seenDot) fracDigits++
                    } else {
                        return str.substring(s, e).toDoubleOrNull()
                    }
                } else if (ch == '.' && !seenDot) {
                    seenDot = true
                } else {
                    return str.substring(s, e).toDoubleOrNull()
                }
                idx++
            }

            if (!seenDigit) return null
            val value = if (fracDigits == 0) {
                mantissa.toDouble()
            } else {
                mantissa.toDouble() / POW10[fracDigits]
            }
            return if (negative) -value else value
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
