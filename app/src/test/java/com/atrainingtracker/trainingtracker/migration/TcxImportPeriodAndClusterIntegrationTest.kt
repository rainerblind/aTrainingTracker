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

package com.atrainingtracker.trainingtracker.migration

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.util.Xml
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit test suite verifying:
 * - TST-MIG-023 / REQ-MIG-026: Direct repository notification pipeline & self-healing period sync integrity.
 * - TST-MIG-024 / REQ-MIG-027: Candidate sport speed inference, workout name similarity discount,
 *   B_SPORT persistence, and reactive cluster cache refresh.
 */
class TcxImportPeriodAndClusterIntegrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockApp: Application
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private lateinit var mockPeriodDb: PeriodSummariesDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase
    private lateinit var mockClusterEngine: WorkoutClusterEngine
    private lateinit var mockDiscovery: EquipmentAndSportTypeDiscoveryManager
    private lateinit var mockWorkoutRepo: WorkoutRepository
    private lateinit var mockClusterRepo: WorkoutClusterRepository

    private val contentValueStores = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<ContentValues, MutableMap<String, Any?>>())

    private fun io.mockk.MockKAnswerScope<*, *>.getRealInstance(): ContentValues {
        try {
            var obj: Any? = call.invocation.originalCall
            while (obj != null) {
                for (f in obj.javaClass.declaredFields) {
                    if (f.name == "self" || f.name == "\$self" || f.name.endsWith("\$self")) {
                        f.isAccessible = true
                        val s = f.get(obj)
                        if (s is ContentValues && s !== this.self) {
                            return s
                        }
                    }
                }
                val nextField = obj.javaClass.declaredFields.firstOrNull { 
                    it.name.contains("originalCall") || it.name.contains("callable") 
                }
                obj = nextField?.apply { isAccessible = true }?.get(obj)
            }
        } catch (_: Exception) { }
        return self as ContentValues
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadToCommunity(any()) } returns false

        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers {
            org.kxml2.io.KXmlParser()
        }

        mockApp = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockApp

        mockSqlDb = mockk(relaxed = true)

        mockSummariesDb = mockk(relaxed = true)
        every { mockSummariesDb.database } returns mockSqlDb
        every { mockSummariesDb.getLong(any(), WorkoutSummaries.CLUSTER_ID) } returns -1L
        every { mockSummariesDb.getLong(any(), WorkoutSummaries.SPORT_ID) } returns -1L
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        mockSamplesDb = mockk(relaxed = true)
        every { mockSamplesDb.database } returns mockSqlDb
        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb

        mockLapsDb = mockk(relaxed = true)
        every { mockLapsDb.database } returns mockSqlDb
        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb

        mockSportTypeDb = mockk(relaxed = true)
        every { mockSportTypeDb.getSportTypeIdFromTcxName("Running") } returns 1L
        every { mockSportTypeDb.getSportTypeIdFromTcxName("Biking") } returns 2L
        every { mockSportTypeDb.getSportTypeIdFromTcxName("Other") } returns -1L
        every { mockSportTypeDb.getBSportType(1L) } returns BSportType.RUN
        every { mockSportTypeDb.getBSportType(2L) } returns BSportType.BIKE
        every { mockSportTypeDb.getBSportType(-1L) } returns BSportType.UNKNOWN
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb
        every { SportTypeDatabaseManager.getSportTypeId(BSportType.RUN) } returns 1L
        every { SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE) } returns 2L

        mockPeriodDb = mockk(relaxed = true)
        every { mockPeriodDb.isSyncFinished() } returns true
        PeriodSummariesDatabaseManager.setInstanceForTesting(mockPeriodDb)

        mockDiscovery = mockk(relaxed = true)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(mockDiscovery)

        mockClusterEngine = mockk(relaxed = true)
        WorkoutClusterEngine.resetForTesting(mockClusterEngine)

        mockWorkoutRepo = mockk(relaxed = true)
        every { mockWorkoutRepo.allWorkouts } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        WorkoutRepository.resetForTesting(mockWorkoutRepo)

        mockClusterRepo = mockk(relaxed = true)
        WorkoutClusterRepository.resetForTesting(mockClusterRepo)

        contentValueStores.clear()
        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<String>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Long>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Int>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Double>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Double>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Float>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Float>()
            Unit
        }
        every { constructedWith<ContentValues>().containsKey(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.containsKey(firstArg<String>()) ?: false
        }
        every { constructedWith<ContentValues>().getAsString(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.get(firstArg<String>())?.toString()
        }
        every { constructedWith<ContentValues>().getAsLong(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            (map?.get(firstArg<String>()) as? Number)?.toLong()
        }
        every { constructedWith<ContentValues>().getAsInteger(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            (map?.get(firstArg<String>()) as? Number)?.toInt()
        }
        every { constructedWith<ContentValues>().getAsDouble(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            (map?.get(firstArg<String>()) as? Number)?.toDouble()
        }
        every { constructedWith<ContentValues>().size() } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.size ?: 0
        }

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.count } returns 0
        every { mockCursor.moveToFirst() } returns false
        every { mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any()) } returns mockCursor
        every { mockSqlDb.insert(WorkoutSummaries.TABLE, any(), any()) } returns 101L
        every { mockSqlDb.insert(match { it != WorkoutSummaries.TABLE }, any(), any()) } returns 1L
        every { mockSqlDb.update(WorkoutSummaries.TABLE, any(), any(), any()) } returns 1
    }

    @After
    fun tearDown() {
        unmockkAll()
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        PeriodSummariesDatabaseManager.setInstanceForTesting(null)
        PeriodsRepository.setInstanceForTesting(null)
        WorkoutRepository.resetForTesting(null)
        WorkoutClusterRepository.resetForTesting(null)
    }

    private fun generateTcx(sport: String, workoutName: String): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1">
  <Activities>
    <Activity Sport="$sport">
      <Id>2026-09-12T10:00:00Z</Id>
      <Lap StartTime="2026-09-12T10:00:00Z">
        <TotalTimeSeconds>300.0</TotalTimeSeconds>
        <DistanceMeters>1000.0</DistanceMeters>
        <Track>
          <Trackpoint>
            <Time>2026-09-12T10:00:00Z</Time>
            <Position><LatitudeDegrees>48.0</LatitudeDegrees><LongitudeDegrees>11.0</LongitudeDegrees></Position>
            <DistanceMeters>0.0</DistanceMeters>
          </Trackpoint>
          <Trackpoint>
            <Time>2026-09-12T10:05:00Z</Time>
            <Position><LatitudeDegrees>48.005</LatitudeDegrees><LongitudeDegrees>11.005</LongitudeDegrees></Position>
            <DistanceMeters>1000.0</DistanceMeters>
          </Trackpoint>
        </Track>
      </Lap>
      <Notes>[$workoutName]</Notes>
      <Extensions>
        <att:ActivityExtension>
          <att:Name>$workoutName</att:Name>
        </att:ActivityExtension>
      </Extensions>
    </Activity>
  </Activities>
</TrainingCenterDatabase>"""
    }

    @Test
    fun testTST_MIG_024_candidateSportsInferredForUnknownSport_and_nameMatchDiscount_and_bSportPersisted() = runBlocking {
        // Given: A TCX workout with unmapped sport ("Other" -> UNKNOWN) and a workout name ("Hausrunde")
        val tcx = generateTcx(sport = "Other", workoutName = "Hausrunde")
        val tempFile = File.createTempFile("test_tcx_heuristics_", ".tcx")
        tempFile.writeText(tcx)

        // Mock Discovery to infer BIKE from average speed
        every { mockDiscovery.getCandidateBSportTypes(BSportType.UNKNOWN, any()) } returns setOf(BSportType.BIKE)

        // Capture parameters sent to suggestCluster
        val candidateSportsSlot = slot<Set<BSportType>>()
        val workoutNameSlot = slot<String?>()
        val mockMatchingCluster = mockk<WorkoutCluster>(relaxed = true)
        every { mockMatchingCluster.name } returns "Hausrunde"
        every { mockMatchingCluster.id } returns 42L
        every { mockMatchingCluster.bSportType } returns BSportType.BIKE

        every {
            mockClusterEngine.suggestCluster(
                any(), any(), any(), any(),
                captureNullable(workoutNameSlot),
                capture(candidateSportsSlot),
                any(), any()
            )
        } returns mockMatchingCluster

        try {
            // When: Import executes
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            // Then:
            // 1. candidateSportTypes MUST be inferred from Discovery (not emptySet or setOf(UNKNOWN))
            assertEquals(setOf(BSportType.BIKE), candidateSportsSlot.captured)

            // 2. workoutName MUST be passed into suggestCluster enabling 50% score bonus
            assertEquals("Hausrunde", workoutNameSlot.captured)

            // 3. B_SPORT must be persisted in WorkoutSummaries
            val updatedValues = mutableListOf<ContentValues>()
            verify { mockSqlDb.update(WorkoutSummaries.TABLE, capture(updatedValues), any(), any()) }
            assertTrue("WorkoutSummaries.B_SPORT must be persisted", updatedValues.any {
                it.getAsString(WorkoutSummaries.B_SPORT) != null
            })
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testTST_MIG_023_directRepositoryNotificationPipelineInvoked() = runBlocking {
        // Given: A valid TCX workout file
        val tcx = generateTcx(sport = "Running", workoutName = "Morning Run")
        val tempFile = File.createTempFile("test_tcx_notification_", ".tcx")
        tempFile.writeText(tcx)

        try {
            // When: Import executes
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            // Then: WorkoutRepository.reloadWorkoutData and WorkoutClusterRepository.refreshClusters
            // MUST be directly invoked (bypassing unbuffered LocalBroadcastManager drops)
            coVerify { mockWorkoutRepo.reloadWorkoutData(101L) }
            coVerify { mockClusterRepo.refreshClusters() }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testTST_MIG_023_selfHealingPeriodSync_detectsDiscrepancyAndTriggersMigration() = runBlocking {
        // Given: A state where WorkoutSummaries contains 15 finished workouts, but PeriodSummaries only has 10 DAY workouts
        every { mockSummariesDb.getFinishedWorkoutCount() } returns 15
        every { mockPeriodDb.getTotalDayWorkoutsCount() } returns 10
        every { mockPeriodDb.isSyncFinished() } returns true

        val repo = PeriodsRepository.getInstance(mockApp)

        // When: checkIntegrityAndSync is called
        val discrepancyDetected = repo.checkIntegrityAndSync()

        // Then: Discrepancy is detected (15 > 10), returns true
        assertTrue("Discrepancy must be detected when finished workouts exceed period summaries", discrepancyDetected)

        // And Given: Sync is in parity (15 finished workouts == 15 period workouts)
        every { mockSummariesDb.getFinishedWorkoutCount() } returns 15
        every { mockPeriodDb.getTotalDayWorkoutsCount() } returns 15

        // When: checkIntegrityAndSync is called again
        val inSync = repo.checkIntegrityAndSync()

        // Then: No discrepancy, returns false
        assertFalse("No discrepancy when finished workouts match period summaries", inSync)
    }
}
