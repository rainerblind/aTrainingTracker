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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

/**
 * Unit test verifying canonical multi-sensor summary isolation and authoritative average speed
 * calculation in TrackerService (REQ-TRK-041.2, REQ-TRK-041.3, TST-TRK-041.2, ATT-4843).
 */
class TrackerServiceMultiSensorSummaryTest {

    private lateinit var service: TrackerService
    private lateinit var mockSamplesManager: WorkoutSamplesDatabaseManager
    private lateinit var mockSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockSummariesDb: SQLiteDatabase
    private lateinit var mockSamplesDb: SQLiteDatabase
    private lateinit var mockLocalBroadcastManager: LocalBroadcastManager

    private val currentTickSamplingMap = Collections.synchronizedMap(LinkedHashMap<String, Any?>())
    private val allPutValues = Collections.synchronizedMap(LinkedHashMap<String, Any?>())
    private val capturedSummaryValues = slot<ContentValues>()

    private val summaryColumnKeys = setOf(
        WorkoutSummaries.TIME_TOTAL_s,
        WorkoutSummaries.TIME_ACTIVE_s,
        WorkoutSummaries.DISTANCE_TOTAL_m,
        WorkoutSummaries.CALORIES,
        WorkoutSummaries.LAPS,
        WorkoutSummaries.ASCENDING,
        WorkoutSummaries.DESCENDING,
        WorkoutSummaries.SPEED_AVERAGE_mps
    )

    private fun recordSamplingEntry(key: String, value: Any?) {
        if (key !in summaryColumnKeys) {
            currentTickSamplingMap[key] = value
        }
    }

    @Before
    fun setUp() {
        currentTickSamplingMap.clear()
        allPutValues.clear()

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val k = firstArg<String>()
            val v = secondArg<Int>()
            allPutValues[k] = v
            recordSamplingEntry(k, v)
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val k = firstArg<String>()
            val v = secondArg<Long>()
            allPutValues[k] = v
            recordSamplingEntry(k, v)
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } answers {
            val k = firstArg<String>()
            val v = secondArg<Double>()
            allPutValues[k] = v
            recordSamplingEntry(k, v)
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } answers {
            val k = firstArg<String>()
            val v = secondArg<String>()
            allPutValues[k] = v
            recordSamplingEntry(k, v)
        }
        every { anyConstructed<ContentValues>().getAsDouble(any()) } answers {
            (allPutValues[firstArg<String>()] as? Number)?.toDouble()
        }
        every { anyConstructed<ContentValues>().getAsInteger(any()) } answers {
            (allPutValues[firstArg<String>()] as? Number)?.toInt()
        }
        every { anyConstructed<ContentValues>().getAsLong(any()) } answers {
            (allPutValues[firstArg<String>()] as? Number)?.toLong()
        }
        every { anyConstructed<ContentValues>().getAsString(any()) } answers {
            allPutValues[firstArg<String>()]?.toString()
        }
        every { anyConstructed<ContentValues>().get(any()) } answers {
            allPutValues[firstArg<String>()]
        }
        every { anyConstructed<ContentValues>().keySet() } answers {
            LinkedHashSet(currentTickSamplingMap.keys)
        }

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        every { TrainingApplication.isManualPauseLocked() } returns false
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        mockkStatic(SettingsDataStoreJavaHelper::class)
        val mockAutoPauseConfig = mockk<AutoPauseConfig>(relaxed = true)
        every { SettingsDataStoreJavaHelper.getAutoPauseConfig(any()) } returns mockAutoPauseConfig

        mockSamplesManager = mockk(relaxed = true)
        mockSummariesManager = mockk(relaxed = true)
        mockSummariesDb = mockk(relaxed = true)
        mockSamplesDb = mockk(relaxed = true)
        mockLocalBroadcastManager = mockk(relaxed = true)

        every { mockSummariesManager.database } returns mockSummariesDb
        every { mockSamplesManager.database } returns mockSamplesDb

        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesManager
        every { WorkoutSamplesDatabaseManager.getTableName(any()) } answers { callOriginal() }

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager

        mockkStatic(LocalBroadcastManager::class)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLocalBroadcastManager

        service = TrackerService()
    }

    @After
    fun tearDown() {
        try {
            unmockkAll()
        } catch (_: ConcurrentModificationException) {
            // Guard against MockK concurrency disposal race
        }
    }

    @Test
    fun testSampleAndWriteToDb_secondarySensor_doesNotOverwriteSummaryDistanceAndSpeed() {
        setPrivateField(service, "mWorkoutID", 434L)
        setPrivateField(service, "mBaseFileName", "2026-10-25_131818")
        setPrivateField(service, "mSamplesTableName", "\"2026-10-25_131818\"")
        setPrivateField(service, "mTableInitializationFuture", CompletableFuture.completedFuture<Void>(null))
        setPrivateField(service, "mDbExecutor", newDirectExecutorService())

        val mockBanalComm = mockk<BANALServiceComm>(relaxed = true)
        every { mockBanalComm.databaseIdsOfActiveRemoteDevices } returns emptyList()
        every { mockBanalComm.bSportType } returns BSportType.UNKNOWN
        every { mockBanalComm.getBestSensorData(any()) } returns null
        setPrivateField(service, "mBanalService", mockBanalComm)

        // Capture summaryValues updated in summariesDb
        every {
            mockSummariesDb.update(WorkoutSummaries.TABLE, capture(capturedSummaryValues), any(), any())
        } returns 1

        // Provide canonical ProxySensor (deviceName == null) followed by secondary Google Fused (deviceName != null)
        val telemetry = listOf(
            SensorData(SensorType.TIME_TOTAL, 15591, "15591", null),
            SensorData(SensorType.TIME_ACTIVE, 5928, "5928", null),
            SensorData(SensorType.DISTANCE_m, 4370.0, "4370.0", null),
            SensorData(SensorType.TIME_ACTIVE, 6000, "6000", "Google Fused"),
            SensorData(SensorType.DISTANCE_m, 13377.31, "13377.31", "Google Fused")
        )
        every { mockBanalComm.allSensorData } returns ArrayList(telemetry)

        service.sampleAndWriteToDb()

        // Verify update was called on summariesDb
        verify(atLeast = 1) {
            mockSummariesDb.update(WorkoutSummaries.TABLE, any(), any(), any())
        }

        val updatedValues = capturedSummaryValues.captured
        assertNotNull(updatedValues)

        // Summary distance must strictly equal canonical proxy sensor value (4370.0m), NOT secondary (13377.31m)
        assertEquals(4370.0, updatedValues.getAsDouble(WorkoutSummaries.DISTANCE_TOTAL_m) ?: 0.0, 0.001)

        // Summary active time must strictly equal canonical proxy sensor value (5928s), NOT secondary (6000s)
        assertEquals(5928, updatedValues.getAsInteger(WorkoutSummaries.TIME_ACTIVE_s) ?: 0)

        // Authoritative average speed must equal canonical distance / canonical active time = 4370.0 / 5928 = ~0.737 m/s
        val expectedAvgSpeed = 4370.0 / 5928.0
        assertEquals(expectedAvgSpeed, updatedValues.getAsDouble(WorkoutSummaries.SPEED_AVERAGE_mps) ?: 0.0, 0.001)

        // Both columns must be recorded in raw sampling values for full telemetry history
        assertTrue(currentTickSamplingMap.containsKey("DISTANCE_m"))
        assertEquals(4370.0, currentTickSamplingMap["DISTANCE_m"] as Double, 0.001)
        assertTrue(currentTickSamplingMap.containsKey("\"DISTANCE_m (Google Fused)\""))
        assertEquals(13377.31, currentTickSamplingMap["\"DISTANCE_m (Google Fused)\""] as Double, 0.001)
    }

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field: Field = TrackerService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun newDirectExecutorService(): ExecutorService {
        val executor = mockk<ExecutorService>(relaxed = true)
        every { executor.execute(any()) } answers {
            firstArg<Runnable>().run()
        }
        return executor
    }
}
