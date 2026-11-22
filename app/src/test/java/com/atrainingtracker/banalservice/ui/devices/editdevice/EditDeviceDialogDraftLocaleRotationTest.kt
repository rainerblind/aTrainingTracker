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
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.AttFusedAltitudePreferences
import com.atrainingtracker.banalservice.devices.AttFusedPreferences
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterBottomSheetBody
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Behavioral Robolectric Compose UI & ViewModel test suite verifying:
 * 1. `de_DE` comma & dot decimal calibration input + local buffer + rotation survival (`REQ-UI-2799.2`).
 * 2. Rotation-safe dirty draft preservation in [EditDeviceViewModel.loadInitialDeviceData].
 * 3. Save-only persistence & Cancel zero-write invariant for Name, Calibration, ATT Odometry Fusion,
 *    ATT Altitude Fusion, TPMS baseline reset, and Radar tail light options.
 * 4. `Rule 8.5` Light/Dark visual PNG capture for `EditDeviceDialog` calibration & `FilterBottomSheetBody`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class EditDeviceDialogDraftLocaleRotationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        app = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit()
        RadarLightController.reset()
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
        RadarLightController.reset()
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit()
        unmockkAll()
    }

    @Test
    fun testParseLocaleDecimal_acceptsCommaAndDotAndRejectsInvalid_tstUi2799_2() {
        assertEquals(1.0, parseLocaleDecimal("1,0000")!!, 1e-9)
        assertEquals(1.085, parseLocaleDecimal("1,085")!!, 1e-9)
        assertEquals(1.125, parseLocaleDecimal(" 1.125 ")!!, 1e-9)
        assertNull(parseLocaleDecimal(""))
        assertNull(parseLocaleDecimal("abc"))
        assertNull(parseLocaleDecimal("1,2,3"))
        assertNull(parseLocaleDecimal("NaN"))
        assertNull(parseLocaleDecimal("Infinity"))
    }

    @Test
    fun testRunCalibrationFactor_germanLocaleAcceptsCommaAndDotAndSurvivesRestoration_tstUi2799_2() {
        Locale.setDefault(Locale.GERMANY)
        var currentFactor by mutableDoubleStateOf(1.0)
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    RunCalibrationFactorContent(
                        calibrationFactor = currentFactor,
                        onCalibrationFactorChanged = { currentFactor = it }
                    )
                }
            }
        }

        // Initial render in de_DE formats with comma
        composeTestRule.onNodeWithTag("run_calibration_factor_input")
            .assertIsDisplayed()
            .assertTextContains("1,0000")

        // User enters German comma decimal -> accepted!
        composeTestRule.onNodeWithTag("run_calibration_factor_input")
            .performTextReplacement("1,085")
        composeTestRule.waitForIdle()
        assertEquals(1.085, currentFactor, 1e-6)
        composeTestRule.onNodeWithTag("run_calibration_factor_input")
            .assertTextContains("1,085")

        // Simulate device rotation via StateRestorationTester -> unsaved buffer preserved!
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("run_calibration_factor_input")
            .assertTextContains("1,085")
        assertEquals(1.085, currentFactor, 1e-6)

        // Dot decimal is also accepted under de_DE locale
        composeTestRule.onNodeWithTag("run_calibration_factor_input")
            .performTextReplacement("1.125")
        composeTestRule.waitForIdle()
        assertEquals(1.125, currentFactor, 1e-6)

        // Calculator dialog also parses comma decimals cleanly under de_DE
        composeTestRule.onNodeWithContentDescription(app.getString(R.string.edit_calibration_factor))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(app.getString(R.string.devices_measuredDistanceText))
            .performTextReplacement("10,0")
        composeTestRule.onNodeWithText(app.getString(R.string.devices_correctDistanceText))
            .performTextReplacement("10,5")
        composeTestRule.onNodeWithText(app.getString(R.string.OK)).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1.125 * 1.05, currentFactor, 1e-5)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testViewModel_rotationPreservesDirtyDraftsAndCancelPersistsZeroWrites_tstUi2799_2() {
        val devicesRepo = mockk<DeviceDataRepository>(relaxed = true)
        val banalRepo = mockk<BANALServiceRepository>(relaxed = true)
        val discoveryMgr = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        val equipRepo = mockk<EquipmentRepository>(relaxed = true)

        val runFootpod = sampleDevice(id = 10L, type = DeviceType.RUN_SPEED, name = "Stryd", calibrationFactor = 1.0)
        val fusedOdo = sampleDevice(id = 11L, type = DeviceType.SPEED_AND_LOCATION_ATT_FUSED, name = "ATT Fused")
        val fusedAlt = sampleDevice(id = 12L, type = DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED, name = "ATT Alt")
        val bikeWheel = sampleDevice(id = 13L, type = DeviceType.BIKE_SPEED, name = "Speed 2", wheelCircumference = 2105, baselineCircumference = 2.105)
        val radar = sampleDevice(id = 14L, type = DeviceType.BIKE_RADAR, name = "Varia RTL515")

        every { devicesRepo.getDeviceSnapshotById(10L) } returns runFootpod
        every { devicesRepo.getDeviceSnapshotById(11L) } returns fusedOdo
        every { devicesRepo.getDeviceSnapshotById(12L) } returns fusedAlt
        every { devicesRepo.getDeviceSnapshotById(13L) } returns bikeWheel
        every { devicesRepo.getDeviceSnapshotById(14L) } returns radar
        every { equipRepo.getCircumferenceHistoryForDevice(any()) } returns emptyList()

        val vm = EditDeviceViewModel(
            application = app,
            devicesRepository = devicesRepo,
            banalServiceRepository = banalRepo,
            discoveryManager = discoveryMgr,
            equipmentRepository = equipRepo,
            ioDispatcher = UnconfinedTestDispatcher()
        )

        // 1. Run Footpod: edit name + calibration -> simulate rotation (re-call loadInitialDeviceData) -> preserved -> Cancel -> 0 writes
        vm.loadInitialDeviceData(10L)
        vm.onDeviceNameChanged("Stryd Rotated")
        vm.onCalibrationFactorChanged(1.045)
        // Simulated LaunchedEffect(deviceId) firing on screen rotation
        vm.loadInitialDeviceData(10L)
        assertEquals("Stryd Rotated", vm.deviceSnapshot.value?.deviceName)
        assertEquals(1.045, vm.deviceSnapshot.value?.calibrationFactor ?: 0.0, 1e-9)
        // Cancel discards cleanly with 0 DB writes
        vm.discardDraft()
        coVerify(exactly = 0) { devicesRepo.updateDevice(any()) }

        // 2. ATT Odometry Fusion: edit toggles -> rotation preserves -> Cancel writes ZERO to SharedPreferences -> Save writes!
        AttFusedPreferences.setGnssEnabled(app, true)
        AttFusedPreferences.setAutoCalibEnabled(app, false)
        vm.loadInitialDeviceData(11L)
        vm.onAttFusedConfigChanged { it.copy(gnssEnabled = false, autoCalibEnabled = true) }
        vm.loadInitialDeviceData(11L) // simulated rotation
        assertEquals(false, vm.attFusedDraft.value?.gnssEnabled)
        assertEquals(true, vm.attFusedDraft.value?.autoCalibEnabled)
        // SharedPreferences must remain untouched before Save
        assertTrue(AttFusedPreferences.isGnssEnabled(app))
        assertFalse(AttFusedPreferences.isAutoCalibEnabled(app))
        // Cancel discards with zero persistence
        vm.discardDraft()
        assertTrue(AttFusedPreferences.isGnssEnabled(app))
        assertFalse(AttFusedPreferences.isAutoCalibEnabled(app))
        // Reopen -> edit -> Save persists!
        vm.loadInitialDeviceData(11L)
        vm.onAttFusedConfigChanged { it.copy(gnssEnabled = false, autoCalibEnabled = true) }
        vm.saveChanges()
        assertFalse(AttFusedPreferences.isGnssEnabled(app))
        assertTrue(AttFusedPreferences.isAutoCalibEnabled(app))

        // 3. ATT Altitude Fusion: edit toggles -> rotation preserves -> Cancel writes ZERO -> Save writes!
        AttFusedAltitudePreferences.setBarometerEnabled(app, true)
        AttFusedAltitudePreferences.setDemEnabled(app, false)
        vm.loadInitialDeviceData(12L)
        vm.onAttFusedAltitudeConfigChanged { it.copy(barometerEnabled = false, demEnabled = true) }
        vm.loadInitialDeviceData(12L) // simulated rotation
        assertEquals(false, vm.attFusedAltitudeDraft.value?.barometerEnabled)
        assertEquals(true, vm.attFusedAltitudeDraft.value?.demEnabled)
        assertTrue(AttFusedAltitudePreferences.isBarometerEnabled(app))
        assertFalse(AttFusedAltitudePreferences.isDemEnabled(app))
        vm.discardDraft()
        assertTrue(AttFusedAltitudePreferences.isBarometerEnabled(app))
        assertFalse(AttFusedAltitudePreferences.isDemEnabled(app))
        vm.loadInitialDeviceData(12L)
        vm.onAttFusedAltitudeConfigChanged { it.copy(barometerEnabled = false, demEnabled = true) }
        vm.saveChanges()
        assertFalse(AttFusedAltitudePreferences.isBarometerEnabled(app))
        assertTrue(AttFusedAltitudePreferences.isDemEnabled(app))

        // 4. TPMS Baseline Reset: staged in draft -> rotation preserves -> Cancel writes ZERO to Devices/Equipment DB
        vm.loadInitialDeviceData(13L)
        vm.onWheelCircumferenceChanged(2140)
        vm.resetTirePressureBaseline()
        vm.loadInitialDeviceData(13L) // simulated rotation
        assertEquals(2140, vm.deviceSnapshot.value?.wheelCircumference)
        assertEquals(2.140, vm.deviceSnapshot.value?.baselineCircumference ?: 0.0, 1e-6)
        vm.discardDraft()
        coVerify(exactly = 0) { devicesRepo.resetTirePressureBaseline(any(), any()) }
        verify(exactly = 0) { equipRepo.recordManualBaselineResetForDevice(any(), any(), any()) }

        // 5. Radar Tail Light: staged in draft -> rotation preserves -> Cancel writes ZERO -> Save commits!
        RadarLightController.updateCachedPreferences(autoMode = true, defaultMode = RadarLightMode.SOLID)
        vm.loadInitialDeviceData(14L)
        vm.onRadarLightSelectionChanged(isAutoMode = false, mode = RadarLightMode.DAY_FLASH)
        vm.loadInitialDeviceData(14L) // simulated rotation
        assertEquals(RadarLightDraftSelection(false, RadarLightMode.DAY_FLASH), vm.radarLightDraft.value)
        assertTrue(RadarLightController.isAutoMode.value)
        assertEquals(RadarLightMode.SOLID, RadarLightController.preferredMode.value)
        vm.discardDraft()
        assertTrue(RadarLightController.isAutoMode.value)
        assertEquals(RadarLightMode.SOLID, RadarLightController.preferredMode.value)
        vm.loadInitialDeviceData(14L)
        vm.onRadarLightSelectionChanged(isAutoMode = false, mode = RadarLightMode.DAY_FLASH)
        vm.saveChanges()
        assertFalse(RadarLightController.isAutoMode.value)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.preferredMode.value)
    }

    @Test
    @Config(qualifiers = "w920dp-h1000dp-xxhdpi")
    fun testCaptureLightAndDarkVisualEvidencePng_Rule8_5_tstUi2799_4() {
        Locale.setDefault(Locale.GERMANY)
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(860.dp)
                    .background(Color(0xFF78909C))
                    .padding(12.dp)
                    .testTag("Att8125VisualPreviewRoot"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Light — EditDevice (de_DE) & FilterSheet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RunCalibrationFactorContent(
                                calibrationFactor = 1.085,
                                onCalibrationFactorChanged = {}
                            )
                            FilterBottomSheetBody(
                                title = "Filter",
                                onDismissRequest = {},
                                onClearAll = {},
                                onApply = {}
                            ) {
                                OutlinedTextField(
                                    value = "Alpenpass",
                                    onValueChange = {},
                                    label = { Text("Suche") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Dark — EditDevice (de_DE) & FilterSheet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RunCalibrationFactorContent(
                                calibrationFactor = 1.085,
                                onCalibrationFactorChanged = {}
                            )
                            FilterBottomSheetBody(
                                title = "Filter",
                                onDismissRequest = {},
                                onClearAll = {},
                                onApply = {}
                            ) {
                                OutlinedTextField(
                                    value = "Alpenpass",
                                    onValueChange = {},
                                    label = { Text("Suche") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("Att8125VisualPreviewRoot")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering").exists()) File("..") else File(".")
        val outFile = File(
            repoRoot,
            "docs/engineering/test_evidence/tickets/ATT-8125/screenshots/att8125_edit_device_and_filter_light_dark.png"
        )
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Screenshot PNG must exist and be non-empty", outFile.exists() && outFile.length() > 1000L)
        assertTrue("Screenshot width must be > 100px", bmp.width > 100)
        assertTrue("Screenshot height must be > 100px", bmp.height > 100)
    }

    private fun sampleDevice(
        id: Long,
        type: DeviceType,
        name: String,
        calibrationFactor: Double? = null,
        wheelCircumference: Int? = null,
        baselineCircumference: Double? = null
    ) = DeviceUiData(
        id = id,
        protocol = Protocol.BLUETOOTH_LE,
        deviceType = type,
        lastSeen = "Now",
        manufacturer = "Test",
        deviceName = name,
        isPaired = true,
        linkedEquipment = emptyList(),
        availableEquipment = emptyList(),
        powerFeaturesFlags = null,
        batteryPercentage = 95,
        linkedSportTypes = emptyList(),
        deviceTypeIconRes = R.drawable.bt_bike_spd,
        batteryStatusIconRes = R.drawable.ic_battery_full,
        onEquipmentResId = R.string.devices_on_equipment_text,
        wheelCircumference = wheelCircumference,
        calibrationFactor = calibrationFactor,
        powerFeatures = null,
        isConnected = false,
        mainValue = null,
        allValues = null,
        baselineCircumference = baselineCircumference
    )
}
