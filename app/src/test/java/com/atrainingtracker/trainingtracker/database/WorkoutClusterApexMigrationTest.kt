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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapper
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests for point-to-point apex self-healing migration and import unification
 * (REQ-DAT-039.1 - REQ-DAT-039.5, TST-DAT-035.1 - TST-DAT-035.3, ATT-6319).
 */
class WorkoutClusterApexMigrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var mockSummariesDb: SQLiteDatabase
    private lateinit var mockSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockClusterDb: WorkoutClusterDatabaseManager
    private lateinit var mockRoutesDb: RoutesDatabaseManager
    private lateinit var mockSportDb: SportTypeDatabaseManager
    private lateinit var mockEquipmentDb: EquipmentDbHelper
    private lateinit var mockStravaDb: StravaUploadDbHelper
    private lateinit var clusterEngine: WorkoutClusterEngine

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getClusterTolEndpoints() } returns 200f
        every { TrainingApplication.getClusterTolApex() } returns 400f
        every { TrainingApplication.getClusterTolDistance() } returns 0.20f
        every { TrainingApplication.getClusterScoreThreshold() } returns 1.2f
        every { TrainingApplication.useSportTypeForClustering() } returns true
        every { TrainingApplication.getDebug(any()) } returns false

        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockPrefsEditor = mockk(relaxed = true)
        mockSummariesDb = mockk(relaxed = true)
        mockSummariesManager = mockk(relaxed = true)
        mockClusterDb = mockk(relaxed = true)
        mockRoutesDb = mockk(relaxed = true)
        mockSportDb = mockk(relaxed = true)
        mockEquipmentDb = mockk(relaxed = true)
        mockStravaDb = mockk(relaxed = true)

        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getString(R.string.cluster_migration_title) } returns "Cluster Migration"
        every { mockContext.getString(R.string.cluster_migration_healing) } returns "Healing"

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putInt(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } returns Unit

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        mockkStatic(SportTypeDatabaseManager::class)
        mockkStatic(EquipmentDbHelper::class)
        mockkStatic(StravaUploadDbHelper::class)

        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDb
        every { EquipmentDbHelper.getInstance(any()) } returns mockEquipmentDb
        every { StravaUploadDbHelper.getInstance(any()) } returns mockStravaDb
        every { mockSummariesManager.database } returns mockSummariesDb

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        RoutesDatabaseManager.resetForTesting(mockRoutesDb)
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutClusterRepository.resetForTesting(null)

        clusterEngine = WorkoutClusterEngine.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutClusterRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        RoutesDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    /**
     * TST-DAT-035.1: Verifies self-healing apex migration recomputes degenerated A->B apexes
     * and updates cluster centroids while leaving closed loops untouched.
     */
    @Test
    fun testHealLegacyPointToPointApexes_recomputesDegenerateApexAndUpdatesClusterCentroid() {
        val workoutId = 42L
        val clusterId = 7L

        val start = LatLng(48.7083, 9.0028)
        val end = LatLng(48.6570, 9.0640)
        val forestApex = LatLng(48.6950, 9.0450)
        val points = listOf(
            start,
            LatLng(48.7050, 9.0150),
            LatLng(48.7000, 9.0300),
            forestApex,
            LatLng(48.6750, 9.0550),
            LatLng(48.6650, 9.0600),
            end
        )
        val polyline = PolyUtil.encode(points)

        // Cursor 1: Scan for polylines in TABLE_WORKOUT_SUMMARIES
        val scanCursor = mockk<Cursor>(relaxed = true)
        var scanAdvanced = false
        every { scanCursor.moveToNext() } answers {
            if (!scanAdvanced) {
                scanAdvanced = true
                true
            } else {
                false
            }
        }
        every { scanCursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID) } returns 0
        every { scanCursor.getColumnIndexOrThrow(WorkoutSummaries.CLUSTER_ID) } returns 1
        every { scanCursor.getColumnIndexOrThrow(WorkoutSummaries.MAP_POLYLINE) } returns 2
        every { scanCursor.getLong(0) } returns workoutId
        every { scanCursor.getLong(1) } returns clusterId
        every { scanCursor.getString(2) } returns polyline
        every { scanCursor.close() } returns Unit

        // Cursor 2: Recalculate cluster centroid
        val clusterCursor = mockk<Cursor>(relaxed = true)
        var clusterAdvanced = false
        every { clusterCursor.moveToNext() } answers {
            if (!clusterAdvanced) {
                clusterAdvanced = true
                true
            } else {
                false
            }
        }
        every { clusterCursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID) } returns 0
        every { clusterCursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m) } returns 1
        every { clusterCursor.getLong(0) } returns workoutId
        every { clusterCursor.getDouble(1) } returns 7500.0
        every { clusterCursor.close() } returns Unit

        every {
            mockSummariesDb.query(
                WorkoutSummaries.TABLE,
                match { it.contains(WorkoutSummaries.MAP_POLYLINE) },
                any(), any(), any(), any(), any()
            )
        } returns scanCursor

        every {
            mockSummariesDb.query(
                WorkoutSummaries.TABLE,
                match { it.contains(WorkoutSummaries.DISTANCE_TOTAL_m) },
                "${WorkoutSummaries.CLUSTER_ID} = ?",
                arrayOf(clusterId.toString()),
                any(), any(), any()
            )
        } returns clusterCursor

        // Extrema in DB: degenerate apex collapsed onto end
        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START) } returns start
        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END) } returns end
        // First call: degenerate apex (end). Second call (during cluster recalc): true healed apex
        var apexQueryCount = 0
        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } answers {
            apexQueryCount++
            if (apexQueryCount <= 1) end else forestApex
        }

        val existingCluster = WorkoutCluster(
            id = clusterId,
            name = "Sindelfingen - Schönaich",
            probableSportId = 1L,
            startLat = start.latitude,
            startLng = start.longitude,
            endLat = end.latitude,
            endLng = end.longitude,
            maxDispLat = end.latitude, // degenerate
            maxDispLng = end.longitude,
            refDistance = 7500.0,
            hitCount = 1,
            bSportType = BSportType.RUN
        )
        every { mockClusterDb.getClusterById(clusterId) } returns existingCluster

        val healed = clusterEngine.healLegacyPointToPointApexes(mockContext)

        assertEquals(1, healed)

        // Verify extrema value updated with non-degenerate forest apex
        verify {
            mockSummariesManager.updateExtremaValue(
                workoutId,
                SensorType.LINE_DISTANCE_m,
                ExtremaType.MAX,
                any(),
                match { Math.abs(it.latitude - forestApex.latitude) < 1e-4 && Math.abs(it.longitude - forestApex.longitude) < 1e-4 }
            )
        }

        // Verify cluster centroid updated to forest apex
        val clusterSlot = slot<WorkoutCluster>()
        verify { mockClusterDb.updateCluster(capture(clusterSlot)) }
        assertEquals(forestApex.latitude, clusterSlot.captured.maxDispLat, 1e-4)
        assertEquals(forestApex.longitude, clusterSlot.captured.maxDispLng, 1e-4)
    }

    /**
     * TST-DAT-035.1: Verifies workouts with overshoot >= 50m from end (previously skipped by < 50m filter)
     * are now healed by healLegacyPointToPointApexes via bipolar product (ATT-6686).
     */
    @Test
    fun testHealLegacyPointToPointApexes_healsWorkoutWithOvershootGreaterThan50m() {
        val workoutId = 2063L
        val clusterId = 1553L

        val start = LatLng(48.7083, 9.0028)
        val end = LatLng(48.6570, 9.0640)
        val forestApex = LatLng(48.6950, 9.0450)
        // Overshoot point ~85.7m past end
        val overshootApex = LatLng(48.6565, 9.0648)
        val distToEnd = WorkoutClusterEngine.distanceBetween(overshootApex, end)
        assertTrue("Overshoot distance to end must be > 50m to verify bugfix", distToEnd > 50f)

        val points = listOf(
            start,
            LatLng(48.7050, 9.0150),
            LatLng(48.7000, 9.0300),
            forestApex,
            LatLng(48.6750, 9.0550),
            end,
            overshootApex
        )
        val polyline = PolyUtil.encode(points)

        val scanCursor = mockk<Cursor>(relaxed = true)
        var scanAdvanced = false
        every { scanCursor.moveToNext() } answers {
            if (!scanAdvanced) {
                scanAdvanced = true
                true
            } else {
                false
            }
        }
        every { scanCursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID) } returns 0
        every { scanCursor.getColumnIndexOrThrow(WorkoutSummaries.CLUSTER_ID) } returns 1
        every { scanCursor.getColumnIndexOrThrow(WorkoutSummaries.MAP_POLYLINE) } returns 2
        every { scanCursor.getLong(0) } returns workoutId
        every { scanCursor.getLong(1) } returns clusterId
        every { scanCursor.getString(2) } returns polyline
        every { scanCursor.close() } returns Unit

        every {
            mockSummariesDb.query(
                WorkoutSummaries.TABLE,
                match { it.contains(WorkoutSummaries.MAP_POLYLINE) },
                any(), any(), any(), any(), any()
            )
        } returns scanCursor

        val clusterCursor = mockk<Cursor>(relaxed = true)
        var clusterAdvanced = false
        every { clusterCursor.moveToNext() } answers {
            if (!clusterAdvanced) {
                clusterAdvanced = true
                true
            } else {
                false
            }
        }
        every { clusterCursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID) } returns 0
        every { clusterCursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m) } returns 1
        every { clusterCursor.getLong(0) } returns workoutId
        every { clusterCursor.getDouble(1) } returns 7500.0
        every { clusterCursor.close() } returns Unit

        every {
            mockSummariesDb.query(
                WorkoutSummaries.TABLE,
                match { it.contains(WorkoutSummaries.DISTANCE_TOTAL_m) },
                "${WorkoutSummaries.CLUSTER_ID} = ?",
                arrayOf(clusterId.toString()),
                any(), any(), any()
            )
        } returns clusterCursor

        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START) } returns start
        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END) } returns end
        var apexQueryCount = 0
        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } answers {
            apexQueryCount++
            if (apexQueryCount <= 1) overshootApex else forestApex
        }

        val existingCluster = WorkoutCluster(
            id = clusterId,
            name = "Bike2Work",
            probableSportId = 1L,
            startLat = start.latitude,
            startLng = start.longitude,
            endLat = end.latitude,
            endLng = end.longitude,
            maxDispLat = overshootApex.latitude,
            maxDispLng = overshootApex.longitude,
            refDistance = 7500.0,
            hitCount = 1,
            bSportType = BSportType.RUN
        )
        every { mockClusterDb.getClusterById(clusterId) } returns existingCluster

        val healed = clusterEngine.healLegacyPointToPointApexes(mockContext)

        assertEquals(1, healed)

        verify {
            mockSummariesManager.updateExtremaValue(
                workoutId,
                SensorType.LINE_DISTANCE_m,
                ExtremaType.MAX,
                any(),
                match { Math.abs(it.latitude - forestApex.latitude) < 1e-4 && Math.abs(it.longitude - forestApex.longitude) < 1e-4 }
            )
        }
    }

    /**
     * TST-DAT-035.1 Invariant: Closed-loop routes (< 150m) are strictly preserved untouched.
     */
    @Test
    fun testHealLegacyPointToPointApexes_preservesClosedLoopRoute() {
        val workoutId = 99L
        val start = LatLng(48.7083, 9.0028)
        val end = LatLng(48.7084, 9.0029) // d < 150m (closed loop)
        val loopApex = LatLng(48.7200, 9.0028)
        val points = listOf(start, loopApex, end)
        val polyline = PolyUtil.encode(points)

        val scanCursor = mockk<Cursor>(relaxed = true)
        var scanAdvanced = false
        every { scanCursor.moveToNext() } answers {
            if (!scanAdvanced) {
                scanAdvanced = true
                true
            } else {
                false
            }
        }
        every { scanCursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID) } returns 0
        every { scanCursor.getColumnIndexOrThrow(WorkoutSummaries.CLUSTER_ID) } returns 1
        every { scanCursor.getColumnIndexOrThrow(WorkoutSummaries.MAP_POLYLINE) } returns 2
        every { scanCursor.getLong(0) } returns workoutId
        every { scanCursor.getLong(1) } returns 5L
        every { scanCursor.getString(2) } returns polyline
        every { scanCursor.close() } returns Unit

        every {
            mockSummariesDb.query(
                WorkoutSummaries.TABLE,
                match { it.contains(WorkoutSummaries.MAP_POLYLINE) },
                any(), any(), any(), any(), any()
            )
        } returns scanCursor

        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START) } returns start
        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END) } returns end
        every { mockSummariesManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } returns loopApex

        val healed = clusterEngine.healLegacyPointToPointApexes(mockContext)

        assertEquals(0, healed)
        verify(exactly = 0) {
            mockSummariesManager.updateExtremaValue(any(), any(), any(), any(), any())
        }
    }

    /**
     * TST-DAT-035.1: Verifies WorkoutClusterRepository triggers apex migration strictly once.
     */
    @Test
    fun testWorkoutClusterRepository_triggersApexMigrationOnce() = runBlocking {
        assertEquals("last_apex_repair_v2", WorkoutClusterRepository.SP_KEY_LAST_APEX_REPAIR)

        val cluster = WorkoutCluster(
            id = 1L,
            name = "Test Cluster",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.05,
            maxDispLng = 9.05,
            refDistance = 5000.0,
            hitCount = 2,
            bSportType = BSportType.RUN
        )
        every { mockClusterDb.getAllClusters() } returns listOf(cluster)
        every { mockPrefs.getInt("last_cluster_bounds_repair_v5", 0) } returns 5
        every { mockPrefs.getInt(WorkoutClusterRepository.SP_KEY_LAST_APEX_REPAIR, 0) } returns 0

        // Mock empty query for scan
        val emptyCursor = mockk<Cursor>(relaxed = true)
        every { emptyCursor.moveToNext() } returns false
        every { mockSummariesDb.query(any(), any(), any(), any(), any(), any(), any()) } returns emptyCursor

        val repo = WorkoutClusterRepository.getInstance(mockContext)
        repo.refreshClusters(forceShowProgress = false, forceCheckIntegrity = false)

        verify(exactly = 1) {
            mockPrefsEditor.putInt(WorkoutClusterRepository.SP_KEY_LAST_APEX_REPAIR, 2)
            mockPrefsEditor.apply()
        }
    }

    /**
     * TST-DAT-035.2: Verifies LegacyImportEngine uses findApexFromPoints and passes workoutPolyline.
     */
    @Test
    fun testLegacyImportEngine_usesUnifiedApexAndForwardsPolyline() {
        val candidateFiles = listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt")
        )
        val sourceFile = candidateFiles.firstOrNull { it.exists() }
        assertNotNull("LegacyImportEngine.kt must exist", sourceFile)
        val source = sourceFile!!.readText()

        assertTrue(
            "LegacyImportEngine must compute apex via findApexFromPoints (REQ-DAT-039.3)",
            source.contains("findApexFromPoints(start, points, end)")
        )
        assertTrue(
            "LegacyImportEngine must forward workoutPolyline to suggestCluster (REQ-DAT-039.3)",
            source.contains("workoutPolyline = points")
        )
    }

    /**
     * TST-DAT-035.3: Verifies WorkoutDataMapper.resolveAuthoritativeApex delegates to findApexFromPoints
     * with endLatLng, correctly resolving non-degenerate apex for A->B routes.
     */
    @Test
    fun testWorkoutDataMapper_resolveAuthoritativeApex_pointToPointParity() {
        val mapper = WorkoutDataMapper(
            mockContext,
            mockSummariesManager,
            mockSportDb,
            mockEquipmentDb,
            mockStravaDb
        )

        val start = LatLng(48.7083, 9.0028)
        val end = LatLng(48.6570, 9.0640)
        val forestApex = LatLng(48.6950, 9.0450)
        val points = listOf(
            start,
            LatLng(48.7050, 9.0150),
            LatLng(48.7000, 9.0300),
            forestApex,
            LatLng(48.6750, 9.0550),
            LatLng(48.6650, 9.0600),
            end
        )
        val polyline = PolyUtil.encode(points)
        val degenerateApex = end
        val degenerateDist = WorkoutClusterEngine.distanceBetween(start, end).toDouble()

        val workoutId = 123L
        val resolved = mapper.resolveAuthoritativeApex(
            workoutId = workoutId,
            startLatLng = start,
            mapPolyline = polyline,
            recordedMaxDisp = degenerateDist,
            recordedApex = degenerateApex,
            endLatLng = end
        )

        assertNotNull(resolved.first)
        assertNotNull(resolved.second)
        assertEquals(forestApex.latitude, resolved.second!!.latitude, 1e-4)
        assertEquals(forestApex.longitude, resolved.second!!.longitude, 1e-4)
        assertNotEquals(end.latitude, resolved.second!!.latitude, 1e-4)

        verify {
            mockSummariesManager.updateExtremaValue(
                workoutId,
                SensorType.LINE_DISTANCE_m,
                ExtremaType.MAX,
                any(),
                match { Math.abs(it.latitude - forestApex.latitude) < 1e-4 && Math.abs(it.longitude - forestApex.longitude) < 1e-4 }
            )
        }
    }
}
