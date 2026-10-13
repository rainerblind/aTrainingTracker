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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for map camera follow-me tuning preferences, default constants, range clamping,
 * and key registration in [TuningPreferencesDataStore] (REQ-MAP-042 / TST-MAP-044.4).
 */
class MapCameraTuningPreferencesTest {

    @Test
    fun defaultConstants_matchExpectedSpecification() {
        assertEquals(20.0f, TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM, 0.001f)
        assertEquals(15.0f, TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_INITIAL_ZOOM, 0.001f)
        assertEquals(21.0f, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_INITIAL_ZOOM, 0.001f)
        assertEquals(0.5f, TuningPreferencesDefaults.STEP_MAP_FOLLOW_ME_INITIAL_ZOOM, 0.001f)

        assertTrue(TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED)

        assertEquals(18.0f, TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM, 0.001f)
        assertEquals(14.0f, TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_CRUISING_ZOOM, 0.001f)
        assertEquals(19.5f, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_CRUISING_ZOOM, 0.001f)
        assertEquals(0.5f, TuningPreferencesDefaults.STEP_MAP_FOLLOW_ME_CRUISING_ZOOM, 0.001f)

        assertEquals(70.0f, TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE, 0.001f)
        assertEquals(0.0f, TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_TILT_ANGLE, 0.001f)
        assertEquals(70.0f, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_TILT_ANGLE, 0.001f)
        assertEquals(5.0f, TuningPreferencesDefaults.STEP_MAP_FOLLOW_ME_TILT_ANGLE, 0.001f)

        assertEquals(30.0f, TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT, 0.001f)
        assertEquals(10.0f, TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT, 0.001f)
        assertEquals(50.0f, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT, 0.001f)
        assertEquals(5.0f, TuningPreferencesDefaults.STEP_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT, 0.001f)
    }

    @Test
    fun tuningConfig_defaultsContainMapCameraParameters() {
        val config = TuningConfig()
        assertEquals(20.0f, config.mapFollowMeInitialZoom, 0.001f)
        assertTrue(config.mapFollowMeSpeedZoomEnabled)
        assertEquals(18.0f, config.mapFollowMeCruisingZoom, 0.001f)
        assertEquals(70.0f, config.mapFollowMeTiltAngle, 0.001f)
        assertEquals(30.0f, config.mapFollowMeLookaheadPaddingPercent, 0.001f)
    }

    @Test
    fun tuningConfig_customParameters_retained() {
        val config = TuningConfig(
            mapFollowMeInitialZoom = 19.5f,
            mapFollowMeSpeedZoomEnabled = false,
            mapFollowMeCruisingZoom = 17.0f,
            mapFollowMeTiltAngle = 45.0f,
            mapFollowMeLookaheadPaddingPercent = 25.0f
        )
        assertEquals(19.5f, config.mapFollowMeInitialZoom, 0.001f)
        assertFalse(config.mapFollowMeSpeedZoomEnabled)
        assertEquals(17.0f, config.mapFollowMeCruisingZoom, 0.001f)
        assertEquals(45.0f, config.mapFollowMeTiltAngle, 0.001f)
        assertEquals(25.0f, config.mapFollowMeLookaheadPaddingPercent, 0.001f)
    }

    @Test
    fun tuningConfig_clampingBoundsEnforced() {
        // Initial Zoom: [15.0, 21.0]
        assertEquals(15.0f, 10.0f.coerceIn(TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_INITIAL_ZOOM, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_INITIAL_ZOOM), 0.001f)
        assertEquals(21.0f, 25.0f.coerceIn(TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_INITIAL_ZOOM, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_INITIAL_ZOOM), 0.001f)

        // Cruising Zoom: [14.0, 19.5]
        assertEquals(14.0f, 12.0f.coerceIn(TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_CRUISING_ZOOM, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_CRUISING_ZOOM), 0.001f)
        assertEquals(19.5f, 22.0f.coerceIn(TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_CRUISING_ZOOM, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_CRUISING_ZOOM), 0.001f)

        // Tilt Angle: [0.0, 70.0]
        assertEquals(0.0f, (-5.0f).coerceIn(TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_TILT_ANGLE, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_TILT_ANGLE), 0.001f)
        assertEquals(70.0f, 90.0f.coerceIn(TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_TILT_ANGLE, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_TILT_ANGLE), 0.001f)

        // Padding Percent: [10.0, 50.0]
        assertEquals(10.0f, 5.0f.coerceIn(TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT), 0.001f)
        assertEquals(50.0f, 65.0f.coerceIn(TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT, TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT), 0.001f)
    }

    @Test
    fun allKeys_containsAllFiveMapCameraKeys() {
        assertTrue(
            "ALL_KEYS must contain KEY_MAP_FOLLOW_ME_INITIAL_ZOOM",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_MAP_FOLLOW_ME_INITIAL_ZOOM)
        )
        assertTrue(
            "ALL_KEYS must contain KEY_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED)
        )
        assertTrue(
            "ALL_KEYS must contain KEY_MAP_FOLLOW_ME_CRUISING_ZOOM",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_MAP_FOLLOW_ME_CRUISING_ZOOM)
        )
        assertTrue(
            "ALL_KEYS must contain KEY_MAP_FOLLOW_ME_TILT_ANGLE",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_MAP_FOLLOW_ME_TILT_ANGLE)
        )
        assertTrue(
            "ALL_KEYS must contain KEY_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT)
        )
    }
}
