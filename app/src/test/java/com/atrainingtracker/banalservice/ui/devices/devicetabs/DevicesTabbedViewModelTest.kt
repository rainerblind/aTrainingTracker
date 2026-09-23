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

package com.atrainingtracker.banalservice.ui.devices.devicetabs

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
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
 * Unit tests verifying DevicesTabbedViewModel handles missing or malformed SavedStateHandle
 * parameters with robust defaults (Protocol.ALL, DeviceType.ALL) and crash immunity (REQ-STB-011, TST-STB-011, ATT-1308).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicesTabbedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockApplication: Application
    private lateinit var mockDeviceDataRepository: DeviceDataRepository
    private lateinit var mockBanalServiceRepository: BANALServiceRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockApplication = mockk(relaxed = true)
        every { mockApplication.registerReceiver(any(), any(), any()) } returns null
        every { mockApplication.registerReceiver(any(), any()) } returns null

        mockDeviceDataRepository = mockk(relaxed = true)
        mockBanalServiceRepository = mockk(relaxed = true)

        mockkObject(DeviceDataRepository.Companion)
        every { DeviceDataRepository.getInstance(any()) } returns mockDeviceDataRepository

        mockkObject(BANALServiceRepository.Companion)
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalServiceRepository
        every { mockBanalServiceRepository.searchingForDevice } returns MutableStateFlow<String?>(null)
        every { mockBanalServiceRepository.isSearchingForNewDevices } returns MutableStateFlow<Boolean>(false)
        every { mockBanalServiceRepository.bindToBANALService() } just Runs
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testInit_withEmptySavedStateHandle_defaultsToProtocolAllAndTabsAll() {
        val savedStateHandle = SavedStateHandle()

        val viewModel = DevicesTabbedViewModel(mockApplication, savedStateHandle)

        assertEquals("Protocol must default to ALL when SavedStateHandle is empty", Protocol.ALL, viewModel.protocol)
        assertEquals("Protocol must be written back to SavedStateHandle", Protocol.ALL.name, savedStateHandle.get<String>(BANALService.PROTOCOL))
        assertEquals("DeviceType must be written back to SavedStateHandle", DeviceType.ALL.name, savedStateHandle.get<String>(BANALService.DEVICE_TYPE))

        val state = viewModel.uiState.value
        assertTrue("UiState must resolve to DisplayingTabs", state is UiState.DisplayingTabs)
        assertEquals("DeviceType in DisplayingTabs must be ALL", DeviceType.ALL, (state as UiState.DisplayingTabs).deviceType)
    }

    @Test
    fun testInit_withMalformedStringTokens_fallsBackSafely() {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                BANALService.PROTOCOL to "NON_EXISTENT_PROTOCOL_123",
                BANALService.DEVICE_TYPE to "NON_EXISTENT_DEVICE_TYPE_456"
            )
        )

        val viewModel = DevicesTabbedViewModel(mockApplication, savedStateHandle)

        assertEquals("Protocol must fall back safely to ALL", Protocol.ALL, viewModel.protocol)
        val state = viewModel.uiState.value
        assertTrue("UiState must resolve to DisplayingTabs", state is UiState.DisplayingTabs)
        assertEquals("DeviceType must fall back safely to ALL", DeviceType.ALL, (state as UiState.DisplayingTabs).deviceType)
    }

    @Test
    fun testInit_withExplicitValidParameters_preservesGivenValues() {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                BANALService.PROTOCOL to Protocol.ANT_PLUS.name,
                BANALService.DEVICE_TYPE to DeviceType.HRM.name
            )
        )

        val viewModel = DevicesTabbedViewModel(mockApplication, savedStateHandle)

        assertEquals("Protocol must match the provided ANT_PLUS", Protocol.ANT_PLUS, viewModel.protocol)
        val state = viewModel.uiState.value
        assertTrue("UiState must resolve to DisplayingTabs", state is UiState.DisplayingTabs)
        assertEquals("DeviceType must match HRM", DeviceType.HRM, (state as UiState.DisplayingTabs).deviceType)
    }

    @Test
    fun testUpdateFilters_updatesSavedStateAndEmitsNewTabsState() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = DevicesTabbedViewModel(mockApplication, savedStateHandle)

        viewModel.updateFilters(Protocol.BLUETOOTH_LE, DeviceType.BIKE_POWER)

        assertEquals("Protocol must update to BLUETOOTH_LE", Protocol.BLUETOOTH_LE, viewModel.protocol)
        assertEquals("SavedStateHandle PROTOCOL must update to BLUETOOTH_LE", Protocol.BLUETOOTH_LE.name, savedStateHandle.get<String>(BANALService.PROTOCOL))
        assertEquals("SavedStateHandle DEVICE_TYPE must update to BIKE_POWER", DeviceType.BIKE_POWER.name, savedStateHandle.get<String>(BANALService.DEVICE_TYPE))

        val state = viewModel.uiState.value
        assertTrue("UiState must resolve to DisplayingTabs", state is UiState.DisplayingTabs)
        assertEquals("DeviceType in DisplayingTabs must be BIKE_POWER", DeviceType.BIKE_POWER, (state as UiState.DisplayingTabs).deviceType)
    }

    @Test
    fun testUpdateFilters_withNullDeviceType_defaultsToDeviceTypeAll() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = DevicesTabbedViewModel(mockApplication, savedStateHandle)

        viewModel.updateFilters(Protocol.ANT_PLUS, null)

        assertEquals("Protocol must update to ANT_PLUS", Protocol.ANT_PLUS, viewModel.protocol)
        assertEquals("SavedStateHandle DEVICE_TYPE must default to ALL when null", DeviceType.ALL.name, savedStateHandle.get<String>(BANALService.DEVICE_TYPE))

        val state = viewModel.uiState.value
        assertTrue("UiState must resolve to DisplayingTabs", state is UiState.DisplayingTabs)
        assertEquals("DeviceType in DisplayingTabs must be ALL", DeviceType.ALL, (state as UiState.DisplayingTabs).deviceType)
    }
}
