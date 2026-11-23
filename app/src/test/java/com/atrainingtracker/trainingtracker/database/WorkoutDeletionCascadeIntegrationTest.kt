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

import android.app.Application
import android.content.ContentValues
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryPruner
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryRecorder
import com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.ExportStatus
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutMutationEvent
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * End-to-end Robolectric SQLite verification for complete cross-database workout deletion cascade,
 * cold-start DB snapshot lookup, synchronous awaited bulk deletion, `.tmp.salvage` sidecar cleanup,
 * `ActiveDevices.db` purge, cluster in-memory sync, and `WorkoutMutationEvent` emission
 * (`REQ-DAT-049.1`–`.3`, `TST-DAT-045.1`–`.3`, `ATT-9607`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkoutDeletionCascadeIntegrationTest {

    private lateinit var app: Application
    private lateinit var summariesDb: WorkoutSummariesDatabaseManager
    private lateinit var samplesDb: WorkoutSamplesDatabaseManager
    private lateinit var lapsDb: LapsDatabaseManager
    private lateinit var rrDb: WorkoutRrDatabaseManager
    private lateinit var activeDevicesDb: ActiveDevicesDbHelper
    private lateinit var exportStatusDb: ExportStatusDatabaseManager
    private lateinit var stravaDb: StravaUploadDbHelper
    private lateinit var clusterDb: WorkoutClusterDatabaseManager
    private lateinit var workoutRepo: WorkoutRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        resetAllSingletons()

        summariesDb = WorkoutSummariesDatabaseManager.getInstance(app)
        samplesDb = WorkoutSamplesDatabaseManager.getInstance(app)
        lapsDb = LapsDatabaseManager.getInstance(app)
        rrDb = WorkoutRrDatabaseManager.getInstance(app)
        activeDevicesDb = ActiveDevicesDbHelper.getInstance(app)
        exportStatusDb = ExportStatusDatabaseManager.getInstance(app)
        stravaDb = StravaUploadDbHelper.getInstance(app)
        clusterDb = WorkoutClusterDatabaseManager.getInstance(app)
        workoutRepo = WorkoutRepository.getInstance(app)
    }

    @After
    fun tearDown() {
        resetAllSingletons()
    }

    private fun resetAllSingletons() {
        WorkoutRepository.resetForTesting(null)
        WorkoutClusterRepository.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutRrRepository.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        LapsDatabaseManager.resetForTesting(null)
        WorkoutRrDatabaseManager.resetForTesting(null)
        ActiveDevicesDbHelper.resetForTesting(null)
        ExportStatusDatabaseManager.resetForTesting(null)
        StravaUploadDbHelper.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
    }

    @Test
    fun testSingleDelete_withUnhydratedAllWorkouts_purgesAll7StoresSidecarsAndSyncsCluster() = runBlocking {
        // Seed a RouteCluster with hitCount = 2
        val clusterId = clusterDb.insertCluster(
            WorkoutCluster(
                name = "Morning Loop",
                probableSportId = 1L,
                startLat = 48.77,
                startLng = 9.18,
                endLat = 48.77,
                endLng = 9.18,
                maxDispLat = 48.80,
                maxDispLng = 9.20,
                refDistance = 20000.0,
                hitCount = 2
            )
        )
        assertTrue("Cluster should be inserted", clusterId > 0L)

        // Seed target workout and a survivor workout across all 7 DBs + files
        val targetBase = "2026-11-23_080000"
        val survivorBase = "2026-11-23_090000"
        val targetId = seedFullWorkout(targetBase, "2026-11-23 08:00:00", clusterId)
        val survivorId = seedFullWorkout(survivorBase, "2026-11-23 09:00:00", clusterId)

        // Hydrate WorkoutClusterRepository in memory BEFORE delete so we verify in-memory sync
        val clusterRepo = WorkoutClusterRepository.getInstance(app)
        clusterRepo.loadAllClustersLightweight()
        assertEquals(2, clusterRepo.allClusters.value.first { it.id == clusterId }.hitCount)

        // Ensure WorkoutRepository._allWorkouts is UNHYDRATED (empty) — cold-start scenario!
        assertTrue("allWorkouts must start empty to test DB snapshot fallback", workoutRepo.allWorkouts.value.isEmpty())

        val events = CopyOnWriteArrayList<WorkoutMutationEvent>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            workoutRepo.workoutMutationEvents.collect { events.add(it) }
        }

        try {
            // Invoke direct WorkoutDeletionHelper -> WorkoutRepository.deleteWorkoutCascade
            val deleted = WorkoutDeletionHelper(app).deleteWorkout(targetId)
            assertTrue("deleteWorkout must return true", deleted)

            // 1. All 5 WorkoutSummaries.db tables purged for target, preserved for survivor
            assertEquals(0, countRows(summariesDb.database, WorkoutSummaries.TABLE, "${WorkoutSummaries.C_ID}=?", targetId))
            assertEquals(0, countRows(summariesDb.database, WorkoutSummaries.TABLE_EXTREMA_VALUES, "${WorkoutSummaries.WORKOUT_ID}=?", targetId))
            assertEquals(0, countRows(summariesDb.database, WorkoutSummaries.TABLE_ACCUMULATED_SENSORS, "${WorkoutSummaries.WORKOUT_ID}=?", targetId))
            assertEquals(0, countRows(summariesDb.database, WorkoutSummaries.TABLE_WORKOUT_PHOTOS, "${WorkoutSummaries.WORKOUT_ID}=?", targetId))
            assertEquals(0, countRows(summariesDb.database, WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, "${WorkoutSummaries.CPD_WORKOUT_ID}=?", targetId))
            assertEquals(1, countRows(summariesDb.database, WorkoutSummaries.TABLE, "${WorkoutSummaries.C_ID}=?", survivorId))

            // 2. Laps.db, WorkoutRR.db, ActiveDevices.db purged for target, preserved for survivor
            assertEquals(0, countRows(lapsDb.database, LapsDatabaseManager.Laps.TABLE, "${LapsDatabaseManager.Laps.WORKOUT_ID}=?", targetId))
            assertEquals(1, countRows(lapsDb.database, LapsDatabaseManager.Laps.TABLE, "${LapsDatabaseManager.Laps.WORKOUT_ID}=?", survivorId))
            assertTrue(rrDb.getIntervalsForWorkout(targetId).isEmpty())
            assertTrue(rrDb.getIntervalsForWorkout(survivorId).isNotEmpty())
            assertTrue("ActiveDevices.db must purge deleted workout", activeDevicesDb.getDatabaseIdsOfActiveDevices(targetId).isEmpty())
            assertEquals(listOf(42L), activeDevicesDb.getDatabaseIdsOfActiveDevices(survivorId))

            // 3. WorkoutSamples.db table dropped, ExportStatus.db & StravaUpload.db purged
            assertFalse("Sample table must be dropped", tableExists(samplesDb.database, WorkoutSamplesDatabaseManager.getTableName(targetBase)))
            assertTrue("Survivor sample table must remain", tableExists(samplesDb.database, WorkoutSamplesDatabaseManager.getTableName(survivorBase)))
            assertNull(exportStatusDb.getExportStatus(ExportInfo(targetBase, FileFormat.GPX, ExportType.FILE)))
            assertEquals(ExportStatus.WAITING, exportStatusDb.getExportStatus(ExportInfo(survivorBase, FileFormat.GPX, ExportType.FILE)))
            assertEquals(0, countRowsByString(stravaDb.writableDatabase, StravaUploadDbHelper.TABLE, "${WorkoutSummaries.FILE_BASE_NAME}=?", targetBase))
            assertEquals(1, countRowsByString(stravaDb.writableDatabase, StravaUploadDbHelper.TABLE, "${WorkoutSummaries.FILE_BASE_NAME}=?", survivorBase))

            // 4. Photo files and Polar/Phone .gz, .tmp, .tmp.salvage sidecars deleted for target, preserved for survivor
            assertFalse(File(app.filesDir, "workout_photos/$targetId").exists())
            assertTrue(File(app.filesDir, "workout_photos/$survivorId/photo.jpg").exists())
            val rawDir = PolarRawTelemetryRecorder.getRawSensorDir(app)
            for (suffix in listOf(
                PolarRawTelemetryRecorder.SIDECAR_EXTENSION,
                "${PolarRawTelemetryRecorder.SIDECAR_EXTENSION}${PolarRawTelemetryRecorder.TMP_EXTENSION}",
                "${PolarRawTelemetryRecorder.SIDECAR_EXTENSION}${PolarRawTelemetryRecorder.TMP_EXTENSION}.salvage",
                SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION,
                "${SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION}${SmartphoneRawTelemetryRecorder.TMP_EXTENSION}",
                "${SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION}${SmartphoneRawTelemetryRecorder.TMP_EXTENSION}.salvage"
            )) {
                assertFalse("Deleted workout sidecar $targetBase$suffix must be removed", File(rawDir, "$targetBase$suffix").exists())
                assertTrue("Survivor sidecar $survivorBase$suffix must remain", File(rawDir, "$survivorBase$suffix").exists())
            }

            // 5. Cluster hitCount decremented in BOTH SQLite AND WorkoutClusterRepository in-memory StateFlow
            assertEquals(1, clusterDb.getClusterById(clusterId)?.hitCount)
            assertEquals(1, clusterRepo.allClusters.value.first { it.id == clusterId }.hitCount)
            assertEquals(1, clusterRepo.clusterStats.value[clusterId]?.workoutCount)

            // 6. Exactly 1 post-commit WorkoutMutationEvent.Deleted emitted
            kotlinx.coroutines.yield()
            assertEquals("Exactly 1 Deleted event expected", 1, events.size)
            val evt = events[0] as WorkoutMutationEvent.Deleted
            assertEquals(targetId, evt.workoutId)
            assertEquals(clusterId, evt.clusterId)
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun testBulkDeleteOldWorkoutsCascade_executesSynchronouslyBeforeReturnAndEmitsSingleBulkEvent() = runBlocking {
        val clusterId = clusterDb.insertCluster(
            WorkoutCluster(
                name = "Bulk Loop",
                probableSportId = 1L,
                startLat = 48.77,
                startLng = 9.18,
                endLat = 48.77,
                endLng = 9.18,
                maxDispLat = 48.80,
                maxDispLng = 9.20,
                refDistance = 15000.0,
                hitCount = 4
            )
        )
        val old1 = seedFullWorkout("2020-01-01_080000", "2020-01-01 08:00:00", clusterId)
        val old2 = seedFullWorkout("2020-01-02_080000", "2020-01-02 08:00:00", clusterId)
        val old3 = seedFullWorkout("2020-01-03_080000", "2020-01-03 08:00:00", clusterId)
        val recent = seedFullWorkout("2099-01-01_080000", "2099-01-01 08:00:00", clusterId)

        val clusterRepo = WorkoutClusterRepository.getInstance(app)
        clusterRepo.loadAllClustersLightweight()
        workoutRepo.loadAllWorkouts()
        assertEquals(4, workoutRepo.allWorkouts.value.size)

        val events = CopyOnWriteArrayList<WorkoutMutationEvent>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            workoutRepo.workoutMutationEvents.collect { events.add(it) }
        }
        val progressCalls = mutableListOf<Triple<Int, Int, Long>>()

        try {
            val result = WorkoutDeletionHelper(app).deleteOldWorkouts(30) { current, total, id ->
                progressCalls.add(Triple(current, total, id))
            }
            assertTrue(result)

            // IMMEDIATELY on return (no delay!), all 3 old workouts MUST already be purged synchronously
            for (oldId in listOf(old1, old2, old3)) {
                assertEquals(0, countRows(summariesDb.database, WorkoutSummaries.TABLE, "${WorkoutSummaries.C_ID}=?", oldId))
                assertTrue(activeDevicesDb.getDatabaseIdsOfActiveDevices(oldId).isEmpty())
            }
            assertEquals(1, countRows(summariesDb.database, WorkoutSummaries.TABLE, "${WorkoutSummaries.C_ID}=?", recent))
            assertEquals(listOf(recent), workoutRepo.allWorkouts.value.map { it.id })
            assertEquals(3, progressCalls.size)
            assertEquals(1, progressCalls[0].first)
            assertEquals(3, progressCalls[0].second)
            assertEquals(3, progressCalls[2].first)

            // Cluster hitCount updated even when deleteOldWorkoutsCascade is called directly
            assertEquals(1, clusterDb.getClusterById(clusterId)?.hitCount)
            assertEquals(1, clusterRepo.allClusters.value.first { it.id == clusterId }.hitCount)

            // Exactly 1 BulkDeleted event containing all 3 IDs
            kotlinx.coroutines.yield()
            assertEquals(1, events.size)
            val bulk = events[0] as WorkoutMutationEvent.BulkDeleted
            assertEquals(setOf(old1, old2, old3), bulk.workoutIds.toSet())
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun testBulkDeleteOldWorkoutsCascade_emptyMatch_emitsZeroEvents() = runBlocking {
        seedFullWorkout("2099-06-01_080000", "2099-06-01 08:00:00", -1L)

        val events = CopyOnWriteArrayList<WorkoutMutationEvent>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            workoutRepo.workoutMutationEvents.collect { events.add(it) }
        }
        try {
            val result = workoutRepo.deleteOldWorkoutsCascade(30, null)
            assertTrue(result)
            kotlinx.coroutines.yield()
            assertTrue("Empty bulk delete must emit zero mutation events", events.isEmpty())
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun testRawSidecarSalvageFiles_preservedByExportInvalidation_deletedByWorkoutCascade() {
        val base = "2026-11-23_101500"
        val rawDir = PolarRawTelemetryRecorder.getRawSensorDir(app)
        rawDir.mkdirs()

        val polarGz = File(rawDir, "$base${PolarRawTelemetryRecorder.SIDECAR_EXTENSION}").apply { writeText("polar") }
        val polarSalvage = File(rawDir, "$base${PolarRawTelemetryRecorder.SIDECAR_EXTENSION}${PolarRawTelemetryRecorder.TMP_EXTENSION}.salvage").apply { writeText("polar_s") }
        val phoneGz = File(rawDir, "$base${SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION}").apply { writeText("phone") }
        val phoneSalvage = File(rawDir, "$base${SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION}${SmartphoneRawTelemetryRecorder.TMP_EXTENSION}.salvage").apply { writeText("phone_s") }

        // ExportManager.invalidateWorkoutExport MUST NOT delete irreplaceable raw sidecars (ATT-9613)
        ExportManager(app).invalidateWorkoutExport(base)
        assertTrue(polarGz.exists())
        assertTrue(polarSalvage.exists())
        assertTrue(phoneGz.exists())
        assertTrue(phoneSalvage.exists())

        // Workout deletion pruner MUST delete all 4 including .tmp.salvage (ATT-9607 / Guardrail 1)
        assertTrue(PolarRawTelemetryPruner.deleteSidecar(app, base))
        assertFalse(polarGz.exists())
        assertFalse(polarSalvage.exists())
        assertFalse(phoneGz.exists())
        assertFalse(phoneSalvage.exists())
    }

    @Test
    fun testMarkWorkoutFinished_emitsFinalizedEvent(): Unit = runBlocking {
        val fileBaseName = "2026-11-23_110000"
        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Live Ride")
            put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName)
            put(WorkoutSummaries.B_SPORT, BSportType.BIKE.name)
            put(WorkoutSummaries.SPORT_ID, 1L)
            put(WorkoutSummaries.EQUIPMENT_ID, 7L)
            put(WorkoutSummaries.TIME_START, "2026-11-23 11:00:00")
            put(WorkoutSummaries.TIME_ACTIVE_s, 1800)
            put(WorkoutSummaries.TIME_TOTAL_s, 1800)
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 12000.0)
            put(WorkoutSummaries.FINISHED, 0)
            put(WorkoutSummaries.CLUSTER_ID, -1L)
        }
        val workoutId = summariesDb.database.insert(WorkoutSummaries.TABLE, null, cv)
        assertTrue("Unfinished workout insert must succeed", workoutId > 0L)

        val events = CopyOnWriteArrayList<WorkoutMutationEvent>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            workoutRepo.workoutMutationEvents.collect { events.add(it) }
        }
        try {
            workoutRepo.markWorkoutFinished(workoutId)
            val received = kotlinx.coroutines.withTimeoutOrNull(3_000L) {
                while (events.none { it is WorkoutMutationEvent.Finalized && it.workoutId == workoutId }) {
                    kotlinx.coroutines.delay(10L)
                }
                true
            }
            assertTrue("markWorkoutFinished must emit WorkoutMutationEvent.Finalized($workoutId)", received == true)
            assertEquals(true, workoutRepo.getWorkoutDataDirect(workoutId)?.finished)
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun testDirectDeleteWorkoutCascade_leavesZeroThreadLocalResidue() {
        val workoutId = seedFullWorkout("2026-11-23_120000", "2026-11-23 12:00:00", -1L)
        assertTrue(workoutRepo.deleteWorkoutCascade(workoutId))

        // Reflectively verify pendingDeleteCascadeFlag ThreadLocal holds null on the calling thread
        val field = WorkoutRepository::class.java.getDeclaredField("pendingDeleteCascadeFlag").apply {
            isAccessible = true
        }
        val threadLocal = field.get(workoutRepo) as ThreadLocal<*>
        assertNull(
            "Direct deleteWorkoutCascade caller must leave zero ThreadLocal residue on worker thread",
            threadLocal.get()
        )
    }

    private fun seedFullWorkout(fileBaseName: String, timeStart: String, clusterId: Long): Long {
        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Ride $fileBaseName")
            put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName)
            put(WorkoutSummaries.B_SPORT, BSportType.BIKE.name)
            put(WorkoutSummaries.SPORT_ID, 1L)
            put(WorkoutSummaries.EQUIPMENT_ID, 7L)
            put(WorkoutSummaries.TIME_START, timeStart)
            put(WorkoutSummaries.TIME_ACTIVE_s, 3600)
            put(WorkoutSummaries.TIME_TOTAL_s, 3600)
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 25000.0)
            put(WorkoutSummaries.FINISHED, 1)
            put(WorkoutSummaries.CLUSTER_ID, clusterId)
        }
        val workoutId = summariesDb.database.insert(WorkoutSummaries.TABLE, null, cv)
        assertTrue("Workout insert must succeed", workoutId > 0L)

        // Extrema, AccumulatedSensors, Photos, CardioPowerDynamics
        val extremaCv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_ID, workoutId)
            put(WorkoutSummaries.SENSOR_TYPE, SensorType.TSS.name)
            put(WorkoutSummaries.EXTREMA_TYPE, ExtremaType.END.name)
            put(WorkoutSummaries.VALUE, 150.0)
        }
        summariesDb.database.insert(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, extremaCv)
        val accCv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_ID, workoutId)
            put(WorkoutSummaries.SENSOR_TYPE, SensorType.POWER.name)
        }
        summariesDb.database.insert(WorkoutSummaries.TABLE_ACCUMULATED_SENSORS, null, accCv)
        val photoDir = File(app.filesDir, "workout_photos/$workoutId").apply { mkdirs() }
        val photoFile = File(photoDir, "photo.jpg").apply { writeText("img") }
        summariesDb.insertPhoto(workoutId, photoFile.absolutePath, 1_700_000_000_000L, null, null, "test")
        val cpdCv = ContentValues().apply {
            put(WorkoutSummaries.CPD_WORKOUT_ID, workoutId)
            put(WorkoutSummaries.CPD_TAU_START, 30.0)
            put(WorkoutSummaries.CPD_HR0_START, 65.0)
            put(WorkoutSummaries.CPD_K_START, 0.4)
            put(WorkoutSummaries.CPD_HR200_START, 145.0)
            put(WorkoutSummaries.CPD_R2_START, 0.85)
        }
        summariesDb.database.insert(WorkoutSummaries.TABLE_CARDIO_POWER_DYNAMICS, null, cpdCv)

        // Laps, RR, ActiveDevices
        lapsDb.saveLap(workoutId, 1L, timeStart, 3600, 25000.0, 6.94)
        rrDb.insertIntervals(
            listOf(
                WorkoutRrInterval(
                    workoutId = workoutId,
                    beatIndex = 0,
                    beatTimestampMs = 1_700_000_000_000L,
                    rrMs = 800f
                )
            )
        )
        activeDevicesDb.addActiveDevice(workoutId, 42L)

        // WorkoutSamples table (getTableName already returns a SQL-quoted identifier)
        val tableName = WorkoutSamplesDatabaseManager.getTableName(fileBaseName)
        samplesDb.database.execSQL("CREATE TABLE IF NOT EXISTS $tableName (_id INTEGER PRIMARY KEY AUTOINCREMENT, time TEXT)")

        // ExportStatus & StravaUpload
        val exportCv = ContentValues().apply {
            put(ExportStatusDatabaseManager.EXPORT_STATUS, ExportStatus.WAITING.name)
            put(ExportStatusDatabaseManager.ANSWER, "")
        }
        exportStatusDb.updateExportStatus(exportCv, fileBaseName, ExportType.FILE, FileFormat.GPX)
        val stravaCv = ContentValues().apply {
            put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName)
            put(StravaUploadDbHelper.UPLOAD_ID, 12345L)
        }
        stravaDb.writableDatabase.insert(StravaUploadDbHelper.TABLE, null, stravaCv)

        // Raw sidecars (.gz, .tmp, .tmp.salvage) for Polar & Phone
        val rawDir = PolarRawTelemetryRecorder.getRawSensorDir(app).apply { mkdirs() }
        for (suffix in listOf(
            PolarRawTelemetryRecorder.SIDECAR_EXTENSION,
            "${PolarRawTelemetryRecorder.SIDECAR_EXTENSION}${PolarRawTelemetryRecorder.TMP_EXTENSION}",
            "${PolarRawTelemetryRecorder.SIDECAR_EXTENSION}${PolarRawTelemetryRecorder.TMP_EXTENSION}.salvage",
            SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION,
            "${SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION}${SmartphoneRawTelemetryRecorder.TMP_EXTENSION}",
            "${SmartphoneRawTelemetryRecorder.SIDECAR_EXTENSION}${SmartphoneRawTelemetryRecorder.TMP_EXTENSION}.salvage"
        )) {
            File(rawDir, "$fileBaseName$suffix").writeText("raw")
        }

        return workoutId
    }

    private fun countRows(db: android.database.sqlite.SQLiteDatabase, table: String, where: String, id: Long): Int {
        return db.rawQuery("SELECT COUNT(*) FROM $table WHERE $where", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun countRowsByString(db: android.database.sqlite.SQLiteDatabase, table: String, where: String, arg: String): Int {
        return db.rawQuery("SELECT COUNT(*) FROM $table WHERE $where", arrayOf(arg)).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun tableExists(db: android.database.sqlite.SQLiteDatabase, tableName: String): Boolean {
        val cleanName = tableName.removeSurrounding("\"")
        return db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(cleanName)
        ).use { it.moveToFirst() }
    }
}
