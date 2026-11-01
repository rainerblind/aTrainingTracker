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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Unit verification for [WorkoutRepository] historical zone threshold resolution (REQ-SET-082, TST-SET-082).
 */
class WorkoutRepositoryHistoricalZoneTest {

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

        mockkStatic(SettingsDataStoreJavaHelper::class)

        WorkoutRepository.resetForTesting(null)
        repository = WorkoutRepository.getInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testGetWorkoutStartTimeMsParsesUtcFormat() {
        val workoutId = 42L
        val dateStr = "2026-05-10 14:30:00"
        every {
            mockSummariesDb.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START)
        } returns dateStr

        val expectedMs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(dateStr)?.time ?: 0L

        val resultMs = repository.getWorkoutStartTimeMs(workoutId)
        assertEquals(expectedMs, resultMs)
    }

    @Test
    fun testLoadWorkoutDetailTelemetryAttachesEffectiveThresholdsAtWorkoutStartTime() = runBlocking {
        val workoutId = 42L
        val dateStr = "2026-05-10 14:30:00"
        every {
            mockSummariesDb.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START)
        } returns dateStr
        every { mockSummariesDb.getBaseFileName(workoutId) } returns "workout_42"
        every { mockSamplesDb.existsTable("workout_42") } returns true

        val columnsCursor = MockCursorFactory.create(
            listOf("name"),
            listOf(
                listOf(SensorType.HR.name),
                listOf(SensorType.POWER.name)
            )
        )
        every { mockSqliteDb.rawQuery(match { it.contains("PRAGMA table_info") }, null) } returns columnsCursor

        val emptyQueryCursor = MockCursorFactory.create(
            listOf(SensorType.HR.name, SensorType.POWER.name),
            emptyList()
        )
        every { mockSqliteDb.query(any(), any(), any(), any(), any(), any(), any()) } returns emptyQueryCursor

        val expectedMs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(dateStr)?.time ?: 0L

        val expectedHrBoundaries = listOf(132, 148, 162, 178)
        val expectedPwrBoundaries = listOf(160, 210, 260, 310, 360, 420)

        every {
            SettingsDataStoreJavaHelper.getEffectiveZoneBoundaries(mockApplication, SettingsDataStore.ZoneType.HR_RUN, expectedMs)
        } returns expectedHrBoundaries

        every {
            SettingsDataStoreJavaHelper.getEffectiveZoneBoundaries(mockApplication, SettingsDataStore.ZoneType.PWR_BIKE, expectedMs)
        } returns expectedPwrBoundaries

        val telemetry = repository.loadWorkoutDetailTelemetry(workoutId, BSportType.RUN)
        assertNotNull(telemetry)
        assertEquals(
            com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds(132, 148, 162, 178),
            telemetry.hrThresholds
        )
        assertEquals(
            com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds(160, 210, 260, 310, 360, 420),
            telemetry.powerThresholds
        )
    }
}
