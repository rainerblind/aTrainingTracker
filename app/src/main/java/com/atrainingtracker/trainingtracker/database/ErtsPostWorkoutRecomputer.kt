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
import android.database.sqlite.SQLiteStatement
import android.util.Log
import com.atrainingtracker.banalservice.odometry.ExtendedRtsAltitudeSlopeSmoother
import com.atrainingtracker.banalservice.odometry.ExtendedRtsOdometrySmoother
import com.atrainingtracker.banalservice.odometry.RawAltitudeSample
import com.atrainingtracker.banalservice.odometry.RawOdometrySample
import com.atrainingtracker.banalservice.odometry.SmoothedAltitudeSlopePoint
import com.atrainingtracker.banalservice.odometry.SmoothedOdometryPoint
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

/**
 * Post-workout Extended Rauch-Tung-Striebel (ERTS/RTS) recomputation engine (REQ-FIL-017.3, TST-FIL-017.3, ATT-7750).
 *
 * Ingests recorded trackpoints from `WorkoutSamples.db`, runs non-causal bidirectional 5-state unicycle
 * odometry smoothing and 3-state kinematic altitude & slope smoothing, and updates STRICTLY canonical
 * unsuffixed columns in a single atomic transaction.
 *
 * Invariant: Device-attributed columns (containing `" ("`) are never modified and remain 100% byte-for-byte identical.
 */
@Suppress("TooManyFunctions", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "MagicNumber")
object ErtsPostWorkoutRecomputer {

    private const val TAG = "ErtsPostWorkoutRecomp"

    /**
     * Whitelist of canonical unsuffixed sensor columns eligible for post-workout ERTS update.
     */
    val ALLOWED_CANONICAL_COLUMNS: Set<String> = setOf(
        SensorType.LATITUDE.name,
        SensorType.LONGITUDE.name,
        SensorType.SPEED_mps.name,
        SensorType.PACE_spm.name,
        SensorType.DISTANCE_m.name,
        SensorType.BEARING.name,
        SensorType.ALTITUDE.name,
        SensorType.SLOPE.name,
        SensorType.VERTICAL_SPEED.name,
        SensorType.ASCENT.name,
        SensorType.DESCENT.name
    )

    const val MAX_PLAUSIBLE_SPEED_MPS = 100.0 // 360 km/h
    const val MAX_PLAUSIBLE_WORKOUT_DISTANCE_M = 1_000_000.0 // 1,000 km
    const val MAX_DISTANCE_DIVERGENCE_FACTOR = 3.0
    const val MIN_PLAUSIBLE_ALTITUDE_M = -1000.0
    const val MAX_PLAUSIBLE_ALTITUDE_M = 9000.0
    const val MAX_PLAUSIBLE_ASCENT_M = 20_000.0

    @JvmStatic
    fun recomputeWorkoutBlocking(context: Context, workoutId: Long): Boolean {
        return runBlocking(Dispatchers.IO) {
            recomputeWorkout(context, workoutId)
        }
    }

    suspend fun recomputeWorkout(context: Context, workoutId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val summariesDbManager = WorkoutSummariesDatabaseManager.getInstance(context)
            val baseFileName = summariesDbManager.getBaseFileName(workoutId) ?: run {
                Log.w(TAG, "No base file name for workout $workoutId")
                return@withContext false
            }

            val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
            if (!samplesDbManager.existsTable(baseFileName)) {
                Log.w(TAG, "Samples table for $baseFileName does not exist")
                return@withContext false
            }

            val samplesDb = samplesDbManager.database
            val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

            val rawRows = readWorkoutSamples(samplesDb, tableName)
            if (rawRows.isEmpty()) {
                Log.w(TAG, "No samples found in $tableName for workout $workoutId")
                return@withContext false
            }

            // 1. Run 5-State Odometry ERTS Backward Smoother if valid GPS data present
            val hasGpsData = rawRows.any { ExtendedRtsOdometrySmoother.hasValidGnssFix(it.latitude, it.longitude) }
            val smoothedOdometry = if (hasGpsData) {
                val odom = runOdometrySmoothing(rawRows)
                if (odom.isNotEmpty()) odom else null
            } else {
                null
            }

            // 2. Run Kinematic Altitude & Slope ERTS Backward Smoother if altitude present
            val hasAltitudeData = rawRows.any { it.rawAltitude != null }
            val smoothedAltitude = if (hasAltitudeData) {
                runAltitudeSmoothing(rawRows, smoothedOdometry)
            } else {
                null
            }

            if (smoothedOdometry == null && smoothedAltitude == null) {
                Log.i(TAG, "Neither GPS nor Altitude data present to smooth for workout $workoutId")
                return@withContext false
            }

            // REQ-FIL-019.4: Output Sanity Guard before modifying SQLite
            if (!isSmoothedOutputSane(rawRows, smoothedOdometry, smoothedAltitude)) {
                Log.e(
                    TAG,
                    "ERTS smoothed output failed sanity check for workout $workoutId; aborting recomputation and preserving live data"
                )
                return@withContext false
            }

            // 3. Update WorkoutSamples.db in a single atomic transaction (STRICT canonical column invariant)
            val updated = updateSamplesDatabase(samplesDb, tableName, rawRows, smoothedOdometry, smoothedAltitude)
            if (!updated) {
                Log.w(TAG, "Failed to update samples table for workout $workoutId")
                return@withContext false
            }

            // 4. Update WorkoutSummaries.db (streams, polyline, total distance, ascent, extrema)
            updateWorkoutSummaries(context, workoutId, rawRows, smoothedOdometry, smoothedAltitude)

            Log.i(TAG, "Successfully completed ERTS post-workout recomputation for workout $workoutId ($baseFileName)")
            return@withContext true
        } catch (t: Throwable) {
            Log.e(TAG, "Error during ERTS post-workout recomputation for workout $workoutId", t)
            return@withContext false
        }
    }

    private fun readWorkoutSamples(db: SQLiteDatabase, tableName: String): List<RawSampleRow> {
        val result = mutableListOf<RawSampleRow>()
        db.query(tableName, null, null, null, null, null, "${WorkoutSamplesDbHelper.C_ID} ASC").use { cursor ->
            val colNames = cursor.columnNames.toSet()
            val idIdx = cursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.C_ID)
            val timeIdx = cursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME)

            val latIdx = cursor.getColumnIndex(SensorType.LATITUDE.name)
            val lonIdx = cursor.getColumnIndex(SensorType.LONGITUDE.name)
            val speedIdx = cursor.getColumnIndex(SensorType.SPEED_mps.name)
            val bearingIdx = cursor.getColumnIndex(SensorType.BEARING.name)
            val accIdx = cursor.getColumnIndex(SensorType.ACCURACY.name)
            val yawRateIdx = cursor.getColumnIndex(SensorType.WORLD_YAW_RATE_radps.name)
            val altIdx = cursor.getColumnIndex(SensorType.ALTITUDE.name)

            // Look for reference altitude (DEM / GPS altitude device column)
            val demColName = colNames.firstOrNull { it.contains("DEM", ignoreCase = true) || it.contains("Topografische", ignoreCase = true) }
            val demIdx = if (demColName != null) cursor.getColumnIndex(demColName) else -1

            val gpsAltColName = colNames.firstOrNull { it.startsWith("ALTITUDE (GPS", ignoreCase = true) }
            val gpsAltIdx = if (gpsAltColName != null) cursor.getColumnIndex(gpsAltColName) else -1

            // Look for wheel speed
            val wheelSpeedCol = colNames.firstOrNull { it.contains("Rad", ignoreCase = true) || it.contains("Wheel", ignoreCase = true) }
            val wheelSpeedIdx = if (wheelSpeedCol != null) cursor.getColumnIndex(wheelSpeedCol) else -1

            var prevTs = 0L
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(idIdx)
                val timeStr = cursor.getString(timeIdx)
                val ts = parseTimestampMs(timeStr) ?: (if (prevTs > 0L) prevTs + 1000L else System.currentTimeMillis())
                prevTs = ts

                val lat = if (latIdx >= 0 && !cursor.isNull(latIdx)) cursor.getDouble(latIdx) else null
                val lon = if (lonIdx >= 0 && !cursor.isNull(lonIdx)) cursor.getDouble(lonIdx) else null
                val speed = if (speedIdx >= 0 && !cursor.isNull(speedIdx)) cursor.getDouble(speedIdx) else null
                val bearing = if (bearingIdx >= 0 && !cursor.isNull(bearingIdx)) cursor.getDouble(bearingIdx) else null
                val acc = if (accIdx >= 0 && !cursor.isNull(accIdx)) cursor.getDouble(accIdx) else null
                val yawRate = if (yawRateIdx >= 0 && !cursor.isNull(yawRateIdx)) cursor.getDouble(yawRateIdx) else null
                val rawAlt = if (altIdx >= 0 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else null

                val refAlt = when {
                    demIdx >= 0 && !cursor.isNull(demIdx) -> cursor.getDouble(demIdx)
                    gpsAltIdx >= 0 && !cursor.isNull(gpsAltIdx) -> cursor.getDouble(gpsAltIdx)
                    else -> null
                }

                val wheelSpeed = if (wheelSpeedIdx >= 0 && !cursor.isNull(wheelSpeedIdx)) cursor.getDouble(wheelSpeedIdx) else null

                result.add(
                    RawSampleRow(
                        rowId = rowId,
                        timestampMs = ts,
                        latitude = lat,
                        longitude = lon,
                        speedMps = speed,
                        bearingDeg = bearing,
                        accuracyM = acc,
                        yawRateRadPerSec = yawRate,
                        rawAltitude = rawAlt,
                        referenceAltitude = refAlt,
                        wheelSpeedMps = wheelSpeed
                    )
                )
            }
        }
        return result
    }

    private fun runOdometrySmoothing(rawRows: List<RawSampleRow>): List<SmoothedOdometryPoint> {
        val rawSamples = rawRows.map { row ->
            RawOdometrySample(
                timestampMs = row.timestampMs,
                latitude = row.latitude,
                longitude = row.longitude,
                speedMps = row.speedMps,
                bearingDeg = row.bearingDeg,
                accuracyMeters = row.accuracyM,
                worldYawRateRadPerSec = row.yawRateRadPerSec,
                wheelSpeedMps = row.wheelSpeedMps
            )
        }
        return ExtendedRtsOdometrySmoother.smoothSamples(rawSamples)
    }

    private fun runAltitudeSmoothing(
        rawRows: List<RawSampleRow>,
        smoothedOdometry: List<SmoothedOdometryPoint>?
    ): List<SmoothedAltitudeSlopePoint> {
        val rawAltSamples = rawRows.mapIndexed { idx, row ->
            val speed = smoothedOdometry?.getOrNull(idx)?.speedMps ?: row.speedMps ?: 0.0
            RawAltitudeSample(
                timestampMs = row.timestampMs,
                rawAltitudeM = row.rawAltitude ?: 0.0,
                referenceAltitudeM = row.referenceAltitude,
                smoothedSpeedMps = speed
            )
        }
        return ExtendedRtsAltitudeSlopeSmoother.smooth(rawAltSamples)
    }

    private fun updateSamplesDatabase(
        db: SQLiteDatabase,
        tableName: String,
        rawRows: List<RawSampleRow>,
        smoothedOdometry: List<SmoothedOdometryPoint>?,
        smoothedAltitude: List<SmoothedAltitudeSlopePoint>?
    ): Boolean {
        // Query column names of the table to update only existing columns
        val existingColumns = db.query(tableName, null, "1=0", null, null, null, null).use {
            it.columnNames.toSet()
        }

        val colsToUpdate = mutableListOf<String>()
        if (smoothedOdometry != null) {
            if (existingColumns.contains(SensorType.LATITUDE.name)) colsToUpdate.add(SensorType.LATITUDE.name)
            if (existingColumns.contains(SensorType.LONGITUDE.name)) colsToUpdate.add(SensorType.LONGITUDE.name)
            if (existingColumns.contains(SensorType.SPEED_mps.name)) colsToUpdate.add(SensorType.SPEED_mps.name)
            if (existingColumns.contains(SensorType.PACE_spm.name)) colsToUpdate.add(SensorType.PACE_spm.name)
            if (existingColumns.contains(SensorType.DISTANCE_m.name)) colsToUpdate.add(SensorType.DISTANCE_m.name)
            if (existingColumns.contains(SensorType.BEARING.name)) colsToUpdate.add(SensorType.BEARING.name)
        }
        if (smoothedAltitude != null) {
            if (existingColumns.contains(SensorType.ALTITUDE.name)) colsToUpdate.add(SensorType.ALTITUDE.name)
            if (existingColumns.contains(SensorType.SLOPE.name)) colsToUpdate.add(SensorType.SLOPE.name)
            if (existingColumns.contains(SensorType.VERTICAL_SPEED.name)) colsToUpdate.add(SensorType.VERTICAL_SPEED.name)
            if (existingColumns.contains(SensorType.ASCENT.name)) colsToUpdate.add(SensorType.ASCENT.name)
            if (existingColumns.contains(SensorType.DESCENT.name)) colsToUpdate.add(SensorType.DESCENT.name)
        }

        // Enforce strict canonical invariant: every column must be in whitelist and MUST NOT contain " ("
        for (col in colsToUpdate) {
            if (!ALLOWED_CANONICAL_COLUMNS.contains(col) || col.contains(" (")) {
                throw IllegalStateException("Strict Column Invariant Violation: attempt to update non-canonical column $col")
            }
        }

        if (colsToUpdate.isEmpty()) {
            return false
        }

        val sql = "UPDATE $tableName SET " + colsToUpdate.joinToString(", ") { "$it = ?" } +
            " WHERE ${WorkoutSamplesDbHelper.C_ID} = ?"

        var cumulativeDist = 0.0

        db.beginTransaction()
        try {
            val stmt: SQLiteStatement = db.compileStatement(sql)
            for (i in rawRows.indices) {
                val row = rawRows[i]
                val odom = smoothedOdometry?.getOrNull(i)
                val alt = smoothedAltitude?.getOrNull(i)

                if (odom != null) {
                    cumulativeDist += odom.deltaDistanceMeters
                }

                var bindIdx = 1
                for (col in colsToUpdate) {
                    bindColumnValue(stmt, bindIdx++, col, odom, alt, cumulativeDist, row)
                }
                stmt.bindLong(bindIdx, row.rowId)
                stmt.executeUpdateDelete()
            }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    internal fun bindColumnValue(
        stmt: SQLiteStatement,
        bindIdx: Int,
        columnName: String,
        odom: SmoothedOdometryPoint?,
        alt: SmoothedAltitudeSlopePoint?,
        cumulativeDist: Double,
        row: RawSampleRow
    ) {
        val hasRawFix = ExtendedRtsOdometrySmoother.hasValidGnssFix(row.latitude, row.longitude)
        when (columnName) {
            SensorType.LATITUDE.name -> {
                // REQ-FIL-019.3: Pre-fix / missing-fix rows preserve SQLite NULL; never populate (0.0, 0.0)
                if (odom != null && hasRawFix) stmt.bindDouble(bindIdx, odom.latitude) else stmt.bindNull(bindIdx)
            }
            SensorType.LONGITUDE.name -> {
                if (odom != null && hasRawFix) stmt.bindDouble(bindIdx, odom.longitude) else stmt.bindNull(bindIdx)
            }
            SensorType.SPEED_mps.name -> {
                if (odom != null) stmt.bindDouble(bindIdx, odom.speedMps) else stmt.bindNull(bindIdx)
            }
            SensorType.PACE_spm.name -> {
                if (odom != null && odom.speedMps > 0.1) {
                    stmt.bindDouble(bindIdx, 1.0 / odom.speedMps)
                } else {
                    stmt.bindNull(bindIdx)
                }
            }
            SensorType.DISTANCE_m.name -> {
                stmt.bindDouble(bindIdx, cumulativeDist)
            }
            SensorType.BEARING.name -> {
                if (odom != null && hasRawFix) stmt.bindDouble(bindIdx, odom.bearingDeg) else stmt.bindNull(bindIdx)
            }
            SensorType.ALTITUDE.name -> {
                if (alt != null) stmt.bindDouble(bindIdx, alt.altitudeM) else stmt.bindNull(bindIdx)
            }
            SensorType.SLOPE.name -> {
                if (alt != null) stmt.bindDouble(bindIdx, alt.slopePercent) else stmt.bindNull(bindIdx)
            }
            SensorType.VERTICAL_SPEED.name -> {
                if (alt != null && alt.verticalSpeedMps.isFinite()) {
                    val vamMph = Math.round((alt.verticalSpeedMps * 3600.0).coerceIn(-3000.0, 3000.0))
                    stmt.bindLong(bindIdx, vamMph)
                } else {
                    stmt.bindNull(bindIdx)
                }
            }
            SensorType.ASCENT.name -> {
                if (alt != null) stmt.bindDouble(bindIdx, alt.ascentM) else stmt.bindNull(bindIdx)
            }
            SensorType.DESCENT.name -> {
                if (alt != null) stmt.bindDouble(bindIdx, alt.descentM) else stmt.bindNull(bindIdx)
            }
            else -> {
                stmt.bindNull(bindIdx)
            }
        }
    }

    private fun updateWorkoutSummaries(
        context: Context,
        workoutId: Long,
        rawRows: List<RawSampleRow>,
        smoothedOdometry: List<SmoothedOdometryPoint>?,
        smoothedAltitude: List<SmoothedAltitudeSlopePoint>?
    ) {
        val summariesDbManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val summariesDb = summariesDbManager.database

        // Compute polyline and distance stream
        var polyline: String? = null
        var distanceStream: String? = null
        var totalDistance = 0.0

        if (smoothedOdometry != null && smoothedOdometry.isNotEmpty()) {
            // REQ-FIL-019.3: Exclude invalid/Null-Island coordinates from mapPolyline
            val validLatLngs = rawRows.indices.mapNotNull { i ->
                val row = rawRows[i]
                val odom = smoothedOdometry.getOrNull(i)
                if (odom != null && ExtendedRtsOdometrySmoother.hasValidGnssFix(row.latitude, row.longitude)) {
                    LatLng(odom.latitude, odom.longitude)
                } else null
            }
            if (validLatLngs.isNotEmpty()) {
                polyline = PolyUtil.encode(validLatLngs)
            }

            var cumDist = 0.0
            val distances = smoothedOdometry.map { pt ->
                cumDist += pt.deltaDistanceMeters
                cumDist
            }
            distanceStream = NumericalEncodingUtils.encodeDoubles(distances)
            totalDistance = cumDist
        }

        // Compute altitude stream, ascent, descent
        var altitudeStream: String? = null
        var totalAscent = 0.0
        var totalDescent = 0.0

        if (smoothedAltitude != null && smoothedAltitude.isNotEmpty()) {
            val altitudes = smoothedAltitude.map { it.altitudeM }
            altitudeStream = NumericalEncodingUtils.encodeDoubles(altitudes)
            totalAscent = smoothedAltitude.last().ascentM
            totalDescent = smoothedAltitude.last().descentM
        }

        // Update map and streams in WorkoutSummaries
        summariesDbManager.updateMapAndStreams(workoutId, polyline, altitudeStream, distanceStream)

        // Update session totals & extrema in WorkoutSummaries.TABLE
        val cv = ContentValues()
        if (smoothedOdometry != null) {
            cv.put(WorkoutSummaries.DISTANCE_TOTAL_m, totalDistance)
            val avgSpeed = smoothedOdometry.map { it.speedMps }.average()
            cv.put(WorkoutSummaries.SPEED_AVERAGE_mps, avgSpeed)
        }
        if (smoothedAltitude != null) {
            cv.put(WorkoutSummaries.ASCENDING, totalAscent)
            cv.put(WorkoutSummaries.DESCENDING, totalDescent)
        }

        if (cv.size() > 0) {
            summariesDb.update(WorkoutSummaries.TABLE, cv, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))
        }

        // Update extrema values
        updateExtrema(summariesDbManager, summariesDb, workoutId, rawRows, smoothedOdometry, smoothedAltitude)
    }

    private fun updateExtrema(
        summariesManager: WorkoutSummariesDatabaseManager,
        summariesDb: SQLiteDatabase,
        workoutId: Long,
        rawRows: List<RawSampleRow>,
        smoothedOdometry: List<SmoothedOdometryPoint>?,
        smoothedAltitude: List<SmoothedAltitudeSlopePoint>?
    ) {
        summariesDb.beginTransaction()
        try {
            if (smoothedOdometry != null && smoothedOdometry.isNotEmpty()) {
                val speeds = smoothedOdometry.map { it.speedMps }
                val maxSpeed = speeds.maxOrNull() ?: 0.0
                val avgSpeed = speeds.average()

                summariesManager.updateExtremaValue(summariesDb, workoutId, SensorType.SPEED_mps, ExtremaType.MAX, maxSpeed, null)
                summariesManager.updateExtremaValue(summariesDb, workoutId, SensorType.SPEED_mps, ExtremaType.AVG, avgSpeed, null)

                // REQ-FIL-019.3: Exclude Null Island / no-fix rows from START and END position extrema
                val validIndices = rawRows.indices.filter { i ->
                    ExtendedRtsOdometrySmoother.hasValidGnssFix(rawRows[i].latitude, rawRows[i].longitude)
                }
                if (validIndices.isNotEmpty()) {
                    val startPt = smoothedOdometry[validIndices.first()]
                    val endPt = smoothedOdometry[validIndices.last()]
                    val startPos = LatLng(startPt.latitude, startPt.longitude)
                    val endPos = LatLng(endPt.latitude, endPt.longitude)
                    summariesManager.updateExtremaValue(summariesDb, workoutId, SensorType.LATITUDE, ExtremaType.START, startPos.latitude, startPos)
                    summariesManager.updateExtremaValue(summariesDb, workoutId, SensorType.LONGITUDE, ExtremaType.START, startPos.longitude, startPos)
                    summariesManager.updateExtremaValue(summariesDb, workoutId, SensorType.LATITUDE, ExtremaType.END, endPos.latitude, endPos)
                    summariesManager.updateExtremaValue(summariesDb, workoutId, SensorType.LONGITUDE, ExtremaType.END, endPos.longitude, endPos)
                }
            }

            if (smoothedAltitude != null && smoothedAltitude.isNotEmpty()) {
                val alts = smoothedAltitude.map { it.altitudeM }
                val minAlt = alts.minOrNull() ?: 0.0
                val maxAlt = alts.maxOrNull() ?: 0.0
                val avgAlt = alts.average()

                summariesManager.updateExtremaValue(summariesDb, workoutId, SensorType.ALTITUDE, ExtremaType.MIN, minAlt, null)
                summariesManager.updateExtremaValue(summariesDb, workoutId, SensorType.ALTITUDE, ExtremaType.MAX, maxAlt, null)
                summariesManager.updateExtremaValue(summariesDb, workoutId, SensorType.ALTITUDE, ExtremaType.AVG, avgAlt, null)

                val slopes = smoothedAltitude.map { it.slopePercent }
                val minSlope = slopes.minOrNull() ?: 0.0
                val maxSlope = slopes.maxOrNull() ?: 0.0
                summariesManager.updateExtremaValue(summariesDb, workoutId, SensorType.SLOPE, ExtremaType.MIN, minSlope, null)
                summariesManager.updateExtremaValue(summariesDb, workoutId, SensorType.SLOPE, ExtremaType.MAX, maxSlope, null)
            }

            summariesDb.setTransactionSuccessful()
        } finally {
            summariesDb.endTransaction()
        }
    }

    /**
     * Enforces the post-workout ERTS output sanity guard (REQ-FIL-019.4).
     *
     * Verifies that smoothed odometry and altitude results are finite, plausible, and do not
     * exhibit non-physical divergence before committing to SQLite.
     */
    internal fun isSmoothedOutputSane(
        rawRows: List<RawSampleRow>,
        smoothedOdometry: List<SmoothedOdometryPoint>?,
        smoothedAltitude: List<SmoothedAltitudeSlopePoint>?
    ): Boolean {
        if (smoothedOdometry != null && !isOdometrySane(rawRows, smoothedOdometry)) {
            return false
        }
        if (smoothedAltitude != null && !isAltitudeSane(smoothedAltitude)) {
            return false
        }
        return true
    }

    private fun isNonFinite(vararg values: Double): Boolean = values.any { it.isNaN() || it.isInfinite() }

    private fun isOdometrySane(rawRows: List<RawSampleRow>, smoothedOdometry: List<SmoothedOdometryPoint>): Boolean {
        var cumulativeDist = 0.0
        for (pt in smoothedOdometry) {
            if (isNonFinite(pt.speedMps, pt.latitude, pt.longitude, pt.deltaDistanceMeters)) {
                Log.w(TAG, "Sanity check failed: Non-finite value in smoothed odometry point")
                return false
            }

            if (pt.speedMps > MAX_PLAUSIBLE_SPEED_MPS || pt.speedMps < 0.0) {
                Log.w(TAG, "Sanity check failed: Non-physical smoothed speed: ${pt.speedMps} m/s")
                return false
            }

            val inBounds = pt.latitude in -90.0..90.0 && pt.longitude in -180.0..180.0
            if (!inBounds) {
                Log.w(TAG, "Sanity check failed: Smoothed coordinates out of bounds: (${pt.latitude}, ${pt.longitude})")
                return false
            }

            cumulativeDist += pt.deltaDistanceMeters
        }

        if (cumulativeDist > MAX_PLAUSIBLE_WORKOUT_DISTANCE_M) {
            Log.w(TAG, "Sanity check failed: Total smoothed distance exceeds 1,000 km: $cumulativeDist m")
            return false
        }

        val rawSpeeds = rawRows.mapNotNull { it.speedMps }
        if (rawSpeeds.isNotEmpty()) {
            val rawAvgSpeed = rawSpeeds.average()
            val durationSec = (rawRows.last().timestampMs - rawRows.first().timestampMs) / 1000.0
            if (durationSec > 0.0 && rawAvgSpeed > 0.5) {
                val approxRawDist = rawAvgSpeed * durationSec
                if (approxRawDist > 1000.0 && cumulativeDist > approxRawDist * MAX_DISTANCE_DIVERGENCE_FACTOR) {
                    Log.w(
                        TAG,
                        "Sanity check failed: Smoothed distance ($cumulativeDist m) diverges > 3x from raw estimate"
                    )
                    return false
                }
            }
        }
        return true
    }

    private fun isAltitudeSane(smoothedAltitude: List<SmoothedAltitudeSlopePoint>): Boolean {
        for (alt in smoothedAltitude) {
            if (isNonFinite(alt.altitudeM, alt.slopePercent, alt.verticalSpeedMps, alt.ascentM, alt.descentM)) {
                Log.w(TAG, "Sanity check failed: Non-finite value in smoothed altitude point")
                return false
            }

            val altInBounds = alt.altitudeM in MIN_PLAUSIBLE_ALTITUDE_M..MAX_PLAUSIBLE_ALTITUDE_M
            if (!altInBounds) {
                Log.w(TAG, "Sanity check failed: Smoothed altitude out of physical bounds: ${alt.altitudeM} m")
                return false
            }

            if (alt.ascentM > MAX_PLAUSIBLE_ASCENT_M || alt.descentM > MAX_PLAUSIBLE_ASCENT_M) {
                Log.w(TAG, "Sanity check failed: Smoothed ascent/descent exceeds 20,000 m")
                return false
            }
        }
        return true
    }

    private fun parseTimestampMs(timeStr: String?): Long? {
        if (timeStr.isNullOrBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd_HHmmss"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(timeStr)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    internal data class RawSampleRow(
        val rowId: Long,
        val timestampMs: Long,
        val latitude: Double?,
        val longitude: Double?,
        val speedMps: Double?,
        val bearingDeg: Double?,
        val accuracyM: Double?,
        val yawRateRadPerSec: Double?,
        val rawAltitude: Double?,
        val referenceAltitude: Double?,
        val wheelSpeedMps: Double?
    )
}
