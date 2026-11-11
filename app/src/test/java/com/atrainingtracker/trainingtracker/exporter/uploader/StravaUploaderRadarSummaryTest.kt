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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.RadarTrafficSummary
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSummaryUpdateData
import io.mockk.*
import okhttp3.FormBody
import okhttp3.RequestBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Unit verification tests for Strava activity description radar summary composition (REQ-RAD-007 / TST-RAD-007.2).
 */
@RunWith(RobolectricTestRunner::class)
class StravaUploaderRadarSummaryTest {

    private lateinit var context: Context
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockSportTypesRepository: SportTypesRepository
    private var capturedFormBody: Map<String, String>? = null

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
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val spiedContext = spyk(appContext)
        every { spiedContext.getString(R.string.radar_summary_header, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val count = formatArgs[0]
            val density = formatArgs[1]
            "📡 Radar: $count vehicles passed ($density/km)"
        }
        every { spiedContext.getString(R.string.radar_summary_close_passes_singular, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val dist = formatArgs[0]
            "⚠️ 1 close pass (<1.5m, min ${dist}m)"
        }
        every { spiedContext.getString(R.string.radar_summary_close_passes, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val count = formatArgs[0]
            val dist = formatArgs[1]
            "⚠️ $count close passes (<1.5m, min ${dist}m)"
        }
        every { spiedContext.getString(R.string.radar_summary_heavy_vehicles_singular) } returns "🚛 1 heavy vehicle"
        every { spiedContext.getString(R.string.radar_summary_heavy_vehicles, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val count = formatArgs[0]
            "🚛 $count heavy vehicles"
        }
        every { spiedContext.getString(R.string.radar_summary_max_speed_simple, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val speed = formatArgs[0]
            "🚀 Max $speed km/h"
        }
        every { spiedContext.getString(R.string.radar_summary_max_speed, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            val speed = formatArgs[0]
            val appSpeed = formatArgs[1]
            "🚀 Max $speed km/h (+$appSpeed km/h approach)"
        }
        context = spiedContext
        Locale.setDefault(Locale.US)

        mockWorkoutRepository = mockk(relaxed = true)
        WorkoutRepository.resetForTesting(mockWorkoutRepository)

        mockSportTypesRepository = mockk(relaxed = true)
        every { mockSportTypesRepository.getStravaName(any()) } returns "Ride"
        SportTypesRepository.resetForTesting(mockSportTypesRepository)

        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns "mock_access_token"

        mockkStatic(TrainingApplication::class)

        val mockStravaUploadDbHelper = mockk<StravaUploadDbHelper>(relaxed = true)
        every { mockStravaUploadDbHelper.getActivityId(any()) } returns "123456789"
        every { mockStravaUploadDbHelper.updateStravaActivityData(any(), any()) } returns Unit
        StravaUploadDbHelper.resetForTesting(mockStravaUploadDbHelper)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        SportTypesRepository.resetForTesting(null)
        StravaUploadDbHelper.resetForTesting(null)
        unmockkAll()
    }

    private fun createUploader(): TestableStravaUploader {
        return TestableStravaUploader(context) { map ->
            capturedFormBody = map
        }
    }

    @Test
    fun doUpdate_withRadarSummaryEnabled_appendsFormattedSummaryToEmptyDescription() {
        every { TrainingApplication.uploadStravaRadarSummary() } returns true

        val summaryData = WorkoutSummaryUpdateData(
            sportId = 1L,
            workoutName = "Morning Ride",
            description = "",
            trainer = false,
            commute = false,
            race = false,
            bSportString = "BIKE",
            bSportType = BSportType.BIKE,
            equipmentId = null
        )
        every { mockWorkoutRepository.getWorkoutSummaryUpdateData("test_file") } returns summaryData

        val radarSummary = RadarTrafficSummary(
            totalVehicles = 114,
            vehiclesPerKm = 1.8,
            maxVehicleSpeedMps = 104.0 / 3.6,
            maxApproachSpeedMps = 68.0 / 3.6
        )
        every { mockWorkoutRepository.getRadarTrafficSummary("test_file") } returns radarSummary

        val exportInfo = ExportInfo("test_file", FileFormat.TCX, ExportType.COMMUNITY)
        val uploader = createUploader()
        val result = uploader.doUpdate(exportInfo, false)

        assertTrue(result.success())
        assertEquals(
            "📡 Radar: 114 vehicles passed (1.8/km) • 🚀 Max 104 km/h (+68 km/h approach)",
            capturedFormBody?.get("description")
        )
    }

    @Test
    fun doUpdate_withRadarSummaryEnabled_appendsFormattedSummaryToExistingNotesWithDoubleNewline() {
        every { TrainingApplication.uploadStravaRadarSummary() } returns true

        val summaryData = WorkoutSummaryUpdateData(
            sportId = 1L,
            workoutName = "Morning Ride",
            description = "Great ride through the hills.",
            trainer = false,
            commute = false,
            race = false,
            bSportString = "BIKE",
            bSportType = BSportType.BIKE,
            equipmentId = null
        )
        every { mockWorkoutRepository.getWorkoutSummaryUpdateData("test_file") } returns summaryData

        val radarSummary = RadarTrafficSummary(
            totalVehicles = 50,
            vehiclesPerKm = 1.2,
            maxVehicleSpeedMps = 90.0 / 3.6,
            maxApproachSpeedMps = 50.0 / 3.6
        )
        every { mockWorkoutRepository.getRadarTrafficSummary("test_file") } returns radarSummary

        val exportInfo = ExportInfo("test_file", FileFormat.TCX, ExportType.COMMUNITY)
        val uploader = createUploader()
        val result = uploader.doUpdate(exportInfo, false)

        assertTrue(result.success())
        assertEquals(
            "Great ride through the hills.\n\n📡 Radar: 50 vehicles passed (1.2/km) • 🚀 Max 90 km/h (+50 km/h approach)",
            capturedFormBody?.get("description")
        )
    }

    @Test
    fun doUpdate_withRadarSummaryDisabled_preservesExistingDescriptionWithoutSummary() {
        every { TrainingApplication.uploadStravaRadarSummary() } returns false

        val summaryData = WorkoutSummaryUpdateData(
            sportId = 1L,
            workoutName = "Morning Ride",
            description = "Solo ride.",
            trainer = false,
            commute = false,
            race = false,
            bSportString = "BIKE",
            bSportType = BSportType.BIKE,
            equipmentId = null
        )
        every { mockWorkoutRepository.getWorkoutSummaryUpdateData("test_file") } returns summaryData

        val exportInfo = ExportInfo("test_file", FileFormat.TCX, ExportType.COMMUNITY)
        val uploader = createUploader()
        val result = uploader.doUpdate(exportInfo, false)

        assertTrue(result.success())
        assertEquals("Solo ride.", capturedFormBody?.get("description"))
    }

    @Test
    fun doUpdate_withRadarSummaryEnabled_doesNotDuplicateIfAlreadyPresent() {
        every { TrainingApplication.uploadStravaRadarSummary() } returns true

        val existingWithRadar = "Nice weather.\n\n📡 Radar: 114 vehicles passed (1.8/km) • 🚀 Max 104 km/h"
        val summaryData = WorkoutSummaryUpdateData(
            sportId = 1L,
            workoutName = "Morning Ride",
            description = existingWithRadar,
            trainer = false,
            commute = false,
            race = false,
            bSportString = "BIKE",
            bSportType = BSportType.BIKE,
            equipmentId = null
        )
        every { mockWorkoutRepository.getWorkoutSummaryUpdateData("test_file") } returns summaryData

        val radarSummary = RadarTrafficSummary(
            totalVehicles = 114,
            vehiclesPerKm = 1.8,
            maxVehicleSpeedMps = 104.0 / 3.6,
            maxApproachSpeedMps = 68.0 / 3.6
        )
        every { mockWorkoutRepository.getRadarTrafficSummary("test_file") } returns radarSummary

        val exportInfo = ExportInfo("test_file", FileFormat.TCX, ExportType.COMMUNITY)
        val uploader = createUploader()
        val result = uploader.doUpdate(exportInfo, false)

        assertTrue(result.success())
        assertEquals(existingWithRadar, capturedFormBody?.get("description"))
    }

    @Test
    fun doUpdate_withZeroVehicles_doesNotAppendSummary() {
        every { TrainingApplication.uploadStravaRadarSummary() } returns true

        val summaryData = WorkoutSummaryUpdateData(
            sportId = 1L,
            workoutName = "Morning Ride",
            description = "Indoor session.",
            trainer = false,
            commute = false,
            race = false,
            bSportString = "BIKE",
            bSportType = BSportType.BIKE,
            equipmentId = null
        )
        every { mockWorkoutRepository.getWorkoutSummaryUpdateData("test_file") } returns summaryData

        val radarSummary = RadarTrafficSummary(totalVehicles = 0)
        every { mockWorkoutRepository.getRadarTrafficSummary("test_file") } returns radarSummary

        val exportInfo = ExportInfo("test_file", FileFormat.TCX, ExportType.COMMUNITY)
        val uploader = createUploader()
        val result = uploader.doUpdate(exportInfo, false)

        assertTrue(result.success())
        assertEquals("Indoor session.", capturedFormBody?.get("description"))
    }
}
