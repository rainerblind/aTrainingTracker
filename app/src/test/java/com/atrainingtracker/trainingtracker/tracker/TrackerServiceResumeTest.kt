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

package com.atrainingtracker.trainingtracker.tracker

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.sensor.SensorValueType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.EncodedStreams
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.ExtremaRecord
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Unit test suite verifying targeted single-row indexed query, bounds safety,
 * asynchronous BANALService deferred resume table name synchronization,
 * ordered onServiceConnected callbacks, sample write error surfacing,
 * and LiveWorkoutSession rehydration for TrackerService crash/user resumption
 * (REQ-DAT-002, TST-DAT-002, ATT-3549; REQ-TRK-026, TST-TRK-018, ATT-3980;
 * REQ-TRK-031, TST-TRK-023, ATT-3987).
 */
class TrackerServiceResumeTest {

    private class TestableTrackerService : TrackerService() {
        val eventOrder = mutableListOf<String>()
        var notifiedWorkoutId: Long? = null
        var stoppedSelf = false
        var interruptedNotificationShown = false

        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true

        public override fun recreateValuesWhenResuming() {
            eventOrder.add("recreateValuesWhenResuming")
            super.recreateValuesWhenResuming()
        }

        override fun createNewLap() {
            eventOrder.add("createNewLap:workoutId=$workoutID:table=$samplesTableName")
        }

        override fun onSearchingFinished() {
            eventOrder.add("onSearchingFinished:workoutId=$workoutID:table=$samplesTableName")
        }

        override fun notifyTrackingStarted(workoutId: Long) {
            notifiedWorkoutId = workoutId
        }

        override fun showTrackingInterruptedNotification() {
            interruptedNotificationShown = true
        }

        override fun performStopSelf() {
            stoppedSelf = true
        }

        override fun performStartForeground(id: Int, notification: Notification?, foregroundServiceType: Int) {}
        override fun sendBroadcast(intent: Intent?) {}
        override fun getPackageName(): String = "com.atrainingtracker"
        override fun performSuperOnDestroy() {}
        override fun unregisterReceiver(receiver: BroadcastReceiver?) {}
        override fun unbindService(conn: ServiceConnection) {}
        override fun endWorkout() {}
        override fun guessCommuteAndTrainer() {}
        fun callFinalizeLiveSession() = super.finalizeLiveSession()
    }

    private class DirectExecutorService : AbstractExecutorService() {
        private var isShutDown = false
        override fun shutdown() { isShutDown = true }
        override fun shutdownNow(): List<Runnable> = emptyList()
        override fun isShutdown(): Boolean = isShutDown
        override fun isTerminated(): Boolean = isShutDown
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
        override fun execute(command: Runnable) { command.run() }
    }

    private lateinit var service: TestableTrackerService
    private val mockDbManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockDb = mockk<SQLiteDatabase>(relaxed = true)
    private val mockSamplesManager = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
    private val mockSamplesDb = mockk<SQLiteDatabase>(relaxed = true)
    private val mockCursor = mockk<Cursor>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(BANALService::class)
        every { BANALService.setInitialSensorValue(any(), any<Int>()) } returns Unit
        every { BANALService.setInitialSensorValue(any(), any<Double>()) } returns Unit
        every { BANALService.checkOrInitializeLocationDevices() } returns Unit
        every { BANALService.isSearching() } returns false

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isManualPauseLocked() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        every { TrainingApplication.isPaused() } returns false

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } just Runs
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } just Runs
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } just Runs
        every { anyConstructed<ContentValues>().keySet() } returns emptySet()

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockDbManager
        every { mockDbManager.database } returns mockDb

        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesManager
        every { mockSamplesManager.database } returns mockSamplesDb

        service = TestableTrackerService()

        val dbExecutorField: Field = TrackerService::class.java.getDeclaredField("mDbExecutor")
        dbExecutorField.isAccessible = true
        dbExecutorField.set(service, DirectExecutorService())
    }

    @After
    fun tearDown() {
        try {
            service.onDestroy()
        } catch (_: Exception) {
        }
        unmockkAll()
    }

    private fun stubResumeCursor(
        baseFileName: String = "2026-10-15_120000",
        workoutId: Long = 42L,
        timeTotal: Int = 3600,
        timeActive: Int = 3450,
        calories: Int = 550,
        laps: Int = 3,
        distance: Double = 12500.5
    ) {
        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                any(),
                null,
                null,
                null,
                null,
                any(),
                any()
            )
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns true

        val expectedColumns = arrayOf(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m
        )

        expectedColumns.forEachIndexed { index, colName ->
            every { mockCursor.getColumnIndexOrThrow(colName) } returns index
        }

        every { mockCursor.getString(0) } returns baseFileName
        every { mockCursor.getLong(1) } returns workoutId
        every { mockCursor.getInt(2) } returns timeTotal
        every { mockCursor.getInt(3) } returns timeActive
        every { mockCursor.getInt(4) } returns calories
        every { mockCursor.getInt(5) } returns laps
        every { mockCursor.getDouble(6) } returns distance
    }

    @Test
    fun testRecreateValuesWhenResuming_queriesTargetedProjectionAndAppliesState() {
        val projectionSlot = slot<Array<String>>()
        val orderBySlot = slot<String>()
        val limitSlot = slot<String>()

        stubResumeCursor()
        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                capture(projectionSlot),
                null,
                null,
                null,
                null,
                capture(orderBySlot),
                capture(limitSlot)
            )
        } returns mockCursor

        service.recreateValuesWhenResuming()

        val expectedColumns = arrayOf(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m
        )

        // 1. Verify query arguments
        assertArrayEquals(expectedColumns, projectionSlot.captured)
        assertEquals("${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} DESC", orderBySlot.captured)
        assertEquals("1", limitSlot.captured)

        // 2. Verify instance fields in TrackerService (including mSamplesTableName per REQ-TRK-026.1)
        assertEquals("2026-10-15_120000", service.baseFileName)
        assertEquals("\"2026-10-15_120000\"", service.samplesTableName)
        assertEquals(42L, service.workoutID)
        assertEquals(3600, service.timeTotal_s)
        assertEquals(3450, service.timeActive_s)
        assertEquals(12500.5, service.distanceTotal_m, 0.001)

        // 3. Verify BANALService accumulators initialized
        verify(exactly = 1) { BANALService.setInitialSensorValue(SensorType.TIME_TOTAL, 3600) }
        verify(exactly = 1) { BANALService.setInitialSensorValue(SensorType.TIME_ACTIVE, 3450) }
        verify(exactly = 1) { BANALService.setInitialSensorValue(SensorType.CALORIES, 550) }
        verify(exactly = 1) { BANALService.setInitialSensorValue(SensorType.LAP_NR, 3) }
        verify(exactly = 1) { BANALService.setInitialSensorValue(SensorType.DISTANCE_m, 12500.5) }

        // 4. Verify deterministic try-with-resources cursor closure
        verify(exactly = 1) { mockCursor.close() }
    }

    @Test
    fun testRecreateValuesWhenResuming_emptyTable_logsWarningAndEarlyReturnsWithoutMutation() {
        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                any(),
                null,
                null,
                null,
                null,
                any(),
                any()
            )
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns false

        service.recreateValuesWhenResuming()

        // State remains unmutated / default
        assertNull(service.baseFileName)
        assertNull(service.samplesTableName)
        assertEquals(0L, service.workoutID)
        assertEquals(0, service.timeTotal_s)
        assertEquals(0, service.timeActive_s)
        assertEquals(0.0, service.distanceTotal_m, 0.001)

        // BANALService never called
        verify(exactly = 0) { BANALService.setInitialSensorValue(any(), any<Int>()) }
        verify(exactly = 0) { BANALService.setInitialSensorValue(any(), any<Double>()) }

        // Warning logged and cursor closed
        verify(exactly = 1) { Log.w("TrackerService", "recreateValuesWhenResuming: No workout summary record found to resume.") }
        verify(exactly = 1) { mockCursor.close() }
    }

    @Test
    fun testRecreateValuesWhenResuming_missingColumn_failsFastWithoutPartialMutation() {
        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                any(),
                null,
                null,
                null,
                null,
                any(),
                any()
            )
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns true
        every {
            mockCursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME)
        } throws IllegalArgumentException("Column not found")

        service.recreateValuesWhenResuming()

        // State remains unmutated
        assertNull(service.baseFileName)
        assertNull(service.samplesTableName)
        assertEquals(0L, service.workoutID)

        // Error logged, BANALService never called, cursor closed
        verify(exactly = 1) { Log.e(eq("TrackerService"), any<String>(), any<IllegalArgumentException>()) }
        verify(exactly = 0) { BANALService.setInitialSensorValue(any(), any<Int>()) }
        verify(exactly = 1) { mockCursor.close() }
    }

    /**
     * TST-TRK-018 Vector 1: Verifies that applyResumedState updates mSamplesTableName,
     * completes mTableInitializationFuture, and notifies tracking started with the restored workoutId.
     */
    @Test
    fun testApplyResumedState_updatesSamplesTableNameAndCompletesFuture() {
        val futureField: Field = TrackerService::class.java.getDeclaredField("mTableInitializationFuture")
        futureField.isAccessible = true
        val pendingFuture = CompletableFuture<Void>()
        futureField.set(service, pendingFuture)

        val state = TrackerService.ResumedWorkoutState(
            "2026-06-15_100000",
            77L,
            1800,
            1750,
            400,
            2,
            8500.0
        )

        service.applyResumedState(state)

        assertEquals("2026-06-15_100000", service.baseFileName)
        assertEquals("\"2026-06-15_100000\"", service.samplesTableName)
        assertTrue("mTableInitializationFuture must be completed by applyResumedState", pendingFuture.isDone && !pendingFuture.isCompletedExceptionally)
        assertEquals(77L, service.notifiedWorkoutId)
    }

    /**
     * TST-TRK-018 Vector 2a: Simulates deferred RESUME_SERVICE_RECREATION when mBanalService is null
     * during onStartCommand() and connects asynchronously via mBanalConnection.onServiceConnected().
     * Verifies mSamplesTableName is never "\"null\"", recreateValuesWhenResuming() runs before
     * onSearchingFinished(), and subsequent sampleAndWriteToDb() calls insert into the resumed table.
     */
    @Test
    fun testDeferredResumeServiceRecreation_whenBanalServiceConnectsLater_restoresTableNameAndWritesSamplesToResumedTable() {
        stubResumeCursor(baseFileName = "2026-06-15_120000", workoutId = 42L, laps = 3)

        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.RESUME_SERVICE_RECREATION.name

        // 1. onStartCommand runs while mBanalService == null
        service.onStartCommand(intent, 0, 1)

        // mSamplesTableName must NOT be "\"null\"", and future must remain pending until BANALService connects
        assertNotEquals("\"null\"", service.samplesTableName)
        assertNull(service.samplesTableName)
        assertFalse("mTableInitializationFuture must be incomplete while waiting for BANALService", service.tableInitializationFuture.isDone)

        // Idempotency check: a second rapid onStartCommand preserves the same pending future instance
        val firstPendingFuture = service.tableInitializationFuture
        service.onStartCommand(intent, 0, 1)
        assertSame("Repeated onStartCommand must preserve active pending future idempotently", firstPendingFuture, service.tableInitializationFuture)

        // 2. BANALService connects asynchronously
        val mockBanalComm = mockk<BANALServiceComm>(relaxed = true)
        val hrData = SensorData<Number>(SensorType.HR, 148, "148", null)
        every { mockBanalComm.allSensorData } returns listOf(hrData)
        every { mockBanalComm.getBestSensorData(any()) } returns null

        service.banalConnectionForTesting.onServiceConnected(
            ComponentName("com.atrainingtracker", "BANALService"),
            mockBanalComm
        )

        // 3. Verify ordering: recreateValuesWhenResuming ran BEFORE onSearchingFinished, with valid workoutId and table
        assertEquals(
            listOf(
                "recreateValuesWhenResuming",
                "onSearchingFinished:workoutId=42:table=\"2026-06-15_120000\""
            ),
            service.eventOrder
        )
        assertEquals("2026-06-15_120000", service.baseFileName)
        assertEquals("\"2026-06-15_120000\"", service.samplesTableName)
        assertTrue("mTableInitializationFuture must complete on service connection", service.tableInitializationFuture.isDone)
        assertEquals(42L, service.notifiedWorkoutId)

        // 4. Invoke sampleAndWriteToDb() and verify samples are inserted into "\"2026-06-15_120000\"" (never "\"null\"")
        val insertedTableSlot = slot<String>()
        every { mockSamplesDb.insertOrThrow(capture(insertedTableSlot), null, any()) } returns 1L

        val sampleMethod: Method = TrackerService::class.java.getDeclaredMethod("sampleAndWriteToDb")
        sampleMethod.isAccessible = true
        sampleMethod.invoke(service)

        assertEquals("\"2026-06-15_120000\"", insertedTableSlot.captured)
        verify(exactly = 1) { mockSamplesDb.setTransactionSuccessful() }
        verify(exactly = 1) { mockDb.setTransactionSuccessful() }
    }

    /**
     * TST-TRK-018 Vector 2b: Simulates deferred RESUME_BY_USER when mBanalService is null during
     * onStartCommand() and connects asynchronously via mBanalConnection.onServiceConnected().
     * Verifies recreateValuesWhenResuming() runs BEFORE createNewLap() and onSearchingFinished().
     */
    @Test
    fun testDeferredResumeByUser_whenBanalServiceConnectsLater_recreatesValuesBeforeCreateNewLapAndSearchingFinished() {
        stubResumeCursor(baseFileName = "2026-06-15_143000", workoutId = 99L, laps = 2)

        // Set mCreateNewLapWhenConnectedToBanalService = true to verify ordering against createNewLap()
        val createLapField: Field = TrackerService::class.java.getDeclaredField("mCreateNewLapWhenConnectedToBanalService")
        createLapField.isAccessible = true
        createLapField.set(service, true)

        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.RESUME_BY_USER.name

        service.onStartCommand(intent, 0, 1)

        assertNull(service.samplesTableName)
        assertFalse(service.tableInitializationFuture.isDone)

        val mockBanalComm = mockk<BANALServiceComm>(relaxed = true)
        service.banalConnectionForTesting.onServiceConnected(
            ComponentName("com.atrainingtracker", "BANALService"),
            mockBanalComm
        )

        assertEquals(
            listOf(
                "recreateValuesWhenResuming",
                "createNewLap:workoutId=99:table=\"2026-06-15_143000\"",
                "onSearchingFinished:workoutId=99:table=\"2026-06-15_143000\""
            ),
            service.eventOrder
        )
        assertEquals("\"2026-06-15_143000\"", service.samplesTableName)
        assertTrue(service.tableInitializationFuture.isDone)
        assertEquals(99L, service.notifiedWorkoutId)
    }

    /**
     * TST-TRK-018 Vector 3: Verifies that WorkoutSamplesDatabaseManager.getTableName() rejects
     * null, empty, blank, or literal "null" strings with IllegalArgumentException, and that
     * TrackerService.resolveSamplesTableNameSafely catches it safely at the service boundary.
     */
    @Test
    fun testGetTableName_rejectsNullEmptyOrLiteralNullString() {
        val invalidInputs = listOf(null, "", "   ", "null", "NULL", " null ")
        for (input in invalidInputs) {
            try {
                WorkoutSamplesDatabaseManager.getTableName(input)
                fail("Expected IllegalArgumentException for input: '$input'")
            } catch (_: IllegalArgumentException) {
                // Expected
            }
            assertNull(
                "resolveSamplesTableNameSafely must return null for invalid input '$input'",
                TrackerService.resolveSamplesTableNameSafely(input)
            )
        }

        assertEquals("\"2026-06-15_120000\"", WorkoutSamplesDatabaseManager.getTableName("2026-06-15_120000"))
        assertEquals("\"2026-06-15_120000\"", TrackerService.resolveSamplesTableNameSafely("2026-06-15_120000"))
    }

    /**
     * TST-TRK-018 Vector 4: Verifies that handleMissingColumns self-heals when a table is missing
     * for a valid mBaseFileName, and returns false (preventing silent transaction commit) when
     * an unrecoverable SQLiteException occurs.
     */
    @Test
    fun testHandleMissingColumns_selfHealsMissingTableWhenBaseFileNameValid_andRejectsUnrecoverableError() {
        val baseFileNameField: Field = TrackerService::class.java.getDeclaredField("mBaseFileName")
        baseFileNameField.isAccessible = true
        baseFileNameField.set(service, "2026-06-15_160000")

        val values = mockk<ContentValues>(relaxed = true)
        every { values.keySet() } returns setOf("HR")
        val name2Type = mapOf("HR" to SensorValueType.INTEGER)

        val mockSchemaCursor = mockk<Cursor>(relaxed = true)
        every { mockSchemaCursor.getColumnIndex(any()) } returns 0
        every { mockSamplesDb.query("\"2026-06-15_160000\"", null, null, null, null, null, null, "1") } returns mockSchemaCursor
        every { mockSamplesDb.insertOrThrow("\"2026-06-15_160000\"", null, any()) } returns 1L

        // Case 1: Missing table with valid mBaseFileName -> self-heals by calling createNewTable and retrying insertOrThrow
        val missingTableEx = mockk<SQLiteException>(relaxed = true) {
            every { message } returns "no such table: \"2026-06-15_160000\" (code 1 SQLITE_ERROR)"
        }
        val recovered = service.handleMissingColumns(
            mockSamplesDb,
            "\"2026-06-15_160000\"",
            values,
            name2Type,
            missingTableEx
        )
        assertTrue("handleMissingColumns must self-heal when mBaseFileName is valid", recovered)
        verify(exactly = 1) { mockSamplesManager.createNewTable("2026-06-15_160000", any()) }
        verify(exactly = 1) { mockSamplesDb.insertOrThrow("\"2026-06-15_160000\"", null, any()) }

        // Case 2: Unrecoverable SQLiteException (e.g. disk full / corrupt DB) -> logs Log.e and returns false
        val diskFullEx = mockk<SQLiteException>(relaxed = true) {
            every { message } returns "database or disk is full (code 13 SQLITE_FULL)"
        }
        val unrecoverable = service.handleMissingColumns(
            mockSamplesDb,
            "\"2026-06-15_160000\"",
            values,
            name2Type,
            diskFullEx
        )
        assertFalse("handleMissingColumns must return false on unrecoverable SQLiteException", unrecoverable)
        verify(atLeast = 1) { Log.e(eq("TrackerService"), any<String>(), any<SQLiteException>()) }
    }

    /**
     * TST-TRK-023.1 & TST-TRK-023.2: Verifies that applyResumedState instantiates mLiveSession
     * and rehydrates streams (polyline, altitude, distance) and sensor extrema (MIN, MAX, AVG, count)
     * before completing mTableInitializationFuture and notifying tracking started.
     */
    @Test
    fun testApplyResumedState_instantiatesAndRehydratesLiveSession_fromEncodedStreamsAndExtrema() {
        val workoutId = 88L
        val p1 = LatLng(48.1000, 11.5000)
        val p2 = LatLng(48.1100, 11.5100)
        val encodedPoly = PolyUtil.encode(listOf(p1, p2))
        val encodedAlt = NumericalEncodingUtils.encodeDoubles(listOf(500.0, 515.0))
        val encodedDist = NumericalEncodingUtils.encodeDoubles(listOf(0.0, 600.0))

        every { mockDbManager.getEncodedStreams(workoutId) } returns EncodedStreams(encodedPoly, encodedAlt, encodedDist)
        every { mockDbManager.getExtremaForWorkouts(listOf(workoutId)) } returns listOf(
            ExtremaRecord(workoutId, SensorType.HR, ExtremaType.MIN, 115.0, p1, null),
            ExtremaRecord(workoutId, SensorType.HR, ExtremaType.MAX, 172.0, p2, null),
            ExtremaRecord(workoutId, SensorType.HR, ExtremaType.AVG, 145.0, null, 80)
        )

        val state = TrackerService.ResumedWorkoutState(
            "2026-10-18_090000",
            workoutId,
            120,
            100,
            85,
            1,
            600.0
        )

        service.applyResumedState(state)

        val liveSessionField: Field = TrackerService::class.java.getDeclaredField("mLiveSession")
        liveSessionField.isAccessible = true
        val liveSession = liveSessionField.get(service) as? LiveWorkoutSession
        assertNotNull("mLiveSession must be non-null after applyResumedState", liveSession)
        assertEquals(workoutId, liveSession!!.workoutId)
        assertEquals(2, liveSession.rawLatLngs.size)
        assertEquals(2, liveSession.sampledLatLngs.size)
        assertEquals(p1.latitude, liveSession.startLatLng.latitude, 1e-4)
        assertEquals(p2.latitude, liveSession.lastLatLng.latitude, 1e-4)
        assertEquals(listOf(500.0, 515.0), liveSession.sampledAltitudes)
        assertEquals(listOf(0.0, 600.0), liveSession.sampledDistances)

        val hrStats = liveSession.sensorStats[SensorType.HR]
        assertNotNull(hrStats)
        assertEquals(115.0, hrStats!!.min, 1e-4)
        assertEquals(172.0, hrStats.max, 1e-4)
        assertEquals(80, hrStats.count)
        assertEquals(145.0, hrStats.average, 1e-4)
        assertEquals(workoutId, service.notifiedWorkoutId)
    }

    /**
     * TST-TRK-023.3: Verifies that while mIsRehydrating is true (during asynchronous DB rehydration),
     * sampleAndWriteToDb() still writes raw SQLite samples but does not mutate mLiveSession or throw
     * ConcurrentModificationException; once mIsRehydrating is cleared, subsequent ticks update mLiveSession.
     */
    @Test
    fun testSampleAndWriteToDb_whileIsRehydratingTrue_skipsLiveSessionMutationWithoutRace() {
        val workoutId = 91L
        val liveSession = LiveWorkoutSession(workoutId, setOf(SensorType.HR))

        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        val baseFileNameField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        val samplesTableField = TrackerService::class.java.getDeclaredField("mSamplesTableName").apply { isAccessible = true }
        val liveSessionField = TrackerService::class.java.getDeclaredField("mLiveSession").apply { isAccessible = true }
        val isRehydratingField = TrackerService::class.java.getDeclaredField("mIsRehydrating").apply { isAccessible = true }
        val banalServiceField = TrackerService::class.java.getDeclaredField("mBanalService").apply { isAccessible = true }

        workoutIdField.set(service, workoutId)
        baseFileNameField.set(service, "2026-10-18_100000")
        samplesTableField.set(service, "\"2026-10-18_100000\"")
        liveSessionField.set(service, liveSession)
        isRehydratingField.set(service, true)

        val mockBanalComm = mockk<BANALServiceComm>(relaxed = true)
        val hrData = SensorData<Number>(SensorType.HR, 155, "155", null)
        every { mockBanalComm.allSensorData } returns listOf(hrData)
        every { mockBanalComm.getBestSensorData(any()) } returns null
        every { mockBanalComm.getBestSensorData(SensorType.HR) } returns hrData
        banalServiceField.set(service, mockBanalComm)

        every { mockSamplesDb.insertOrThrow("\"2026-10-18_100000\"", null, any()) } returns 1L

        val sampleMethod: Method = TrackerService::class.java.getDeclaredMethod("sampleAndWriteToDb")
        sampleMethod.isAccessible = true

        // 1. Tick while mIsRehydrating == true -> writes to DB, skips mLiveSession mutation
        sampleMethod.invoke(service)
        assertNull("mLiveSession must not be mutated while mIsRehydrating is true", liveSession.sensorStats[SensorType.HR])
        verify(exactly = 1) { mockSamplesDb.insertOrThrow("\"2026-10-18_100000\"", null, any()) }

        // 2. Clear mIsRehydrating -> next tick updates mLiveSession
        isRehydratingField.set(service, false)
        sampleMethod.invoke(service)
        assertEquals("mLiveSession must record sample once mIsRehydrating is false", 1, liveSession.sensorStats[SensorType.HR]!!.count)
        assertEquals(155.0, liveSession.sensorStats[SensorType.HR]!!.max, 1e-4)
    }

    /**
     * TST-TRK-023.4 Boundary Check: Verifies that if MAP_POLYLINE is corrupt/malformed,
     * rehydrateLiveSession catches the exception for the polyline block independently and still
     * restores valid ALTITUDE_STREAM, DISTANCE_STREAM, and sensor extrema.
     */
    @Test
    fun testRehydrateLiveSession_corruptPolyline_stillRestoresAltitudeDistanceAndExtrema() {
        val workoutId = 95L
        val encodedAlt = NumericalEncodingUtils.encodeDoubles(listOf(420.0, 435.0))
        val encodedDist = NumericalEncodingUtils.encodeDoubles(listOf(0.0, 500.0))

        // Corrupt polyline string that fails PolyUtil.decode
        every { mockDbManager.getEncodedStreams(workoutId) } returns EncodedStreams("corrupt_polyline_\u0000_invalid", encodedAlt, encodedDist)
        every { mockDbManager.getExtremaForWorkouts(listOf(workoutId)) } returns listOf(
            ExtremaRecord(workoutId, SensorType.HR, ExtremaType.MIN, 120.0, null, null),
            ExtremaRecord(workoutId, SensorType.HR, ExtremaType.MAX, 165.0, null, null),
            ExtremaRecord(workoutId, SensorType.HR, ExtremaType.AVG, 142.0, null, 50)
        )

        val state = TrackerService.ResumedWorkoutState(
            "2026-10-18_110000",
            workoutId,
            60,
            50,
            40,
            1,
            500.0
        )

        service.applyResumedState(state)

        val liveSessionField = TrackerService::class.java.getDeclaredField("mLiveSession").apply { isAccessible = true }
        val liveSession = liveSessionField.get(service) as LiveWorkoutSession

        // Altitude and distance streams plus extrema must still be restored despite polyline failure
        assertEquals(listOf(420.0, 435.0), liveSession.sampledAltitudes)
        assertEquals(listOf(0.0, 500.0), liveSession.sampledDistances)
        assertEquals(165.0, liveSession.sensorStats[SensorType.HR]!!.max, 1e-4)
        assertEquals(50, liveSession.sensorStats[SensorType.HR]!!.count)
    }

    /**
     * TST-TRK-023.5: Verifies end-to-end resume + post-resume sampling + finalizeLiveSession()
     * persists the combined route polyline, combined streams, preserved start location, updated end
     * location, and merged extrema to WorkoutSummariesDatabaseManager and WorkoutRepository.
     */
    @Test
    fun testResumedSession_finalizeLiveSession_persistsCombinedRouteStreamsAndMergedExtrema() {
        val workoutId = 105L
        val p1 = LatLng(48.1000, 11.5000)
        val p2 = LatLng(48.1100, 11.5100)
        val p3 = LatLng(48.1250, 11.5250)

        val encodedPoly = PolyUtil.encode(listOf(p1, p2))
        val encodedAlt = NumericalEncodingUtils.encodeDoubles(listOf(500.0, 510.0))
        val encodedDist = NumericalEncodingUtils.encodeDoubles(listOf(0.0, 1000.0))

        every { mockDbManager.getEncodedStreams(workoutId) } returns EncodedStreams(encodedPoly, encodedAlt, encodedDist)
        every { mockDbManager.getExtremaForWorkouts(listOf(workoutId)) } returns listOf(
            ExtremaRecord(workoutId, SensorType.HR, ExtremaType.MIN, 110.0, p1, null),
            ExtremaRecord(workoutId, SensorType.HR, ExtremaType.MAX, 160.0, p2, null),
            ExtremaRecord(workoutId, SensorType.HR, ExtremaType.AVG, 140.0, null, 10)
        )

        val repositoryMock = mockk<WorkoutRepository>(relaxed = true)
        val repoField = TrackerService::class.java.getDeclaredField("mWorkoutRepository").apply { isAccessible = true }
        repoField.set(service, repositoryMock)

        val state = TrackerService.ResumedWorkoutState(
            "2026-10-18_120000",
            workoutId,
            150,
            100,
            120,
            1,
            1000.0
        )

        // 1. Resume workout
        service.applyResumedState(state)

        val liveSessionField = TrackerService::class.java.getDeclaredField("mLiveSession").apply { isAccessible = true }
        val liveSession = liveSessionField.get(service) as LiveWorkoutSession

        // 2. Append post-resume samples (20 steps for a 20s stream point at p3, plus 10 HR samples at 180 bpm -> merged avg = 160.0 over 20 samples)
        for (i in 1..20) {
            liveSession.recordStreamPoint(p3, 530.0, 1500.0)
        }
        for (i in 1..10) {
            liveSession.addSample(SensorType.HR, 180.0, p3)
        }

        // 3. Finalize session upon stop
        service.callFinalizeLiveSession()

        // 4. Verify combined polyline & streams persisted
        val polySlot = slot<String>()
        val altSlot = slot<String>()
        val distSlot = slot<String>()
        verify(exactly = 1) {
            mockDbManager.updateMapAndStreams(
                eq(workoutId),
                capture(polySlot),
                capture(altSlot),
                capture(distSlot)
            )
        }
        val finalDecodedPoints = PolyUtil.decode(polySlot.captured)
        assertTrue("Final polyline must include both pre-resume and post-resume points", finalDecodedPoints.size >= 2)
        assertEquals(p1.latitude, finalDecodedPoints.first().latitude, 1e-4)
        assertEquals(p3.latitude, finalDecodedPoints.last().latitude, 1e-4)
        assertEquals(listOf(500.0, 510.0, 530.0), NumericalEncodingUtils.decodeDoubles(altSlot.captured))
        assertEquals(listOf(0.0, 1000.0, 1500.0), NumericalEncodingUtils.decodeDoubles(distSlot.captured))

        // 5. Verify START is p1, END is p3, HR MIN is still 110.0 at p1, HR MAX is 180.0 at p3, HR AVG is 160.0 with count 20
        verify { mockDbManager.updateExtremaValue(eq(workoutId), eq(SensorType.LATITUDE), eq(ExtremaType.START), eq(p1.latitude), any()) }
        verify { mockDbManager.updateExtremaValue(eq(workoutId), eq(SensorType.LATITUDE), eq(ExtremaType.END), eq(p3.latitude), any()) }
        verify { mockDbManager.updateExtremaValue(eq(workoutId), eq(SensorType.HR), eq(ExtremaType.MIN), eq(110.0), any()) }
        verify { mockDbManager.updateExtremaValue(eq(workoutId), eq(SensorType.HR), eq(ExtremaType.MAX), eq(180.0), eq(p3)) }
        verify { mockDbManager.updateExtremaValue(eq(workoutId), eq(SensorType.HR), eq(ExtremaType.AVG), eq(160.0), isNull(), eq(20)) }
        verify { repositoryMock.setMapPolyline(eq(workoutId), eq(polySlot.captured)) }
    }
}
