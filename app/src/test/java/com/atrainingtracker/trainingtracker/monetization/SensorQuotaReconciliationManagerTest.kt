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
        protocol: Protocol = if (deviceType == DeviceType.SPEED_AND_LOCATION_GPS ||
            deviceType == DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED ||
            deviceType == DeviceType.ALTITUDE_FROM_PRESSURE ||
            deviceType == DeviceType.ALTITUDE_FROM_POSITION
        ) Protocol.SMARTPHONE else Protocol.BLUETOOTH_LE,
        isPaired: Boolean = true,
        isConnected: Boolean = false,
        lastSeen: String? = null
    ): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = protocol,
            deviceType = deviceType,
            lastSeen = lastSeen,
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
            isConnected = isConnected,
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
        // Defaults must be GPS (Slot 1) + first remote sensor HRM (Slot 2)
        assertEquals(setOf(1L, 3L), event?.defaultKeepDeviceIds)
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

        coVerify(exactly = 1) { mockDeviceRepo.reconcilePairedDevices(setOf(1L, 3L)) }
        assertNull("Event must be cleared after dismissal", manager.keepTwoSensorsDialogEvent.value)
    }

    @Test
    fun testDetermineDefaultKeepDeviceIds_bothGpsAndFusedPaired_preselectsFusedAndActiveRemote() {
        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { false }
        )

        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS)
        val pairedFused = createUiDevice(2L, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val pairedHrm = createUiDevice(3L, DeviceType.HRM, isConnected = false, lastSeen = "2026-11-06 10:00:00")
        val pairedPwr = createUiDevice(4L, DeviceType.BIKE_POWER, isConnected = true, lastSeen = "2026-11-06 09:00:00")

        val result = manager.determineDefaultKeepDeviceIds(listOf(pairedGps, pairedFused, pairedHrm, pairedPwr))

        // Slot 1: Google Fused (2L) preferred over GPS (1L)
        // Slot 2: Connected BIKE_POWER (4L) preferred over disconnected HRM (3L)
        assertEquals(setOf(2L, 4L), result)
    }

    @Test
    fun testDetermineDefaultKeepDeviceIds_remoteSensorsSortedByLastSeenTimestamp() {
        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { false }
        )

        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS)
        val pairedHrm = createUiDevice(3L, DeviceType.HRM, isConnected = false, lastSeen = "2026-11-06 12:00:00")
        val pairedPwr = createUiDevice(4L, DeviceType.BIKE_POWER, isConnected = false, lastSeen = "2026-11-06 08:00:00")

        val result = manager.determineDefaultKeepDeviceIds(listOf(pairedGps, pairedPwr, pairedHrm))

        // Slot 1: GPS (1L)
        // Slot 2: HRM (3L) with newer lastSeen timestamp over Power (4L)
        assertEquals(setOf(1L, 3L), result)
    }

    @Test
    fun testDetermineDefaultKeepDeviceIds_onlyFusedPaired_preservesFusedAndActiveRemote() {
        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { false }
        )

        val pairedFused = createUiDevice(2L, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val pairedHrm = createUiDevice(3L, DeviceType.HRM)
        val pairedBaro = createUiDevice(5L, DeviceType.ALTITUDE_FROM_PRESSURE)

        val result = manager.determineDefaultKeepDeviceIds(listOf(pairedFused, pairedHrm, pairedBaro))

        // Slot 1: Google Fused (2L)
        // Slot 2: Remote HRM (3L)
        assertEquals(setOf(2L, 3L), result)
    }

    @Test
    fun testDetermineDefaultKeepDeviceIds_noRemoteSensors_fallsBackToBarometer() {
        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { false }
        )

        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS)
        val pairedFused = createUiDevice(2L, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val pairedBaro = createUiDevice(3L, DeviceType.ALTITUDE_FROM_PRESSURE)

        val result = manager.determineDefaultKeepDeviceIds(listOf(pairedGps, pairedFused, pairedBaro))

        // Slot 1: Google Fused (2L)
        // Slot 2: Barometer fallback (3L)
        assertEquals(setOf(2L, 3L), result)
    }

    @Test
    fun testDetermineRecommendedUnpairDeviceId_unpairHierarchy() {
        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { false }
        )

        val pairedGps = createUiDevice(1L, DeviceType.SPEED_AND_LOCATION_GPS)
        val pairedFused = createUiDevice(2L, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val pairedBaro = createUiDevice(3L, DeviceType.ALTITUDE_FROM_PRESSURE)
        val pairedHrm = createUiDevice(4L, DeviceType.HRM)

        // 1. Barometer present -> recommend unpairing Barometer first
        assertEquals(3L, manager.determineRecommendedUnpairDeviceId(listOf(pairedGps, pairedFused, pairedBaro, pairedHrm)))

        // 2. Both Fused and GPS present without Barometer -> recommend unpairing GPS next
        assertEquals(1L, manager.determineRecommendedUnpairDeviceId(listOf(pairedGps, pairedFused, pairedHrm)))

        // 3. Fused + Remote present -> NEVER recommend Fused; recommend remote sensor (REQ-SET-084.2, ATT-5657)
        assertEquals(4L, manager.determineRecommendedUnpairDeviceId(listOf(pairedFused, pairedHrm)))

        // 4. Sole GPS + Remote present -> NEVER recommend GPS; recommend remote sensor (ATT-5657)
        assertEquals(4L, manager.determineRecommendedUnpairDeviceId(listOf(pairedGps, pairedHrm)))
    }

    @Test
    fun testDetermineRecommendedUnpairDeviceId_remoteSensorPrioritization() {
        val manager = SensorQuotaReconciliationManager(
            context = mockContext,
            devicesDBRepository = mockDeviceRepo,
            proFeatureManager = mockProFeatureManager,
            isTrackingProvider = { false }
        )

        val pairedFused = createUiDevice(2L, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val connectedHrm = createUiDevice(3L, DeviceType.HRM, isConnected = true, lastSeen = "2026-11-06 10:00:00")
        val disconnectedPwr = createUiDevice(4L, DeviceType.BIKE_POWER, isConnected = false, lastSeen = "2026-11-06 11:00:00")

        // Disconnected sensor preferred over connected sensor for unpairing
        assertEquals(4L, manager.determineRecommendedUnpairDeviceId(listOf(pairedFused, connectedHrm, disconnectedPwr)))

        // Between disconnected sensors, oldest lastSeen timestamp preferred for unpairing
        val olderCad = createUiDevice(5L, DeviceType.BIKE_CADENCE, isConnected = false, lastSeen = "2026-11-06 08:00:00")
        assertEquals(5L, manager.determineRecommendedUnpairDeviceId(listOf(pairedFused, disconnectedPwr, olderCad)))

        // When only location sensor present, fallback to location sensor
        assertEquals(2L, manager.determineRecommendedUnpairDeviceId(listOf(pairedFused)))
    }
}
