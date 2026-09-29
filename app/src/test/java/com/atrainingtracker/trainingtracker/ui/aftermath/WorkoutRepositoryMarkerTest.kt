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
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying start and finish marker title annotations with favorite location names
 * in [WorkoutRepository.getWorkoutMarkers] (ATT-1400 / REQ-UI-184 / TST-UI-137.3).
 */
class WorkoutRepositoryMarkerTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private lateinit var repository: WorkoutRepository

    private val startPos = LatLng(48.137, 11.576)
    private val endPos = LatLng(48.140, 11.580)

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

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb
        every { mockSummariesDb.getExtremaValue(any(), any(), any()) } returns null
        every { mockSummariesDb.getExtremaPosition(any(), any(), any()) } returns null

        mockkStatic(com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager::class)
        val mockSamplesDb = mockk<com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager>(relaxed = true)
        every { com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb
        every { mockSamplesDb.getExtremaPosition(any(), any(), any(), any()) } returns null

        mockkConstructor(StravaUploadDbHelper::class)
        every { anyConstructed<StravaUploadDbHelper>().getStravaActivityData(any()) } returns null

        mockkConstructor(ExportStatusDataProvider::class)
        every { anyConstructed<ExportStatusDataProvider>().createGroupData(any(), any()) } returns mockk(relaxed = true)

        every { mockApplication.getString(R.string.Start) } returns "Start"
        every { mockApplication.getString(R.string.Stop) } returns "Stop"

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkStatic(WorkoutSummariesDatabaseManager::class)
        unmockkStatic(com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager::class)
        unmockkAll()
    }

    private fun createWorkoutData(
        startLatLng: LatLng? = null,
        endLatLng: LatLng? = null,
        startLocationName: String? = null,
        endLocationName: String? = null
    ): WorkoutData {
        return WorkoutData(
            id = 1L,
            finished = true,
            fileBaseName = "2026-09-29-10-00-00",
            workoutName = "Test Workout",
            sportId = 1L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = 1700000000L,
            formattedDate = "2026-09-29",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 10000.0,
            maxDisplacement = null,
            activeTimeSec = 1800L,
            totalTimeSec = 1800L,
            avgSpeedMps = 5.5,
            ascentMeters = 100L,
            descentMeters = 100L,
            minAltitude = null,
            maxAltitude = null,
            startLatLng = startLatLng,
            endLatLng = endLatLng,
            startLocationName = startLocationName,
            endLocationName = endLocationName,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Test
    fun testWorkoutMarkers_withLocations_formatsLocationTitles() = runBlocking {
        val workoutData = createWorkoutData(
            startLatLng = startPos,
            endLatLng = endPos,
            startLocationName = "Zuhause",
            endLocationName = "Büro"
        )

        val markers = repository.getWorkoutMarkers(workoutData)

        val startMarker = markers.find { it.iconResId == R.drawable.control_start }
        val stopMarker = markers.find { it.iconResId == R.drawable.control_stop }

        assertNotNull("Start marker must be present", startMarker)
        assertEquals("Start: Zuhause", startMarker?.title)

        assertNotNull("Stop marker must be present", stopMarker)
        assertEquals("Stop: Büro", stopMarker?.title)
    }

    @Test
    fun testWorkoutMarkers_withoutLocations_defaultsToStandardTitles() = runBlocking {
        val workoutData = createWorkoutData(
            startLatLng = startPos,
            endLatLng = endPos,
            startLocationName = null,
            endLocationName = null
        )

        val markers = repository.getWorkoutMarkers(workoutData)

        val startMarker = markers.find { it.iconResId == R.drawable.control_start }
        val stopMarker = markers.find { it.iconResId == R.drawable.control_stop }

        assertNotNull("Start marker must be present", startMarker)
        assertEquals("Start", startMarker?.title)

        assertNotNull("Stop marker must be present", stopMarker)
        assertEquals("Stop", stopMarker?.title)
    }

    @Test
    fun testWorkoutMarkers_withoutCoordinates_omitsMarkers() = runBlocking {
        val workoutData = createWorkoutData(
            startLatLng = null,
            endLatLng = null
        )

        val markers = repository.getWorkoutMarkers(workoutData)

        val startMarker = markers.find { it.iconResId == R.drawable.control_start }
        val stopMarker = markers.find { it.iconResId == R.drawable.control_stop }

        assertNull("Start marker must be omitted when start coordinate is null", startMarker)
        assertNull("Stop marker must be omitted when end coordinate is null", stopMarker)
    }
}
