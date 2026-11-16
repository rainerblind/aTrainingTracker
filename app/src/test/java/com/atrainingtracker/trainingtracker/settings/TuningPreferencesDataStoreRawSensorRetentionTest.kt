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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification for raw sensor telemetry retention policy preferences (TST-SET-119.4 / REQ-SET-117.3).
 */
class TuningPreferencesDataStoreRawSensorRetentionTest {

    // TST-SET-119.4
    @Test
    fun defaultConstants_matchSpecification() {
        assertEquals(
            RawSensorRetentionPolicy.DAYS_30,
            TuningPreferencesDefaults.RAW_SENSOR_RETENTION_POLICY
        )
        val defaultConfig = TuningConfig()
        assertEquals(
            RawSensorRetentionPolicy.DAYS_30,
            defaultConfig.rawSensorRetentionPolicy
        )
    }

    // TST-SET-119.4
    @Test
    fun preferenceKey_isRegisteredInAllKeys() {
        assertTrue(
            "KEY_RAW_SENSOR_RETENTION_POLICY must be registered in ALL_KEYS for factory reset",
            TuningPreferencesDataStore.ALL_KEYS.contains(
                TuningPreferencesDataStore.KEY_RAW_SENSOR_RETENTION_POLICY
            )
        )
    }

    // TST-SET-119.4
    @Test
    fun enumValues_haveCorrectDayValues() {
        assertEquals(30, RawSensorRetentionPolicy.DAYS_30.days)
        assertEquals(-1, RawSensorRetentionPolicy.KEEP_ALL.days)
        assertEquals(7, RawSensorRetentionPolicy.DAYS_7.days)
        assertEquals(0, RawSensorRetentionPolicy.OFF.days)
    }
}
