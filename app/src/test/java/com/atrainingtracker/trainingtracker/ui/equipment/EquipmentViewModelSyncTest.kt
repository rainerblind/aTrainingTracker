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
import org.junit.Before
import org.junit.Test

/**
 * Unit verification suite for EquipmentViewModel bi-directional reactivity
 * upon external EquipmentRepository link change notifications (REQ-UI-257, TST-UI-216.4, REQ-UI-511).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentViewModelSyncTest {

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
        every { mockEquipmentRepo.getEquipmentItems(any<BSportType>()) } returns emptyList()
        every { mockDeviceRepo.allRemoteSensors } returns emptyList()
    }

    @After
    fun tearDown() {
        setStaticField(com.atrainingtracker.trainingtracker.TrainingApplication::class.java, "cSharedPreferences", null)
        EquipmentRepository.resetForTesting(null)
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

    /**
     * TST-UI-216.4: EquipmentViewModel reloads equipment reactively when
     * EquipmentRepository.notifyEquipmentLinksChanged() is invoked.
     */
    @Test
    fun testEquipmentViewModel_reloadsEquipment_onExternalLinksChanged() {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify initial load occurred
        verify(atLeast = 1) { mockEquipmentRepo.getEquipmentItems(BSportType.BIKE) }

        clearMocks(mockEquipmentRepo, answers = false)

        // Act: External event (e.g. from sensor settings) triggers notification
        EquipmentRepository.notifyEquipmentLinksChanged(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert: loadEquipment was re-triggered
        verify(atLeast = 1) { mockEquipmentRepo.getEquipmentItems(BSportType.BIKE) }
    }
}
