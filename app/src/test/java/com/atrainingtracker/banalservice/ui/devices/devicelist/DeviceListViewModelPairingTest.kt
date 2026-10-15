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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TST-UI-337.2] verifying sensor pairing interception, discrimination,
 * and contextual zone onboarding event emission in [DeviceListViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModelPairingTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockDeviceRepo = mockk<DeviceDataRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockSettingsDataStore = mockk<SettingsDataStore>(relaxed = true)

    private val allDevicesFlow = MutableStateFlow<List<DeviceUiData>>(emptyList())
    private val newlyFoundIdsFlow = MutableStateFlow<List<Long>>(emptyList())
    private val activeTelemetryFlow = MutableStateFlow<List<com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(DeviceDataRepository.Companion)
        mockkObject(BANALServiceRepository.Companion)

        every { DeviceDataRepository.getInstance(any()) } returns mockDeviceRepo
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo

        every { mockDeviceRepo.allDevices } returns allDevicesFlow
        every { mockBanalRepo.newlyFoundDevicesIds } returns newlyFoundIdsFlow
        every { mockBanalRepo.allActiveDevicesTelemetry } returns activeTelemetryFlow
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
    fun `pairing unconfigured HRM emits zone onboarding event with tab 0 and hr_paired reason`() = runTest(testDispatcher) {
        val deviceId = 101L
        val device = createUiDevice(deviceId, DeviceType.HRM, isPaired = false)
        every { mockDeviceRepo.getDeviceSnapshotById(deviceId) } returns device
        every { mockSettingsDataStore.isHrZonesConfigured } returns flowOf(false)

        val viewModel = DeviceListViewModel(
            application = mockApplication,
            settingsDataStore = mockSettingsDataStore,
            devicesDBRepository = mockDeviceRepo,
            banalServiceRepository = mockBanalRepo
        )

        val events = mutableListOf<ZoneOnboardingEvent>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.zoneNavigationEvent.toList(events)
        }

        viewModel.onPairedChanged(deviceId, true)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(0, events[0].initialTab)
        assertEquals(DeviceListViewModel.PROMPT_REASON_HR_PAIRED, events[0].promptReason)
        coVerify { mockDeviceRepo.updateDevice(match { it.id == deviceId && it.isPaired }) }

        job.cancel()
    }

    @Test
    fun `pairing configured HRM does not emit zone onboarding event`() = runTest(testDispatcher) {
        val deviceId = 102L
        val device = createUiDevice(deviceId, DeviceType.HRM, isPaired = false)
        every { mockDeviceRepo.getDeviceSnapshotById(deviceId) } returns device
        every { mockSettingsDataStore.isHrZonesConfigured } returns flowOf(true)

        val viewModel = DeviceListViewModel(
            application = mockApplication,
            settingsDataStore = mockSettingsDataStore,
            devicesDBRepository = mockDeviceRepo,
            banalServiceRepository = mockBanalRepo
        )

        val events = mutableListOf<ZoneOnboardingEvent>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.zoneNavigationEvent.toList(events)
        }

        viewModel.onPairedChanged(deviceId, true)
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        coVerify { mockDeviceRepo.updateDevice(match { it.id == deviceId && it.isPaired }) }

        job.cancel()
    }

    @Test
    fun `pairing unconfigured BIKE_POWER emits zone onboarding event with tab 2 and pwr_paired reason`() = runTest(testDispatcher) {
        val deviceId = 201L
        val device = createUiDevice(deviceId, DeviceType.BIKE_POWER, isPaired = false)
        every { mockDeviceRepo.getDeviceSnapshotById(deviceId) } returns device
        every { mockSettingsDataStore.isPwrZonesConfigured } returns flowOf(false)

        val viewModel = DeviceListViewModel(
            application = mockApplication,
            settingsDataStore = mockSettingsDataStore,
            devicesDBRepository = mockDeviceRepo,
            banalServiceRepository = mockBanalRepo
        )

        val events = mutableListOf<ZoneOnboardingEvent>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.zoneNavigationEvent.toList(events)
        }

        viewModel.onPairedChanged(deviceId, true)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(2, events[0].initialTab)
        assertEquals(DeviceListViewModel.PROMPT_REASON_PWR_PAIRED, events[0].promptReason)
        coVerify { mockDeviceRepo.updateDevice(match { it.id == deviceId && it.isPaired }) }

        job.cancel()
    }

    @Test
    fun `pairing configured BIKE_POWER does not emit zone onboarding event`() = runTest(testDispatcher) {
        val deviceId = 202L
        val device = createUiDevice(deviceId, DeviceType.BIKE_POWER, isPaired = false)
        every { mockDeviceRepo.getDeviceSnapshotById(deviceId) } returns device
        every { mockSettingsDataStore.isPwrZonesConfigured } returns flowOf(true)

        val viewModel = DeviceListViewModel(
            application = mockApplication,
            settingsDataStore = mockSettingsDataStore,
            devicesDBRepository = mockDeviceRepo,
            banalServiceRepository = mockBanalRepo
        )

        val events = mutableListOf<ZoneOnboardingEvent>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.zoneNavigationEvent.toList(events)
        }

        viewModel.onPairedChanged(deviceId, true)
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        coVerify { mockDeviceRepo.updateDevice(match { it.id == deviceId && it.isPaired }) }

        job.cancel()
    }

    @Test
    fun `pairing non-physiological sensors completes silently without emitting zone event`() = runTest(testDispatcher) {
        val nonPhysiologicalTypes = listOf(
            DeviceType.BIKE_SPEED,
            DeviceType.BIKE_CADENCE,
            DeviceType.BIKE_SPEED_AND_CADENCE,
            DeviceType.RUN_SPEED,
            DeviceType.ENVIRONMENT,
            DeviceType.ALTITUDE_FROM_PRESSURE
        )

        for ((index, type) in nonPhysiologicalTypes.withIndex()) {
            val deviceId = 300L + index
            val device = createUiDevice(deviceId, type, isPaired = false)
            every { mockDeviceRepo.getDeviceSnapshotById(deviceId) } returns device
            every { mockSettingsDataStore.isHrZonesConfigured } returns flowOf(false)
            every { mockSettingsDataStore.isPwrZonesConfigured } returns flowOf(false)

            val viewModel = DeviceListViewModel(
                application = mockApplication,
                settingsDataStore = mockSettingsDataStore,
                devicesDBRepository = mockDeviceRepo,
                banalServiceRepository = mockBanalRepo
            )

            val events = mutableListOf<ZoneOnboardingEvent>()
            val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
                viewModel.zoneNavigationEvent.toList(events)
            }

            viewModel.onPairedChanged(deviceId, true)
            advanceUntilIdle()

            assertTrue("Expected no event for $type", events.isEmpty())
            job.cancel()
        }
    }

    @Test
    fun `unpairing an already paired device does not emit zone onboarding event`() = runTest(testDispatcher) {
        val deviceId = 401L
        val device = createUiDevice(deviceId, DeviceType.HRM, isPaired = true)
        every { mockDeviceRepo.getDeviceSnapshotById(deviceId) } returns device
        every { mockSettingsDataStore.isHrZonesConfigured } returns flowOf(false)

        val viewModel = DeviceListViewModel(
            application = mockApplication,
            settingsDataStore = mockSettingsDataStore,
            devicesDBRepository = mockDeviceRepo,
            banalServiceRepository = mockBanalRepo
        )

        val events = mutableListOf<ZoneOnboardingEvent>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.zoneNavigationEvent.toList(events)
        }

        // Toggling from paired to unpaired
        viewModel.onPairedChanged(deviceId, false)
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        coVerify { mockDeviceRepo.updateDevice(match { it.id == deviceId && !it.isPaired }) }

        job.cancel()
    }
}
