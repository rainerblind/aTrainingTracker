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
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import com.google.android.gms.maps.model.LatLng
import java.time.LocalDateTime
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying language-independent [SensorType]-keyed [ExtremaDataRow] matching,
 * single-reload cluster mutations, and coalesced `pendingNotifyInitialLoadedIds` propagation
 * in [WorkoutRepository] (REQ-DAT-048.5, TST-DAT-044.5, ATT-8128).
 */
class WorkoutRepositoryExtremaAndClusterTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockStravaUploadDbHelper = mockk<StravaUploadDbHelper>(relaxed = true)
    private val mockClusterDbManager = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
    private val mockLapsDbManager = mockk<LapsDatabaseManager>(relaxed = true)
    private val mockSportTypeDbManager = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDbHelper = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockPeriodsRepo = mockk<PeriodsRepository>(relaxed = true)
    private val mockClusterEngine = mockk<WorkoutClusterEngine>(relaxed = true)
    private val mockClusterRepo = mockk<WorkoutClusterRepository>(relaxed = true)
    private val mockDeletionHelper = mockk<WorkoutDeletionHelper>(relaxed = true)

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
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
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

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDbManager)
        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDbManager

        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDbManager
        every { mockSportTypeDbManager.getBSportType(any()) } returns BSportType.RUN
        every { mockSportTypeDbManager.getUIName(any()) } returns "Running"
        every { mockSportTypeDbManager.getStravaName(any()) } returns "Run"

        every { mockStravaUploadDbHelper.getStravaActivityDataForWorkouts(any()) } returns emptyMap()
        every { mockStravaUploadDbHelper.getStravaActivityData(any()) } returns null
        StravaUploadDbHelper.resetForTesting(mockStravaUploadDbHelper)
        EquipmentDbHelper.resetForTesting(mockEquipmentDbHelper)

        mockkConstructor(ExportManager::class)
        mockkConstructor(ExportStatusDataProvider::class)
        every { anyConstructed<ExportStatusDataProvider>().createGroupData(any(), any()) } returns mockk(relaxed = true)

        PeriodsRepository.setInstanceForTesting(mockPeriodsRepo)
        WorkoutClusterEngine.resetForTesting(mockClusterEngine)
        WorkoutClusterRepository.resetForTesting(mockClusterRepo)

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
        repository.deletionHelperInstance = mockDeletionHelper
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        PeriodsRepository.resetInstanceForTesting()
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutClusterRepository.resetForTesting(null)
        WorkoutRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        StravaUploadDbHelper.resetForTesting(null)
        EquipmentDbHelper.resetForTesting(null)
        unmockkAll()
    }

    private fun createCursorForWorkout(id: Long, name: String = "Morning Run", clusterId: Long = 10L) =
        MockCursorFactory.create(
            columns = columns,
            rows = listOf(
                listOf(
                    id, 1L, -1L, "2026-11-22 08:00:00", "base_$id",
                    5000.0, "", "", "", name, clusterId, 1,
                    0, 0, 0, 0,
                    null, null, null, null,
                    1500L, 1520L, 3.33, 50L, 45L, "", "", ""
                )
            )
        )

    @Suppress("UNCHECKED_CAST")
    private fun seedWorkouts(vararg workouts: WorkoutData) {
        val field = WorkoutRepository::class.java.getDeclaredField("_allWorkouts")
        field.isAccessible = true
        val stateFlow = field.get(repository) as MutableStateFlow<List<WorkoutData>>
        stateFlow.value = workouts.toList()
    }

    private fun sampleWorkout(id: Long, rows: List<ExtremaDataRow>): WorkoutData = WorkoutData(
        id = id,
        finished = true,
        fileBaseName = "base_$id",
        workoutName = "Run",
        sportId = 1L,
        sportName = "Running",
        bSportType = BSportType.RUN,
        startTimeS = 1_763_798_400L,
        formattedDate = "2026-11-22",
        formattedTime = "08:00",
        localDateTime = LocalDateTime.of(2026, 11, 22, 8, 0),
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
        totalTimeSec = 1520L,
        avgSpeedMps = 3.33,
        ascentMeters = 50L,
        descentMeters = 45L,
        minAltitude = null,
        maxAltitude = null,
        description = null,
        goal = null,
        method = null,
        stravaSportName = null,
        extremaRows = rows,
        laps = emptyList(),
        exportStatuses = emptyList()
    )

    @Suppress("DEPRECATION")
    @Test
    fun updateExtremaValueInMemory_matchesBySensorTypeAcrossLocaleSwitchAndFallsBackWhenNull_TST_DAT_044_5() {
        val hrRowBuiltInEnglish = ExtremaDataRow(
            sensorLabel = "HR",
            unitLabel = "bpm",
            minValue = "110",
            avgValue = "145",
            maxValue = "170",
            sensorType = SensorType.HR
        )
        val powerRowBuiltInEnglish = ExtremaDataRow(
            sensorLabel = "Power",
            unitLabel = "W",
            minValue = "100",
            avgValue = "200",
            maxValue = "350",
            sensorType = SensorType.POWER
        )
        seedWorkouts(sampleWorkout(50L, listOf(hrRowBuiltInEnglish, powerRowBuiltInEnglish)))

        // Simulate runtime language change to German ("HF" / "Leistung" instead of "HR" / "Power")
        every { mockApplication.getString(SensorType.HR.shortNameId) } returns "HF"
        every { mockApplication.getString(SensorType.POWER.shortNameId) } returns "Leistung"

        val peakPos = LatLng(48.1351, 11.5820)
        repository.updateExtremaValueInMemory(50L, SensorType.HR, ExtremaType.MAX, 188.0, peakPos)

        // All rows carry sensorType -> matched strictly by enum identity, never calling getString(HR.shortNameId)
        val afterHrUpdate = repository.allWorkouts.value.first { it.id == 50L }
        val updatedHrRow = afterHrUpdate.extremaRows.first { it.sensorType == SensorType.HR }
        assertEquals(SensorType.HR.myFormatter.format(188.0), updatedHrRow.maxValue)
        assertEquals(peakPos, updatedHrRow.maxLatLng)
        verify(exactly = 0) { mockApplication.getString(SensorType.HR.shortNameId) }

        // Legacy row with sensorType == null falls back cleanly to lazy localized label match
        val legacyPowerRowWithoutEnum = ExtremaDataRow(
            sensorLabel = "Leistung",
            unitLabel = "W",
            minValue = "100",
            avgValue = "200",
            maxValue = "350",
            sensorType = null
        )
        seedWorkouts(sampleWorkout(51L, listOf(legacyPowerRowWithoutEnum)))
        repository.updateExtremaValueInMemory(51L, SensorType.POWER, ExtremaType.MAX, 410.0, null)
        val afterPowerUpdate = repository.allWorkouts.value.first { it.id == 51L }
        val updatedPowerRow = afterPowerUpdate.extremaRows.first { it.sensorLabel == "Leistung" }
        assertEquals(SensorType.POWER.myFormatter.format(410.0), updatedPowerRow.maxValue)
    }

    @Test
    fun addOrUpdateWorkout_preservesExtremaPositionsAcrossLocaleLabelChangeViaSensorType_TST_DAT_044_5() {
        val minPos = LatLng(48.10, 11.50)
        val maxPos = LatLng(48.20, 11.60)
        val existingRowEn = ExtremaDataRow(
            sensorLabel = "HR",
            unitLabel = "bpm",
            minValue = "110",
            minLatLng = minPos,
            avgValue = "145",
            maxValue = "175",
            maxLatLng = maxPos,
            sensorType = SensorType.HR
        )
        seedWorkouts(sampleWorkout(60L, listOf(existingRowEn)))

        val dbRowDeWithoutPositions = ExtremaDataRow(
            sensorLabel = "HF",
            unitLabel = "bpm",
            minValue = "110",
            minLatLng = null,
            avgValue = "146",
            maxValue = "178",
            maxLatLng = null,
            sensorType = SensorType.HR
        )
        val addOrUpdate = WorkoutRepository::class.java.getDeclaredMethod("addOrUpdateWorkout", WorkoutData::class.java)
        addOrUpdate.isAccessible = true
        addOrUpdate.invoke(repository, sampleWorkout(60L, listOf(dbRowDeWithoutPositions)))

        val mergedRow = repository.allWorkouts.value.first { it.id == 60L }.extremaRows.single()
        assertEquals("HF", mergedRow.sensorLabel)
        assertEquals(minPos, mergedRow.minLatLng)
        assertEquals(maxPos, mergedRow.maxLatLng)
    }

    @Test
    fun assignAndUnassignCluster_executeSingleDbReloadAndNotifyInitialWorkoutLoaded_TST_DAT_044_5() = runBlocking {
        seedWorkouts(sampleWorkout(77L, emptyList()))
        every { mockSummariesManager.getWorkoutCursor(77L) } answers { createCursorForWorkout(77L, "Commute #3", clusterId = 9L) }

        val received = mutableListOf<WorkoutData>()
        val firstLatch = CountDownLatch(1)
        val observer = Observer<WorkoutData> {
            received.add(it)
            firstLatch.countDown()
        }
        repository.initialWorkoutLoaded.observeForever(observer)
        try {
            repository.assignClusterToWorkout(77L, 9L)
            assertTrue("assignClusterToWorkout must notify initialWorkoutLoaded", firstLatch.await(5, TimeUnit.SECONDS))
            delay(150)

            verify(exactly = 1) { mockSummariesManager.getWorkoutCursor(77L) }
            assertEquals(1, received.size)
            assertEquals(9L, received[0].clusterId)

            val unassignJob = repository.unassignClusterFromWorkout(77L)
            unassignJob.join()

            verify(exactly = 2) { mockSummariesManager.getWorkoutCursor(77L) }
            assertEquals(2, received.size)
        } finally {
            repository.initialWorkoutLoaded.removeObserver(observer)
        }
    }

    @Test
    fun reloadWorkoutDataInternal_coalescedNotifyInitialLoadedStillFiresAfterInFlightPlainReload_TST_DAT_044_5() = runBlocking {
        seedWorkouts(sampleWorkout(88L, emptyList()))

        val enteredFirstRead = CountDownLatch(1)
        val releaseFirstRead = CountDownLatch(1)
        val readCount = AtomicInteger(0)

        every { mockSummariesManager.getWorkoutCursor(88L) } answers {
            val call = readCount.incrementAndGet()
            if (call == 1) {
                enteredFirstRead.countDown()
                releaseFirstRead.await(5, TimeUnit.SECONDS)
            }
            createCursorForWorkout(88L, "Pass $call", clusterId = if (call == 1) -1L else 42L)
        }

        val notifiedLatch = CountDownLatch(1)
        var notifiedWorkout: WorkoutData? = null
        val observer = Observer<WorkoutData> {
            notifiedWorkout = it
            notifiedLatch.countDown()
        }
        repository.initialWorkoutLoaded.observeForever(observer)
        try {
            // Start a plain reloadWorkoutData(88L) that holds activeReloadWorkoutIds
            val firstReload = async(Dispatchers.IO) { repository.reloadWorkoutData(88L) }
            assertTrue("First reload must enter DB read", enteredFirstRead.await(5, TimeUnit.SECONDS))

            // While pass 1 is in flight, assignClusterToWorkout arrives requesting notifyInitialLoaded = true
            repository.assignClusterToWorkout(88L, 42L)
            delay(100) // Allow assignClusterToWorkout to mark dirtyReloadWorkoutIds + pendingNotifyInitialLoadedIds

            // Release pass 1 -> must coalesce into pass 2 and post initialWorkoutLoaded!
            releaseFirstRead.countDown()
            firstReload.await()

            assertTrue("Coalesced notifyInitialLoaded must fire on follow-up pass", notifiedLatch.await(5, TimeUnit.SECONDS))
            assertNotNull(notifiedWorkout)
            assertEquals(42L, notifiedWorkout!!.clusterId)
            assertEquals("Expected exactly 2 sequential DB reads (plain + coalesced notify)", 2, readCount.get())
        } finally {
            repository.initialWorkoutLoaded.removeObserver(observer)
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun reloadWorkoutDataInternal_cleansUpTrackingSetsOnNormalExitAndException_TST_DAT_044_5() = runBlocking {
        seedWorkouts(sampleWorkout(99L, emptyList()))
        val activeField = WorkoutRepository::class.java.getDeclaredField("activeReloadWorkoutIds").apply { isAccessible = true }
        val dirtyField = WorkoutRepository::class.java.getDeclaredField("dirtyReloadWorkoutIds").apply { isAccessible = true }
        val pendingField = WorkoutRepository::class.java.getDeclaredField("pendingNotifyInitialLoadedIds").apply { isAccessible = true }
        val activeSet = activeField.get(repository) as Set<Long>
        val dirtySet = dirtyField.get(repository) as Set<Long>
        val pendingSet = pendingField.get(repository) as Set<Long>

        // 1. Normal completion releases cleanly inside while-else without leaving residue
        every { mockSummariesManager.getWorkoutCursor(99L) } answers { createCursorForWorkout(99L, "Normal", clusterId = 5L) }
        repository.reloadWorkoutData(99L)
        assertTrue(activeSet.isEmpty())
        assertTrue(dirtySet.isEmpty())
        assertTrue(pendingSet.isEmpty())

        // 2. Abnormal exception triggers guarded finally cleanup so subsequent reloads still execute cleanly
        every { mockSummariesManager.getWorkoutCursor(99L) } throws IllegalStateException("Transient SQLite error")
        try {
            repository.reloadWorkoutData(99L)
            org.junit.Assert.fail("Expected IllegalStateException to propagate")
        } catch (_: IllegalStateException) {
            // expected
        }
        assertTrue(activeSet.isEmpty())
        assertTrue(dirtySet.isEmpty())
        assertTrue(pendingSet.isEmpty())

        every { mockSummariesManager.getWorkoutCursor(99L) } answers { createCursorForWorkout(99L, "Recovered", clusterId = 6L) }
        repository.reloadWorkoutData(99L)
        assertEquals("Recovered", repository.allWorkouts.value.first { it.id == 99L }.workoutName)
    }
}
