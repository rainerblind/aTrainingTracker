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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportStatus
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [WorkoutRepository.loadAllWorkouts] eliminates the ~15-queries/workout N+1 loop
 * (`REQ-DAT-045.1`, `REQ-DAT-045.3`, `TST-DAT-041.4`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutRepositoryBatchLoadTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockStravaUploadDb = mockk<StravaUploadDbHelper>(relaxed = true)
    private val mockClusterDb = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
    private val mockLapsDb = mockk<LapsDatabaseManager>(relaxed = true)
    private val mockSportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockLocationsDb = mockk<KnownLocationsDatabaseManager>(relaxed = true)
    private val mockMetricsDb = mockk<AthleteMetricsDatabaseManager>(relaxed = true)
    private val mockExportStatusDb = mockk<ExportStatusDatabaseManager>(relaxed = true)

    private lateinit var repository: WorkoutRepository

    private val columns = listOf(
        WorkoutSummaries.C_ID,
        WorkoutSummaries.SPORT_ID,
        WorkoutSummaries.EQUIPMENT_ID,
        WorkoutSummaries.TIME_START,
        WorkoutSummaries.FILE_BASE_NAME,
        WorkoutSummaries.DISTANCE_TOTAL_m,
        WorkoutSummaries.MAP_POLYLINE,
        WorkoutSummaries.ALTITUDE_STREAM,
        WorkoutSummaries.DISTANCE_STREAM,
        WorkoutSummaries.WORKOUT_NAME,
        WorkoutSummaries.CLUSTER_ID,
        WorkoutSummaries.FINISHED,
        WorkoutSummaries.COMMUTE,
        WorkoutSummaries.TRAINER,
        WorkoutSummaries.RACE,
        WorkoutSummaries.UPLOAD_TO_STRAVA,
        WorkoutSummaries.BOUND_MIN_LAT,
        WorkoutSummaries.BOUND_MIN_LNG,
        WorkoutSummaries.BOUND_MAX_LAT,
        WorkoutSummaries.BOUND_MAX_LNG,
        WorkoutSummaries.TIME_ACTIVE_s,
        WorkoutSummaries.TIME_TOTAL_s,
        WorkoutSummaries.SPEED_AVERAGE_mps,
        WorkoutSummaries.ASCENDING,
        WorkoutSummaries.DESCENDING,
        WorkoutSummaries.DESCRIPTION,
        WorkoutSummaries.GOAL,
        WorkoutSummaries.METHOD
    )

    @Before
    fun setUp() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

        mockkStatic(LocalBroadcastManager::class)
        every { LocalBroadcastManager.getInstance(any()) } returns mockk(relaxed = true)

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)

        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb

        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDb
        every { mockSportDb.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportDb.getUIName(any()) } returns "Road Bike"
        every { mockSportDb.getStravaName(any()) } returns "Ride"

        StravaUploadDbHelper.resetForTesting(mockStravaUploadDb)
        EquipmentDbHelper.resetForTesting(mockEquipmentDb)

        mockkStatic(KnownLocationsDatabaseManager::class)
        every { KnownLocationsDatabaseManager.getInstance(any()) } returns mockLocationsDb

        io.mockk.mockkObject(AthleteMetricsDatabaseManager.Companion)
        every { AthleteMetricsDatabaseManager.getInstance(any()) } returns mockMetricsDb

        ExportStatusDatabaseManager.resetForTesting(mockExportStatusDb)
        ExportStatusRepository.resetForTesting(null)
        SettingsDataStore.setInstanceForTesting(mockk(relaxed = true))

        every { mockApplication.getString(any()) } returns "Label"
        val res = mockk<android.content.res.Resources>(relaxed = true)
        every { res.getQuantityString(any(), any(), *anyVararg()) } returns "1 FIT"
        every { mockApplication.resources } returns res

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        ExportStatusRepository.resetForTesting(null)
        ExportStatusDatabaseManager.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        StravaUploadDbHelper.resetForTesting(null)
        EquipmentDbHelper.resetForTesting(null)
        SettingsDataStore.setInstanceForTesting(null)
        unmockkAll()
    }

    @Test
    fun testLoadAllWorkouts_50WorkoutChunk_executesBoundedBatchQueriesAndZeroPerWorkoutFallbacks_TST_DAT_041_4() = runBlocking {
        val count = 50
        val rows = (1L..count).map { id ->
            listOf(
                id, 1L, 7L, "2026-11-21 06:00:00", "base_$id", 10_000.0, "", "", "",
                "Ride $id", 11L, 1, 0, 0, 0, 0, 48.0, 11.0, 48.1, 11.1,
                1800L, 1850L, 5.5, 100L, 100L, null, null, null
            )
        }
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSummariesManager.getCursorForAllWorkouts() } returns cursor

        val startPos = LatLng(48.1370, 11.5760)
        val outsidePos = LatLng(49.0000, 12.0000)
        every { mockSummariesManager.getExtremaForWorkouts(any()) } returns (1L..count).flatMap { id ->
            val pos = if (id == 2L) outsidePos else startPos
            listOf(
                WorkoutSummariesDatabaseManager.ExtremaRecord(id, SensorType.LATITUDE, ExtremaType.START, 0.0, pos),
                WorkoutSummariesDatabaseManager.ExtremaRecord(id, SensorType.LATITUDE, ExtremaType.END, 0.0, pos),
                WorkoutSummariesDatabaseManager.ExtremaRecord(id, SensorType.POWER, ExtremaType.AVG, 200.0, null)
            )
        }
        every { mockEquipmentDb.getEquipmentItems() } returns listOf(
            EquipmentDbHelper.EquipmentData(7L, "Aero Bike", BSportType.BIKE, 0, null, null)
        )
        every { mockClusterDb.getClusterNamesForIds(any()) } returns mapOf(11L to "Loop")
        every { mockLocationsDb.allLocations } returns listOf(
            KnownLocationsDatabaseManager.MyLocation(1L, startPos.latitude, startPos.longitude, "Home", 500.0, 250, 1)
        )
        every { mockMetricsDb.getEntriesByType(AthleteMetricType.FTP_WATTS) } returns listOf(
            AthleteMetricEntry(1L, 1L, AthleteMetricType.FTP_WATTS, 250.0)
        )
        every { mockMetricsDb.getEntriesByType(AthleteMetricType.WEIGHT_KG) } returns listOf(
            AthleteMetricEntry(2L, 1L, AthleteMetricType.WEIGHT_KG, 70.0)
        )
        every { mockExportStatusDb.getExportRowsForWorkouts(any()) } returns (1L..count).associate { id ->
            "base_$id" to listOf(
                ExportStatusDatabaseManager.ExportRow(ExportType.FILE, FileFormat.FIT, ExportStatus.FINISHED_SUCCESS, "OK")
            )
        }

        repository.loadAllWorkouts()

        val loaded = repository.allWorkouts.value
        assertEquals(50, loaded.size)
        assertEquals("Aero Bike", loaded[0].equipmentName)
        assertEquals("Loop", loaded[0].clusterName)
        assertEquals("Home", loaded[0].startLocationName)
        assertTrue(loaded[0].exportStatuses.isNotEmpty())

        // Verify strictly 1 batch call per auxiliary source for the 50-workout chunk (<= 12 total)
        verify(exactly = 1) { mockExportStatusDb.getExportRowsForWorkouts(any()) }
        verify(exactly = 1) { mockSummariesManager.getExtremaForWorkouts(any()) }
        verify(exactly = 1) { mockStravaUploadDb.getStravaActivityDataForWorkouts(any()) }
        verify(exactly = 1) { mockClusterDb.getClusterNamesForIds(any()) }
        verify(exactly = 1) { mockLapsDb.getLapsForWorkouts(any()) }
        verify(exactly = 1) { mockSummariesManager.getCardioPowerDynamicsForWorkouts(any()) }
        verify(exactly = 1) { mockEquipmentDb.getEquipmentItems() }
        verify(exactly = 1) { mockLocationsDb.allLocations }
        verify(exactly = 1) { mockMetricsDb.getEntriesByType(AthleteMetricType.FTP_WATTS) }
        verify(exactly = 1) { mockMetricsDb.getEntriesByType(AthleteMetricType.WEIGHT_KG) }
        verify(exactly = 1) { mockSportDb.getBSportType(1L) }

        // Zero per-workout N+1 fallbacks
        verify(exactly = 0) { mockExportStatusDb.getExportRows(any()) }
        verify(exactly = 0) { mockEquipmentDb.getEquipmentNameFromId(any()) }
        verify(exactly = 0) { mockLocationsDb.getMyLocation(any<LatLng>()) }
        verify(exactly = 0) { mockMetricsDb.getEntryEffectiveAt(any(), any()) }
        verify(exactly = 0) { mockSummariesManager.getCardioPowerDynamics(any()) }
        verify(exactly = 0) { mockSummariesManager.getExtremaValue(any(), any(), any()) }
        verify(exactly = 0) { mockSummariesManager.getExtremaPosition(any(), any(), any()) }
    }
}
