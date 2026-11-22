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
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineCapabilityStore
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineGattCoordinator
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.devicedata.raw2UiDeviceData
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Unit, repository persistence, and Rule 8.5 Compose screenshot verification for the
 * Polar Live IMU Streaming `@RequiresApi(33)` toggle in [EditDeviceDialog] (`ATT-8016`, `REQ-SET-112.1`, `TST-SET-114.2`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class EditDevicePolarLiveImuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DevicesDatabaseManager.resetForTesting()
        DeviceDataRepository.resetForTesting()
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
    }

    @After
    fun tearDown() {
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
        DeviceDataRepository.resetForTesting()
        DevicesDatabaseManager.resetForTesting()
    }

    @Test
    fun testPolarLiveImuRepositoryPersistence_andEligibilityFilter() {
        // TST-SET-114.2
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:01",
            "Polar H10 1234ABCD",
            "Polar Electro",
            95,
            true
        )
        val garminId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:02",
            "Garmin HRM-Pro",
            "Garmin",
            95,
            true
        )

        val repo = DeviceDataRepository(context, Dispatchers.Unconfined, Dispatchers.Unconfined)
        assertFalse("Opt-in must default to disabled", repo.isPolarLiveImuBridgeEligible(h10Id, "AA:BB:CC:DD:EE:01"))

        db.setPolarLiveImuEnabled(h10Id, true)
        db.setPolarLiveImuEnabled(garminId, true)

        assertTrue("Polar H10 opted-in must be eligible", repo.isPolarLiveImuBridgeEligible(h10Id, "AA:BB:CC:DD:EE:01"))
        assertFalse("Non-Polar HRM must never route to Polar bridge", repo.isPolarLiveImuBridgeEligible(garminId, "AA:BB:CC:DD:EE:02"))
    }

    @Test
    fun testPolarLiveImuRepositoryEligibility_forRenamedPolarSensorsWithDisMetadata() {
        // TST-CON-049.4
        val db = DevicesDatabaseManager.getInstance(context)
        val renamedH10Mac = "AA:BB:CC:DD:EE:03"
        val renamedVerityMac = "AA:BB:CC:DD:EE:04"
        val renamedH10Id = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, renamedH10Mac, "Chest Strap", "", 95, true)
        val renamedVerityId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, renamedVerityMac, "Armband", "", 95, true)
        db.setPolarLiveImuEnabled(renamedH10Id, true)
        db.setPolarLiveImuEnabled(renamedVerityId, true)

        val repo = DeviceDataRepository(context, Dispatchers.Unconfined, Dispatchers.Unconfined)
        assertFalse(repo.isPolarLiveImuBridgeEligible(renamedH10Id, renamedH10Mac))
        assertFalse(repo.isPolarLiveImuBridgeEligible(renamedVerityId, renamedVerityMac))

        PolarOfflineCapabilityStore.recordDisMetadata(context, renamedH10Mac, modelNumber = "H10", serialNumber = "AA11BB22")
        PolarOfflineCapabilityStore.recordDisMetadata(context, renamedVerityMac, modelNumber = "Verity Sense", serialNumber = "CC33DD44")

        assertTrue(repo.isPolarLiveImuBridgeEligible(renamedH10Id, renamedH10Mac))
        assertTrue(repo.isPolarLiveImuBridgeEligible(renamedVerityId, renamedVerityMac))
    }

    @Test
    fun testUpdateDevice_polarLiveImuToggleEmitsBroadcast_whileNonImuEditDoesNot() = runBlocking {
        // TST-CON-050.1
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:05",
            "Polar H10 CAFE1234",
            "Polar Electro",
            95,
            true
        )
        val app = context.applicationContext as Application
        val shadowApp = Shadows.shadowOf(app)
        val repo = DeviceDataRepository(app, Dispatchers.Unconfined, Dispatchers.Unconfined)
        val initial = repo.getDeviceSnapshotById(h10Id)!!

        shadowApp.clearBroadcastIntents()
        repo.updateDevice(initial.copy(deviceName = "Chest Strap Renamed"))
        assertTrue(
            "Non-IMU device edit must not emit POLAR_LIVE_IMU_CHANGED",
            shadowApp.broadcastIntents.none {
                it.action == com.atrainingtracker.banalservice.BANALService.POLAR_LIVE_IMU_CHANGED
            }
        )

        shadowApp.clearBroadcastIntents()
        val afterRename = repo.getDeviceSnapshotById(h10Id)!!
        repo.updateDevice(afterRename.copy(polarLiveImuEnabled = true))
        val enableBroadcast = shadowApp.broadcastIntents.single {
            it.action == com.atrainingtracker.banalservice.BANALService.POLAR_LIVE_IMU_CHANGED
        }
        assertEquals(h10Id, enableBroadcast.getLongExtra(com.atrainingtracker.banalservice.BANALService.DEVICE_ID, -1L))
        assertTrue(enableBroadcast.getBooleanExtra(com.atrainingtracker.banalservice.BANALService.POLAR_LIVE_IMU_ENABLED, false))

        shadowApp.clearBroadcastIntents()
        val afterEnable = repo.getDeviceSnapshotById(h10Id)!!
        repo.updateDevice(afterEnable.copy(polarLiveImuEnabled = false))
        val disableBroadcast = shadowApp.broadcastIntents.single {
            it.action == com.atrainingtracker.banalservice.BANALService.POLAR_LIVE_IMU_CHANGED
        }
        assertEquals(h10Id, disableBroadcast.getLongExtra(com.atrainingtracker.banalservice.BANALService.DEVICE_ID, -1L))
        assertFalse(disableBroadcast.getBooleanExtra(com.atrainingtracker.banalservice.BANALService.POLAR_LIVE_IMU_ENABLED, true))
    }

    @Test
    fun testPolarLiveImuCard_uiToggleStatesAndApi33DisabledHint() {
        // TST-SET-114.2
        var toggledState = false
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = true) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PolarLiveImuCard(
                        checked = true,
                        isVeritySense = false,
                        isAndroid13Plus = true,
                        onCheckedChange = { toggledState = it }
                    )
                    PolarLiveImuCard(
                        checked = true,
                        isVeritySense = true,
                        isAndroid13Plus = false,
                        onCheckedChange = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.device_polar_h10_live_imu_desc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.device_polar_verity_live_imu_desc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.device_polar_live_imu_requires_android_13)).assertIsDisplayed()
        assertFalse(toggledState)
    }

    @Test
    fun testCapturePolarLiveImuToggleScreenshot() {
        // TST-SET-114.2
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = true) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("polar_live_imu_toggle_preview")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Polar H10 (Android 13+)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        PolarBackupRecordingCard(
                            checked = true,
                            isVeritySense = false,
                            isAndroid13Plus = true,
                            onCheckedChange = {}
                        )
                        PolarLiveImuCard(
                            checked = true,
                            isVeritySense = false,
                            isAndroid13Plus = true,
                            onCheckedChange = {}
                        )
                        Text(
                            text = "Polar Verity Sense (< Android 13)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        PolarLiveImuCard(
                            checked = false,
                            isVeritySense = true,
                            isAndroid13Plus = false,
                            onCheckedChange = {}
                        )
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("polar_live_imu_toggle_preview")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8016_polar_live_imu_toggle.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot file must exist", outFile.exists())
        assertTrue("Screenshot file size must be > 0", outFile.length() > 0L)
        assertTrue("Strings must be non-empty", stringResourceCheck())
    }

    private fun stringResourceCheck(): Boolean {
        return context.getString(R.string.device_polar_live_imu_title).isNotBlank() &&
            context.getString(R.string.device_polar_h10_live_imu_desc).isNotBlank() &&
            context.getString(R.string.device_polar_verity_live_imu_desc).isNotBlank() &&
            context.getString(R.string.device_polar_live_imu_requires_android_13).isNotBlank()
    }

    @Test
    fun testUpdateDevice_preRenameLegacyH10BackfillAndNonPolarSuppression_TST_CON_049_4() = runBlocking {
        // REQ-CON-049.3 / REQ-CON-049.4 / TST-CON-049.4
        val db = DevicesDatabaseManager.getInstance(context)
        val existingH10Mac = "AA:BB:CC:DD:EE:21"
        val genericHrmMac = "AA:BB:CC:DD:EE:22"
        val existingH10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, existingH10Mac, "Polar H10 1C3B2421", "Polar Electro", 95, true
        )
        val genericHrmId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, genericHrmMac, "My HR Sensor", "", 95, true
        )
        db.setPolarLiveImuEnabled(existingH10Id, true)
        db.setPolarLiveImuEnabled(genericHrmId, true)

        val app = context.applicationContext as Application
        val repo = DeviceDataRepository(app, Dispatchers.Unconfined, Dispatchers.Unconfined)

        // 1. Existing paired Polar H10 renamed before ever reconnecting retains H10 type + 8-hex ID
        val initialH10 = repo.getDeviceSnapshotById(existingH10Id)!!
        repo.updateDevice(initialH10.copy(deviceName = "Brustgurt"))
        assertEquals(PolarOfflineCapabilityStore.PolarDeviceType.H10, PolarOfflineCapabilityStore.getDeviceType(app, existingH10Mac))
        assertEquals("1C3B2421", PolarOfflineCapabilityStore.getPolarDeviceId(app, existingH10Mac))
        assertTrue(repo.isPolarLiveImuBridgeEligible(existingH10Id, existingH10Mac))
        assertTrue(PolarOfflineGattCoordinator.isPolarH10Device(app, "Brustgurt", "Polar Electro", existingH10Mac))
        assertTrue(PolarOfflineGattCoordinator.isPolarH10Device(app, "", "", existingH10Mac))

        // 2. Un-reconnected non-Polar HRM renamed to "Polar H10" stays suppressed even after save
        val initialGeneric = repo.getDeviceSnapshotById(genericHrmId)!!
        repo.updateDevice(initialGeneric.copy(deviceName = "Polar H10"))
        assertTrue(PolarOfflineCapabilityStore.isLegacyNameHeuristicSuppressed(app, genericHrmMac))
        assertFalse(repo.isPolarLiveImuBridgeEligible(genericHrmId, genericHrmMac))
        assertFalse(PolarOfflineGattCoordinator.isPolarH10Device(app, "Polar H10", "", genericHrmMac))

        // 3. Real GATT + DIS hardware truth supersedes suppression if that MAC later proves Polar
        PolarOfflineCapabilityStore.recordGattServiceCapabilities(app, genericHrmMac, hasPmdService = true, hasPftpService = true)
        PolarOfflineCapabilityStore.recordDisMetadata(app, genericHrmMac, modelNumber = "H10", serialNumber = "FACEB00C")
        assertFalse(PolarOfflineCapabilityStore.isLegacyNameHeuristicSuppressed(app, genericHrmMac))
        assertTrue(repo.isPolarLiveImuBridgeEligible(genericHrmId, genericHrmMac))
    }

    @Test
    fun testEditDevicePolarSection_nonPolarRenameExistingH10RenameAndEmptyNameVerity_TST_CON_049_4() {
        // REQ-CON-049.4 / REQ-CON-049.5 / REQ-PRO-132 / TST-CON-049.4
        val db = DevicesDatabaseManager.getInstance(context)
        val nonPolarMac = "AA:BB:CC:DD:EE:31"
        val existingH10Mac = "AA:BB:CC:DD:EE:32"
        val verityMac = "AA:BB:CC:DD:EE:33"

        val nonPolarId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, nonPolarMac, "HRM-Pro", "Garmin", 90, true)
        val existingH10Id = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, existingH10Mac, "Polar H10 99887766", "Polar Electro", 90, true)
        val verityId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, verityMac, "Armband", "", 90, true)

        PolarOfflineCapabilityStore.recordGattServiceCapabilities(context, nonPolarMac, hasPmdService = false, hasPftpService = false)
        // Legacy Verity Sense with offline_rec_ only (no polar_type_ yet)
        PolarOfflineCapabilityStore.markOfflineRecordingCapable(context, verityMac)

        val app = context.applicationContext as Application
        val repo = DeviceDataRepository(app, Dispatchers.Unconfined, Dispatchers.Unconfined)
        val vm = EditDeviceViewModel(
            application = app,
            devicesRepository = repo,
            banalServiceRepository = mockk<BANALServiceRepository>(relaxed = true),
            discoveryManager = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true),
            equipmentRepository = mockk<EquipmentRepository>(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined
        )

        composeTestRule.setContent {
            val snap by vm.deviceSnapshot.collectAsState()
            val persisted by vm.persistedDeviceIdentity.collectAsState()
            ATrainingTrackerTheme(darkTheme = false) {
                snap?.let { current ->
                    val mac = if (current.protocol == Protocol.BLUETOOTH_LE) vm.getBluetoothMacAddress(current.id) else null
                    PolarOfflineDeviceSectionContent(
                        data = current,
                        persistedIdentity = persisted ?: current,
                        polarMac = mac,
                        onOfflineRecordingChanged = vm::onPolarH10OfflineRecordingChanged,
                        onLiveImuEnabledChanged = vm::onPolarLiveImuEnabledChanged
                    )
                }
            }
        }

        // Case 1: Non-Polar HRM renamed live to "Polar H10" -> 0 Polar cards rendered
        vm.loadInitialDeviceData(nonPolarId)
        vm.onDeviceNameChanged("Polar H10")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.device_polar_h10_backup_recording_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.device_polar_live_imu_title)).assertDoesNotExist()

        // Case 2: Already-paired existing Polar H10 renamed live to "Brustgurt" -> H10 cards stay displayed
        vm.loadInitialDeviceData(existingH10Id)
        vm.onDeviceNameChanged("Brustgurt")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.device_polar_h10_backup_recording_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.device_polar_live_imu_title)).assertIsDisplayed()

        // Case 3: Polar Verity Sense with name cleared to "" -> Verity Sense cards stay displayed
        vm.loadInitialDeviceData(verityId)
        vm.onDeviceNameChanged("")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.device_polar_verity_backup_recording_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.device_polar_verity_live_imu_desc)).assertIsDisplayed()
    }

    @Test
    fun testLegacyOfflineRecOnlyVeritySenseRenamedToPolarH10_staysVerityNeverBleedsH10_TST_CON_049_4() = runBlocking {
        // REQ-CON-049.3 / REQ-CON-049.4 / TST-CON-049.4 (Stage 6 Bounce 1 Finding 2)
        val db = DevicesDatabaseManager.getInstance(context)
        val verityMac = "AA:BB:CC:DD:EE:51"
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Armband", "", 90, true
        )
        // Legacy pre-ATT-8717 Verity Sense with offline_rec_ only (no polar_type_, non-Verity custom name)
        PolarOfflineCapabilityStore.markOfflineRecordingCapable(context, verityMac)

        val app = context.applicationContext as Application
        val repo = DeviceDataRepository(app, Dispatchers.Unconfined, Dispatchers.Unconfined)
        val initial = repo.getDeviceSnapshotById(verityId)!!

        // Renaming "Armband" -> "Polar H10" must NEVER classify as H10; must preserve VERITY_SENSE
        repo.updateDevice(initial.copy(deviceName = "Polar H10"))

        assertEquals(
            PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE,
            PolarOfflineCapabilityStore.getDeviceType(app, verityMac)
        )
        assertFalse(
            "offline_rec_-only Verity Sense renamed 'Polar H10' must NOT classify as H10",
            PolarOfflineGattCoordinator.isPolarH10Device(app, "Polar H10", "", verityMac)
        )
        assertTrue(
            "offline_rec_-only Verity Sense renamed 'Polar H10' must stay Verity Sense",
            PolarOfflineGattCoordinator.isPolarVeritySenseDevice(app, "Polar H10", "", verityMac)
        )

        val renamedData = repo.getDeviceSnapshotById(verityId)!!
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                PolarOfflineDeviceSectionContent(
                    data = renamedData,
                    persistedIdentity = renamedData,
                    polarMac = verityMac,
                    onOfflineRecordingChanged = {},
                    onLiveImuEnabledChanged = {}
                )
            }
        }
        composeTestRule.onNodeWithText(context.getString(R.string.device_polar_verity_backup_recording_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.device_polar_h10_backup_recording_title)).assertDoesNotExist()
    }

    @Test
    fun testLoadAllDevices_throwingBackfillDoesNotAbortDeviceListLoad_TST_CON_049_3() = runBlocking {
        // REQ-CON-049.3 / TST-CON-049.3 (Stage 6 Bounce 1 Finding 3)
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Mac = "AA:BB:CC:DD:EE:52"
        val garminMac = "AA:BB:CC:DD:EE:53"
        val h10Id = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, h10Mac, "Polar H10 11223344", "Polar Electro", 90, true)
        db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, garminMac, "HRM-Pro", "Garmin", 85, true)

        val app = context.applicationContext as Application
        val spyRepo = spyk(DeviceDataRepository(app, Dispatchers.Unconfined, Dispatchers.Unconfined))
        every { spyRepo.backfillLegacyPolarIdentityIfNeeded(match { it.id == h10Id }) } throws
            IllegalStateException("Simulated SharedPreferences failure during backfill")

        spyRepo.loadAllDevices()

        assertTrue(
            "loadAllDevices must still populate all devices when backfill throws for one device",
            spyRepo.allDevices.value.size >= 2
        )
        assertTrue(spyRepo.allDevices.value.any { it.deviceName == "Polar H10 11223344" })
        assertTrue(spyRepo.allDevices.value.any { it.deviceName == "HRM-Pro" })
    }

    @Test
    @Config(sdk = [34], qualifiers = "w840dp-h840dp-xxhdpi")
    fun testCaptureAtt8949EditDevicePolarDetectionScreenshot_TST_CON_049_4() {
        // Rule 8.5 Light & Dark side-by-side visual verification of ATT-8949 positive & negative Polar detection states
        val h10Mac = "AA:BB:CC:DD:EE:41"
        val verityMac = "AA:BB:CC:DD:EE:42"
        val nonPolarMac = "AA:BB:CC:DD:EE:43"
        PolarOfflineCapabilityStore.recordDisMetadata(context, h10Mac, modelNumber = "H10", serialNumber = "1C3B2421")
        PolarOfflineCapabilityStore.recordGattServiceCapabilities(context, verityMac, hasPmdService = true, hasPftpService = true)
        PolarOfflineCapabilityStore.recordDisMetadata(context, verityMac, modelNumber = "Sense", serialNumber = "A1B2C3D4")
        PolarOfflineCapabilityStore.recordGattServiceCapabilities(context, nonPolarMac, hasPmdService = false, hasPftpService = false)

        val h10Renamed = DeviceUiData(
            id = 1L, protocol = Protocol.BLUETOOTH_LE, deviceType = DeviceType.HRM, lastSeen = "now",
            manufacturer = "Polar Electro", deviceName = "Brustgurt", isPaired = true,
            linkedEquipment = emptyList(), availableEquipment = emptyList(), powerFeaturesFlags = null,
            batteryPercentage = 95, linkedSportTypes = emptyList(), deviceTypeIconRes = 0,
            batteryStatusIconRes = 0, onEquipmentResId = 0, wheelCircumference = null,
            calibrationFactor = null, powerFeatures = null, baselineCircumference = null,
            polarH10OfflineRecording = true, polarLiveImuEnabled = true, isConnected = true,
            mainValue = "140 bpm", allValues = null
        )
        val verityEmptyName = h10Renamed.copy(id = 2L, deviceName = "", polarLiveImuEnabled = false)
        val nonPolarNamedH10 = h10Renamed.copy(id = 3L, deviceName = "Polar H10", manufacturer = "Garmin")

        composeTestRule.setContent {
            Row(modifier = Modifier.fillMaxWidth().testTag("att8949_polar_detection_gallery")) {
                listOf(false to "Light Theme", true to "Dark Theme").forEach { (dark, label) ->
                    ATrainingTrackerTheme(darkTheme = dark) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "$label — H10 Renamed & Verity Empty", style = MaterialTheme.typography.titleSmall)
                                PolarOfflineDeviceSectionContent(
                                    data = h10Renamed,
                                    persistedIdentity = h10Renamed,
                                    polarMac = h10Mac,
                                    onOfflineRecordingChanged = {},
                                    onLiveImuEnabledChanged = {}
                                )
                                PolarOfflineDeviceSectionContent(
                                    data = verityEmptyName,
                                    persistedIdentity = verityEmptyName,
                                    polarMac = verityMac,
                                    onOfflineRecordingChanged = {},
                                    onLiveImuEnabledChanged = {}
                                )
                                Text(
                                    text = "Non-Polar HRM renamed \"Polar H10\" (0 Polar cards below):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                PolarOfflineDeviceSectionContent(
                                    data = nonPolarNamedH10,
                                    persistedIdentity = nonPolarNamedH10,
                                    polarMac = nonPolarMac,
                                    onOfflineRecordingChanged = {},
                                    onLiveImuEnabledChanged = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("att8949_polar_detection_gallery")
            .captureToImage()
            .asAndroidBitmap()
        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8949_edit_device_polar_detection.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        assertTrue("ATT-8949 PNG screenshot must exist", outFile.exists() && outFile.length() > 0L)
    }
}
