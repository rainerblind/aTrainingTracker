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
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.datastore.preferences.core.preferencesOf
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.settings.InMemoryPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Targeted Compose UI & ViewModel verification suite for Post-Workout ERTS smoothing integration
 * inside [EditDeviceDialog] for ATT Fused sensors (`REQ-FIL-017.5`, `REQ-UI-2799.2`, `TST-FIL-017.5`, `ATT-8113`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class EditDeviceDialogErtsSmoothingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var storeScope: CoroutineScope

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        unmockkAll()
    }

    private fun createInMemoryTuningStore(initialErtsEnabled: Boolean = false): TuningPreferencesDataStore {
        val backing = InMemoryPreferencesDataStore(
            preferencesOf(TuningPreferencesDataStore.KEY_POST_WORKOUT_ERTS_SMOOTHING to initialErtsEnabled)
        )
        return TuningPreferencesDataStore.createForTesting(
            context = app,
            testScope = storeScope,
            dataStoreOverride = backing
        )
    }

    private fun createViewModel(
        devicesById: Map<Long, DeviceUiData>,
        tuningStore: TuningPreferencesDataStore
    ): EditDeviceViewModel {
        val devicesRepo = mockk<DeviceDataRepository>(relaxed = true)
        val banalRepo = mockk<BANALServiceRepository>(relaxed = true)
        val discoveryMgr = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        val equipRepo = mockk<EquipmentRepository>(relaxed = true)
        devicesById.forEach { (id, data) ->
            every { devicesRepo.getDeviceSnapshotById(id) } returns data
        }
        every { equipRepo.getCircumferenceHistoryForDevice(any()) } returns emptyList()
        return EditDeviceViewModel(
            application = app,
            devicesRepository = devicesRepo,
            banalServiceRepository = banalRepo,
            discoveryManager = discoveryMgr,
            equipmentRepository = equipRepo,
            ioDispatcher = UnconfinedTestDispatcher(),
            tuningPreferencesDataStore = tuningStore
        )
    }

    @Test
    fun testShouldShowPostWorkoutErtsToggle_exclusivelyForBothAttFusedSensors_tstFil017_5() {
        val expectedEnabled = setOf(
            DeviceType.SPEED_AND_LOCATION_ATT_FUSED,
            DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED
        )
        for (deviceType in DeviceType.values()) {
            val actual = shouldShowPostWorkoutErtsToggle(deviceType)
            if (deviceType in expectedEnabled) {
                assertTrue("ERTS switch must be enabled for $deviceType", actual)
            } else {
                assertFalse("ERTS switch must be excluded for non-Fused device $deviceType", actual)
            }
        }
    }

    @Test
    fun testEditDeviceDialog_odometryAttFusedRendersErtsToggleAndPersistsOnSave_tstFil017_5() {
        val tuningStore = createInMemoryTuningStore(initialErtsEnabled = false)
        val odometryDevice = sampleDevice(
            id = 101L,
            type = DeviceType.SPEED_AND_LOCATION_ATT_FUSED,
            name = "ATT Fused (Position & Speed)"
        )
        val vm = createViewModel(mapOf(101L to odometryDevice), tuningStore)
        var dismissed = false

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    EditDeviceDialog(
                        deviceId = 101L,
                        onDismiss = { dismissed = true },
                        viewModel = vm
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_card")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(app.getString(R.string.tuning_post_workout_erts_smoothing_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()

        // Toggle ON -> staged in draft, NOT yet persisted prior to Save (REQ-UI-2799.2)
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_switch")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_switch").assertIsOn()
        assertFalse(
            "DataStore must remain untouched before Save",
            runBlocking { tuningStore.postWorkoutErtsSmoothingFlow.first() }
        )
        assertFalse(
            "Cached TuningConfig must remain untouched before Save",
            tuningStore.getTuningConfig().postWorkoutErtsSmoothingEnabled
        )

        // Tap Save -> persists immediately to KEY_POST_WORKOUT_ERTS_SMOOTHING & syncs TuningConfig cache
        composeTestRule.onNodeWithText(app.getString(R.string.save))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
        assertTrue(runBlocking { tuningStore.postWorkoutErtsSmoothingFlow.first() })
        assertTrue(tuningStore.getTuningConfig().postWorkoutErtsSmoothingEnabled)
        assertTrue(runBlocking { tuningStore.tuningConfigFlow.first().postWorkoutErtsSmoothingEnabled })
    }

    @Test
    fun testEditDeviceDialog_altitudeAttFusedHydratesPersistedStateAndTogglesOff_tstFil017_5() {
        val tuningStore = createInMemoryTuningStore(initialErtsEnabled = true)
        val altitudeDevice = sampleDevice(
            id = 102L,
            type = DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED,
            name = "ATT Fused (Altitude & Slope)"
        )
        val vm = createViewModel(mapOf(102L to altitudeDevice), tuningStore)

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = true) {
                Surface {
                    EditDeviceDialog(
                        deviceId = 102L,
                        onDismiss = {},
                        viewModel = vm
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_card")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOn()

        // Toggle OFF and Save -> immediately synced back to DataStore
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_switch")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_switch").assertIsOff()
        composeTestRule.onNodeWithText(app.getString(R.string.save))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        assertFalse(runBlocking { tuningStore.postWorkoutErtsSmoothingFlow.first() })
        assertFalse(tuningStore.getTuningConfig().postWorkoutErtsSmoothingEnabled)
        assertFalse(runBlocking { tuningStore.tuningConfigFlow.first().postWorkoutErtsSmoothingEnabled })
    }

    @Test
    fun testPostWorkoutErtsSmoothingCard_pointerClickTogglesSwitchAndShowsLocalizedText_tstFil017_5() {
        var checked by mutableStateOf(false)
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    PostWorkoutErtsSmoothingCard(
                        checked = checked,
                        onCheckedChange = { checked = it }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_card").assertIsDisplayed()
        composeTestRule.onNodeWithText(app.getString(R.string.tuning_post_workout_erts_smoothing_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(app.getString(R.string.tuning_post_workout_erts_smoothing_desc))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_switch")
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        composeTestRule.waitForIdle()
        assertTrue(checked)
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_switch")
            .assertIsOn()
            .performClick()
        composeTestRule.waitForIdle()
        assertFalse(checked)
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_switch").assertIsOff()
    }

    @Test
    fun testEditDeviceDialog_excludesErtsSwitchForNonFusedSensors_tstFil017_5() {
        val tuningStore = createInMemoryTuningStore(initialErtsEnabled = true)
        val gpsDevice = sampleDevice(id = 201L, type = DeviceType.SPEED_AND_LOCATION_GPS, name = "GPS")
        val vm = createViewModel(mapOf(201L to gpsDevice), tuningStore)

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    EditDeviceDialog(
                        deviceId = 201L,
                        onDismiss = {},
                        viewModel = vm
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("edit_device_post_workout_erts_switch").assertDoesNotExist()
    }

    @Test
    fun testBidirectionalSync_betweenBothFusedSensorsAndAdvancedTuningConfig_tstFil017_5() = runBlocking {
        val tuningStore = createInMemoryTuningStore(initialErtsEnabled = false)
        val odoDevice = sampleDevice(id = 301L, type = DeviceType.SPEED_AND_LOCATION_ATT_FUSED, name = "ATT Fused Odo")
        val altDevice = sampleDevice(id = 302L, type = DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED, name = "ATT Fused Alt")
        val vm = createViewModel(mapOf(301L to odoDevice, 302L to altDevice), tuningStore)

        // 1. AdvancedTuningScreen writes true via saveTuningConfig() -> both Fused sensors immediately see true
        tuningStore.saveTuningConfig(TuningConfig(postWorkoutErtsSmoothingEnabled = true))
        vm.loadInitialDeviceData(301L)
        assertTrue(vm.postWorkoutErtsPersisted.value)
        assertNull(vm.postWorkoutErtsDraft.value)

        vm.loadInitialDeviceData(302L)
        assertTrue(vm.postWorkoutErtsPersisted.value)
        assertNull(vm.postWorkoutErtsDraft.value)

        // 2. User toggles OFF in Altitude Fused dialog and Saves -> Odometry Fused dialog & AdvancedTuningScreen see false
        vm.onPostWorkoutErtsSmoothingChanged(false)
        vm.saveChanges()
        assertFalse(tuningStore.getTuningConfig().postWorkoutErtsSmoothingEnabled)
        assertFalse(tuningStore.tuningConfigFlow.first().postWorkoutErtsSmoothingEnabled)

        vm.loadInitialDeviceData(301L)
        assertFalse(vm.postWorkoutErtsPersisted.value)

        // 3. External update while dialog is clean (no draft) updates postWorkoutErtsPersisted reactively
        tuningStore.saveTuningConfig(TuningConfig(postWorkoutErtsSmoothingEnabled = true))
        assertTrue(vm.postWorkoutErtsPersisted.value)
    }

    @Test
    fun testErtsDraft_survivesRotationAndDiscardsWithZeroWritesOnCancel_tstFil017_5() = runBlocking {
        val tuningStore = createInMemoryTuningStore(initialErtsEnabled = false)
        val odoDevice = sampleDevice(id = 401L, type = DeviceType.SPEED_AND_LOCATION_ATT_FUSED, name = "ATT Fused Odo")
        val vm = createViewModel(mapOf(401L to odoDevice), tuningStore)

        vm.loadInitialDeviceData(401L)
        assertFalse(vm.postWorkoutErtsPersisted.value)

        // Toggle ON in draft
        vm.onPostWorkoutErtsSmoothingChanged(true)
        assertEquals(true, vm.postWorkoutErtsDraft.value)

        // Simulate screen rotation (re-invoking loadInitialDeviceData for same deviceId)
        vm.loadInitialDeviceData(401L)
        assertEquals(true, vm.postWorkoutErtsDraft.value)
        assertFalse(tuningStore.postWorkoutErtsSmoothingFlow.first())

        // Cancel -> discards draft with ZERO writes to TuningPreferencesDataStore
        vm.discardDraft()
        assertNull(vm.postWorkoutErtsDraft.value)
        assertFalse(tuningStore.postWorkoutErtsSmoothingFlow.first())
        assertFalse(tuningStore.getTuningConfig().postWorkoutErtsSmoothingEnabled)

        // Reopen -> cleanly reloads persisted false
        vm.loadInitialDeviceData(401L)
        assertFalse(vm.postWorkoutErtsPersisted.value)
        assertNull(vm.postWorkoutErtsDraft.value)
    }

    private fun sampleDevice(
        id: Long,
        type: DeviceType,
        name: String
    ) = DeviceUiData(
        id = id,
        protocol = Protocol.SMARTPHONE,
        deviceType = type,
        lastSeen = "Now",
        manufacturer = "aTrainingTracker",
        deviceName = name,
        isPaired = true,
        linkedEquipment = emptyList(),
        availableEquipment = emptyList(),
        powerFeaturesFlags = null,
        batteryPercentage = 100,
        linkedSportTypes = emptyList(),
        deviceTypeIconRes = R.drawable.bt_bike_spd,
        batteryStatusIconRes = R.drawable.ic_battery_full,
        onEquipmentResId = R.string.devices_on_equipment_text,
        wheelCircumference = null,
        calibrationFactor = null,
        powerFeatures = null,
        isConnected = false,
        mainValue = null,
        allValues = null
    )
}
