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
import android.database.Cursor
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusGroupData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.time.LocalDateTime

/**
 * Unit tests verifying WorkoutRepository.reloadExportStatusesFor updates in-memory workoutName
 * when refreshed in WorkoutSummaries database (ATT-902).
 */
class WorkoutRepositoryExportStatusTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
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

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        val mockStravaHelper = mockk<StravaUploadDbHelper>(relaxed = true)
        every { mockStravaHelper.getStravaActivityData(any()) } returns null
        StravaUploadDbHelper.resetForTesting(mockStravaHelper)

        mockkConstructor(ExportStatusDataProvider::class)
        every { anyConstructed<ExportStatusDataProvider>().createGroupData(any(), any()) } returns mockk(relaxed = true)

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        WorkoutRepository.resetForTesting(null)
        StravaUploadDbHelper.resetForTesting(null)
        unmockkStatic(WorkoutSummariesDatabaseManager::class)
        unmockkAll()
    }

    private fun seedWorkouts(workouts: List<WorkoutData>) {
        val field: Field = WorkoutRepository::class.java.getDeclaredField("_allWorkouts")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(repository) as MutableStateFlow<List<WorkoutData>>
        flow.value = workouts
    }

    private fun createDummyWorkout(workoutId: Long, fileBaseName: String, workoutName: String): WorkoutData {
        return WorkoutData(
            id = workoutId,
            finished = true,
            fileBaseName = fileBaseName,
            workoutName = workoutName,
            sportId = 1L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1700000000L,
            formattedDate = "2026-09-11",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.of(2026, 9, 11, 10, 0),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 5000.0,
            maxDisplacement = null,
            activeTimeSec = 1500L,
            totalTimeSec = 1600L,
            avgSpeedMps = 3.33,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = null,
            maxAltitude = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            extremaRows = emptyList(),
            laps = emptyList(),
            exportStatuses = emptyList()
        )
    }

    @Test
    fun testReloadExportStatuses_refreshesWorkoutNameFromDatabase() = runBlocking {
        val workout = createDummyWorkout(101L, "2026_09_11_10_00_00", "2026_09_11_10_00_00")
        seedWorkouts(listOf(workout))

        every {
            mockSummariesDb.getString(101L, WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME)
        } returns "Strava Lunch Run"

        val method: Method = WorkoutRepository::class.java.getDeclaredMethod("reloadExportStatusesFor", String::class.java)
        method.isAccessible = true
        method.invoke(repository, "2026_09_11_10_00_00")

        var attempts = 0
        while (repository.allWorkouts.value.first().workoutName != "Strava Lunch Run" && attempts < 20) {
            delay(50)
            attempts++
        }

        assertEquals("Strava Lunch Run", repository.allWorkouts.value.first().workoutName)
    }

    private fun installMockMapper(targetRepo: WorkoutRepository, mockMapper: WorkoutDataMapper) {
        val field: Field = WorkoutRepository::class.java.getDeclaredField("mapper\$delegate")
        field.isAccessible = true
        field.set(targetRepo, lazyOf(mockMapper))
    }

    @Test
    // Verifies REQ-DAT-046 / TST-DAT-042.1
    fun testLoadWorkout_cachedWorkout_preservesExportStatusesAndTransientFieldsWhileUpdatingDbFields_TST_DAT_042_1() = runBlocking {
        val cachedExtrema = listOf(
            ExtremaDataRow(
                sensorLabel = "HR",
                unitLabel = "bpm",
                minValue = "110",
                minLatLng = LatLng(48.10, 11.50),
                avgValue = "145",
                maxValue = "175",
                maxLatLng = LatLng(48.20, 11.60)
            )
        )
        val cachedExportStatuses = listOf(
            ExportStatusGroupData(hasContent = true, groupTitle = "Strava", succeededLine = "Uploaded"),
            ExportStatusGroupData(hasContent = true, groupTitle = "Dropbox", succeededLine = "Uploaded"),
            ExportStatusGroupData(hasContent = true, groupTitle = "Google Drive", succeededLine = "Uploaded")
        )
        val cachedWorkout = createDummyWorkout(101L, "2026_09_11_10_00_00", "Morning Run").copy(
            mapPolyline = "encoded_poly_abc",
            encodedAltitudes = "encoded_alt_123",
            encodedDistances = "encoded_dist_456",
            extremaRows = cachedExtrema,
            exportStatuses = cachedExportStatuses
        )
        seedWorkouts(listOf(cachedWorkout))

        val dbWorkout = createDummyWorkout(101L, "2026_09_11_10_00_00", "Updated Loop Ride").copy(
            sportId = 2L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            equipmentId = 7L,
            equipmentName = "Road Bike",
            clusterId = 42L,
            clusterName = "Morning Loop",
            radarTrafficSummary = RadarTrafficSummary(totalVehicles = 5),
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            extremaRows = listOf(
                ExtremaDataRow(
                    sensorLabel = "HR",
                    unitLabel = "bpm",
                    minValue = "110",
                    minLatLng = null,
                    avgValue = "145",
                    maxValue = "175",
                    maxLatLng = null
                )
            ),
            exportStatuses = emptyList()
        )
        val enrichedRadar = RadarTrafficSummary(totalVehicles = 12, closePassCount = 1)
        val mockCursor = mockk<Cursor>(relaxed = true) {
            every { moveToFirst() } returns true
        }
        every { mockSummariesDb.getWorkoutCursor(101L) } returns mockCursor

        val mockMapper = mockk<WorkoutDataMapper>()
        every { mockMapper.fromCursor(mockCursor) } returns dbWorkout

        val spyRepo = spyk(repository)
        installMockMapper(spyRepo, mockMapper)
        every { spyRepo.getRadarTrafficSummary("2026_09_11_10_00_00") } returns enrichedRadar

        spyRepo.loadWorkout(101L)

        val updated = spyRepo.allWorkouts.value.single { it.id == 101L }
        assertEquals(cachedExportStatuses, updated.exportStatuses)
        assertEquals("encoded_poly_abc", updated.mapPolyline)
        assertEquals("encoded_alt_123", updated.encodedAltitudes)
        assertEquals("encoded_dist_456", updated.encodedDistances)
        assertEquals(LatLng(48.10, 11.50), updated.extremaRows.single().minLatLng)
        assertEquals(LatLng(48.20, 11.60), updated.extremaRows.single().maxLatLng)
        assertEquals(42L, updated.clusterId)
        assertEquals("Morning Loop", updated.clusterName)
        assertEquals("Updated Loop Ride", updated.workoutName)
        assertEquals(2L, updated.sportId)
        assertEquals("Cycling", updated.sportName)
        assertEquals(7L, updated.equipmentId)
        assertEquals("Road Bike", updated.equipmentName)
        assertEquals(enrichedRadar, updated.radarTrafficSummary)

        val emitted = spyRepo.initialWorkoutLoaded.value
        assertEquals(101L, emitted?.id)
        assertEquals(42L, emitted?.clusterId)
        assertEquals("Morning Loop", emitted?.clusterName)
        assertEquals("Updated Loop Ride", emitted?.workoutName)
        assertEquals(enrichedRadar, emitted?.radarTrafficSummary)
    }

    @Test
    // Verifies REQ-DAT-046 / TST-DAT-042.2
    fun testLoadWorkout_uncachedWorkout_insertsSortedDescendingAndEmitsInitialWorkout_TST_DAT_042_2() = runBlocking {
        val newest = createDummyWorkout(200L, "2026_09_11_12_00_00", "Newest").copy(startTimeS = 1700002000L)
        val oldest = createDummyWorkout(100L, "2026_09_11_08_00_00", "Oldest").copy(startTimeS = 1700000000L)
        seedWorkouts(listOf(newest, oldest))

        val middleFromDb = createDummyWorkout(150L, "2026_09_11_10_00_00", "Middle").copy(startTimeS = 1700001000L)
        val mockCursor = mockk<Cursor>(relaxed = true) {
            every { moveToFirst() } returns true
        }
        every { mockSummariesDb.getWorkoutCursor(150L) } returns mockCursor

        val mockMapper = mockk<WorkoutDataMapper>()
        every { mockMapper.fromCursor(mockCursor) } returns middleFromDb
        installMockMapper(repository, mockMapper)

        repository.loadWorkout(150L)

        assertEquals(listOf(200L, 150L, 100L), repository.allWorkouts.value.map { it.id })
        assertEquals(150L, repository.initialWorkoutLoaded.value?.id)
    }
}
