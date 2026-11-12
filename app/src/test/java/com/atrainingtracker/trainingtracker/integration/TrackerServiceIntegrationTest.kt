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

package com.atrainingtracker.trainingtracker.integration

import android.app.Notification
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.Cursor
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatListener
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.testutil.RealSqliteTestHelper
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseState
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * End-to-end integration tests verifying the integrated tracking pipeline across
 * TrackerService, simulated sensor layer (BANALServiceComm), LiveWorkoutSession,
 * WorkoutRepository, and authentic SQLite databases (REQ-TRK-075, TST-TRK-075, ATT-6185).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackerServiceIntegrationTest {

    private class TestableTrackerService : TrackerService() {
        var stopped = false
        var customAutoPauseConfig: AutoPauseConfig? = null

        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true
        override fun showTrackingInterruptedNotification() {}
        override fun performStopSelf() {
            stopped = true
        }
        override fun performStartForeground(id: Int, notification: Notification?, foregroundServiceType: Int) {}
        override fun getSportTypeId(): Long = 1L

        override fun getAutoPauseConfig(): AutoPauseConfig {
            return customAutoPauseConfig ?: super.getAutoPauseConfig()
        }
    }

    private class DirectExecutorService : AbstractExecutorService() {
        private var shutdown = false
        override fun shutdown() { shutdown = true }
        override fun shutdownNow(): List<Runnable> { shutdown = true; return emptyList() }
        override fun isShutdown(): Boolean = shutdown
        override fun isTerminated(): Boolean = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
        override fun execute(command: Runnable) {
            command.run()
        }
    }

    private lateinit var context: Context
    private lateinit var service: TestableTrackerService
    private lateinit var mockBanalComm: BANALServiceComm
    private var capturedRrBeatListener: RrBeatListener? = null
    private val simulatedSensorMap = mutableMapOf<SensorType, SensorData<*>>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Clean up pre-existing SQLite databases
        context.deleteDatabase("WorkoutSummaries.db")
        context.deleteDatabase("WorkoutSamples.db")
        context.deleteDatabase("Laps.db")
        context.deleteDatabase("WorkoutRr.db")

        // Seed TrainingApplication static fields
        val appField = TrainingApplication::class.java.getDeclaredField("cAppContext")
        appField.isAccessible = true
        appField.set(null, context)

        val prefField = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        prefField.isAccessible = true
        prefField.set(null, androidx.preference.PreferenceManager.getDefaultSharedPreferences(context))

        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        TrainingApplication.setAutoPaused(false, context)
        TrainingApplication.setManualPauseLock(false, context)

        // Reset WorkoutRepository singleton to pick up clean context
        WorkoutRepository.resetForTesting(null)

        // Build mock BANALServiceComm
        mockBanalComm = mockk(relaxed = true)
        every { mockBanalComm.bSportType } returns BSportType.BIKE
        every { mockBanalComm.sportTypeId } returns 1L
        every { mockBanalComm.databaseIdsOfActiveRemoteDevices } returns listOf(101L)
        every { mockBanalComm.accumulatedSensorTypeSet } returns setOf(
            SensorType.HR,
            SensorType.SPEED_mps,
            SensorType.CADENCE,
            SensorType.POWER,
            SensorType.DISTANCE_m,
            SensorType.ALTITUDE,
            SensorType.TIME_ACTIVE,
            SensorType.TIME_TOTAL
        )
        every { mockBanalComm.accumulatedGCDataString } returns ""
        every { mockBanalComm.addRrBeatListener(any()) } answers {
            capturedRrBeatListener = firstArg()
        }
        every { mockBanalComm.removeRrBeatListener(any()) } answers {
            capturedRrBeatListener = null
        }
        every { mockBanalComm.allSensorData } answers {
            simulatedSensorMap.values.toList()
        }
        every { mockBanalComm.getBestSensorData(any<SensorType>()) } answers {
            val type = firstArg<SensorType>()
            simulatedSensorMap[type]
        }

        // Instantiate testable service and attach context
        service = TestableTrackerService()
        val attachMethod: Method = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachMethod.isAccessible = true
        attachMethod.invoke(service, context)

        // Inject direct synchronous executors
        val directExecutor = DirectExecutorService()
        val dbExecutorField: Field = TrackerService::class.java.getDeclaredField("mDbExecutor")
        dbExecutorField.isAccessible = true
        dbExecutorField.set(service, directExecutor)

        val rrExecutorField: Field = TrackerService::class.java.getDeclaredField("mRrFlushExecutor")
        rrExecutorField.isAccessible = true
        rrExecutorField.set(service, directExecutor)

        // Inject relaxed mock ScheduledExecutorService to prevent uncontrollable background 1 Hz ticks
        val mockScheduler = mockk<java.util.concurrent.ScheduledExecutorService>(relaxed = true)
        val schedulerField: Field = TrackerService::class.java.getDeclaredField("mScheduler")
        schedulerField.isAccessible = true
        schedulerField.set(service, mockScheduler)

        // Inject mock BANALServiceComm
        val banalField: Field = TrackerService::class.java.getDeclaredField("mBanalService")
        banalField.isAccessible = true
        banalField.set(service, mockBanalComm)

        // Set mock TrainingApplication reference in service
        val mockApp = mockk<TrainingApplication>(relaxed = true)
        val appRefField: Field = TrackerService::class.java.getDeclaredField("mTrainingApplication")
        appRefField.isAccessible = true
        appRefField.set(service, mockApp)

        // Inject SportTypesRepository
        val mockSportRepo = mockk<com.atrainingtracker.trainingtracker.repositories.SportTypesRepository>(relaxed = true)
        every { mockSportRepo.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportRepo.getUIName(any()) } returns "Cycling"
        val sportRepoField: Field = TrackerService::class.java.getDeclaredField("mSportTypesRepository")
        sportRepoField.isAccessible = true
        sportRepoField.set(service, mockSportRepo)

        // Inject KnownLocationsRepository to avoid Geocoder / LocationNameResolver during start
        val mockLocationsRepo = mockk<com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository>(relaxed = true)
        val locationsRepoField: Field = TrackerService::class.java.getDeclaredField("mKnownLocationsRepository")
        locationsRepoField.isAccessible = true
        locationsRepoField.set(service, mockLocationsRepo)

        // Connect RR listener on service
        val rrListenerField: Field = TrackerService::class.java.getDeclaredField("mRrBeatListener")
        rrListenerField.isAccessible = true
        val listener = rrListenerField.get(service) as RrBeatListener
        mockBanalComm.addRrBeatListener(listener)
    }

    @After
    fun tearDown() {
        try {
            service.onDestroy()
        } catch (_: Throwable) {
        }
        unmockkAll()
        WorkoutRepository.resetForTesting(null)
        context.deleteDatabase("WorkoutSummaries.db")
        context.deleteDatabase("WorkoutSamples.db")
        context.deleteDatabase("Laps.db")
        context.deleteDatabase("WorkoutRr.db")
    }

    private fun setSimulatedSensor(type: SensorType, value: Any) {
        val sensorData = when (value) {
            is Int -> SensorData<Number>(type, value, value.toString(), null)
            is Double -> SensorData<Number>(type, value, value.toString(), null)
            is Float -> SensorData<Number>(type, value.toDouble(), value.toString(), null)
            is Long -> SensorData<Number>(type, value, value.toString(), null)
            else -> SensorData<Any>(type, value, value.toString(), null)
        }
        simulatedSensorMap[type] = sensorData
    }

    private fun getWorkoutId(): Long {
        val field = TrackerService::class.java.getDeclaredField("mWorkoutID")
        field.isAccessible = true
        return field.getLong(service)
    }

    private fun getBaseFileName(): String {
        val field = TrackerService::class.java.getDeclaredField("mBaseFileName")
        field.isAccessible = true
        return field.get(service) as String
    }

    private fun getSamplesTableName(): String {
        val field = TrackerService::class.java.getDeclaredField("mSamplesTableName")
        field.isAccessible = true
        return field.get(service) as String
    }

    private fun invokeSampleAndWriteToDb() {
        val method = TrackerService::class.java.getDeclaredMethod("sampleAndWriteToDb", BANALServiceComm::class.java)
        method.isAccessible = true
        method.invoke(service, mockBanalComm)
    }

    private fun invokeFlushLiveRrQueueSync(workoutId: Long) {
        val method = TrackerService::class.java.getDeclaredMethod("flushLiveRrQueueSync", java.lang.Long.TYPE)
        method.isAccessible = true
        method.invoke(service, workoutId)
    }

    // =========================================================================
    // Test Scenario 1: Start tracking -> receive simulated samples -> verify SQLite
    // (REQ-TRK-075.1, TST-TRK-075.1)
    // =========================================================================
    @Test
    fun startTracking_ingestsMultiSensorSamplesAndPopulatesSqlite() {
        val startIntent = Intent(context, TrackerService::class.java).apply {
            putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name)
        }
        service.onStartCommand(startIntent, 0, 1)

        val workoutId = getWorkoutId()
        val baseFileName = getBaseFileName()
        val samplesTable = getSamplesTableName()

        assertTrue("Workout ID should be > 0", workoutId > 0)
        assertNotNull("Base file name must be generated", baseFileName)
        assertNotNull("Samples table name must be resolved", samplesTable)

        // Simulate multi-sensor packet
        setSimulatedSensor(SensorType.TIME_ACTIVE, 1)
        setSimulatedSensor(SensorType.TIME_TOTAL, 1)
        setSimulatedSensor(SensorType.DISTANCE_m, 100.0)
        setSimulatedSensor(SensorType.SPEED_mps, 8.33)
        setSimulatedSensor(SensorType.HR, 145)
        setSimulatedSensor(SensorType.CADENCE, 85)
        setSimulatedSensor(SensorType.POWER, 220)
        setSimulatedSensor(SensorType.ALTITUDE, 250.0)
        setSimulatedSensor(SensorType.LATITUDE, 48.137)
        setSimulatedSensor(SensorType.LONGITUDE, 11.576)
        setSimulatedSensor(SensorType.ASCENT, 10.0)

        // Process live sample tick
        invokeSampleAndWriteToDb()

        // Deliver beat-to-beat RR interval
        assertNotNull("RR beat listener must be registered", capturedRrBeatListener)
        capturedRrBeatListener?.onRrBeat(RrBeatEvent(System.currentTimeMillis(), 800.0, 0, ProtocolType.SIMULATED, 0, 0))
        invokeFlushLiveRrQueueSync(workoutId)

        // Verify SQLite: WorkoutSummaries
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database
        assertNotNull(summariesDb)
        summariesDb!!.query(
            WorkoutSummaries.TABLE,
            null,
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue("Workout summary row must exist", cursor.moveToFirst())
            val finishedIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.FINISHED)
            val distIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)
            val activeIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s)
            assertEquals("Workout should be active (finished == 0)", 0, cursor.getInt(finishedIdx))
            assertEquals("Distance must be persisted", 100.0, cursor.getDouble(distIdx), 0.01)
            assertEquals("Active time must be persisted", 1, cursor.getInt(activeIdx))
        }

        // Verify SQLite: Dynamic Samples Table
        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context).database
        assertNotNull(samplesDb)
        samplesDb!!.query(samplesTable, null, null, null, null, null, null).use { cursor ->
            assertEquals("Samples table must have 1 row", 1, cursor.count)
            assertTrue(cursor.moveToFirst())
            val hrIdx = cursor.getColumnIndex(SensorType.HR.name)
            val speedIdx = cursor.getColumnIndex(SensorType.SPEED_mps.name)
            val cadenceIdx = cursor.getColumnIndex(SensorType.CADENCE.name)
            val powerIdx = cursor.getColumnIndex(SensorType.POWER.name)

            assertTrue("HR column must exist in samples table", hrIdx >= 0)
            assertEquals(145, cursor.getInt(hrIdx))
            assertTrue("Speed column must exist", speedIdx >= 0)
            assertEquals(8.33, cursor.getDouble(speedIdx), 0.01)
            assertTrue("Cadence column must exist", cadenceIdx >= 0)
            assertEquals(85, cursor.getInt(cadenceIdx))
            assertTrue("Power column must exist", powerIdx >= 0)
            assertEquals(220, cursor.getInt(powerIdx))
        }

        // Verify SQLite: RR Intervals
        val rrRepo = WorkoutRrRepository(WorkoutRrDatabaseManager.getInstance(context))
        val intervals = rrRepo.getIntervalsForWorkoutBlocking(workoutId)
        assertEquals("One RR interval must be persisted", 1, intervals.size)
        assertEquals(800.0f, intervals[0].rrMs, 0.01f)
    }

    // =========================================================================
    // Test Scenario 2: Pause tracking -> sample ingestion pauses & active time freezes
    // (REQ-TRK-075.2, TST-TRK-075.2)
    // =========================================================================
    @Test
    fun pauseTracking_suspendsSampleWritesAndFreezesActiveTime() {
        val startIntent = Intent(context, TrackerService::class.java).apply {
            putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name)
        }
        service.onStartCommand(startIntent, 0, 1)
        val workoutId = getWorkoutId()
        val samplesTable = getSamplesTableName()

        // 1. Ingest active sample 1
        setSimulatedSensor(SensorType.TIME_ACTIVE, 1)
        setSimulatedSensor(SensorType.TIME_TOTAL, 1)
        setSimulatedSensor(SensorType.SPEED_mps, 5.0)
        invokeSampleAndWriteToDb()

        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context).database!!
        assertEquals("Initial sample count must be 1", 1L, RealSqliteTestHelper.getRowCount(samplesDb, samplesTable))

        // 2. Pause tracking
        TrainingApplication.setTrackingModeForTesting(TrackingMode.PAUSED)

        // 3. Emit sample tick while paused (Total time advances to 5, active time frozen at 1)
        setSimulatedSensor(SensorType.TIME_ACTIVE, 1)
        setSimulatedSensor(SensorType.TIME_TOTAL, 5)
        invokeSampleAndWriteToDb()

        // Verify: No new rows in samples table
        assertEquals("Sample row count must not increase during pause", 1L, RealSqliteTestHelper.getRowCount(samplesDb, samplesTable))

        // Verify: WorkoutSummaries active time remains 1, while total time updated to 5
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database!!
        summariesDb.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.TIME_ACTIVE_s, WorkoutSummaries.TIME_TOTAL_s),
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Active time must remain frozen at 1s", 1, cursor.getInt(0))
            assertEquals("Total time must update to 5s during pause", 5, cursor.getInt(1))
        }
    }

    // =========================================================================
    // Test Scenario 3: Resume tracking -> active time resumes & samples continue writing
    // (REQ-TRK-075.3, TST-TRK-075.3)
    // =========================================================================
    @Test
    fun resumeTracking_resumesSampleIngestionAndActiveTimeAccumulation() {
        val startIntent = Intent(context, TrackerService::class.java).apply {
            putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name)
        }
        service.onStartCommand(startIntent, 0, 1)
        val workoutId = getWorkoutId()
        val samplesTable = getSamplesTableName()
        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context).database!!

        // Sample 1 (Active)
        setSimulatedSensor(SensorType.TIME_ACTIVE, 1)
        setSimulatedSensor(SensorType.TIME_TOTAL, 1)
        setSimulatedSensor(SensorType.SPEED_mps, 5.0)
        invokeSampleAndWriteToDb()
        assertEquals(1L, RealSqliteTestHelper.getRowCount(samplesDb, samplesTable))

        // Pause
        TrainingApplication.setTrackingModeForTesting(TrackingMode.PAUSED)
        setSimulatedSensor(SensorType.TIME_TOTAL, 5)
        invokeSampleAndWriteToDb()
        assertEquals(1L, RealSqliteTestHelper.getRowCount(samplesDb, samplesTable))

        // Resume tracking
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        service.autoPauseManager.onManualResume(false)

        // Sample 2 (Resumed active tracking)
        setSimulatedSensor(SensorType.TIME_ACTIVE, 2)
        setSimulatedSensor(SensorType.TIME_TOTAL, 6)
        setSimulatedSensor(SensorType.SPEED_mps, 6.0)
        invokeSampleAndWriteToDb()

        // Verify: Second sample successfully persisted to SQLite
        assertEquals("Sample row count must increment to 2 after resume", 2L, RealSqliteTestHelper.getRowCount(samplesDb, samplesTable))

        // Verify: Active time resumed in WorkoutSummaries
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database!!
        summariesDb.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.TIME_ACTIVE_s, WorkoutSummaries.TIME_TOTAL_s),
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Active time must resume accumulation to 2s", 2, cursor.getInt(0))
            assertEquals("Total time must advance to 6s", 6, cursor.getInt(1))
        }
    }

    // =========================================================================
    // Test Scenario 4: Auto-pause trigger on speed drop and resume on motion
    // (REQ-TRK-075.4, TST-TRK-075.4)
    // =========================================================================
    @Test
    fun autoPause_transitionsStateOnSpeedDropAndResumesOnMotion() {
        val startIntent = Intent(context, TrackerService::class.java).apply {
            putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name)
        }
        service.onStartCommand(startIntent, 0, 1)

        // Custom config: threshold = 5 km/h (~1.39 m/s), debounce = 1s, resume debounce = 1s
        service.customAutoPauseConfig = AutoPauseConfig(
            bikeEnabled = true,
            bikeSpeedThreshold = 5,
            bikeDebounceSec = 1,
            bikeResumeDebounceSec = 1
        )

        // 1. Initial moving speed (5.0 m/s = 18 km/h > 5 km/h)
        setSimulatedSensor(SensorType.SPEED_mps, 5.0)
        setSimulatedSensor(SensorType.TIME_ACTIVE, 1)
        setSimulatedSensor(SensorType.TIME_TOTAL, 1)
        invokeSampleAndWriteToDb()
        assertEquals(AutoPauseState.TRACKING, service.autoPauseManager.currentState)

        // 2. Drop speed to stationary (0.2 m/s = 0.72 km/h < 5 km/h)
        setSimulatedSensor(SensorType.SPEED_mps, 0.2)
        invokeSampleAndWriteToDb() // Tick 1: enters PAUSE_DEBOUNCE_PENDING or AUTO_PAUSED based on debounce

        // Debounce tick: advance time/tick to trigger AUTO_PAUSED
        invokeSampleAndWriteToDb()
        assertEquals("Auto-pause should transition to AUTO_PAUSED upon speed drop", AutoPauseState.AUTO_PAUSED, service.autoPauseManager.currentState)

        // 3. Motion resumption (6.0 m/s > 5 km/h)
        setSimulatedSensor(SensorType.SPEED_mps, 6.0)
        invokeSampleAndWriteToDb() // Tick 1
        invokeSampleAndWriteToDb() // Tick 2: resume debounce triggers TRACKING
        assertEquals("Auto-pause should resume to TRACKING upon renewed motion", AutoPauseState.TRACKING, service.autoPauseManager.currentState)
    }

    // =========================================================================
    // Test Scenario 5: Stop and finish tracking -> summary calculation and persistence
    // (REQ-TRK-075.5, TST-TRK-075.5)
    // =========================================================================
    @Test
    fun stopTracking_persistsLapsAndFinalSummaryMetricsInSqlite() {
        val startIntent = Intent(context, TrackerService::class.java).apply {
            putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name)
        }
        service.onStartCommand(startIntent, 0, 1)
        val workoutId = getWorkoutId()

        // Feed telemetry across several ticks
        setSimulatedSensor(SensorType.TIME_ACTIVE, 60)
        setSimulatedSensor(SensorType.TIME_TOTAL, 70)
        setSimulatedSensor(SensorType.DISTANCE_m, 1200.0)
        setSimulatedSensor(SensorType.SPEED_mps, 10.0)
        setSimulatedSensor(SensorType.HR, 140)
        setSimulatedSensor(SensorType.ASCENT, 35.0)
        setSimulatedSensor(SensorType.LAP_NR, 1)
        setSimulatedSensor(SensorType.TIME_LAP, 60)
        setSimulatedSensor(SensorType.DISTANCE_m_LAP, 1200.0)
        invokeSampleAndWriteToDb()

        // Second tick with higher HR to test extrema
        setSimulatedSensor(SensorType.HR, 175)
        setSimulatedSensor(SensorType.TIME_ACTIVE, 120)
        setSimulatedSensor(SensorType.TIME_TOTAL, 130)
        setSimulatedSensor(SensorType.DISTANCE_m, 2400.0)
        setSimulatedSensor(SensorType.ASCENT, 50.0)
        setSimulatedSensor(SensorType.TIME_LAP, 120)
        setSimulatedSensor(SensorType.DISTANCE_m_LAP, 2400.0)
        invokeSampleAndWriteToDb()

        // Finalize workout
        service.endWorkout()

        // Verify SQLite: WorkoutSummaries finished = 1 and correct totals
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database!!
        summariesDb.query(
            WorkoutSummaries.TABLE,
            null,
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue("Workout summary must exist", cursor.moveToFirst())
            val finishedIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.FINISHED)
            val distIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)
            val activeIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s)
            val ascentIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.ASCENDING)

            assertEquals("FINISHED flag must be committed to 1", 1, cursor.getInt(finishedIdx))
            assertEquals("Total distance must be persisted", 2400.0, cursor.getDouble(distIdx), 0.01)
            assertEquals("Active time must be persisted", 120, cursor.getInt(activeIdx))
            assertEquals("Ascent must be persisted", 50.0, cursor.getDouble(ascentIdx), 0.01)
        }

        // Verify SQLite: Extrema Table contains Max HR = 175
        val repo = WorkoutRepository.getInstance(context)
        val maxHr = repo.getExtremaValue(workoutId, SensorType.HR, ExtremaType.MAX)
        assertNotNull("Max HR extrema must be recorded", maxHr)
        assertEquals(175.0, maxHr!!, 0.01)

        // Verify SQLite: Laps table has persisted lap
        val laps = repo.getLaps(workoutId)
        assertFalse("At least one lap must be saved on finish", laps.isEmpty())
        val lap = laps[0]
        assertEquals(1L, lap.lapNr)
        assertEquals(2400.0, lap.distanceTotalM, 0.01)
    }
}
