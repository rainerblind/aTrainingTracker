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

        assertEquals(1.5f, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_SPEED_THRESHOLD_KMH, 0.001f)
        assertEquals(0.5f, TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD_KMH, 0.001f)
        assertEquals(5.0f, TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD_KMH, 0.001f)
        assertEquals(0.1f, TuningPreferencesDefaults.STEP_AUTO_PAUSE_SPEED_THRESHOLD_KMH, 0.001f)

        assertEquals(2, TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_DEBOUNCE_SEC)
        assertEquals(1, TuningPreferencesDefaults.MIN_AUTO_PAUSE_DEBOUNCE_SEC)
        assertEquals(5, TuningPreferencesDefaults.MAX_AUTO_PAUSE_DEBOUNCE_SEC)
        assertEquals(1, TuningPreferencesDefaults.STEP_AUTO_PAUSE_DEBOUNCE_SEC)
    }

    @Test
    fun tuningConfig_defaultsToSportSpecificPolicy() {
        val config = TuningConfig()
        assertTrue("Bike must be enabled by default per REQ-TRK-018", config.autoPauseBikeEnabled)
        assertFalse("Run must be disabled by default per REQ-TRK-018", config.autoPauseRunEnabled)
        assertFalse("Other must be disabled by default per REQ-TRK-018", config.autoPauseOtherEnabled)
        assertEquals(1.5f, config.autoPauseSpeedThresholdKmh, 0.001f)
        assertEquals(2, config.autoPauseDebounceSec)
    }

    @Test
    fun tuningConfig_customValuesRetained() {
        val config = TuningConfig(
            autoPauseBikeEnabled = false,
            autoPauseRunEnabled = true,
            autoPauseOtherEnabled = true,
            autoPauseSpeedThresholdKmh = 3.0f,
            autoPauseDebounceSec = 4
        )
        assertFalse(config.autoPauseBikeEnabled)
        assertTrue(config.autoPauseRunEnabled)
        assertTrue(config.autoPauseOtherEnabled)
        assertEquals(3.0f, config.autoPauseSpeedThresholdKmh, 0.001f)
        assertEquals(4, config.autoPauseDebounceSec)
    }

    @Test
    fun clampingBounds_enforcedCorrectly() {
        val belowMinSpeed = 0.1f.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD_KMH,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD_KMH
        )
        val aboveMaxSpeed = 10.0f.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD_KMH,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD_KMH
        )
        val validSpeed = 2.5f.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_SPEED_THRESHOLD_KMH,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_SPEED_THRESHOLD_KMH
        )

        assertEquals(0.5f, belowMinSpeed, 0.001f)
        assertEquals(5.0f, aboveMaxSpeed, 0.001f)
        assertEquals(2.5f, validSpeed, 0.001f)

        val belowMinDebounce = 0.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_DEBOUNCE_SEC
        )
        val aboveMaxDebounce = 10.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_DEBOUNCE_SEC
        )
        val validDebounce = 3.coerceIn(
            TuningPreferencesDefaults.MIN_AUTO_PAUSE_DEBOUNCE_SEC,
            TuningPreferencesDefaults.MAX_AUTO_PAUSE_DEBOUNCE_SEC
        )

        assertEquals(1, belowMinDebounce)
        assertEquals(5, aboveMaxDebounce)
        assertEquals(3, validDebounce)
    }

    @Test
    fun keys_registeredInAllKeysForFactoryReset() {
        assertEquals("tuning_auto_pause_bike_enabled", TuningPreferencesDataStore.KEY_AUTO_PAUSE_BIKE_ENABLED.name)
        assertEquals("tuning_auto_pause_run_enabled", TuningPreferencesDataStore.KEY_AUTO_PAUSE_RUN_ENABLED.name)
        assertEquals("tuning_auto_pause_other_enabled", TuningPreferencesDataStore.KEY_AUTO_PAUSE_OTHER_ENABLED.name)
        assertEquals("tuning_auto_pause_speed_threshold_kmh", TuningPreferencesDataStore.KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH.name)
        assertEquals("tuning_auto_pause_debounce_sec", TuningPreferencesDataStore.KEY_AUTO_PAUSE_DEBOUNCE_SEC.name)

        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_BIKE_ENABLED))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_RUN_ENABLED))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_OTHER_ENABLED))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH))
        assertTrue(TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_AUTO_PAUSE_DEBOUNCE_SEC))
    }
}
