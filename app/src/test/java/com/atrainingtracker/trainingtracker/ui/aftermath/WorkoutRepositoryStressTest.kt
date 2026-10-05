package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.MyUnits
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * High-volume synthetic stress test verifying that [WorkoutRepository.loadAllWorkouts]
 * streams >1,600 workouts (here 2,000) completely to completion without throwing
 * [IllegalStateException] from CursorWindow boundary crossing (ATT-2309 / REQ-STB-013 / TST-STB-013).
 */
class WorkoutRepositoryStressTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockStravaUploadDbHelper = mockk<StravaUploadDbHelper>(relaxed = true)
    private val mockClusterDbManager = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
    private val mockLapsDbManager = mockk<LapsDatabaseManager>(relaxed = true)
    private val mockSportTypeDbManager = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDbHelper = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockExportManager = mockk<ExportManager>(relaxed = true)

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
        val mockLbm = mockk<LocalBroadcastManager>(relaxed = true)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLbm

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDbManager)

        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDbManager

        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDbManager
        every { mockSportTypeDbManager.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportTypeDbManager.getUIName(any()) } returns "Cycling"
        every { mockSportTypeDbManager.getStravaName(any()) } returns "Ride"

        mockkConstructor(StravaUploadDbHelper::class)
        every { anyConstructed<StravaUploadDbHelper>().getStravaActivityDataForWorkouts(any()) } returns emptyMap()
        every { anyConstructed<StravaUploadDbHelper>().getStravaActivityData(any()) } returns null

        mockkConstructor(EquipmentDbHelper::class)
        mockkConstructor(ExportManager::class)

        mockkConstructor(com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider::class)
        every { anyConstructed<com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider>().createGroupData(any(), any()) } returns mockk(relaxed = true)

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun loadAllWorkouts_stressTest2000Workouts_completesSuccessfullyWithoutException() = runBlocking {
        // Arrange: 2,000 synthetic workout rows (exceeds the 1,600 boundary by 25%)
        val totalWorkouts = 2000
        val rows = (1..totalWorkouts).map { id ->
            listOf(
                id.toLong(),
                1L,
                -1L,
                "2026-10-04 10:00:00",
                "base_$id",
                25000.0,
                "",
                "",
                "",
                "Ride #$id",
                -1L,
                1,
                0,
                0,
                0,
                0,
                48.0,
                11.0,
                48.2,
                11.2,
                3600L,
                3700L,
                6.94,
                250L,
                250L,
                "Stress test ride $id",
                null,
                null
            )
        }

        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSummariesManager.getCursorForAllWorkouts() } returns cursor

        // Act
        repository.loadAllWorkouts()

        // Assert: All 2,000 workouts are in allWorkouts
        val loaded = repository.allWorkouts.value
        assertEquals(totalWorkouts, loaded.size)

        // Zero backward seeks occurred across the 2,000-row traversal
        verify(exactly = 0) { cursor.moveToPosition(any()) }
    }
}
