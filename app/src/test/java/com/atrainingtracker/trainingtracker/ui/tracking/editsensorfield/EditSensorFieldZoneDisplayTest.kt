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

package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.settings.ZoneDisplayOptionsDraft
import com.atrainingtracker.trainingtracker.ui.settings.ZoneProfileTarget
import com.atrainingtracker.trainingtracker.ui.settings.resolveZoneProfileTarget
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.CockpitTypographySection
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldConfig
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldVariant
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Unit, DataStore, and Compose UI verification for Expert Settings Zone Display removal
 * and sensor-tile long-press [ZoneDisplayOptions] editing in [EditSensorFieldDialog]
 * (`REQ-UI-2783.1`, `REQ-UI-2783.2`, `TST-UI-2783.1`, `TST-UI-2783.2`).
 */
// Traces to: REQ-UI-2783, TST-UI-2783.1, TST-UI-2783.2
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h1600dp-xhdpi")
class EditSensorFieldZoneDisplayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testResolveZoneProfileTarget_matchesTrackingViewModelMapping() {
        assertEquals(ZoneProfileTarget.HR_RUN, resolveZoneProfileTarget(SensorType.HR, BSportType.RUN))
        assertEquals(ZoneProfileTarget.HR_BIKE, resolveZoneProfileTarget(SensorType.HR, BSportType.BIKE))
        assertEquals(ZoneProfileTarget.PWR_BIKE, resolveZoneProfileTarget(SensorType.POWER, BSportType.BIKE))
        assertEquals(ZoneProfileTarget.HRV_DFA_A1, resolveZoneProfileTarget(SensorType.HRV_DFA_A1, BSportType.RUN))
        assertEquals(ZoneProfileTarget.HRV_DFA_A1, resolveZoneProfileTarget(SensorType.HRV_DFA_A1, BSportType.BIKE))
        assertEquals(ZoneProfileTarget.HRV_DFA_A1, resolveZoneProfileTarget(SensorType.HRV_DFA_A1, BSportType.UNKNOWN))

        assertNull(resolveZoneProfileTarget(SensorType.HR, BSportType.UNKNOWN))
        assertNull(resolveZoneProfileTarget(SensorType.POWER, BSportType.RUN))
        assertNull(resolveZoneProfileTarget(SensorType.SPEED_mps, BSportType.BIKE))
        assertNull(resolveZoneProfileTarget(SensorType.CADENCE, BSportType.RUN))
        assertNull(resolveZoneProfileTarget(null, BSportType.BIKE))
    }

    @Test
    fun testCockpitTypographySection_noLongerRendersZoneDisplayControls() {
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                CockpitTypographySection(
                    cockpitFontFamily = CockpitFontFamily.SYSTEM_DEFAULT,
                    onFontFamilyChange = {},
                    cockpitFontWeight = CockpitFontWeight.BOLD,
                    onFontWeightChange = {},
                    sensorFieldVariant = SensorFieldVariant.ELEVATED_CARDS,
                    sensorFieldCornerRadius = 12f,
                    sensorFieldBorderThickness = 1f,
                    sensorFieldBorderContrast = 0.2f
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_apply_all)).assertDoesNotExist()
    }

    @Test
    fun testEditSensorFieldDialog_showsZoneDisplayForZoneCapableMetrics_andHidesForNonZone() {
        val stateFlow = MutableStateFlow(
            EditDialogUiState(
                selectedSensorType = SensorType.HR,
                sportType = BSportType.RUN,
                availableSensorTypesForCurrentActivityType = listOf(SensorType.HR, SensorType.SPEED_mps, SensorType.HRV_DFA_A1),
                availableDevices = listOf(-1L to "Best Sensor"),
                showValue = true,
                showGraph = false
            )
        )
        val viewModel = mockk<EditSensorFieldViewModel>(relaxed = true)
        every { viewModel.uiState } returns stateFlow

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                EditSensorFieldDialog(
                    title = context.getString(R.string.edit_field),
                    viewModel = viewModel,
                    onDismissRequest = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. HR + RUN -> shows HR_RUN Zone Display without HRV gauge toggle
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_title)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.tab_hr_run)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_gauge)).assertDoesNotExist()
        assertEquals(4, composeTestRule.onAllNodes(isToggleable()).fetchSemanticsNodes().size)

        // Toggle text color -> calls ViewModel callback
        composeTestRule.onAllNodes(isToggleable())[3].performScrollTo().performClick()
        verify(exactly = 1) {
            viewModel.onZoneDisplayOptionsChanged(
                ZoneProfileTarget.HR_RUN,
                ZoneDisplayOptions(showTextColor = true)
            )
        }

        // Apply to all -> calls ViewModel apply-all callback
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_apply_all)).performScrollTo().performClick()
        verify(exactly = 1) { viewModel.onApplyZoneDisplayToAll(ZoneProfileTarget.HR_RUN) }

        // 2. HRV_DFA_A1 -> shows 5th gauge toggle
        stateFlow.value = stateFlow.value.copy(selectedSensorType = SensorType.HRV_DFA_A1)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.tab_hrv_dfa_a1)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_gauge)).performScrollTo().assertIsDisplayed()
        assertEquals(5, composeTestRule.onAllNodes(isToggleable()).fetchSemanticsNodes().size)

        // 3. SPEED_mps -> hides Zone Display section completely
        stateFlow.value = stateFlow.value.copy(selectedSensorType = SensorType.SPEED_mps)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.zone_display_apply_all)).assertDoesNotExist()
    }

    @Test
    fun testEditSensorFieldViewModel_cancelDiscards_savePersistsAndPreservesThresholds() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dataStore = SettingsDataStore.getInstance(app)
        val customThresholds = listOf(122, 136, 150, 166)
        runBlocking {
            dataStore.applyLatestZoneBoundaries(SettingsDataStore.ZoneType.HR_BIKE, customThresholds)
            ZoneDisplayOptionsDraft(
                hrv = ZoneDisplayOptions(showGauge = true)
            ).persistTo(dataStore)
        }

        val trackingRepo = mockk<TrackingViewsRepository>(relaxed = true)
        val banalRepo = mockk<BANALServiceRepository>(relaxed = true)
        val initialField = SensorFieldConfig(
            sensorFieldId = 10L,
            rowNr = 0,
            colNr = 0,
            sensorType = SensorType.HR,
            sourceDeviceId = -1L,
            sourceDeviceName = null,
            viewSize = ViewSize.NORMAL,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0
        )
        every { trackingRepo.getSensorFieldConfig(10L) } returns flowOf(initialField)
        every { trackingRepo.getSensorFieldConfigsForView(1L) } returns flowOf(listOf(initialField))

        // 1. Cancel without saveChanges() -> DataStore remains unchanged
        val cancelVm = EditSensorFieldViewModel(
            application = app,
            trackingViewsRepository = trackingRepo,
            banalServiceRepository = banalRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = 10L,
            tabViewId = 1L,
            rowNr = 0,
            colNr = 0,
            tuningConfigFlow = flowOf(TuningConfig()),
            settingsDataStore = dataStore
        )
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.idleMainLooper()
            cancelVm.uiState.value.selectedSensorType == SensorType.HR &&
                cancelVm.uiState.value.zoneDisplayDraft.hrv.showGauge
        }
        cancelVm.onZoneDisplayOptionsChanged(
            ZoneProfileTarget.HR_BIKE,
            ZoneDisplayOptions(showBackground = false, showLeftBar = false, showRightBar = true, showTextColor = true)
        )
        val afterCancel = runBlocking { dataStore.snapshotFlow.first() }
        assertTrue(afterCancel.hrBikeDisplayOptions.showBackground)
        assertFalse(afterCancel.hrBikeDisplayOptions.showTextColor)

        // 2. Save -> persists updated profile & apply-to-all (preserving hrv.showGauge and thresholds)
        val saveVm = EditSensorFieldViewModel(
            application = app,
            trackingViewsRepository = trackingRepo,
            banalServiceRepository = banalRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = 10L,
            tabViewId = 1L,
            rowNr = 0,
            colNr = 0,
            tuningConfigFlow = flowOf(TuningConfig()),
            settingsDataStore = dataStore
        )
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.idleMainLooper()
            saveVm.uiState.value.selectedSensorType == SensorType.HR &&
                saveVm.uiState.value.zoneDisplayDraft.hrv.showGauge
        }
        saveVm.onZoneDisplayOptionsChanged(
            ZoneProfileTarget.HR_BIKE,
            ZoneDisplayOptions(showBackground = false, showLeftBar = false, showRightBar = true, showTextColor = true)
        )
        saveVm.onApplyZoneDisplayToAll(ZoneProfileTarget.HR_BIKE)
        saveVm.saveChanges()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.idleMainLooper()
            runBlocking { dataStore.snapshotFlow.first().hrRunDisplayOptions.showTextColor }
        }
        val afterSave = runBlocking { dataStore.snapshotFlow.first() }
        assertFalse(afterSave.hrRunDisplayOptions.showBackground)
        assertTrue(afterSave.hrRunDisplayOptions.showRightBar)
        assertTrue(afterSave.pwrBikeDisplayOptions.showTextColor)
        assertTrue(afterSave.hrvDisplayOptions.showTextColor)
        assertTrue("HRV showGauge must be preserved across applyToAll", afterSave.hrvDisplayOptions.showGauge)
        assertEquals(customThresholds, afterSave.hrBikeZoneMax)
    }

    @Test
    fun testEditSensorFieldViewModel_delayedRepositoryLoadDoesNotClobberHydratedZoneDisplayDraft() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dataStore = SettingsDataStore.getInstance(app)
        val initialHrBike = ZoneDisplayOptions(
            showBackground = true,
            showLeftBar = false,
            showRightBar = true,
            showTextColor = true
        )
        runBlocking {
            ZoneDisplayOptionsDraft(
                hrRun = ZoneDisplayOptions(),
                hrBike = initialHrBike,
                pwrBike = ZoneDisplayOptions(),
                hrv = ZoneDisplayOptions(showGauge = true)
            ).persistTo(dataStore)
        }

        val trackingRepo = mockk<TrackingViewsRepository>(relaxed = true)
        val banalRepo = mockk<BANALServiceRepository>(relaxed = true)
        val initialField = SensorFieldConfig(
            sensorFieldId = 20L,
            rowNr = 0,
            colNr = 0,
            sensorType = SensorType.HR,
            sourceDeviceId = -1L,
            sourceDeviceName = null,
            viewSize = ViewSize.NORMAL,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0
        )
        // Simulate Room returning strictly AFTER DataStore snapshotFlow completes
        val delayedConfigFlow = MutableSharedFlow<SensorFieldConfig>(replay = 1)
        every { trackingRepo.getSensorFieldConfig(20L) } returns delayedConfigFlow
        every { trackingRepo.getSensorFieldConfigsForView(1L) } returns flowOf(listOf(initialField))

        val vm = EditSensorFieldViewModel(
            application = app,
            trackingViewsRepository = trackingRepo,
            banalServiceRepository = banalRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = 20L,
            tabViewId = 1L,
            rowNr = 0,
            colNr = 0,
            tuningConfigFlow = flowOf(TuningConfig()),
            settingsDataStore = dataStore
        )

        // Step 1: Wait until DataStore hydrates BEFORE Room emits
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.zoneDisplayDraft.hrv.showGauge &&
                vm.uiState.value.zoneDisplayDraft.hrBike == initialHrBike
        }

        // Step 2: Now let Room emit so loadInitialState() completes after hydration
        assertTrue(delayedConfigFlow.tryEmit(initialField))

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.idleMainLooper()
            vm.uiState.value.selectedSensorType == SensorType.HR
        }

        // Hydrated options MUST survive loadInitialState() completion
        assertEquals(initialHrBike, vm.uiState.value.zoneDisplayDraft.hrBike)
        assertTrue(vm.uiState.value.zoneDisplayDraft.hrv.showGauge)

        // Apply-to-all + Save must preserve hrv.showGauge and apply hrBike flags across profiles
        vm.onApplyZoneDisplayToAll(ZoneProfileTarget.HR_BIKE)
        vm.saveChanges()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.idleMainLooper()
            runBlocking { dataStore.snapshotFlow.first().hrRunDisplayOptions.showTextColor }
        }
        val persisted = runBlocking { dataStore.snapshotFlow.first() }
        assertFalse(persisted.hrRunDisplayOptions.showLeftBar)
        assertTrue(persisted.pwrBikeDisplayOptions.showTextColor)
        assertTrue(persisted.hrvDisplayOptions.showGauge)
    }

    @Test
    fun testEditSensorFieldDialog_cancelThenReopenSameTile_discardsCancelledDraftAndRehydrates() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dataStore = SettingsDataStore.getInstance(app)
        runBlocking {
            ZoneDisplayOptionsDraft(
                hrBike = ZoneDisplayOptions(showTextColor = false),
                hrv = ZoneDisplayOptions(showGauge = true)
            ).persistTo(dataStore)
        }

        val trackingRepo = mockk<TrackingViewsRepository>(relaxed = true)
        val banalRepo = mockk<BANALServiceRepository>(relaxed = true)
        val initialField = SensorFieldConfig(
            sensorFieldId = 30L,
            rowNr = 0,
            colNr = 0,
            sensorType = SensorType.HR,
            sourceDeviceId = -1L,
            sourceDeviceName = null,
            viewSize = ViewSize.NORMAL,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0
        )
        every { trackingRepo.getSensorFieldConfig(30L) } returns flowOf(initialField)
        every { trackingRepo.getSensorFieldConfigsForView(1L) } returns flowOf(listOf(initialField))

        val reusedVm = EditSensorFieldViewModel(
            application = app,
            trackingViewsRepository = trackingRepo,
            banalServiceRepository = banalRepo,
            activityType = ActivityType.BIKE_POWER,
            sensorFieldId = 30L,
            tabViewId = 1L,
            rowNr = 0,
            colNr = 0,
            tuningConfigFlow = flowOf(TuningConfig()),
            settingsDataStore = dataStore
        )

        var showDialog by mutableStateOf(true)
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                if (showDialog) {
                    EditSensorFieldDialog(
                        title = context.getString(R.string.edit_field),
                        viewModel = reusedVm,
                        onDismissRequest = { showDialog = false }
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.idleMainLooper()
            reusedVm.uiState.value.selectedSensorType == SensorType.HR &&
                reusedVm.uiState.value.zoneDisplayDraft.hrv.showGauge
        }
        composeTestRule.waitForIdle()

        // Toggle Text Color ON, then Cancel
        val textColorSwitch = composeTestRule.onAllNodes(isToggleable())[3]
        textColorSwitch.performScrollTo()
        textColorSwitch.assertIsOff()
        textColorSwitch.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(isToggleable())[3].assertIsOn()

        composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        composeTestRule.waitForIdle()
        assertFalse(runBlocking { dataStore.snapshotFlow.first().hrBikeDisplayOptions.showTextColor })

        // Reopen same tile with the SAME reused ViewModel -> cancelled toggle MUST be discarded
        showDialog = true
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(isToggleable())[3].performScrollTo().assertIsOff()

        // Saving without toggling again must keep persisted state (showTextColor == false, hrv.showGauge == true)
        composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
        composeTestRule.waitForIdle()
        val afterReopenSave = runBlocking { dataStore.snapshotFlow.first() }
        assertFalse(afterReopenSave.hrBikeDisplayOptions.showTextColor)
        assertTrue(afterReopenSave.hrvDisplayOptions.showGauge)
    }
}
