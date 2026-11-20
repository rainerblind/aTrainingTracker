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
import com.atrainingtracker.banalservice.devices.BarometricAltitudeFilter
import com.atrainingtracker.banalservice.odometry.ExtendedRtsAltitudeSlopeSmoother
import com.atrainingtracker.banalservice.odometry.ExtendedRtsOdometrySmoother
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.banalservice.odometry.RawAltitudeSample
import com.atrainingtracker.banalservice.odometry.RawOdometrySample
import com.atrainingtracker.banalservice.odometry.SmoothedAltitudeSlopePoint
import com.atrainingtracker.banalservice.odometry.SmoothedOdometryPoint
import com.atrainingtracker.banalservice.sensor.SensorType
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
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
        SensorType.DISTANCE_m_LAP.name,
        SensorType.BEARING.name,
        SensorType.ALTITUDE.name,
        SensorType.SLOPE.name,
        SensorType.VERTICAL_SPEED.name,
        SensorType.ASCENT.name,
        SensorType.DESCENT.name,
        SensorType.ACTIVITY_AND_CARRIAGE_MODE.name
    )

    private const val POLYLINE_SIMPLIFY_TOLERANCE_M = 10.0

    // 9-locale defaults for R.string.devices_altitude_from_pressure + ATT-5073 legacy names
    // (mirrors WorkoutSamplesDatabaseManager.resolveBarometricDeviceNames).
    private val BARO_DEVICE_NAME_TOKENS = listOf(
        "Altitude from Pressure", "Höhe aus Luftdruck", "Altitud por presión",
        "Altitude par pression", "Altitudine da pressione", "気圧からの高度",
        "Hoogte uit luchtdruk", "Wysokość z ciśnienia", "Altitude por pressão",
        "Barometer",
        "Air Pressure", "Luftdruck", "Presión Aire", "Pression Air",
        "Pressione Aria", "気圧", "Luchtdruk", "Ciśnienie pow.", "Pressão do Ar"
    )

    private fun isBarometricAltitudeColumn(colName: String): Boolean {
        if (!colName.startsWith("ALTITUDE (", ignoreCase = true)) return false
        if (colName.contains("DEM", ignoreCase = true) ||
            colName.contains("Topografische", ignoreCase = true) ||
            colName.contains("GPS", ignoreCase = true) ||
            colName.contains("Google Fused", ignoreCase = true) ||
            colName.contains("att_fused", ignoreCase = true)
        ) {
            return false
        }
        return BARO_DEVICE_NAME_TOKENS.any { token -> colName.contains(token, ignoreCase = true) }
    }

    private fun isWheelSpeedColumn(colName: String): Boolean {
        if (!colName.startsWith("${SensorType.SPEED_mps.name} (", ignoreCase = true)) return false
        if (colName.contains("RADAR", ignoreCase = true) ||
            colName.contains("YAW", ignoreCase = true) ||
            colName.contains("CADENCE", ignoreCase = true) ||
            colName.contains("ATT", ignoreCase = true) ||
            colName.contains("GPS", ignoreCase = true) ||
            colName.contains("Google", ignoreCase = true)
        ) {
            return false
        }
        return colName.contains("Rad", ignoreCase = true) ||
            colName.contains("Wheel", ignoreCase = true)
    }

    internal fun subsampleStream(values: List<Double>, stepSize: Int = WorkoutSummaries.ENCODING_STEP_SIZE): List<Double> {
        if (values.size <= 1 || stepSize <= 1) return values
        return values.filterIndexed { index, _ -> index % stepSize == 0 || index == values.lastIndex }
    }

    const val MAX_PLAUSIBLE_SPEED_MPS = 100.0 // 360 km/h
    const val MAX_PLAUSIBLE_WORKOUT_DISTANCE_M = 1_000_000.0 // 1,000 km
    const val MAX_DISTANCE_DIVERGENCE_FACTOR = 3.0
    const val MIN_DISTANCE_DIVERGENCE_FACTOR = 0.85
    const val MIN_RAW_DISTANCE_FOR_DIVERGENCE_CHECK_M = 500.0
    private const val MAX_ACTIVE_SAMPLE_GAP_SEC = 5.0
    private const val PAUSE_GAP_THRESHOLD_MS = 5_000L
    private const val PAUSE_TIME_TOTAL_GAP_SEC = 5L
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

            // 2. Run Kinematic Altitude & Slope ERTS Backward Smoother if altitude or reference present (REQ-FIL-033.3)
            val hasAltitudeData = rawRows.any { it.rawAltitude != null || it.referenceAltitude != null }
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
            val carriageModeIdx = cursor.getColumnIndex(SensorType.ACTIVITY_AND_CARRIAGE_MODE.name)
            val altIdx = cursor.getColumnIndex(SensorType.ALTITUDE.name)
            val distIdx = cursor.getColumnIndex(SensorType.DISTANCE_m.name)
            val lapNrIdx = cursor.getColumnIndex(SensorType.LAP_NR.name)
            val timeTotalIdx = cursor.getColumnIndex(SensorType.TIME_TOTAL.name)

            // Look for raw 9-locale barometer altitude column for slew/median pre-filtering (REQ-FIL-027.5)
            val baroAltColName = colNames.firstOrNull { isBarometricAltitudeColumn(it) }
            val baroAltIdx = if (baroAltColName != null) cursor.getColumnIndex(baroAltColName) else -1

            // Look for reference altitude (DEM / GPS altitude device column)
            val demColName = colNames.firstOrNull { it.contains("DEM", ignoreCase = true) || it.contains("Topografische", ignoreCase = true) }
            val demIdx = if (demColName != null) cursor.getColumnIndex(demColName) else -1

            val gpsAltColName = colNames.firstOrNull { it.startsWith("ALTITUDE (GPS", ignoreCase = true) }
            val gpsAltIdx = if (gpsAltColName != null) cursor.getColumnIndex(gpsAltColName) else -1

            // Look for wheel speed (strictly SPEED_mps device columns, never RADAR_THREAT_COUNT / WORLD_YAW_RATE_radps)
            val wheelSpeedCol = colNames.firstOrNull { isWheelSpeedColumn(it) }
            val wheelSpeedIdx = if (wheelSpeedCol != null) cursor.getColumnIndex(wheelSpeedCol) else -1

            val rawTimingPairs = mutableListOf<Pair<Long?, Long?>>()
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(idIdx)
                val timeStr = cursor.getString(timeIdx)
                val parsedTs = parseTimestampMs(timeStr)
                val timeTotalSec = if (timeTotalIdx >= 0 && !cursor.isNull(timeTotalIdx)) {
                    cursor.getLong(timeTotalIdx)
                } else {
                    null
                }
                rawTimingPairs.add(parsedTs to timeTotalSec)

                val lat = if (latIdx >= 0 && !cursor.isNull(latIdx)) cursor.getDouble(latIdx).takeIf { it.isFinite() } else null
                val lon = if (lonIdx >= 0 && !cursor.isNull(lonIdx)) cursor.getDouble(lonIdx).takeIf { it.isFinite() } else null
                val speed = if (speedIdx >= 0 && !cursor.isNull(speedIdx)) cursor.getDouble(speedIdx).takeIf { it.isFinite() } else null
                val bearing = if (bearingIdx >= 0 && !cursor.isNull(bearingIdx)) cursor.getDouble(bearingIdx).takeIf { it.isFinite() } else null
                val acc = if (accIdx >= 0 && !cursor.isNull(accIdx)) cursor.getDouble(accIdx).takeIf { it.isFinite() } else null
                val yawRate = if (yawRateIdx >= 0 && !cursor.isNull(yawRateIdx)) cursor.getDouble(yawRateIdx).takeIf { it.isFinite() } else null
                val recordedCarriageMode = if (carriageModeIdx >= 0 && !cursor.isNull(carriageModeIdx)) {
                    PhoneCarriageMode.fromCode(cursor.getInt(carriageModeIdx))
                } else {
                    null
                }
                val rawBaroAlt = if (baroAltIdx >= 0 && !cursor.isNull(baroAltIdx)) {
                    cursor.getDouble(baroAltIdx).takeIf { it.isFinite() }
                } else {
                    null
                }
                val canonicalAlt = if (altIdx >= 0 && !cursor.isNull(altIdx)) {
                    cursor.getDouble(altIdx).takeIf { it.isFinite() }
                } else {
                    null
                }
                val rawDist = if (distIdx >= 0 && !cursor.isNull(distIdx)) cursor.getDouble(distIdx).takeIf { it.isFinite() } else null
                val lapNr = if (lapNrIdx >= 0 && !cursor.isNull(lapNrIdx)) cursor.getLong(lapNrIdx) else null

                val refAlt = when {
                    demIdx >= 0 && !cursor.isNull(demIdx) -> cursor.getDouble(demIdx).takeIf { it.isFinite() }
                    gpsAltIdx >= 0 && !cursor.isNull(gpsAltIdx) -> cursor.getDouble(gpsAltIdx).takeIf { it.isFinite() }
                    else -> null
                }

                val wheelSpeed = if (wheelSpeedIdx >= 0 && !cursor.isNull(wheelSpeedIdx)) cursor.getDouble(wheelSpeedIdx).takeIf { it.isFinite() } else null

                result.add(
                    RawSampleRow(
                        rowId = rowId,
                        timestampMs = 0L,
                        latitude = lat,
                        longitude = lon,
                        speedMps = speed,
                        bearingDeg = bearing,
                        accuracyM = acc,
                        yawRateRadPerSec = yawRate,
                        rawAltitude = canonicalAlt,
                        referenceAltitude = refAlt,
                        wheelSpeedMps = wheelSpeed,
                        rawDistanceM = rawDist,
                        lapNr = lapNr,
                        recordedCarriageMode = recordedCarriageMode,
                        rawBaroAltitude = rawBaroAlt
                    )
                )
            }
            // Reconstruct beat-immune monotonic 1-Hz timestamps BEFORE barometer/ERTS filtering (REQ-FIL-027.1, REQ-FIL-032.1)
            val reconstructedTs = reconstructTimestamps(rawTimingPairs)
            for (i in result.indices) {
                result[i] = result[i].copy(timestampMs = reconstructedTs[i])
            }
        }
        return resolveCarriageModesAndFilterBaro(result)
    }

    /**
     * Reconstructs monotonic 1-Hz sample timestamps in milliseconds from `(parsedWallTsMs, timeTotalSec)`
     * pairs without ratcheting on asynchronous `ClockDevice`/`TrackerService` `TIME_TOTAL` `{0, 2}`
     * clock-beat pairs in any ordering, while preserving genuine `> 5 s` wall-clock or `TIME_TOTAL`
     * pauses and `<= 6 s` SQLite batch-flush stalls (`REQ-FIL-027.1`, `REQ-FIL-032.1`).
     */
    internal fun reconstructTimestamps(
        samples: List<Pair<Long?, Long?>>,
        fallbackStartMs: Long = System.currentTimeMillis()
    ): LongArray {
        val result = LongArray(samples.size)
        var prevTs = 0L
        var prevTimeTotalSec: Long? = null
        var prevTimeTotalIdx = -1
        for (i in samples.indices) {
            val (parsedTs, timeTotalSec) = samples[i]
            val ts = if (i == 0) {
                parsedTs ?: fallbackStartMs
            } else {
                val oneHzTs = prevTs + 1000L
                val wallPauseTs = if (parsedTs != null && (parsedTs - oneHzTs) > PAUSE_GAP_THRESHOLD_MS) {
                    parsedTs
                } else {
                    null
                }
                val timeTotalPauseTs = if (timeTotalSec != null && prevTimeTotalSec != null) {
                    val extraGapSec = (timeTotalSec - prevTimeTotalSec) - (i - prevTimeTotalIdx).toLong()
                    if (extraGapSec >= PAUSE_TIME_TOTAL_GAP_SEC) {
                        oneHzTs + extraGapSec * 1000L
                    } else {
                        null
                    }
                } else {
                    null
                }
                when {
                    wallPauseTs != null && timeTotalPauseTs != null -> max(wallPauseTs, timeTotalPauseTs)
                    wallPauseTs != null -> wallPauseTs
                    timeTotalPauseTs != null -> timeTotalPauseTs
                    else -> oneHzTs
                }
            }
            result[i] = ts
            prevTs = ts
            if (timeTotalSec != null) {
                prevTimeTotalSec = timeTotalSec
                prevTimeTotalIdx = i
            } else if (i > 0 && ts > result[i - 1] + 1000L) {
                // Re-anchor TIME_TOTAL reference after a wall-clock pause on a null TIME_TOTAL row
                prevTimeTotalSec = null
                prevTimeTotalIdx = -1
            }
        }
        return result
    }

    /**
     * Resolves recorded 1-Hz [PhoneCarriageMode] entries (falling back to deterministic 1-Hz kinematic
     * and barometric-jitter inference on legacy DBs) and pre-filters raw barometer columns using
     * each epoch's adaptive median/slew parameters (REQ-FIL-028.4).
     */
    internal fun resolveCarriageModesAndFilterBaro(rows: List<RawSampleRow>): List<RawSampleRow> {
        if (rows.isEmpty()) return rows
        val baroFilter = BarometricAltitudeFilter()
        val n = rows.size
        val resolved = ArrayList<RawSampleRow>(n)
        for (i in 0 until n) {
            val row = rows[i]
            val mode = row.recordedCarriageMode?.takeIf { it != PhoneCarriageMode.UNKNOWN }
                ?: inferLegacyCarriageModeAt(rows, i)
            val epochSpeedMps = row.wheelSpeedMps?.takeIf { it.isFinite() && it >= 0.0 }
                ?: row.speedMps?.takeIf { it.isFinite() && it >= 0.0 }
                ?: 0.0
            val filteredAlt = when {
                row.rawBaroAltitude != null -> {
                    baroFilter.setFilterParameters(mode.profile.baroMaxSlewRateMps, mode.profile.baroMedianWindowSize)
                    baroFilter.filterSlewOnly(row.rawBaroAltitude, row.timestampMs, epochSpeedMps).takeIf { it.isFinite() }
                }
                else -> row.rawAltitude
            }
            resolved.add(
                row.copy(
                    rawAltitude = filteredAlt,
                    effectiveCarriageMode = mode
                )
            )
        }
        return resolved
    }

    internal fun inferLegacyCarriageModeAt(rows: List<RawSampleRow>, idx: Int): PhoneCarriageMode {
        val row = rows[idx]
        val speed = row.speedMps?.takeIf { it.isFinite() }
        val wheel = row.wheelSpeedMps?.takeIf { it.isFinite() }

        if (speed != null && speed <= 0.05 && (wheel == null || wheel <= 0.05)) {
            return PhoneCarriageMode.STATIONARY
        }

        val lo = max(0, idx - 3)
        val hi = min(rows.size - 1, idx + 3)
        var yawSumSq = 0.0
        var yawCount = 0
        val baros = mutableListOf<Pair<Long, Double>>()
        for (j in lo..hi) {
            if (abs(rows[j].timestampMs - row.timestampMs) > 4_000L) continue
            val y = rows[j].yawRateRadPerSec
            if (y != null && y.isFinite()) {
                yawSumSq += y * y
                yawCount++
            }
            val b = rows[j].rawBaroAltitude ?: rows[j].rawAltitude
            if (b != null && b.isFinite()) {
                baros.add(rows[j].timestampMs to b)
            }
        }
        val yawRms = if (yawCount > 0) sqrt(yawSumSq / yawCount) else null
        var baroSecondDiffSq = 0.0
        var baroDiffCount = 0
        for (k in 2 until baros.size) {
            val dt1 = (baros[k - 1].first - baros[k - 2].first) / 1000.0
            val dt2 = (baros[k].first - baros[k - 1].first) / 1000.0
            if (dt1 in 0.5..3.0 && dt2 in 0.5..3.0) {
                val vz1 = (baros[k - 1].second - baros[k - 2].second) / dt1
                val vz2 = (baros[k].second - baros[k - 1].second) / dt2
                val d2 = vz2 - vz1
                baroSecondDiffSq += d2 * d2
                baroDiffCount++
            }
        }
        val baroJitterRms = if (baroDiffCount > 0) sqrt(baroSecondDiffSq / baroDiffCount) else null

        if (wheel == null && speed != null && speed in 0.35..2.05 &&
            (yawRms == null || yawRms in 0.08..0.45) && (baroJitterRms == null || baroJitterRms < 0.65)
        ) {
            return PhoneCarriageMode.WALKING_TRANSITION
        }

        val isMovingCycling = (wheel != null && wheel > 0.5) || (speed != null && speed > 2.2)
        if (isMovingCycling) {
            if ((yawRms != null && yawRms >= 0.35) || (baroJitterRms != null && baroJitterRms >= 0.65)) {
                return PhoneCarriageMode.BIKE_FRONT_POCKET
            }
            if ((yawRms != null && yawRms in 0.08..0.28) || (baroJitterRms != null && baroJitterRms in 0.22..0.50)) {
                return PhoneCarriageMode.BIKE_JERSEY_BACK
            }
            if (yawRms != null && yawRms < 0.06 && (baroJitterRms == null || baroJitterRms < 0.16)) {
                return PhoneCarriageMode.BIKE_HANDLEBAR
            }
        }
        return PhoneCarriageMode.UNKNOWN
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
                wheelSpeedMps = row.wheelSpeedMps,
                carriageMode = row.effectiveCarriageMode
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
                rawAltitudeM = row.rawAltitude ?: Double.NaN,
                referenceAltitudeM = row.referenceAltitude,
                smoothedSpeedMps = speed,
                carriageMode = row.effectiveCarriageMode
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
            if (existingColumns.contains(SensorType.DISTANCE_m_LAP.name)) colsToUpdate.add(SensorType.DISTANCE_m_LAP.name)
            if (existingColumns.contains(SensorType.BEARING.name)) colsToUpdate.add(SensorType.BEARING.name)
        }
        if (smoothedAltitude != null) {
            if (existingColumns.contains(SensorType.ALTITUDE.name)) colsToUpdate.add(SensorType.ALTITUDE.name)
            if (existingColumns.contains(SensorType.SLOPE.name)) colsToUpdate.add(SensorType.SLOPE.name)
            if (existingColumns.contains(SensorType.VERTICAL_SPEED.name)) colsToUpdate.add(SensorType.VERTICAL_SPEED.name)
            if (existingColumns.contains(SensorType.ASCENT.name)) colsToUpdate.add(SensorType.ASCENT.name)
            if (existingColumns.contains(SensorType.DESCENT.name)) colsToUpdate.add(SensorType.DESCENT.name)
        }
        if ((smoothedOdometry != null || smoothedAltitude != null) &&
            existingColumns.contains(SensorType.ACTIVITY_AND_CARRIAGE_MODE.name)
        ) {
            colsToUpdate.add(SensorType.ACTIVITY_AND_CARRIAGE_MODE.name)
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
        var lapCumulativeDist = 0.0
        var currentLapNr: Long? = null

        db.beginTransaction()
        try {
            val stmt: SQLiteStatement = db.compileStatement(sql)
            for (i in rawRows.indices) {
                val row = rawRows[i]
                val odom = smoothedOdometry?.getOrNull(i)
                val alt = smoothedAltitude?.getOrNull(i)

                if (row.lapNr != null) {
                    if (currentLapNr != null && row.lapNr != currentLapNr) {
                        lapCumulativeDist = 0.0
                    }
                    currentLapNr = row.lapNr
                }

                if (odom != null) {
                    cumulativeDist += odom.deltaDistanceMeters
                    lapCumulativeDist += odom.deltaDistanceMeters
                }

                var bindIdx = 1
                for (col in colsToUpdate) {
                    bindColumnValue(stmt, bindIdx++, col, odom, alt, cumulativeDist, row, lapCumulativeDist)
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
        row: RawSampleRow,
        lapCumulativeDist: Double = cumulativeDist
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
            SensorType.DISTANCE_m_LAP.name -> {
                stmt.bindDouble(bindIdx, lapCumulativeDist)
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
            SensorType.ACTIVITY_AND_CARRIAGE_MODE.name -> {
                val persistedMode = row.recordedCarriageMode
                    ?.takeIf { it != PhoneCarriageMode.UNKNOWN }
                    ?: row.effectiveCarriageMode
                stmt.bindLong(bindIdx, persistedMode.code.toLong())
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
                polyline = PolyUtil.encode(PolyUtil.simplify(validLatLngs, POLYLINE_SIMPLIFY_TOLERANCE_M))
            }

            var cumDist = 0.0
            val distances = smoothedOdometry.map { pt ->
                cumDist += pt.deltaDistanceMeters
                cumDist
            }
            distanceStream = NumericalEncodingUtils.encodeDoubles(subsampleStream(distances))
            totalDistance = cumDist
        }

        // Compute altitude stream, ascent, descent
        var altitudeStream: String? = null
        var totalAscent = 0.0
        var totalDescent = 0.0

        if (smoothedAltitude != null && smoothedAltitude.isNotEmpty()) {
            val altitudes = smoothedAltitude.map { it.altitudeM }
            altitudeStream = NumericalEncodingUtils.encodeDoubles(subsampleStream(altitudes))
            totalAscent = smoothedAltitude.last().ascentM
            totalDescent = smoothedAltitude.last().descentM
        }

        // Update map and streams in WorkoutSummaries
        summariesDbManager.updateMapAndStreams(workoutId, polyline, altitudeStream, distanceStream)

        // Update session totals & extrema in WorkoutSummaries.TABLE
        val authoritativeAvgSpeed = if (smoothedOdometry != null && smoothedOdometry.isNotEmpty()) {
            val timeActiveSec = summariesDbManager.getInt(workoutId, WorkoutSummaries.TIME_ACTIVE_s)
            computeAuthoritativeAverageSpeedMps(totalDistance, timeActiveSec, smoothedOdometry)
        } else {
            null
        }
        val cv = ContentValues()
        if (smoothedOdometry != null) {
            cv.put(WorkoutSummaries.DISTANCE_TOTAL_m, totalDistance)
            if (authoritativeAvgSpeed != null) {
                cv.put(WorkoutSummaries.SPEED_AVERAGE_mps, authoritativeAvgSpeed)
            }
        }
        if (smoothedAltitude != null) {
            cv.put(WorkoutSummaries.ASCENDING, totalAscent)
            cv.put(WorkoutSummaries.DESCENDING, totalDescent)
        }

        if (cv.size() > 0) {
            summariesDb.update(WorkoutSummaries.TABLE, cv, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))
        }

        // Update extrema values
        updateExtrema(
            summariesDbManager,
            summariesDb,
            workoutId,
            rawRows,
            smoothedOdometry,
            smoothedAltitude,
            authoritativeAvgSpeed
        )

        // REQ-FIL-025.4: Keep Laps.db synchronized with smoothed WorkoutSummaries.db totals
        if (smoothedOdometry != null && smoothedOdometry.isNotEmpty()) {
            val smoothedLapDistances = mutableMapOf<Long, Double>()
            for (i in rawRows.indices) {
                val lapNr = rawRows[i].lapNr
                val pt = smoothedOdometry.getOrNull(i)
                if (lapNr != null && lapNr > 0L && pt != null) {
                    smoothedLapDistances[lapNr] = (smoothedLapDistances[lapNr] ?: 0.0) + pt.deltaDistanceMeters
                }
            }
            LapsDatabaseManager.getInstance(context).syncLapDistancesAndSpeeds(
                workoutId,
                smoothedLapDistances,
                totalDistance
            )
        }
    }

    /**
     * Resolves one authoritative finite non-negative workout average speed in m/s shared across
     * `WorkoutSummaries.SPEED_AVERAGE_mps` and `ExtremumValues(SPEED_mps, AVG)` (`REQ-FIL-032.2`).
     *
     * Precedence:
     * 1. `totalDistanceM / timeActiveSec` when persisted `WorkoutSummaries.TIME_ACTIVE_s > 0`
     *    (matching `TrackerService.getAverageSpeed()` and `LapsDatabaseManager.syncLapDistancesAndSpeeds()`).
     * 2. Matched active-step ratio `activeDist / activeDt` summed strictly over consecutive samples
     *    with `dt in (0.0, MAX_ACTIVE_SAMPLE_GAP_SEC]` (preventing pause-gap `deltaDistanceMeters`
     *    inflation when `TIME_ACTIVE_s` is `NULL` or `<= 0`).
     * 3. Finite non-negative unweighted mean of finite `speedMps` values (`0.0` if empty/non-finite).
     */
    internal fun computeAuthoritativeAverageSpeedMps(
        totalDistanceM: Double,
        timeActiveSec: Int?,
        smoothedOdometry: List<SmoothedOdometryPoint>
    ): Double {
        if (timeActiveSec != null && timeActiveSec > 0 && totalDistanceM.isFinite() && totalDistanceM >= 0.0) {
            val speed = totalDistanceM / timeActiveSec.toDouble()
            if (speed.isFinite() && speed >= 0.0) return speed
        }

        if (smoothedOdometry.size >= 2) {
            var activeDistM = 0.0
            var activeDtSec = 0.0
            for (i in 1 until smoothedOdometry.size) {
                val dtSec = (smoothedOdometry[i].timestampMs - smoothedOdometry[i - 1].timestampMs) / 1000.0
                if (dtSec > 0.0 && dtSec <= MAX_ACTIVE_SAMPLE_GAP_SEC) {
                    val stepDist = smoothedOdometry[i].deltaDistanceMeters
                    if (stepDist.isFinite() && stepDist >= 0.0) {
                        activeDistM += stepDist
                        activeDtSec += dtSec
                    }
                }
            }
            if (activeDtSec > 0.0) {
                val matchedSpeed = activeDistM / activeDtSec
                if (matchedSpeed.isFinite() && matchedSpeed >= 0.0) return matchedSpeed
            }
        }

        var finiteSum = 0.0
        var finiteCount = 0
        for (pt in smoothedOdometry) {
            if (pt.speedMps.isFinite() && pt.speedMps >= 0.0) {
                finiteSum += pt.speedMps
                finiteCount++
            }
        }
        return if (finiteCount > 0) finiteSum / finiteCount else 0.0
    }

    private fun updateExtrema(
        summariesManager: WorkoutSummariesDatabaseManager,
        summariesDb: SQLiteDatabase,
        workoutId: Long,
        rawRows: List<RawSampleRow>,
        smoothedOdometry: List<SmoothedOdometryPoint>?,
        smoothedAltitude: List<SmoothedAltitudeSlopePoint>?,
        authoritativeAvgSpeed: Double? = null
    ) {
        summariesDb.beginTransaction()
        try {
            if (smoothedOdometry != null && smoothedOdometry.isNotEmpty()) {
                val speeds = smoothedOdometry.map { it.speedMps }
                val maxSpeed = speeds.maxOrNull() ?: 0.0
                val avgSpeed = authoritativeAvgSpeed
                    ?: computeAuthoritativeAverageSpeedMps(
                        totalDistanceM = smoothedOdometry.sumOf { it.deltaDistanceMeters },
                        timeActiveSec = null,
                        smoothedOdometry = smoothedOdometry
                    )

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

        val rawRefDist = estimateRawReferenceDistanceMeters(rawRows)
        if (rawRefDist != null && rawRefDist >= MIN_RAW_DISTANCE_FOR_DIVERGENCE_CHECK_M) {
            if (cumulativeDist > rawRefDist * MAX_DISTANCE_DIVERGENCE_FACTOR) {
                Log.w(
                    TAG,
                    "Sanity check failed: Smoothed distance ($cumulativeDist m) diverges > ${MAX_DISTANCE_DIVERGENCE_FACTOR}x from raw estimate ($rawRefDist m)"
                )
                return false
            }
            if (cumulativeDist < rawRefDist * MIN_DISTANCE_DIVERGENCE_FACTOR) {
                Log.w(
                    TAG,
                    "Sanity check failed: Smoothed distance ($cumulativeDist m) collapsed < ${MIN_DISTANCE_DIVERGENCE_FACTOR}x of raw estimate ($rawRefDist m)"
                )
                return false
            }
        }
        return true
    }

    /**
     * Estimates raw reference distance in meters from `DISTANCE_m` delta when populated, or via
     * pause-aware step-by-step speed integration over active gaps (`<= 5 s`) so pauses do not
     * inflate expected distance (REQ-FIL-025.3, ATT-8705).
     */
    private fun estimateRawReferenceDistanceMeters(rawRows: List<RawSampleRow>): Double? {
        if (rawRows.size < 2) return null

        val validDistances = rawRows.mapNotNull { row ->
            row.rawDistanceM?.takeIf { it.isFinite() && it >= 0.0 }
        }
        if (validDistances.size >= 2) {
            val deltaDist = validDistances.last() - validDistances.first()
            if (deltaDist > 0.0) {
                return deltaDist
            }
        }

        var integratedDist = 0.0
        var hasValidSpeed = false
        for (i in 1 until rawRows.size) {
            val dtSec = (rawRows[i].timestampMs - rawRows[i - 1].timestampMs) / 1000.0
            if (dtSec > 0.0 && dtSec <= MAX_ACTIVE_SAMPLE_GAP_SEC) {
                val speed = rawRows[i].speedMps ?: rawRows[i - 1].speedMps
                if (speed != null && speed.isFinite() && speed > 0.0) {
                    integratedDist += speed * dtSec
                    hasValidSpeed = true
                }
            }
        }
        return if (hasValidSpeed && integratedDist > 0.0) integratedDist else null
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
        val wheelSpeedMps: Double?,
        val rawDistanceM: Double? = null,
        val lapNr: Long? = null,
        val recordedCarriageMode: PhoneCarriageMode? = null,
        val effectiveCarriageMode: PhoneCarriageMode = PhoneCarriageMode.UNKNOWN,
        val rawBaroAltitude: Double? = null
    )
}
