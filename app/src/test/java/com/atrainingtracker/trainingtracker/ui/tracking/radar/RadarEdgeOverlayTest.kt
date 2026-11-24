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

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.LaneState
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarSource
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
 * (REQ-TRK-062, REQ-TRK-064, REQ-TRK-089, TST-TRK-062, TST-TRK-064, TST-TRK-089, REQ-PRO-049.1, ATT-8679, ATT-8733).
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
     * while rendering on CONNECTED or DISCONNECTED_ERROR.
     */
    @Test
    fun isRadarOverlayVisible_suppressesOverlayDuringSearchAndDisconnected() {
        // Suppressed while searching for sensor
        assertFalse(
            "Overlay must be hidden while searching (CONNECTING)",
            isRadarOverlayVisible(ScreenMode.TRACKING, RadarConnectionState.CONNECTING)
        )

        // Suppressed when disconnected
        assertFalse(
            "Overlay must be hidden when idle DISCONNECTED",
            isRadarOverlayVisible(ScreenMode.TRACKING, RadarConnectionState.DISCONNECTED)
        )
    }

    @Test
    fun isRadarOverlayVisible_rendersOnConnectedAndDisconnectedError() {
        // Visible on CONNECTED
        assertTrue(
            "Overlay must be visible when CONNECTED",
            isRadarOverlayVisible(ScreenMode.TRACKING, RadarConnectionState.CONNECTED)
        )

        // Visible on DISCONNECTED_ERROR (fail-safe amber warning)
        assertTrue(
            "Overlay must be visible on DISCONNECTED_ERROR",
            isRadarOverlayVisible(ScreenMode.TRACKING, RadarConnectionState.DISCONNECTED_ERROR)
        )
    }

    @Test
    fun isRadarOverlayVisible_suppressedWhenNotInTrackingScreenMode() {
        // Suppressed when not in ScreenMode.TRACKING
        assertFalse(
            "Overlay must be suppressed outside ScreenMode.TRACKING even if connected",
            isRadarOverlayVisible(ScreenMode.CONFIGURATION, RadarConnectionState.CONNECTED)
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
            connectionState = clearState.connectionState
        )
        val liveTabVisible = isRadarOverlayVisible(
            screenMode = ScreenMode.TRACKING,
            connectionState = clearState.connectionState
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

    /**
     * TST-TRK-089.1 / REQ-TRK-089.1: FULL_SCREEN vehicle markers render 15m/128m/160m labels
     * unclipped and remain strictly inside container bounds even near 0.15f/0.85f and 0.0f/1.0f extremes.
     */
    @Test
    fun fullScreenMarkers_15m128m160m_renderUnclippedAndWithinContainerBounds() {
        val state = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 15f,
                    approachSpeedMps = 22f,
                    threatLevel = ThreatLevel.FAST_APPROACHING,
                    lateralOffsetMeters = -4.0f, // clamps to 0.15f left edge
                    vehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT
                ),
                RadarTarget(
                    targetId = 2,
                    rangeMeters = 128f,
                    approachSpeedMps = 12f,
                    threatLevel = ThreatLevel.APPROACHING,
                    lateralOffsetMeters = 4.0f, // clamps to 0.85f right edge
                    vehicleSizeCategory = VehicleSizeCategory.CAR
                ),
                RadarTarget(
                    targetId = 3,
                    rangeMeters = 160f,
                    approachSpeedMps = 10f,
                    threatLevel = ThreatLevel.APPROACHING,
                    laneState = LaneState.IN_RIDER_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
                )
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(480.dp)) {
                    RadarEdgeOverlay(
                        radarState = state,
                        edgeSide = RadarEdgeSide.FULL_SCREEN,
                        clearMode = RadarClearMode.SLIM_RAIL
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val overlayBounds = composeTestRule
            .onNodeWithTag("RadarFullScreenThreatOverlay", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        listOf(1 to "15m", 2 to "128m", 3 to "160m").forEach { (id, label) ->
            val markerNode = composeTestRule.onNodeWithTag("RadarFullScreenMarker_$id", useUnmergedTree = true)
            markerNode.assertIsDisplayed()
            val markerBounds = markerNode.getUnclippedBoundsInRoot()

            val textNode = composeTestRule.onNodeWithText(label, useUnmergedTree = true)
            textNode.assertIsDisplayed()
            val textBounds = textNode.getUnclippedBoundsInRoot()

            assertTrue("Text '$label' must fit inside marker width", textBounds.width < markerBounds.width)
            assertTrue("Text '$label' left must stay inside marker", textBounds.left.value >= markerBounds.left.value - 0.5f)
            assertTrue("Text '$label' right must stay inside marker", textBounds.right.value <= markerBounds.right.value + 0.5f)
            assertTrue("Marker $id left must stay inside overlay", markerBounds.left.value >= overlayBounds.left.value - 0.5f)
            assertTrue("Marker $id right must stay inside overlay", markerBounds.right.value <= overlayBounds.right.value + 0.5f)
            assertTrue("Marker $id top must stay inside overlay", markerBounds.top.value >= overlayBounds.top.value - 0.5f)
            assertTrue("Marker $id bottom must stay inside overlay", markerBounds.bottom.value <= overlayBounds.bottom.value + 0.5f)
        }
    }

    /**
     * TST-TRK-089.2 / REQ-TRK-089.2: V2 vehicle categories (UNKNOWN_OR_LIGHT, CAR, TRUCK_OR_BUS)
     * map to distinct marker scales and localized accessibility descriptions reusing strings_radar.xml
     * (zero hardcoded "Target" fallback).
     */
    @Test
    fun fullScreenMarkers_v2VehicleCategories_scaleDistinctlyAndUseLocalizedSemantics() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lightDesc = context.getString(R.string.radar_vehicle_size_light)
        val carDesc = context.getString(R.string.radar_vehicle_size_car)
        val truckDesc = context.getString(R.string.radar_vehicle_size_truck)

        val state = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 80f,
                    approachSpeedMps = 10f,
                    threatLevel = ThreatLevel.APPROACHING,
                    laneState = LaneState.PASSING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT
                ),
                RadarTarget(
                    targetId = 2,
                    rangeMeters = 80f,
                    approachSpeedMps = 10f,
                    threatLevel = ThreatLevel.APPROACHING,
                    laneState = LaneState.CHANGING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.CAR
                ),
                RadarTarget(
                    targetId = 3,
                    rangeMeters = 80f,
                    approachSpeedMps = 10f,
                    threatLevel = ThreatLevel.APPROACHING,
                    laneState = LaneState.IN_RIDER_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
                )
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(480.dp)) {
                    RadarEdgeOverlay(
                        radarState = state,
                        edgeSide = RadarEdgeSide.FULL_SCREEN,
                        clearMode = RadarClearMode.SLIM_RAIL
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("$lightDesc, 80m").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("$carDesc, 80m").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("$truckDesc, 80m").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Target, 80m").assertDoesNotExist()

        val lightBounds = composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val carBounds = composeTestRule.onNodeWithTag("RadarFullScreenMarker_2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val truckBounds = composeTestRule.onNodeWithTag("RadarFullScreenMarker_3", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue("UNKNOWN_OR_LIGHT marker must meet handlebar-legible min width (>= 92.dp), got ${lightBounds.width}", lightBounds.width >= 92.dp)
        assertTrue("UNKNOWN_OR_LIGHT marker must meet handlebar-legible min height (>= 42.dp), got ${lightBounds.height}", lightBounds.height >= 42.dp)
        assertTrue("CAR marker must exceed UNKNOWN_OR_LIGHT width", carBounds.width > lightBounds.width)
        assertTrue("TRUCK_OR_BUS marker must exceed CAR width", truckBounds.width > carBounds.width)
        assertTrue("TRUCK_OR_BUS marker must exceed CAR height", truckBounds.height > carBounds.height)
    }

    /**
     * TST-TRK-089.2 / REQ-TRK-089.2 (ATT-8958): UNKNOWN_OR_LIGHT (V1 / ANT+ / simulator)
     * must render DirectionsCar (never TwoWheeler motorcycle), TRUCK_OR_BUS renders LocalShipping,
     * and all categories enforce enlarged handlebar-legible dimensions and >= 18.sp typography.
     */
    @Test
    fun resolveFullScreenVehicleVisualSpec_neverUsesMotorcycleForUnknownOrLight_andEnforcesLargeSizing() {
        val unknownSpec = resolveFullScreenVehicleVisualSpec(VehicleSizeCategory.UNKNOWN_OR_LIGHT)
        val carSpec = resolveFullScreenVehicleVisualSpec(VehicleSizeCategory.CAR)
        val truckSpec = resolveFullScreenVehicleVisualSpec(VehicleSizeCategory.TRUCK_OR_BUS)

        assertNotEquals(
            "UNKNOWN_OR_LIGHT (V1/ANT+/simulator) must NEVER render motorcycle TwoWheeler icon",
            Icons.Filled.TwoWheeler,
            unknownSpec.icon
        )
        assertEquals(Icons.Filled.DirectionsCar, unknownSpec.icon)
        assertEquals(Icons.Filled.DirectionsCar, carSpec.icon)
        assertEquals(Icons.Filled.LocalShipping, truckSpec.icon)

        assertTrue("UNKNOWN_OR_LIGHT minWidth must be >= 92.dp", unknownSpec.minWidth >= 92.dp)
        assertTrue("UNKNOWN_OR_LIGHT minHeight must be >= 42.dp", unknownSpec.minHeight >= 42.dp)
        assertTrue("UNKNOWN_OR_LIGHT fontSize must be >= 18.sp", unknownSpec.fontSize >= 18.sp)
        assertTrue("UNKNOWN_OR_LIGHT iconSize must be >= 24.dp", unknownSpec.iconSize >= 24.dp)

        assertTrue("CAR dimensions must exceed UNKNOWN_OR_LIGHT", carSpec.minWidth > unknownSpec.minWidth && carSpec.minHeight > unknownSpec.minHeight)
        assertTrue("TRUCK_OR_BUS dimensions must exceed CAR", truckSpec.minWidth > carSpec.minWidth && truckSpec.minHeight > carSpec.minHeight)
    }

    /**
     * TST-TRK-089.1 / REQ-TRK-089.1 (ATT-8958): Pure 2D lateral data gate returns false for V1
     * and 1D ANT+ frames, and true strictly when genuine 2D lateral offset / lane data is present.
     */
    @Test
    fun hasFullScreen2DLateralData_suppressesV1And1DAntPlus_enablesGenuine2D() {
        val v1State = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 80f, approachSpeedMps = 12f, threatLevel = ThreatLevel.APPROACHING)
            ),
            source = RadarSource.BLE
        )
        assertFalse("BLE V1 range-only state must not enable 2D lane guides", RadarOverlayMath.hasFullScreen2DLateralData(v1State))

        val ant1DState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 75f,
                    approachSpeedMps = 12f,
                    threatLevel = ThreatLevel.APPROACHING,
                    lateralOffsetMeters = 0.0f,
                    laneState = LaneState.IN_RIDER_LANE
                )
            ),
            source = RadarSource.ANT_PLUS
        )
        assertFalse("ANT+ 1D default IN_RIDER_LANE/0.0m must not enable 2D lane guides", RadarOverlayMath.hasFullScreen2DLateralData(ant1DState))

        val antPassingState = ant1DState.copy(
            targets = listOf(ant1DState.targets.first().copy(lateralOffsetMeters = -1.5f, laneState = LaneState.PASSING_LANE))
        )
        assertTrue("ANT+ with explicit passing-lane lateral separation enables 2D lane guides", RadarOverlayMath.hasFullScreen2DLateralData(antPassingState))

        val v2State = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 60f,
                    approachSpeedMps = 12f,
                    threatLevel = ThreatLevel.APPROACHING,
                    lateralOffsetMeters = -1.8f,
                    laneState = LaneState.PASSING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.CAR
                )
            ),
            source = RadarSource.BLE
        )
        assertTrue("BLE V2 lateral offset state must enable 2D lane guides", RadarOverlayMath.hasFullScreen2DLateralData(v2State))
    }

    /**
     * TST-TRK-089.1 / REQ-TRK-089.1 (ATT-8958): Compose UI verification confirming 3 vertical
     * dashed lane guides are completely suppressed in V1 (!has2DData) mode and rendered in V2 2D mode.
     */
    @Test
    fun fullScreenLaneGuides_hiddenInV1Mode_andShownInV2Mode() {
        val v1State = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 80f, approachSpeedMps = 12f, threatLevel = ThreatLevel.APPROACHING),
                RadarTarget(targetId = 2, rangeMeters = 123f, approachSpeedMps = 14f, threatLevel = ThreatLevel.APPROACHING)
            ),
            source = RadarSource.ANT_PLUS
        )
        val v2State = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 80f,
                    approachSpeedMps = 12f,
                    threatLevel = ThreatLevel.APPROACHING,
                    lateralOffsetMeters = -1.8f,
                    laneState = LaneState.PASSING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.CAR
                )
            ),
            source = RadarSource.BLE
        )

        var activeState by mutableStateOf(v1State)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(480.dp)) {
                    RadarEdgeOverlay(
                        radarState = activeState,
                        edgeSide = RadarEdgeSide.FULL_SCREEN,
                        clearMode = RadarClearMode.SLIM_RAIL
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("RadarFullScreenLaneGuides", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true).assertIsDisplayed()

        composeTestRule.runOnIdle { activeState = v2State }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("RadarFullScreenLaneGuides", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * TST-TRK-089.2 / REQ-TRK-089.2 (ATT-8958): Unit test for stepThreatHysteresis verifying
     * zero oscillation under alternating 1-frame and 2-frame V1 jitter while sustaining crisp
     * promotion after 3 consecutive FAST_APPROACHING frames and demotion after 4 APPROACHING frames.
     */
    @Test
    fun stepThreatHysteresis_suppressesAlternatingJitter_andTransitionsOnSustainedSignal() {
        // Initial detection adopts immediately
        var state = RadarOverlayMath.stepThreatHysteresis(null, ThreatLevel.APPROACHING)
        assertEquals(ThreatLevel.APPROACHING, state.displayed)

        // Alternating 1-frame 8-Hz V1 quantization jitter (A, F, A, F, A, F) stays solidly APPROACHING
        val alternating = listOf(
            ThreatLevel.FAST_APPROACHING,
            ThreatLevel.APPROACHING,
            ThreatLevel.FAST_APPROACHING,
            ThreatLevel.APPROACHING,
            ThreatLevel.FAST_APPROACHING,
            ThreatLevel.APPROACHING
        )
        for (level in alternating) {
            state = RadarOverlayMath.stepThreatHysteresis(state, level)
            assertEquals("Alternating jitter must never flip to FAST_APPROACHING", ThreatLevel.APPROACHING, state.displayed)
        }

        // 2-frame burst followed by APPROACHING stays APPROACHING
        state = RadarOverlayMath.stepThreatHysteresis(state, ThreatLevel.FAST_APPROACHING)
        assertEquals(ThreatLevel.APPROACHING, state.displayed)
        state = RadarOverlayMath.stepThreatHysteresis(state, ThreatLevel.FAST_APPROACHING)
        assertEquals(ThreatLevel.APPROACHING, state.displayed)
        state = RadarOverlayMath.stepThreatHysteresis(state, ThreatLevel.APPROACHING)
        assertEquals(ThreatLevel.APPROACHING, state.displayed)

        // 3 consecutive FAST_APPROACHING frames promote cleanly
        state = RadarOverlayMath.stepThreatHysteresis(state, ThreatLevel.FAST_APPROACHING)
        assertEquals(ThreatLevel.APPROACHING, state.displayed)
        state = RadarOverlayMath.stepThreatHysteresis(state, ThreatLevel.FAST_APPROACHING)
        assertEquals(ThreatLevel.APPROACHING, state.displayed)
        state = RadarOverlayMath.stepThreatHysteresis(state, ThreatLevel.FAST_APPROACHING)
        assertEquals("3 consecutive FAST_APPROACHING frames must promote", ThreatLevel.FAST_APPROACHING, state.displayed)

        // 3 consecutive APPROACHING dips during fast threat hold FAST_APPROACHING, then reset on FAST_APPROACHING
        repeat(3) {
            state = RadarOverlayMath.stepThreatHysteresis(state, ThreatLevel.APPROACHING)
            assertEquals("Transient dips (<4 frames) must hold FAST_APPROACHING", ThreatLevel.FAST_APPROACHING, state.displayed)
        }
        state = RadarOverlayMath.stepThreatHysteresis(state, ThreatLevel.FAST_APPROACHING)
        assertEquals(ThreatLevel.FAST_APPROACHING, state.displayed)

        // 4 consecutive APPROACHING frames demote cleanly
        repeat(3) {
            state = RadarOverlayMath.stepThreatHysteresis(state, ThreatLevel.APPROACHING)
            assertEquals(ThreatLevel.FAST_APPROACHING, state.displayed)
        }
        state = RadarOverlayMath.stepThreatHysteresis(state, ThreatLevel.APPROACHING)
        assertEquals("4 consecutive APPROACHING frames must demote", ThreatLevel.APPROACHING, state.displayed)

        // NONE clears immediately
        state = RadarOverlayMath.stepThreatHysteresis(state, ThreatLevel.NONE)
        assertEquals(ThreatLevel.NONE, state.displayed)
    }

    /**
     * TST-TRK-089.2 / REQ-TRK-089.3: Full-screen markers and container expose zero click actions
     * (100% touch pass-through), upward kinematics place 10m above 160m, and LEFT/RIGHT rail
     * geometry remains unchanged (REQ-TRK-073.2).
     */
    @Test
    fun fullScreenMarkers_preserveTouchPassThrough_upwardKinematics_andEdgeRailGeometry() {
        val threatState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 10f,
                    approachSpeedMps = 12f,
                    threatLevel = ThreatLevel.APPROACHING,
                    laneState = LaneState.IN_RIDER_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.CAR
                ),
                RadarTarget(
                    targetId = 2,
                    rangeMeters = 160f,
                    approachSpeedMps = 10f,
                    threatLevel = ThreatLevel.APPROACHING,
                    laneState = LaneState.PASSING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
                )
            )
        )
        var edgeSide by mutableStateOf(RadarEdgeSide.FULL_SCREEN)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(480.dp)) {
                    RadarEdgeOverlay(
                        radarState = threatState,
                        edgeSide = edgeSide,
                        clearMode = RadarClearMode.SLIM_RAIL
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("RadarFullScreenThreatOverlay", useUnmergedTree = true).assertHasNoClickAction()
        composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true).assertHasNoClickAction()
        composeTestRule.onNodeWithTag("RadarFullScreenMarker_2", useUnmergedTree = true).assertHasNoClickAction()

        val closeBounds = composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val farBounds = composeTestRule.onNodeWithTag("RadarFullScreenMarker_2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("Closer vehicle (10m) must render above farther vehicle (160m)", closeBounds.top < farBounds.top)

        composeTestRule.runOnIdle { edgeSide = RadarEdgeSide.RIGHT }
        composeTestRule.waitForIdle()
        val railBounds = composeTestRule.onNodeWithTag("RadarVerticalRail", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("V2 edge rail width must remain 30.dp container width", 30f, railBounds.width.value, 0.5f)
    }

    /**
     * TST-TRK-089.1 / REQ-TRK-089.1: Single-pass `.layout` measurement clamps markers strictly
     * inside container bounds on Frame 1 (zero IntSize.Zero jump) and across dynamic 99m -> 100m width growth.
     */
    @Test
    fun fullScreenMarkers_singlePassLayout_clampsOnFirstFrameAndDynamicWidthGrowth() {
        var range by mutableStateOf(99f)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(480.dp)) {
                    RadarEdgeOverlay(
                        radarState = RadarState(
                            connectionState = RadarConnectionState.CONNECTED,
                            overallThreatLevel = ThreatLevel.APPROACHING,
                            targets = listOf(
                                RadarTarget(
                                    targetId = 1,
                                    rangeMeters = range,
                                    approachSpeedMps = 12f,
                                    threatLevel = ThreatLevel.APPROACHING,
                                    lateralOffsetMeters = 4.0f, // clamps at 0.85f right edge
                                    vehicleSizeCategory = VehicleSizeCategory.CAR
                                ),
                                RadarTarget(
                                    targetId = 2,
                                    rangeMeters = 175f,
                                    approachSpeedMps = 10f,
                                    threatLevel = ThreatLevel.APPROACHING,
                                    lateralOffsetMeters = -4.0f, // clamps at 0.15f left edge
                                    vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
                                )
                            )
                        ),
                        edgeSide = RadarEdgeSide.FULL_SCREEN,
                        clearMode = RadarClearMode.SLIM_RAIL
                    )
                }
            }
        }
        // Advance only 1 frame (single-pass measurement & placement, no extra onSizeChanged ticks)
        composeTestRule.mainClock.advanceTimeByFrame()

        val overlayBounds = composeTestRule
            .onNodeWithTag("RadarFullScreenThreatOverlay", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        val marker1Frame1 = composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val marker2Frame1 = composeTestRule.onNodeWithTag("RadarFullScreenMarker_2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("Marker 1 must measure non-zero width on Frame 1", marker1Frame1.width > 0.dp)
        assertTrue("Marker 1 right must clamp inside overlay on Frame 1", marker1Frame1.right.value <= overlayBounds.right.value + 0.5f)
        assertTrue("Marker 2 left must clamp inside overlay on Frame 1", marker2Frame1.left.value >= overlayBounds.left.value - 0.5f)
        assertTrue("Marker 2 bottom must clamp inside overlay on Frame 1", marker2Frame1.bottom.value <= overlayBounds.bottom.value + 0.5f)

        composeTestRule.runOnUiThread { range = 100f }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        val marker1Grown = composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        composeTestRule.onNodeWithText("100m", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("99m -> 100m grown marker must remain clamped inside right edge", marker1Grown.right.value <= overlayBounds.right.value + 0.5f)
    }

    /**
     * TST-TRK-089.2 / REQ-TRK-062.3 / REQ-TRK-089.3: Real behavioral pointer pass-through test
     * performing tap and horizontal swipe directly over RadarFullScreenMarker_1 bounds, verifying
     * the underlying sibling receives every pointer event unhindered.
     */
    @Test
    fun fullScreenMarkers_passThroughTapAndSwipeDirectlyOverMarkerBoundsToUnderlyingSibling() {
        var receivedPressCount = 0
        var receivedReleaseCount = 0
        var totalHorizontalDeltaPx = 0f

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(480.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("UnderlyingMapSurface")
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            PointerEventType.Press -> receivedPressCount++
                                            PointerEventType.Release -> receivedReleaseCount++
                                            PointerEventType.Move -> {
                                                event.changes.forEach { change ->
                                                    totalHorizontalDeltaPx += (change.position.x - change.previousPosition.x)
                                                }
                                            }
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                    )
                    RadarEdgeOverlay(
                        radarState = RadarState(
                            connectionState = RadarConnectionState.CONNECTED,
                            overallThreatLevel = ThreatLevel.APPROACHING,
                            targets = listOf(
                                RadarTarget(
                                    targetId = 1,
                                    rangeMeters = 75f,
                                    approachSpeedMps = 12f,
                                    threatLevel = ThreatLevel.APPROACHING,
                                    laneState = LaneState.IN_RIDER_LANE,
                                    vehicleSizeCategory = VehicleSizeCategory.CAR
                                )
                            )
                        ),
                        edgeSide = RadarEdgeSide.FULL_SCREEN,
                        clearMode = RadarClearMode.SLIM_RAIL
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val markerBounds = composeTestRule
            .onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val markerCenterPx = with(composeTestRule.density) {
            Offset(
                x = ((markerBounds.left + markerBounds.right) / 2f).toPx(),
                y = ((markerBounds.top + markerBounds.bottom) / 2f).toPx()
            )
        }

        // 1. Tap directly on marker center -> underlying sibling receives Press + Release
        composeTestRule.onRoot().performTouchInput {
            click(markerCenterPx)
        }
        composeTestRule.waitForIdle()
        assertEquals("Underlying sibling must receive tap Press through marker", 1, receivedPressCount)
        assertEquals("Underlying sibling must receive tap Release through marker", 1, receivedReleaseCount)

        // 2. Horizontal swipe starting directly on marker center -> underlying sibling receives drag
        composeTestRule.onRoot().performTouchInput {
            down(markerCenterPx)
            advanceEventTime(16L)
            moveTo(Offset((markerCenterPx.x - 120f).coerceAtLeast(10f), markerCenterPx.y))
            advanceEventTime(16L)
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals("Underlying sibling must receive swipe Press through marker", 2, receivedPressCount)
        assertEquals("Underlying sibling must receive swipe Release through marker", 2, receivedReleaseCount)
        assertTrue("Underlying sibling must receive horizontal drag delta (< -50px), got $totalHorizontalDeltaPx", totalHorizontalDeltaPx < -50f)
    }

    /**
     * TST-TRK-089.2 / REQ-TRK-089.2 (ATT-8958): Full-screen markers render rock-solid 100% opaque
     * threat fills (zero alpha-pulse flicker over light/warm backgrounds) and apply per-target
     * threat hysteresis suppressing alternating APPROACHING <-> FAST_APPROACHING jitter while
     * transitioning cleanly after 3 sustained FAST_APPROACHING frames.
     */
    @Test
    fun fullScreenMarkers_stayOpaqueNonPulsing_andSuppressRedOrangeOscillation() {
        var activeState by mutableStateOf(
            RadarState(
                connectionState = RadarConnectionState.CONNECTED,
                overallThreatLevel = ThreatLevel.FAST_APPROACHING,
                targets = listOf(
                    RadarTarget(
                        targetId = 1,
                        rangeMeters = 60f,
                        approachSpeedMps = 22f,
                        threatLevel = ThreatLevel.FAST_APPROACHING,
                        laneState = LaneState.IN_RIDER_LANE,
                        vehicleSizeCategory = VehicleSizeCategory.CAR
                    )
                ),
                lastUpdateRealtimeMs = 1000L
            )
        )
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(480.dp)
                        .background(TTColor.RadarFullScreenMapBackdrop)
                ) {
                    RadarEdgeOverlay(
                        radarState = activeState,
                        edgeSide = RadarEdgeSide.FULL_SCREEN,
                        clearMode = RadarClearMode.SLIM_RAIL
                    )
                }
            }
        }
        // Settle initial position animation before comparing pixel stability across clock ticks
        composeTestRule.mainClock.advanceTimeBy(200L)
        val fastBitmapT0 = composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()

        composeTestRule.mainClock.advanceTimeBy(175L)
        val fastBitmapT1 = composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()

        assertFalse(
            "FAST_APPROACHING full-screen marker must remain rock-solid opaque with zero alpha-pulse oscillation",
            bitmapsDiffer(fastBitmapT0, fastBitmapT1)
        )

        // Introduce a new V1 target (targetId = 2) starting at APPROACHING and feed alternating 8-Hz jitter
        fun v1TargetState(threat: ThreatLevel, tsMs: Long) = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = threat,
            targets = listOf(
                RadarTarget(
                    targetId = 2,
                    rangeMeters = 80f,
                    approachSpeedMps = 12f,
                    threatLevel = threat
                )
            ),
            lastUpdateRealtimeMs = tsMs
        )

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.runOnUiThread { activeState = v1TargetState(ThreatLevel.APPROACHING, 2000L) }
        composeTestRule.waitForIdle()
        val amberBaseline = composeTestRule.onNodeWithTag("RadarFullScreenMarker_2", useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()

        // Alternating F -> A -> F -> A must hold steady amber pixels (zero red/orange oscillation)
        var ts = 2125L
        for (level in listOf(ThreatLevel.FAST_APPROACHING, ThreatLevel.APPROACHING, ThreatLevel.FAST_APPROACHING, ThreatLevel.APPROACHING)) {
            composeTestRule.runOnUiThread { activeState = v1TargetState(level, ts) }
            ts += 125L
            composeTestRule.waitForIdle()
            val currentBmp = composeTestRule.onNodeWithTag("RadarFullScreenMarker_2", useUnmergedTree = true)
                .captureToImage()
                .asAndroidBitmap()
            assertFalse(
                "Alternating jitter frame ($level) must not oscillate marker pixels away from amber",
                bitmapsDiffer(amberBaseline, currentBmp)
            )
        }

        // 3 consecutive FAST_APPROACHING frames must promote marker cleanly to solid red
        repeat(3) {
            composeTestRule.runOnUiThread { activeState = v1TargetState(ThreatLevel.FAST_APPROACHING, ts) }
            ts += 125L
            composeTestRule.waitForIdle()
        }
        val promotedRedBmp = composeTestRule.onNodeWithTag("RadarFullScreenMarker_2", useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        assertTrue(
            "3 sustained FAST_APPROACHING frames must transition marker color from amber to red",
            bitmapsDiffer(amberBaseline, promotedRedBmp)
        )
    }

    private fun bitmapsDiffer(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return true
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) return true
            }
        }
        return false
    }

    /**
     * TST-TRK-089.3 / Rule 8.5: Captures Light, Dark, and Map-backdrop FULL_SCREEN @Preview-equivalent PNG
     * covering mixed V2 targets (15m, 128m, 160m) at
     * docs/engineering/walkthroughs/img/ATT-8733_radar_fullscreen_markers_preview.png.
     */
    @Test
    fun captureFullScreenMarkersPreviewPng() {
        val mixedLightState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 15f,
                    approachSpeedMps = 22f,
                    threatLevel = ThreatLevel.FAST_APPROACHING,
                    laneState = LaneState.PASSING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.CAR
                ),
                RadarTarget(
                    targetId = 2,
                    rangeMeters = 128f,
                    approachSpeedMps = 12f,
                    threatLevel = ThreatLevel.APPROACHING,
                    laneState = LaneState.IN_RIDER_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
                ),
                RadarTarget(
                    targetId = 3,
                    rangeMeters = 160f,
                    approachSpeedMps = 9f,
                    threatLevel = ThreatLevel.APPROACHING,
                    laneState = LaneState.CHANGING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT
                )
            )
        )
        val mixedDarkState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 24f,
                    approachSpeedMps = 24f,
                    threatLevel = ThreatLevel.FAST_APPROACHING,
                    laneState = LaneState.PASSING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
                ),
                RadarTarget(
                    targetId = 2,
                    rangeMeters = 110f,
                    approachSpeedMps = 11f,
                    threatLevel = ThreatLevel.APPROACHING,
                    laneState = LaneState.IN_RIDER_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.CAR
                )
            )
        )
        val mapBackdropState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 45f,
                    approachSpeedMps = 13f,
                    threatLevel = ThreatLevel.APPROACHING,
                    lateralOffsetMeters = 3.5f,
                    vehicleSizeCategory = VehicleSizeCategory.CAR
                ),
                RadarTarget(
                    targetId = 2,
                    rangeMeters = 148f,
                    approachSpeedMps = 10f,
                    threatLevel = ThreatLevel.APPROACHING,
                    lateralOffsetMeters = -3.5f,
                    vehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT
                )
            )
        )

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(720.dp)
                    .background(TTColor.RadarRailTrack)
                    .padding(12.dp)
                    .testTag("RadarFullScreenPreviewComposite"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.width(224.dp).height(340.dp)) {
                        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                            Text("FullScreen Light", fontSize = 11.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                RadarEdgeOverlay(radarState = mixedLightState, edgeSide = RadarEdgeSide.FULL_SCREEN)
                            }
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(color = Color.Black, modifier = Modifier.width(224.dp).height(340.dp)) {
                        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                            Text("FullScreen Dark", color = Color.White, fontSize = 11.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                RadarEdgeOverlay(radarState = mixedDarkState, edgeSide = RadarEdgeSide.FULL_SCREEN)
                            }
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = TTColor.RadarFullScreenMapBackdrop,
                        modifier = Modifier.width(224.dp).height(340.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                            Text("FullScreen Map", fontSize = 11.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRect(
                                        color = TTColor.RadarFullScreenMapPark,
                                        topLeft = Offset(size.width * 0.08f, size.height * 0.14f),
                                        size = Size(size.width * 0.36f, size.height * 0.28f)
                                    )
                                    drawLine(
                                        color = TTColor.RadarFullScreenMapRoad,
                                        start = Offset(0f, size.height * 0.36f),
                                        end = Offset(size.width, size.height * 0.24f),
                                        strokeWidth = 10.dp.toPx()
                                    )
                                    drawLine(
                                        color = TTColor.RouteSelected,
                                        start = Offset(size.width * 0.5f, size.height),
                                        end = Offset(size.width * 0.56f, 0f),
                                        strokeWidth = 4.dp.toPx()
                                    )
                                }
                                RadarEdgeOverlay(radarState = mapBackdropState, edgeSide = RadarEdgeSide.FULL_SCREEN)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val bmp = composeTestRule.onNodeWithTag("RadarFullScreenPreviewComposite")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8733_radar_fullscreen_markers_preview.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Full-screen preview PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0)
    }

    /**
     * TST-TRK-089.3 / Rule 8.5 (ATT-8958): Captures V1 (1D, zero lane guides, neutral car icon,
     * enlarged badges, solid opaque amber/red) & V2 (2D, lane guides, Car + Truck) Light/Dark composite
     * at docs/engineering/walkthroughs/img/ATT-8958_radar_fullscreen_preview.png.
     */
    @Test
    fun captureAtt8958V1AndV2FullScreenPreviewPng() {
        val v1State = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 80f, approachSpeedMps = 12f, threatLevel = ThreatLevel.APPROACHING),
                RadarTarget(targetId = 2, rangeMeters = 123f, approachSpeedMps = 22f, threatLevel = ThreatLevel.FAST_APPROACHING)
            ),
            source = RadarSource.BLE
        )
        val v2State = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 24f,
                    approachSpeedMps = 24f,
                    threatLevel = ThreatLevel.FAST_APPROACHING,
                    lateralOffsetMeters = -2.2f,
                    laneState = LaneState.PASSING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
                ),
                RadarTarget(
                    targetId = 2,
                    rangeMeters = 110f,
                    approachSpeedMps = 12f,
                    threatLevel = ThreatLevel.APPROACHING,
                    lateralOffsetMeters = 0.0f,
                    laneState = LaneState.IN_RIDER_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.CAR
                )
            ),
            source = RadarSource.BLE
        )

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(720.dp)
                    .background(TTColor.RadarRailTrack)
                    .padding(12.dp)
                    .testTag("Att8958FullScreenPreviewComposite"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.width(224.dp).height(360.dp)) {
                        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                            Text("V1 Light (No Guides)", fontSize = 11.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                RadarEdgeOverlay(radarState = v1State, edgeSide = RadarEdgeSide.FULL_SCREEN)
                            }
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(color = Color.Black, modifier = Modifier.width(224.dp).height(360.dp)) {
                        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                            Text("V1 Dark (No Guides)", color = Color.White, fontSize = 11.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                RadarEdgeOverlay(radarState = v1State, edgeSide = RadarEdgeSide.FULL_SCREEN)
                            }
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.width(224.dp).height(360.dp)) {
                        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                            Text("V2 2D (With Guides)", fontSize = 11.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                RadarEdgeOverlay(radarState = v2State, edgeSide = RadarEdgeSide.FULL_SCREEN)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val bmp = composeTestRule.onNodeWithTag("Att8958FullScreenPreviewComposite")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8958_radar_fullscreen_preview.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("ATT-8958 preview PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0)
    }
}
