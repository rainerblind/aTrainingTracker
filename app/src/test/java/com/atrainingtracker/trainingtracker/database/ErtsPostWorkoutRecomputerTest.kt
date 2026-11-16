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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
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
        samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
        summariesDbManager = WorkoutSummariesDatabaseManager.getInstance(context)
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

                assertTrue("Smoothed latitude valid", lat > 40.0)
                assertTrue("Smoothed longitude valid", lon > 10.0)
                assertTrue("Smoothed speed valid", spd > 0.0)
                assertTrue("Smoothed distance non-negative", dist >= 0.0)
                assertTrue("Smoothed altitude valid", alt > 400.0)

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
            assertEquals("Polyline has 30 points", 30, decodedPoints.size)

            assertNotNull("Altitude stream populated", altStream)
            assertNotNull("Distance stream populated", distStream)
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
            assertEquals(
                "Polyline must contain exactly 25 valid fix points (5 no-fix rows excluded)",
                25,
                decodedPoints.size
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
}
