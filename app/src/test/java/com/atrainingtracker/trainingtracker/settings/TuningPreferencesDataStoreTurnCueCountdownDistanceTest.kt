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
 * Unit tests for turn cue countdown distance preference model, default constants, bounds,
 * and key registration (TST-UI-247 / REQ-UI-287 / ATT-3321).
 */
class TuningPreferencesDataStoreTurnCueCountdownDistanceTest {

    @Test
    fun defaultConstants_matchExpectedSpecification() {
        assertEquals(100.0f, TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS, 0.001f)
        assertEquals(30.0f, TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS, 0.001f)
        assertEquals(300.0f, TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS, 0.001f)
    }

    @Test
    fun tuningConfig_defaultsTo100Meters() {
        val config = TuningConfig()
        assertEquals(100.0f, config.turnCueCountdownDistanceMeters, 0.001f)
    }

    @Test
    fun tuningConfig_customDistance_retained() {
        val config = TuningConfig(turnCueCountdownDistanceMeters = 200.0f)
        assertEquals(200.0f, config.turnCueCountdownDistanceMeters, 0.001f)
    }

    @Test
    fun tuningConfig_clampingBoundsEnforced() {
        val belowMin = 15.0f.coerceIn(
            TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS,
            TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS
        )
        val aboveMax = 500.0f.coerceIn(
            TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS,
            TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS
        )
        val withinRange = 150.0f.coerceIn(
            TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS,
            TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS
        )

        assertEquals(30.0f, belowMin, 0.001f)
        assertEquals(300.0f, aboveMax, 0.001f)
        assertEquals(150.0f, withinRange, 0.001f)
    }

    @Test
    fun allKeys_containsTurnCueCountdownDistanceKey() {
        assertTrue(
            "ALL_KEYS must contain KEY_TURN_CUE_COUNTDOWN_DISTANCE for complete factory reset",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_TURN_CUE_COUNTDOWN_DISTANCE)
        )
        assertEquals(
            "tuning_turn_cue_countdown_distance",
            TuningPreferencesDataStore.KEY_TURN_CUE_COUNTDOWN_DISTANCE.name
        )
    }
}
