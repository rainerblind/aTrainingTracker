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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Structural and behavioral contract test for sensor management pairing relocation (REQ-UI-278, TST-UI-238, ATT-2189):
 * 1. Verification of Pairing FAB in DevicesTabbedScreen.kt.
 * 2. Cascading integration with PairingProtocolBottomSheet and DeviceTypeSelectionDialog.
 * 3. Dynamic filter updating and discovery restart in DevicesTabbedViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicesTabbedScreenContractTest {

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

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testDevicesTabbedScreenDeclaresPairingFabAndDialogs() {
        val file = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt")
        assertTrue("DevicesTabbedScreen.kt must exist", file.exists())

        val content = file.readText()

        // Verify FAB presence and configuration
        assertTrue("DevicesTabbedScreen must contain FloatingActionButton", content.contains("FloatingActionButton"))
        assertTrue("FAB must use Icons.Default.Add", content.contains("Icons.Default.Add"))
        assertTrue("FAB must use R.string.devices_pair_sensor", content.contains("R.string.devices_pair_sensor"))

        // Verify PairingProtocolBottomSheet integration
        assertTrue("DevicesTabbedScreen must integrate PairingProtocolBottomSheet", content.contains("PairingProtocolBottomSheet("))

        // Verify DeviceTypeSelectionDialog integration
        assertTrue("DevicesTabbedScreen must integrate DeviceTypeSelectionDialog", content.contains("DeviceTypeSelectionDialog("))

        // Verify filter update and scroll to available tab
        assertTrue("DevicesTabbedScreen must invoke updateFilters", content.contains("tabViewModel.updateFilters("))
        assertTrue("DevicesTabbedScreen must animate scroll to Tab 0", content.contains("pagerState.animateScrollToPage(0)"))
    }

    @Test
    fun testDevicesTabbedViewModelUpdateFilters_reconfiguresAndRestartsSearch() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = DevicesTabbedViewModel(mockApplication, savedStateHandle)

        // Start initial searching state
        viewModel.startSearching()
        verify(exactly = 1) { mockBanalServiceRepository.startSearchingForNewDevices(Protocol.ALL, DeviceType.ALL) }

        // Trigger filter update for Bluetooth LE Heart Rate
        viewModel.updateFilters(Protocol.BLUETOOTH_LE, DeviceType.HRM)

        assertEquals("Protocol must be updated to BLUETOOTH_LE", Protocol.BLUETOOTH_LE, viewModel.protocol)
        assertEquals("Protocol must be stored in SavedStateHandle", Protocol.BLUETOOTH_LE.name, savedStateHandle.get<String>(BANALService.PROTOCOL))
        assertEquals("DeviceType must be stored in SavedStateHandle", DeviceType.HRM.name, savedStateHandle.get<String>(BANALService.DEVICE_TYPE))

        val uiState = viewModel.uiState.value
        assertTrue("UiState must be DisplayingTabs", uiState is UiState.DisplayingTabs)
        assertEquals("DeviceType in UiState must be HRM", DeviceType.HRM, (uiState as UiState.DisplayingTabs).deviceType)

        // Verify that discovery was restarted with new parameters
        verify(exactly = 1) { mockBanalServiceRepository.stopSearchingForNewDevices() }
        verify(exactly = 1) { mockBanalServiceRepository.startSearchingForNewDevices(Protocol.BLUETOOTH_LE, DeviceType.HRM) }
    }
}
