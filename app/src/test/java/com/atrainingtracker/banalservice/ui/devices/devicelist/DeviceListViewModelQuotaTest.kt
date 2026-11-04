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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import android.app.Application
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.monetization.ProFeatureManager
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit and contract tests for Free-tier quota interception and 1-tap swap flow (TST-SET-083, TST-SET-084).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModelQuotaTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockDeviceRepo = mockk<DeviceDataRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockSettingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val mockProFeatureManager = mockk<ProFeatureManager>(relaxed = true)

    private val allDevicesFlow = MutableStateFlow<List<DeviceUiData>>(emptyList())
    private val newlyFoundIdsFlow = MutableStateFlow<List<Long>>(emptyList())
    private val activeTelemetryFlow = MutableStateFlow<List<com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(DeviceDataRepository.Companion)
        mockkObject(BANALServiceRepository.Companion)
        mockkObject(ProFeatureManager.Companion)

        every { DeviceDataRepository.getInstance(any()) } returns mockDeviceRepo
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo
        every { ProFeatureManager.getInstance(any()) } returns mockProFeatureManager

        every { mockDeviceRepo.allDevices } returns allDevicesFlow
        every { mockBanalRepo.newlyFoundDevicesIds } returns newlyFoundIdsFlow
        every { mockBanalRepo.allActiveDevicesTelemetry } returns activeTelemetryFlow
        every { mockSettingsDataStore.isHrZonesConfigured } returns flowOf(true)
        every { mockSettingsDataStore.isPwrZonesConfigured } returns flowOf(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createUiDevice(
        id: Long,
        deviceType: DeviceType,
        isPaired: Boolean = false
    ): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = deviceType,
            lastSeen = null,
            manufacturer = "Polar",
            deviceName = "Test ${deviceType.name}",
            isPaired = isPaired,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 90,
            linkedSportTypes = emptyList(),
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = false,
            mainValue = null,
            allValues = null,
            isNewlyDiscovered = false
        )
    }

    @Test
    fun testQuotaInterception_emitsSwapOrUpgradeDialogEvent_whenFreeQuotaReached() = runTest(testDispatcher) {
        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS, isPaired = true)
        val pairedBaro = createUiDevice(2L, DeviceType.ALTITUDE_FROM_PRESSURE, isPaired = true)
        val candidateSensor = createUiDevice(3L, DeviceType.HRM, isPaired = false)

        coEvery { mockDeviceRepo.getDeviceSnapshotById(3L) } returns candidateSensor
        coEvery { mockDeviceRepo.getPairedDevices() } returns listOf(pairedGps, pairedBaro)
        every { mockProFeatureManager.isDevicePairingAllowed(2) } returns false

        val viewModel = DeviceListViewModel(
            application = mockApplication,
            settingsDataStore = mockSettingsDataStore,
            devicesDBRepository = mockDeviceRepo,
            banalServiceRepository = mockBanalRepo,
            proFeatureManager = mockProFeatureManager
        )

        // Attempt to pair candidate device 3
        viewModel.onPairedChanged(3L, true)
        advanceUntilIdle()

        // Verify updateDevice was NOT called for candidate
        coVerify(exactly = 0) { mockDeviceRepo.updateDevice(match { it.id == 3L && it.isPaired }) }

        // Verify SwapOrUpgradeDialogEvent was emitted
        val event = viewModel.swapOrUpgradeDialogEvent.value
        assertNotNull("SwapOrUpgradeDialogEvent must be emitted", event)
        assertEquals(3L, event?.candidateDevice?.id)
        assertEquals(2, event?.currentlyPairedDevices?.size)
        // Barometer is recommended to unpair first (outdoor default)
        assertEquals(2L, event?.recommendedUnpairDeviceId)
    }

    @Test
    fun testExecuteSwapAndPair_invokesRepositorySwap_andClearsDialogEvent() = runTest(testDispatcher) {
        val candidate = createUiDevice(3L, DeviceType.HRM, isPaired = false)
        coEvery { mockDeviceRepo.getDeviceSnapshotById(3L) } returns candidate

        val viewModel = DeviceListViewModel(
            application = mockApplication,
            settingsDataStore = mockSettingsDataStore,
            devicesDBRepository = mockDeviceRepo,
            banalServiceRepository = mockBanalRepo,
            proFeatureManager = mockProFeatureManager
        )

        viewModel.executeSwapAndPair(unpairDeviceId = 2L, candidateDeviceId = 3L)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockDeviceRepo.swapPairedDevice(2L, 3L) }
        assertNull("Event must be cleared after swap", viewModel.swapOrUpgradeDialogEvent.value)
    }

    @Test
    fun testDismissSwapOrUpgradeDialog_clearsEvent() = runTest(testDispatcher) {
        val viewModel = DeviceListViewModel(
            application = mockApplication,
            settingsDataStore = mockSettingsDataStore,
            devicesDBRepository = mockDeviceRepo,
            banalServiceRepository = mockBanalRepo,
            proFeatureManager = mockProFeatureManager
        )

        viewModel.dismissSwapOrUpgradeDialog()
        assertNull(viewModel.swapOrUpgradeDialogEvent.value)
    }

    @Test
    fun testProUser_bypassesQuotaInterception() = runTest(testDispatcher) {
        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS, isPaired = true)
        val pairedBaro = createUiDevice(2L, DeviceType.ALTITUDE_FROM_PRESSURE, isPaired = true)
        val candidate = createUiDevice(3L, DeviceType.HRM, isPaired = false)

        coEvery { mockDeviceRepo.getDeviceSnapshotById(3L) } returns candidate
        coEvery { mockDeviceRepo.getPairedDevices() } returns listOf(pairedGps, pairedBaro)
        // Pro user allows pairing beyond 2 devices
        every { mockProFeatureManager.isDevicePairingAllowed(2) } returns true

        val viewModel = DeviceListViewModel(
            application = mockApplication,
            settingsDataStore = mockSettingsDataStore,
            devicesDBRepository = mockDeviceRepo,
            banalServiceRepository = mockBanalRepo,
            proFeatureManager = mockProFeatureManager
        )

        viewModel.onPairedChanged(3L, true)
        advanceUntilIdle()

        // Dialog must NOT be shown
        assertNull("Dialog must not be shown for Pro user", viewModel.swapOrUpgradeDialogEvent.value)
        // Update device must be called
        coVerify(exactly = 1) { mockDeviceRepo.updateDevice(match { it.id == 3L && it.isPaired }) }
    }
}
