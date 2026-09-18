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

package com.atrainingtracker.trainingtracker.ui.equipment

import android.app.Application
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying EquipmentViewModel handles EquipmentItem.isRetired
 * during loadEquipment, updateEquipment, and toggleRetired (REQ-UI-158, TST-UI-111, ATT-1118).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentViewModelRetiredTest {

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
            dbSummariesManager = mockSummariesManager
        )
    }

    @Test
    fun testLoadEquipment_populatesIsRetiredAccurately() {
        val activeBikeData = EquipmentDbHelper.EquipmentData(
            1L, "Active Canyon", BSportType.BIKE, 3, "Canyon", "c1", false
        )
        val retiredBikeData = EquipmentDbHelper.EquipmentData(
            2L, "Retired Trek", BSportType.BIKE, 3, "Trek", "t2", true
        )

        every {
            mockDbHelper.getEquipmentItems(BSportType.BIKE)
        } returns listOf(activeBikeData, retiredBikeData)

        every {
            mockDbHelper.getEquipmentItems(BSportType.RUN)
        } returns emptyList()

        val viewModel = createViewModel()
        viewModel.loadEquipment()
        testDispatcher.scheduler.advanceUntilIdle()

        val bikes = viewModel.bikes.value
        assertEquals(2, bikes.size)
        assertFalse("First bike should have isRetired == false", bikes[0].isRetired)
        assertTrue("Second bike should have isRetired == true", bikes[1].isRetired)
    }

    @Test
    fun testUpdateEquipment_passesIsRetiredToDbHelper() {
        val viewModel = createViewModel()

        val itemToUpdate = EquipmentItem(
            id = 42L,
            name = "My Specialized",
            linkedDeviceIds = listOf(101L),
            linkedDeviceNames = "Speed Sensor",
            linkedSportTypeIds = listOf(1L),
            linkedSportTypeNames = "Cycling",
            frameType = 1,
            stravaName = "Spec",
            stravaId = "s123",
            isRetired = true,
            firstUsed = null,
            lastUsed = null,
            statsData = mockk(relaxed = true)
        )

        every {
            mockDbHelper.updateEquipment(42L, "My Specialized", 1, listOf(101L), true)
        } just Runs

        viewModel.updateEquipment(itemToUpdate)
        testDispatcher.scheduler.advanceUntilIdle()

        verify {
            mockDbHelper.updateEquipment(42L, "My Specialized", 1, listOf(101L), true)
        }
    }

    @Test
    fun testToggleRetired_callsSetEquipmentRetiredWithInvertedStatus() {
        val viewModel = createViewModel()

        val activeItem = EquipmentItem(
            id = 10L,
            name = "Active Bike",
            linkedDeviceIds = emptyList(),
            linkedDeviceNames = "",
            linkedSportTypeIds = emptyList(),
            linkedSportTypeNames = "",
            frameType = 3,
            stravaName = null,
            stravaId = null,
            isRetired = false,
            firstUsed = null,
            lastUsed = null,
            statsData = mockk(relaxed = true)
        )

        every {
            mockDbHelper.setEquipmentRetired(10L, true)
        } just Runs

        viewModel.toggleRetired(activeItem)
        testDispatcher.scheduler.advanceUntilIdle()

        verify {
            mockDbHelper.setEquipmentRetired(10L, true)
        }
    }
}
