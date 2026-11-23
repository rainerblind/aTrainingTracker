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

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification tests for canonical 2-key radar tail light preference consolidation,
 * one-time [TuningPreferencesDataStore.createPreferencesMigration], cluster tolerance migration
 * & cleanUp non-resurrection, and fresh-install single-source-of-truth consistency
 * (REQ-CON-056.1, TST-CON-056.1, REQ-CON-031.5).
 */
@Suppress("DEPRECATION")
class TuningPreferencesDataStoreRadarLightControlModeTest {

    @After
    fun tearDown() {
        TuningPreferencesDataStore.setInstanceForTesting(null)
    }

    @Test
    fun defaultConstants_areUnifiedAndConsistentOnFreshInstall_tstCon056_1() {
        assertEquals(RadarLightControlMode.SMART_AUTOMATION, TuningPreferencesDefaults.RADAR_LIGHT_CONTROL_MODE)
        assertTrue(TuningPreferencesDefaults.RADAR_LIGHT_AUTO_MODE)
        assertFalse(TuningPreferencesDefaults.RADAR_AUTO_LIGHT_CONTROL)
        assertTrue(TuningPreferencesDefaults.RADAR_SMART_LIGHT_AUTOMATION)

        val config = TuningConfig()
        assertEquals(RadarLightControlMode.SMART_AUTOMATION, config.radarLightControlMode)
        assertTrue(config.radarLightAutoMode)
        assertFalse(config.radarAutoLightControl)
        assertTrue(config.radarSmartLightAutomation)

        val emptyPrefs = emptyPreferences()
        assertEquals(RadarLightControlMode.SMART_AUTOMATION, TuningPreferencesDataStore.resolveRadarLightControlMode(emptyPrefs))
        assertTrue(TuningPreferencesDataStore.resolveRadarLightAutoMode(emptyPrefs))
        assertEquals(RadarLightMode.DAY_FLASH, TuningPreferencesDataStore.resolveRadarLightDefaultMode(emptyPrefs))
    }

    @Test
    fun canonicalKeysRegistered_andLegacyRadarLightKeysExcludedFromAllKeys_tstCon056_1() {
        assertTrue(
            "KEY_RADAR_LIGHT_CONTROL_MODE must be registered in ALL_KEYS",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_RADAR_LIGHT_CONTROL_MODE)
        )
        assertTrue(
            "KEY_RADAR_LIGHT_DEFAULT_MODE must be registered in ALL_KEYS",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_RADAR_LIGHT_DEFAULT_MODE)
        )
        for (legacyKey in TuningPreferencesDataStore.LEGACY_RADAR_LIGHT_KEYS) {
            assertFalse(
                "Legacy key ${legacyKey.name} must not remain in canonical ALL_KEYS",
                TuningPreferencesDataStore.ALL_KEYS.contains(legacyKey)
            )
        }
    }

    @Test
    fun oneTimeMigration_migratesAllLegacyRadarLightPermutationsAndIsIdempotent_tstCon056_1() = runTest {
        val migration = TuningPreferencesDataStore.createPreferencesMigration(context = null, sharedPreferencesProvider = { null })

        // 1. Legacy auto=false, smart=false -> MANUAL + default mode OFF
        val p1 = preferencesOf(
            TuningPreferencesDataStore.KEY_RADAR_AUTO_LIGHT_CONTROL to false,
            TuningPreferencesDataStore.KEY_RADAR_SMART_LIGHT_AUTOMATION to false
        )
        assertTrue(migration.shouldMigrate(p1))
        val m1 = migration.migrate(p1)
        assertEquals(RadarLightControlMode.MANUAL, TuningPreferencesDataStore.resolveRadarLightControlMode(m1))
        assertEquals(RadarLightMode.OFF, TuningPreferencesDataStore.resolveRadarLightDefaultMode(m1))
        assertFalse(migration.shouldMigrate(m1))

        // 2. Legacy smart=false (fallthrough!) with auto=true -> FIXED_WORKOUT_MODE
        val p2 = preferencesOf(
            TuningPreferencesDataStore.KEY_RADAR_AUTO_LIGHT_CONTROL to true,
            TuningPreferencesDataStore.KEY_RADAR_SMART_LIGHT_AUTOMATION to false
        )
        val m2 = migration.migrate(p2)
        assertEquals(RadarLightControlMode.FIXED_WORKOUT_MODE, TuningPreferencesDataStore.resolveRadarLightControlMode(m2))
        assertFalse(TuningPreferencesDataStore.resolveRadarLightAutoMode(m2))
        assertFalse(migration.shouldMigrate(m2))

        // 3. Legacy smart=false with absent auto -> falls through to historical auto=true (FIXED_WORKOUT_MODE)
        val p2b = preferencesOf(TuningPreferencesDataStore.KEY_RADAR_SMART_LIGHT_AUTOMATION to false)
        val m2b = migration.migrate(p2b)
        assertEquals(RadarLightControlMode.FIXED_WORKOUT_MODE, TuningPreferencesDataStore.resolveRadarLightControlMode(m2b))
        assertFalse(migration.shouldMigrate(m2b))

        // 4. Legacy smart=true overrides auto=false -> SMART_AUTOMATION
        val p3 = preferencesOf(
            TuningPreferencesDataStore.KEY_RADAR_AUTO_LIGHT_CONTROL to false,
            TuningPreferencesDataStore.KEY_RADAR_SMART_LIGHT_AUTOMATION to true
        )
        val m3 = migration.migrate(p3)
        assertEquals(RadarLightControlMode.SMART_AUTOMATION, TuningPreferencesDataStore.resolveRadarLightControlMode(m3))
        assertTrue(TuningPreferencesDataStore.resolveRadarLightAutoMode(m3))
        assertFalse(migration.shouldMigrate(m3))

        // 5. Legacy KEY_RADAR_LIGHT_AUTO_MODE=false without KEY_RADAR_LIGHT_CONTROL_MODE -> FIXED_WORKOUT_MODE
        val p4 = preferencesOf(TuningPreferencesDataStore.KEY_RADAR_LIGHT_AUTO_MODE to false)
        val m4 = migration.migrate(p4)
        assertEquals(RadarLightControlMode.FIXED_WORKOUT_MODE, TuningPreferencesDataStore.resolveRadarLightControlMode(m4))
        assertFalse(migration.shouldMigrate(m4))

        // 6. Explicit KEY_RADAR_LIGHT_CONTROL_MODE=MANUAL takes precedence over stale legacy booleans
        val p5 = preferencesOf(
            TuningPreferencesDataStore.KEY_RADAR_LIGHT_CONTROL_MODE to RadarLightControlMode.MANUAL.name,
            TuningPreferencesDataStore.KEY_RADAR_LIGHT_AUTO_MODE to true,
            TuningPreferencesDataStore.KEY_RADAR_AUTO_LIGHT_CONTROL to true,
            TuningPreferencesDataStore.KEY_RADAR_SMART_LIGHT_AUTOMATION to true
        )
        val m5 = migration.migrate(p5)
        assertEquals(RadarLightControlMode.MANUAL, TuningPreferencesDataStore.resolveRadarLightControlMode(m5))
        assertFalse(TuningPreferencesDataStore.resolveRadarLightAutoMode(m5))
        for (legacyKey in TuningPreferencesDataStore.LEGACY_RADAR_LIGHT_KEYS) {
            assertFalse("Legacy key ${legacyKey.name} must be removed", m5.contains(legacyKey))
        }
        assertFalse(migration.shouldMigrate(m5))
    }

    @Test
    fun oneTimeMigration_migratesLegacyClusterPrefsAndCleanUpPreventsResurrectionAfterReset_tstCon056_1() = runTest {
        val spValues = mutableMapOf<String, Any>(
            TrainingApplication.SP_CLUSTER_TOL_ENDPOINTS to 150f,
            TrainingApplication.SP_CLUSTER_TOL_APEX to 300f,
            TrainingApplication.SP_CLUSTER_TOL_DISTANCE to 0.15f,
            TrainingApplication.SP_CLUSTER_TOL_ALTITUDE_POS to 250f,
            TrainingApplication.SP_CLUSTER_USE_SPORT_TYPE to false,
            TrainingApplication.SP_CLUSTER_USE_ALTITUDE_POS to false,
            TrainingApplication.SP_CLUSTER_SCORE_THRESHOLD to 1.7f
        )
        val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { mockEditor.remove(any()) } answers {
            spValues.remove(firstArg())
            mockEditor
        }
        val mockSp = mockk<SharedPreferences>(relaxed = true)
        every { mockSp.contains(any()) } answers { spValues.containsKey(firstArg()) }
        every { mockSp.getFloat(any(), any()) } answers { (spValues[firstArg()] as? Float) ?: secondArg() }
        every { mockSp.getBoolean(any(), any()) } answers { (spValues[firstArg()] as? Boolean) ?: secondArg() }
        every { mockSp.edit() } returns mockEditor

        val migration = TuningPreferencesDataStore.createPreferencesMigration(sharedPreferencesProvider = { mockSp })

        assertTrue(migration.shouldMigrate(emptyPreferences()))
        val migrated = migration.migrate(emptyPreferences())
        assertEquals(150f, migrated[TuningPreferencesDataStore.KEY_CLUSTER_TOL_ENDPOINTS] ?: 0f, 0.001f)
        assertEquals(300f, migrated[TuningPreferencesDataStore.KEY_CLUSTER_TOL_APEX] ?: 0f, 0.001f)
        assertEquals(0.15f, migrated[TuningPreferencesDataStore.KEY_CLUSTER_TOL_DISTANCE] ?: 0f, 0.001f)
        assertEquals(250f, migrated[TuningPreferencesDataStore.KEY_CLUSTER_TOL_ALTITUDE_POS] ?: 0f, 0.001f)
        assertEquals(false, migrated[TuningPreferencesDataStore.KEY_CLUSTER_USE_SPORT_TYPE])
        assertEquals(false, migrated[TuningPreferencesDataStore.KEY_CLUSTER_USE_ALTITUDE_POS])
        assertEquals(1.7f, migrated[TuningPreferencesDataStore.KEY_CLUSTER_SCORE_THRESHOLD] ?: 0f, 0.001f)

        // Post-commit cleanUp removes legacy SP_CLUSTER_* keys so resetToDefaults() can never resurrect them
        migration.cleanUp()
        assertTrue("All SP_CLUSTER_* keys must be deleted in cleanUp()", spValues.isEmpty())
        assertFalse("After cleanUp + resetToDefaults (emptyPreferences), migration must NOT re-run", migration.shouldMigrate(emptyPreferences()))
    }

    @Test
    fun mutatorsMaintainSingleSourceOfTruthAcrossAllFlowsAndConfig_tstCon056_1() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val inMemory = InMemoryPreferencesDataStore()
        val store = TuningPreferencesDataStore.createForTesting(
            context = mockContext,
            testScope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            dataStoreOverride = inMemory
        )

        // Switching updateRadarAutoLightControl(false) sets MANUAL and immediately clears radarLightAutoMode
        store.updateRadarAutoLightControl(false)
        assertEquals(RadarLightControlMode.MANUAL, store.getTuningConfig().radarLightControlMode)
        assertFalse(store.getTuningConfig().radarLightAutoMode)
        assertFalse(store.getTuningConfig().radarAutoLightControl)
        assertFalse(store.getTuningConfig().radarSmartLightAutomation)
        assertFalse(store.radarLightAutoModeFlow.first())
        assertEquals(RadarLightControlMode.MANUAL, store.radarLightControlModeFlow.first())

        // Switching to FIXED_WORKOUT_MODE + SOLID keeps all projections consistent
        store.updateRadarLightSelection(isAuto = false, mode = RadarLightMode.SOLID)
        assertEquals(RadarLightControlMode.FIXED_WORKOUT_MODE, store.getTuningConfig().radarLightControlMode)
        assertEquals(RadarLightMode.SOLID, store.getTuningConfig().radarLightDefaultMode)
        assertFalse(store.getTuningConfig().radarLightAutoMode)
        assertTrue(store.getTuningConfig().radarAutoLightControl)
        assertFalse(store.getTuningConfig().radarSmartLightAutomation)

        // Switching back to SMART_AUTOMATION keeps all projections consistent
        store.updateRadarLightControlMode(RadarLightControlMode.SMART_AUTOMATION)
        assertEquals(RadarLightControlMode.SMART_AUTOMATION, store.getTuningConfig().radarLightControlMode)
        assertTrue(store.getTuningConfig().radarLightAutoMode)
        assertFalse(store.getTuningConfig().radarAutoLightControl)
        assertTrue(store.getTuningConfig().radarSmartLightAutomation)
    }
}
