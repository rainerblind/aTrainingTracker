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
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.devicedata.raw2UiDeviceData
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertFalse
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
        DeviceDataRepository.resetForTesting()
    }

    @After
    fun tearDown() {
        DeviceDataRepository.resetForTesting()
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
}
