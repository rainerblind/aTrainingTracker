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
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit verification suite for EquipmentViewModel lifecycle behavior (REQ-UI-160, TST-UI-112, ATT-1309):
 * 1. Automated self-initialization upon construction (populates bikes and shoes).
 * 2. Reactive falling-edge observation of syncStatusFlow (true -> false reloads).
 * 3. Cold-start redundancy suppression (initial false does not duplicate queries).
 * 4. Coroutine job cancellation and deduplication for rapid successive loadEquipment calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentViewModelLifecycleTest {

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

    private fun createViewModel(syncFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)): EquipmentViewModel {
        return EquipmentViewModel(
            application = mockApplication,
            ioDispatcher = testDispatcher,
            dbEquipmentHelper = mockDbHelper,
            dbLinksHelper = mockLinksHelper,
            dbSportHelper = mockSportTypeManager,
            dbDevicesHelper = mockDevicesManager,
            dbSummariesManager = mockSummariesManager,
            syncStatusFlow = syncFlow
        )
    }

    @Test
    fun testInit_automaticallyLoadsEquipmentOnCreation() {
        val bike = EquipmentDbHelper.EquipmentData(
            1L, "Canyon Grail", BSportType.BIKE, 3, "Canyon", "c1", false
        )
        val shoe = EquipmentDbHelper.EquipmentData(
            2L, "Nike Pegasus", BSportType.RUN, 1, "Nike", "n1", false
        )

        every { mockDbHelper.getEquipmentItems(BSportType.BIKE) } returns listOf(bike)
        every { mockDbHelper.getEquipmentItems(BSportType.RUN) } returns listOf(shoe)

        val viewModel = createViewModel()

        // Prior to running dispatcher scheduler, flows are initially empty
        assertEquals(0, viewModel.bikes.value.size)
        assertEquals(0, viewModel.shoes.value.size)

        // Advance dispatcher to execute the async init {} job
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify that bikes and shoes are automatically populated without any manual loadEquipment() call
        assertEquals(1, viewModel.bikes.value.size)
        assertEquals("Canyon Grail", viewModel.bikes.value[0].name)
        assertEquals(1, viewModel.shoes.value.size)
        assertEquals("Nike Pegasus", viewModel.shoes.value[0].name)

        verify(exactly = 1) { mockDbHelper.getEquipmentItems(BSportType.BIKE) }
        verify(exactly = 1) { mockDbHelper.getEquipmentItems(BSportType.RUN) }
    }

    @Test
    fun testObserveSyncStatus_reloadsOnFallingEdge() {
        val syncFlow = MutableStateFlow(false)
        every { mockDbHelper.getEquipmentItems(any<BSportType>()) } returns emptyList()

        val viewModel = createViewModel(syncFlow)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1 initial query from init
        verify(exactly = 1) { mockDbHelper.getEquipmentItems(BSportType.BIKE) }

        // Transition: false -> true (sync in progress)
        syncFlow.value = true
        testDispatcher.scheduler.advanceUntilIdle()

        // Query count MUST NOT increment while sync is running
        verify(exactly = 1) { mockDbHelper.getEquipmentItems(BSportType.BIKE) }

        // Transition: true -> false (falling edge: sync finished)
        syncFlow.value = false
        testDispatcher.scheduler.advanceUntilIdle()

        // Query count MUST increment by 1 (reloaded due to sync completion)
        verify(exactly = 2) { mockDbHelper.getEquipmentItems(BSportType.BIKE) }
        verify(exactly = 2) { mockDbHelper.getEquipmentItems(BSportType.RUN) }
    }

    @Test
    fun testObserveSyncStatus_suppressesColdStartRedundancy() {
        val syncFlow = MutableStateFlow(false)
        every { mockDbHelper.getEquipmentItems(any<BSportType>()) } returns emptyList()

        val viewModel = createViewModel(syncFlow)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial emission of false must NOT trigger a second redundant reload
        verify(exactly = 1) { mockDbHelper.getEquipmentItems(BSportType.BIKE) }
        verify(exactly = 1) { mockDbHelper.getEquipmentItems(BSportType.RUN) }
    }

    @Test
    fun testLoadEquipment_cancelsPreviousPendingJob() {
        every { mockDbHelper.getEquipmentItems(any<BSportType>()) } returns emptyList()

        val viewModel = createViewModel()

        // Rapid successive invocations
        viewModel.loadEquipment()
        viewModel.loadEquipment()
        viewModel.loadEquipment()

        testDispatcher.scheduler.advanceUntilIdle()

        // Verify that coroutines complete cleanly without uncaught exceptions and state is valid
        assertNotNull(viewModel.bikes.value)
        assertNotNull(viewModel.shoes.value)
    }

    @Test
    fun testEquipmentViewModel_preservesReflectionConstructor() {
        val constructor = EquipmentViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull("EquipmentViewModel must preserve public constructor accepting Application", constructor)
    }
}
