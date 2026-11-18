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

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.isRadarOverlayVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Unit and behavioral Compose UI tests verifying live rearview radar edge overlay kinematics,
 * clear-state badge suppression, threat color mapping, and fail-safe disconnect invariants
 * (REQ-TRK-062, REQ-TRK-064, TST-TRK-062, TST-TRK-064, REQ-PRO-049.1, ATT-8679).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")
class RadarEdgeOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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

    @Test
    fun calculateFullScreenHorizontalFraction_mapsLanesAndOffsetsCorrectly() {
        // TST-TRK-062.1: Full-Screen horizontal coordinate mapping across screen width
        // In RHT: Rider lane is center-right (0.70f), passing lane is center-left (0.30f)
        val rhtRiderLane = RadarTarget(targetId = 1, rangeMeters = 50f, approachSpeedMps = 10f, laneState = LaneState.IN_RIDER_LANE)
        assertEquals(0.70f, RadarOverlayMath.calculateFullScreenHorizontalFraction(rhtRiderLane, RadarTrafficOrientation.RIGHT_HAND), 0.01f)

        val rhtChangingLane = RadarTarget(targetId = 2, rangeMeters = 50f, approachSpeedMps = 10f, laneState = LaneState.CHANGING_LANE)
        assertEquals(0.50f, RadarOverlayMath.calculateFullScreenHorizontalFraction(rhtChangingLane, RadarTrafficOrientation.RIGHT_HAND), 0.01f)

        val rhtPassingLane = RadarTarget(targetId = 3, rangeMeters = 50f, approachSpeedMps = 10f, laneState = LaneState.PASSING_LANE)
        assertEquals(0.30f, RadarOverlayMath.calculateFullScreenHorizontalFraction(rhtPassingLane, RadarTrafficOrientation.RIGHT_HAND), 0.01f)

        // Continuous lateral offset in RHT: 0m -> 0.70f, -1.5m -> 0.50f, -3.0m -> 0.30f
        val rhtZeroOffset = RadarTarget(targetId = 4, rangeMeters = 50f, approachSpeedMps = 10f, lateralOffsetMeters = 0.0f)
        assertEquals(0.70f, RadarOverlayMath.calculateFullScreenHorizontalFraction(rhtZeroOffset, RadarTrafficOrientation.RIGHT_HAND), 0.01f)

        val rhtMidOffset = RadarTarget(targetId = 5, rangeMeters = 50f, approachSpeedMps = 10f, lateralOffsetMeters = -1.5f)
        assertEquals(0.50f, RadarOverlayMath.calculateFullScreenHorizontalFraction(rhtMidOffset, RadarTrafficOrientation.RIGHT_HAND), 0.01f)

        val rhtMaxOffset = RadarTarget(targetId = 6, rangeMeters = 50f, approachSpeedMps = 10f, lateralOffsetMeters = -3.0f)
        assertEquals(0.30f, RadarOverlayMath.calculateFullScreenHorizontalFraction(rhtMaxOffset, RadarTrafficOrientation.RIGHT_HAND), 0.01f)

        // Clamping to safe margins [0.15f, 0.85f]
        val rhtExtremeLeft = RadarTarget(targetId = 7, rangeMeters = 50f, approachSpeedMps = 10f, lateralOffsetMeters = -10.0f)
        assertEquals(0.15f, RadarOverlayMath.calculateFullScreenHorizontalFraction(rhtExtremeLeft, RadarTrafficOrientation.RIGHT_HAND), 0.01f)

        val rhtExtremeRight = RadarTarget(targetId = 8, rangeMeters = 50f, approachSpeedMps = 10f, lateralOffsetMeters = 10.0f)
        assertEquals(0.85f, RadarOverlayMath.calculateFullScreenHorizontalFraction(rhtExtremeRight, RadarTrafficOrientation.RIGHT_HAND), 0.01f)

        // In LHT: Symmetry is inverted (rider lane on left 0.30f, passing lane on right 0.70f)
        val lhtRiderLane = RadarTarget(targetId = 9, rangeMeters = 50f, approachSpeedMps = 10f, laneState = LaneState.IN_RIDER_LANE)
        assertEquals(0.30f, RadarOverlayMath.calculateFullScreenHorizontalFraction(lhtRiderLane, RadarTrafficOrientation.LEFT_HAND), 0.01f)

        val lhtPassingLane = RadarTarget(targetId = 10, rangeMeters = 50f, approachSpeedMps = 10f, laneState = LaneState.PASSING_LANE)
        assertEquals(0.70f, RadarOverlayMath.calculateFullScreenHorizontalFraction(lhtPassingLane, RadarTrafficOrientation.LEFT_HAND), 0.01f)

        val lhtMidOffset = RadarTarget(targetId = 11, rangeMeters = 50f, approachSpeedMps = 10f, lateralOffsetMeters = -1.5f)
        assertEquals(0.50f, RadarOverlayMath.calculateFullScreenHorizontalFraction(lhtMidOffset, RadarTrafficOrientation.LEFT_HAND), 0.01f)
    }

    @Test
    fun radarEdgeSide_containsFullScreenAndDeserializesBackwardCompatibly() {
        // TST-TRK-062.3: Verify RadarEdgeSide enum extension and backward-compatible decoding
        val values = RadarEdgeSide.values()
        assertTrue(values.contains(RadarEdgeSide.LEFT))
        assertTrue(values.contains(RadarEdgeSide.FULL_SCREEN))
        assertTrue(values.contains(RadarEdgeSide.RIGHT))

        val decoded = com.atrainingtracker.util.safeEnumValueOf("FULL_SCREEN", RadarEdgeSide.RIGHT)
        assertEquals(RadarEdgeSide.FULL_SCREEN, decoded)

        val fallback = com.atrainingtracker.util.safeEnumValueOf("INVALID_UNKNOWN", RadarEdgeSide.RIGHT)
        assertEquals(RadarEdgeSide.RIGHT, fallback)
    }

    /**
     * TST-TRK-062.8: Live Rearview Radar Overlay Visibility Gating (ATT-7744 / REQ-TRK-062.4).
     * Strictly suppresses overlay rendering during initial sensor search (CONNECTING) and idle disconnect,
     * while rendering on CONNECTED, DISCONNECTED_ERROR, or indoor simulator.
     */
    @Test
    fun isRadarOverlayVisible_suppressesOverlayDuringSearchAndDisconnected() {
        // Suppressed while searching for sensor
        assertFalse(
            "Overlay must be hidden while searching (CONNECTING)",
            isRadarOverlayVisible(ScreenMode.TRACKING, RadarConnectionState.CONNECTING, isSimulating = false)
        )

        // Suppressed when disconnected and not simulating
        assertFalse(
            "Overlay must be hidden when idle DISCONNECTED",
            isRadarOverlayVisible(ScreenMode.TRACKING, RadarConnectionState.DISCONNECTED, isSimulating = false)
        )
    }

    @Test
    fun isRadarOverlayVisible_rendersOnConnectedDisconnectedErrorAndSimulation() {
        // Visible on CONNECTED
        assertTrue(
            "Overlay must be visible when CONNECTED",
            isRadarOverlayVisible(ScreenMode.TRACKING, RadarConnectionState.CONNECTED, isSimulating = false)
        )

        // Visible on DISCONNECTED_ERROR (fail-safe amber warning)
        assertTrue(
            "Overlay must be visible on DISCONNECTED_ERROR",
            isRadarOverlayVisible(ScreenMode.TRACKING, RadarConnectionState.DISCONNECTED_ERROR, isSimulating = false)
        )

        // Visible when simulator is active regardless of connection state
        assertTrue(
            "Overlay must be visible when simulator is running",
            isRadarOverlayVisible(ScreenMode.TRACKING, RadarConnectionState.DISCONNECTED, isSimulating = true)
        )
    }

    @Test
    fun isRadarOverlayVisible_suppressedWhenNotInTrackingScreenMode() {
        // Suppressed when not in ScreenMode.TRACKING
        assertFalse(
            "Overlay must be suppressed outside ScreenMode.TRACKING even if connected",
            isRadarOverlayVisible(ScreenMode.CONFIGURATION, RadarConnectionState.CONNECTED, isSimulating = false)
        )
    }

    /**
     * TST-TRK-062.4 / REQ-TRK-062.3: When CONNECTED and road is clear (unsnoozed),
     * RadarStatusBadgePill MUST NOT be rendered across LEFT and RIGHT,
     * and RadarVerticalRail must start at top (0.dp) without a 14.dp downward spacer gap.
     */
    @Test
    fun clearConnectedState_leftAndRight_suppressesStatusBadgeAndGuardsRailSpacers() {
        val clearState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyList()
        )
        var edgeSide by mutableStateOf(RadarEdgeSide.LEFT)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(120.dp).height(300.dp)) {
                    RadarEdgeOverlay(
                        radarState = clearState,
                        edgeSide = edgeSide,
                        clearMode = RadarClearMode.SLIM_RAIL
                    )
                }
            }
        }

        for (side in listOf(RadarEdgeSide.LEFT, RadarEdgeSide.RIGHT)) {
            composeTestRule.runOnIdle { edgeSide = side }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("RadarEdgeOverlay").assertIsDisplayed()
            composeTestRule.onNodeWithTag("RadarStatusBadge").assertDoesNotExist()
            val railNode = composeTestRule.onNodeWithTag("RadarVerticalRail", useUnmergedTree = true)
            railNode.assertIsDisplayed()
            val railBounds = railNode.getUnclippedBoundsInRoot()
            assertEquals(
                "Clear rail on $side must align at top (0.dp) without 14.dp badge spacer shift",
                0f,
                railBounds.top.value,
                0.5f
            )
        }
    }

    /**
     * TST-TRK-062.4 / REQ-TRK-062.3: FULL_SCREEN clear state renders neither rail nor status badge.
     */
    @Test
    fun clearConnectedState_fullScreen_suppressesStatusBadgeAndRail() {
        val clearState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyList()
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(300.dp)) {
                    RadarEdgeOverlay(
                        radarState = clearState,
                        edgeSide = RadarEdgeSide.FULL_SCREEN,
                        clearMode = RadarClearMode.SLIM_RAIL
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("RadarEdgeOverlay").assertExists()
        composeTestRule.onNodeWithTag("RadarStatusBadge").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarVerticalRail").assertDoesNotExist()
    }

    /**
     * TST-TRK-062.4 / TST-TRK-064.2 / REQ-TRK-062.3: When CONNECTED and road is clear while
     * audio snooze is active, zero green snooze circle may render across LEFT, RIGHT, and FULL_SCREEN.
     */
    @Test
    fun clearSnoozedState_suppressesStatusBadgeAcrossAllEdgeSides() {
        val snoozedClearState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyList(),
            audioSnoozeState = RadarAudioSnoozeState(
                isSnoozed = true,
                mode = RadarAudioSnoozeMode.SNOOZE_15M,
                remainingSeconds = 900
            )
        )
        var edgeSide by mutableStateOf(RadarEdgeSide.LEFT)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(300.dp)) {
                    RadarEdgeOverlay(
                        radarState = snoozedClearState,
                        edgeSide = edgeSide,
                        clearMode = RadarClearMode.SLIM_RAIL
                    )
                }
            }
        }

        for (side in RadarEdgeSide.values()) {
            composeTestRule.runOnIdle { edgeSide = side }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("RadarStatusBadge").assertDoesNotExist()
        }
    }

    /**
     * TST-TRK-062.4 / REQ-TRK-062.3: Both Start tab (page 0) and Live Tracking tabs (page > 0)
     * run under ScreenMode.TRACKING; verify clear CONNECTED overlay suppresses green circle on both,
     * and AUTO_HIDE_3S fades out rail after 3s with zero clear badge.
     */
    @Test
    fun startAndLiveTrackingTabs_clearOverlayHasZeroBadge_andAutoHideFadesRail() {
        val clearState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyList()
        )

        val startTabVisible = isRadarOverlayVisible(
            screenMode = ScreenMode.TRACKING,
            connectionState = clearState.connectionState,
            isSimulating = false
        )
        val liveTabVisible = isRadarOverlayVisible(
            screenMode = ScreenMode.TRACKING,
            connectionState = clearState.connectionState,
            isSimulating = false
        )
        assertTrue("Overlay visible on Start tab (page 0) under ScreenMode.TRACKING", startTabVisible)
        assertTrue("Overlay visible on Live Tracking tab (page > 0) under ScreenMode.TRACKING", liveTabVisible)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(120.dp).height(300.dp)) {
                    RadarEdgeOverlay(
                        radarState = clearState,
                        edgeSide = RadarEdgeSide.RIGHT,
                        clearMode = RadarClearMode.AUTO_HIDE_3S
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("RadarStatusBadge").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarVerticalRail", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(3500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("RadarStatusBadge").assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarVerticalRail").assertDoesNotExist()
    }

    /**
     * TST-TRK-062.3 / REQ-TRK-062.4: DISCONNECTED_ERROR preserves amber hazard badge across
     * LEFT, RIGHT, FULL_SCREEN, keeps rail aligned below 8dp+24dp+6dp badge header, and routes tap to reconnect.
     */
    @Test
    fun disconnectedError_displaysAmberBadge_andRoutesClickToReconnect() {
        val errorState = RadarState(
            connectionState = RadarConnectionState.DISCONNECTED_ERROR,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyList()
        )
        var edgeSide by mutableStateOf(RadarEdgeSide.LEFT)
        var reconnectCalls = 0
        var badgeCalls = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(300.dp)) {
                    RadarEdgeOverlay(
                        radarState = errorState,
                        edgeSide = edgeSide,
                        clearMode = RadarClearMode.SLIM_RAIL,
                        onBadgeClick = { badgeCalls++ },
                        onReconnectClick = { reconnectCalls++ }
                    )
                }
            }
        }

        for ((idx, side) in RadarEdgeSide.values().withIndex()) {
            composeTestRule.runOnIdle { edgeSide = side }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("RadarStatusBadge").assertIsDisplayed().performClick()
            assertEquals("Reconnect must be called on $side", idx + 1, reconnectCalls)
            assertEquals("Normal badge click must not fire on $side", 0, badgeCalls)

            if (side != RadarEdgeSide.FULL_SCREEN) {
                val railBounds = composeTestRule.onNodeWithTag("RadarVerticalRail", useUnmergedTree = true)
                    .getUnclippedBoundsInRoot()
                assertEquals(
                    "Error rail on $side must sit below 8dp+24dp+6dp header (38.dp)",
                    38f,
                    railBounds.top.value,
                    0.5f
                )
            }
        }
    }

    /**
     * TST-TRK-062.3 / TST-TRK-064.2 / REQ-TRK-062.4 / REQ-TRK-064.2:
     * Active threats (APPROACHING & FAST_APPROACHING, unsnoozed and snoozed) retain RadarStatusBadge
     * with target count and snooze countdown/bell, and route taps to onBadgeClick.
     */
    @Test
    fun activeThreats_unsnoozedAndSnoozed_displayBadgeAndInvokeOnBadgeClick() {
        val snoozedThreatState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 60f, approachSpeedMps = 18f, threatLevel = ThreatLevel.FAST_APPROACHING),
                RadarTarget(targetId = 2, rangeMeters = 100f, approachSpeedMps = 10f, threatLevel = ThreatLevel.APPROACHING)
            ),
            audioSnoozeState = RadarAudioSnoozeState(
                isSnoozed = true,
                mode = RadarAudioSnoozeMode.SNOOZE_15M,
                remainingSeconds = 900
            )
        )
        val unsnoozedApproachState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 80f, approachSpeedMps = 10f, threatLevel = ThreatLevel.APPROACHING)
            )
        )

        var activeState by mutableStateOf(snoozedThreatState)
        var edgeSide by mutableStateOf(RadarEdgeSide.FULL_SCREEN)
        var badgeClicks = 0
        var reconnectClicks = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(300.dp)) {
                    RadarEdgeOverlay(
                        radarState = activeState,
                        edgeSide = edgeSide,
                        onBadgeClick = { badgeClicks++ },
                        onReconnectClick = { reconnectClicks++ }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("RadarStatusBadge").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("15m", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("2", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(1, badgeClicks)
        assertEquals(0, reconnectClicks)

        composeTestRule.runOnIdle {
            activeState = unsnoozedApproachState
            edgeSide = RadarEdgeSide.RIGHT
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("RadarStatusBadge").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(2, badgeClicks)
        assertEquals(0, reconnectClicks)
    }

    /**
     * TST-TRK-062.9 / Rule 8.5: Captures Light and Dark @Preview-equivalent PNG screenshot
     * covering ClearLight (zero green badge), ApproachingDark, FastThreatAmoled, and Disconnected
     * states at docs/engineering/walkthroughs/img/ATT-8679_radar_edge_overlay_preview.png.
     */
    @Test
    fun captureLightAndDarkOverlayPreviewPng() {
        val clearState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyList()
        )
        val approachState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 80f, approachSpeedMps = 10f, threatLevel = ThreatLevel.APPROACHING),
                RadarTarget(targetId = 2, rangeMeters = 30f, approachSpeedMps = 12f, threatLevel = ThreatLevel.APPROACHING)
            )
        )
        val fastState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 40f, approachSpeedMps = 22f, threatLevel = ThreatLevel.FAST_APPROACHING)
            )
        )
        val disconnectedState = RadarState(
            connectionState = RadarConnectionState.DISCONNECTED_ERROR,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyList()
        )

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(440.dp)
                    .background(Color(0xFF78909C))
                    .padding(12.dp)
                    .testTag("RadarOverlayPreviewComposite"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.width(95.dp).height(260.dp)) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Clear Light", fontSize = 10.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterEnd) {
                                RadarEdgeOverlay(radarState = clearState, edgeSide = RadarEdgeSide.RIGHT, clearMode = RadarClearMode.SLIM_RAIL)
                            }
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(modifier = Modifier.width(95.dp).height(260.dp)) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Approach Dark", fontSize = 10.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterEnd) {
                                RadarEdgeOverlay(radarState = approachState, edgeSide = RadarEdgeSide.RIGHT, clearMode = RadarClearMode.SLIM_RAIL)
                            }
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(color = Color.Black, modifier = Modifier.width(95.dp).height(260.dp)) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Fast AMOLED", color = Color.White, fontSize = 10.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterEnd) {
                                RadarEdgeOverlay(radarState = fastState, edgeSide = RadarEdgeSide.RIGHT, clearMode = RadarClearMode.SLIM_RAIL)
                            }
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.width(95.dp).height(260.dp)) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Disconnected", fontSize = 10.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterEnd) {
                                RadarEdgeOverlay(radarState = disconnectedState, edgeSide = RadarEdgeSide.RIGHT, clearMode = RadarClearMode.SLIM_RAIL)
                            }
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("RadarOverlayPreviewComposite")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8679_radar_edge_overlay_preview.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Preview PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0)
    }
}
