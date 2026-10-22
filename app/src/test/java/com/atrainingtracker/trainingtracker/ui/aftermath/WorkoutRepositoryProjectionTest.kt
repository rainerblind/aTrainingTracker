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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite regression verification for projected queries in [WorkoutRepository]
 * with source-qualified location columns ("LATITUDE (GPS)", "LATITUDE (Google Fused)", "LATITUDE (Network)").
 * (ATT-4707 / REQ-DAT-033.4 / TST-DAT-028.5 / TST-DAT-028.6).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryProjectionTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSamplesDb = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
    private lateinit var realDb: SQLiteDatabase
    private lateinit var repository: WorkoutRepository

    private val testBaseFileName = "2026-10-16_055700"
    private val testWorkoutId = 100L

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

        mockkStatic(LocalBroadcastManager::class)
        val mockLbm = mockk<LocalBroadcastManager>(relaxed = true)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLbm

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper::class)
        every { anyConstructed<com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper>().getStravaActivityData(any()) } returns null

        mockkConstructor(com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider::class)
        every { anyConstructed<com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider>().createGroupData(any(), any()) } returns mockk(relaxed = true)

        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getZoneMax(any(), any(), 1) } returns 140
        every { SettingsDataStoreJavaHelper.getZoneMax(any(), any(), 2) } returns 160
        every { SettingsDataStoreJavaHelper.getZoneMax(any(), any(), 3) } returns 180
        every { SettingsDataStoreJavaHelper.getZoneMax(any(), any(), 4) } returns 200
        every { SettingsDataStoreJavaHelper.getZoneMax(any(), any(), 5) } returns 250
        every { SettingsDataStoreJavaHelper.getZoneMax(any(), any(), 6) } returns 300

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb
        every { mockSummariesDb.getBaseFileName(testWorkoutId) } returns testBaseFileName

        realDb = SQLiteDatabase.create(null)
        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb
        every { mockSamplesDb.database } returns realDb
        every { mockSamplesDb.existsTable(testBaseFileName) } returns true

        val tableName = WorkoutSamplesDatabaseManager.getTableName(testBaseFileName)
        realDb.execSQL(
            """
            CREATE TABLE $tableName (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                time TEXT,
                TIME_ACTIVE INTEGER,
                TIME_TOTAL INTEGER,
                DISTANCE_m REAL,
                ALTITUDE REAL,
                HR INTEGER,
                POWER INTEGER,
                SPEED_mps REAL,
                SLOPE REAL,
                LATITUDE REAL,
                LONGITUDE REAL,
                "LATITUDE (GPS)" REAL,
                "LONGITUDE (GPS)" REAL,
                "LATITUDE (Google Fused)" REAL,
                "LONGITUDE (Google Fused)" REAL,
                "LATITUDE (Network)" REAL,
                "LONGITUDE (Network)" REAL
            );
            """.trimIndent()
        )

        realDb.execSQL(
            """
            INSERT INTO $tableName (
                time, TIME_ACTIVE, TIME_TOTAL, DISTANCE_m, ALTITUDE, HR, POWER, SPEED_mps, SLOPE,
                LATITUDE, LONGITUDE,
                "LATITUDE (GPS)", "LONGITUDE (GPS)",
                "LATITUDE (Google Fused)", "LONGITUDE (Google Fused)",
                "LATITUDE (Network)", "LONGITUDE (Network)"
            ) VALUES (
                '2026-10-16 05:57:00', 0, 0, 0.0, 520.0, 140, 200, 5.0, 0.0,
                48.137, 11.575,
                48.1371, 11.5751,
                48.1372, 11.5752,
                48.1373, 11.5753
            );
            """.trimIndent()
        )

        realDb.execSQL(
            """
            INSERT INTO $tableName (
                time, TIME_ACTIVE, TIME_TOTAL, DISTANCE_m, ALTITUDE, HR, POWER, SPEED_mps, SLOPE,
                LATITUDE, LONGITUDE,
                "LATITUDE (GPS)", "LONGITUDE (GPS)",
                "LATITUDE (Google Fused)", "LONGITUDE (Google Fused)",
                "LATITUDE (Network)", "LONGITUDE (Network)"
            ) VALUES (
                '2026-10-16 05:57:10', 10, 10, 50.0, 525.0, 155, 230, 5.5, 1.2,
                48.138, 11.576,
                48.1381, 11.5761,
                48.1382, 11.5762,
                48.1383, 11.5763
            );
            """.trimIndent()
        )

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        if (::repository.isInitialized) {
            repository.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
        if (::realDb.isInitialized && realDb.isOpen) {
            realDb.close()
        }
        unmockkAll()
    }

    @Test
    fun testQuoteSqlIdentifier_escapesQuotesAndWrapsInDoubleQuotes() {
        assertEquals("\"LATITUDE\"", WorkoutRepository.quoteSqlIdentifier("LATITUDE"))
        assertEquals("\"LATITUDE (Google Fused)\"", WorkoutRepository.quoteSqlIdentifier("LATITUDE (Google Fused)"))
        assertEquals("\"LATITUDE (GPS)\"", WorkoutRepository.quoteSqlIdentifier("LATITUDE (GPS)"))
        assertEquals("\"LATITUDE (Network)\"", WorkoutRepository.quoteSqlIdentifier("LATITUDE (Network)"))
        assertEquals("\"column\"\"name\"", WorkoutRepository.quoteSqlIdentifier("column\"name"))
    }

    @Test
    fun testLoadWorkoutDetailTelemetry_withSourceQualifiedLocationColumns_executesWithoutException() = runBlocking {
        val telemetry = repository.loadWorkoutDetailTelemetry(testWorkoutId, BSportType.BIKE)

        assertEquals(4, telemetry.tracks.size)
        assertTrue(telemetry.tracks.containsKey(TrackType.BEST))
        assertTrue(telemetry.tracks.containsKey(TrackType.GPS))
        assertTrue(telemetry.tracks.containsKey(TrackType.FUSED))
        assertTrue(telemetry.tracks.containsKey(TrackType.NETWORK))

        val fusedPoints = telemetry.tracks[TrackType.FUSED]!!
        assertEquals(2, fusedPoints.size)
        assertEquals(48.1372, fusedPoints[0].latLng.latitude, 0.0001)
        assertEquals(11.5752, fusedPoints[0].latLng.longitude, 0.0001)
        assertEquals(48.1382, fusedPoints[1].latLng.latitude, 0.0001)
        assertEquals(11.5762, fusedPoints[1].latLng.longitude, 0.0001)

        val gpsPoints = telemetry.tracks[TrackType.GPS]!!
        assertEquals(2, gpsPoints.size)
        assertEquals(48.1371, gpsPoints[0].latLng.latitude, 0.0001)
        assertEquals(11.5751, gpsPoints[0].latLng.longitude, 0.0001)

        val networkPoints = telemetry.tracks[TrackType.NETWORK]!!
        assertEquals(2, networkPoints.size)
        assertEquals(48.1373, networkPoints[0].latLng.latitude, 0.0001)
        assertEquals(11.5753, networkPoints[0].latLng.longitude, 0.0001)

        val bestPoints = telemetry.tracks[TrackType.BEST]!!
        assertEquals(2, bestPoints.size)
        assertEquals(48.137, bestPoints[0].latLng.latitude, 0.0001)
        assertEquals(11.575, bestPoints[0].latLng.longitude, 0.0001)

        assertNotNull(telemetry.hrZoneDistribution)
        assertNotNull(telemetry.powerZoneDistribution)
    }

    @Test
    fun testGetWorkoutTrackPoints_forAllTrackTypes_executesWithoutException() = runBlocking {
        for (trackType in TrackType.entries) {
            val points = repository.getWorkoutTrackPoints(testWorkoutId, trackType)
            assertEquals("Failed to extract track points for $trackType", 2, points.size)
            when (trackType) {
                TrackType.BEST -> {
                    assertEquals(48.137, points[0].latLng.latitude, 0.0001)
                    assertEquals(11.575, points[0].latLng.longitude, 0.0001)
                }
                TrackType.GPS -> {
                    assertEquals(48.1371, points[0].latLng.latitude, 0.0001)
                    assertEquals(11.5751, points[0].latLng.longitude, 0.0001)
                }
                TrackType.FUSED -> {
                    assertEquals(48.1372, points[0].latLng.latitude, 0.0001)
                    assertEquals(11.5752, points[0].latLng.longitude, 0.0001)
                }
                TrackType.NETWORK -> {
                    assertEquals(48.1373, points[0].latLng.latitude, 0.0001)
                    assertEquals(11.5753, points[0].latLng.longitude, 0.0001)
                }
            }
        }
    }

    @Test
    fun testGetZoneDistributions_executeWithoutException() = runBlocking {
        val hrDist = repository.getHeartRateZoneDistribution(testWorkoutId, BSportType.BIKE)
        assertNotNull(hrDist)
        assertEquals(6L, hrDist!!.totalActiveTimeSec)

        val pwrDist = repository.getPowerZoneDistribution(testWorkoutId)
        assertNotNull(pwrDist)
        assertEquals(6L, pwrDist!!.totalActiveTimeSec)
    }
}
