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

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.BaseExporter
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.ExportStatus
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriter
import com.atrainingtracker.trainingtracker.exporter.writer.GPXFileWriter
import com.atrainingtracker.trainingtracker.exporter.writer.TCXFileWriter
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.garmin.fit.Decode
import com.garmin.fit.FileIdMesg
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Software Integration Verification Test: Repository/ExportManager to File Exporters (ASPICE SWE.5,
 * INT-SWE-07, IF-SWE-07, REQ-PRO-111.3, TST-INT-015, ATT-8156).
 *
 * Verifies real SQLite -> WorkoutRepository -> FitFileWriter / GPXFileWriter / TCXFileWriter
 * and ExportManager -> ExportStatusDatabaseManager status transitions on real disk storage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileExportersIntegrationTest {

    private lateinit var context: Context
    private val fileBaseName = "2026-11-17-071500"
    private var seededWorkoutId: Long = -1L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cleanupAll()

        TrainingApplication.setAppContextForTesting(context)
        TrainingApplication.setSharedPreferencesForTesting(
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        WorkoutRepository.resetForTesting(null)
        ExportStatusDatabaseManager.resetForTesting(null)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        seedRealWorkoutInSqlite()
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        ExportStatusDatabaseManager.resetForTesting(null)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        TrainingApplication.setAppContextForTesting(null)
        TrainingApplication.setSharedPreferencesForTesting(null)
        unmockkAll()
        cleanupAll()
    }

    private fun cleanupAll() {
        listOf(
            "WorkoutSummaries.db",
            "WorkoutSamples.db",
            "Laps.db",
            "SportTypes.db",
            "ExportStatus.db"
        ).forEach { context.deleteDatabase(it) }
        FileFormat.values().forEach { fmt ->
            File(BaseExporter.getBaseDirFile(context), fmt.dirName).deleteRecursively()
        }
    }

    private fun seedRealWorkoutInSqlite() {
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database!!
        val summaryCv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Export Pipeline Integration Ride")
            put(WorkoutSummaries.DESCRIPTION, "Real SQLite to FIT/GPX/TCX export")
            put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName)
            put(WorkoutSummaries.SPORT_ID, 3L)
            put(WorkoutSummaries.B_SPORT, BSportType.BIKE.name)
            put(WorkoutSummaries.TIME_START, "2026-11-17 07:15:00")
            put(WorkoutSummaries.TIME_ACTIVE_s, 120)
            put(WorkoutSummaries.TIME_TOTAL_s, 120)
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 1000.0)
            put(WorkoutSummaries.SPEED_AVERAGE_mps, 8.33)
            put(WorkoutSummaries.GC_DATA, "-GDHAPCSN")
            put(WorkoutSummaries.FINISHED, 1)
        }
        seededWorkoutId = summariesDb.insertOrThrow(WorkoutSummaries.TABLE, null, summaryCv)

        val samplesMgr = WorkoutSamplesDatabaseManager.getInstance(context)
        val sensors = listOf(
            SensorType.TIME_TOTAL,
            SensorType.TIME_ACTIVE,
            SensorType.DISTANCE_m,
            SensorType.SPEED_mps,
            SensorType.ALTITUDE,
            SensorType.LATITUDE,
            SensorType.LONGITUDE,
            SensorType.HR,
            SensorType.POWER,
            SensorType.CADENCE,
            SensorType.LAP_NR
        )
        samplesMgr.createNewTable(fileBaseName, sensors)
        val samplesDb = samplesMgr.database!!
        val table = WorkoutSamplesDatabaseManager.getTableName(fileBaseName)

        for (i in 0 until 5) {
            val cv = ContentValues().apply {
                put(SensorType.TIME_TOTAL.name, i * 30)
                put(SensorType.TIME_ACTIVE.name, i * 30)
                put(SensorType.DISTANCE_m.name, i * 250.0)
                put(SensorType.SPEED_mps.name, 8.33)
                put(SensorType.ALTITUDE.name, 510.0 + i * 2.0)
                put(SensorType.LATITUDE.name, 48.1370 + i * 0.0005)
                put(SensorType.LONGITUDE.name, 11.5760 + i * 0.0005)
                put(SensorType.HR.name, 140 + i)
                put(SensorType.POWER.name, 220 + i * 5)
                put(SensorType.CADENCE.name, 88)
                put(SensorType.LAP_NR.name, 1)
            }
            samplesDb.insertOrThrow(table, null, cv)
        }

        LapsDatabaseManager.getInstance(context).saveLap(
            seededWorkoutId,
            1L,
            "2026-11-17 07:15:00",
            120,
            1000.0,
            8.33,
            "Lap 1",
            "Main lap"
        )
    }

    /**
     * TST-INT-015.1: Verifies real FitFileWriter produces a valid binary FIT file decodable by
     * Garmin FIT SDK Decode with no leftover temporary files.
     */
    @Test
    fun testFitFileWriter_fromRealSqlite_producesDecodableFitBinary() {
        val exportInfo = ExportInfo(fileBaseName, FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(context)
        val result = writer.export(exportInfo)

        assertTrue("FitFileWriter must succeed: ${result.answer()}", result.success())
        val outFile = File(BaseExporter.getBaseDirFile(context), exportInfo.shortPath)
        assertTrue("FIT output file must exist on disk", outFile.exists() && outFile.length() > 0L)
        assertEquals(
            "Zero .tmp files must remain in FIT export directory",
            0,
            outFile.parentFile?.listFiles { _, name -> name.endsWith(".tmp") }?.size ?: 0
        )

        val decodedFileIds = mutableListOf<FileIdMesg>()
        val decodedRecords = mutableListOf<RecordMesg>()
        FileInputStream(outFile).use { fis ->
            val decode = Decode()
            val broadcaster = MesgBroadcaster(decode)
            broadcaster.addListener { mesg: FileIdMesg -> decodedFileIds.add(mesg) }
            broadcaster.addListener { mesg: RecordMesg -> decodedRecords.add(mesg) }
            assertTrue("Garmin FIT SDK must decode exported FIT file cleanly", decode.read(fis, broadcaster))
        }
        assertTrue("FIT file must contain FileIdMesg", decodedFileIds.isNotEmpty())
        assertEquals("FIT file must contain 5 sample RecordMesg entries", 5, decodedRecords.size)
    }

    /**
     * TST-INT-015.2: Verifies real GPXFileWriter and TCXFileWriter produce well-formed XML files
     * containing persisted SQLite trackpoints and sensor metrics without leftover .tmp artifacts.
     */
    @Test
    fun testGpxAndTcxWriters_fromRealSqlite_produceValidXml() {
        val gpxInfo = ExportInfo(fileBaseName, FileFormat.GPX, ExportType.FILE)
        val gpxResult = GPXFileWriter(context).export(gpxInfo)
        assertTrue("GPXFileWriter must succeed: ${gpxResult.answer()}", gpxResult.success())
        val gpxFile = File(BaseExporter.getBaseDirFile(context), gpxInfo.shortPath)
        assertTrue(gpxFile.exists() && gpxFile.length() > 0L)
        val gpxDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(gpxFile)
        assertEquals(5, gpxDoc.getElementsByTagName("trkpt").getLength())
        assertEquals(0, gpxFile.parentFile?.listFiles { _, name -> name.endsWith(".tmp") }?.size ?: 0)

        val tcxInfo = ExportInfo(fileBaseName, FileFormat.TCX, ExportType.FILE)
        val tcxResult = TCXFileWriter(context).export(tcxInfo)
        assertTrue("TCXFileWriter must succeed: ${tcxResult.answer()}", tcxResult.success())
        val tcxFile = File(BaseExporter.getBaseDirFile(context), tcxInfo.shortPath)
        assertTrue(tcxFile.exists() && tcxFile.length() > 0L)
        val tcxDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(tcxFile)
        assertEquals(5, tcxDoc.getElementsByTagName("Trackpoint").getLength())
        assertTrue(tcxDoc.getElementsByTagName("HeartRateBpm").getLength() >= 5)
        assertEquals(0, tcxFile.parentFile?.listFiles { _, name -> name.endsWith(".tmp") }?.size ?: 0)
    }

    /**
     * TST-INT-015.3: Verifies ExportManager lifecycle transitions ExportStatusDatabaseManager
     * states cleanly across newWorkout -> workoutFinished -> real FILE status update.
     */
    @Test
    fun testExportManager_exportStatusDatabase_lifecycleTransitions() {
        TrainingApplication.setExportToGPX(true)
        val exportManager = ExportManager(context)
        val statusDb = ExportStatusDatabaseManager.getInstance(context)
        val gpxInfo = ExportInfo(fileBaseName, FileFormat.GPX, ExportType.FILE)

        exportManager.newWorkout(fileBaseName)
        assertEquals(
            ExportStatus.TRACKING,
            statusDb.getExportStatus(gpxInfo)
        )

        exportManager.workoutFinished(fileBaseName)
        assertEquals(
            ExportStatus.TRACKING_FINISHED,
            statusDb.getExportStatus(gpxInfo)
        )

        val result = ExportManager.getExporter(context, gpxInfo).export(gpxInfo)
        assertTrue(result.success())

        val doneCv = ContentValues().apply {
            put(ExportStatusDatabaseManager.EXPORT_STATUS, ExportStatus.FINISHED_SUCCESS.name)
            put(ExportStatusDatabaseManager.ANSWER, result.answer())
        }
        statusDb.updateExportStatus(doneCv, fileBaseName, ExportType.FILE, FileFormat.GPX)
        assertEquals(
            ExportStatus.FINISHED_SUCCESS,
            statusDb.getExportStatus(gpxInfo)
        )
        assertFalse(result.shallRetry())
    }
}
