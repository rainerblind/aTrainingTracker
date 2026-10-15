/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Auto-Pause preferences model, default constants, bounds, and keys (TST-TRK-010.1 / REQ-TRK-018).
 */
class TuningPreferencesDataStoreAutoPauseTest {

    @Test
    fun defaultConstants_matchSpecification() {
        assertTrue(TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_ENABLED)
        assertFalse(TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_ENABLED)
        assertFalse(TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_ENABLED)

        assertEquals(1, TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD)
        assertEquals(5, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD)
        assertEquals(1, TuningPreferencesDefaults.STEP_AUTO_PAUSE_SPEED_THRESHOLD)

        // Sport threshold defaults
        assertEquals(3, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC)
        assertEquals(2, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_IMPERIAL)
        assertEquals(2, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC)
        assertEquals(1, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_IMPERIAL)
        assertEquals(1, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC)
        assertEquals(1, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_IMPERIAL)

        // Debounce ranges & defaults
        assertEquals(2, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC)
        assertEquals(1, TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_DEBOUNCE_SEC)
        assertEquals(10, TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC)

        assertEquals(5, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC)
        assertEquals(1, TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_DEBOUNCE_SEC)
        assertEquals(20, TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC)

        assertEquals(10, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC)
        assertEquals(1, TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_DEBOUNCE_SEC)
        assertEquals(60, TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC)
    }

    @Test
    fun tuningConfig_defaultsToSportSpecificPolicy() {
        val config = TuningConfig()
        assertTrue("Bike must be enabled by default per REQ-TRK-018", config.autoPauseBikeEnabled)
        assertFalse("Run must be disabled by default per REQ-TRK-018", config.autoPauseRunEnabled)
        assertFalse("Other must be disabled by default per REQ-TRK-018", config.autoPauseOtherEnabled)

        assertEquals(3, config.autoPauseBikeSpeedThreshold)
        assertEquals(2, config.autoPauseBikeDebounceSec)
        assertEquals(2, config.autoPauseRunSpeedThreshold)
        assertEquals(5, config.autoPauseRunDebounceSec)
        assertEquals(1, config.autoPauseOtherSpeedThreshold)
        assertEquals(10, config.autoPauseOtherDebounceSec)

        // Backward compatibility getters
        assertEquals(3.0f, config.autoPauseSpeedThresholdKmh, 0.001f)
        assertEquals(2, config.autoPauseDebounceSec)
    }

    @Test
    fun tuningConfig_customValuesRetained() {
        val config = TuningConfig(
            autoPauseBikeEnabled = false,
            autoPauseRunEnabled = true,
            autoPauseOtherEnabled = true,
            autoPauseBikeSpeedThreshold = 4,
            autoPauseBikeDebounceSec = 8,
            autoPauseRunSpeedThreshold = 3,
            autoPauseRunDebounceSec = 15,
            autoPauseOtherSpeedThreshold = 2,
            autoPauseOtherDebounceSec = 45
        )
        assertFalse(config.autoPauseBikeEnabled)
        assertTrue(config.autoPauseRunEnabled)
        assertTrue(config.autoPauseOtherEnabled)
        assertEquals(4, config.autoPauseBikeSpeedThreshold)
        assertEquals(8, config.autoPauseBikeDebounceSec)
        assertEquals(3, config.autoPauseRunSpeedThreshold)
        assertEquals(15, config.autoPauseRunDebounceSec)
        assertEquals(2, config.autoPauseOtherSpeedThreshold)
        assertEquals(45, config.autoPauseOtherDebounceSec)
    }

    @Test
    fun clampingBounds_enforcedCorrectly() {
        // Thresholds 1..5
        assertEquals(1, 0.coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD))
        assertEquals(5, 10.coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD))
        assertEquals(3, 3.coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD))

        // Bike debounce 1..10
        assertEquals(1, 0.coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC))
        assertEquals(10, 15.coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_BIKE_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_BIKE_DEBOUNCE_SEC))

        // Run debounce 1..20
        assertEquals(1, 0.coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC))
        assertEquals(20, 25.coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_RUN_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_RUN_DEBOUNCE_SEC))

        // Other debounce 1..60
        assertEquals(1, 0.coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC))
        assertEquals(60, 100.coerceIn(TuningPreferencesDefaults.MIN_AUTO_PAUSE_OTHER_DEBOUNCE_SEC, TuningPreferencesDefaults.MAX_AUTO_PAUSE_OTHER_DEBOUNCE_SEC))
    }

    @Test
    fun keys_registeredInAllKeysForFactoryReset() {
        assertEquals("tuning_auto_pause_bike_enabled", TuningPreferencesDataStore.KEY_AUTO_PAUSE_BIKE_ENABLED.name)
        assertEquals("tuning_auto_pause_run_enabled", TuningPreferencesDataStore.KEY_AUTO_PAUSE_RUN_ENABLED.name)
        assertEquals("tuning_auto_pause_other_enabled", TuningPreferencesDataStore.KEY_AUTO_PAUSE_OTHER_ENABLED.name)
        assertEquals("tuning_auto_pause_speed_threshold_kmh", TuningPreferencesDataStore.KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH.name)
        assertEquals("tuning_auto_pause_debounce_sec", TuningPreferencesDataStore.KEY_AUTO_PAUSE_DEBOUNCE_SEC.name)

        assertEquals("tuning_auto_pause_bike_speed_threshold", TuningPreferencesDataStore.KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD.name)
        assertEquals("tuning_auto_pause_bike_debounce_sec", TuningPreferencesDataStore.KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC.name)
        assertEquals("tuning_auto_pause_run_speed_threshold", TuningPreferencesDataStore.KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD.name)
        assertEquals("tuning_auto_pause_run_debounce_sec", TuningPreferencesDataStore.KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC.name)
        assertEquals("tuning_auto_pause_other_speed_threshold", TuningPreferencesDataStore.KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD.name)
        assertEquals("tuning_auto_pause_other_debounce_sec", TuningPreferencesDataStore.KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC.name)

        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_BIKE_ENABLED))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_RUN_ENABLED))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_OTHER_ENABLED))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_DEBOUNCE_SEC))

        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC))
    }

    @Test
    fun mapAutoPausePreferences_migratesLegacyKeysCorrectly() {
        // Scenario 1: Legacy user with only legacy keys
        val legacyPrefs = preferencesOf(
            TuningPreferencesDataStore.KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH to 3.4f,
            TuningPreferencesDataStore.KEY_AUTO_PAUSE_DEBOUNCE_SEC to 4
        )
        val migrated = TuningPreferencesDataStore.mapAutoPausePreferences(legacyPrefs)

        // All sports should inherit the rounded legacy threshold (3) and debounce (4)
        assertEquals(3, migrated.bikeSpeedThreshold)
        assertEquals(4, migrated.bikeDebounceSec)
        assertEquals(3, migrated.runSpeedThreshold)
        assertEquals(4, migrated.runDebounceSec)
        assertEquals(3, migrated.otherSpeedThreshold)
        assertEquals(4, migrated.otherDebounceSec)
        // Default enabled states preserved
        assertTrue(migrated.bikeEnabled)
        assertFalse(migrated.runEnabled)
        assertFalse(migrated.otherEnabled)

        // Scenario 2: Sport-specific overrides take precedence over legacy keys
        val partialOverridePrefs = preferencesOf(
            TuningPreferencesDataStore.KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH to 3.4f,
            TuningPreferencesDataStore.KEY_AUTO_PAUSE_DEBOUNCE_SEC to 4,
            TuningPreferencesDataStore.KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD to 5,
            TuningPreferencesDataStore.KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC to 8
        )
        val overridden = TuningPreferencesDataStore.mapAutoPausePreferences(partialOverridePrefs)
        assertEquals(5, overridden.bikeSpeedThreshold)
        assertEquals(8, overridden.bikeDebounceSec)
        // Run and Other still inherit legacy fallback
        assertEquals(3, overridden.runSpeedThreshold)
        assertEquals(4, overridden.runDebounceSec)
        assertEquals(3, overridden.otherSpeedThreshold)
        assertEquals(4, overridden.otherDebounceSec)

        // Scenario 3: Clean installation (empty preferences) gets sport defaults
        val cleanPrefs = emptyPreferences()
        val defaults = TuningPreferencesDataStore.mapAutoPausePreferences(cleanPrefs)
        assertEquals(TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC, defaults.bikeSpeedThreshold)
        assertEquals(TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC, defaults.bikeDebounceSec)
        assertEquals(TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC, defaults.runSpeedThreshold)
        assertEquals(TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC, defaults.runDebounceSec)
        assertEquals(TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC, defaults.otherSpeedThreshold)
        assertEquals(TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC, defaults.otherDebounceSec)
    }
}
