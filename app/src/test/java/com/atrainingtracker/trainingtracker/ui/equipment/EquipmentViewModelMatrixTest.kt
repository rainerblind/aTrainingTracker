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

package com.atrainingtracker.trainingtracker.ui.equipment

import android.app.Application
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.SimpleSensorInfo
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit verification suite for EquipmentViewModel matrix state flow and sensor link operations
 * (REQ-UI-256, TST-UI-215, ATT-2126).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentViewModelMatrixTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockApplication: Application
    private lateinit var mockDbHelper: EquipmentDbHelper
    private lateinit var mockLinksHelper: SportTypeEquipmentLinkManager
    private lateinit var mockSportTypeManager: SportTypeDatabaseManager
    private lateinit var mockDevicesManager: DevicesDatabaseManager
    private lateinit var mockSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockPrefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        mockPrefs = mockk(relaxed = true)
        every { mockPrefs.getString(any(), any()) } returns "METRIC"
        setStaticField(com.atrainingtracker.trainingtracker.TrainingApplication::class.java, "cSharedPreferences", mockPrefs)

        mockApplication = mockk(relaxed = true)
        mockDbHelper = mockk(relaxed = true)
        mockLinksHelper = mockk(relaxed = true)
        mockSportTypeManager = mockk(relaxed = true)
        mockDevicesManager = mockk(relaxed = true)
        mockSummariesManager = mockk(relaxed = true)

        every { mockSummariesManager.getEquipmentStats(any()) } returns WorkoutSummariesDatabaseManager.Stats()
    }

    @After
    fun tearDown() {
        setStaticField(com.atrainingtracker.trainingtracker.TrainingApplication::class.java, "cSharedPreferences", null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    private fun createViewModel(): EquipmentViewModel {
        return EquipmentViewModel(
            application = mockApplication,
            ioDispatcher = testDispatcher,
            dbEquipmentHelper = mockDbHelper,
            dbLinksHelper = mockLinksHelper,
            dbSportHelper = mockSportTypeManager,
            dbDevicesHelper = mockDevicesManager,
            dbSummariesManager = mockSummariesManager,
            syncStatusFlow = MutableStateFlow(false)
        )
    }

    @Test
    fun testAllRemoteSensors_initializationAndEmission() {
        val sensors = listOf(
            SimpleSensorInfo(10L, "Garmin HRM-Dual"),
            SimpleSensorInfo(20L, "Stages Power Meter"),
            SimpleSensorInfo(30L, "Wahoo SPEED")
        )
        every { mockDevicesManager.allRemoteSensors } returns sensors
        every { mockDbHelper.getEquipmentItems(any<BSportType>()) } returns emptyList()

        val viewModel = createViewModel()
        assertEquals(0, viewModel.allRemoteSensors.value.size)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.allRemoteSensors.value.size)
        assertEquals("Garmin HRM-Dual", viewModel.allRemoteSensors.value[0].name)
        assertEquals("Stages Power Meter", viewModel.allRemoteSensors.value[1].name)
        assertEquals("Wahoo SPEED", viewModel.allRemoteSensors.value[2].name)
    }

    @Test
    fun testSetSensorLink_invokesDbHelperAndReloadsEquipment() {
        val bike = EquipmentDbHelper.EquipmentData(
            1L, "Trek Emonda", BSportType.BIKE, 3, null, null, false
        )
        every { mockDbHelper.getEquipmentItems(BSportType.BIKE) } returns listOf(bike)
        every { mockDbHelper.getEquipmentItems(BSportType.RUN) } returns emptyList()
        every { mockDbHelper.getDeviceIdsForEquipment(1L) } returns listOf(10L) andThen listOf(10L, 20L)
        every { mockDevicesManager.allRemoteSensors } returns listOf(
            SimpleSensorInfo(10L, "HRM"),
            SimpleSensorInfo(20L, "Power")
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.bikes.value.size)
        assertEquals(listOf(10L), viewModel.bikes.value[0].linkedDeviceIds)

        // Toggle sensor 20 to true
        viewModel.setSensorLink(1L, 20L, true)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockDbHelper.setDeviceLink(1L, 20L, true) }
        // Verify loadEquipment was triggered and updated state
        assertEquals(1, viewModel.bikes.value.size)
        assertEquals(listOf(10L, 20L), viewModel.bikes.value[0].linkedDeviceIds)

        // Toggle sensor 10 to false (unlink)
        every { mockDbHelper.getDeviceIdsForEquipment(1L) } returns listOf(20L)
        viewModel.setSensorLink(1L, 10L, false)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockDbHelper.setDeviceLink(1L, 10L, false) }
        assertEquals(listOf(20L), viewModel.bikes.value[0].linkedDeviceIds)
    }
}
