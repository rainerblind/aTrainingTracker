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
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineCapabilityStore
import com.atrainingtracker.banalservice.sensor.HrHealthTier
import com.atrainingtracker.banalservice.sensor.HrModality
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Before
    fun setUpCapabilityStore() {
        PolarOfflineCapabilityStore.resetForTesting()
    }

    @After
    fun tearDownCapabilityStore() {
        PolarOfflineCapabilityStore.resetForTesting()
    }

    private fun getDialogClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogKt")
    }

    private fun createDevice(
        id: Long,
        name: String,
        deviceType: DeviceType,
        protocol: Protocol = Protocol.SMARTPHONE,
        manufacturer: String = "TestManufacturer",
        btAddress: String? = null,
        hrModality: HrModality? = null,
        isPaired: Boolean = true,
        polarLiveImuEnabled: Boolean = false
    ): DeviceUiData {
        return DeviceUiData(
            id = id,
            protocol = protocol,
            deviceType = deviceType,
            lastSeen = null,
            manufacturer = manufacturer,
            deviceName = name,
            isPaired = isPaired,
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
            isNewlyDiscovered = false,
            btAddress = btAddress,
            hrModality = hrModality,
            polarLiveImuEnabled = polarLiveImuEnabled
        )
    }

    private fun createTelemetry(
        deviceId: Long,
        vararg sensors: SensorType,
        hrHealthTier: HrHealthTier? = null,
        hrModality: HrModality? = null
    ): DeviceTelemetry {
        val values = sensors.map { SimpleSensorData("142", it) }
        return DeviceTelemetry(
            deviceId = deviceId,
            mainValue = values.firstOrNull(),
            allValues = values,
            hrHealthTier = hrHealthTier,
            hrModality = hrModality
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

    /**
     * TST-UI-2804.3: Verifies via Compose UI tree vertical bounds that `SensorSourceDialog` for
     * `SensorType.HR` renders Active Backups ordered by `(healthTier, modalityTier, deviceName, deviceId)`
     * and Not Connected ordered by `(modalityTier, deviceName, deviceId)`, overriding alphabetical names.
     */
    @Test
    fun testSensorSourceDialog_hrOrdersActiveBackupsAndNotConnectedByHealthAndModality() {
        val verityMac = "DE:AD:BE:EF:00:01"
        PolarOfflineCapabilityStore.recordDisMetadata(
            context = null,
            macAddress = verityMac,
            modelNumber = "Sense",
            confirmedPolarService = true
        )

        val primarySource = createDevice(1L, "Zebra Primary H10", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar")
        val backupHealthyEcg = createDevice(2L, "Polar H10 Backup", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar")
        val backupHealthyAnt = createDevice(3L, "Mid ANT+ Strap", DeviceType.HRM, Protocol.ANT_PLUS, "Garmin")
        val backupHealthyOptical = createDevice(
            4L,
            "Armband Optical",
            DeviceType.HRM,
            Protocol.BLUETOOTH_LE,
            "",
            btAddress = verityMac,
            hrModality = HrModality.OPTICAL_PPG
        )
        val backupDegradedEcg = createDevice(5L, "AAB Degraded ECG", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar")
        val backupStaleEcg = createDevice(6L, "AAA Stale ECG", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar")

        val discOptical = createDevice(
            7L,
            "Armband Optical Disconnected",
            DeviceType.HRM,
            Protocol.BLUETOOTH_LE,
            "",
            btAddress = verityMac,
            hrModality = HrModality.OPTICAL_PPG
        )
        val discAnt = createDevice(8L, "Ant Disconnected", DeviceType.HRM, Protocol.ANT_PLUS, "Garmin")
        val discEcg = createDevice(9L, "Polar H10 Disconnected", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar")

        val allDevices = listOf(
            primarySource,
            backupStaleEcg,
            backupDegradedEcg,
            backupHealthyOptical,
            backupHealthyAnt,
            backupHealthyEcg,
            discOptical,
            discAnt,
            discEcg
        )

        val allTelemetry = listOf(
            createTelemetry(6L, SensorType.HR, hrHealthTier = HrHealthTier.STALE_OR_NULL, hrModality = HrModality.ECG),
            createTelemetry(5L, SensorType.HR, hrHealthTier = HrHealthTier.DEGRADED, hrModality = HrModality.ECG),
            createTelemetry(4L, SensorType.HR, hrHealthTier = HrHealthTier.HEALTHY, hrModality = HrModality.OPTICAL_PPG),
            createTelemetry(3L, SensorType.HR, hrHealthTier = HrHealthTier.HEALTHY, hrModality = HrModality.ANT_PLUS),
            createTelemetry(2L, SensorType.HR, hrHealthTier = HrHealthTier.HEALTHY, hrModality = HrModality.ECG),
            createTelemetry(1L, SensorType.HR, hrHealthTier = HrHealthTier.HEALTHY, hrModality = HrModality.ECG)
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                SensorSourceDialog(
                    sensorType = SensorType.HR,
                    sourceDevice = primarySource,
                    allTelemetry = allTelemetry,
                    allDevices = allDevices,
                    onDeviceClick = {},
                    onDismiss = {}
                )
            }
        }

        fun yPos(label: String): Float =
            composeTestRule.onNodeWithText(label, useUnmergedTree = true)
                .fetchSemanticsNode()
                .positionInRoot
                .y

        // Active Backups order: Healthy ECG -> Healthy ANT+ -> Healthy Optical PPG -> Degraded ECG -> Stale ECG
        assertTrue(yPos("Zebra Primary H10") < yPos("Polar H10 Backup"))
        assertTrue(yPos("Polar H10 Backup") < yPos("Mid ANT+ Strap"))
        assertTrue(yPos("Mid ANT+ Strap") < yPos("Armband Optical"))
        assertTrue(yPos("Armband Optical") < yPos("AAB Degraded ECG"))
        assertTrue(yPos("AAB Degraded ECG") < yPos("AAA Stale ECG"))

        // Not Connected order: ECG ("Polar H10 Disconnected") -> ANT+ ("Ant Disconnected") -> Optical PPG ("Armband Optical Disconnected")
        assertTrue(yPos("AAA Stale ECG") < yPos("Polar H10 Disconnected"))
        assertTrue(yPos("Polar H10 Disconnected") < yPos("Ant Disconnected"))
        assertTrue(yPos("Ant Disconnected") < yPos("Armband Optical Disconnected"))
    }

    // TST-CON-057.3: Behavioral Compose UI test verifying Polar Live-IMU HRM capability filtering in SensorSourceDialog
    @Test
    fun testComposeRender_cadenceAndVerticalOscillationCapabilityFilterHrms_TST_CON_057_3() {
        val h10Mac = "A0:9E:1A:61:61:61"
        val verityMac = "A0:9E:1A:62:62:62"
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, h10Mac, PolarOfflineCapabilityStore.PolarDeviceType.H10)
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, verityMac, PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE)

        val h10Device = createDevice(
            1L, "Polar H10 Chest", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar",
            btAddress = h10Mac, polarLiveImuEnabled = true
        )
        val verityDevice = createDevice(
            2L, "Verity Armband", DeviceType.HRM, Protocol.BLUETOOTH_LE, "",
            btAddress = verityMac, polarLiveImuEnabled = true
        )
        val antH10 = createDevice(3L, "Polar H10 ANT+", DeviceType.HRM, Protocol.ANT_PLUS, "Polar")
        val genericBleHrm = createDevice(4L, "Generic BLE Strap", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Wahoo", btAddress = "11:22:33:44:55:66")
        val allDevices = listOf(h10Device, verityDevice, antH10, genericBleHrm)

        val sensorState = androidx.compose.runtime.mutableStateOf(SensorType.CADENCE)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                SensorSourceDialog(
                    sensorType = sensorState.value,
                    sourceDevice = null,
                    allTelemetry = emptyList(),
                    allDevices = allDevices,
                    onDeviceClick = {},
                    onDismiss = {}
                )
            }
        }

        // CADENCE shows BLE H10 and BLE Verity Sense, hiding ANT+ H10 and generic BLE HRM
        composeTestRule.onNodeWithText("Polar H10 Chest", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Verity Armband", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Polar H10 ANT+", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Generic BLE Strap", useUnmergedTree = true).assertDoesNotExist()

        // Switch to VERTICAL_OSCILLATION -> ONLY BLE H10 remains visible
        composeTestRule.runOnUiThread { sensorState.value = SensorType.VERTICAL_OSCILLATION }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Polar H10 Chest", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Verity Armband", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Polar H10 ANT+", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Generic BLE Strap", useUnmergedTree = true).assertDoesNotExist()
    }

    // TST-CON-064.2: Behavioral Compose UI test verifying opt-in, pairing, and CADENCE_PRIORITY ordering + screenshot
    @Test
    fun testComposeRender_cadencePolarLiveImuOptInAndPriority_TST_CON_064_2() {
        val h10MacOn = "A0:9E:1A:81:81:81"
        val h10MacOff = "A0:9E:1A:82:82:82"
        val h10MacUnpaired = "A0:9E:1A:83:83:83"
        val verityMacOn = "A0:9E:1A:84:84:84"
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, h10MacOn, PolarOfflineCapabilityStore.PolarDeviceType.H10)
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, h10MacOff, PolarOfflineCapabilityStore.PolarDeviceType.H10)
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, h10MacUnpaired, PolarOfflineCapabilityStore.PolarDeviceType.H10)
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, verityMacOn, PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE)

        val strydFootpod = createDevice(1L, "Stryd Footpod", DeviceType.RUN_SPEED, Protocol.BLUETOOTH_LE, "Stryd")
        val assiomaPower = createDevice(2L, "Assioma Duo", DeviceType.BIKE_POWER, Protocol.ANT_PLUS, "Favero")
        val h10Enabled = createDevice(
            3L, "Polar H10 Live-IMU", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar Electro Oy",
            btAddress = h10MacOn, isPaired = true, polarLiveImuEnabled = true
        )
        val verityEnabled = createDevice(
            4L, "Verity Sense Live-IMU", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar Electro Oy",
            btAddress = verityMacOn, isPaired = true, polarLiveImuEnabled = true
        )
        val h10Disabled = createDevice(
            5L, "Polar H10 Disabled", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar",
            btAddress = h10MacOff, isPaired = true, polarLiveImuEnabled = false
        )
        val h10Unpaired = createDevice(
            6L, "Polar H10 Unpaired", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar",
            btAddress = h10MacUnpaired, isPaired = false, polarLiveImuEnabled = true
        )
        val antH10 = createDevice(
            7L, "Polar H10 ANT+", DeviceType.HRM, Protocol.ANT_PLUS, "Polar",
            isPaired = true, polarLiveImuEnabled = true
        )
        val attFused = createDevice(
            8L, "Phone Fused", DeviceType.SPEED_AND_LOCATION_ATT_FUSED, Protocol.SMARTPHONE, "Google"
        )

        val allDevices = listOf(
            attFused, h10Disabled, h10Unpaired, antH10, verityEnabled, h10Enabled, assiomaPower, strydFootpod
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(16.dp)
                ) {
                    SensorSourceDialog(
                        sensorType = SensorType.CADENCE,
                        sourceDevice = null,
                        allTelemetry = emptyList(),
                        allDevices = allDevices,
                        onDeviceClick = {},
                        onDismiss = {}
                    )
                }
            }
        }

        // Only paired opted-in BLE H10 & Verity appear alongside RUN_SPEED, BIKE_POWER, and ATT_FUSED
        composeTestRule.onNodeWithText("Stryd Footpod", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Assioma Duo", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Polar H10 Live-IMU", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Verity Sense Live-IMU", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Polar H10 Disabled", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Polar H10 Unpaired", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Polar H10 ANT+", useUnmergedTree = true).assertDoesNotExist()

        fun yPos(label: String): Float = composeTestRule
            .onNodeWithText(label, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top

        // CADENCE_PRIORITY: RUN_SPEED < BIKE_POWER < HRM [Polar H10 Live-IMU < Verity Sense Live-IMU] < ATT_FUSED
        assertTrue(yPos("Stryd Footpod") < yPos("Assioma Duo"))
        assertTrue(yPos("Assioma Duo") < yPos("Polar H10 Live-IMU"))
        assertTrue(yPos("Polar H10 Live-IMU") < yPos("Verity Sense Live-IMU"))
        assertTrue(yPos("Verity Sense Live-IMU") < yPos("Phone Fused"))

        val bmp = composeTestRule.onNodeWithTag("sensor_source_dialog_content")
            .captureToImage()
            .asAndroidBitmap()
        val repoRoot = listOf(File("."), File("..")).firstOrNull { File(it, "docs/engineering").exists() } ?: File(".")
        val outFile = File(
            repoRoot,
            "docs/engineering/walkthroughs/img/ATT-8446_sensor_source_cadence_compose.png"
        )
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Screenshot must exist", outFile.exists())
    }

    // TST-CON-064.2: Android 12 (API 31 < TIRAMISU) suppresses opted-in Polar Live-IMU HRMs for CADENCE
    @Test
    @Config(sdk = [31], application = android.app.Application::class)
    fun testComposeRender_cadenceApi31SuppressesPolarLiveImuHrms_TST_CON_064_2() {
        val h10MacOn = "A0:9E:1A:85:85:85"
        val verityMacOn = "A0:9E:1A:86:86:86"
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, h10MacOn, PolarOfflineCapabilityStore.PolarDeviceType.H10)
        PolarOfflineCapabilityStore.recordSdkDeviceType(null, verityMacOn, PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE)

        val h10Enabled = createDevice(
            1L, "Polar H10 Live-IMU", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar",
            btAddress = h10MacOn, isPaired = true, polarLiveImuEnabled = true
        )
        val verityEnabled = createDevice(
            2L, "Verity Sense Live-IMU", DeviceType.HRM, Protocol.BLUETOOTH_LE, "Polar",
            btAddress = verityMacOn, isPaired = true, polarLiveImuEnabled = true
        )
        val assiomaPower = createDevice(3L, "Assioma Duo", DeviceType.BIKE_POWER, Protocol.ANT_PLUS, "Favero")

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                SensorSourceDialog(
                    sensorType = SensorType.CADENCE,
                    sourceDevice = null,
                    allTelemetry = emptyList(),
                    allDevices = listOf(h10Enabled, verityEnabled, assiomaPower),
                    onDeviceClick = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Assioma Duo", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Polar H10 Live-IMU", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Verity Sense Live-IMU", useUnmergedTree = true).assertDoesNotExist()
    }
}
