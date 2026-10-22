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

package com.atrainingtracker.trainingtracker.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for DataStore persistence and flow reactivity of ZoneDisplayOptions.showGauge (REQ-UI-376.1, TST-UI-339.2).
 */
class SettingsDataStoreZoneGaugeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestDataStore(): Pair<DataStore<Preferences>, SettingsDataStore> {
        val testFile = tempFolder.newFile("test_zone_gauge_${System.nanoTime()}.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { testFile }
        )
        val context = mockk<Context>(relaxed = true)
        val settingsDataStore = SettingsDataStore(context, testDataStore)
        return Pair(testDataStore, settingsDataStore)
    }

    @Test
    fun defaultZoneDisplayOptions_hasShowGaugeFalseAcrossAllProfiles() = runBlocking {
        val (_, dataStore) = createTestDataStore()

        val hrRunOpts = dataStore.getZoneDisplayOptionsFlow(SettingsDataStore.ZoneType.HR_RUN).first()
        val hrBikeOpts = dataStore.getZoneDisplayOptionsFlow(SettingsDataStore.ZoneType.HR_BIKE).first()
        val pwrBikeOpts = dataStore.getZoneDisplayOptionsFlow(SettingsDataStore.ZoneType.PWR_BIKE).first()

        assertFalse("HR_RUN showGauge must default to false", hrRunOpts.showGauge)
        assertFalse("HR_BIKE showGauge must default to false", hrBikeOpts.showGauge)
        assertFalse("PWR_BIKE showGauge must default to false", pwrBikeOpts.showGauge)
    }

    @Test
    fun saveZoneDisplayOptions_persistsShowGaugeIndependentlyPerProfile() = runBlocking {
        val (_, dataStore) = createTestDataStore()

        // Enable showGauge for PWR_BIKE only
        val customPowerOptions = ZoneDisplayOptions(
            showBackground = false,
            showLeftBar = true,
            showRightBar = false,
            showTextColor = false,
            showGauge = true
        )
        dataStore.saveZoneDisplayOptions(SettingsDataStore.ZoneType.PWR_BIKE, customPowerOptions)

        val updatedPwr = dataStore.getZoneDisplayOptionsFlow(SettingsDataStore.ZoneType.PWR_BIKE).first()
        val hrRun = dataStore.getZoneDisplayOptionsFlow(SettingsDataStore.ZoneType.HR_RUN).first()
        val hrBike = dataStore.getZoneDisplayOptionsFlow(SettingsDataStore.ZoneType.HR_BIKE).first()

        assertTrue("PWR_BIKE showGauge must be true", updatedPwr.showGauge)
        assertFalse("PWR_BIKE showBackground must be false", updatedPwr.showBackground)
        assertFalse("HR_RUN showGauge must remain false", hrRun.showGauge)
        assertFalse("HR_BIKE showGauge must remain false", hrBike.showGauge)

        // Now enable showGauge for HR_RUN
        dataStore.saveZoneDisplayOptions(
            SettingsDataStore.ZoneType.HR_RUN,
            ZoneDisplayOptions(showGauge = true)
        )
        val updatedHrRun = dataStore.getZoneDisplayOptionsFlow(SettingsDataStore.ZoneType.HR_RUN).first()
        assertTrue("HR_RUN showGauge must now be true", updatedHrRun.showGauge)
        assertFalse("HR_BIKE showGauge must still remain false", dataStore.getZoneDisplayOptionsFlow(SettingsDataStore.ZoneType.HR_BIKE).first().showGauge)
    }
}
