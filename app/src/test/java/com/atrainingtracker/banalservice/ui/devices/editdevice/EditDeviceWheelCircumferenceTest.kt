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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WheelCircumferenceRecord
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.ui.equipment.WheelCircumferenceHistoryChart
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
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
 * Strictly offline Robolectric Compose UI, Repository SQLite, ViewModel async sync, and Light/Dark
 * visual verification suite for [EditDeviceDialog] wheel circumference cleanup and embedded
 * [WheelCircumferenceHistoryChart] (`REQ-SEN-033`, `TST-SEN-033.1`..`TST-SEN-033.3`, `ATT-8422`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h1200dp-xxhdpi")
class EditDeviceWheelCircumferenceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        EquipmentDbHelper.resetForTesting()
        EquipmentRepository.resetForTesting()
    }

    @After
    fun tearDown() {
        EquipmentRepository.resetForTesting()
        EquipmentDbHelper.resetForTesting()
    }

    // REQ-SEN-033.1 / TST-SEN-033.1: Bike sensor removes known-distance calculator, retains tire presets & auto-calibration hint, localizes baseline, and renders a single consolidated reset button
    @Test
    fun testWheelCircumferenceSection_removesBikeCalculatorPreservesPresetsAndAutoHint_tstSen033_1() {
        var updatedCircumference: Int? = null
        var resetClicks = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    WheelCircumferenceSection(
                        wheelCircumferenceMm = 2136,
                        baselineCircumferenceMeters = 2.136,
                        wheelSizeNames = listOf("700 x 25", "700 x 28", "700 x 32"),
                        wheelSizeValues = listOf("2105", "2136", "2155"),
                        historyRecords = listOf(
                            WheelCircumferenceRecord(1, 10, 1000L, WheelPosition.FRONT, 2136),
                            WheelCircumferenceRecord(2, 10, 2000L, WheelPosition.FRONT, 2130)
                        ),
                        onWheelCircumferenceChanged = { updatedCircumference = it },
                        onResetBaseline = { resetClicks++ }
                    )
                }
            }
        }

        // Known-distance calculator button must be absent for bike sensors
        composeTestRule
            .onAllNodesWithContentDescription(context.getString(R.string.edit_calibration_factor))
            .assertCountEquals(0)

        // Localized auto-calibration explanation and localized baseline readout must render
        composeTestRule
            .onNodeWithText(context.getString(R.string.devices_wheel_circumference_auto_hint))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.devices_wheel_baseline_format, 2136))
            .assertIsDisplayed()

        // Exactly ONE consolidated reset baseline button must exist
        composeTestRule
            .onAllNodesWithTag("wheel_circumference_reset_baseline_button")
            .assertCountEquals(1)

        // Tire-size preset dropdown remains interactive for newly paired sensors
        composeTestRule.onNodeWithTag("wheel_circumference_input").performClick()
        composeTestRule.onNodeWithText("700 x 32 (2155 mm)").performClick()
        assertEquals(2155, updatedCircumference)

        // Clicking unified reset baseline button triggers callback once
        composeTestRule.onNodeWithTag("wheel_circumference_reset_baseline_button").performClick()
        assertEquals(1, resetClicks)
    }

    // REQ-SEN-033.1 / TST-SEN-033.1: Run footpod calibration preserves calculator button and CorrectCalibrationDialog
    @Test
    fun testRunCalibrationFactorContent_preservesKnownDistanceCalculator_tstSen033_1() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    RunCalibrationFactorContent(
                        calibrationFactor = 1.02,
                        onCalibrationFactorChanged = {}
                    )
                }
            }
        }

        val calcButton = composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.edit_calibration_factor)
        )
        calcButton.assertIsDisplayed()
        calcButton.performClick()

        composeTestRule
            .onNodeWithText(context.getString(R.string.devices_correct_calibration_factor_title_run))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.devices_measuredDistanceText))
            .assertIsDisplayed()
    }

    // REQ-SEN-033.2 / TST-SEN-033.2: EquipmentRepository filters history per linked bike/position, sorts chronologically, handles UNSPECIFIED/unlinked gracefully, and persists manual baseline resets
    @Test
    fun testEquipmentRepository_getCircumferenceHistoryForDeviceAndManualReset_tstSen033_2() {
        val dbHelper = EquipmentDbHelper.getInstance(context)
        val repo = EquipmentRepository(context, dbHelper = dbHelper)

        // Unlinked sensor returns empty list gracefully
        assertTrue(repo.getCircumferenceHistoryForDevice(999L).isEmpty())
        assertTrue(repo.recordManualBaselineResetForDevice(999L, 2136).isEmpty())

        // Create Bike A (device 101=FRONT, device 102=REAR), Bike B (device 101=REAR), Bike C (device 103=UNSPECIFIED)
        val bikeA = dbHelper.addEquipment("Gravel Bike", 1, emptyList())
        val bikeB = dbHelper.addEquipment("Road Bike", 1, emptyList())
        val bikeC = dbHelper.addEquipment("Commuter Bike", 1, emptyList())

        dbHelper.addDeviceLink(bikeA, 101L, WheelPosition.FRONT)
        dbHelper.addDeviceLink(bikeA, 102L, WheelPosition.REAR)
        dbHelper.addDeviceLink(bikeB, 101L, WheelPosition.REAR)
        dbHelper.addDeviceLink(bikeC, 103L, WheelPosition.UNSPECIFIED)

        // Insert out-of-order records across bikes and positions
        dbHelper.addCircumferenceHistory(bikeA, 3000L, WheelPosition.FRONT, 2104, WheelCircumferenceRecord.SOURCE_GNSS_AUTO)
        dbHelper.addCircumferenceHistory(bikeA, 1500L, WheelPosition.REAR, 2090, WheelCircumferenceRecord.SOURCE_GNSS_AUTO) // belongs to device 102, not 101
        dbHelper.addCircumferenceHistory(bikeB, 1000L, WheelPosition.REAR, 2130, WheelCircumferenceRecord.SOURCE_GNSS_AUTO)
        dbHelper.addCircumferenceHistory(bikeA, 2000L, WheelPosition.FRONT, 2108, WheelCircumferenceRecord.SOURCE_GNSS_AUTO)
        dbHelper.addCircumferenceHistory(bikeC, 2500L, WheelPosition.UNSPECIFIED, 2150, WheelCircumferenceRecord.SOURCE_GNSS_AUTO)
        dbHelper.addCircumferenceHistory(bikeC, 1200L, WheelPosition.UNSPECIFIED, 2155, WheelCircumferenceRecord.SOURCE_GNSS_AUTO)

        // Device 101 gets Bike B REAR (t=1000) + Bike A FRONT (t=2000, t=3000), excluding Bike A REAR (2090)
        val history101 = repo.getCircumferenceHistoryForDevice(101L)
        assertEquals(listOf(1000L, 2000L, 3000L), history101.map { it.timestampMs })
        assertEquals(listOf(2130, 2108, 2104), history101.map { it.circumferenceMm })
        assertEquals(listOf(WheelPosition.REAR, WheelPosition.FRONT, WheelPosition.FRONT), history101.map { it.wheelPosition })

        // Device 103 with UNSPECIFIED link gets all Bike C records sorted chronologically
        val history103 = repo.getCircumferenceHistoryForDevice(103L)
        assertEquals(listOf(1200L, 2500L), history103.map { it.timestampMs })
        assertEquals(listOf(2155, 2150), history103.map { it.circumferenceMm })

        // Manual baseline reset records SOURCE_MANUAL_RESET for both bikes linked to device 101
        val inserted = repo.recordManualBaselineResetForDevice(101L, 2140, timestampMs = 4000L)
        assertEquals(2, inserted.size)
        val afterReset = repo.getCircumferenceHistoryForDevice(101L)
        assertEquals(5, afterReset.size)
        assertEquals(2, afterReset.count { it.timestampMs == 4000L && it.circumferenceMm == 2140 && it.source == WheelCircumferenceRecord.SOURCE_MANUAL_RESET })
    }

    // REQ-SEN-033.2 / TST-SEN-033.2: EditDeviceViewModel loads history asynchronously on ioDispatcher, syncs draft circumference on baseline reset, and clears stale history on non-wheel devices
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testEditDeviceViewModel_asyncHistoryLoadStaleClearAndDraftBaselineSync_tstSen033_2() {
        val app = context as Application
        val devicesRepo = mockk<DeviceDataRepository>(relaxed = true)
        val banalRepo = mockk<BANALServiceRepository>(relaxed = true)
        val discoveryMgr = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        val equipRepo = mockk<EquipmentRepository>(relaxed = true)

        val initialRecords = listOf(
            WheelCircumferenceRecord(1, 10, 1000L, WheelPosition.FRONT, 2105)
        )
        val refreshedRecords = initialRecords + WheelCircumferenceRecord(
            2, 10, 2000L, WheelPosition.FRONT, 2136, WheelCircumferenceRecord.SOURCE_MANUAL_RESET
        )

        val wheelSensor = DeviceUiData(
            id = 55L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.BIKE_SPEED,
            lastSeen = "Now",
            manufacturer = "Garmin",
            deviceName = "Front Speed",
            isPaired = true,
            linkedEquipment = listOf("Gravel Bike"),
            availableEquipment = listOf("Gravel Bike"),
            powerFeaturesFlags = null,
            batteryPercentage = 95,
            linkedSportTypes = listOf("Cycling"),
            deviceTypeIconRes = R.drawable.bt_bike_spd,
            batteryStatusIconRes = R.drawable.ic_battery_full,
            onEquipmentResId = R.string.devices_on_bikes_text,
            wheelCircumference = 2105,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = false,
            mainValue = null,
            allValues = null,
            baselineCircumference = 2.105
        )
        val hrmSensor = DeviceUiData(
            id = 77L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.HRM,
            lastSeen = "Now",
            manufacturer = "Polar",
            deviceName = "Polar H10",
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 95,
            linkedSportTypes = emptyList(),
            deviceTypeIconRes = R.drawable.bt_hr,
            batteryStatusIconRes = R.drawable.ic_battery_full,
            onEquipmentResId = R.string.devices_on_equipment_text,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = false,
            mainValue = null,
            allValues = null
        )

        every { devicesRepo.getDeviceSnapshotById(55L) } returns wheelSensor
        every { devicesRepo.getDeviceSnapshotById(77L) } returns hrmSensor
        every { equipRepo.getCircumferenceHistoryForDevice(55L) } returnsMany listOf(initialRecords, refreshedRecords)
        every { equipRepo.recordManualBaselineResetForDevice(55L, 2136, any()) } returns listOf(2L)
        coEvery { devicesRepo.resetTirePressureBaseline(55L, 2136) } returns Unit

        val viewModel = EditDeviceViewModel(
            application = app,
            devicesRepository = devicesRepo,
            banalServiceRepository = banalRepo,
            discoveryManager = discoveryMgr,
            equipmentRepository = equipRepo,
            ioDispatcher = UnconfinedTestDispatcher()
        )

        // 1. Load wheel sensor -> populates history asynchronously
        viewModel.loadInitialDeviceData(55L)
        assertEquals(initialRecords, viewModel.wheelCircumferenceHistory.value)

        // 2. Edit draft circumference and tap Reset Baseline -> staged in draft only (0 DB writes until Save; REQ-UI-2799.2)
        viewModel.onWheelCircumferenceChanged(2136)
        viewModel.resetTirePressureBaseline()

        assertEquals(2.136, viewModel.deviceSnapshot.value?.baselineCircumference ?: 0.0, 1e-6)
        coVerify(exactly = 0) { devicesRepo.resetTirePressureBaseline(any(), any()) }
        verify(exactly = 0) { equipRepo.recordManualBaselineResetForDevice(any(), any(), any()) }

        // 2b. Save commits both draft circumference and baseline reset -> both DBs receive 2136 and history refreshes
        viewModel.saveChanges()

        coVerify(exactly = 1) { devicesRepo.resetTirePressureBaseline(55L, 2136) }
        verify(exactly = 1) { equipRepo.recordManualBaselineResetForDevice(55L, 2136, any()) }
        assertEquals(refreshedRecords, viewModel.wheelCircumferenceHistory.value)
        assertEquals(2.136, viewModel.deviceSnapshot.value?.baselineCircumference ?: 0.0, 1e-6)

        // 3. Switching to a non-wheel device clears stale history cleanly
        viewModel.loadInitialDeviceData(77L)
        assertTrue(viewModel.wheelCircumferenceHistory.value.isEmpty())
    }

    // REQ-SEN-033.3 / TST-SEN-033.3: WheelCircumferenceHistoryChart renders conditional UNSPECIFIED legend & sensor empty state without regressing Front/Rear parity
    @Test
    fun testWheelCircumferenceHistoryChart_unspecifiedLegendAndSensorEmptyState_tstSen033_3() {
        // 1. Front/Rear records must NOT show Unspecified legend (preserves EditEquipmentDialog parity)
        val frontRearOnly = listOf(
            WheelCircumferenceRecord(1, 10, 1000L, WheelPosition.FRONT, 2105),
            WheelCircumferenceRecord(2, 10, 2000L, WheelPosition.REAR, 2102)
        )
        // 2. Unspecified records MUST show Unspecified legend and non-empty chart
        val unspecifiedRecords = listOf(
            WheelCircumferenceRecord(3, 10, 1000L, WheelPosition.UNSPECIFIED, 2140),
            WheelCircumferenceRecord(4, 10, 2000L, WheelPosition.UNSPECIFIED, 2134)
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.testTag("front_rear_chart_host")) {
                        WheelCircumferenceHistoryChart(
                            historyRecords = frontRearOnly,
                            onResetBaseline = {}
                        )
                    }
                    Column(modifier = Modifier.testTag("unspecified_chart_host")) {
                        WheelCircumferenceHistoryChart(
                            historyRecords = unspecifiedRecords,
                            onResetBaseline = {}
                        )
                    }
                    Column(modifier = Modifier.testTag("empty_sensor_chart_host")) {
                        WheelCircumferenceHistoryChart(
                            historyRecords = emptyList(),
                            onResetBaseline = {},
                            emptyStateText = context.getString(R.string.devices_wheel_history_empty)
                        )
                    }
                }
            }
        }

        // Unspecified legend appears exactly once (only for the unspecified chart, not Front/Rear or empty)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.equipment_tpms_unspecified_legend))
            .assertCountEquals(1)
        // Sensor-specific empty state text renders in empty chart
        composeTestRule
            .onNodeWithText(context.getString(R.string.devices_wheel_history_empty))
            .assertIsDisplayed()
    }

    // REQ-SEN-033.3 / TST-SEN-033.3: Rule 8.5 Light & Dark PNG visual capture of WheelCircumferenceSection for EditDeviceDialog
    @Test
    fun testLightDarkWheelCircumferenceSection_renderAndCapturePng_rule85_tstSen033_3() {
        val sampleHistory = listOf(
            WheelCircumferenceRecord(1, 10, 1000L, WheelPosition.FRONT, 2136),
            WheelCircumferenceRecord(2, 10, 2000L, WheelPosition.FRONT, 2134),
            WheelCircumferenceRecord(3, 10, 3000L, WheelPosition.FRONT, 2130),
            WheelCircumferenceRecord(4, 10, 4000L, WheelPosition.FRONT, 2124),
            WheelCircumferenceRecord(5, 10, 5000L, WheelPosition.FRONT, 2136, WheelCircumferenceRecord.SOURCE_MANUAL_RESET)
        )

        composeTestRule.setContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("WheelCircumferenceEditDevicePreviewRoot"),
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
                                text = "Light — Wheel Circumference in EditDeviceDialog",
                                style = MaterialTheme.typography.titleMedium
                            )
                            WheelCircumferenceSection(
                                wheelCircumferenceMm = 2136,
                                baselineCircumferenceMeters = 2.136,
                                wheelSizeNames = listOf("700 x 25", "700 x 28", "700 x 32"),
                                wheelSizeValues = listOf("2105", "2136", "2155"),
                                historyRecords = sampleHistory,
                                onWheelCircumferenceChanged = {},
                                onResetBaseline = {}
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
                                text = "Dark — Wheel Circumference in EditDeviceDialog",
                                style = MaterialTheme.typography.titleMedium
                            )
                            WheelCircumferenceSection(
                                wheelCircumferenceMm = 2136,
                                baselineCircumferenceMeters = 2.136,
                                wheelSizeNames = listOf("700 x 25", "700 x 28", "700 x 32"),
                                wheelSizeValues = listOf("2105", "2136", "2155"),
                                historyRecords = sampleHistory,
                                onWheelCircumferenceChanged = {},
                                onResetBaseline = {}
                            )
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("WheelCircumferenceEditDevicePreviewRoot")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8422_wheel_circumference_edit_device.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Captured Light/Dark preview PNG must exist and be non-empty", outFile.exists() && outFile.length() > 1000L)
        assertTrue("Captured PNG width must be > 100px (was ${bmp.width})", bmp.width > 100)
        assertTrue("Captured PNG height must be > 100px (was ${bmp.height})", bmp.height > 100)
    }

    // REQ-SEN-033.1 / TST-SEN-033.1: Stage 6 Bounce 1 regression — IEEE-754 2.010 m baseline rounds cleanly to 2010 mm (not truncated to 2009 mm)
    @Test
    fun testBaselineCircumferenceRounding_avoidsIeee754TruncationAt2010mm_tstSen033_1() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    WheelCircumferenceSection(
                        wheelCircumferenceMm = 2010,
                        baselineCircumferenceMeters = 2.010,
                        wheelSizeNames = listOf("700 x 25"),
                        wheelSizeValues = listOf("2010"),
                        historyRecords = emptyList(),
                        onWheelCircumferenceChanged = {},
                        onResetBaseline = {}
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.devices_wheel_baseline_format, 2010))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.devices_wheel_baseline_format, 2009))
            .assertCountEquals(0)
    }

    // REQ-SEN-033.3 / TST-SEN-033.3: Stage 6 Bounce 1 regression — narrow 280dp container wraps 3-legend FlowRow (Front/Rear/Unspecified) without clipping or zero-width collapse
    @Test
    fun testNarrow280dpThreeLegendFlowRow_wrapsWithoutClippingOrZeroWidth_tstSen033_3() {
        val mixedRecords = listOf(
            WheelCircumferenceRecord(1, 10, 1000L, WheelPosition.FRONT, 2105),
            WheelCircumferenceRecord(2, 10, 2000L, WheelPosition.REAR, 2102),
            WheelCircumferenceRecord(3, 10, 3000L, WheelPosition.UNSPECIFIED, 2100)
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface(modifier = Modifier.width(280.dp)) {
                    WheelCircumferenceHistoryChart(
                        historyRecords = mixedRecords,
                        onResetBaseline = {}
                    )
                }
            }
        }

        val legendLabels = listOf(
            context.getString(R.string.equipment_tpms_front_legend),
            context.getString(R.string.equipment_tpms_rear_legend),
            context.getString(R.string.equipment_tpms_unspecified_legend)
        )
        for (label in legendLabels) {
            val node = composeTestRule.onNodeWithText(label)
            node.assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("Legend '$label' must have positive width (was ${bounds.right - bounds.left})", (bounds.right - bounds.left) > 0.dp)
            assertTrue("Legend '$label' must fit within 280.dp container (right=${bounds.right})", bounds.right <= 280.dp)
        }
    }
}
