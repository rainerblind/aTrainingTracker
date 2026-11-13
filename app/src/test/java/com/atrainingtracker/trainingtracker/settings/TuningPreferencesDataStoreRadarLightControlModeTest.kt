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

import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification tests for 3-way radar tail light control mode schema migration,
 * downgrade safety, default constants, and key registration (TST-CON-031.5d / REQ-CON-031.5).
 */
class TuningPreferencesDataStoreRadarLightControlModeTest {

    @Test
    fun defaultConstants_matchSpecification() {
        assertEquals(RadarLightControlMode.FIXED_WORKOUT_MODE, TuningPreferencesDefaults.RADAR_LIGHT_CONTROL_MODE)
        val config = TuningConfig()
        assertEquals(RadarLightControlMode.FIXED_WORKOUT_MODE, config.radarLightControlMode)
    }

    @Test
    fun preferenceKey_isRegisteredInAllKeys() {
        assertTrue(
            "KEY_RADAR_LIGHT_CONTROL_MODE must be registered in ALL_KEYS for backup and reset",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_RADAR_LIGHT_CONTROL_MODE)
        )
    }

    @Test
    fun resolveRadarLightControlMode_migratesLegacyFlagsCorrectly() {
        // Permutation 1: legacyAuto = false, legacySmart = false -> MANUAL
        val prefsManual = preferencesOf(
            TuningPreferencesDataStore.KEY_RADAR_AUTO_LIGHT_CONTROL to false,
            TuningPreferencesDataStore.KEY_RADAR_SMART_LIGHT_AUTOMATION to false
        )
        assertEquals(RadarLightControlMode.MANUAL, TuningPreferencesDataStore.resolveRadarLightControlMode(prefsManual))

        // Permutation 2: legacyAuto = true, legacySmart = false -> FIXED_WORKOUT_MODE
        val prefsFixed = preferencesOf(
            TuningPreferencesDataStore.KEY_RADAR_AUTO_LIGHT_CONTROL to true,
            TuningPreferencesDataStore.KEY_RADAR_SMART_LIGHT_AUTOMATION to false
        )
        assertEquals(RadarLightControlMode.FIXED_WORKOUT_MODE, TuningPreferencesDataStore.resolveRadarLightControlMode(prefsFixed))

        // Permutation 3: legacyAuto = false, legacySmart = true -> SMART_AUTOMATION
        val prefsSmart1 = preferencesOf(
            TuningPreferencesDataStore.KEY_RADAR_AUTO_LIGHT_CONTROL to false,
            TuningPreferencesDataStore.KEY_RADAR_SMART_LIGHT_AUTOMATION to true
        )
        assertEquals(RadarLightControlMode.SMART_AUTOMATION, TuningPreferencesDataStore.resolveRadarLightControlMode(prefsSmart1))

        // Permutation 4: legacyAuto = true, legacySmart = true -> SMART_AUTOMATION (smart overrides fixed)
        val prefsSmart2 = preferencesOf(
            TuningPreferencesDataStore.KEY_RADAR_AUTO_LIGHT_CONTROL to true,
            TuningPreferencesDataStore.KEY_RADAR_SMART_LIGHT_AUTOMATION to true
        )
        assertEquals(RadarLightControlMode.SMART_AUTOMATION, TuningPreferencesDataStore.resolveRadarLightControlMode(prefsSmart2))

        // Empty / Default preferences (both absent) -> FIXED_WORKOUT_MODE
        val emptyPrefs = preferencesOf()
        assertEquals(RadarLightControlMode.FIXED_WORKOUT_MODE, TuningPreferencesDataStore.resolveRadarLightControlMode(emptyPrefs))
    }

    @Test
    fun resolveRadarLightControlMode_explicitKeyTakesPrecedenceOverLegacyFlags() {
        // Explicit MANUAL overrides legacy auto=true
        val prefsManualOverride = preferencesOf(
            TuningPreferencesDataStore.KEY_RADAR_LIGHT_CONTROL_MODE to RadarLightControlMode.MANUAL.name,
            TuningPreferencesDataStore.KEY_RADAR_AUTO_LIGHT_CONTROL to true,
            TuningPreferencesDataStore.KEY_RADAR_SMART_LIGHT_AUTOMATION to true
        )
        assertEquals(RadarLightControlMode.MANUAL, TuningPreferencesDataStore.resolveRadarLightControlMode(prefsManualOverride))

        // Explicit FIXED overrides legacy smart=true
        val prefsFixedOverride = preferencesOf(
            TuningPreferencesDataStore.KEY_RADAR_LIGHT_CONTROL_MODE to RadarLightControlMode.FIXED_WORKOUT_MODE.name,
            TuningPreferencesDataStore.KEY_RADAR_SMART_LIGHT_AUTOMATION to true
        )
        assertEquals(RadarLightControlMode.FIXED_WORKOUT_MODE, TuningPreferencesDataStore.resolveRadarLightControlMode(prefsFixedOverride))

        // Explicit SMART overrides legacy auto=false
        val prefsSmartOverride = preferencesOf(
            TuningPreferencesDataStore.KEY_RADAR_LIGHT_CONTROL_MODE to RadarLightControlMode.SMART_AUTOMATION.name,
            TuningPreferencesDataStore.KEY_RADAR_AUTO_LIGHT_CONTROL to false
        )
        assertEquals(RadarLightControlMode.SMART_AUTOMATION, TuningPreferencesDataStore.resolveRadarLightControlMode(prefsSmartOverride))
    }
}
