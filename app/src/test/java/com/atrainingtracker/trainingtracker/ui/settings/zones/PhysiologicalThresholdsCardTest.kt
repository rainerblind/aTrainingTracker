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

package com.atrainingtracker.trainingtracker.ui.settings.zones

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.ZoneProfileState
import com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository
import com.atrainingtracker.trainingtracker.ui.settings.HrMethod
import com.atrainingtracker.trainingtracker.ui.settings.ZoneCalculatorDialog
import com.atrainingtracker.trainingtracker.ui.settings.ZoneCalculatorStateHolder
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit & Compose verification suite for `ATT-9627` (`REQ-SET-136.1`, `REQ-SET-136.2`, `REQ-SET-136.3`,
 * `TST-SET-136.1`, `TST-SET-136.2`, `TST-SET-136.3`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PhysiologicalThresholdsCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun resolveVisibleThresholdFields_respectsAllExpertSettingsFlags_TST_SET_136_1() {
        val allOff = TuningConfig(
            enableCogganPowerMetrics = false,
            enableCriticalPowerModel = false,
            enableAerobicEfficiencyModel = false,
            enablePmcModel = false
        )
        assertTrue(resolveVisibleThresholdFields(ZoneType.PWR_BIKE, allOff).isEmpty())
        assertEquals(
            listOf(AthleteMetricType.HR_MAX_RUN_BPM, AthleteMetricType.HR_REST_BPM),
            resolveVisibleThresholdFields(ZoneType.HR_RUN, allOff)
        )
        assertEquals(
            listOf(AthleteMetricType.HR_MAX_BIKE_BPM, AthleteMetricType.HR_REST_BPM),
            resolveVisibleThresholdFields(ZoneType.HR_BIKE, allOff)
        )

        val pmcOnly = allOff.copy(enablePmcModel = true)
        assertEquals(
            listOf(AthleteMetricType.HR_MAX_RUN_BPM, AthleteMetricType.HR_REST_BPM, AthleteMetricType.LTHR_RUN_BPM),
            resolveVisibleThresholdFields(ZoneType.HR_RUN, pmcOnly)
        )

        val allOn = TuningConfig(
            enableCogganPowerMetrics = true,
            enableCriticalPowerModel = true,
            enableAerobicEfficiencyModel = true,
            enablePmcModel = true
        )
        assertEquals(
            listOf(
                AthleteMetricType.FTP_WATTS,
                AthleteMetricType.CP_WATTS,
                AthleteMetricType.W_PRIME_KJ,
                AthleteMetricType.LT1_PWR_BIKE_WATTS
            ),
            resolveVisibleThresholdFields(ZoneType.PWR_BIKE, allOn)
        )
        assertEquals(
            listOf(
                AthleteMetricType.HR_MAX_BIKE_BPM,
                AthleteMetricType.HR_REST_BPM,
                AthleteMetricType.LTHR_BIKE_BPM,
                AthleteMetricType.LT1_HR_BIKE_BPM
            ),
            resolveVisibleThresholdFields(ZoneType.HR_BIKE, allOn)
        )
    }

    @Test
    fun powerCard_hidesCpAndEstimatorWhenDisabled_andShowsImmediatelyWhenToggledOn_TST_SET_136_1() {
        val tuningState = mutableStateOf(
            TuningConfig(
                enableCogganPowerMetrics = true,
                enableCriticalPowerModel = false,
                enableAerobicEfficiencyModel = false
            )
        )
        var appliedEstimate: Pair<Int, Double>? = null

        composeRule.setContent {
            ATrainingTrackerTheme {
                PhysiologicalThresholdsCard(
                    zoneType = ZoneType.PWR_BIKE,
                    tuningConfig = tuningState.value,
                    values = ZoneThresholdsValues(ftp = 270, cp = 260, wPrimeKj = 18.0f),
                    onSaveThreshold = { _, _ -> },
                    onSaveCpAndWPrime = { cp, wPrime -> appliedEstimate = cp to wPrime },
                    onOpenRecalculateDialog = {}
                )
            }
        }

        composeRule.onNodeWithTag("threshold_input_FTP_WATTS").assertIsDisplayed()
        composeRule.onNodeWithTag("threshold_input_CP_WATTS").assertDoesNotExist()
        composeRule.onNodeWithTag("threshold_input_W_PRIME_KJ").assertDoesNotExist()
        composeRule.onNodeWithTag("open_cp_estimator_button").assertDoesNotExist()

        // Toggle Critical Power ON -> CP, W', and 2-Point Estimator appear immediately
        composeRule.runOnIdle {
            tuningState.value = tuningState.value.copy(enableCriticalPowerModel = true)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("threshold_input_CP_WATTS").assertIsDisplayed()
        composeRule.onNodeWithTag("threshold_input_W_PRIME_KJ").assertIsDisplayed()
        composeRule.onNodeWithTag("open_cp_estimator_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cp_w_prime_estimator_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("cp_estimator_apply_button")
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(
            "Applying 2-Point Estimator must emit positive CP & W'",
            (appliedEstimate?.first ?: 0) > 200
        )
    }

    @Test
    fun hrAndPowerCards_validateInvariantsAndEmitValidEditsOnly_TST_SET_136_2() {
        val saved = mutableListOf<Pair<AthleteMetricType, Double>>()
        val zoneTypeState = mutableStateOf(ZoneType.HR_RUN)
        val valuesState = mutableStateOf(
            ZoneThresholdsValues(hrMax = 192, hrRest = 50, lthr = 172, lt1Hr = 148, ftp = 270, lt1PwrBike = 210)
        )

        composeRule.setContent {
            ATrainingTrackerTheme {
                PhysiologicalThresholdsCard(
                    zoneType = zoneTypeState.value,
                    tuningConfig = TuningConfig(
                        enableCogganPowerMetrics = true,
                        enableCriticalPowerModel = false,
                        enableAerobicEfficiencyModel = true,
                        enablePmcModel = true
                    ),
                    values = valuesState.value,
                    onSaveThreshold = { m, v -> saved.add(m to v) },
                    onSaveCpAndWPrime = { _, _ -> },
                    onOpenRecalculateDialog = {}
                )
            }
        }

        // Invalid LTHR >= HRmax (195 >= 192) displays validation error and does NOT emit save
        composeRule.onNodeWithTag("threshold_input_LTHR_RUN_BPM").performTextReplacement("195")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("threshold_validation_error").assertIsDisplayed()
        assertFalse(saved.any { it.first == AthleteMetricType.LTHR_RUN_BPM && it.second == 195.0 })

        // Valid LTHR (174 < 192) clears error and emits save
        composeRule.onNodeWithTag("threshold_input_LTHR_RUN_BPM").performTextReplacement("174")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("threshold_validation_error").assertDoesNotExist()
        assertTrue(saved.any { it.first == AthleteMetricType.LTHR_RUN_BPM && it.second == 174.0 })

        // Switch to PWR_BIKE: LT1 >= FTP (285 >= 270) shows validation error and blocks save
        composeRule.runOnIdle { zoneTypeState.value = ZoneType.PWR_BIKE }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("threshold_input_LT1_PWR_BIKE_WATTS").performTextReplacement("285")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("threshold_validation_error").assertIsDisplayed()
        assertFalse(saved.any { it.first == AthleteMetricType.LT1_PWR_BIKE_WATTS && it.second == 285.0 })

        // Valid FTP (280) with LT1 back to 210 emits save
        composeRule.onNodeWithTag("threshold_input_LT1_PWR_BIKE_WATTS").performTextReplacement("210")
        composeRule.onNodeWithTag("threshold_input_FTP_WATTS").performTextReplacement("280")
        composeRule.waitForIdle()
        assertTrue(saved.any { it.first == AthleteMetricType.FTP_WATTS && it.second == 280.0 })
    }

    @Test
    fun zonesSettingsViewModel_savePhysiologicalThreshold_persistsWithoutOverwritingCustomZones_TST_SET_136_2() =
        runTest(testDispatcher) {
            val mockApp = mockk<Application>(relaxed = true)
            val mockDataStore = mockk<SettingsDataStore>(relaxed = true)
            val mockRepo = mockk<AthleteMetricsRepository>(relaxed = true)
            val mockTuning = mockk<TuningPreferencesDataStore>(relaxed = true)

            val customPower = ZoneProfileState(
                type = ZoneType.PWR_BIKE,
                z1 = 140, z2 = 195, z3 = 235, z4 = 275, z5 = 315, z6 = 395
            )
            coEvery { mockDataStore.loadAllZoneProfiles() } returns listOf(customPower)
            every { mockRepo.zoneHistoryFlow } returns MutableStateFlow(emptyMap())
            every { mockTuning.tuningConfigFlow } returns MutableStateFlow(TuningConfig())

            val vm = ZonesSettingsViewModel(
                application = mockApp,
                savedStateHandle = SavedStateHandle(),
                dataStore = mockDataStore,
                ioDispatcher = testDispatcher,
                athleteMetricsRepository = mockRepo,
                tuningDataStoreOverride = mockTuning
            )
            advanceUntilIdle()

            // Edit FTP from 270 to 280 via decoupled threshold save
            vm.savePhysiologicalThreshold(AthleteMetricType.FTP_WATTS, 280.0, debounceMs = 100L)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                mockRepo.addEntry(metricType = AthleteMetricType.FTP_WATTS, value = 280.0, timestampMs = any())
            }
            // Custom Z1..Z6 cutoffs MUST NOT be overwritten
            coVerify(exactly = 0) { mockDataStore.saveZoneProfile(any()) }
            assertEquals(customPower, vm.getProfile(ZoneType.PWR_BIKE))

            // Explicit recalculation snapshot DOES record zone_history
            val newCutoffs = listOf(154, 210, 252, 294, 336, 420)
            vm.saveRecalculatedZonesSnapshot(ZoneType.PWR_BIKE, newCutoffs, timestampMs = 1_760_000_000_000L)
            advanceUntilIdle()
            coVerify(exactly = 1) {
                mockRepo.saveZoneSnapshot(ZoneType.PWR_BIKE, newCutoffs, 1_760_000_000_000L)
            }
        }

    @Test
    fun zoneCalculatorDialog_overwriteConfirmation_preservesLthrAndThresholdAnchoredMethods_TST_SET_136_3() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val applyLabel = context.getString(R.string.zone_calc_apply_button)
        val savedMethods = mutableListOf<String>()
        val savedThresholds = mutableListOf<Pair<Int?, Int?>>()

        val holder = ZoneCalculatorStateHolder(
            zoneType = ZoneType.HR_RUN,
            initialCustomized = true,
            initialHrMax = 192,
            initialHrRest = 50,
            initialLthr = 172,
            initialLt1Hr = 148,
            initialHrMethod = HrMethod.THRESHOLD_ANCHORED
        )

        composeRule.setContent {
            ATrainingTrackerTheme {
                ZoneCalculatorDialog(
                    holder = holder,
                    onDismiss = {},
                    onSavePower = { _, _ -> },
                    onSaveHeartRate = { _, _, _, method, _ -> savedMethods.add(method) },
                    onSaveHeartRateThresholds = { lthr, lt1 -> savedThresholds.add(lthr to lt1) }
                )
            }
        }
        composeRule.waitForIdle()

        // First click opens overwrite confirmation modal
        composeRule.onAllNodesWithText(applyLabel)[0].performClick()
        composeRule.waitForIdle()
        assertTrue("Overwrite confirmation modal must be displayed", holder.state.showOverwriteWarning)
        assertTrue("Save must not run before confirmation", savedMethods.isEmpty())

        // Second click on confirmation Apply button must preserve HRVT_ANCHORS & invoke onSaveHeartRateThresholds
        val applyNodes = composeRule.onAllNodesWithText(applyLabel)
        applyNodes[applyNodes.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("HRVT_ANCHORS"), savedMethods)
        assertEquals(listOf(172 to 148), savedThresholds)
    }

    @Test
    fun crossFieldRecovery_andUnifiedWPrimeBounds_flushBothRecoveredFieldsWithoutSavingErrors_TST_SET_136_2() {
        val saved = mutableListOf<Pair<AthleteMetricType, Double>>()
        val zoneTypeState = mutableStateOf(ZoneType.HR_RUN)

        composeRule.setContent {
            ATrainingTrackerTheme {
                PhysiologicalThresholdsCard(
                    zoneType = zoneTypeState.value,
                    tuningConfig = TuningConfig(
                        enableCogganPowerMetrics = true,
                        enableCriticalPowerModel = true,
                        enableAerobicEfficiencyModel = true,
                        enablePmcModel = true
                    ),
                    values = ZoneThresholdsValues(
                        hrMax = 192, hrRest = 50, lthr = 172, lt1Hr = 148,
                        ftp = 280, cp = 270, wPrimeKj = 18.0f, lt1PwrBike = 210
                    ),
                    onSaveThreshold = { m, v -> saved.add(m to v) },
                    onSaveCpAndWPrime = { _, _ -> },
                    onOpenRecalculateDialog = {}
                )
            }
        }

        // Step 1: Raise LT1 above current LTHR (175 >= 172) -> blocked with error
        composeRule.onNodeWithTag("threshold_input_LT1_HR_RUN_BPM").performTextReplacement("175")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("threshold_validation_error").assertIsDisplayed()
        assertTrue("No save while cross-field invariant is violated", saved.isEmpty())

        // Step 2: Raise LTHR above LT1 (180 > 175) -> invariant recovers and BOTH fields persist!
        composeRule.onNodeWithTag("threshold_input_LTHR_RUN_BPM").performTextReplacement("180")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("threshold_validation_error").assertDoesNotExist()
        assertTrue("Recovered LT1=175 must persist", saved.contains(AthleteMetricType.LT1_HR_RUN_BPM to 175.0))
        assertTrue("Recovered LTHR=180 must persist", saved.contains(AthleteMetricType.LTHR_RUN_BPM to 180.0))

        // Step 3: Switch to PWR_BIKE and verify canonical W' >= 5.0 kJ & CP up to 1000 W bounds
        saved.clear()
        composeRule.runOnIdle { zoneTypeState.value = ZoneType.PWR_BIKE }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("threshold_input_W_PRIME_KJ").performTextReplacement("3.5")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("threshold_validation_error").assertIsDisplayed()
        assertTrue("W'=3.5 (< 5.0 kJ) must NOT emit save", saved.isEmpty())

        composeRule.onNodeWithTag("threshold_input_W_PRIME_KJ").performTextReplacement("5.5")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("threshold_validation_error").assertDoesNotExist()
        assertTrue("Valid W'=5.5 must persist", saved.contains(AthleteMetricType.W_PRIME_KJ to 5.5))

        composeRule.onNodeWithTag("threshold_input_CP_WATTS").performTextReplacement("850")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("threshold_validation_error").assertDoesNotExist()
        assertTrue("Valid CP=850 (<= 1000W) must persist", saved.contains(AthleteMetricType.CP_WATTS to 850.0))
    }

    @Test
    fun zonesSettingsViewModel_flushesPendingDebouncedThresholdsOnCleared_TST_SET_136_2() =
        runTest(testDispatcher) {
            val mockApp = mockk<Application>(relaxed = true)
            val mockDataStore = mockk<SettingsDataStore>(relaxed = true)
            val mockRepo = mockk<AthleteMetricsRepository>(relaxed = true)
            val mockTuning = mockk<TuningPreferencesDataStore>(relaxed = true)

            coEvery { mockDataStore.loadAllZoneProfiles() } returns emptyList()
            every { mockRepo.zoneHistoryFlow } returns MutableStateFlow(emptyMap())
            every { mockTuning.tuningConfigFlow } returns MutableStateFlow(TuningConfig())

            val vm = ZonesSettingsViewModel(
                application = mockApp,
                savedStateHandle = SavedStateHandle(),
                dataStore = mockDataStore,
                ioDispatcher = testDispatcher,
                athleteMetricsRepository = mockRepo,
                tuningDataStoreOverride = mockTuning
            )
            advanceUntilIdle()

            // Schedule a 350ms debounced edit, then immediately trigger onCleared() before debounce elapses
            vm.savePhysiologicalThreshold(AthleteMetricType.FTP_WATTS, 295.0, debounceMs = 350L)
            val onClearedMethod = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
            onClearedMethod.isAccessible = true
            onClearedMethod.invoke(vm)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                mockRepo.addEntry(metricType = AthleteMetricType.FTP_WATTS, value = 295.0, timestampMs = any())
            }
        }
}
