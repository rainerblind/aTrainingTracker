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

package com.atrainingtracker.trainingtracker.exporter.uploader

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.BaseExporter.ExportResult
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import okhttp3.FormBody
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying smart asymmetric workout naming strategy for Strava uploads and duplicate reconciliations
 * (REQ-EXP-008, TST-EXP-005, ATT-902).
 */
class StravaUploaderNamingTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDbManager: WorkoutSummariesDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager

    private val capturedUpdatedValues = mutableListOf<ContentValues>()
    private val capturedStravaRequests = mutableListOf<Map<String, String>>()
    private val capturedDuplicateActivityIds = mutableListOf<String>()
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

    private open class TestableStravaUploader(
        context: Context,
        private val stravaActivityResponse: JSONObject?,
        private val mockExistingActivity: JSONObject? = null,
        private val mockExistingActivitiesArray: JSONArray? = null,
        private val onRequestCaptured: (Map<String, String>) -> Unit = {}
    ) : StravaUploader(context) {

        override fun findExistingStravaActivityForWorkout(fileBaseName: String): JSONObject? {
            return mockExistingActivity ?: super.findExistingStravaActivityForWorkout(fileBaseName)
        }

        override fun getStravaJsonArray(url: String): JSONArray? {
            return mockExistingActivitiesArray ?: super.getStravaJsonArray(url)
        }

        public override fun doExport(exportInfo: ExportInfo): ExportResult {
            return super.doExport(exportInfo)
        }

        override fun updateStravaActivity(stravaActivityId: String, requestBody: RequestBody): JSONObject? {
            if (requestBody is FormBody) {
                val map = mutableMapOf<String, String>()
                for (i in 0 until requestBody.size) {
                    map[requestBody.name(i)] = requestBody.value(i)
                }
                onRequestCaptured(map)
            }
            return JSONObject().apply {
                put("id", stravaActivityId)
                put("type", "Ride")
                put("sport_type", "Ride")
            }
        }

        override fun getStravaActivity(stravaActivityId: String): JSONObject? {
            return stravaActivityResponse ?: JSONObject().apply {
                put("id", stravaActivityId)
                put("type", "Ride")
                put("sport_type", "Ride")
            }
        }

        override fun getGearId(eqId: Long): String? {
            return if (eqId > 0) "b12345" else null
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns java.io.File("/tmp/mock_files")
        mockSummariesDbManager = mockk(relaxed = true)
        mockSqlDb = mockk(relaxed = true)
        mockSportTypeDb = mockk(relaxed = true)

        capturedUpdatedValues.clear()
        capturedStravaRequests.clear()
        capturedDuplicateActivityIds.clear()
        contentValueStores.clear()

        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<String>()
        }
        every { constructedWith<ContentValues>().getAsString(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.get(firstArg<String>())?.toString()
        }

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDbManager
        every { mockSummariesDbManager.database } returns mockSqlDb

        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb
        every { mockSportTypeDb.getStravaName(any()) } returns "Ride"

        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns "mock_strava_token"

        mockkConstructor(StravaUploadDbHelper::class)
        every { anyConstructed<StravaUploadDbHelper>().getActivityId(any()) } returns "987654321"
        every { anyConstructed<StravaUploadDbHelper>().updateStravaActivityData(any(), any()) } returns Unit
        every { anyConstructed<StravaUploadDbHelper>().updateAll(any(), any(), capture(capturedDuplicateActivityIds), any(), any()) } returns Unit
        every { mockSqlDb.update(WorkoutSummaries.TABLE, capture(capturedUpdatedValues), any(), any()) } returns 1
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun setupMockCursor(
        workoutName: String?,
        fileBaseName: String = "2024-05-12_10-15-30",
        description: String? = "Test notes",
        trainer: Boolean = false,
        commute: Boolean = false,
        timeStart: String = "2024-05-12 10:15:30"
    ) {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true

        val colFileBaseName = 0
        val colWorkoutName = 1
        val colDescription = 2
        val colTrainer = 3
        val colCommute = 4
        val colSportId = 5
        val colEquipmentId = 6
        val colTimeStart = 7

        every { mockCursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME) } returns colFileBaseName
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME) } returns colFileBaseName
        every { mockCursor.getString(colFileBaseName) } returns fileBaseName
        every { mockCursor.isNull(colFileBaseName) } returns false

        every { mockCursor.getColumnIndex(WorkoutSummaries.WORKOUT_NAME) } returns colWorkoutName
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME) } returns colWorkoutName
        every { mockCursor.getString(colWorkoutName) } returns workoutName
        every { mockCursor.isNull(colWorkoutName) } returns (workoutName == null)

        every { mockCursor.getColumnIndex(WorkoutSummaries.DESCRIPTION) } returns colDescription
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.DESCRIPTION) } returns colDescription
        every { mockCursor.getString(colDescription) } returns description
        every { mockCursor.isNull(colDescription) } returns (description == null)

        every { mockCursor.getColumnIndex(WorkoutSummaries.TRAINER) } returns colTrainer
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.TRAINER) } returns colTrainer
        every { mockCursor.getInt(colTrainer) } returns (if (trainer) 1 else 0)
        every { mockCursor.isNull(colTrainer) } returns false

        every { mockCursor.getColumnIndex(WorkoutSummaries.COMMUTE) } returns colCommute
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.COMMUTE) } returns colCommute
        every { mockCursor.getInt(colCommute) } returns (if (commute) 1 else 0)
        every { mockCursor.isNull(colCommute) } returns false

        every { mockCursor.getColumnIndex(WorkoutSummaries.SPORT_ID) } returns colSportId
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID) } returns colSportId
        every { mockCursor.getLong(colSportId) } returns 1L
        every { mockCursor.isNull(colSportId) } returns false

        every { mockCursor.getColumnIndex(WorkoutSummaries.EQUIPMENT_ID) } returns colEquipmentId
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID) } returns colEquipmentId
        every { mockCursor.getLong(colEquipmentId) } returns 10L
        every { mockCursor.isNull(colEquipmentId) } returns false

        every { mockCursor.getColumnIndex(WorkoutSummaries.TIME_START) } returns colTimeStart
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_START) } returns colTimeStart
        every { mockCursor.getString(colTimeStart) } returns timeStart
        every { mockCursor.isNull(colTimeStart) } returns false

        every {
            mockSqlDb.query(
                WorkoutSummaries.TABLE,
                any(),
                "${WorkoutSummaries.FILE_BASE_NAME}=?",
                arrayOf(fileBaseName),
                any(),
                any(),
                any()
            )
        } returns mockCursor
    }

    @Test
    fun testDuplicateWithFallbackNameEnrichesLocalAndProtectsStrava() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = fileBaseName, fileBaseName = fileBaseName)

        val stravaResponse = JSONObject().apply {
            put("id", 987654321L)
            put("name", "Berlin Half Marathon")
            put("type", "Ride")
            put("sport_type", "Ride")
        }

        val uploader = TestableStravaUploader(mockContext, stravaResponse) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val result = uploader.doUpdate(exportInfo, isDuplicate = true)

        assertTrue("Update must succeed", result.success())

        // 1. Verify Strava update form request does NOT contain "name"
        val metadataUpdate = capturedStravaRequests.last()
        assertFalse("Duplicate update must NOT send name to Strava", metadataUpdate.containsKey("name"))

        // 2. Verify SQLite WorkoutSummaries.WORKOUT_NAME was enriched with Strava's title
        assertTrue("SQLite must be updated with enriched Strava name", capturedUpdatedValues.isNotEmpty())
        val updatedName = capturedUpdatedValues.first().getAsString(WorkoutSummaries.WORKOUT_NAME)
        assertEquals("Berlin Half Marathon", updatedName)
    }

    @Test
    fun testDuplicateWithTcxImportedNameEnrichesLocalAndProtectsStrava() {
        val fileBaseName = "2024-05-12_10-15-30"
        // Simulates TCX import where <Name> tag sets workoutName to "Cycling"
        setupMockCursor(workoutName = "Cycling", fileBaseName = fileBaseName)

        val stravaResponse = JSONObject().apply {
            put("id", 987654321L)
            put("name", "Sunday Club Ride")
            put("type", "Ride")
            put("sport_type", "Ride")
        }

        val uploader = TestableStravaUploader(mockContext, stravaResponse) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val result = uploader.doUpdate(exportInfo, isDuplicate = true)

        assertTrue("Update must succeed", result.success())

        // 1. Verify Strava update form request does NOT contain "name" (protects Strava title)
        val metadataUpdate = capturedStravaRequests.last()
        assertFalse("Duplicate update must NOT send name to Strava", metadataUpdate.containsKey("name"))

        // 2. Verify SQLite WorkoutSummaries.WORKOUT_NAME was enriched with Strava's title
        assertTrue("SQLite must be updated with enriched Strava name", capturedUpdatedValues.isNotEmpty())
        val updatedName = capturedUpdatedValues.first().getAsString(WorkoutSummaries.WORKOUT_NAME)
        assertEquals("Sunday Club Ride", updatedName)
    }

    @Test
    fun testDuplicateWithCustomNameProtectsStravaNameAndEnrichesLocal() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = "Local Custom Workout", fileBaseName = fileBaseName)

        val stravaResponse = JSONObject().apply {
            put("id", 987654321L)
            put("name", "Tuesday Track on Strava")
            put("type", "Ride")
            put("sport_type", "Ride")
        }

        val uploader = TestableStravaUploader(mockContext, stravaResponse) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val result = uploader.doUpdate(exportInfo, isDuplicate = true)

        assertTrue("Update must succeed", result.success())

        // 1. Verify Strava update form request does NOT contain "name"
        val metadataUpdate = capturedStravaRequests.last()
        assertFalse("Duplicate update must NOT send name to Strava", metadataUpdate.containsKey("name"))

        // 2. Strava title is authoritative and enriches local SQLite
        assertTrue("SQLite must be updated with Strava name on duplicate", capturedUpdatedValues.isNotEmpty())
        val updatedName = capturedUpdatedValues.first().getAsString(WorkoutSummaries.WORKOUT_NAME)
        assertEquals("Tuesday Track on Strava", updatedName)
    }

    @Test
    fun testDuplicateDetectionInErrorField() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = fileBaseName, fileBaseName = fileBaseName)

        val uploader = TestableStravaUploader(mockContext, null) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val duplicateJson = JSONObject().apply {
            put("id", "upload_123")
            put("error", "activity.tcx duplicate of activity 119487747")
        }

        val result = uploader.checkAndUpdateDuplicate(exportInfo, duplicateJson)
        assertNotNull("Must detect duplicate from error field", result)
        assertTrue("Duplicate handling must succeed", result!!.success())
        assertEquals("119487747", capturedDuplicateActivityIds.last())
    }

    @Test
    fun testDuplicateDetectionInStatusField() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = fileBaseName, fileBaseName = fileBaseName)

        val uploader = TestableStravaUploader(mockContext, null) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val duplicateJson = JSONObject().apply {
            put("id", "upload_456")
            put("status", "activity.tcx duplicate of activity 654321")
        }

        val result = uploader.checkAndUpdateDuplicate(exportInfo, duplicateJson)
        assertNotNull("Must detect duplicate from status field", result)
        assertTrue("Duplicate handling must succeed", result!!.success())
        assertEquals("654321", capturedDuplicateActivityIds.last())
    }

    @Test
    fun testDuplicateDetectionWithDirectIdPhrasing() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = fileBaseName, fileBaseName = fileBaseName)

        val uploader = TestableStravaUploader(mockContext, null) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val duplicateJson = JSONObject().apply {
            put("id", "upload_789")
            put("error", "duplicate of 778899")
        }

        val result = uploader.checkAndUpdateDuplicate(exportInfo, duplicateJson)
        assertNotNull("Must detect duplicate with direct ID phrasing", result)
        assertTrue("Duplicate handling must succeed", result!!.success())
        assertEquals("778899", capturedDuplicateActivityIds.last())
    }

    @Test
    fun testNonDuplicateReturnsNull() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = fileBaseName, fileBaseName = fileBaseName)

        val uploader = TestableStravaUploader(mockContext, null) { }
        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)

        val errorJson = JSONObject().apply {
            put("id", "upload_000")
            put("error", "Rate limit exceeded")
        }

        val result = uploader.checkAndUpdateDuplicate(exportInfo, errorJson)
        assertNull("Non-duplicate error must return null", result)
    }

    @Test
    fun testFreshUploadWithFallbackNameOmitsName() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = fileBaseName, fileBaseName = fileBaseName)

        val uploader = TestableStravaUploader(mockContext, null) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val result = uploader.doUpdate(exportInfo, isDuplicate = false)

        assertTrue("Update must succeed", result.success())

        // Verify fresh upload with fallback name omits "name" so Strava auto-names
        val metadataUpdate = capturedStravaRequests.last()
        assertFalse("Fresh upload with fallback name must omit name", metadataUpdate.containsKey("name"))
        assertTrue("SQLite must NOT be updated for fresh upload", capturedUpdatedValues.isEmpty())
    }

    @Test
    fun testFreshUploadWithCustomNameSendsName() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = "Mountain Stage", fileBaseName = fileBaseName)

        val uploader = TestableStravaUploader(mockContext, null) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val result = uploader.doUpdate(exportInfo, isDuplicate = false)

        assertTrue("Update must succeed", result.success())

        // Verify fresh upload with custom name includes "name"
        val metadataUpdate = capturedStravaRequests.last()
        assertTrue("Fresh upload with custom name must include name", metadataUpdate.containsKey("name"))
        assertEquals("Mountain Stage", metadataUpdate["name"])
    }

    @Test
    fun testMetadataPreservedAcrossBothPaths() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(
            workoutName = "Morning Commute",
            fileBaseName = fileBaseName,
            description = "Felt fast",
            trainer = true,
            commute = true
        )

        val uploader = TestableStravaUploader(mockContext, null) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val result = uploader.doUpdate(exportInfo, isDuplicate = true)

        assertTrue("Update must succeed", result.success())

        val metadataUpdate = capturedStravaRequests.last()
        assertEquals("b12345", metadataUpdate["gear_id"])
        assertEquals("Felt fast", metadataUpdate["description"])
        assertEquals("true", metadataUpdate["trainer"])
        assertEquals("true", metadataUpdate["commute"])
    }

    @Test
    fun testIsDefaultStravaNameEvaluation() {
        // German standard patterns
        assertTrue(StravaUploader.isDefaultStravaName("Radfahrt am Morgen"))
        assertTrue(StravaUploader.isDefaultStravaName("Radfahrt am Vormittag"))
        assertTrue(StravaUploader.isDefaultStravaName("Radfahrt am Mittag"))
        assertTrue(StravaUploader.isDefaultStravaName("Radfahrt am Nachmittag"))
        assertTrue(StravaUploader.isDefaultStravaName("Radfahrt am Abend"))
        assertTrue(StravaUploader.isDefaultStravaName("Radfahrt in der Nacht"))
        assertTrue(StravaUploader.isDefaultStravaName("Radfahrt nachts"))
        assertTrue(StravaUploader.isDefaultStravaName("Lauf am Abend"))
        assertTrue(StravaUploader.isDefaultStravaName("Wanderung am Morgen"))
        assertTrue(StravaUploader.isDefaultStravaName("Mittags-Radfahrt"))
        assertTrue(StravaUploader.isDefaultStravaName("Morgen-Lauf"))

        // German patterns with weather emojis
        assertTrue(StravaUploader.isDefaultStravaName("Lauf am Abend ⛅"))
        assertTrue(StravaUploader.isDefaultStravaName("Radfahrt am Nachmittag ☀️"))
        assertTrue(StravaUploader.isDefaultStravaName("Radfahrt am Morgen 🌧️"))

        // English standard patterns
        assertTrue(StravaUploader.isDefaultStravaName("Morning Ride"))
        assertTrue(StravaUploader.isDefaultStravaName("Afternoon Run"))
        assertTrue(StravaUploader.isDefaultStravaName("Evening Walk"))
        assertTrue(StravaUploader.isDefaultStravaName("Lunch Ride"))
        assertTrue(StravaUploader.isDefaultStravaName("Night Hike"))
        assertTrue(StravaUploader.isDefaultStravaName("Morning Swim"))
        assertTrue(StravaUploader.isDefaultStravaName("Morning Workout"))
        assertTrue(StravaUploader.isDefaultStravaName("Afternoon Weight Training"))
        assertTrue(StravaUploader.isDefaultStravaName("Morning Ride ⛅"))

        // French standard patterns
        assertTrue(StravaUploader.isDefaultStravaName("Sortie vélo le matin"))
        assertTrue(StravaUploader.isDefaultStravaName("Course à pied le soir"))

        // Custom titles must NOT be recognized as default
        assertFalse(StravaUploader.isDefaultStravaName("Sunday Club Ride"))
        assertFalse(StravaUploader.isDefaultStravaName("Schwarzwald Tour"))
        assertFalse(StravaUploader.isDefaultStravaName("Hausrunde"))
        assertFalse(StravaUploader.isDefaultStravaName("Hausrunde #3"))
        assertFalse(StravaUploader.isDefaultStravaName("Berlin Half Marathon"))
        assertFalse(StravaUploader.isDefaultStravaName("Radfahrt am Morgen mit Peter"))
        assertFalse(StravaUploader.isDefaultStravaName("Morning Ride with Friends"))
        assertFalse(StravaUploader.isDefaultStravaName("Cycling"))
        assertFalse(StravaUploader.isDefaultStravaName(""))
        assertFalse(StravaUploader.isDefaultStravaName(null))
    }

    @Test
    fun testDuplicateWithStravaDefaultNamePreservesLocalAppNameAndProtectsStrava() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = "Hausrunde #3", fileBaseName = fileBaseName)

        val stravaResponse = JSONObject().apply {
            put("id", 987654321L)
            put("name", "Radfahrt am Morgen")
            put("type", "Ride")
            put("sport_type", "Ride")
        }

        val uploader = TestableStravaUploader(mockContext, stravaResponse) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val result = uploader.doUpdate(exportInfo, isDuplicate = true)

        assertTrue("Update must succeed", result.success())

        // 1. Verify Strava update form request does NOT contain "name" (protects Strava)
        val metadataUpdate = capturedStravaRequests.last()
        assertFalse("Duplicate update must NOT send name to Strava", metadataUpdate.containsKey("name"))

        // 2. Verify local SQLite WORKOUT_NAME was NOT updated because Strava name is default
        assertTrue(
            "SQLite must NOT update workout name when Strava title is default",
            capturedUpdatedValues.isEmpty() || !capturedUpdatedValues.first().containsKey(WorkoutSummaries.WORKOUT_NAME)
        )
    }

    @Test
    fun testDuplicateWithStravaDefaultNameAndEmojiPreservesLocalAppName() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = "Feierabendrunde", fileBaseName = fileBaseName)

        val stravaResponse = JSONObject().apply {
            put("id", 987654321L)
            put("name", "Lauf am Abend ⛅")
            put("type", "Ride")
            put("sport_type", "Ride")
        }

        val uploader = TestableStravaUploader(mockContext, stravaResponse) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val result = uploader.doUpdate(exportInfo, isDuplicate = true)

        assertTrue("Update must succeed", result.success())

        // 1. Verify Strava update form request does NOT contain "name"
        val metadataUpdate = capturedStravaRequests.last()
        assertFalse("Duplicate update must NOT send name to Strava", metadataUpdate.containsKey("name"))

        // 2. Verify local SQLite WORKOUT_NAME was NOT updated because Strava name is default
        assertTrue(
            "SQLite must NOT update workout name when Strava title is default with emoji",
            capturedUpdatedValues.isEmpty() || !capturedUpdatedValues.first().containsKey(WorkoutSummaries.WORKOUT_NAME)
        )
    }

    @Test
    fun testPreUploadDiscoveryFindsExistingActivityBypassesUploadAndEnrichesCustomName() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = fileBaseName, fileBaseName = fileBaseName)

        var trackedActivityId: String? = null
        every { anyConstructed<StravaUploadDbHelper>().getActivityId(fileBaseName) } answers { trackedActivityId }
        every { anyConstructed<StravaUploadDbHelper>().updateAll(fileBaseName, any(), any(), any(), any()) } answers {
            trackedActivityId = secondArg()
        }

        val existingStravaJson = JSONObject().apply {
            put("id", 888123L)
            put("name", "Sunday Club Ride")
            put("type", "Ride")
            put("sport_type", "Ride")
        }

        val uploader = TestableStravaUploader(
            context = mockContext,
            stravaActivityResponse = existingStravaJson,
            mockExistingActivity = existingStravaJson
        ) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val result = uploader.doExport(exportInfo)

        assertTrue("Export must succeed via pre-upload duplicate discovery", result.success())
        assertFalse("Must not request retry", result.shallRetry())
        assertEquals("888123", trackedActivityId)

        // Metadata update must NOT send name to Strava (protects Strava's title)
        val metadataUpdate = capturedStravaRequests.last()
        assertFalse("Duplicate update must NOT send name to Strava", metadataUpdate.containsKey("name"))

        // SQLite WORKOUT_NAME must be enriched with Strava's custom title
        assertTrue("SQLite must be updated with Strava name", capturedUpdatedValues.isNotEmpty())
        val updatedName = capturedUpdatedValues.first().getAsString(WorkoutSummaries.WORKOUT_NAME)
        assertEquals("Sunday Club Ride", updatedName)
    }

    @Test
    fun testPreUploadDiscoveryFindsDefaultTitlePreservesLocalName() {
        val fileBaseName = "2024-05-12_10-15-30"
        setupMockCursor(workoutName = "Hausrunde #3", fileBaseName = fileBaseName)

        var trackedActivityId: String? = null
        every { anyConstructed<StravaUploadDbHelper>().getActivityId(fileBaseName) } answers { trackedActivityId }
        every { anyConstructed<StravaUploadDbHelper>().updateAll(fileBaseName, any(), any(), any(), any()) } answers {
            trackedActivityId = secondArg()
        }

        val existingStravaJson = JSONObject().apply {
            put("id", 777666L)
            put("name", "Radfahrt am Morgen")
            put("type", "Ride")
            put("sport_type", "Ride")
        }

        val uploader = TestableStravaUploader(
            context = mockContext,
            stravaActivityResponse = existingStravaJson,
            mockExistingActivity = existingStravaJson
        ) { requestMap ->
            capturedStravaRequests.add(requestMap)
        }

        val exportInfo = ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY)
        val result = uploader.doExport(exportInfo)

        assertTrue("Export must succeed via pre-upload duplicate discovery", result.success())
        assertEquals("777666", trackedActivityId)

        // Metadata update must NOT send name to Strava
        val metadataUpdate = capturedStravaRequests.last()
        assertFalse("Duplicate update must NOT send name to Strava", metadataUpdate.containsKey("name"))

        // SQLite WORKOUT_NAME must NOT be overwritten when Strava name is default
        assertTrue(
            "SQLite must NOT update workout name when Strava title is default",
            capturedUpdatedValues.isEmpty() || !capturedUpdatedValues.first().containsKey(WorkoutSummaries.WORKOUT_NAME)
        )
    }

    @Test
    fun testParseTimeToEpochSecondsFormats() {
        val uploader = TestableStravaUploader(mockContext, null)

        // Test ISO 8601 string from Strava start_date
        val isoEpoch = uploader.parseTimeToEpochSeconds("2024-05-12T10:15:30Z")
        assertNotNull("ISO format must parse", isoEpoch)
        assertEquals(1715508930L, isoEpoch)

        // Test SQLite format from WorkoutSummaries.TIME_START
        val sqliteEpoch = uploader.parseTimeToEpochSeconds("2024-05-12 10:15:30")
        assertNotNull("SQLite format must parse", sqliteEpoch)
        assertEquals(1715508930L, sqliteEpoch)

        // Invalid format
        assertNull(uploader.parseTimeToEpochSeconds("invalid-date-string"))
    }

    @Test
    fun testFindExistingStravaActivityMatchesClosestWithinWindow() {
        val targetEpoch = 1715508930L // 2024-05-12T10:15:30Z

        val actFar = JSONObject().apply {
            put("id", 111L)
            put("name", "Far Activity")
            put("start_date", "2024-05-12T10:18:00Z") // +150s
        }
        val actClose = JSONObject().apply {
            put("id", 222L)
            put("name", "Close Activity")
            put("start_date", "2024-05-12T10:15:45Z") // +15s
        }

        val jsonArray = JSONArray().apply {
            put(actFar)
            put(actClose)
        }

        val uploader = TestableStravaUploader(
            context = mockContext,
            stravaActivityResponse = null,
            mockExistingActivitiesArray = jsonArray
        )

        val matched = uploader.findExistingStravaActivity(targetEpoch)
        assertNotNull("Must match activity within window", matched)
        assertEquals(222L, matched!!.optLong("id"))
        assertEquals("Close Activity", matched.optString("name"))
    }

    @Test
    fun testFindExistingStravaActivityReturnsNullWhenNoActivities() {
        val uploader = TestableStravaUploader(
            context = mockContext,
            stravaActivityResponse = null,
            mockExistingActivitiesArray = JSONArray()
        )

        val matched = uploader.findExistingStravaActivity(1715508930L)
        assertNull("Empty array must return null", matched)
    }

    @Test
    fun testFindExistingStravaActivityReturnsNullWhenHttpError() {
        val uploader = object : TestableStravaUploader(mockContext, null) {
            override fun getStravaJsonArray(url: String): JSONArray? = null
        }

        val matched = uploader.findExistingStravaActivity(1715508930L)
        assertNull("HTTP failure must return null", matched)
    }
}
