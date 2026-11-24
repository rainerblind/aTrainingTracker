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

package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder
import com.atrainingtracker.trainingtracker.settings.RawSensorRetentionPolicy
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
 * Behavioral Compose UI & DataStore tests for [SensorsGpsFilterSection] opt-in smartphone raw
 * telemetry toggle (REQ-EXP-045.5, TST-EXP-045.5, REQ-PRO-132).
 */
// TST-EXP-045.5
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h1200dp-xhdpi")
class SensorsGpsFilterSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        TuningPreferencesDataStore.setInstanceForTesting(null)
        SmartphoneRawTelemetryRecorder.setEnabledOverrideForTest(null)
    }

    @After
    fun tearDown() {
        TuningPreferencesDataStore.setInstanceForTesting(null)
        SmartphoneRawTelemetryRecorder.setEnabledOverrideForTest(null)
    }

    @Test
    fun smartphoneRawTelemetryToggle_rendersAndInvokesCallback_TST_EXP_045_5() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = context.getString(R.string.tuning_record_smartphone_raw_title)
        val desc = context.getString(R.string.tuning_record_smartphone_raw_desc)

        var enabledState by mutableStateOf(false)
        val observedChanges = mutableListOf<Boolean>()

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SensorsGpsFilterSection(
                        gpsAccuracy = 50f,
                        onGpsAccuracyChange = {},
                        altitudeWindowSec = 21,
                        onAltitudeWindowChange = {},
                        slopeMinSpeed = 0.5f,
                        onSlopeMinSpeedChange = {},
                        rawSensorRetentionPolicy = RawSensorRetentionPolicy.DAYS_30,
                        onRawSensorRetentionPolicyChange = {},
                        recordSmartphoneRawTelemetryEnabled = enabledState,
                        onRecordSmartphoneRawTelemetryChange = {
                            observedChanges.add(it)
                            enabledState = it
                        }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(desc).performScrollTo().assertIsDisplayed()

        // Toggle switches in SensorsGpsFilterSection: index 0 = ERTS smoothing, index 1 = smartphone raw telemetry
        val toggles = composeTestRule.onAllNodes(isToggleable())
        toggles[1].performScrollTo().assertIsOff()
        toggles[1].performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(true), observedChanges)
        toggles[1].assertIsOn()
    }

    @Test
    fun tuningPreferencesDataStore_persistsAndResetsSmartphoneRawTelemetryFlag_TST_EXP_045_5() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = TuningPreferencesDataStore.getInstance(context)
        dataStore.resetToDefaults()

        val initialConfig = dataStore.tuningConfigFlow.first()
        assertFalse(
            "Default recordSmartphoneRawTelemetryEnabled must be false",
            initialConfig.recordSmartphoneRawTelemetryEnabled
        )
        assertEquals(
            TuningPreferencesDefaults.DEFAULT_RECORD_SMARTPHONE_RAW_TELEMETRY_ENABLED,
            initialConfig.recordSmartphoneRawTelemetryEnabled
        )

        dataStore.saveTuningConfig(TuningConfig(recordSmartphoneRawTelemetryEnabled = true))
        val updatedConfig = dataStore.tuningConfigFlow.first()
        assertTrue(
            "Persisted recordSmartphoneRawTelemetryEnabled must be true after save",
            updatedConfig.recordSmartphoneRawTelemetryEnabled
        )

        dataStore.resetToDefaults()
        assertFalse(dataStore.tuningConfigFlow.first().recordSmartphoneRawTelemetryEnabled)
    }

    @Test
    fun coldStartFreshInstance_isEnabledImmediatelyReturnsTrueAfterPersistedOptIn_TST_EXP_045_1() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = TuningPreferencesDataStore.getInstance(context)
        try {
            dataStore.saveTuningConfig(TuningConfig(recordSmartphoneRawTelemetryEnabled = true))
            // Simulate cold launch / post-crash process recreation with fresh uninitialized singleton
            TuningPreferencesDataStore.setInstanceForTesting(null)
            assertFalse(TuningPreferencesDataStore.hasInitializedInstance())

            assertTrue(
                "First isEnabled() call on cold start MUST observe persisted opt-in true",
                SmartphoneRawTelemetryRecorder.isEnabled(context)
            )
            assertTrue(
                "SettingsDataStoreJavaHelper cold-start read MUST observe persisted opt-in true",
                SettingsDataStoreJavaHelper.isRecordSmartphoneRawTelemetryEnabled(context)
            )
        } finally {
            TuningPreferencesDataStore.getInstance(context).resetToDefaults()
        }
    }

    // Linked test specs: TST-SET-133.1
    @Test
    fun coldStartFreshInstance_updateAndResetMirrorParity_TST_SET_133_1() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = TuningPreferencesDataStore.getInstance(context)
        try {
            // 1. Direct updateRecordSmartphoneRawTelemetryEnabled(true) survives cold start
            dataStore.updateRecordSmartphoneRawTelemetryEnabled(true)
            TuningPreferencesDataStore.setInstanceForTesting(null)
            assertTrue(
                "updateRecordSmartphoneRawTelemetryEnabled(true) must survive cold start",
                SmartphoneRawTelemetryRecorder.isEnabled(context)
            )
            assertTrue(SettingsDataStoreJavaHelper.isRecordSmartphoneRawTelemetryEnabled(context))

            // 2. Upgrade-path backfill: DataStore holds true while SP mirror key is absent
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit().remove(TuningPreferencesDataStore.KEY_RECORD_SMARTPHONE_RAW_TELEMETRY.name).commit()
            TuningPreferencesDataStore.setInstanceForTesting(null)
            val hydrated = TuningPreferencesDataStore.getInstance(context)
            withTimeout(5_000L) {
                hydrated.recordSmartphoneRawTelemetryEnabled.first { it }
            }
            assertTrue(
                "init hydration must backfill SP cold-start mirror when DataStore has true",
                sp.getBoolean(TuningPreferencesDataStore.KEY_RECORD_SMARTPHONE_RAW_TELEMETRY.name, false)
            )
            // Subsequent cold start observes backfilled true immediately
            TuningPreferencesDataStore.setInstanceForTesting(null)
            assertTrue(SmartphoneRawTelemetryRecorder.isEnabled(context))
            assertTrue(SettingsDataStoreJavaHelper.isRecordSmartphoneRawTelemetryEnabled(context))

            // 3. resetToDefaults() clears mirror and subsequent cold start returns false
            TuningPreferencesDataStore.getInstance(context).resetToDefaults()
            TuningPreferencesDataStore.setInstanceForTesting(null)
            assertFalse(
                "resetToDefaults() must clear cold-start mirror to false",
                SmartphoneRawTelemetryRecorder.isEnabled(context)
            )
            assertFalse(SettingsDataStoreJavaHelper.isRecordSmartphoneRawTelemetryEnabled(context))
        } finally {
            TuningPreferencesDataStore.getInstance(context).resetToDefaults()
            TuningPreferencesDataStore.setInstanceForTesting(null)
        }
    }
}
