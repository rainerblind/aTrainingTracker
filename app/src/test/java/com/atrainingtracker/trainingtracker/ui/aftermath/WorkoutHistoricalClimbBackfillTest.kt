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
import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepository
import com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test suite verifying historical workout climb backfill, idempotency,
 * and LiveClimbsRepository refresh (REQ-MAP-066.1, TST-MAP-068.1, ATT-6691).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutHistoricalClimbBackfillTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSamplesDb = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
    private val mockClimbsDb = mockk<ClimbsDatabaseManager>(relaxed = true)
    private val mockLiveClimbsRepo = mockk<LiveClimbsRepository>(relaxed = true)
    private val mockSqliteDb = mockk<SQLiteDatabase>(relaxed = true)
    private val mockPrefs = mockk<SharedPreferences>(relaxed = true)
    private val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

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

        every { mockApplication.applicationContext } returns mockApplication

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any<Context>()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        // Default to true during setUp so init's background launch doesn't race
        every { mockPrefs.getBoolean(WorkoutRepository.PREF_CLIMBS_WORKOUT_BACKFILL_V1, false) } returns true

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb
        every { mockSummariesDb.database } returns mockSqliteDb
        every { mockSqliteDb.isOpen } returns true

        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb

        ClimbsDatabaseManager.resetForTesting(mockClimbsDb)
        LiveClimbsRepository.resetForTesting(mockLiveClimbsRepo)

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = spyk(constructor.newInstance(mockApplication))
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        WorkoutRepository.resetForTesting(null)
        ClimbsDatabaseManager.resetForTesting(null)
        LiveClimbsRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testBackfillHistoricalClimbs_whenNotYetRun_processesCandidatesAndSetsFlag() = runBlocking {
        // Flag unset for this test run
        every { mockPrefs.getBoolean(WorkoutRepository.PREF_CLIMBS_WORKOUT_BACKFILL_V1, false) } returns false

        // 2 outdoor candidate workouts in cursor
        val cursor = MatrixCursor(arrayOf(BaseColumns._ID)).apply {
            addRow(arrayOf(10L))
            addRow(arrayOf(20L))
        }
        every {
            mockSqliteDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(BaseColumns._ID),
                any(),
                null,
                null,
                null,
                "${BaseColumns._ID} ASC"
            )
        } returns cursor

        // 1000m climb points for workout 10
        val points10 = mutableListOf<PathPoint>()
        for (i in 0..20) {
            val dist = i * 50.0
            val alt = 200.0 + (dist * 0.08)
            points10.add(PathPoint(distance = dist, latLng = LatLng(47.5 + (i * 0.0005), 10.0), altitude = alt))
        }

        // Flat track for workout 20
        val points20 = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(47.5, 10.0), altitude = 200.0),
            PathPoint(distance = 1000.0, latLng = LatLng(47.51, 10.0), altitude = 200.0)
        )

        coEvery { repository.getWorkoutTrackPoints(10L, TrackType.BEST) } returns points10
        coEvery { repository.getWorkoutTrackPoints(20L, TrackType.BEST) } returns points20

        coEvery { mockClimbsDb.insertClimbsWithDeduplicationBatch(any(), thresholdMeters = 50.0f) } returns listOf(101L)
        coEvery { mockLiveClimbsRepo.refreshClimbs() } returns Unit

        val resultCount = repository.backfillHistoricalClimbs()

        assertEquals("Should report 1 persisted climb from workout 10", 1, resultCount)

        coVerify(exactly = 1) {
            mockClimbsDb.insertClimbsWithDeduplicationBatch(
                match { climbs ->
                    climbs.size == 1 && climbs[0].routeId == null
                },
                thresholdMeters = 50.0f
            )
        }

        verify(exactly = 1) {
            mockEditor.putBoolean(WorkoutRepository.PREF_CLIMBS_WORKOUT_BACKFILL_V1, true)
            mockEditor.apply()
        }

        coVerify(atLeast = 1) {
            mockLiveClimbsRepo.refreshClimbs()
        }
    }

    @Test
    fun testBackfillHistoricalClimbs_whenAlreadyRun_isIdempotentNoOp() = runBlocking {
        // Flag already set to true
        every { mockPrefs.getBoolean(WorkoutRepository.PREF_CLIMBS_WORKOUT_BACKFILL_V1, false) } returns true

        val resultCount = repository.backfillHistoricalClimbs()

        assertEquals("Should return 0 immediately without scanning DB", 0, resultCount)

        verify(exactly = 0) {
            mockSqliteDb.query(any(), any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            mockClimbsDb.insertClimbsWithDeduplicationBatch(any(), any())
        }
    }
}
