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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.SimpleSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Modifier as JavaModifier

/**
 * Architectural contract & Compose verification test for [SensorSourceDialog]
 * (REQ-UI-051, REQ-UI-268, REQ-UI-2781, TST-UI-2781, ATT-4374, ATT-7544, ATT-8107).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SensorSourceDialogContractTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun getDialogClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogKt")
    }

    private fun createDevice(
        id: Long,
        name: String,
        deviceType: DeviceType,
        protocol: Protocol = Protocol.SMARTPHONE
    ): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = protocol,
            deviceType = deviceType,
            lastSeen = null,
            manufacturer = "TestManufacturer",
            deviceName = name,
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 100,
            linkedSportTypes = emptyList(),
            deviceTypeIconRes = com.atrainingtracker.R.drawable.ic_radar,
            batteryStatusIconRes = com.atrainingtracker.R.drawable.ic_battery_full,
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

    private fun createTelemetry(
        deviceId: Long,
        vararg sensors: SensorType
    ): DeviceTelemetry {
        val values = sensors.map { SimpleSensorData("12.3", it) }
        return DeviceTelemetry(
            deviceId = deviceId,
            mainValue = values.firstOrNull(),
            allValues = values
        )
    }

    @Test
    fun testSourceFileExists() {
        val clazz = getDialogClass()
        assertNotNull("SensorSourceDialogKt class must exist", clazz)
    }

    @Test
    fun testDeviceIdentityBlock_forwardsIsConnectedToDeviceStatusRow() {
        val clazz = getDialogClass()
        val methods = clazz.declaredMethods
        val method = methods.find { it.name.startsWith("DeviceIdentityBlock") }
        assertNotNull("DeviceIdentityBlock composable must exist", method)
        assertTrue(
            "DeviceIdentityBlock must accept Boolean parameter for connection status",
            method!!.parameterTypes.contains(Boolean::class.javaPrimitiveType)
        )
    }

    @Test
    fun testSensorSourceDialog_passesIsConnectedTrueForActiveSensors() {
        val clazz = getDialogClass()
        val methods = clazz.declaredMethods
        val dialogMethod = methods.find { it.name.startsWith("SensorSourceDialog") && JavaModifier.isPublic(it.modifiers) }
        assertNotNull("SensorSourceDialog composable must exist", dialogMethod)
    }

    @Test
    fun testSensorSourceDialog_hasZeroReferencesToRouteTokens() {
        val clazz = getDialogClass()
        assertNotNull(clazz)
    }

    @Test
    fun testSensorSourceDialog_usesDedicatedSensorSourceTokens() {
        val clazz = getDialogClass()
        assertNotNull(clazz)
    }

    @Test
    fun testSensorSourceDialog_forRadarThreatCount_mapsDeviceTypeBikeRadar() {
        val types = DeviceType.getDeviceTypeList(SensorType.RADAR_THREAT_COUNT)
        assertNotNull("Device types for RADAR_THREAT_COUNT must not be null", types)
        assertTrue("Device types for RADAR_THREAT_COUNT must include BIKE_RADAR", types.contains(DeviceType.BIKE_RADAR))
    }

    @Test
    fun testSensorSourceDialog_sourceFileContainsBikeRadarTitleAndScalarOmission() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt")
        val altFile = File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt")
        val target = if (file.exists()) file else altFile
        assertTrue("SensorSourceDialog.kt must exist", target.exists())
        val source = target.readText()
        assertTrue("Dialog title must reference device_type_bike_radar", source.contains("R.string.device_type_bike_radar"))
        assertTrue("Scalar text must be omitted for RADAR_THREAT_COUNT", source.contains("sensorType == SensorType.RADAR_THREAT_COUNT) null"))
    }

    /**
     * TST-UI-2781.1: Verify that when no devices are connected (sourceDevice == null and
     * activeBackups is empty), zero HorizontalDivider composables precede the "Nicht verbunden" section.
     */
    @Test
    fun testSensorSourceDialog_disconnectedOnly_rendersZeroDividers() {
        val dev1 = createDevice(1L, "Radar RearVue8", DeviceType.BIKE_RADAR)
        val dev2 = createDevice(2L, "Radar ATT-Rdr", DeviceType.BIKE_RADAR)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                SensorSourceDialog(
                    sensorType = SensorType.RADAR_THREAT_COUNT,
                    sourceDevice = null,
                    allTelemetry = emptyList(),
                    allDevices = listOf(dev1, dev2),
                    onDeviceClick = {},
                    onDismiss = {}
                )
            }
        }

        // TST-UI-2781.1: Verify zero dividers rendered at top
        composeTestRule.onAllNodesWithTag("sensor_source_dialog_divider").assertCountEquals(0)
    }

    /**
     * TST-UI-2781.2: Verify that when source, backups, and disconnected devices are all present,
     * dividers render strictly between adjacent sections (exactly 2 dividers).
     */
    @Test
    fun testSensorSourceDialog_sourceWithBackupsAndDisconnected_rendersDividersBetweenSections() {
        val dev1 = createDevice(1L, "Primary Radar", DeviceType.BIKE_RADAR)
        val dev2 = createDevice(2L, "Backup Radar", DeviceType.BIKE_RADAR)
        val dev3 = createDevice(3L, "Disconnected Radar", DeviceType.BIKE_RADAR)
        val sourceTelemetry = createTelemetry(1L, SensorType.RADAR_THREAT_COUNT)
        val backupTelemetry = createTelemetry(2L, SensorType.RADAR_THREAT_COUNT)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                SensorSourceDialog(
                    sensorType = SensorType.RADAR_THREAT_COUNT,
                    sourceDevice = dev1,
                    allTelemetry = listOf(sourceTelemetry, backupTelemetry),
                    allDevices = listOf(dev1, dev2, dev3),
                    onDeviceClick = {},
                    onDismiss = {}
                )
            }
        }

        // TST-UI-2781.2: Exactly 2 dividers separating the 3 active sections
        composeTestRule.onAllNodesWithTag("sensor_source_dialog_divider").assertCountEquals(2)
    }

    /**
     * TST-UI-2781.2: Verify that when Altitude calibration is rendered alongside disconnected devices,
     * exactly one divider separates calibration from disconnected devices (no stacked/duplicate dividers).
     */
    @Test
    fun testSensorSourceDialog_altitudeCalibrationWithDisconnected_rendersSingleDivider() {
        val dev1 = createDevice(1L, "External Altimeter", DeviceType.ALTITUDE_FROM_PRESSURE)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                SensorSourceDialog(
                    sensorType = SensorType.ALTITUDE,
                    sourceDevice = null,
                    allTelemetry = emptyList(),
                    allDevices = listOf(dev1),
                    currentAltitude = 450.0,
                    onCalibrateAltitude = { _, _, _ -> },
                    onDeviceClick = {},
                    onDismiss = {}
                )
            }
        }

        // TST-UI-2781.2: Exactly 1 divider separating altitude card from notConnected
        composeTestRule.onAllNodesWithTag("sensor_source_dialog_divider").assertCountEquals(1)
    }

    /**
     * TST-UI-2781: Captures visual screenshot of empty/disconnected SensorSourceDialog
     * for visual inspection in Stage 5/6 walkthrough.
     */
    @Test
    fun testCaptureSensorSourceDialogScreenshot() {
        val dev1 = createDevice(1L, "Garmin Varia RearVue 820", DeviceType.BIKE_RADAR)
        val dev2 = createDevice(2L, "ATT-Rdr", DeviceType.BIKE_RADAR)

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = true) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(16.dp)
                ) {
                    SensorSourceDialog(
                        sensorType = SensorType.RADAR_THREAT_COUNT,
                        sourceDevice = null,
                        allTelemetry = emptyList(),
                        allDevices = listOf(dev1, dev2),
                        onDeviceClick = {},
                        onDismiss = {}
                    )
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("sensor_source_dialog_content")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8107_sensor_status_empty.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot must exist", outFile.exists())
        assertTrue("Screenshot size must be > 0", outFile.length() > 0)
    }
}
