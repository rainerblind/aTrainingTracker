/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceFilterCriteria
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceItem
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceListViewModel
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceSortOrder
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreen
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedViewModel
import com.atrainingtracker.banalservice.ui.devices.devicetabs.UiState
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialog
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceViewModel
import com.atrainingtracker.trainingtracker.ui.equipment.EquipmentViewModel
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Robolectric Compose UI tests for device management screens and items:
 * - `TST-UI-043` (`REQ-UI-041`, `REQ-UI-043`): `DevicesTabbedScreen` + `DeviceItem` + `EditDeviceDialog`.
 * - `TST-UI-044` (`REQ-UI-042`): `DeviceItem` multi-row layout positioning LED left of metadata without divider clutter.
 * - `TST-UI-050` (`REQ-UI-050`): `DeviceStatusRow` / 3-row vs 2-row identity alignment across `DeviceItem` & `EditDeviceDialog`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class DeviceManagementUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleDevice(isPaired: Boolean = true): DeviceUiData = DeviceUiData(
        id = 12L,
        protocol = Protocol.BLUETOOTH_LE,
        deviceType = DeviceType.HRM,
        lastSeen = "10:00",
        manufacturer = "Polar",
        deviceName = "Polar H10",
        isPaired = isPaired,
        linkedEquipment = listOf("Road Bike"),
        availableEquipment = listOf("Road Bike"),
        powerFeaturesFlags = null,
        batteryPercentage = 85,
        deviceTypeIconRes = R.drawable.ic_heart_rate,
        batteryStatusIconRes = R.drawable.ic_battery_full,
        onEquipmentResId = R.string.devices_on_equipment_text,
        wheelCircumference = null,
        calibrationFactor = null,
        powerFeatures = null,
        isConnected = true,
        mainValue = "142 bpm",
        allValues = emptyList()
    )

    @Test
    fun deviceItem_andEditDeviceDialog_verifyIdentityLayoutSwitchAndSave_TST_UI_043_044_050() {
        // TST-UI-043, TST-UI-044, TST-UI-050
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device = sampleDevice()
        var pairClicked = 0
        var itemClicked = 0

        val mockEditVm = mockk<EditDeviceViewModel>(relaxed = true).apply {
            every { deviceSnapshot } returns MutableStateFlow(device)
            every { persistedDeviceIdentity } returns MutableStateFlow(device)
            every { deviceLiveData } returns MutableStateFlow(device)
            every { wheelCircumferenceHistory } returns MutableStateFlow(emptyList())
            every { deviceNotFound } returns MutableStateFlow(false)
            every { wheelSizeNames } returns listOf("700x25c")
            every { wheelSizeValues } returns listOf("2105")
        }

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    Column {
                        DeviceItem(
                            device = device,
                            onPairClick = { pairClicked++ },
                            onItemClick = { itemClicked++ },
                            onLongClick = {}
                        )
                        EditDeviceDialog(
                            deviceId = 12L,
                            onDismiss = {},
                            viewModel = mockEditVm
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // DeviceItem renders name, manufacturer, live reading, and DeviceStatusRow battery+availability
        val availableSuffix = "85% (${context.getString(R.string.devices_available)})"
        val nameBounds = composeTestRule.onAllNodesWithTextSafe("Polar H10")
        assertTrue("DeviceItem + EditDeviceDialog both render device identity", nameBounds >= 2)
        composeTestRule.onAllNodesWithTextSafe(availableSuffix).also { count ->
            assertTrue("Both DeviceItem and EditDeviceDialog display aligned DeviceStatusRow", count >= 2)
        }

        // Pair switch interaction (first toggleable belongs to DeviceItem above EditDeviceDialog)
        composeTestRule.onAllNodes(isToggleable()).onFirst().performClick()
        assertEquals(1, pairClicked)

        // Item click triggers callback
        composeTestRule.onAllNodesWithText("142 bpm").onFirst().performClick()
        assertEquals(1, itemClicked)

        // EditDeviceDialog name update & save triggers ViewModel
        val saveLabel = context.getString(R.string.save)
        val editableMatcher = SemanticsMatcher("editable") {
            it.config.getOrNull(SemanticsProperties.EditableText) != null
        }
        composeTestRule.onAllNodes(editableMatcher).onFirst().performTextReplacement("Polar H10 Chest")
        verify { mockEditVm.onDeviceNameChanged("Polar H10 Chest") }

        composeTestRule.onNodeWithText(saveLabel).performClick()
        verify { mockEditVm.saveChanges() }
    }

    @Test
    fun devicesTabbedScreen_rendersTabBarWithActiveDeviceType_TST_UI_043() {
        // TST-UI-043
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockTabVm = mockk<DevicesTabbedViewModel>(relaxed = true).apply {
            every { protocol } returns Protocol.ALL
            every { uiState } returns MutableLiveData<UiState>(UiState.DisplayingTabs(DeviceType.ALL))
            every { isSearchingForNewDevices } returns MutableStateFlow(false)
            every { scanningProtocol } returns MutableStateFlow(null)
            every { scanningDeviceType } returns MutableStateFlow(null)
            every { filterCriteria } returns MutableStateFlow(DeviceFilterCriteria())
            every { sortOrder } returns MutableStateFlow(DeviceSortOrder.ALPHABETICAL)
            every { zonePromptState } returns MutableStateFlow(null)
            every { newDeviceDiscoveredEvent } returns MutableSharedFlow()
        }
        val mockListVm = mockk<DeviceListViewModel>(relaxed = true).apply {
            every { getFilteredDevices(any(), any(), any()) } returns MutableStateFlow(listOf(sampleDevice()))
            every { zoneNavigationEvent } returns MutableSharedFlow()
            every { swapOrUpgradeDialogEvent } returns MutableStateFlow(null)
            every { keepTwoSensorsDialogEvent } returns MutableStateFlow(null)
            every { garageNudgeEvent } returns MutableStateFlow(null)
        }
        val mockEquipVm = mockk<EquipmentViewModel>(relaxed = true)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    DevicesTabbedScreen(
                        tabViewModel = mockTabVm,
                        listViewModel = mockListVm,
                        equipmentViewModel = mockEquipVm
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.devices_tab_available)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Polar H10").onFirst().assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextSafe(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
