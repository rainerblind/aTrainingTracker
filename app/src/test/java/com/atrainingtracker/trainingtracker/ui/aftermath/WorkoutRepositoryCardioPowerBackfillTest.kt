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
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

/**
 * Unit verification for [WorkoutRepository] lazy backfill, projected queries,
 * ineligible caching, and bounded execution (REQ-REV-009.2c, TST-REV-010.8).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryCardioPowerBackfillTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSamplesDb = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
    private lateinit var realDb: SQLiteDatabase
    private lateinit var repository: WorkoutRepository

    private val testBaseFileName = "2026-11-11_080000"
    private val testWorkoutId = 101L

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

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb
        every { mockSummariesDb.getBaseFileName(testWorkoutId) } returns testBaseFileName
        every { mockSummariesDb.getCardioPowerDynamics(any()) } returns null

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
                HR INTEGER,
                POWER INTEGER
            );
            """.trimIndent()
        )

        // Seed 1800 rows of valid paired telemetry (alternating 150W / 250W power and proportional HR)
        realDb.beginTransaction()
        try {
            var hr = 90.0
            for (i in 0 until 1800) {
                val p = if ((i / 200) % 2 == 0) 150.0 else 250.0
                hr = 0.97 * hr + 0.03 * (70.0 + 0.25 * p)
                realDb.execSQL(
                    "INSERT INTO $tableName (time, TIME_ACTIVE, TIME_TOTAL, HR, POWER) VALUES ('2026-11-11 08:00:00', $i, $i, ${hr.toInt()}, ${p.toInt()})"
                )
            }
            realDb.setTransactionSuccessful()
        } finally {
            realDb.endTransaction()
        }

        WorkoutRepository.resetForTesting(null)
        repository = WorkoutRepository.getInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        realDb.close()
        unmockkAll()
    }

    @Test
    fun testLazyBackfillProjectsColumnsAndSavesRecord() = runBlocking {
        val recordSlot = slot<CardioPowerDynamicsRecord>()
        every { mockSummariesDb.saveCardioPowerDynamics(testWorkoutId, capture(recordSlot)) } just Runs

        val result = repository.getOrComputeCardioPowerDynamics(testWorkoutId)
        assertNotNull("Qualifying telemetry should yield calculated dynamics", result)
        assertTrue(recordSlot.isCaptured)
        assertEquals(testWorkoutId, recordSlot.captured.workoutId)
        assertTrue("Fresh model tau should be identified", recordSlot.captured.tauStart > 0.0)
    }

    @Test
    fun testIneligibleWorkoutIsCachedToPreventRepeatedQueries() = runBlocking {
        val ineligibleId = 999L
        every { mockSummariesDb.getBaseFileName(ineligibleId) } returns null

        val firstResult = repository.getOrComputeCardioPowerDynamics(ineligibleId)
        assertNull(firstResult)
        assertTrue(
            "Ineligible workout must be recorded in memory cache",
            repository.ineligibleCardioPowerWorkoutIds.contains(ineligibleId)
        )

        // Reset base file name mock to verify that cache bypasses any DB check
        clearMocks(mockSummariesDb, answers = false)
        val secondResult = repository.getOrComputeCardioPowerDynamics(ineligibleId)
        assertNull(secondResult)
        verify(exactly = 0) { mockSummariesDb.getBaseFileName(ineligibleId) }
    }

    @Test
    fun testBackfillHistoricalCardioPowerDynamicsBoundsExecutionToLimit() = runBlocking {
        val recordSlot = slot<CardioPowerDynamicsRecord>()
        every { mockSummariesDb.saveCardioPowerDynamics(any(), capture(recordSlot)) } just Runs

        // Mark test workout as ineligible
        repository.ineligibleCardioPowerWorkoutIds.add(testWorkoutId)

        val processed = repository.backfillHistoricalCardioPowerDynamics(limit = 20)
        assertEquals(0, processed) // testWorkoutId was cached ineligible, so 0 processed
    }
}
