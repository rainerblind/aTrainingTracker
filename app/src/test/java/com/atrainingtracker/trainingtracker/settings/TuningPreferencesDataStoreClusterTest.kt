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
 * Unit tests for workout route clustering parameter defaults, clamping bounds,
 * and TuningConfig integration (TST-UI-318.3 / REQ-SET-075 / ATT-3355).
 */
class TuningPreferencesDataStoreClusterTest {

    @Test
    fun defaultConstants_matchExpectedSpecification() {
        assertEquals(200.0f, TuningPreferencesDefaults.CLUSTER_TOL_ENDPOINTS, 0.001f)
        assertEquals(10.0f, TuningPreferencesDefaults.MIN_CLUSTER_TOL_ENDPOINTS, 0.001f)
        assertEquals(500.0f, TuningPreferencesDefaults.MAX_CLUSTER_TOL_ENDPOINTS, 0.001f)

        assertEquals(400.0f, TuningPreferencesDefaults.CLUSTER_TOL_APEX, 0.001f)
        assertEquals(10.0f, TuningPreferencesDefaults.MIN_CLUSTER_TOL_APEX, 0.001f)
        assertEquals(500.0f, TuningPreferencesDefaults.MAX_CLUSTER_TOL_APEX, 0.001f)

        assertEquals(0.20f, TuningPreferencesDefaults.CLUSTER_TOL_DISTANCE, 0.001f)
        assertEquals(0.01f, TuningPreferencesDefaults.MIN_CLUSTER_TOL_DISTANCE, 0.001f)
        assertEquals(0.20f, TuningPreferencesDefaults.MAX_CLUSTER_TOL_DISTANCE, 0.001f)

        assertEquals(400.0f, TuningPreferencesDefaults.CLUSTER_TOL_ALTITUDE_POS, 0.001f)
        assertEquals(10.0f, TuningPreferencesDefaults.MIN_CLUSTER_TOL_ALTITUDE_POS, 0.001f)
        assertEquals(500.0f, TuningPreferencesDefaults.MAX_CLUSTER_TOL_ALTITUDE_POS, 0.001f)

        assertTrue(TuningPreferencesDefaults.CLUSTER_USE_SPORT_TYPE)
        assertTrue(TuningPreferencesDefaults.CLUSTER_USE_ALTITUDE_POS)
    }

    @Test
    fun tuningConfig_defaultsMatchConstants() {
        val config = TuningConfig()
        assertEquals(200.0f, config.clusterTolEndpoints, 0.001f)
        assertEquals(400.0f, config.clusterTolApex, 0.001f)
        assertEquals(0.20f, config.clusterTolDistance, 0.001f)
        assertEquals(400.0f, config.clusterTolAltitudePos, 0.001f)
        assertTrue(config.clusterUseSportType)
        assertTrue(config.clusterUseAltitudePos)
    }

    @Test
    fun tuningConfig_customValuesRetained() {
        val config = TuningConfig(
            clusterTolEndpoints = 150f,
            clusterTolApex = 350f,
            clusterTolDistance = 0.15f,
            clusterTolAltitudePos = 300f,
            clusterUseSportType = false,
            clusterUseAltitudePos = false
        )
        assertEquals(150.0f, config.clusterTolEndpoints, 0.001f)
        assertEquals(350.0f, config.clusterTolApex, 0.001f)
        assertEquals(0.15f, config.clusterTolDistance, 0.001f)
        assertEquals(300.0f, config.clusterTolAltitudePos, 0.001f)
        assertEquals(false, config.clusterUseSportType)
        assertEquals(false, config.clusterUseAltitudePos)
    }

    @Test
    fun tuningConfig_clampingBoundsEnforced() {
        val clampedEndpoint = 600f.coerceIn(
            TuningPreferencesDefaults.MIN_CLUSTER_TOL_ENDPOINTS,
            TuningPreferencesDefaults.MAX_CLUSTER_TOL_ENDPOINTS
        )
        assertEquals(500f, clampedEndpoint, 0.001f)

        val clampedApex = 5f.coerceIn(
            TuningPreferencesDefaults.MIN_CLUSTER_TOL_APEX,
            TuningPreferencesDefaults.MAX_CLUSTER_TOL_APEX
        )
        assertEquals(10f, clampedApex, 0.001f)

        val clampedDistance = 0.30f.coerceIn(
            TuningPreferencesDefaults.MIN_CLUSTER_TOL_DISTANCE,
            TuningPreferencesDefaults.MAX_CLUSTER_TOL_DISTANCE
        )
        assertEquals(0.20f, clampedDistance, 0.001f)

        val clampedAlt = 0f.coerceIn(
            TuningPreferencesDefaults.MIN_CLUSTER_TOL_ALTITUDE_POS,
            TuningPreferencesDefaults.MAX_CLUSTER_TOL_ALTITUDE_POS
        )
        assertEquals(10f, clampedAlt, 0.001f)
    }
}
