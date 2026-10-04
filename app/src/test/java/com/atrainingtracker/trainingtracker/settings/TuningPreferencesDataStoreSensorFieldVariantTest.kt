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

import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for cockpit sensor field variant preference model, defaults, and DataStore key configuration (TST-UI-217.1, TST-UI-217.3, REQ-UI-258).
 */
class TuningPreferencesDataStoreSensorFieldVariantTest {

    @Test
    fun defaultConstants_matchClassicSeamlessProductionBaseline() {
        assertEquals(SensorFieldVariant.CLASSIC_SEAMLESS, TuningPreferencesDefaults.SENSOR_FIELD_VARIANT)
    }

    @Test
    fun tuningConfig_defaultsToClassicSeamless() {
        val config = TuningConfig()
        assertEquals(SensorFieldVariant.CLASSIC_SEAMLESS, config.sensorFieldVariant)
    }

    @Test
    fun tuningConfig_customSensorFieldVariant_retained() {
        val config = TuningConfig(sensorFieldVariant = SensorFieldVariant.ELEVATED_CARDS)
        assertEquals(SensorFieldVariant.ELEVATED_CARDS, config.sensorFieldVariant)
    }

    @Test
    fun dataStoreKey_hasExpectedName() {
        assertEquals("tuning_sensor_field_variant", TuningPreferencesDataStore.KEY_SENSOR_FIELD_VARIANT.name)
    }

    @Test
    fun allKeys_includesSensorFieldVariantKeyForAtomicReset() {
        assertTrue(
            "ALL_KEYS must include KEY_SENSOR_FIELD_VARIANT for atomic factory reset",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_SENSOR_FIELD_VARIANT)
        )
    }
}
