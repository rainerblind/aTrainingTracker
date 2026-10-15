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

package com.atrainingtracker.trainingtracker.zones

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Companion.KEY_CYCLING_FTP
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Companion.KEY_HR_BIKE_ZONES_CUSTOMIZED
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Companion.KEY_HR_MAX
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Companion.KEY_HR_RUN_ZONES_CUSTOMIZED
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Companion.KEY_IS_HR_ZONES_CONFIGURED
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Companion.KEY_IS_PWR_ZONES_CONFIGURED
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Companion.KEY_PWR_BIKE_ZONES_CUSTOMIZED
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Zone
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for zone configuration flags and migration heuristics conforming to [TST-UI-337.1] (REQ-UI-374.2).
 */
class SettingsDataStoreConfiguredTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestDataStore(): Pair<DataStore<Preferences>, SettingsDataStore> {
        val testFile = tempFolder.newFile("test_configured_${System.nanoTime()}.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { testFile }
        )

        val context = mockk<Context>(relaxed = true)
        val settingsDataStore = SettingsDataStore(context, testDataStore)
        return Pair(testDataStore, settingsDataStore)
    }

    @Test
    fun testFreshInstall_defaultsToFalse() = runBlocking {
        val (_, settingsDataStore) = createTestDataStore()

        assertFalse(settingsDataStore.isHrZonesConfigured.first())
        assertFalse(settingsDataStore.isPwrZonesConfigured.first())
    }

    @Test
    fun testHrMigrationFallback_hrMaxResolvesToTrue() = runBlocking {
        val (rawStore, settingsDataStore) = createTestDataStore()

        rawStore.edit { prefs ->
            prefs[KEY_HR_MAX] = 190
        }

        assertTrue(settingsDataStore.isHrZonesConfigured.first())
        assertFalse(settingsDataStore.isPwrZonesConfigured.first())
    }

    @Test
    fun testHrMigrationFallback_customizedFlagResolvesToTrue() = runBlocking {
        val (rawStore, settingsDataStore) = createTestDataStore()

        rawStore.edit { prefs ->
            prefs[KEY_HR_RUN_ZONES_CUSTOMIZED] = true
        }

        assertTrue(settingsDataStore.isHrZonesConfigured.first())

        rawStore.edit { prefs ->
            prefs[KEY_HR_RUN_ZONES_CUSTOMIZED] = false
            prefs[KEY_HR_BIKE_ZONES_CUSTOMIZED] = true
        }

        assertTrue(settingsDataStore.isHrZonesConfigured.first())
    }

    @Test
    fun testPwrMigrationFallback_ftpResolvesToTrue() = runBlocking {
        val (rawStore, settingsDataStore) = createTestDataStore()

        rawStore.edit { prefs ->
            prefs[KEY_CYCLING_FTP] = 250
        }

        assertTrue(settingsDataStore.isPwrZonesConfigured.first())
        assertFalse(settingsDataStore.isHrZonesConfigured.first())
    }

    @Test
    fun testPwrMigrationFallback_customizedFlagResolvesToTrue() = runBlocking {
        val (rawStore, settingsDataStore) = createTestDataStore()

        rawStore.edit { prefs ->
            prefs[KEY_PWR_BIKE_ZONES_CUSTOMIZED] = true
        }

        assertTrue(settingsDataStore.isPwrZonesConfigured.first())
    }

    @Test
    fun testExplicitMutators_overrideFallback() = runBlocking {
        val (_, settingsDataStore) = createTestDataStore()

        settingsDataStore.setHrZonesConfigured(true)
        assertTrue(settingsDataStore.isHrZonesConfigured.first())

        settingsDataStore.setPwrZonesConfigured(true)
        assertTrue(settingsDataStore.isPwrZonesConfigured.first())

        settingsDataStore.setHrZonesConfigured(false)
        assertFalse(settingsDataStore.isHrZonesConfigured.first())

        settingsDataStore.setPwrZonesConfigured(false)
        assertFalse(settingsDataStore.isPwrZonesConfigured.first())
    }

    @Test
    fun testAutoCalculation_setsConfiguredFlag() = runBlocking {
        val (_, settingsDataStore) = createTestDataStore()

        assertFalse(settingsDataStore.isPwrZonesConfigured.first())
        settingsDataStore.saveCalculatedPowerZones(250, listOf(138, 188, 225, 263, 300, 375))
        assertTrue(settingsDataStore.isPwrZonesConfigured.first())

        assertFalse(settingsDataStore.isHrZonesConfigured.first())
        settingsDataStore.saveCalculatedHeartRateZones(
            ZoneType.HR_RUN,
            190,
            50,
            "karvonen",
            listOf(133, 147, 161, 175)
        )
        assertTrue(settingsDataStore.isHrZonesConfigured.first())
    }

    @Test
    fun testManualSave_setsConfiguredFlag() = runBlocking {
        val (_, settingsDataStore) = createTestDataStore()

        assertFalse(settingsDataStore.isHrZonesConfigured.first())
        settingsDataStore.saveHrZoneMax(ZoneType.HR_RUN, Zone.ZONE_1, 140)
        assertTrue(settingsDataStore.isHrZonesConfigured.first())

        assertFalse(settingsDataStore.isPwrZonesConfigured.first())
        settingsDataStore.saveHrZoneMax(ZoneType.PWR_BIKE, Zone.ZONE_1, 150)
        assertTrue(settingsDataStore.isPwrZonesConfigured.first())
    }
}
