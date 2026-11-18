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
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.banalservice.odometry.SmoothedAltitudeSlopePoint
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * TST-FIL-017.3: Strict column scope invariant & database recomputation test (REQ-FIL-017.3, ATT-7750).
 */
@RunWith(RobolectricTestRunner::class)
class ErtsPostWorkoutRecomputerTest {

    private lateinit var context: Context
    private lateinit var samplesDbManager: WorkoutSamplesDatabaseManager
    private lateinit var summariesDbManager: WorkoutSummariesDatabaseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        LapsDatabaseManager.resetForTesting(null)
        samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
        summariesDbManager = WorkoutSummariesDatabaseManager.getInstance(context)
    }

    @org.junit.After
    fun tearDown() {
        LapsDatabaseManager.resetForTesting(null)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
    }

    private fun createWorkout(baseFileName: String): Long {
        val cv = ContentValues().apply {
            put(WorkoutSummaries.FILE_BASE_NAME, baseFileName)
            put(WorkoutSummaries.TIME_START, "2026-11-14 10:00:00")
            put(WorkoutSummaries.TIME_TOTAL_s, 60.0)
            put(WorkoutSummaries.SPORT_ID, 1L)
        }
        return summariesDbManager.database.insertOrThrow(WorkoutSummaries.TABLE, null, cv)
    }

    @Test
    fun testStrictColumnScopeInvariantAndRecomputation() = runBlocking {
        val baseFileName = "2026-11-14_100000"
        val workoutId = createWorkout(baseFileName)

        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

        // 1. Create table with canonical columns
        val canonicalTypes = listOf(
            SensorType.LATITUDE,
            SensorType.LONGITUDE,
            SensorType.SPEED_mps,
            SensorType.PACE_spm,
            SensorType.DISTANCE_m,
            SensorType.BEARING,
            SensorType.ALTITUDE,
            SensorType.SLOPE,
            SensorType.VERTICAL_SPEED,
            SensorType.ASCENT,
            SensorType.DESCENT
        )
        samplesDbManager.createNewTable(baseFileName, canonicalTypes)

        // 2. Add 6 device-attributed columns
        val deviceColumns = listOf(
            "\"LATITUDE (GPS)\" REAL",
            "\"SPEED_mps (GPS)\" REAL",
            "\"SPEED_mps (Google Fused)\" REAL",
            "\"SPEED_mps (att_fused)\" REAL",
            "\"ALTITUDE (Höhe aus Luftdruck)\" REAL",
            "\"ALTITUDE (Topografische Höhe (DEM))\" REAL"
        )
        for (colDef in deviceColumns) {
            samplesDb.execSQL("ALTER TABLE $tableName ADD COLUMN $colDef")
        }

        // 3. Populate 30 seconds of trackpoint samples
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTs = 1_700_000_000_000L

        val initialDeviceValues = mutableMapOf<String, MutableList<Double>>()
        for (col in listOf("LATITUDE (GPS)", "SPEED_mps (GPS)", "SPEED_mps (Google Fused)", "SPEED_mps (att_fused)", "ALTITUDE (Höhe aus Luftdruck)", "ALTITUDE (Topografische Höhe (DEM))")) {
            initialDeviceValues[col] = mutableListOf()
        }

        samplesDb.beginTransaction()
        try {
            for (sec in 0 until 30) {
                val cv = ContentValues().apply {
                    put("time", sdf.format(Date(startTs + sec * 1000L)))
                    put(SensorType.LATITUDE.name, 48.1370 + sec * 0.0001)
                    put(SensorType.LONGITUDE.name, 11.5760 + sec * 0.0001)
                    put(SensorType.SPEED_mps.name, 5.0)
                    put(SensorType.PACE_spm.name, 0.2)
                    put(SensorType.DISTANCE_m.name, sec * 5.0)
                    put(SensorType.BEARING.name, 45.0)
                    put(SensorType.ALTITUDE.name, 500.0 + sec * 0.2)
                    put(SensorType.SLOPE.name, 4.0)
                    put(SensorType.VERTICAL_SPEED.name, 1.0)
                    put(SensorType.ASCENT.name, sec * 0.2)
                    put(SensorType.DESCENT.name, 0.0)

                    // Device columns with distinct raw values
                    val latGps = 48.13705 + sec * 0.00011
                    val spdGps = 5.12 + sec * 0.01
                    val spdFused = 5.05 + sec * 0.01
                    val spdAtt = 5.08 + sec * 0.01
                    val altBaro = 510.0 + sec * 0.25
                    val altDem = 500.0 + sec * 0.20

                    put("\"LATITUDE (GPS)\"", latGps)
                    put("\"SPEED_mps (GPS)\"", spdGps)
                    put("\"SPEED_mps (Google Fused)\"", spdFused)
                    put("\"SPEED_mps (att_fused)\"", spdAtt)
                    put("\"ALTITUDE (Höhe aus Luftdruck)\"", altBaro)
                    put("\"ALTITUDE (Topografische Höhe (DEM))\"", altDem)

                    initialDeviceValues["LATITUDE (GPS)"]?.add(latGps)
                    initialDeviceValues["SPEED_mps (GPS)"]?.add(spdGps)
                    initialDeviceValues["SPEED_mps (Google Fused)"]?.add(spdFused)
                    initialDeviceValues["SPEED_mps (att_fused)"]?.add(spdAtt)
                    initialDeviceValues["ALTITUDE (Höhe aus Luftdruck)"]?.add(altBaro)
                    initialDeviceValues["ALTITUDE (Topografische Höhe (DEM))"]?.add(altDem)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        // 4. Run ERTS recomputation
        val success = ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertTrue("Recomputation should succeed", success)

        // 5. Assert all device-attributed columns are byte-for-byte IDENTICAL
        samplesDb.query(tableName, null, null, null, null, null, "_id ASC").use { cursor ->
            var rowIdx = 0
            while (cursor.moveToNext()) {
                for (col in listOf("LATITUDE (GPS)", "SPEED_mps (GPS)", "SPEED_mps (Google Fused)", "SPEED_mps (att_fused)", "ALTITUDE (Höhe aus Luftdruck)", "ALTITUDE (Topografische Höhe (DEM))")) {
                    val colIdx = cursor.getColumnIndexOrThrow(col)
                    val expectedVal = initialDeviceValues[col]!![rowIdx]
                    val actualVal = cursor.getDouble(colIdx)
                    assertEquals("Device column $col at row $rowIdx must remain 100% untouched", expectedVal, actualVal, 1e-9)
                }

                // 6. Assert canonical columns have valid smoothed values
                val lat = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name))
                val lon = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name))
                val spd = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.SPEED_mps.name))
                val dist = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.DISTANCE_m.name))
                val alt = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.ALTITUDE.name))
                val vamMph = cursor.getLong(cursor.getColumnIndexOrThrow(SensorType.VERTICAL_SPEED.name))

                assertTrue("Smoothed latitude valid", lat > 40.0)
                assertTrue("Smoothed longitude valid", lon > 10.0)
                assertTrue("Smoothed speed valid", spd > 0.0)
                assertTrue("Smoothed distance non-negative", dist >= 0.0)
                assertTrue("Smoothed altitude valid", alt > 400.0)
                if (rowIdx in 5..25) {
                    assertTrue("Smoothed VERTICAL_SPEED must be in m/h (~720 m/h), was $vamMph", vamMph in 400L..1100L)
                }

                rowIdx++
            }
            assertEquals(30, rowIdx)
        }

        // 7. Assert WorkoutSummaries.db map, streams, distance, and extrema were updated
        summariesDbManager.database.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val polyline = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.MAP_POLYLINE))
            val altStream = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.ALTITUDE_STREAM))
            val distStream = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_STREAM))
            val totalDist = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m))

            assertNotNull("Polyline should be populated", polyline)
            assertTrue("Polyline not empty", polyline.isNotEmpty())
            val decodedPoints = PolyUtil.decode(polyline)
            assertEquals("Collinear polyline simplifies via PolyUtil.simplify(..., 10.0) to 2 endpoints", 2, decodedPoints.size)

            assertNotNull("Altitude stream populated", altStream)
            assertNotNull("Distance stream populated", distStream)
            assertEquals(3, NumericalEncodingUtils.decodeDoubles(altStream).size)
            assertEquals(3, NumericalEncodingUtils.decodeDoubles(distStream).size)
            assertTrue("Total distance > 0", totalDist > 0.0)
        }
    }

    // TST-FIL-019.3: Leading/trailing NULL preservation & map polyline exclusion test
    @Test
    fun testLeadingNullCoordinatesPreservedInDbAndExcludedFromPolyline() = runBlocking {
        val baseFileName = "2026-11-16_060310"
        val workoutId = createWorkout(baseFileName)

        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

        val canonicalTypes = listOf(
            SensorType.LATITUDE,
            SensorType.LONGITUDE,
            SensorType.SPEED_mps,
            SensorType.PACE_spm,
            SensorType.DISTANCE_m,
            SensorType.BEARING,
            SensorType.ALTITUDE,
            SensorType.SLOPE,
            SensorType.VERTICAL_SPEED,
            SensorType.ASCENT,
            SensorType.DESCENT
        )
        samplesDbManager.createNewTable(baseFileName, canonicalTypes)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTs = 1_700_000_100_000L

        // 30 rows: first 5 have NULL GPS coordinates, next 25 have valid fixes at Schönaich
        samplesDb.beginTransaction()
        try {
            for (sec in 0 until 30) {
                val cv = ContentValues().apply {
                    put("time", sdf.format(Date(startTs + sec * 1000L)))
                    if (sec < 5) {
                        putNull(SensorType.LATITUDE.name)
                        putNull(SensorType.LONGITUDE.name)
                        putNull(SensorType.BEARING.name)
                        put(SensorType.SPEED_mps.name, 0.0)
                    } else {
                        put(SensorType.LATITUDE.name, 48.6567 + (sec - 5) * 0.0001)
                        put(SensorType.LONGITUDE.name, 9.0545 + (sec - 5) * 0.0001)
                        put(SensorType.BEARING.name, 90.0)
                        put(SensorType.SPEED_mps.name, 5.0)
                    }
                    put(SensorType.DISTANCE_m.name, if (sec < 5) 0.0 else (sec - 5) * 5.0)
                    put(SensorType.ALTITUDE.name, 500.0)
                    put(SensorType.SLOPE.name, 0.0)
                    put(SensorType.VERTICAL_SPEED.name, 0.0)
                    put(SensorType.ASCENT.name, 0.0)
                    put(SensorType.DESCENT.name, 0.0)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        val success = ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertTrue("Recomputation should succeed", success)

        // Verify WorkoutSamples.db: rows 0..4 MUST preserve SQLite NULL coordinates!
        samplesDb.query(tableName, null, null, null, null, null, "_id ASC").use { cursor ->
            val latIdx = cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name)
            val lonIdx = cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name)

            var rowIdx = 0
            while (cursor.moveToNext()) {
                if (rowIdx < 5) {
                    assertTrue("Row $rowIdx LATITUDE must be NULL in SQLite", cursor.isNull(latIdx))
                    assertTrue("Row $rowIdx LONGITUDE must be NULL in SQLite", cursor.isNull(lonIdx))
                } else {
                    assertTrue("Row $rowIdx LATITUDE must not be null", !cursor.isNull(latIdx))
                    assertTrue("Row $rowIdx LONGITUDE must not be null", !cursor.isNull(lonIdx))
                    val lat = cursor.getDouble(latIdx)
                    val lon = cursor.getDouble(lonIdx)
                    assertTrue("Latitude must be near Schönaich (was $lat)", kotlin.math.abs(lat - 48.6567) < 0.01)
                    assertTrue("Longitude must be near Schönaich (was $lon)", kotlin.math.abs(lon - 9.0545) < 0.01)
                }
                rowIdx++
            }
            assertEquals(30, rowIdx)
        }

        // Verify WorkoutSummaries.db: polyline must exclude the 5 leading no-fix rows (size == 25)
        summariesDbManager.database.query(
            WorkoutSummaries.TABLE,
            null,
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val polyline = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.MAP_POLYLINE))
            assertNotNull("Polyline must exist", polyline)
            val decodedPoints = PolyUtil.decode(polyline)
            assertTrue(
                "25-fix polyline must exclude 5 no-fix rows and simplify via PolyUtil.simplify(..., 10.0) to <= 3 points (was ${decodedPoints.size})",
                decodedPoints.size in 2..3
            )
            for (pt in decodedPoints) {
                assertTrue(
                    "Polyline point must not be near Null Island (was ${pt.latitude}, ${pt.longitude})",
                    pt.latitude > 40.0
                )
            }
        }
    }

    // TST-FIL-019.4: Output sanity guard anomaly abort test
    @Test
    fun testOutputSanityGuardAbortsCorruptedOutputAndPreservesLiveData() {
        val corruptedOdom = listOf(
            com.atrainingtracker.banalservice.odometry.SmoothedOdometryPoint(
                timestampMs = 1000L,
                eastMeters = 0.0,
                northMeters = 0.0,
                speedMps = 720_735.0, // Non-physical speed shock!
                headingRad = 0.0,
                gyroBiasRadPerSec = 0.0,
                latitude = 48.0,
                longitude = 11.0,
                bearingDeg = 0.0,
                deltaDistanceMeters = 720_735.0
            )
        )

        val isSane = ErtsPostWorkoutRecomputer.isSmoothedOutputSane(emptyList(), corruptedOdom, null)
        org.junit.Assert.assertFalse("Sanity guard must reject speed > 100 m/s", isSane)

        // Non-finite value check
        val nanOdom = listOf(
            com.atrainingtracker.banalservice.odometry.SmoothedOdometryPoint(
                timestampMs = 1000L,
                eastMeters = 0.0,
                northMeters = 0.0,
                speedMps = Double.NaN,
                headingRad = 0.0,
                gyroBiasRadPerSec = 0.0,
                latitude = 48.0,
                longitude = 11.0,
                bearingDeg = 0.0,
                deltaDistanceMeters = 10.0
            )
        )
        val isNanSane = ErtsPostWorkoutRecomputer.isSmoothedOutputSane(emptyList(), nanOdom, null)
        org.junit.Assert.assertFalse("Sanity guard must reject NaN values", isNanSane)

        // Distance > 1,000 km check
        val hugeDistOdom = listOf(
            com.atrainingtracker.banalservice.odometry.SmoothedOdometryPoint(
                timestampMs = 1000L,
                eastMeters = 0.0,
                northMeters = 0.0,
                speedMps = 20.0,
                headingRad = 0.0,
                gyroBiasRadPerSec = 0.0,
                latitude = 48.0,
                longitude = 11.0,
                bearingDeg = 0.0,
                deltaDistanceMeters = 1_500_000.0 // 1,500 km
            )
        )
        val isHugeDistSane = ErtsPostWorkoutRecomputer.isSmoothedOutputSane(emptyList(), hugeDistOdom, null)
        org.junit.Assert.assertFalse("Sanity guard must reject distance > 1,000 km", isHugeDistSane)
    }

    // TST-FIL-021.1: Positive climb & negative descent m/h conversion with untouched device columns
    @Test
    fun testVerticalSpeedRecomputation_convertsClimbAndDescentToIntegerMphAndPreservesDeviceColumns() = runBlocking {
        val baseFileName = "2026-11-17_120000"
        val workoutId = createWorkout(baseFileName)

        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        samplesDbManager.createNewTable(
            baseFileName,
            listOf(
                SensorType.LATITUDE,
                SensorType.LONGITUDE,
                SensorType.SPEED_mps,
                SensorType.PACE_spm,
                SensorType.DISTANCE_m,
                SensorType.BEARING,
                SensorType.ALTITUDE,
                SensorType.SLOPE,
                SensorType.VERTICAL_SPEED,
                SensorType.ASCENT,
                SensorType.DESCENT
            )
        )
        samplesDb.execSQL("ALTER TABLE $tableName ADD COLUMN \"ALTITUDE (Topografische Höhe (DEM))\" REAL")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTs = 1_700_000_200_000L
        val expectedDem = mutableListOf<Double>()

        samplesDb.beginTransaction()
        try {
            // 0..19: climb +0.25 m/s (+900 m/h); 20..39: descent -0.25 m/s (-900 m/h)
            for (sec in 0 until 40) {
                val alt = if (sec < 20) 500.0 + sec * 0.25 else 500.0 + 19 * 0.25 - (sec - 19) * 0.25
                expectedDem.add(alt)
                val cv = ContentValues().apply {
                    put("time", sdf.format(Date(startTs + sec * 1000L)))
                    put(SensorType.LATITUDE.name, 48.6567 + sec * 0.0001)
                    put(SensorType.LONGITUDE.name, 9.0545 + sec * 0.0001)
                    put(SensorType.SPEED_mps.name, 5.0)
                    put(SensorType.PACE_spm.name, 0.2)
                    put(SensorType.DISTANCE_m.name, sec * 5.0)
                    put(SensorType.BEARING.name, 45.0)
                    put(SensorType.ALTITUDE.name, alt)
                    put(SensorType.SLOPE.name, if (sec < 20) 5.0 else -5.0)
                    put(SensorType.VERTICAL_SPEED.name, 0)
                    put(SensorType.ASCENT.name, 0.0)
                    put(SensorType.DESCENT.name, 0.0)
                    put("\"ALTITUDE (Topografische Höhe (DEM))\"", alt)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        val success = ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertTrue("Recomputation should succeed", success)

        val climbVams = mutableListOf<Long>()
        val descentVams = mutableListOf<Long>()
        samplesDb.query(tableName, null, null, null, null, null, "_id ASC").use { cursor ->
            val vamIdx = cursor.getColumnIndexOrThrow(SensorType.VERTICAL_SPEED.name)
            val demIdx = cursor.getColumnIndexOrThrow("ALTITUDE (Topografische Höhe (DEM))")
            var rowIdx = 0
            while (cursor.moveToNext()) {
                assertEquals(
                    "Device DEM column must remain untouched at row $rowIdx",
                    expectedDem[rowIdx],
                    cursor.getDouble(demIdx),
                    1e-9
                )
                assertEquals(
                    "VERTICAL_SPEED must be persisted as SQLite INTEGER",
                    android.database.Cursor.FIELD_TYPE_INTEGER,
                    cursor.getType(vamIdx)
                )
                val vam = cursor.getLong(vamIdx)
                if (rowIdx in 5..14) climbVams.add(vam)
                if (rowIdx in 25..34) descentVams.add(vam)
                rowIdx++
            }
            assertEquals(40, rowIdx)
        }

        assertTrue("Climb VAM should be positive m/h (~900), was $climbVams", climbVams.all { it in 500L..1300L })
        assertTrue("Descent VAM should be negative m/h (~-900), was $descentVams", descentVams.all { it in -1300L..-500L })
    }

    // TST-FIL-021.1 & TST-FIL-021.2: Exact scaling, rounding, [-3000, 3000] clamping & NULL safety
    @Test
    fun testBindColumnValue_verticalSpeedScalesRoundsClampsAndHandlesNull() {
        val baseFileName = "2026-11-17_120500"
        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        samplesDbManager.createNewTable(baseFileName, listOf(SensorType.VERTICAL_SPEED))

        val cases = listOf(
            0.25 to 900L,      // exact positive climb
            -0.25 to -900L,    // exact negative descent
            0.0004 to 1L,      // 1.44 -> 1
            -0.0004 to -1L,    // -1.44 -> -1
            0.0007 to 3L,      // 2.52 -> 3
            -0.0007 to -3L,    // -2.52 -> -3
            3.0 to 3000L,      // +10800 m/h clamped to +3000 m/h
            -3.0 to -3000L,    // -10800 m/h clamped to -3000 m/h
            Double.NaN to null,// non-finite -> SQLite NULL
            null to null       // alt == null -> SQLite NULL
        )

        val dummyRow = ErtsPostWorkoutRecomputer.RawSampleRow(
            rowId = 1L,
            timestampMs = 1000L,
            latitude = null,
            longitude = null,
            speedMps = null,
            bearingDeg = null,
            accuracyM = null,
            yawRateRadPerSec = null,
            rawAltitude = null,
            referenceAltitude = null,
            wheelSpeedMps = null
        )

        val insertStmt = samplesDb.compileStatement(
            "INSERT INTO $tableName (${SensorType.VERTICAL_SPEED.name}) VALUES (?)"
        )
        for ((mps, _) in cases) {
            val altPt = mps?.let {
                SmoothedAltitudeSlopePoint(
                    timestampMs = 1000L,
                    altitudeM = 500.0,
                    slopePercent = 0.0,
                    verticalSpeedMps = it,
                    ascentM = 0.0,
                    descentM = 0.0
                )
            }
            ErtsPostWorkoutRecomputer.bindColumnValue(
                stmt = insertStmt,
                bindIdx = 1,
                columnName = SensorType.VERTICAL_SPEED.name,
                odom = null,
                alt = altPt,
                cumulativeDist = 0.0,
                row = dummyRow
            )
            insertStmt.executeInsert()
        }

        samplesDb.query(tableName, arrayOf(SensorType.VERTICAL_SPEED.name), null, null, null, null, "_id ASC").use { cursor ->
            var idx = 0
            while (cursor.moveToNext()) {
                val (inputMps, expectedMph) = cases[idx]
                if (expectedMph == null) {
                    assertTrue("Case $idx (mps=$inputMps) must bind SQLite NULL", cursor.isNull(0))
                } else {
                    assertEquals(
                        "Case $idx (mps=$inputMps) must store SQLite INTEGER",
                        android.database.Cursor.FIELD_TYPE_INTEGER,
                        cursor.getType(0)
                    )
                    assertEquals("Case $idx (mps=$inputMps) mismatch", expectedMph, cursor.getLong(0))
                }
                idx++
            }
            assertEquals(cases.size, idx)
        }
    }

    // TST-FIL-025.3: Symmetric lower/upper distance divergence bounds & pause-aware sanity check
    @Test
    fun testSymmetricSanityBounds_rejectsCollapsedOutputAndAllowsPausedWorkout_tstFil025_3() {
        // Build 500s of raw motion at 2.0 m/s (= 1,000 m raw distance) with a 600s timestamp pause in the middle
        val rawRows = mutableListOf<ErtsPostWorkoutRecomputer.RawSampleRow>()
        var ts = 1_700_000_000_000L
        var rawDist = 0.0
        for (i in 0 until 500) {
            if (i == 250) {
                ts += 600_000L // 10-minute pause gap
            }
            rawRows.add(
                ErtsPostWorkoutRecomputer.RawSampleRow(
                    rowId = i.toLong() + 1L,
                    timestampMs = ts,
                    latitude = 48.1370,
                    longitude = 11.5760,
                    speedMps = 2.0,
                    bearingDeg = 180.0,
                    accuracyM = 4.0,
                    yawRateRadPerSec = 0.0,
                    rawAltitude = 500.0,
                    referenceAltitude = 500.0,
                    wheelSpeedMps = null,
                    rawDistanceM = rawDist,
                    lapNr = 1L
                )
            )
            ts += 1000L
            rawDist += 2.0
        }

        // Collapsed ERTS output (800 m vs 998 m raw -> ~0.80x < 0.85x) MUST be rejected
        val collapsedOdom = rawRows.mapIndexed { idx, row ->
            com.atrainingtracker.banalservice.odometry.SmoothedOdometryPoint(
                timestampMs = row.timestampMs,
                eastMeters = 0.0,
                northMeters = -idx * 1.6,
                speedMps = 1.6,
                headingRad = -kotlin.math.PI / 2.0,
                gyroBiasRadPerSec = 0.0,
                latitude = 48.1370,
                longitude = 11.5760,
                bearingDeg = 180.0,
                deltaDistanceMeters = if (idx == 0) 0.0 else 1.6
            )
        }
        org.junit.Assert.assertFalse(
            "Sanity guard must reject collapsed distance (< 0.85x)",
            ErtsPostWorkoutRecomputer.isSmoothedOutputSane(rawRows, collapsedOdom, null)
        )

        // Paused workout matching ~1,000 m raw distance MUST be accepted despite 600s pause
        val validPausedOdom = rawRows.mapIndexed { idx, row ->
            com.atrainingtracker.banalservice.odometry.SmoothedOdometryPoint(
                timestampMs = row.timestampMs,
                eastMeters = 0.0,
                northMeters = -idx * 2.0,
                speedMps = 2.0,
                headingRad = -kotlin.math.PI / 2.0,
                gyroBiasRadPerSec = 0.0,
                latitude = 48.1370,
                longitude = 11.5760,
                bearingDeg = 180.0,
                deltaDistanceMeters = if (idx == 0) 0.0 else 2.0
            )
        }
        assertTrue(
            "Pause-aware sanity guard must allow valid paused workout",
            ErtsPostWorkoutRecomputer.isSmoothedOutputSane(rawRows, validPausedOdom, null)
        )
    }

    // TST-FIL-025.4: Multi-lap ERTS recomputation synchronizes Laps.db with WorkoutSummaries.db
    @Test
    fun testRecomputeWorkout_synchronizesLapsDbWithSmoothedLapSegments_tstFil025_4() = runBlocking {
        val baseFileName = "2026-11-18_001000"
        val workoutId = createWorkout(baseFileName)

        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        samplesDbManager.createNewTable(
            baseFileName,
            listOf(
                SensorType.LATITUDE,
                SensorType.LONGITUDE,
                SensorType.SPEED_mps,
                SensorType.PACE_spm,
                SensorType.DISTANCE_m,
                SensorType.BEARING,
                SensorType.ALTITUDE,
                SensorType.SLOPE,
                SensorType.VERTICAL_SPEED,
                SensorType.ASCENT,
                SensorType.DESCENT,
                SensorType.LAP_NR
            )
        )

        val lapsDbManager = LapsDatabaseManager.getInstance(context)
        lapsDbManager.saveLap(workoutId, 1L, "2026-11-18 00:10:00", 30, 999.0, 33.3, "Lap One", "Warmup")
        lapsDbManager.saveLap(workoutId, 2L, "2026-11-18 00:10:30", 30, 888.0, 29.6, "Lap Two", "Tempo")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTs = 1_700_000_300_000L
        val originLat = 48.137154
        val originLon = 11.576124
        val speed = 2.2
        val latStepDeg = Math.toDegrees(-speed / 6371000.0) // Moving South

        samplesDb.beginTransaction()
        try {
            for (sec in 0 until 60) {
                val lapNr = if (sec < 30) 1L else 2L
                val cv = ContentValues().apply {
                    put("time", sdf.format(Date(startTs + sec * 1000L)))
                    put(SensorType.LATITUDE.name, originLat + sec * latStepDeg)
                    put(SensorType.LONGITUDE.name, originLon)
                    put(SensorType.SPEED_mps.name, speed)
                    put(SensorType.PACE_spm.name, 1.0 / speed)
                    put(SensorType.DISTANCE_m.name, sec * speed)
                    put(SensorType.BEARING.name, 180.0)
                    put(SensorType.ALTITUDE.name, 500.0)
                    put(SensorType.SLOPE.name, 0.0)
                    put(SensorType.VERTICAL_SPEED.name, 0)
                    put(SensorType.ASCENT.name, 0.0)
                    put(SensorType.DESCENT.name, 0.0)
                    put(SensorType.LAP_NR.name, lapNr)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        val success = ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertTrue("Recomputation must succeed", success)

        var summaryTotalDist = 0.0
        summariesDbManager.database.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.DISTANCE_TOTAL_m),
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            summaryTotalDist = cursor.getDouble(0)
        }

        val syncedLaps = lapsDbManager.getLaps(workoutId)
        assertEquals(2, syncedLaps.size)
        val lapSumDist = syncedLaps.sumOf { it.distanceTotalM }
        assertEquals(
            "Sum of Laps.db distances must match WorkoutSummaries.DISTANCE_TOTAL_m",
            summaryTotalDist,
            lapSumDist,
            1e-3
        )
        assertEquals("Lap 1 name must be preserved", "Lap One", syncedLaps[0].name)
        assertEquals("Lap 2 description must be preserved", "Tempo", syncedLaps[1].description)
        assertEquals(syncedLaps[0].distanceTotalM / 30.0, syncedLaps[0].speedAverageMps, 1e-6)
        assertEquals(syncedLaps[1].distanceTotalM / 30.0, syncedLaps[1].speedAverageMps, 1e-6)

        // Verify proportional fallback when smoothedLapDistancesM is empty
        lapsDbManager.syncLapDistancesAndSpeeds(workoutId, emptyMap(), 200.0)
        val fallbackLaps = lapsDbManager.getLaps(workoutId)
        assertEquals(200.0, fallbackLaps.sumOf { it.distanceTotalM }, 1e-3)
    }

    // TST-FIL-027.1: TIME_TOTAL monotonic timestamp reconstruction eliminates dt=0/2s quantization & 0 m/h VAM drops
    @Test
    fun testTimeTotalReconstructsMonotonicTimestamps_andEliminatesZeroVamDrops_tstFil027_1() = runBlocking {
        val baseFileName = "2026-11-18_002000"
        val workoutId = createWorkout(baseFileName)

        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        samplesDbManager.createNewTable(
            baseFileName,
            listOf(
                SensorType.TIME_TOTAL,
                SensorType.LATITUDE,
                SensorType.LONGITUDE,
                SensorType.SPEED_mps,
                SensorType.PACE_spm,
                SensorType.DISTANCE_m,
                SensorType.BEARING,
                SensorType.ALTITUDE,
                SensorType.SLOPE,
                SensorType.VERTICAL_SPEED,
                SensorType.ASCENT,
                SensorType.DESCENT
            )
        )

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTs = 1_700_000_500_000L
        val originLat = 48.6567
        val originLon = 9.0545
        val speed = 6.0
        val lonStepDeg = Math.toDegrees(speed / (6371000.0 * kotlin.math.cos(Math.toRadians(originLat))))

        samplesDb.beginTransaction()
        try {
            for (sec in 0 until 40) {
                // Inject dt=0s / dt=2s batch-flush alternation on even/odd pairs while TIME_TOTAL is monotonic
                val quantizedSec = if (sec % 2 == 1) sec - 1 else sec
                val cv = ContentValues().apply {
                    put("time", sdf.format(Date(startTs + quantizedSec * 1000L)))
                    put(SensorType.TIME_TOTAL.name, sec.toLong())
                    put(SensorType.LATITUDE.name, originLat)
                    put(SensorType.LONGITUDE.name, originLon + sec * lonStepDeg)
                    put(SensorType.SPEED_mps.name, speed)
                    put(SensorType.PACE_spm.name, 1.0 / speed)
                    put(SensorType.DISTANCE_m.name, sec * speed)
                    put(SensorType.BEARING.name, 90.0)
                    put(SensorType.ALTITUDE.name, 450.0 + sec * 0.3)
                    put(SensorType.SLOPE.name, 5.0)
                    put(SensorType.VERTICAL_SPEED.name, 0)
                    put(SensorType.ASCENT.name, sec * 0.3)
                    put(SensorType.DESCENT.name, 0.0)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        val success = ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertTrue("Recomputation must succeed with TIME_TOTAL reconstruction", success)

        samplesDb.query(tableName, null, null, null, null, null, "_id ASC").use { cursor ->
            val distIdx = cursor.getColumnIndexOrThrow(SensorType.DISTANCE_m.name)
            val vamIdx = cursor.getColumnIndexOrThrow(SensorType.VERTICAL_SPEED.name)
            var rowIdx = 0
            var prevDist = -1.0
            while (cursor.moveToNext()) {
                val dist = cursor.getDouble(distIdx)
                val vam = cursor.getLong(vamIdx)
                if (rowIdx > 0) {
                    assertTrue("DISTANCE_m froze at row $rowIdx ($prevDist -> $dist)", dist > prevDist + 1.0)
                }
                if (rowIdx in 5 until 38) {
                    assertTrue("VERTICAL_SPEED dropped to 0 m/h at row $rowIdx (was $vam)", vam > 500L)
                }
                prevDist = dist
                rowIdx++
            }
            assertEquals(40, rowIdx)
        }
    }

    // TST-FIL-027.4: DISTANCE_m_LAP sync with LAP_NR transitions & ENCODING_STEP_SIZE stream subsampling
    @Test
    fun testDistanceMLapSyncAndStreamSubsamplingParity_tstFil027_4() = runBlocking {
        val baseFileName = "2026-11-18_003000"
        val workoutId = createWorkout(baseFileName)

        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        samplesDbManager.createNewTable(
            baseFileName,
            listOf(
                SensorType.TIME_TOTAL,
                SensorType.LATITUDE,
                SensorType.LONGITUDE,
                SensorType.SPEED_mps,
                SensorType.PACE_spm,
                SensorType.DISTANCE_m,
                SensorType.DISTANCE_m_LAP,
                SensorType.BEARING,
                SensorType.ALTITUDE,
                SensorType.SLOPE,
                SensorType.VERTICAL_SPEED,
                SensorType.ASCENT,
                SensorType.DESCENT,
                SensorType.LAP_NR
            )
        )

        val lapsDbManager = LapsDatabaseManager.getInstance(context)
        lapsDbManager.saveLap(workoutId, 1L, "2026-11-18 00:30:00", 30, 100.0, 3.3, "Lap 1", "")
        lapsDbManager.saveLap(workoutId, 2L, "2026-11-18 00:30:30", 30, 100.0, 3.3, "Lap 2", "")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTs = 1_700_000_600_000L
        val originLat = 48.6567
        val originLon = 9.0545
        val speed = 5.0
        val latStepDeg = Math.toDegrees(speed / 6371000.0)

        samplesDb.beginTransaction()
        try {
            for (sec in 0 until 60) {
                val lapNr = if (sec < 30) 1L else 2L
                val cv = ContentValues().apply {
                    put("time", sdf.format(Date(startTs + sec * 1000L)))
                    put(SensorType.TIME_TOTAL.name, sec.toLong())
                    put(SensorType.LATITUDE.name, originLat + sec * latStepDeg)
                    put(SensorType.LONGITUDE.name, originLon)
                    put(SensorType.SPEED_mps.name, speed)
                    put(SensorType.PACE_spm.name, 1.0 / speed)
                    put(SensorType.DISTANCE_m.name, sec * speed)
                    put(SensorType.DISTANCE_m_LAP.name, 0.0)
                    put(SensorType.BEARING.name, 0.0)
                    put(SensorType.ALTITUDE.name, 440.0 + sec * 0.2)
                    put(SensorType.SLOPE.name, 4.0)
                    put(SensorType.VERTICAL_SPEED.name, 720)
                    put(SensorType.ASCENT.name, sec * 0.2)
                    put(SensorType.DESCENT.name, 0.0)
                    put(SensorType.LAP_NR.name, lapNr)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        val success = ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertTrue(success)

        val laps = lapsDbManager.getLaps(workoutId)
        assertEquals(2, laps.size)

        var lap1LastDist = 0.0
        var lap2FirstDist = -1.0
        var lap2LastDist = 0.0
        samplesDb.query(tableName, arrayOf(SensorType.LAP_NR.name, SensorType.DISTANCE_m_LAP.name), null, null, null, null, "_id ASC").use { cursor ->
            var rowIdx = 0
            while (cursor.moveToNext()) {
                val lapNr = cursor.getLong(0)
                val lapDist = cursor.getDouble(1)
                if (lapNr == 1L) lap1LastDist = lapDist
                if (rowIdx == 30) lap2FirstDist = lapDist
                if (lapNr == 2L) lap2LastDist = lapDist
                rowIdx++
            }
        }
        assertTrue("DISTANCE_m_LAP must reset at Lap 2 boundary (was $lap2FirstDist vs lap1End $lap1LastDist)", lap2FirstDist < 10.0)
        assertEquals("Lap 1 final DISTANCE_m_LAP must match Laps.db", laps[0].distanceTotalM, lap1LastDist, 1e-3)
        assertEquals("Lap 2 final DISTANCE_m_LAP must match Laps.db", laps[1].distanceTotalM, lap2LastDist, 1e-3)

        summariesDbManager.database.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.MAP_POLYLINE, WorkoutSummaries.DISTANCE_STREAM, WorkoutSummaries.ALTITUDE_STREAM),
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val polylinePoints = PolyUtil.decode(cursor.getString(0))
            assertEquals("60-sample collinear route simplifies via PolyUtil.simplify(..., 10.0) to 2 endpoints", 2, polylinePoints.size)
            val distPoints = NumericalEncodingUtils.decodeDoubles(cursor.getString(1))
            val altPoints = NumericalEncodingUtils.decodeDoubles(cursor.getString(2))
            assertEquals("60-sample stream subsampled by ENCODING_STEP_SIZE=20 yields 4 points (0,20,40,59)", 4, distPoints.size)
            assertEquals(4, altPoints.size)
        }

        // Verify <20-sample subsampling retains [first, last] and 1-sample retains [0]
        val shortList = (0 until 10).map { it.toDouble() }
        assertEquals(listOf(0.0, 9.0), ErtsPostWorkoutRecomputer.subsampleStream(shortList))
        assertEquals(listOf(42.0), ErtsPostWorkoutRecomputer.subsampleStream(listOf(42.0)))
    }

    // TST-FIL-027.5: 9-locale raw barometer column pre-filtered via BarometricAltitudeFilter.filterSlewOnly()
    @Test
    fun testNineLocaleBarometerPreFilteredViaSlewOnly_andRawPreserved_tstFil027_5() = runBlocking {
        val baseFileName = "2026-11-18_004000"
        val workoutId = createWorkout(baseFileName)

        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        samplesDbManager.createNewTable(
            baseFileName,
            listOf(
                SensorType.TIME_TOTAL,
                SensorType.LATITUDE,
                SensorType.LONGITUDE,
                SensorType.SPEED_mps,
                SensorType.PACE_spm,
                SensorType.DISTANCE_m,
                SensorType.BEARING,
                SensorType.ALTITUDE,
                SensorType.SLOPE,
                SensorType.VERTICAL_SPEED,
                SensorType.ASCENT,
                SensorType.DESCENT
            )
        )
        // Add English-locale barometer column + DEM reference column
        val baroCol = "ALTITUDE (Altitude from Pressure)"
        val demCol = "ALTITUDE (Topografische Höhe (DEM))"
        samplesDb.execSQL("ALTER TABLE $tableName ADD COLUMN \"$baroCol\" REAL")
        samplesDb.execSQL("ALTER TABLE $tableName ADD COLUMN \"$demCol\" REAL")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTs = 1_700_000_700_000L
        val rawBaroExpected = mutableListOf<Double>()

        samplesDb.beginTransaction()
        try {
            for (sec in 0 until 60) {
                // Flat 438.0 m ride with jeans-pocket compression spike (-8.05 m / +6.87 m)
                val baroVal = when (sec) {
                    20 -> 438.0 - 8.05
                    21 -> 438.0 + 6.87
                    40 -> 438.0 - 7.50
                    else -> 438.0
                }
                rawBaroExpected.add(baroVal)
                val cv = ContentValues().apply {
                    put("time", sdf.format(Date(startTs + sec * 1000L)))
                    put(SensorType.TIME_TOTAL.name, sec.toLong())
                    put(SensorType.LATITUDE.name, 48.6567 + sec * 0.00005)
                    put(SensorType.LONGITUDE.name, 9.0545)
                    put(SensorType.SPEED_mps.name, 5.5)
                    put(SensorType.PACE_spm.name, 1.0 / 5.5)
                    put(SensorType.DISTANCE_m.name, sec * 5.5)
                    put(SensorType.BEARING.name, 0.0)
                    put(SensorType.ALTITUDE.name, 438.0)
                    put(SensorType.SLOPE.name, 0.0)
                    put(SensorType.VERTICAL_SPEED.name, 0)
                    put(SensorType.ASCENT.name, 0.0)
                    put(SensorType.DESCENT.name, 0.0)
                    put("\"$baroCol\"", baroVal)
                    put("\"$demCol\"", 438.0)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        val success = ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertTrue(success)

        samplesDb.query(tableName, arrayOf("\"$baroCol\"", SensorType.ASCENT.name, SensorType.DESCENT.name), null, null, null, null, "_id ASC").use { cursor ->
            var rowIdx = 0
            var lastAscent = 0.0
            var lastDescent = 0.0
            while (cursor.moveToNext()) {
                assertEquals("Raw 9-locale baro column untouched at $rowIdx", rawBaroExpected[rowIdx], cursor.getDouble(0), 1e-9)
                lastAscent = cursor.getDouble(1)
                lastDescent = cursor.getDouble(2)
                rowIdx++
            }
            assertEquals("Flat ride with pocket spikes must yield 0.0 m ascent", 0.0, lastAscent, 0.01)
            assertEquals("Flat ride with pocket spikes must yield 0.0 m descent", 0.0, lastDescent, 0.01)
        }
    }

    // TST-FIL-027.3 (Bounce 1/3): RADAR_THREAT_COUNT / WORLD_YAW_RATE_radps must NOT match wheel speed, and NaN wheelSpeed must NOT bypass ZUPT or poison EKF
    @Test
    fun testRadarAndYawColumnsNotMatchedAsWheelSpeed_andNaNSanitized_tstFil027_3() = runBlocking {
        val baseFileName = "2026-11-18_005000"
        val workoutId = createWorkout(baseFileName)

        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        samplesDbManager.createNewTable(
            baseFileName,
            listOf(
                SensorType.TIME_TOTAL,
                SensorType.LATITUDE,
                SensorType.LONGITUDE,
                SensorType.SPEED_mps,
                SensorType.PACE_spm,
                SensorType.DISTANCE_m,
                SensorType.BEARING,
                SensorType.ALTITUDE,
                SensorType.SLOPE,
                SensorType.VERTICAL_SPEED,
                SensorType.ASCENT,
                SensorType.DESCENT,
                SensorType.WORLD_YAW_RATE_radps,
                SensorType.RADAR_THREAT_COUNT
            )
        )
        val wheelCol = "SPEED_mps (Wheel Sensor)"
        samplesDb.execSQL("ALTER TABLE $tableName ADD COLUMN \"$wheelCol\" REAL")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTs = 1_700_000_800_000L
        var lat = 48.6567
        val lon = 9.0545
        val latStepDeg = Math.toDegrees(6.0 / 6371000.0)

        samplesDb.beginTransaction()
        try {
            for (sec in 0 until 60) {
                val isStop = sec in 20..39
                if (!isStop && sec > 0) {
                    lat += latStepDeg
                }
                val speed = if (isStop) 0.0 else 6.0
                val acc = if (isStop) 50.0 else 3.0
                val cv = ContentValues().apply {
                    put("time", sdf.format(Date(startTs + sec * 1000L)))
                    put(SensorType.TIME_TOTAL.name, sec.toLong())
                    put(SensorType.LATITUDE.name, lat)
                    put(SensorType.LONGITUDE.name, lon)
                    put(SensorType.SPEED_mps.name, speed)
                    put(SensorType.PACE_spm.name, if (speed > 0.0) 1.0 / speed else 0.0)
                    put(SensorType.DISTANCE_m.name, 0.0)
                    put(SensorType.BEARING.name, 0.0)
                    put(SensorType.ALTITUDE.name, 438.0)
                    put(SensorType.SLOPE.name, 0.0)
                    put(SensorType.VERTICAL_SPEED.name, 0)
                    put(SensorType.ASCENT.name, 0.0)
                    put(SensorType.DESCENT.name, 0.0)
                    // Non-speed columns containing "Rad"/"rad" tokens that must never match as wheelSpeed
                    put(SensorType.WORLD_YAW_RATE_radps.name, 1.5)
                    put(SensorType.RADAR_THREAT_COUNT.name, 3)
                    // Actual wheel speed column with NaN during stop must not bypass ZUPT or poison EKF
                    if (isStop) {
                        put("\"$wheelCol\"", Double.NaN)
                    } else {
                        putNull("\"$wheelCol\"")
                    }
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        assertTrue(ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId))

        samplesDb.query(
            tableName,
            arrayOf(SensorType.SPEED_mps.name, SensorType.DISTANCE_m.name),
            null, null, null, null, "_id ASC"
        ).use { cursor ->
            var rowIdx = 0
            while (cursor.moveToNext()) {
                val spd = cursor.getDouble(0)
                val dist = cursor.getDouble(1)
                assertTrue("Speed must remain finite at row $rowIdx", spd.isFinite())
                assertTrue("Distance must remain finite at row $rowIdx", dist.isFinite())
                if (rowIdx in 20..39) {
                    assertEquals("Indoor stop with RADAR/YAW columns & NaN wheelSpeed must pin speed to 0.0 at $rowIdx", 0.0, spd, 1e-9)
                }
                rowIdx++
            }
            assertEquals(60, rowIdx)
        }
    }

    // TST-FIL-027.1 (Bounce 1/3): Leading null TIME_TOTAL + >=6s SQLite batch-flush stall + genuine repeated wall-clock pauses
    @Test
    fun testLeadingNullTimeTotal_batchFlushStall6s_andDoubleWallClockPause_tstFil027_1() = runBlocking {
        val baseFileName = "2026-11-18_005500"
        val workoutId = createWorkout(baseFileName)

        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        samplesDbManager.createNewTable(
            baseFileName,
            listOf(
                SensorType.TIME_TOTAL,
                SensorType.LATITUDE,
                SensorType.LONGITUDE,
                SensorType.SPEED_mps,
                SensorType.PACE_spm,
                SensorType.DISTANCE_m,
                SensorType.BEARING,
                SensorType.ALTITUDE,
                SensorType.SLOPE,
                SensorType.VERTICAL_SPEED,
                SensorType.ASCENT,
                SensorType.DESCENT
            )
        )
        val baroCol = "ALTITUDE (Altitude from Pressure)"
        samplesDb.execSQL("ALTER TABLE $tableName ADD COLUMN \"$baroCol\" REAL")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTs = 1_700_000_900_000L
        val latStepDeg = Math.toDegrees(6.0 / 6371000.0)
        var lat = 48.6567

        // Timeline (32 rows):
        // 0..2: leading NULL TIME_TOTAL (1s wall-clock steps)
        // 3..9: valid TIME_TOTAL = 1..7 (1s steps)
        // 10..16: >=6s SQLite batch-flush stall (wall-clock frozen at T0+9s for 10..15, then +8s flush catch-up to T0+17s at row 16 while TIME_TOTAL = 8..14 advances 1s/row)
        // 17..20: normal 1s progression (isolates row 16 flush catch-up from pause boundary)
        // 21..25: genuine 30s wall-clock pause before row 21 (wall jumps +31s while TIME_TOTAL advances +1s to 19..23)
        // 26..31: genuine 25s second wall-clock pause before row 26 (wall jumps +26s while TIME_TOTAL advances +1s to 24..29)
        samplesDb.beginTransaction()
        try {
            for (i in 0 until 32) {
                val wallOffsetSec = when {
                    i <= 9 -> i
                    i in 10..15 -> 9 // frozen SQLite flush timestamp across 6 rows
                    i == 16 -> 17 // +8s flush catch-up (old parsedTs-prevParsedTs check falsely tripped >6s!)
                    i in 17..20 -> i // normal 1s steps (17..20)
                    i in 21..25 -> 51 + (i - 21) // +30s genuine pause
                    else -> 82 + (i - 26) // +25s second genuine pause
                }
                val stepCount = when (i) {
                    0 -> 0
                    21 -> 31
                    26 -> 26
                    else -> 1
                }
                lat += stepCount * latStepDeg
                val alt = 400.0 + (wallOffsetSec.coerceAtLeast(i)) * 0.5 // consistent 0.5 m/s climb across pauses
                val cv = ContentValues().apply {
                    put("time", sdf.format(Date(startTs + wallOffsetSec * 1000L)))
                    if (i < 3) {
                        putNull(SensorType.TIME_TOTAL.name)
                    } else {
                        put(SensorType.TIME_TOTAL.name, (i - 2).toLong())
                    }
                    put(SensorType.LATITUDE.name, lat)
                    put(SensorType.LONGITUDE.name, 9.0545)
                    put(SensorType.SPEED_mps.name, 6.0)
                    put(SensorType.PACE_spm.name, 1.0 / 6.0)
                    put(SensorType.DISTANCE_m.name, i * 6.0)
                    put(SensorType.BEARING.name, 0.0)
                    put(SensorType.ALTITUDE.name, alt)
                    put(SensorType.SLOPE.name, 8.3)
                    put(SensorType.VERTICAL_SPEED.name, 1800)
                    put(SensorType.ASCENT.name, 0.0)
                    put(SensorType.DESCENT.name, 0.0)
                    put("\"$baroCol\"", alt)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        assertTrue(ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId))

        samplesDb.query(
            tableName,
            arrayOf(SensorType.VERTICAL_SPEED.name, SensorType.SPEED_mps.name, SensorType.DISTANCE_m.name),
            null, null, null, null, "_id ASC"
        ).use { cursor ->
            val vams = mutableListOf<Double>()
            val speeds = mutableListOf<Double>()
            val dists = mutableListOf<Double>()
            while (cursor.moveToNext()) {
                vams.add(cursor.getDouble(0))
                speeds.add(cursor.getDouble(1))
                dists.add(cursor.getDouble(2))
            }
            assertEquals(32, vams.size)
            // Leading null TIME_TOTAL (0..2), normal (3..9), and 6s batch-flush stall + catch-up (10..16):
            // dt must stay 1.0 s everywhere (deltaDist ~ 6.0 m, speed ~ 6.0 m/s, VAM > 1000 m/h)
            for (i in 1..20) {
                val deltaDist = dists[i] - dists[i - 1]
                assertTrue("Row $i must maintain ~1s dt (deltaDist=$deltaDist)", deltaDist in 4.0..8.5)
                if (i >= 5) {
                    assertTrue("Row $i VAM must stay > 1000 m/h (was ${vams[i]})", vams[i] > 1000.0)
                }
                assertTrue("Row $i speed must stay near 6.0 m/s (was ${speeds[i]})", speeds[i] > 4.5)
            }
            // Row 21 (genuine 30s pause) must preserve ~31s dt (deltaDist > 100 m),
            // and rows 22..25 must re-anchor and resume 1s dt (~6.0 m/row)
            assertTrue("Row 21 genuine 30s pause must yield > 100 m deltaDist (was ${dists[21] - dists[20]})", (dists[21] - dists[20]) > 100.0)
            for (i in 22..25) {
                val deltaDist = dists[i] - dists[i - 1]
                assertTrue("Post-pause-1 row $i must resume ~1s dt (deltaDist=$deltaDist)", deltaDist in 4.0..8.5)
                assertTrue("Post-pause-1 row $i VAM must stay > 1000 m/h (was ${vams[i]})", vams[i] > 1000.0)
            }
            // Row 26 (second genuine 25s pause) must preserve ~26s dt (deltaDist > 100 m),
            // and rows 27..31 must re-anchor cleanly again and resume 1s dt
            assertTrue("Row 26 genuine 25s pause must yield > 100 m deltaDist (was ${dists[26] - dists[25]})", (dists[26] - dists[25]) > 100.0)
            for (i in 27..31) {
                val deltaDist = dists[i] - dists[i - 1]
                assertTrue("Post-pause-2 row $i must resume ~1s dt (deltaDist=$deltaDist)", deltaDist in 4.0..8.5)
                assertTrue("Post-pause-2 row $i VAM must stay > 500 m/h (was ${vams[i]})", vams[i] > 500.0)
            }
        }
    }

    // TST-FIL-028.2 & TST-FIL-028.4: 1-Hz ACTIVITY_AND_CARRIAGE_MODE persistence, ERTS ingestion & preservation
    @Test
    fun testRecomputeWorkout_ingestsAndPreserves1HzActivityAndCarriageMode_tstFil028_2_4() = runBlocking {
        assertTrue(ErtsPostWorkoutRecomputer.ALLOWED_CANONICAL_COLUMNS.contains(SensorType.ACTIVITY_AND_CARRIAGE_MODE.name))

        val baseFileName = "2026-11-18_093000"
        val workoutId = createWorkout(baseFileName)
        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

        samplesDbManager.createNewTable(
            baseFileName,
            listOf(
                SensorType.TIME_TOTAL,
                SensorType.LATITUDE,
                SensorType.LONGITUDE,
                SensorType.SPEED_mps,
                SensorType.PACE_spm,
                SensorType.DISTANCE_m,
                SensorType.BEARING,
                SensorType.ALTITUDE,
                SensorType.SLOPE,
                SensorType.VERTICAL_SPEED,
                SensorType.ASCENT,
                SensorType.DESCENT,
                SensorType.ACTIVITY_AND_CARRIAGE_MODE
            )
        )
        val baroCol = "ALTITUDE (Altitude from Pressure)"
        samplesDb.execSQL("ALTER TABLE $tableName ADD COLUMN \"$baroCol\" REAL")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTs = 1_700_002_000_000L
        var lat = 48.6500
        val expectedModes = mutableListOf<Int>()

        samplesDb.beginTransaction()
        try {
            for (sec in 0 until 60) {
                val mode = when {
                    sec < 20 -> PhoneCarriageMode.BIKE_FRONT_POCKET
                    sec < 30 -> PhoneCarriageMode.WALKING_TRANSITION
                    sec < 45 -> PhoneCarriageMode.STATIONARY
                    else -> PhoneCarriageMode.BIKE_HANDLEBAR
                }
                expectedModes.add(mode.code)
                val speed = when (mode) {
                    PhoneCarriageMode.BIKE_FRONT_POCKET -> 6.0
                    PhoneCarriageMode.WALKING_TRANSITION -> 1.2
                    PhoneCarriageMode.STATIONARY -> 0.16 // GPS jitter above 0.05 but pinned via explicit STATIONARY
                    PhoneCarriageMode.BIKE_HANDLEBAR -> 7.0
                    else -> 0.0
                }
                if (mode != PhoneCarriageMode.STATIONARY) {
                    lat += Math.toDegrees(speed / 6371000.0)
                }
                val pocketPulse = if (mode == PhoneCarriageMode.BIKE_FRONT_POCKET && sec == 10) -8.0 else 0.0
                val baroAlt = 400.0 + pocketPulse + if (mode == PhoneCarriageMode.BIKE_HANDLEBAR) (sec - 45) * 0.4 else 0.0

                val cv = ContentValues().apply {
                    put("time", sdf.format(Date(startTs + sec * 1000L)))
                    put(SensorType.TIME_TOTAL.name, sec + 1L)
                    put(SensorType.LATITUDE.name, lat)
                    put(SensorType.LONGITUDE.name, 9.0545)
                    put(SensorType.SPEED_mps.name, speed)
                    put(SensorType.PACE_spm.name, if (speed > 0.1) 1.0 / speed else 0.0)
                    put(SensorType.DISTANCE_m.name, sec * 4.0)
                    put(SensorType.BEARING.name, 0.0)
                    put(SensorType.ALTITUDE.name, baroAlt)
                    put(SensorType.SLOPE.name, 0.0)
                    put(SensorType.VERTICAL_SPEED.name, 0)
                    put(SensorType.ASCENT.name, 0.0)
                    put(SensorType.DESCENT.name, 0.0)
                    put(SensorType.ACTIVITY_AND_CARRIAGE_MODE.name, mode.code)
                    put("\"$baroCol\"", baroAlt)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        assertTrue(ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId))

        samplesDb.query(
            tableName,
            arrayOf(
                SensorType.SPEED_mps.name,
                SensorType.ACTIVITY_AND_CARRIAGE_MODE.name,
                SensorType.ALTITUDE.name
            ),
            null, null, null, null, "_id ASC"
        ).use { cursor ->
            var rowIdx = 0
            while (cursor.moveToNext()) {
                val spd = cursor.getDouble(0)
                val persistedMode = cursor.getInt(1)
                val alt = cursor.getDouble(2)
                assertEquals("Recorded mode must stay preserved at row $rowIdx", expectedModes[rowIdx], persistedMode)
                if (rowIdx in 30 until 45) {
                    assertEquals("STATIONARY dismount mode must pin 0.16 m/s jitter to 0.0 at row $rowIdx", 0.0, spd, 1e-9)
                }
                if (rowIdx in 8..13) {
                    assertTrue("Front-pocket -8m compression spike must be attenuated near 400m at $rowIdx (was $alt)", alt > 397.5)
                }
                rowIdx++
            }
            assertEquals(60, rowIdx)
        }
    }

    // TST-FIL-028.4: Legacy DB without ACTIVITY_AND_CARRIAGE_MODE falls back to deterministic 1-Hz kinematic inference
    @Test
    fun testLegacyTableWithoutCarriageModeColumn_infers1HzKinematicFallback_tstFil028_4() = runBlocking {
        val baseFileName = "2026-11-18_094500"
        val workoutId = createWorkout(baseFileName)
        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

        samplesDbManager.createNewTable(
            baseFileName,
            listOf(
                SensorType.TIME_TOTAL,
                SensorType.LATITUDE,
                SensorType.LONGITUDE,
                SensorType.SPEED_mps,
                SensorType.PACE_spm,
                SensorType.DISTANCE_m,
                SensorType.BEARING,
                SensorType.ALTITUDE,
                SensorType.SLOPE,
                SensorType.VERTICAL_SPEED,
                SensorType.ASCENT,
                SensorType.DESCENT,
                SensorType.WORLD_YAW_RATE_radps
            )
        )

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTs = 1_700_003_000_000L
        var lat = 48.6600
        samplesDb.beginTransaction()
        try {
            for (sec in 0 until 45) {
                val speed = when {
                    sec < 15 -> 6.5
                    sec < 25 -> 1.1
                    sec < 35 -> 0.0
                    else -> 7.0
                }
                val yaw = when {
                    sec < 15 -> if (sec % 2 == 0) 0.55 else -0.55 // Thigh pocket alias
                    sec < 25 -> 0.16 // Dismount walk
                    sec < 35 -> 0.0
                    else -> 0.01 // Smooth handlebar
                }
                lat += Math.toDegrees(speed / 6371000.0)
                val cv = ContentValues().apply {
                    put("time", sdf.format(Date(startTs + sec * 1000L)))
                    put(SensorType.TIME_TOTAL.name, sec + 1L)
                    put(SensorType.LATITUDE.name, lat)
                    put(SensorType.LONGITUDE.name, 9.0545)
                    put(SensorType.SPEED_mps.name, speed)
                    put(SensorType.PACE_spm.name, if (speed > 0.1) 1.0 / speed else 0.0)
                    put(SensorType.DISTANCE_m.name, sec * 4.0)
                    put(SensorType.BEARING.name, 0.0)
                    put(SensorType.ALTITUDE.name, 410.0)
                    put(SensorType.SLOPE.name, 0.0)
                    put(SensorType.VERTICAL_SPEED.name, 0)
                    put(SensorType.ASCENT.name, 0.0)
                    put(SensorType.DESCENT.name, 0.0)
                    put(SensorType.WORLD_YAW_RATE_radps.name, yaw)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        // Verify deterministic legacy fallback classification before running full recomputeWorkout()
        val syntheticRows = (0 until 45).map { sec ->
            ErtsPostWorkoutRecomputer.RawSampleRow(
                rowId = sec + 1L,
                timestampMs = startTs + sec * 1000L,
                latitude = 48.66 + sec * 0.00004,
                longitude = 9.0545,
                speedMps = when {
                    sec < 15 -> 6.5
                    sec < 25 -> 1.1
                    sec < 35 -> 0.0
                    else -> 7.0
                },
                bearingDeg = 0.0,
                accuracyM = 3.0,
                yawRateRadPerSec = when {
                    sec < 15 -> if (sec % 2 == 0) 0.55 else -0.55
                    sec < 25 -> 0.16
                    sec < 35 -> 0.0
                    else -> 0.01
                },
                rawAltitude = 410.0,
                referenceAltitude = null,
                wheelSpeedMps = null
            )
        }
        val resolved = ErtsPostWorkoutRecomputer.resolveCarriageModesAndFilterBaro(syntheticRows)
        assertEquals(PhoneCarriageMode.BIKE_FRONT_POCKET, resolved[7].effectiveCarriageMode)
        assertEquals(PhoneCarriageMode.WALKING_TRANSITION, resolved[20].effectiveCarriageMode)
        assertEquals(PhoneCarriageMode.STATIONARY, resolved[30].effectiveCarriageMode)
        assertEquals(PhoneCarriageMode.BIKE_HANDLEBAR, resolved[40].effectiveCarriageMode)

        assertTrue(ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId))
    }
}
