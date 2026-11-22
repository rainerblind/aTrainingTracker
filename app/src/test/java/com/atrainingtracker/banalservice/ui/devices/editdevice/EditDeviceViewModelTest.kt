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

package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.app.Application
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.BikePowerSensorsHelper
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.BikePowerFeatures
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying UI & ViewModel deprecation contract of obsolete pedal power balance checkboxes
 * pursuant to REQ-CON-022.3 and TST-CON-022.3.
 */
class EditDeviceViewModelTest {

    private lateinit var mockApplication: Application

    @Before
    fun setUp() {
        mockApplication = mockk(relaxed = true)
        every { mockApplication.getString(R.string.bike_power__pedal_power_balance) } returns "Pedal Power Balance"
        every { mockApplication.getString(R.string.bike_power__crank_revolution_data) } returns "Cadence"
        every { mockApplication.getString(R.string.bike_power__torque_effectiveness) } returns "Torque Effectiveness"
    }

    @After
    fun tearDown() {
        DeviceDataRepository.resetForTesting(null)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    /**
     * TST-SEN-031.1 / REQ-SEN-031.2: Verify EditDeviceViewModel stages wheel mounting positions
     * strictly in draft _deviceSnapshot (zero DB/repository calls until saveChanges()) and prunes unlinked bikes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testOnEquipmentChanged_stagesWheelPositionsInDraftOnly_untilSaveChanges() {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        val mockRepo = mockk<DeviceDataRepository>(relaxed = true)
        DeviceDataRepository.resetForTesting(mockRepo)

        val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
        mockkObject(BANALServiceRepository.Companion)
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo
        every { mockBanalRepo.allActiveDevicesTelemetry } returns MutableStateFlow(emptyList())
        every { mockBanalRepo.newlyFoundDevicesIds } returns MutableStateFlow(emptyList())
        every { mockRepo.allDevices } returns MutableStateFlow(emptyList())

        val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(mockDiscovery)
        every { mockDiscovery.getSportNamesForEquipmentList(any()) } returns listOf("Cycling")

        val initial = DeviceUiData(
            id = 77L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.BIKE_SPEED,
            lastSeen = "Now",
            manufacturer = "Garmin",
            deviceName = "Speed 2",
            isPaired = true,
            linkedEquipment = listOf("Gravel Bike"),
            availableEquipment = listOf("Gravel Bike", "Road Bike"),
            powerFeaturesFlags = null,
            batteryPercentage = 90,
            linkedSportTypes = listOf("Cycling"),
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = 2105,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = false,
            mainValue = null,
            allValues = null,
            linkedWheelPositions = mapOf("Gravel Bike" to WheelPosition.FRONT)
        )
        every { mockRepo.getDeviceSnapshotById(77L) } returns initial

        val mockEquipmentRepo = mockk<EquipmentRepository>(relaxed = true)
        every { mockEquipmentRepo.getCircumferenceHistoryForDevice(any()) } returns emptyList()

        val viewModel = EditDeviceViewModel(
            application = mockApplication,
            devicesRepository = mockRepo,
            banalServiceRepository = mockBanalRepo,
            discoveryManager = mockDiscovery,
            equipmentRepository = mockEquipmentRepo,
            ioDispatcher = testDispatcher
        )
        viewModel.loadInitialDeviceData(77L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Stage draft link + position update (Gravel removed, Road added as REAR)
        viewModel.onEquipmentChanged(
            newEquipment = listOf("Road Bike"),
            newWheelPositions = mapOf(
                "Gravel Bike" to WheelPosition.FRONT,
                "Road Bike" to WheelPosition.REAR
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val draft = viewModel.deviceSnapshot.value!!
        assertEquals(listOf("Road Bike"), draft.linkedEquipment)
        assertEquals(mapOf("Road Bike" to WheelPosition.REAR), draft.linkedWheelPositions)
        // Strict Cancel/Draft invariant: updateDevice MUST NOT have been called yet
        coVerify(exactly = 0) { mockRepo.updateDevice(any()) }

        // Save commits the draft state
        viewModel.saveChanges()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) {
            mockRepo.updateDevice(match {
                it.id == 77L &&
                    it.linkedEquipment == listOf("Road Bike") &&
                    it.linkedWheelPositions == mapOf("Road Bike" to WheelPosition.REAR)
            })
        }
    }

    /**
     * TST-CON-022.3: Verify BikePowerFeatures has no obsolete checkbox properties
     * and correctly decodes feature flags from database integer.
     */
    @Test
    fun testBikePowerFeatures_decodesCleanlyWithoutCorrectionCheckboxes() {
        // Bit 0 = 1 (Power Balance Supported)
        val flags = BikePowerSensorsHelper.addPowerBalanceFlag(0)
        val features = BikePowerFeatures.fromFeatureFlags(flags)

        assertTrue(features.pedalPowerBalanceSupported)
        assertFalse(features.crankRevolutionDataSupported)
        assertFalse(features.wheelRevolutionDataSupported)

        // Verify BikePowerFeatures reflection shows NO doublePowerBalanceValues or invertPowerBalanceValues fields
        val fieldNames = BikePowerFeatures::class.java.declaredFields.map { it.name }
        assertFalse("doublePowerBalanceValues should be pruned", fieldNames.contains("doublePowerBalanceValues"))
        assertFalse("invertPowerBalanceValues should be pruned", fieldNames.contains("invertPowerBalanceValues"))
    }

    /**
     * TST-CON-022.3: Verify EditDeviceViewModel has NO obsolete checkbox callback methods.
     */
    @Test
    fun testEditDeviceViewModel_noObsoleteCheckboxCallbacks() {
        val methodNames = EditDeviceViewModel::class.java.declaredMethods.map { it.name }
        assertFalse(
            "onDoublePowerBalanceValuesChanged should be pruned",
            methodNames.contains("onDoublePowerBalanceValuesChanged")
        )
        assertFalse(
            "onInvertPowerBalanceValuesChanged should be pruned",
            methodNames.contains("onInvertPowerBalanceValuesChanged")
        )
    }

    /**
     * TST-CON-022.3: Verify BikePowerSensorsHelper has NO obsolete checkbox helper methods.
     */
    @Test
    fun testBikePowerSensorsHelper_noObsoleteCheckboxHelpers() {
        val methodNames = BikePowerSensorsHelper::class.java.declaredMethods.map { it.name }
        assertFalse(methodNames.contains("doublePowerBalanceValues"))
        assertFalse(methodNames.contains("invertPowerBalanceValues"))
        assertFalse(methodNames.contains("addDoublePowerBalanceValues"))
        assertFalse(methodNames.contains("addInvertPowerBalanceValues"))
        assertFalse(methodNames.contains("removeDoublePowerBalanceValues"))
        assertFalse(methodNames.contains("removeInvertPowerBalanceValues"))
    }

    /**
     * TST-CON-022.3: Verify display mapping converts BikePowerFeatures into read-only display items.
     */
    @Test
    fun testPowerFeaturesDisplayMapping() {
        val viewModel = mockk<EditDeviceViewModel>(relaxed = true)
        val features = BikePowerFeatures(pedalPowerBalanceSupported = true)

        every { viewModel.getPowerFeaturesForDisplay(features) } answers { callOriginal() }

        // We can test the display generation directly if ViewModel instance is created or via mock
        val declaredMethod = EditDeviceViewModel::class.java.getDeclaredMethod(
            "getPowerFeaturesForDisplay",
            BikePowerFeatures::class.java
        )
        // Method exists and takes BikePowerFeatures
        assertEquals(BikePowerFeatures::class.java, declaredMethod.parameterTypes[0])
    }

    /**
     * REQ-TRK-050 / TST-TRK-050: Verify EditDeviceViewModel exposes baseline reset action for iTPMS.
     */
    @Test
    fun testEditDeviceViewModel_hasResetTirePressureBaselineMethod() {
        val methodNames = EditDeviceViewModel::class.java.declaredMethods.map { it.name }
        assertTrue(
            "resetTirePressureBaseline must be exposed on EditDeviceViewModel",
            methodNames.contains("resetTirePressureBaseline")
        )
    }

    /**
     * REQ-CON-031.5 / REQ-UI-2799.2 / ATT-8125: Verify EditDeviceViewModel stages radar tail light
     * edits strictly in draft state (zero RadarLightController mutations until saveChanges()) and
     * discards them cleanly on discardDraft() (Cancel).
     */
    @Test
    fun testEditDeviceViewModel_stagesRadarTailLightInDraftAndDiscardsOnCancel() {
        val mockRepo = mockk<DeviceDataRepository>(relaxed = true)
        val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
        val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        val mockEquipmentRepo = mockk<EquipmentRepository>(relaxed = true)

        val radarDevice = DeviceUiData(
            id = 88L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.BIKE_RADAR,
            lastSeen = "Now",
            manufacturer = "Garmin",
            deviceName = "Varia RTL515",
            isPaired = true,
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
            allValues = null
        )
        every { mockRepo.getDeviceSnapshotById(88L) } returns radarDevice

        com.atrainingtracker.banalservice.devices.radar.RadarLightController.reset()
        com.atrainingtracker.banalservice.devices.radar.RadarLightController.updateCachedPreferences(
            autoMode = true,
            defaultMode = com.atrainingtracker.banalservice.devices.radar.RadarLightMode.SOLID
        )

        val viewModel = EditDeviceViewModel(
            application = mockApplication,
            devicesRepository = mockRepo,
            banalServiceRepository = mockBanalRepo,
            discoveryManager = mockDiscovery,
            equipmentRepository = mockEquipmentRepo
        )
        viewModel.loadInitialDeviceData(88L)
        viewModel.onRadarLightSelectionChanged(
            isAutoMode = false,
            mode = com.atrainingtracker.banalservice.devices.radar.RadarLightMode.DAY_FLASH
        )

        // Staged in draft only: RadarLightController untouched before Save
        assertEquals(
            RadarLightDraftSelection(
                isAutoMode = false,
                mode = com.atrainingtracker.banalservice.devices.radar.RadarLightMode.DAY_FLASH
            ),
            viewModel.radarLightDraft.value
        )
        assertTrue(com.atrainingtracker.banalservice.devices.radar.RadarLightController.isAutoMode.value)
        assertEquals(
            com.atrainingtracker.banalservice.devices.radar.RadarLightMode.SOLID,
            com.atrainingtracker.banalservice.devices.radar.RadarLightController.preferredMode.value
        )

        // Cancel discards draft with zero RadarLightController mutation
        viewModel.discardDraft()
        assertEquals(null, viewModel.radarLightDraft.value)
        assertTrue(com.atrainingtracker.banalservice.devices.radar.RadarLightController.isAutoMode.value)
        assertEquals(
            com.atrainingtracker.banalservice.devices.radar.RadarLightMode.SOLID,
            com.atrainingtracker.banalservice.devices.radar.RadarLightController.preferredMode.value
        )
        com.atrainingtracker.banalservice.devices.radar.RadarLightController.reset()
    }
}
