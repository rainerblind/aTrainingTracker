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
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectHrvImporter
import com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectManager
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessRepository
import com.atrainingtracker.trainingtracker.exporter.BaseExporter
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportStatus
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.exporter.uploader.DropboxUploader
import com.atrainingtracker.trainingtracker.exporter.uploader.StravaUploader
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Software Integration Verification Test: Cloud APIs & Health Connect (ASPICE SWE.5,
 * INT-SWE-08, IF-SWE-08, REQ-PRO-111.4, TST-INT-016, ATT-8156).
 *
 * Verifies real uploader and Health Connect components wired to real SQLite repositories:
 * 1. StravaUploader using OkHttpClient Interceptor transport double + real StravaUploadDbHelper / ExportStatusRepository,
 *    plus DropboxUploader failure classification on missing credential.
 * 2. HealthConnectManager.syncWorkout() -> HealthConnectClient double + real ExportStatusDatabaseManager persistence.
 * 3. HealthConnectHrvImporter.importRecentHrv() -> HealthConnectClient double + real HrvReadinessRepository SQLite persistence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudHealthConnectIntegrationTest {

    private lateinit var context: Context
    private val fileBaseName = "2026-11-17-073000"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cleanupAll()

        TrainingApplication.setAppContextForTesting(context)
        TrainingApplication.setSharedPreferencesForTesting(
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        StravaUploadDbHelper.resetForTesting(null)
        ExportStatusRepository.resetForTesting(null)
        ExportStatusDatabaseManager.resetForTesting(null)
        HrvReadinessDatabaseManager.resetForTesting(null)
        HrvReadinessRepository.resetForTesting(null)

        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.isLoggable(any<String>(), any<Int>()) } returns false
    }

    @After
    fun tearDown() {
        HealthConnectManager.sdkStatusOverride = null
        HealthConnectManager.clientProvider = null
        TrainingApplication.setSyncToHealthConnect(false)
        TrainingApplication.setHealthConnectHrvImportEnabled(false)
        StravaUploadDbHelper.resetForTesting(null)
        ExportStatusRepository.resetForTesting(null)
        ExportStatusDatabaseManager.resetForTesting(null)
        HrvReadinessDatabaseManager.resetForTesting(null)
        HrvReadinessRepository.resetForTesting(null)
        TrainingApplication.setAppContextForTesting(null)
        TrainingApplication.setSharedPreferencesForTesting(null)
        unmockkAll()
        cleanupAll()
    }

    private fun cleanupAll() {
        listOf(
            "WorkoutSummaries.db",
            "WorkoutSamples.db",
            "SportTypes.db",
            "ExportStatus.db",
            "StravaUpload.db",
            "HrvReadiness.db"
        ).forEach { context.deleteDatabase(it) }
        File(BaseExporter.getBaseDirFile(context), FileFormat.STRAVA.dirName).deleteRecursively()
        File(BaseExporter.getBaseDirFile(context), FileFormat.GPX.dirName).deleteRecursively()
    }

    private fun jsonResponse(request: okhttp3.Request, code: Int, json: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }

    /**
     * TST-INT-016.1: Verifies StravaUploader end-to-end multipart upload + polling + activity update
     * over OkHttpClient Interceptor transport double persisting upload & activity IDs into SQLite,
     * plus 500 retry classification and DropboxUploader credential guard.
     */
    @Test
    fun testStravaAndDropboxUploaders_okHttpInterceptor_persistsStravaIdsAndClassifiesRetry() {
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database!!
        summariesDb.insertOrThrow(WorkoutSummaries.TABLE, null, ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Cloud Sync Ride")
            put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName)
            put(WorkoutSummaries.SPORT_ID, 3L)
            put(WorkoutSummaries.B_SPORT, BSportType.BIKE.name)
            put(WorkoutSummaries.TIME_START, "2026-11-17 07:30:00")
            put(WorkoutSummaries.TIME_ACTIVE_s, 1800)
            put(WorkoutSummaries.TIME_TOTAL_s, 1800)
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 15000.0)
            put(WorkoutSummaries.FINISHED, 1)
        })

        val stravaInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val tcxFile = File(BaseExporter.getBaseDirFile(context), stravaInfo.shortPath)
        tcxFile.parentFile?.mkdirs()
        tcxFile.writeText("<TrainingCenterDatabase/>")

        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns "test-oauth-access-token"

        val dispatchedPaths = mutableListOf<String>()
        var pollCount = 0
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val path = req.url.encodedPath
            dispatchedPaths.add("${req.method} $path")
            when {
                req.method == "GET" && path == "/api/v3/athlete/activities" ->
                    jsonResponse(req, 200, "[]")
                req.method == "POST" && path == "/api/v3/uploads" ->
                    jsonResponse(req, 201, """{"id": 9001, "status": "Your activity is still being processed."}""")
                req.method == "GET" && path == "/api/v3/uploads/9001" -> {
                    pollCount++
                    if (pollCount == 1) {
                        jsonResponse(req, 200, """{"id": 9001, "status": "Your activity is still being processed."}""")
                    } else {
                        jsonResponse(req, 200, """{"id": 9001, "activity_id": "777888", "status": "Your activity is ready."}""")
                    }
                }
                req.method == "GET" && path == "/api/v3/activities/777888" ->
                    jsonResponse(req, 200, """{"id": 777888, "name": "Afternoon Ride", "description": ""}""")
                req.method == "PUT" && path == "/api/v3/activities/777888" ->
                    jsonResponse(req, 200, """{"id": 777888, "name": "Cloud Sync Ride"}""")
                else -> jsonResponse(req, 404, "{}")
            }
        }

        val uploader = StravaUploader(context, OkHttpClient.Builder().addInterceptor(interceptor).build()).apply {
            sleepStrategy = {}
        }
        val result = uploader.export(stravaInfo)

        assertTrue("Strava upload + polling + PUT update must succeed: ${result.answer()}", result.success())
        assertTrue(dispatchedPaths.contains("POST /api/v3/uploads"))
        assertTrue(dispatchedPaths.contains("PUT /api/v3/activities/777888"))
        assertEquals("777888", ExportStatusRepository.getInstance(context).getStravaActivityId(fileBaseName))

        // Transient HTTP 503 must classify as retryable failure
        val retryUploader = StravaUploader(
            context,
            OkHttpClient.Builder().addInterceptor { chain ->
                jsonResponse(chain.request(), 503, "Service Unavailable")
            }.build()
        ).apply { sleepStrategy = {} }
        val retryResult = retryUploader.uploadToStrava(stravaInfo, tcxFile, "test-oauth-access-token")
        assertFalse(retryResult.success())
        assertTrue("HTTP 503 must mark shallRetry = true", retryResult.shallRetry())

        // DropboxUploader missing file / credential check
        val dropboxResult = DropboxUploader(context).export(ExportInfo(fileBaseName, FileFormat.GPX, ExportType.DROPBOX))
        assertFalse(dropboxResult.success())
        assertFalse(dropboxResult.shallRetry())
    }

    /**
     * TST-INT-016.2: Verifies HealthConnectManager.syncWorkout() inserts mapped Health Connect
     * records through HealthConnectClient and updates ExportStatusDatabaseManager.
     */
    @Test
    fun testHealthConnectManager_syncWorkout_updatesExportStatusDatabase() = runBlocking {
        val fakeClient = mockk<HealthConnectClient>(relaxed = true)
        val permController = mockk<PermissionController>()
        coEvery { fakeClient.permissionController } returns permController
        coEvery { permController.getGrantedPermissions() } returns HealthConnectManager.REQUIRED_PERMISSIONS
        val capturedRecords = mutableListOf<List<Record>>()
        coEvery { fakeClient.insertRecords(capture(capturedRecords)) } returns mockk<InsertRecordsResponse>()

        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE
        HealthConnectManager.clientProvider = { fakeClient }
        TrainingApplication.setSyncToHealthConnect(true)

        val statusDb = ExportStatusDatabaseManager.getInstance(context)
        statusDb.addExportStatus(ContentValues().apply {
            put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName)
            put(ExportStatusDatabaseManager.FORMAT, FileFormat.HEALTH_CONNECT.name)
            put(ExportStatusDatabaseManager.TYPE, ExportType.COMMUNITY.name)
            put(ExportStatusDatabaseManager.EXPORT_STATUS, ExportStatus.WAITING.name)
        })

        val workoutData = WorkoutData(
            id = 401L,
            finished = true,
            fileBaseName = fileBaseName,
            workoutName = "Health Connect Integration Run",
            sportId = 2L,
            sportName = "Run",
            bSportType = BSportType.RUN,
            startTimeS = 1763364600L,
            formattedDate = "17.11.2026",
            formattedTime = "07:30",
            localDateTime = LocalDateTime.of(2026, 11, 17, 7, 30),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            source = WorkoutSource.TRACKED,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 5000.0,
            maxDisplacement = 2500.0,
            activeTimeSec = 1500L,
            totalTimeSec = 1520L,
            avgSpeedMps = 3.33,
            ascentMeters = 40L,
            descentMeters = 35L,
            minAltitude = 500.0,
            maxAltitude = 540.0,
            description = "Integration test run",
            goal = "",
            method = "",
            stravaSportName = "Run",
            syncToHealthConnect = true
        )

        val result = HealthConnectManager.syncWorkout(context, workoutData, force = true)
        assertTrue("syncWorkout must succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(capturedRecords.flatten().any { it is ExerciseSessionRecord })
        assertEquals(
            ExportStatus.FINISHED_SUCCESS,
            statusDb.getExportStatus(ExportInfo(fileBaseName, FileFormat.HEALTH_CONNECT, ExportType.COMMUNITY))
        )
    }

    /**
     * TST-INT-016.3: Verifies HealthConnectHrvImporter ingests nocturnal rMSSD and resting HR
     * records into real HrvReadinessRepository SQLite storage.
     */
    @Test
    fun testHealthConnectHrvImporter_ingestsIntoRealHrvReadinessRepository() = runBlocking {
        val hrvRepo = HrvReadinessRepository.getInstance(context)
        val fakeClient = mockk<HealthConnectClient>(relaxed = true)
        val permController = mockk<PermissionController>()
        coEvery { fakeClient.permissionController } returns permController
        coEvery { permController.getGrantedPermissions() } returns HealthConnectManager.HRV_READ_PERMISSIONS

        val nightInstant = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS)
        val meta = mockk<Metadata>(relaxed = true)
        every { meta.id } returns "hc-hrv-1"
        every { meta.dataOrigin } returns DataOrigin("com.wearable.test")

        val hrvRecords = listOf(
            HeartRateVariabilityRmssdRecord(nightInstant, ZoneOffset.UTC, 40.0, meta),
            HeartRateVariabilityRmssdRecord(nightInstant.plusSeconds(1800), ZoneOffset.UTC, 50.0, meta)
        )
        val rhrRecords = listOf(
            RestingHeartRateRecord(nightInstant, ZoneOffset.UTC, 49L, meta)
        )

        coEvery { fakeClient.readRecords(any<ReadRecordsRequest<Record>>()) } answers {
            val req = firstArg<ReadRecordsRequest<*>>()
            val records: List<Record> = when (req.recordType) {
                HeartRateVariabilityRmssdRecord::class -> hrvRecords
                RestingHeartRateRecord::class -> rhrRecords
                else -> emptyList()
            }
            ReadRecordsResponse(records, null)
        }

        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE
        HealthConnectManager.clientProvider = { fakeClient }
        TrainingApplication.setHealthConnectHrvImportEnabled(true)

        val result = HealthConnectHrvImporter.importRecentHrv(
            context = context,
            daysBack = 7,
            repository = hrvRepo,
            clientOverride = fakeClient
        )
        assertTrue("HRV import must succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(1, result.getOrNull())

        val persistedEntries = hrvRepo.getAllEntries()
        assertEquals(1, persistedEntries.size)
        assertEquals(HrvPosture.OVERNIGHT_WEARABLE, persistedEntries[0].posture)
        assertEquals(49, persistedEntries[0].restingHrBpm)
        assertNotNull(persistedEntries[0].rmssdMs)
        // Geometric mean of 40.0 and 50.0 is sqrt(2000) ~= 44.72
        assertEquals(44.72, persistedEntries[0].rmssdMs!!, 0.1)
    }
}
