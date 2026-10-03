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
 */

package com.atrainingtracker.trainingtracker.exporter.uploader

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import io.mockk.*
import okhttp3.FormBody
import okhttp3.RequestBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Strava workout_type mapping for Race workouts (REQ-UI-236, TST-UI-195.5).
 */
class StravaUploaderRaceWorkoutTypeTest {

    private lateinit var mockContext: Context
    private lateinit var mockSqlDb: SQLiteDatabase
    private lateinit var mockCursor: Cursor

    private class TestableStravaUploader(
        context: Context,
        private val onRequestCaptured: (Map<String, String>) -> Unit
    ) : StravaUploader(context) {

        override fun getStravaActivity(stravaActivityId: String): JSONObject? {
            return JSONObject().apply {
                put("id", stravaActivityId)
                put("type", "Ride")
                put("sport_type", "Ride")
            }
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
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockSqlDb = mockk(relaxed = true)
        mockCursor = mockk(relaxed = true)

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager
        every { mockSummariesManager.database } returns mockSqlDb
        every { mockSqlDb.query(any(), any(), any(), any(), any(), any(), any()) } returns mockCursor

        mockkStatic(SportTypeDatabaseManager::class)
        val mockSportTypeManager = mockk<SportTypeDatabaseManager>(relaxed = true)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeManager
        every { mockSportTypeManager.getStravaName(any()) } returns "Ride"

        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns "mock_access_token"

        mockkConstructor(StravaUploadDbHelper::class)
        every { anyConstructed<StravaUploadDbHelper>().getActivityId(any()) } returns "123456789"
        every { anyConstructed<StravaUploadDbHelper>().updateStravaActivityData(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun setupCursor(race: Boolean, bSportType: BSportType) {
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID) } returns 0
        every { mockCursor.getLong(0) } returns 1L

        val columnMap = mapOf(
            WorkoutSummaries.SPORT_ID to 0,
            WorkoutSummaries.WORKOUT_NAME to 1,
            WorkoutSummaries.DESCRIPTION to 2,
            WorkoutSummaries.TRAINER to 3,
            WorkoutSummaries.COMMUTE to 4,
            WorkoutSummaries.EQUIPMENT_ID to 5,
            WorkoutSummaries.RACE to 6,
            WorkoutSummaries.B_SPORT to 7
        )

        columnMap.forEach { (colName, index) ->
            every { mockCursor.getColumnIndexOrThrow(colName) } returns index
            every { mockCursor.getColumnIndex(colName) } returns index
        }
        every { mockCursor.isNull(any()) } answers { firstArg<Int>() == 5 } // null equipment

        every { mockCursor.getString(1) } returns "Race Event"
        every { mockCursor.getString(2) } returns "Championship"
        every { mockCursor.getInt(3) } returns 0 // trainer = false
        every { mockCursor.getInt(4) } returns 0 // commute = false
        every { mockCursor.getInt(6) } returns if (race) 1 else 0 // race
        every { mockCursor.getString(7) } returns bSportType.name // bSportType
    }

    @Test
    fun testDoUpdate_bikeRace_setsWorkoutType11() {
        setupCursor(race = true, bSportType = BSportType.BIKE)

        val capturedForms = mutableListOf<Map<String, String>>()
        val uploader = TestableStravaUploader(mockContext) { map ->
            capturedForms.add(map)
        }

        val exportInfo = ExportInfo("2026-10-02-100000", FileFormat.STRAVA, ExportType.COMMUNITY)
        uploader.doUpdate(exportInfo, isDuplicate = false)

        val metadataForm = capturedForms.firstOrNull { it.containsKey("trainer") }
        assertEquals("11", metadataForm?.get("workout_type"))
    }

    @Test
    fun testDoUpdate_runRace_setsWorkoutType1() {
        setupCursor(race = true, bSportType = BSportType.RUN)

        val capturedForms = mutableListOf<Map<String, String>>()
        val uploader = TestableStravaUploader(mockContext) { map ->
            capturedForms.add(map)
        }

        val exportInfo = ExportInfo("2026-10-02-100000", FileFormat.STRAVA, ExportType.COMMUNITY)
        uploader.doUpdate(exportInfo, isDuplicate = false)

        val metadataForm = capturedForms.firstOrNull { it.containsKey("trainer") }
        assertEquals("1", metadataForm?.get("workout_type"))
    }

    @Test
    fun testDoUpdate_raceFalse_omitsWorkoutType() {
        setupCursor(race = false, bSportType = BSportType.BIKE)

        val capturedForms = mutableListOf<Map<String, String>>()
        val uploader = TestableStravaUploader(mockContext) { map ->
            capturedForms.add(map)
        }

        val exportInfo = ExportInfo("2026-10-02-100000", FileFormat.STRAVA, ExportType.COMMUNITY)
        uploader.doUpdate(exportInfo, isDuplicate = false)

        val metadataForm = capturedForms.firstOrNull { it.containsKey("trainer") }
        assertNull(metadataForm?.get("workout_type"))
    }
}
