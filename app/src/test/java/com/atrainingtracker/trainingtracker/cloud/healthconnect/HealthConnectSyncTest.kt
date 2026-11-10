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

package com.atrainingtracker.trainingtracker.cloud.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field
import java.time.LocalDateTime

/**
 * Unit test suite verifying Health Connect asynchronous sync, idempotent record insertions,
 * and preference state integration (REQ-SET-103, TST-SET-103).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectSyncTest {

    private lateinit var context: Context
    private val mockClient = mockk<HealthConnectClient>(relaxed = true)
    private val mockPermissionController = mockk<PermissionController>()

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field: Field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", prefs)

        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE
        HealthConnectManager.clientProvider = { mockClient }

        coEvery { mockClient.permissionController } returns mockPermissionController
        coEvery { mockPermissionController.getGrantedPermissions() } returns HealthConnectManager.REQUIRED_PERMISSIONS
        coEvery { mockClient.insertRecords(any()) } returns mockk<InsertRecordsResponse>()
    }

    @After
    fun tearDown() {
        HealthConnectManager.sdkStatusOverride = null
        HealthConnectManager.clientProvider = null
        TrainingApplication.setSyncToHealthConnect(false)
    }

    private fun sampleWorkout(): WorkoutData {
        return WorkoutData(
            id = 101L,
            finished = true,
            fileBaseName = "2026-11-10-120000",
            workoutName = "Lunch Run",
            sportId = 2L,
            sportName = "Run",
            bSportType = BSportType.RUN,
            startTimeS = 1762775000L,
            formattedDate = "10.11.2026",
            formattedTime = "12:00",
            localDateTime = LocalDateTime.of(2026, 11, 10, 12, 0),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            source = WorkoutSource.TRACKED,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 1,
            totalDistance = 5000.0,
            maxDisplacement = 2500.0,
            activeTimeSec = 1500L,
            totalTimeSec = 1550L,
            avgSpeedMps = 3.2,
            ascentMeters = 30L,
            descentMeters = 30L,
            minAltitude = 100.0,
            maxAltitude = 130.0,
            description = null,
            goal = null,
            method = null,
            stravaSportName = "Run"
        )
    }

    @Test
    fun testSyncDisabledBySetting() = runBlocking {
        TrainingApplication.setSyncToHealthConnect(false)

        val result = HealthConnectManager.syncWorkout(context, sampleWorkout())
        assertFalse(result.isSuccess)
        coVerify(exactly = 0) { mockClient.insertRecords(any()) }
    }

    @Test
    fun testSyncSuccessWhenEnabledAndPermitted() = runBlocking {
        TrainingApplication.setSyncToHealthConnect(true)

        val result = HealthConnectManager.syncWorkout(context, sampleWorkout())
        assertTrue(result.isSuccess)
        coVerify(atLeast = 1) { mockClient.insertRecords(any()) }
        assertTrue(TrainingApplication.getHealthConnectLastSyncTimestamp() > 0L)
    }

    @Test
    fun testSyncIdempotentFallbackToUpdate() = runBlocking {
        TrainingApplication.setSyncToHealthConnect(true)

        // When insert throws a conflict / exception
        coEvery { mockClient.insertRecords(any()) } throws RuntimeException("Record already exists")
        coEvery { mockClient.updateRecords(any()) } returns Unit

        val result = HealthConnectManager.syncWorkout(context, sampleWorkout())
        assertTrue(result.isSuccess)
        coVerify(atLeast = 1) { mockClient.updateRecords(any()) }
    }
}
