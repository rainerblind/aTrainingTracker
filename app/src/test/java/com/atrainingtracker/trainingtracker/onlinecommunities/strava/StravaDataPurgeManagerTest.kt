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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying comprehensive Strava data deletion and reauthorization integrity (REQ-EXT-009, TST-EXT-006, ATT-1078).
 */
class StravaDataPurgeManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockWorkManager: WorkManager
    private lateinit var mockSegmentsDb: SegmentsDatabaseManager
    private lateinit var mockRoutesDb: RoutesDatabaseManager
    private lateinit var mockAuthRepo: StravaAuthRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.deleteStravaToken() } returns Unit
        every { TrainingApplication.getStravaAccessToken() } returns "mock_token"

        mockkConstructor(StravaUploadDbHelper::class)
        every { anyConstructed<StravaUploadDbHelper>().clearAllStravaData() } returns 5

        mockSegmentsDb = mockk(relaxed = true)
        mockkStatic(SegmentsDatabaseManager::class)
        every { SegmentsDatabaseManager.getInstance(any()) } returns mockSegmentsDb

        mockRoutesDb = mockk(relaxed = true)
        RoutesDatabaseManager.resetForTesting(mockRoutesDb)

        mockkConstructor(EquipmentDbHelper::class)
        every { anyConstructed<EquipmentDbHelper>().unlinkAllStravaEquipment() } returns 2

        mockWorkManager = mockk(relaxed = true)
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns mockWorkManager
        every { TrainingApplication.isWorkManagerAvailable() } returns true

        mockAuthRepo = mockk(relaxed = true)
        mockkObject(StravaAuthRepository.Companion)
        every { StravaAuthRepository.getInstance() } returns mockAuthRepo
    }

    @After
    fun tearDown() {
        RoutesDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testExecuteLocalDataPurge_clearsAllStorageVectors() {
        StravaDataPurgeManager.executeLocalDataPurge(mockContext)

        // 1. SharedPreferences credentials cleared
        verify { TrainingApplication.deleteStravaToken() }

        // 2. StravaUpload.db activity JSON and IDs purged
        verify { anyConstructed<StravaUploadDbHelper>().clearAllStravaData() }

        // 3. Segments.db starred segments wiped
        verify { mockSegmentsDb.deleteAllTables() }

        // 4. Routes.db Strava-origin routes deleted
        verify { mockRoutesDb.deleteRoutesBySource(RouteSource.STRAVA) }

        // 5. Equipment.db Strava IDs unlinked (NOT deleted)
        verify { anyConstructed<EquipmentDbHelper>().unlinkAllStravaEquipment() }

        // 6. WorkManager periodic sync workers canceled
        verify { mockWorkManager.cancelUniqueWork("automated_strava_segments_sync_work") }
        verify { mockWorkManager.cancelUniqueWork("automated_strava_routes_sync_work") }

        // 7. Auth state reset
        verify { mockAuthRepo.resetState() }
    }

    @Test
    fun testExecuteLocalDataPurge_preservesInvariants() {
        StravaDataPurgeManager.executeLocalDataPurge(mockContext)

        // Verifies that Equipment rows are UNLINKED rather than dropped
        verify(exactly = 1) { anyConstructed<EquipmentDbHelper>().unlinkAllStravaEquipment() }

        // Verifies that ONLY Strava routes are deleted, preserving local GPX and workout routes
        verify(exactly = 1) { mockRoutesDb.deleteRoutesBySource(RouteSource.STRAVA) }
        verify(exactly = 0) { mockRoutesDb.deleteRoutesBySource(RouteSource.LOCAL_GPX) }
        verify(exactly = 0) { mockRoutesDb.deleteRoutesBySource(RouteSource.WORKOUT) }
    }
}
