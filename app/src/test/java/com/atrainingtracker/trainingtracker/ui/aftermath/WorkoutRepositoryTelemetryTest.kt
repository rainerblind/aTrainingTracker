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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit verification for [WorkoutRepository.getWorkoutTelemetryPoints] (REQ-UI-235 / TST-UI-194.1 / ATT-2006).
 */
class WorkoutRepositoryTelemetryTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSamplesDb = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
    private val mockSqliteDb = mockk<SQLiteDatabase>(relaxed = true)
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

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

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb
        every { mockSamplesDb.database } returns mockSqliteDb

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testExtractTelemetryPoints_whenNoTableOrBaseName_returnsEmptyList() = runBlocking {
        every { mockSummariesDb.getBaseFileName(1L) } returns null
        val resultNullBase = repository.getWorkoutTelemetryPoints(1L)
        assertTrue(resultNullBase.isEmpty())

        every { mockSummariesDb.getBaseFileName(2L) } returns "2026-10-02-12-00-00"
        every { mockSamplesDb.existsTable("2026-10-02-12-00-00") } returns false
        val resultNoTable = repository.getWorkoutTelemetryPoints(2L)
        assertTrue(resultNoTable.isEmpty())
    }

    @Test
    fun testExtractTelemetryPoints_whenNoGpsCoordinates_returnsValidTimeDomainPoints() = runBlocking {
        val baseFileName = "2026-10-02-12-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(100L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val columns = listOf(
            SensorType.TIME_ACTIVE.name,
            SensorType.HR.name,
            SensorType.POWER.name,
            SensorType.SPEED_mps.name
        )
        val rows = listOf(
            listOf(0L, 120, 180, 5.0),
            listOf(1L, 122, 185, 5.2),
            listOf(2L, 125, 190, 5.1)
        )
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSqliteDb.query(tableName, null, null, null, null, null, null) } returns cursor

        val points = repository.getWorkoutTelemetryPoints(100L)
        assertEquals(3, points.size)
        assertEquals(0L, points[0].timeSec)
        assertEquals(120, points[0].hr)
        assertEquals(180, points[0].power)
        assertEquals(5.0, points[0].speedMps ?: 0.0, 0.001)
        assertEquals(0.0, points[0].distance, 0.0)
        assertEquals(LatLng(0.0, 0.0), points[0].latLng)
    }

    @Test
    fun testExtractTelemetryPoints_populatesHeartRateAndPower_withZeroDistance() = runBlocking {
        val baseFileName = "2026-10-02-13-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(200L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val columns = listOf(
            SensorType.TIME_ACTIVE.name,
            SensorType.HR.name,
            SensorType.POWER.name
        )
        val rows = listOf(
            listOf(10L, 145, 220),
            listOf(20L, 150, 230)
        )
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSqliteDb.query(tableName, null, null, null, null, null, null) } returns cursor

        val points = repository.getWorkoutTelemetryPoints(200L)
        assertEquals(2, points.size)
        assertEquals(145, points[0].hr)
        assertEquals(220, points[0].power)
        assertEquals(0.0, points[0].distance, 0.0)
        assertEquals(LatLng(0.0, 0.0), points[0].latLng)
        assertEquals(0.0, points[0].altitude, 0.0)
    }

    @Test
    fun testExtractTelemetryPoints_decimatesWhenOver800Points() = runBlocking {
        val baseFileName = "2026-10-02-14-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(300L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val columns = listOf(
            SensorType.TIME_ACTIVE.name,
            SensorType.HR.name
        )
        // 1600 samples
        val rows = (0 until 1600).map { i ->
            listOf(i.toLong(), 100 + (i % 60))
        }
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSqliteDb.query(tableName, null, null, null, null, null, null) } returns cursor

        val points = repository.getWorkoutTelemetryPoints(300L)
        assertTrue("Points size should be <= 801, was ${points.size}", points.size <= 801)
        assertEquals("First point must be preserved", 0L, points.first().timeSec)
        assertEquals("Last point must be preserved", 1599L, points.last().timeSec)
    }

    @Test
    fun testExtractTelemetryPoints_populatesAltitude_whenPresentInSamplesDb() = runBlocking {
        val baseFileName = "2026-10-02-15-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(400L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val columns = listOf(
            SensorType.TIME_ACTIVE.name,
            SensorType.HR.name,
            SensorType.ALTITUDE.name
        )
        val rows = listOf(
            listOf(0L, 130, 450.5),
            listOf(1L, 132, 452.0),
            listOf(2L, 135, 455.2)
        )
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSqliteDb.query(tableName, null, null, null, null, null, null) } returns cursor

        val points = repository.getWorkoutTelemetryPoints(400L)
        assertEquals(3, points.size)
        assertEquals(450.5, points[0].altitude, 0.001)
        assertEquals(452.0, points[1].altitude, 0.001)
        assertEquals(455.2, points[2].altitude, 0.001)
    }

    @Test
    fun testExtractTelemetryPoints_ingestsPointsWithOnlyAltitude_whenHrPowerSpeedAbsent() = runBlocking {
        val baseFileName = "2026-10-02-16-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(500L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val columns = listOf(
            SensorType.TIME_ACTIVE.name,
            SensorType.ALTITUDE.name
        )
        val rows = listOf(
            listOf(0L, 510.0),
            listOf(10L, 515.5)
        )
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSqliteDb.query(tableName, null, null, null, null, null, null) } returns cursor

        val points = repository.getWorkoutTelemetryPoints(500L)
        assertEquals(2, points.size)
        assertEquals(0L, points[0].timeSec)
        assertEquals(510.0, points[0].altitude, 0.001)
        assertNull(points[0].hr)
        assertNull(points[0].power)
        assertNull(points[0].speedMps)
        assertEquals(10L, points[1].timeSec)
        assertEquals(515.5, points[1].altitude, 0.001)
    }
}
