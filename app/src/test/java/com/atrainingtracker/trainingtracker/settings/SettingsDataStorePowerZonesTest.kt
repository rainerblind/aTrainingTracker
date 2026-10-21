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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for SettingsDataStore 7-zone Coggan power model, dynamic proportional migration,
 * monotonic validation, and failure containment (REQ-UI-370, TST-UI-333.1, TST-UI-333.2, TST-UI-333.9).
 */
class SettingsDataStorePowerZonesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestDataStore(): Pair<DataStore<Preferences>, SettingsDataStore> {
        val testFile = tempFolder.newFile("test_user_settings_${System.nanoTime()}.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { testFile }
        )

        val context = mockk<Context>(relaxed = true)
        val settingsDataStore = SettingsDataStore(context, testDataStore)
        return Pair(testDataStore, settingsDataStore)
    }

    @Test
    fun testPowerZones_defaultValuesWhenUnset() = runBlocking {
        val (_, dataStore) = createTestDataStore()

        assertEquals(132, dataStore.getZone1MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
        assertEquals(180, dataStore.getZone2MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
        assertEquals(216, dataStore.getZone3MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
        assertEquals(252, dataStore.getZone4MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
        assertEquals(288, dataStore.getZone5MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
        assertEquals(360, dataStore.getZone6MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
    }

    @Test
    fun testPowerZones_proportionalMigrationFromStoredZone4Max() = runBlocking {
        val (prefsDataStore, dataStore) = createTestDataStore()

        // Simulate existing installation with z4Max = 210 (200W FTP athlete) and missing z5/z6
        prefsDataStore.edit { prefs ->
            prefs[intPreferencesKey("pwr_bike_zone1_max")] = 110
            prefs[intPreferencesKey("pwr_bike_zone2_max")] = 150
            prefs[intPreferencesKey("pwr_bike_zone3_max")] = 180
            prefs[intPreferencesKey("pwr_bike_zone4_max")] = 210
        }

        // Calculation:
        // Estimated FTP = max(50, round(210 / 1.05)) = 200
        // z5Max = max(210 + 10, round(200 * 1.20)) = max(220, 240) = 240
        // z6Max = max(240 + 10, round(200 * 1.50)) = max(250, 300) = 300
        assertEquals(240, dataStore.getZone5MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
        assertEquals(300, dataStore.getZone6MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())

        // Run checkAndMigratePowerZones and ensure values are persisted into DataStore
        dataStore.checkAndMigratePowerZones()

        val persistedZ5 = prefsDataStore.data.first()[intPreferencesKey("pwr_bike_zone5_max")]
        val persistedZ6 = prefsDataStore.data.first()[intPreferencesKey("pwr_bike_zone6_max")]
        assertEquals(240, persistedZ5)
        assertEquals(300, persistedZ6)
    }

    @Test
    fun testPowerZones_lowBoundFtpMigration() = runBlocking {
        val (prefsDataStore, dataStore) = createTestDataStore()

        // Low bound FTP: z4Max = 50W
        prefsDataStore.edit { prefs ->
            prefs[intPreferencesKey("pwr_bike_zone1_max")] = 25
            prefs[intPreferencesKey("pwr_bike_zone2_max")] = 35
            prefs[intPreferencesKey("pwr_bike_zone3_max")] = 42
            prefs[intPreferencesKey("pwr_bike_zone4_max")] = 50
        }

        // Calculation:
        // Estimated FTP = max(50, round(50 / 1.05)) = 50
        // z5Max = max(50 + 10, round(50 * 1.20)) = max(60, 60) = 60
        // z6Max = max(60 + 10, round(50 * 1.50)) = max(70, 75) = 75
        val z5 = dataStore.getZone5MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first()
        val z6 = dataStore.getZone6MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first()

        assertTrue("z5Max must be >= 60", z5 >= 60)
        assertTrue("z6Max must be >= 70", z6 >= 70)
        assertTrue("Monotonicity preserved", z6 > z5 && z5 > 50)
    }

    @Test
    fun testPowerZones_migrationFromCorruptedState_fallsBackToCanonicalDefaults() = runBlocking {
        val (prefsDataStore, dataStore) = createTestDataStore()

        // Corrupted existing legacy data where z4Max is lower than z3Max
        prefsDataStore.edit { prefs ->
            prefs[intPreferencesKey("pwr_bike_zone1_max")] = 150
            prefs[intPreferencesKey("pwr_bike_zone2_max")] = 200
            prefs[intPreferencesKey("pwr_bike_zone3_max")] = 320
            prefs[intPreferencesKey("pwr_bike_zone4_max")] = 300 // Non-monotonic!
        }

        dataStore.checkAndMigratePowerZones()

        // Sanity check must fail and trigger reset to canonical defaults
        assertEquals(132, prefsDataStore.data.first()[intPreferencesKey("pwr_bike_zone1_max")])
        assertEquals(180, prefsDataStore.data.first()[intPreferencesKey("pwr_bike_zone2_max")])
        assertEquals(216, prefsDataStore.data.first()[intPreferencesKey("pwr_bike_zone3_max")])
        assertEquals(252, prefsDataStore.data.first()[intPreferencesKey("pwr_bike_zone4_max")])
        assertEquals(288, prefsDataStore.data.first()[intPreferencesKey("pwr_bike_zone5_max")])
        assertEquals(360, prefsDataStore.data.first()[intPreferencesKey("pwr_bike_zone6_max")])
    }

    @Test
    fun testPowerZones_monotonicValidation() {
        val (_, dataStore) = createTestDataStore()

        // Valid strictly monotonic
        assertTrue(dataStore.validatePowerZones(150, 200, 250, 300, 350, 450))

        // Non-monotonic cases
        assertFalse("z1 <= 0 is invalid", dataStore.validatePowerZones(0, 200, 250, 300, 350, 450))
        assertFalse("z2 <= z1 is invalid", dataStore.validatePowerZones(150, 150, 250, 300, 350, 450))
        assertFalse("z3 <= z2 is invalid", dataStore.validatePowerZones(150, 200, 190, 300, 350, 450))
        assertFalse("z4 <= z3 is invalid", dataStore.validatePowerZones(150, 200, 250, 240, 350, 450))
        assertFalse("z5 <= z4 is invalid", dataStore.validatePowerZones(150, 200, 250, 300, 290, 450))
        assertFalse("z6 <= z5 is invalid", dataStore.validatePowerZones(150, 200, 250, 300, 350, 340))
    }

    @Test
    fun testResetToDefaults() = runBlocking {
        val (prefsDataStore, dataStore) = createTestDataStore()

        prefsDataStore.edit { prefs ->
            prefs[intPreferencesKey("pwr_bike_zone1_max")] = 180
            prefs[intPreferencesKey("pwr_bike_zone2_max")] = 220
            prefs[intPreferencesKey("pwr_bike_zone3_max")] = 260
            prefs[intPreferencesKey("pwr_bike_zone4_max")] = 310
            prefs[intPreferencesKey("pwr_bike_zone5_max")] = 360
            prefs[intPreferencesKey("pwr_bike_zone6_max")] = 460
        }

        dataStore.resetToDefaults(SettingsDataStore.ZoneType.PWR_BIKE)

        assertEquals(132, dataStore.getZone1MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
        assertEquals(180, dataStore.getZone2MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
        assertEquals(216, dataStore.getZone3MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
        assertEquals(252, dataStore.getZone4MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
        assertEquals(288, dataStore.getZone5MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
        assertEquals(360, dataStore.getZone6MaxFlow(SettingsDataStore.ZoneType.PWR_BIKE).first())
    }
}
