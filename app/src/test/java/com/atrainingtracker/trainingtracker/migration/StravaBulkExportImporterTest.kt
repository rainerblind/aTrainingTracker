/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.migration.parsers.ParsedWorkoutData
import com.atrainingtracker.trainingtracker.migration.parsers.TcxWorkoutParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests verifying Strava bulk export archive ingestion, in-memory GZIP decompression,
 * Zip-Slip prevention, metadata enrichment, and equipment synchronization (REQ-MIG-048 / TST-MIG-046.3, TST-MIG-046.4).
 */
class StravaBulkExportImporterTest {

    private lateinit var mockContext: Context
    private lateinit var mockEquipmentDb: EquipmentDbHelper
    private lateinit var mockSportTypeDb: com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
    private lateinit var mockSummaryDb: WorkoutSummariesDatabaseManager
    private lateinit var mockStravaDb: StravaUploadDbHelper

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockContext = mockk(relaxed = true)
        mockEquipmentDb = mockk(relaxed = true)
        mockSportTypeDb = mockk(relaxed = true)
        mockSummaryDb = mockk(relaxed = true)
        mockStravaDb = mockk(relaxed = true)

        every { mockSportTypeDb.getSportTypeIdFromTcxName(any()) } returns 1L
        every { mockSportTypeDb.getBSportType(any()) } returns BSportType.BIKE
        every { mockSummaryDb.allFileBaseNames } returns emptySet()
        every { mockStravaDb.allActivityIds } returns emptySet()

        EquipmentDbHelper.resetForTesting(mockEquipmentDb)
        com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.resetForTesting(mockSportTypeDb)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(mockSummaryDb)
        StravaUploadDbHelper.resetForTesting(mockStravaDb)

        mockkObject(LegacyImportEngine)
    }

    @After
    fun tearDown() {
        EquipmentDbHelper.resetForTesting(null)
        com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        StravaUploadDbHelper.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testZipSlip_rejection() {
        // Valid entries
        StravaBulkExportImporter.validateZipEntry("activities.csv")
        StravaBulkExportImporter.validateZipEntry("activities/12345.tcx.gz")
        StravaBulkExportImporter.validateZipEntry("bikes.csv")

        // Malicious directory traversal entries
        assertThrows(SecurityException::class.java) {
            StravaBulkExportImporter.validateZipEntry("../../evil.tcx")
        }
        assertThrows(SecurityException::class.java) {
            StravaBulkExportImporter.validateZipEntry("activities/../../../etc/passwd")
        }
        assertThrows(SecurityException::class.java) {
            StravaBulkExportImporter.validateZipEntry("/root/file.fit")
        }
        assertThrows(SecurityException::class.java) {
            StravaBulkExportImporter.validateZipEntry("\\Windows\\System32\\calc.exe")
        }
    }

    @Test
    fun testImportArchive_successWithMetadataEnrichmentAndEquipmentSync() = runBlocking {
        // Create synthetic Strava export ZIP
        val tempZip = File.createTempFile("strava_test_export", ".zip")
        try {
            val tcxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                  <Activities>
                    <Activity Sport="Biking">
                      <Id>2026-11-01T01:46:43Z</Id>
                      <Lap StartTime="2026-11-01T01:46:43Z">
                        <TotalTimeSeconds>1800.0</TotalTimeSeconds>
                        <DistanceMeters>25000.0</DistanceMeters>
                      </Lap>
                    </Activity>
                  </Activities>
                </TrainingCenterDatabase>
            """.trimIndent()

            // Compress TCX with GZIP
            val baos = ByteArrayOutputStream()
            GZIPOutputStream(baos).use { gzip ->
                gzip.write(tcxContent.toByteArray(Charsets.UTF_8))
            }
            val gzippedTcx = baos.toByteArray()

            val activitiesCsv = """
                Aktivitäts-ID,Aktivitätsdatum,Name der Aktivität,Aktivitätsart,Aktivitätsbeschreibung,Pendeln,Hinweis zur Privatsphäre für Aktivitäten,Aktivitätsausrüstung,Dateiname
                20811350233,"01.11.2026, 01:46:43",Runde ab Zu Hause,Radfahrt,"Sonnige Runde",false,Intervalltraining,Endurace AL,activities/21975890558.tcx.gz
            """.trimIndent()

            val bikesCsv = """
                Fahrradname,Fahrradmarke,Fahrradmodell,Fahrrad-Standardsportarten
                Endurace AL,Canyon,Endurace,Radfahren
            """.trimIndent()

            ZipOutputStream(FileOutputStream(tempZip)).use { zip ->
                zip.putNextEntry(ZipEntry("activities.csv"))
                zip.write(activitiesCsv.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("bikes.csv"))
                zip.write(bikesCsv.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("activities/21975890558.tcx.gz"))
                zip.write(gzippedTcx)
                zip.closeEntry()
            }

            every { mockEquipmentDb.getEquipmentId("Endurace AL") } returns 42L

            val capturedWorkout = slot<ParsedWorkoutData>()
            val capturedBaseName = slot<String>()
            val capturedSource = slot<WorkoutSource>()

            coEvery {
                LegacyImportEngine.persistParsedWorkout(
                    context = any(),
                    baseFileName = capture(capturedBaseName),
                    parsedWorkout = capture(capturedWorkout),
                    source = capture(capturedSource),
                    uploadToStrava = false,
                    listener = any()
                )
            } returns LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 101L)

            val result = StravaBulkExportImporter.importArchive(mockContext, tempZip, null)

            assertEquals(1, result.importedCount)
            assertEquals(0, result.skippedCount)
            assertEquals(0, result.failedCount)
            assertEquals(1, result.totalScanned)
            assertEquals(101L, result.lastImportedWorkoutId)

            assertEquals("21975890558", capturedBaseName.captured)
            assertEquals(WorkoutSource.TCX, capturedSource.captured)

            val saved = capturedWorkout.captured
            assertEquals("Runde ab Zu Hause", saved.workoutName)
            assertEquals("Sonnige Runde", saved.workoutNotes)
            assertEquals("Intervalltraining", saved.goal)
            assertEquals(BSportType.BIKE, saved.bSportType)
            assertEquals(42L, saved.equipmentId)
            assertEquals("20811350233", saved.stravaActivityId)
        } finally {
            tempZip.delete()
        }
    }

    @Test
    fun testImportArchive_duplicateSkipped() = runBlocking {
        val tempZip = File.createTempFile("strava_dup_test", ".zip")
        try {
            val activitiesCsv = """
                Activity ID,Filename,Activity Name,Activity Type
                11223344,activities/11223344.tcx.gz,Morning Ride,Ride
            """.trimIndent()

            val minimalTcx = """
                <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                  <Activities>
                    <Activity Sport="Biking">
                      <Id>2026-11-01T01:46:43Z</Id>
                      <Lap StartTime="2026-11-01T01:46:43Z">
                        <TotalTimeSeconds>10.0</TotalTimeSeconds>
                        <DistanceMeters>100.0</DistanceMeters>
                      </Lap>
                    </Activity>
                  </Activities>
                </TrainingCenterDatabase>
            """.trimIndent()

            val baos = ByteArrayOutputStream()
            GZIPOutputStream(baos).use { gzip ->
                gzip.write(minimalTcx.toByteArray(Charsets.UTF_8))
            }

            ZipOutputStream(FileOutputStream(tempZip)).use { zip ->
                zip.putNextEntry(ZipEntry("activities.csv"))
                zip.write(activitiesCsv.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("activities/11223344.tcx.gz"))
                zip.write(baos.toByteArray())
                zip.closeEntry()
            }

            coEvery {
                LegacyImportEngine.persistParsedWorkout(
                    any(), any(), any(), any(), any(), any()
                )
            } returns LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED)

            val result = StravaBulkExportImporter.importArchive(mockContext, tempZip, null)
            assertEquals(0, result.importedCount)
            assertEquals(1, result.skippedCount)
            assertEquals(0, result.failedCount)
        } finally {
            tempZip.delete()
        }
    }

    @Test
    fun testImportArchive_sortsActivitiesAscendingByActivityId() = runBlocking {
        val tempZip = File.createTempFile("strava_sort_test", ".zip")
        try {
            // Strava exports activities newest first (descending IDs)
            val activitiesCsv = """
                Activity ID,Filename,Activity Name,Activity Type
                3000,activities/3000.tcx.gz,Newest Workout,Ride
                2000,activities/2000.tcx.gz,Middle Workout,Ride
                1000,activities/1000.tcx.gz,Oldest Workout,Ride
            """.trimIndent()

            val minimalTcx = """
                <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                  <Activities>
                    <Activity Sport="Biking">
                      <Id>2026-11-01T01:46:43Z</Id>
                      <Lap StartTime="2026-11-01T01:46:43Z">
                        <TotalTimeSeconds>10.0</TotalTimeSeconds>
                        <DistanceMeters>100.0</DistanceMeters>
                      </Lap>
                    </Activity>
                  </Activities>
                </TrainingCenterDatabase>
            """.trimIndent()

            val baos = ByteArrayOutputStream()
            GZIPOutputStream(baos).use { gzip ->
                gzip.write(minimalTcx.toByteArray(Charsets.UTF_8))
            }
            val streamBytes = baos.toByteArray()

            ZipOutputStream(FileOutputStream(tempZip)).use { zip ->
                zip.putNextEntry(ZipEntry("activities.csv"))
                zip.write(activitiesCsv.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                for (id in listOf("3000", "2000", "1000")) {
                    zip.putNextEntry(ZipEntry("activities/$id.tcx.gz"))
                    zip.write(streamBytes)
                    zip.closeEntry()
                }
            }

            val processedIds = mutableListOf<String>()
            val progressSteps = mutableListOf<String>()

            coEvery {
                LegacyImportEngine.persistParsedWorkout(
                    any(), any(), any(), any(), any(), any()
                )
            } answers {
                val baseName = secondArg<String>()
                processedIds.add(baseName)
                LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 1L)
            }

            val listener = mockk<LegacyImportEngine.ProgressListener>(relaxed = true)
            every { listener.onProgress(any(), any(), any()) } answers {
                val name = thirdArg<String>()
                progressSteps.add(name)
            }

            val result = StravaBulkExportImporter.importArchive(mockContext, tempZip, listener)

            assertEquals(3, result.importedCount)
            assertEquals(0, result.skippedCount)
            assertEquals(0, result.failedCount)

            // Must process chronologically ascending: 1000 (oldest) first, then 2000, then 3000 (newest)
            assertEquals(listOf("1000", "2000", "3000"), processedIds)
            assertEquals(listOf("Oldest Workout", "Middle Workout", "Newest Workout"), progressSteps)
        } finally {
            tempZip.delete()
        }
    }

    @Test
    fun testImportArchive_zeroIoFastForwardDuplicates() = runBlocking {
        // Configure snapshot to know about baseName "21975890558" and Strava Activity ID "20811350233"
        every { mockSummaryDb.allFileBaseNames } returns setOf("21975890558")
        every { mockStravaDb.allActivityIds } returns setOf("20811350233")

        val tempZip = File.createTempFile("strava_zero_io_test", ".zip")
        try {
            val activitiesCsv = """
                Aktivitäts-ID,Aktivitätsdatum,Name der Aktivität,Aktivitätsart,Aktivitätsbeschreibung,Pendeln,Hinweis zur Privatsphäre für Aktivitäten,Aktivitätsausrüstung,Dateiname
                20811350233,"01.11.2026, 01:46:43",Runde ab Zu Hause,Radfahrt,"Sonnige Runde",false,Intervalltraining,Endurace AL,activities/21975890558.tcx.gz
            """.trimIndent()

            ZipOutputStream(FileOutputStream(tempZip)).use { zip ->
                zip.putNextEntry(ZipEntry("activities.csv"))
                zip.write(activitiesCsv.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // Deliberately DO NOT add "activities/21975890558.tcx.gz" entry.
                // If zero-I/O pre-filtering works, it fast-forwards immediately without opening streams!
            }

            val result = StravaBulkExportImporter.importArchive(mockContext, tempZip, null)

            assertEquals(0, result.importedCount)
            assertEquals(1, result.skippedCount)
            assertEquals(0, result.failedCount)
            assertEquals(1, result.totalScanned)

            // Verify persistParsedWorkout was NEVER invoked!
            coVerify(exactly = 0) {
                LegacyImportEngine.persistParsedWorkout(any(), any(), any(), any(), any(), any())
            }
        } finally {
            tempZip.delete()
        }
    }
}
