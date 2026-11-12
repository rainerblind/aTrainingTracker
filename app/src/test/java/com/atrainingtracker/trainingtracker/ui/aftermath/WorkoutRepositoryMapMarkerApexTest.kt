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
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying differentiated apex map marker labeling for A->B point-to-point routes
 * versus closed-loop routes (REQ-SET-097.5 / ATT-7315 / TST-SET-097.5).
 */
class WorkoutRepositoryMapMarkerApexTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private lateinit var repository: WorkoutRepository

    // P2P route: ~2.7 km apart (> 150m)
    private val startP2p = LatLng(48.7000, 9.0000)
    private val endP2p = LatLng(48.7200, 9.0200)
    private val apexP2p = LatLng(48.7100, 9.0100)

    // Closed-loop route: ~67 m apart (< 150m)
    private val startLoop = LatLng(48.7000, 9.0000)
    private val endLoop = LatLng(48.7005, 9.0005)
    private val apexLoop = LatLng(48.7100, 9.0100)

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
        every { mockApplication.getString(R.string.route_apex) } returns "Scheitelpunkt"
        every { mockApplication.getString(R.string.max_line_distance) } returns "weiteste Entfernung"

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
        startLatLng: LatLng?,
        endLatLng: LatLng?,
        maxDisplacementLatLng: LatLng?
    ): WorkoutData {
        return WorkoutData(
            id = 1L,
            finished = true,
            fileBaseName = "2026-11-12-10-00-00",
            workoutName = "Commute Bike Ride",
            sportId = 1L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = 1700000000L,
            formattedDate = "2026-11-12",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = true,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 15000.0,
            maxDisplacement = 7500.0,
            activeTimeSec = 2400L,
            totalTimeSec = 2400L,
            avgSpeedMps = 6.25,
            ascentMeters = 150L,
            descentMeters = 120L,
            minAltitude = null,
            maxAltitude = null,
            startLatLng = startLatLng,
            endLatLng = endLatLng,
            maxDisplacementLatLng = maxDisplacementLatLng,
            startLocationName = null,
            endLocationName = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Test
    fun testMapMarkerApexLabel_pointToPoint_usesRouteApex() = runBlocking {
        val workout = createWorkoutData(
            startLatLng = startP2p,
            endLatLng = endP2p,
            maxDisplacementLatLng = apexP2p
        )

        assertTrue("Workout must be classified as point-to-point (d >= 150m)", workout.isPointToPoint)

        val markers = repository.getWorkoutMarkers(workout)
        val apexMarker = markers.find { it.iconResId == R.drawable.ic_distance }

        assertNotNull("Apex distance marker must be present", apexMarker)
        assertEquals("Point-to-point route apex must be labeled 'Scheitelpunkt'", "Scheitelpunkt", apexMarker?.title)
    }

    @Test
    fun testMapMarkerApexLabel_closedLoop_usesMaxLineDistance() = runBlocking {
        val workout = createWorkoutData(
            startLatLng = startLoop,
            endLatLng = endLoop,
            maxDisplacementLatLng = apexLoop
        )

        assertFalse("Workout must be classified as closed-loop (d < 150m)", workout.isPointToPoint)

        val markers = repository.getWorkoutMarkers(workout)
        val apexMarker = markers.find { it.iconResId == R.drawable.ic_distance }

        assertNotNull("Apex distance marker must be present", apexMarker)
        assertEquals("Closed-loop turnaround point must be labeled 'weiteste Entfernung'", "weiteste Entfernung", apexMarker?.title)
    }

    @Test
    fun testWorkoutCluster_isPointToPoint() {
        val p2pCluster = WorkoutCluster(
            name = "Sindelfingen -> Schönaich Commute",
            probableSportId = 1L,
            startLat = startP2p.latitude,
            startLng = startP2p.longitude,
            endLat = endP2p.latitude,
            endLng = endP2p.longitude,
            maxDispLat = apexP2p.latitude,
            maxDispLng = apexP2p.longitude,
            refDistance = 15000.0,
            hitCount = 5
        )
        assertTrue("WorkoutCluster must evaluate isPointToPoint=true for d >= 150m", p2pCluster.isPointToPoint)

        val loopCluster = WorkoutCluster(
            name = "Forest Loop Roundtrip",
            probableSportId = 1L,
            startLat = startLoop.latitude,
            startLng = startLoop.longitude,
            endLat = endLoop.latitude,
            endLng = endLoop.longitude,
            maxDispLat = apexLoop.latitude,
            maxDispLng = apexLoop.longitude,
            refDistance = 15000.0,
            hitCount = 5
        )
        assertFalse("WorkoutCluster must evaluate isPointToPoint=false for d < 150m", loopCluster.isPointToPoint)
    }
}
