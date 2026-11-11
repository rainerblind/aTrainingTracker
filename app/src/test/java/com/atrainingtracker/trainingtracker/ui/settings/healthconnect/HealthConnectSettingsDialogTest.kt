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

package com.atrainingtracker.trainingtracker.ui.settings.healthconnect

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field

/**
 * Contract and integration test suite for [HealthConnectSettingsDialog] (REQ-SET-103, TST-SET-103).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectSettingsDialogTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field: Field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", prefs)
        TrainingApplication.setHealthConnectHrvImportEnabled(false)
        TrainingApplication.setSyncToHealthConnect(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        TrainingApplication.setHealthConnectHrvImportEnabled(false)
        TrainingApplication.setSyncToHealthConnect(false)
    }

    @Test
    fun testHealthConnectManager_permissionsContract() {
        assertTrue(
            HealthConnectManager.HRV_READ_PERMISSIONS.contains(
                androidx.health.connect.client.permission.HealthPermission.getReadPermission(
                    androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord::class
                )
            )
        )
        assertTrue(
            HealthConnectManager.HRV_READ_PERMISSIONS.contains(
                androidx.health.connect.client.permission.HealthPermission.getReadPermission(
                    androidx.health.connect.client.records.RestingHeartRateRecord::class
                )
            )
        )
    }

    @Test
    fun testPreferences_hrvImportToggleStatePersistence() {
        assertFalse(TrainingApplication.isHealthConnectHrvImportEnabled())
        TrainingApplication.setHealthConnectHrvImportEnabled(true)
        assertTrue(TrainingApplication.isHealthConnectHrvImportEnabled())
        TrainingApplication.setHealthConnectHrvImportEnabled(false)
        assertFalse(TrainingApplication.isHealthConnectHrvImportEnabled())
    }
}
