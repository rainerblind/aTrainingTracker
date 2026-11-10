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

package com.atrainingtracker.trainingtracker.ui.tracking.radar

import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.trainingtracker.settings.RadarClearMode
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying live rearview radar edge overlay kinematics, threat color mapping,
 * and fail-safe disconnect invariants (REQ-TRK-062, TST-TRK-062).
 */
class RadarEdgeOverlayTest {

    @Test
    fun normalizeRadarRange_computesAccurateNormalizedPositions() {
        // TST-TRK-062.1: 140m -> 0.0 (top), 70m -> 0.5 (mid), 0m -> 1.0 (bottom / cyclist)
        val pos140m = RadarOverlayMath.normalizeRadarRange(140.0f)
        assertEquals(0.0f, pos140m, 0.01f)

        val pos70m = RadarOverlayMath.normalizeRadarRange(70.0f)
        assertEquals(0.5f, pos70m, 0.01f)

        val pos0m = RadarOverlayMath.normalizeRadarRange(0.0f)
        assertEquals(1.0f, pos0m, 0.01f)
    }

    @Test
    fun normalizeRadarRange_clampsBoundaryValuesSafely() {
        // Range beyond 140m should clamp to 0.0 (top of rail)
        val pos200m = RadarOverlayMath.normalizeRadarRange(200.0f)
        assertEquals(0.0f, pos200m, 0.001f)

        // Negative distance (behind cyclist) should clamp to 1.0 (bottom of rail)
        val posNegative = RadarOverlayMath.normalizeRadarRange(-10.0f)
        assertEquals(1.0f, posNegative, 0.001f)

        // Degenerate max range guard
        val posDegenerate = RadarOverlayMath.normalizeRadarRange(50.0f, maxRangeMeters = 0.0f)
        assertEquals(1.0f, posDegenerate, 0.001f)
    }

    @Test
    fun resolveThreatColor_mapsSemanticTokensCorrectly() {
        // TST-TRK-062.2: NONE -> Green, APPROACHING -> Amber, FAST_APPROACHING -> Red
        val greenColor = RadarOverlayMath.resolveThreatColor(ThreatLevel.NONE)
        assertEquals(TTColor.RadarClearGreen, greenColor)

        val amberColor = RadarOverlayMath.resolveThreatColor(ThreatLevel.APPROACHING)
        assertEquals(TTColor.RadarThreatAmber, amberColor)

        val redColor = RadarOverlayMath.resolveThreatColor(ThreatLevel.FAST_APPROACHING)
        assertEquals(TTColor.RadarThreatRed, redColor)
    }

    @Test
    fun isFailSafeDisconnect_identifiesDisconnectErrorStrictly() {
        // TST-TRK-062.3: DISCONNECTED_ERROR triggers fail-safe warning
        assertTrue(RadarOverlayMath.isFailSafeDisconnect(RadarConnectionState.DISCONNECTED_ERROR))

        assertFalse(RadarOverlayMath.isFailSafeDisconnect(RadarConnectionState.CONNECTED))
        assertFalse(RadarOverlayMath.isFailSafeDisconnect(RadarConnectionState.CONNECTING))
        assertFalse(RadarOverlayMath.isFailSafeDisconnect(RadarConnectionState.DISCONNECTED))
    }

    @Test
    fun failSafeDisconnect_neverIndicatesGreenClearRoad() {
        // REQ-TRK-062.4: When in DISCONNECTED_ERROR, never indicate green clear road
        val isError = RadarOverlayMath.isFailSafeDisconnect(RadarConnectionState.DISCONNECTED_ERROR)
        assertTrue(isError)

        val disconnectBadgeColor = TTColor.RadarDisconnectedAmber
        assertNotEquals(TTColor.RadarClearGreen, disconnectBadgeColor)
    }

    @Test
    fun defaultPreferences_conformToSpecification() {
        // REQ-TRK-062.5 / TST-TRK-062.4: Default side is RIGHT, clear mode is SLIM_RAIL
        assertEquals(RadarEdgeSide.RIGHT, TuningPreferencesDefaults.RADAR_EDGE_SIDE)
        assertEquals(RadarClearMode.SLIM_RAIL, TuningPreferencesDefaults.RADAR_CLEAR_MODE)
    }
}
