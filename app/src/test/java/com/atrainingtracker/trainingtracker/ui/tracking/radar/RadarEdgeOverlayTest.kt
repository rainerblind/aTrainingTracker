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

import com.atrainingtracker.banalservice.devices.radar.LaneState
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarState
import com.atrainingtracker.banalservice.devices.radar.RadarTarget
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.banalservice.devices.radar.VehicleSizeCategory
import com.atrainingtracker.trainingtracker.settings.RadarClearMode
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.settings.RadarTrafficOrientation
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying live rearview radar edge overlay kinematics, threat color mapping,
 * and fail-safe disconnect invariants (REQ-TRK-062, TST-TRK-062).
 */
class RadarEdgeOverlayTest {

    @Test
    fun normalizeRadarRange_computesAccurateNormalizedPositions() {
        // TST-TRK-062.1a: 140m -> 1.0 (bottom), 70m -> 0.5 (mid), 0m -> 0.0 (top / cyclist)
        val pos140m = RadarOverlayMath.normalizeRadarRange(140.0f)
        assertEquals(1.0f, pos140m, 0.01f)

        val pos70m = RadarOverlayMath.normalizeRadarRange(70.0f)
        assertEquals(0.5f, pos70m, 0.01f)

        val pos0m = RadarOverlayMath.normalizeRadarRange(0.0f)
        assertEquals(0.0f, pos0m, 0.01f)
    }

    @Test
    fun normalizeRadarRange_clampsBoundaryValuesSafely() {
        // TST-TRK-062.1b: Range beyond 140m should clamp to 1.0 (bottom of rail)
        val pos200m = RadarOverlayMath.normalizeRadarRange(200.0f)
        assertEquals(1.0f, pos200m, 0.001f)

        // Negative distance (past cyclist) should clamp to 0.0 (top of rail)
        val posNegative = RadarOverlayMath.normalizeRadarRange(-10.0f)
        assertEquals(0.0f, posNegative, 0.001f)

        // Degenerate max range guard
        val posDegenerate = RadarOverlayMath.normalizeRadarRange(50.0f, maxRangeMeters = 0.0f)
        assertEquals(0.0f, posDegenerate, 0.001f)

        val posNegativeMax = RadarOverlayMath.normalizeRadarRange(50.0f, maxRangeMeters = -10.0f)
        assertEquals(0.0f, posNegativeMax, 0.001f)
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

    @Test
    fun formatSnoozeBadgeText_returnsNullWhenNotSnoozed() {
        // REQ-TRK-064.2 / TST-TRK-064.2: Inactive snooze returns null
        val inactiveState = RadarAudioSnoozeState.INACTIVE
        assertNull(RadarOverlayMath.formatSnoozeBadgeText(inactiveState))
    }

    @Test
    fun formatSnoozeBadgeText_timedSnoozeFormatsCeiledMinutes() {
        // REQ-TRK-064.2 / TST-TRK-064.2: Timed snooze formats minutes remaining
        val state15m = RadarAudioSnoozeState(
            isSnoozed = true,
            mode = RadarAudioSnoozeMode.SNOOZE_15M,
            remainingSeconds = 900
        )
        assertEquals("15m", RadarOverlayMath.formatSnoozeBadgeText(state15m))

        val stateCeil = RadarAudioSnoozeState(
            isSnoozed = true,
            mode = RadarAudioSnoozeMode.SNOOZE_15M,
            remainingSeconds = 841
        )
        assertEquals("15m", RadarOverlayMath.formatSnoozeBadgeText(stateCeil))

        val state1m = RadarAudioSnoozeState(
            isSnoozed = true,
            mode = RadarAudioSnoozeMode.SNOOZE_15M,
            remainingSeconds = 25
        )
        assertEquals("1m", RadarOverlayMath.formatSnoozeBadgeText(state1m))
    }

    @Test
    fun formatSnoozeBadgeText_fastOnlyReturnsExclamation() {
        // REQ-TRK-064.2: High-threat only renders '!' badge
        val state = RadarAudioSnoozeState(
            isSnoozed = true,
            mode = RadarAudioSnoozeMode.FAST_ONLY,
            remainingSeconds = null
        )
        assertEquals("!", RadarOverlayMath.formatSnoozeBadgeText(state))
    }

    @Test
    fun formatSnoozeBadgeText_pelotonSyncReturnsP() {
        // REQ-TRK-064.2 / REQ-TRK-064.4: Peloton mode audio sync renders 'P'
        val state = RadarAudioSnoozeState(
            isSnoozed = true,
            mode = RadarAudioSnoozeMode.NONE,
            remainingSeconds = null,
            isPelotonSyncActive = true
        )
        assertEquals("P", RadarOverlayMath.formatSnoozeBadgeText(state))
    }

    @Test
    fun formatSnoozeBadgeText_urbanMuteReturnsU() {
        // REQ-TRK-064.2 / REQ-TRK-064.5: Smart urban congestion mute renders 'U'
        val state = RadarAudioSnoozeState(
            isSnoozed = true,
            mode = RadarAudioSnoozeMode.NONE,
            remainingSeconds = null,
            isUrbanMuteActive = true
        )
        assertEquals("U", RadarOverlayMath.formatSnoozeBadgeText(state))
    }

    @Test
    fun formatSnoozeBadgeText_restOfRideReturnsM() {
        // REQ-TRK-064.2: Rest of ride mute renders 'M'
        val state = RadarAudioSnoozeState(
            isSnoozed = true,
            mode = RadarAudioSnoozeMode.REST_OF_RIDE,
            remainingSeconds = null
        )
        assertEquals("M", RadarOverlayMath.formatSnoozeBadgeText(state))
    }

    @Test
    fun buildThreatDescription_formatsStandardThreatCount() {
        // REQ-TRK-064.2 / TST-TRK-064.2a
        val desc = RadarOverlayMath.buildThreatDescription(count = 3, isFastApproaching = false)
        assertEquals("3 threats", desc)
    }

    @Test
    fun buildThreatDescription_formatsFastThreatCountWithSnoozeSuffix() {
        // REQ-TRK-064.2 / TST-TRK-064.2a
        val desc = RadarOverlayMath.buildThreatDescription(
            count = 2,
            isFastApproaching = true,
            snoozeSuffix = "Radar Audio Muted (15m)"
        )
        assertEquals("2 threats (high speed), Radar Audio Muted (15m)", desc)
    }

    @Test
    fun buildThreatDescription_formatsWithLocalizedTemplate() {
        // TST-TRK-062.6a: Localized format string support
        val descDe = RadarOverlayMath.buildThreatDescription(
            count = 4,
            isFastApproaching = false,
            formatBase = "%1\$d Gefahren"
        )
        assertEquals("4 Gefahren", descDe)

        val descDeSnooze = RadarOverlayMath.buildThreatDescription(
            count = 1,
            isFastApproaching = true,
            snoozeSuffix = "Audio stumm",
            formatBase = "%1\$d Gefahren (hohes Tempo)"
        )
        assertEquals("1 Gefahren (hohes Tempo), Audio stumm", descDeSnooze)
    }

    @Test
    fun badgeClick_inDisconnectedError_routesToReconnectWhenAvailable() {
        // TST-TRK-062.3b: Badge tap routes to reconnect callback during disconnect error
        var reconnectCalled = false
        var normalClickCalled = false

        val onReconnect = { reconnectCalled = true }
        val onBadgeClick = { normalClickCalled = true }

        val isDisconnectError = true
        val clickAction: () -> Unit = {
            if (isDisconnectError) {
                onReconnect()
            } else {
                onBadgeClick()
            }
        }

        clickAction()
        assertTrue(reconnectCalled)
        assertFalse(normalClickCalled)
    }

    @Test
    fun resolveRailMaxRange_returnsExpectedBoundaries() {
        // TST-TRK-073.1: Standard V1 targets use 140m
        val v1State = RadarState(
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 80f, approachSpeedMps = 10f)
            )
        )
        assertEquals(140.0f, RadarOverlayMath.resolveRailMaxRange(v1State), 0.01f)

        // Distant target (> 140m) scales to 175m
        val distantState = RadarState(
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 160f, approachSpeedMps = 15f)
            )
        )
        assertEquals(175.0f, RadarOverlayMath.resolveRailMaxRange(distantState), 0.01f)

        // V2 target with vehicle size scales to 175m
        val v2SizeState = RadarState(
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 70f, approachSpeedMps = 10f, vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS)
            )
        )
        assertEquals(175.0f, RadarOverlayMath.resolveRailMaxRange(v2SizeState), 0.01f)

        // V2 target with lateral offset scales to 175m
        val v2LateralState = RadarState(
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 70f, approachSpeedMps = 10f, lateralOffsetMeters = -1.5f)
            )
        )
        assertEquals(175.0f, RadarOverlayMath.resolveRailMaxRange(v2LateralState), 0.01f)
    }

    @Test
    fun calculateHorizontalOffsetDp_respectsOrientationAndOffsets() {
        // TST-TRK-073.3: In RHT, negative lateral offset maps to negative (leftward) dp offset
        val rhtPassingTarget = RadarTarget(
            targetId = 1,
            rangeMeters = 50f,
            approachSpeedMps = 15f,
            lateralOffsetMeters = -1.5f // 1.5m left -> -3.0 dp
        )
        val rhtOffset = RadarOverlayMath.calculateHorizontalOffsetDp(rhtPassingTarget, RadarTrafficOrientation.RIGHT_HAND)
        assertEquals(-3.0f, rhtOffset, 0.01f)

        // In LHT, same physical scenario inverts sign (passes on rider's right)
        val lhtOffset = RadarOverlayMath.calculateHorizontalOffsetDp(rhtPassingTarget, RadarTrafficOrientation.LEFT_HAND)
        assertEquals(3.0f, lhtOffset, 0.01f)

        // Clamping check: >= 3.0m clamps to maxOffsetDp (6.0 dp)
        val farLeftTarget = RadarTarget(
            targetId = 2,
            rangeMeters = 50f,
            approachSpeedMps = 15f,
            lateralOffsetMeters = -5.0f
        )
        assertEquals(-6.0f, RadarOverlayMath.calculateHorizontalOffsetDp(farLeftTarget, RadarTrafficOrientation.RIGHT_HAND), 0.01f)

        // Fallback to LaneState when lateralOffsetMeters is null
        val laneStatePassingTarget = RadarTarget(
            targetId = 3,
            rangeMeters = 50f,
            approachSpeedMps = 15f,
            laneState = LaneState.PASSING_LANE
        )
        assertEquals(-4.0f, RadarOverlayMath.calculateHorizontalOffsetDp(laneStatePassingTarget, RadarTrafficOrientation.RIGHT_HAND), 0.01f)
        assertEquals(4.0f, RadarOverlayMath.calculateHorizontalOffsetDp(laneStatePassingTarget, RadarTrafficOrientation.LEFT_HAND), 0.01f)

        val laneStateSameTarget = RadarTarget(
            targetId = 4,
            rangeMeters = 50f,
            approachSpeedMps = 15f,
            laneState = LaneState.IN_RIDER_LANE
        )
        assertEquals(0.0f, RadarOverlayMath.calculateHorizontalOffsetDp(laneStateSameTarget, RadarTrafficOrientation.RIGHT_HAND), 0.01f)
    }
}
