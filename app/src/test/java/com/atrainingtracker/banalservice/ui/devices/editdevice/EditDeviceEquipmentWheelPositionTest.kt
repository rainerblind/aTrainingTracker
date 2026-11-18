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

import android.content.Context
import android.database.MatrixCursor
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.devicedata.RawDeviceDataProvider
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Strictly offline Robolectric Compose UI and hydration test suite for [EditDeviceDialog]
 * wheel speed sensor mounting position display, [WheelPositionSelectionDialog] reuse with
 * conflict preview, draft Cancel/OK semantics, non-wheel sensor isolation, and Light/Dark
 * preview PNG capture (`REQ-SEN-031`, `TST-SEN-031.2`..`TST-SEN-031.4`, `REQ-PRO-049.1`, `Rule 8.5`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class EditDeviceEquipmentWheelPositionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun sampleDevice(
        deviceType: DeviceType,
        linkedEquipment: List<String> = listOf("Gravel Bike"),
        availableEquipment: List<String> = listOf("Gravel Bike", "Road Bike"),
        linkedWheelPositions: Map<String, WheelPosition> = mapOf("Gravel Bike" to WheelPosition.FRONT),
        wheelPositionConflicts: Map<String, Map<WheelPosition, String>> = emptyMap(),
        onEquipmentResId: Int = R.string.devices_on_bikes_text
    ): DeviceUiData = DeviceUiData(
        id = 42L,
        protocol = Protocol.BLUETOOTH_LE,
        deviceType = deviceType,
        lastSeen = "Now",
        manufacturer = "Garmin",
        deviceName = "Speed Sensor 2",
        isPaired = true,
        linkedEquipment = linkedEquipment,
        availableEquipment = availableEquipment,
        powerFeaturesFlags = null,
        batteryPercentage = 95,
        linkedSportTypes = listOf("Cycling"),
        deviceTypeIconRes = R.drawable.bt_bike_spd,
        batteryStatusIconRes = R.drawable.ic_battery_full,
        onEquipmentResId = onEquipmentResId,
        wheelCircumference = 2105,
        calibrationFactor = null,
        powerFeatures = null,
        isConnected = false,
        mainValue = null,
        allValues = null,
        linkedWheelPositions = linkedWheelPositions,
        wheelPositionConflicts = wheelPositionConflicts
    )

    @Test
    fun testWheelSpeedSensor_displaysPositionsInSummaryAndSheet_andPromptsWithConflictPreview() {
        var committedEquipment: List<String>? = null
        var committedPositions: Map<String, WheelPosition>? = null

        val device = sampleDevice(
            deviceType = DeviceType.BIKE_SPEED,
            linkedEquipment = listOf("Gravel Bike"),
            availableEquipment = listOf("Gravel Bike", "Road Bike"),
            linkedWheelPositions = mapOf("Gravel Bike" to WheelPosition.FRONT),
            wheelPositionConflicts = mapOf(
                "Road Bike" to mapOf(WheelPosition.REAR to "Wahoo Rear")
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                EquipmentSectionContent(
                    data = device,
                    onEquipmentChanged = { eq, pos ->
                        committedEquipment = eq
                        committedPositions = pos
                    }
                )
            }
        }

        val frontLabel = context.getString(R.string.equipment_wheel_position_front)
        val rearLabel = context.getString(R.string.equipment_wheel_position_rear)
        val expectedConflict = context.getString(R.string.equipment_wheel_position_conflict_swap, "Wahoo Rear")

        // Summary displays bike + localized Front label
        composeTestRule.onNodeWithTag("equipment_summary_field")
            .assertIsDisplayed()
            .assertTextContains("Gravel Bike ($frontLabel)")

        // Open bike-linking sheet
        composeTestRule.onNodeWithTag("equipment_summary_trigger").performClick()
        composeTestRule.onNodeWithTag("equipment_sheet_row_Gravel Bike").assertIsDisplayed()
        composeTestRule.onNodeWithTag("equipment_sheet_row_Road Bike").assertIsDisplayed()

        // Tap Road Bike -> WheelPositionSelectionDialog opens with conflict swap preview for Rear
        composeTestRule.onNodeWithTag("equipment_sheet_row_Road Bike").performClick()
        composeTestRule.onNodeWithTag("wheel_position_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wheel_position_rear_conflict", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains(expectedConflict)

        // Select Rear -> stages draft inside sheet without invoking onEquipmentChanged yet
        composeTestRule.onNodeWithTag("wheel_position_rear").performClick()
        assertEquals(null, committedEquipment)
        composeTestRule.onNodeWithText(rearLabel).assertIsDisplayed()

        // Confirm sheet OK -> passes updated draft to ViewModel callback
        composeTestRule.onNodeWithTag("equipment_sheet_ok").performClick()
        assertEquals(listOf("Gravel Bike", "Road Bike"), committedEquipment)
        assertEquals(
            mapOf("Gravel Bike" to WheelPosition.FRONT, "Road Bike" to WheelPosition.REAR),
            committedPositions
        )
    }

    @Test
    fun testBikeSpeedAndCadence_sheetCancelDiscardsDraftWheelPositionChanges() {
        var callbackCount = 0
        val device = sampleDevice(
            deviceType = DeviceType.BIKE_SPEED_AND_CADENCE,
            linkedEquipment = listOf("Gravel Bike"),
            availableEquipment = listOf("Gravel Bike", "Road Bike"),
            linkedWheelPositions = mapOf("Gravel Bike" to WheelPosition.UNSPECIFIED)
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                EquipmentSectionContent(
                    data = device,
                    onEquipmentChanged = { _, _ -> callbackCount++ }
                )
            }
        }

        val unspecifiedLabel = context.getString(R.string.equipment_wheel_position_unspecified)
        composeTestRule.onNodeWithTag("equipment_summary_field")
            .assertTextContains("Gravel Bike ($unspecifiedLabel)")

        // Open sheet, change Gravel Bike to FRONT, then tap Cancel
        composeTestRule.onNodeWithTag("equipment_summary_trigger").performClick()
        composeTestRule.onNodeWithTag("equipment_sheet_row_Gravel Bike").performClick()
        composeTestRule.onNodeWithTag("wheel_position_front").performClick()
        composeTestRule.onNodeWithTag("equipment_sheet_cancel").performClick()

        // Cancel must discard draft changes with zero callback invocation
        assertEquals(0, callbackCount)
        composeTestRule.onNodeWithTag("equipment_summary_field")
            .assertTextContains("Gravel Bike ($unspecifiedLabel)")
    }

    @Test
    fun testNonWheelSensors_retainSimpleCheckboxUiWithoutWheelPositionDialog() {
        val nonWheelTypes = listOf(
            DeviceType.HRM,
            DeviceType.BIKE_POWER,
            DeviceType.BIKE_RADAR,
            DeviceType.BIKE_CADENCE,
            DeviceType.RUN_SPEED
        )

        fun buildNonWheelDevice(type: DeviceType) = sampleDevice(
            deviceType = type,
            linkedEquipment = listOf("Gravel Bike"),
            availableEquipment = listOf("Gravel Bike", "Road Bike"),
            linkedWheelPositions = mapOf("Gravel Bike" to WheelPosition.FRONT),
            onEquipmentResId = if (type == DeviceType.RUN_SPEED) {
                R.string.devices_on_shoes_text
            } else {
                R.string.devices_on_bikes_text
            }
        )

        val currentDeviceState = androidx.compose.runtime.mutableStateOf(buildNonWheelDevice(nonWheelTypes.first()))
        var committed: List<String>? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                EquipmentSectionContent(
                    data = currentDeviceState.value,
                    onEquipmentChanged = { eq, _ -> committed = eq }
                )
            }
        }

        nonWheelTypes.forEach { type ->
            committed = null
            composeTestRule.runOnUiThread {
                currentDeviceState.value = buildNonWheelDevice(type)
            }
            composeTestRule.waitForIdle()

            // Summary must render plain name without wheel position
            composeTestRule.onNodeWithTag("equipment_summary_field")
                .assertTextContains("Gravel Bike")
            composeTestRule.onNodeWithText(context.getString(R.string.equipment_wheel_position_front))
                .assertDoesNotExist()

            // Tapping row in sheet toggles directly without opening WheelPositionSelectionDialog
            composeTestRule.onNodeWithTag("equipment_summary_trigger").performClick()
            composeTestRule.onNodeWithTag("equipment_sheet_row_Road Bike").performClick()
            composeTestRule.onNodeWithTag("wheel_position_dialog").assertDoesNotExist()
            composeTestRule.onNodeWithTag("equipment_sheet_ok").performClick()

            assertEquals("Simple toggle failed for $type", listOf("Gravel Bike", "Road Bike"), committed)
        }
    }

    @Test
    fun testRawDeviceDataProvider_hydratesWheelPositionsAndConflictsOnlyForWheelSensors() {
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        val mockEqDb = mockk<EquipmentDbHelper>(relaxed = true)
        val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)

        val cursor = MatrixCursor(
            arrayOf(
                DevicesDatabaseManager.DevicesDbHelper.C_ID,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE,
                DevicesDatabaseManager.DevicesDbHelper.PROTOCOL,
                DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE,
                DevicesDatabaseManager.DevicesDbHelper.LAST_BATTERY_PERCENTAGE,
                DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME,
                DevicesDatabaseManager.DevicesDbHelper.NAME,
                DevicesDatabaseManager.DevicesDbHelper.PAIRED,
                DevicesDatabaseManager.DevicesDbHelper.CALIBRATION_FACTOR
            )
        )
        cursor.addRow(arrayOf<Any?>(42L, DeviceType.BIKE_SPEED.name, Protocol.BLUETOOTH_LE.name, "Now", 90, "Garmin", "Front Speed", 1, 2.105))
        cursor.moveToFirst()

        every { mockEqDb.getLinkedEquipmentFromDeviceId(42L) } returns listOf("Gravel Bike")
        every { mockEqDb.getEquipment(any<com.atrainingtracker.banalservice.BSportType>()) } returns listOf("Gravel Bike", "Road Bike")
        every { mockEqDb.getEquipmentId("Gravel Bike") } returns 10L
        every { mockEqDb.getEquipmentId("Road Bike") } returns 20L
        every { mockEqDb.getWheelPosition(10L, 42L) } returns WheelPosition.FRONT
        every { mockEqDb.getDeviceWheelPositionsForEquipment(10L) } returns mapOf(
            42L to WheelPosition.FRONT,
            99L to WheelPosition.REAR
        )
        every { mockEqDb.getDeviceWheelPositionsForEquipment(20L) } returns emptyMap()
        every { mockDevicesDb.getDeviceName(99L) } returns "Rear Cadence/Speed"

        val provider = RawDeviceDataProvider(mockDevicesDb, mockEqDb, mockDiscovery)
        val raw = provider.getDeviceData(cursor)

        assertEquals(mapOf("Gravel Bike" to WheelPosition.FRONT), raw.linkedWheelPositions)
        assertEquals(
            mapOf("Gravel Bike" to mapOf(WheelPosition.REAR to "Rear Cadence/Speed")),
            raw.wheelPositionConflicts
        )
    }

    @Test
    fun testLightDarkWheelPositionPreviews_renderAndCapturePng_rule85() {
        val device = sampleDevice(
            deviceType = DeviceType.BIKE_SPEED,
            linkedEquipment = listOf("Gravel Bike", "Road Bike"),
            availableEquipment = listOf("Gravel Bike", "Road Bike", "Winter Bike"),
            linkedWheelPositions = mapOf(
                "Gravel Bike" to WheelPosition.FRONT,
                "Road Bike" to WheelPosition.REAR
            )
        )

        composeTestRule.setContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("WheelPositionEditDevicePreviewRoot"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Light — Wheel Position in EditDeviceDialog",
                                style = MaterialTheme.typography.titleMedium
                            )
                            EquipmentSectionContent(
                                data = device,
                                onEquipmentChanged = { _, _ -> }
                            )
                            EquipmentSheetListContent(
                                availableEquipment = device.availableEquipment,
                                selectedItems = device.linkedEquipment,
                                draftPositions = device.linkedWheelPositions,
                                isWheelSensor = true,
                                onSimpleToggle = {},
                                onWheelBikeClick = {}
                            )
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Dark — Wheel Position in EditDeviceDialog",
                                style = MaterialTheme.typography.titleMedium
                            )
                            EquipmentSectionContent(
                                data = device,
                                onEquipmentChanged = { _, _ -> }
                            )
                            EquipmentSheetListContent(
                                availableEquipment = device.availableEquipment,
                                selectedItems = device.linkedEquipment,
                                draftPositions = device.linkedWheelPositions,
                                isWheelSensor = true,
                                onSimpleToggle = {},
                                onWheelBikeClick = {}
                            )
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("WheelPositionEditDevicePreviewRoot")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8719_wheel_position_edit_device.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }
}
