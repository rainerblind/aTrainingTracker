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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification tests for close-pass warning distance preferences (TST-TRK-073.4 / REQ-TRK-073.4).
 */
class TuningPreferencesDataStoreRadarClosePassTest {

    @Test
    fun defaultClosePassThreshold_isOnePointFiveMeters() {
        // REQ-TRK-073.4: Default close-pass threshold is 1.5m (StVO urban safe passing distance)
        assertEquals(1.5f, TuningPreferencesDefaults.RADAR_CLOSE_PASS_THRESHOLD_M, 0.001f)
    }

    @Test
    fun tuningConfig_defaultsToStandardClosePassThreshold() {
        val config = TuningConfig()
        assertEquals(1.5f, config.radarClosePassThresholdM, 0.001f)
    }

    @Test
    fun tuningConfig_customClosePassThreshold_retained() {
        val config1m = TuningConfig(radarClosePassThresholdM = 1.0f)
        assertEquals(1.0f, config1m.radarClosePassThresholdM, 0.001f)

        val config2m = TuningConfig(radarClosePassThresholdM = 2.0f)
        assertEquals(2.0f, config2m.radarClosePassThresholdM, 0.001f)
    }

    @Test
    fun preferenceKey_isRegisteredInAllKeys() {
        assertTrue(
            "KEY_RADAR_CLOSE_PASS_THRESHOLD must be registered in ALL_KEYS for backup and reset",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_RADAR_CLOSE_PASS_THRESHOLD)
        )
    }
}
