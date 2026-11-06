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

package com.atrainingtracker.trainingtracker.monetization

import android.content.Context
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SensorQuotaReconciliationManager] verifying downgrade reconciliation,
 * sensible default preselection, active tracking suppression, and confirmation/dismissal pruning
 * (REQ-SET-084.5, TST-SET-084.9).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SensorQuotaReconciliationManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockDeviceRepo = mockk<DeviceDataRepository>(relaxed = true)
    private val mockProFeatureManager = mockk<ProFeatureManager>(relaxed = true)

    private val isProActiveFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockProFeatureManager.isProActive } returns isProActiveFlow
        SensorQuotaReconciliationManager.setInstanceForTesting(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        SensorQuotaReconciliationManager.setInstanceForTesting(null)
    }

    private fun createUiDevice(
        id: Long,
        deviceType: DeviceType,
        isPaired: Boolean = true
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
    fun testCheckDowngradeReconciliation_triggers_whenFreeTierAndPairedDevicesGreaterThanTwo() = runTest(testDispatcher) {
        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS)
        val pairedBaro = createUiDevice(2L, DeviceType.ALTITUDE_FROM_PRESSURE)
        val pairedHrm = createUiDevice(3L, DeviceType.HRM)
        val pairedPwr = createUiDevice(4L, DeviceType.BIKE_POWER)

        coEvery { mockDeviceRepo.getPairedDevices() } returns listOf(pairedGps, pairedBaro, pairedHrm, pairedPwr)
        isProActiveFlow.value = false

        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { false }
        )

        val triggered = manager.checkDowngradeReconciliation()
        assertTrue("Reconciliation must trigger when paired > 2 on Free tier", triggered)

        val event = manager.keepTwoSensorsDialogEvent.value
        assertNotNull(event)
        assertEquals(4, event?.currentlyPairedDevices?.size)
        assertEquals(setOf(1L, 2L), event?.defaultKeepDeviceIds)
    }

    @Test
    fun testCheckDowngradeReconciliation_suppressed_duringActiveTracking() = runTest(testDispatcher) {
        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS)
        val pairedBaro = createUiDevice(2L, DeviceType.ALTITUDE_FROM_PRESSURE)
        val pairedHrm = createUiDevice(3L, DeviceType.HRM)

        coEvery { mockDeviceRepo.getPairedDevices() } returns listOf(pairedGps, pairedBaro, pairedHrm)
        isProActiveFlow.value = false

        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { true }
        )

        val triggered = manager.checkDowngradeReconciliation()
        assertFalse("Reconciliation MUST NOT trigger during active workout tracking", triggered)
        assertNull(manager.keepTwoSensorsDialogEvent.value)
    }

    @Test
    fun testCheckDowngradeReconciliation_doesNotTrigger_whenProTierActive() = runTest(testDispatcher) {
        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS)
        val pairedBaro = createUiDevice(2L, DeviceType.ALTITUDE_FROM_PRESSURE)
        val pairedHrm = createUiDevice(3L, DeviceType.HRM)

        coEvery { mockDeviceRepo.getPairedDevices() } returns listOf(pairedGps, pairedBaro, pairedHrm)
        isProActiveFlow.value = true

        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { false }
        )

        val triggered = manager.checkDowngradeReconciliation()
        assertFalse("Reconciliation must not trigger when athlete is Pro active", triggered)
        assertNull(manager.keepTwoSensorsDialogEvent.value)
    }

    @Test
    fun testCheckDowngradeReconciliation_doesNotTrigger_whenPairedDevicesCountLessThanOrEqualTwo() = runTest(testDispatcher) {
        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS)
        val pairedBaro = createUiDevice(2L, DeviceType.ALTITUDE_FROM_PRESSURE)

        coEvery { mockDeviceRepo.getPairedDevices() } returns listOf(pairedGps, pairedBaro)
        isProActiveFlow.value = false

        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { false }
        )

        val triggered = manager.checkDowngradeReconciliation()
        assertFalse("Reconciliation must not trigger when paired devices <= 2", triggered)
        assertNull(manager.keepTwoSensorsDialogEvent.value)
    }

    @Test
    fun testConfirmKeepTwoSensors_reconcilesSelectedIds_andClearsEvent() = runTest(testDispatcher) {
        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS)
        val pairedBaro = createUiDevice(2L, DeviceType.ALTITUDE_FROM_PRESSURE)
        val pairedHrm = createUiDevice(3L, DeviceType.HRM)

        coEvery { mockDeviceRepo.getPairedDevices() } returns listOf(pairedGps, pairedBaro, pairedHrm)
        isProActiveFlow.value = false

        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { false }
        )

        manager.checkDowngradeReconciliation()
        assertNotNull(manager.keepTwoSensorsDialogEvent.value)

        manager.confirmKeepTwoSensors(setOf(1L, 3L))
        advanceUntilIdle()

        coVerify(exactly = 1) { mockDeviceRepo.reconcilePairedDevices(setOf(1L, 3L)) }
        assertNull("Event must be cleared after confirmation", manager.keepTwoSensorsDialogEvent.value)
    }

    @Test
    fun testDismissKeepTwoSensors_reconcilesDefaultIds_toPreventBypass() = runTest(testDispatcher) {
        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS)
        val pairedBaro = createUiDevice(2L, DeviceType.ALTITUDE_FROM_PRESSURE)
        val pairedHrm = createUiDevice(3L, DeviceType.HRM)

        coEvery { mockDeviceRepo.getPairedDevices() } returns listOf(pairedGps, pairedBaro, pairedHrm)
        isProActiveFlow.value = false

        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { false }
        )

        manager.checkDowngradeReconciliation()
        assertNotNull(manager.keepTwoSensorsDialogEvent.value)

        manager.dismissKeepTwoSensors()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockDeviceRepo.reconcilePairedDevices(setOf(1L, 2L)) }
        assertNull("Event must be cleared after dismissal", manager.keepTwoSensorsDialogEvent.value)
    }
}
