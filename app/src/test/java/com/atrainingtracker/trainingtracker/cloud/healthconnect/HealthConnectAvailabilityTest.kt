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
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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

/**
 * Unit test suite verifying Health Connect SDK availability lifecycle, permission sets,
 * and contract checks (REQ-SET-100, TST-SET-100).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectAvailabilityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        HealthConnectManager.sdkStatusOverride = null
        HealthConnectManager.clientProvider = null
    }

    @After
    fun tearDown() {
        HealthConnectManager.sdkStatusOverride = null
        HealthConnectManager.clientProvider = null
    }

    @Test
    fun testSdkStatusAvailabilityTransitions() {
        // When SDK is available
        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE
        assertEquals(HealthConnectClient.SDK_AVAILABLE, HealthConnectManager.getSdkStatus(context))
        assertTrue(HealthConnectManager.isAvailable(context))

        // When SDK is unavailable
        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_UNAVAILABLE
        assertEquals(HealthConnectClient.SDK_UNAVAILABLE, HealthConnectManager.getSdkStatus(context))
        assertFalse(HealthConnectManager.isAvailable(context))

        // When update required
        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
        assertEquals(HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED, HealthConnectManager.getSdkStatus(context))
        assertFalse(HealthConnectManager.isAvailable(context))
    }

    @Test
    fun testRequiredPermissionsCompleteness() {
        val permissions = HealthConnectManager.REQUIRED_PERMISSIONS
        assertTrue(permissions.contains(HealthPermission.getWritePermission(ExerciseSessionRecord::class)))
        assertTrue(permissions.contains("android.permission.health.WRITE_EXERCISE_ROUTE"))
        assertTrue(permissions.contains(HealthPermission.getWritePermission(HeartRateRecord::class)))
        assertTrue(permissions.contains(HealthPermission.getWritePermission(PowerRecord::class)))
        assertTrue(permissions.contains(HealthPermission.getWritePermission(SpeedRecord::class)))
        assertTrue(permissions.contains(HealthPermission.getWritePermission(DistanceRecord::class)))
        assertTrue(permissions.contains(HealthPermission.getWritePermission(ElevationGainedRecord::class)))
        assertTrue(permissions.contains(HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)))
        assertTrue(permissions.contains(HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class)))
        assertTrue(permissions.contains(HealthPermission.getWritePermission(StepsCadenceRecord::class)))
        // 9 unique permissions (CyclingPedalingCadenceRecord shares WRITE_EXERCISE with ExerciseSessionRecord)
        assertEquals(9, permissions.size)
    }

    @Test
    fun testManifestPermissionsAndAliasDeclaration() {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        )
        val manifestFile = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find AndroidManifest.xml")
        val manifestContent = manifestFile.readText()
        assertTrue(manifestContent.contains("android.permission.health.WRITE_ELEVATION_GAINED"))
        assertTrue(manifestContent.contains("android.permission.health.WRITE_STEPS"))
        assertTrue(manifestContent.contains("android.intent.action.VIEW_PERMISSION_USAGE"))
        assertTrue(manifestContent.contains("android.permission.START_VIEW_PERMISSION_USAGE"))
    }

    @Test
    fun testPermissionInspectionContract() = runBlocking {
        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE

        val mockClient = mockk<HealthConnectClient>()
        val mockPermissionController = mockk<PermissionController>()

        coEvery { mockClient.permissionController } returns mockPermissionController
        coEvery { mockPermissionController.getGrantedPermissions() } returns setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        )

        HealthConnectManager.clientProvider = { mockClient }

        // Has minimal exercise write permission
        assertTrue(HealthConnectManager.hasWriteExercisePermission(context))
        // But does not yet have ALL permissions
        assertFalse(HealthConnectManager.hasAllPermissions(context))

        // When all granted
        coEvery { mockPermissionController.getGrantedPermissions() } returns HealthConnectManager.REQUIRED_PERMISSIONS
        assertTrue(HealthConnectManager.hasAllPermissions(context))
    }
}
