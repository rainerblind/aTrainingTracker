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
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.SimpleSensorInfo
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutStatsSummary
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
 * (REQ-UI-256, TST-UI-215, ATT-2126, REQ-UI-511).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentViewModelMatrixTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockApplication: Application
    private lateinit var mockEquipmentRepo: EquipmentRepository
    private lateinit var mockSportRepo: SportTypesRepository
    private lateinit var mockDeviceRepo: DeviceDataRepository
    private lateinit var mockWorkoutRepo: WorkoutRepository
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
        mockEquipmentRepo = mockk(relaxed = true)
        mockSportRepo = mockk(relaxed = true)
        mockDeviceRepo = mockk(relaxed = true)
        mockWorkoutRepo = mockk(relaxed = true)

        every { mockWorkoutRepo.getEquipmentStats(any()) } returns WorkoutStatsSummary()
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
            equipmentRepository = mockEquipmentRepo,
            sportTypesRepository = mockSportRepo,
            deviceDataRepository = mockDeviceRepo,
            workoutRepository = mockWorkoutRepo,
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
        every { mockDeviceRepo.allRemoteSensors } returns sensors
        every { mockEquipmentRepo.getEquipmentItems(any<BSportType>()) } returns emptyList()

        val viewModel = createViewModel()
        assertEquals(0, viewModel.allRemoteSensors.value.size)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.allRemoteSensors.value.size)
        assertEquals("Garmin HRM-Dual", viewModel.allRemoteSensors.value[0].name)
        assertEquals("Stages Power Meter", viewModel.allRemoteSensors.value[1].name)
        assertEquals("Wahoo SPEED", viewModel.allRemoteSensors.value[2].name)
    }

    @Test
    fun testSportPartitionedSensors_emitsFilteredSensors() {
        val sensors = listOf(
            SimpleSensorInfo(10L, "Garmin HRM-Dual", com.atrainingtracker.banalservice.devices.DeviceType.HRM),
            SimpleSensorInfo(20L, "Stages Power", com.atrainingtracker.banalservice.devices.DeviceType.BIKE_POWER),
            SimpleSensorInfo(30L, "Stryd Footpod", com.atrainingtracker.banalservice.devices.DeviceType.RUN_SPEED),
            SimpleSensorInfo(40L, "Tempe", com.atrainingtracker.banalservice.devices.DeviceType.ENVIRONMENT)
        )
        every { mockDeviceRepo.allRemoteSensors } returns sensors
        every { mockEquipmentRepo.getEquipmentItems(any<BSportType>()) } returns emptyList()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Bike sensors: Stages Power (bike power), HRM (shared), Tempe (shared) -> 3 items ordered by precedence
        assertEquals(3, viewModel.bikeSensors.value.size)
        assertEquals(listOf(20L, 10L, 40L), viewModel.bikeSensors.value.map { it.id })

        // Shoe sensors: Stryd Footpod (run speed), HRM (shared), Tempe (shared) -> 3 items ordered by precedence
        assertEquals(3, viewModel.shoeSensors.value.size)
        assertEquals(listOf(30L, 10L, 40L), viewModel.shoeSensors.value.map { it.id })
    }

    @Test
    fun testSetSensorLink_invokesDbHelperAndReloadsEquipment() {
        val bike = EquipmentDbHelper.EquipmentData(
            1L, "Trek Emonda", BSportType.BIKE, 3, null, null, false
        )
        every { mockEquipmentRepo.getEquipmentItems(BSportType.BIKE) } returns listOf(bike)
        every { mockEquipmentRepo.getEquipmentItems(BSportType.RUN) } returns emptyList()
        every { mockEquipmentRepo.getDeviceIdsForEquipment(1L) } returns listOf(10L) andThen listOf(10L, 20L)
        every { mockDeviceRepo.allRemoteSensors } returns listOf(
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

        verify(exactly = 1) { mockEquipmentRepo.setDeviceLink(1L, 20L, true) }
        // Verify loadEquipment was triggered and updated state
        assertEquals(1, viewModel.bikes.value.size)
        assertEquals(listOf(10L, 20L), viewModel.bikes.value[0].linkedDeviceIds)

        // Toggle sensor 10 to false (unlink)
        every { mockEquipmentRepo.getDeviceIdsForEquipment(1L) } returns listOf(20L)
        viewModel.setSensorLink(1L, 10L, false)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockEquipmentRepo.setDeviceLink(1L, 10L, false) }
        assertEquals(listOf(20L), viewModel.bikes.value[0].linkedDeviceIds)
    }
}
