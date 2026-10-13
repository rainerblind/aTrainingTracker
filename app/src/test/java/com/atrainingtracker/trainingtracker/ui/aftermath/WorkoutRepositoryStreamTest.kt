package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.database.Cursor
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests verifying single-pass sequential workout streaming and exception shielding
 * in [WorkoutRepository.loadAllWorkouts] (ATT-2309 / REQ-STB-013 / TST-STB-013).
 */
class WorkoutRepositoryStreamTest {

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
        every { mockSportTypeDbManager.getBSportType(any()) } returns BSportType.RUN
        every { mockSportTypeDbManager.getUIName(any()) } returns "Running"
        every { mockSportTypeDbManager.getStravaName(any()) } returns "Run"

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

    private fun createSampleRow(id: Long, name: String = "Workout $id"): List<Any?> {
        return listOf(
            id,                                      // C_ID
            1L,                                      // SPORT_ID
            -1L,                                     // EQUIPMENT_ID
            "2026-10-04 10:00:00",                   // TIME_START
            "base_$id",                              // FILE_BASE_NAME
            5000.0,                                  // DISTANCE_TOTAL_m
            "",                                      // MAP_POLYLINE
            "",                                      // ALTITUDE_STREAM
            "",                                      // DISTANCE_STREAM
            name,                                    // WORKOUT_NAME
            -1L,                                     // CLUSTER_ID
            1,                                       // FINISHED
            0,                                       // COMMUTE
            0,                                       // TRAINER
            0,                                       // RACE
            0,                                       // UPLOAD_TO_STRAVA
            48.0,                                    // BOUND_MIN_LAT
            11.0,                                    // BOUND_MIN_LNG
            48.1,                                    // BOUND_MAX_LAT
            11.1,                                    // BOUND_MAX_LNG
            1800L,                                   // TIME_ACTIVE_s
            1850L,                                   // TIME_TOTAL_s
            2.77,                                    // SPEED_AVERAGE_mps
            50L,                                     // ASCENDING
            50L,                                     // DESCENDING
            "Test description",                      // DESCRIPTION
            "Test goal",                             // GOAL
            "Test method"                            // METHOD
        )
    }

    @Test
    fun loadAllWorkouts_singlePassTraversal_streamsWorkoutsWithoutBackwardSeeking() = runBlocking {
        // Arrange: 120 sample rows (spans 3 batches: 50, 50, 20)
        val rows = (1L..120L).map { createSampleRow(it) }
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSummariesManager.getCursorForAllWorkouts() } returns cursor

        // Act
        repository.loadAllWorkouts()

        // Assert: All 120 workouts loaded into allWorkouts
        val loaded = repository.allWorkouts.value
        assertEquals(120, loaded.size)
        assertEquals(1L, loaded[0].id)
        assertEquals(120L, loaded[119].id)

        // Verify that moveToPosition was NEVER called (zero backward seeking across CursorWindow)
        verify(exactly = 0) { cursor.moveToPosition(any()) }

        // Verify moveToFirst was called once
        verify(exactly = 1) { cursor.moveToFirst() }

        // Verify batch queries were executed in vectorized chunks
        verify(atLeast = 1) { mockSummariesManager.getExtremaForWorkouts(any()) }
        verify(atLeast = 1) { anyConstructed<StravaUploadDbHelper>().getStravaActivityDataForWorkouts(any()) }
    }

    @Test
    fun loadAllWorkouts_faultyRowException_shieldsExceptionAndContinuesStreaming() = runBlocking {
        // Arrange: 10 rows, where row index 4 throws IllegalStateException when reading column
        val rows = (1L..10L).map { createSampleRow(it) }
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSummariesManager.getCursorForAllWorkouts() } returns cursor

        // Make row at position 4 fail as if CursorWindow threw
        every { cursor.getLong(0) } answers {
            if (cursor.position == 4) {
                throw IllegalStateException("Couldn't read row 4, col 0 from CursorWindow.")
            }
            // Normal behavior: return the ID from the sample row
            rows[cursor.position][0] as Long
        }

        // Act: Must not throw or crash
        repository.loadAllWorkouts()

        // Assert: 9 workouts loaded successfully (row 4 was skipped safely)
        val loaded = repository.allWorkouts.value
        assertEquals(9, loaded.size)
        assertFalse(loaded.any { it.id == 5L }) // Row 4 corresponds to id 5L

        // Verify error was logged
        verify { Log.e(any<String>(), match { it.contains("error reading row at position 4") }, any()) }
    }

    @Test
    fun rawCursorSnapshot_readCursorSnapshot_extractsAllPrimitiveColumnsAccurately() {
        val row = createSampleRow(42L, "Evening Ride")
        val cursor = MockCursorFactory.create(columns, listOf(row))
        cursor.moveToFirst()

        val mapper = WorkoutDataMapper(
            context = mockApplication,
            workoutSummariesDatabaseManager = mockSummariesManager,
            sportTypeDatabaseManager = mockSportTypeDbManager,
            equipmentDbHelper = mockEquipmentDbHelper,
            stravaUploadDbHelper = mockStravaUploadDbHelper
        )

        val snapshot = mapper.readCursorSnapshot(cursor)
        assertEquals(42L, snapshot.workoutId)
        assertEquals("Evening Ride", snapshot.workoutName)
        assertEquals(5000.0, snapshot.totalDistance, 0.001)
        assertEquals(1800L, snapshot.activeTimeSec)
        assertTrue(snapshot.finished)
        assertEquals("base_42", snapshot.fileBaseName)
    }
}
