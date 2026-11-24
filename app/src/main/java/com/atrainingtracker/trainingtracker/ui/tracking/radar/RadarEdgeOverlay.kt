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

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.LaneState
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarSnoozeController
import com.atrainingtracker.banalservice.devices.radar.RadarSource
import com.atrainingtracker.banalservice.devices.radar.RadarState
import com.atrainingtracker.banalservice.devices.radar.RadarTarget
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.banalservice.devices.radar.VehicleSizeCategory
import com.atrainingtracker.trainingtracker.settings.RadarClearMode
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.settings.RadarFullScreenMarkerStyle
import com.atrainingtracker.trainingtracker.settings.RadarTrafficOrientation
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import kotlinx.coroutines.delay

/**
 * Pure helper and kinematics functions for cycling radar edge overlay (REQ-TRK-062).
 */
object RadarOverlayMath {
    const val MAX_RADAR_RANGE_METERS = 140.0f
    const val MAX_RADAR_RANGE_V2_METERS = 175.0f
    const val THREAT_ESCALATE_CONSECUTIVE_FRAMES = 3
    const val THREAT_DEESCALATE_CONSECUTIVE_FRAMES = 4

    /**
     * Per-target display hysteresis state preventing frame-to-frame red/orange oscillation (REQ-TRK-089.2).
     */
    data class ThreatHysteresisState(
        val displayed: ThreatLevel,
        val pending: ThreatLevel? = null,
        val pendingCount: Int = 0
    )

    /**
     * Advances display threat hysteresis for a single target (REQ-TRK-089.2, ATT-8958):
     * - Initial detection adopts [incoming] immediately (zero Frame-1 lag).
     * - Escalating [ThreatLevel.APPROACHING] -> [ThreatLevel.FAST_APPROACHING] requires
     *   [THREAT_ESCALATE_CONSECUTIVE_FRAMES] consecutive frames.
     * - De-escalating [ThreatLevel.FAST_APPROACHING] -> [ThreatLevel.APPROACHING] requires
     *   [THREAT_DEESCALATE_CONSECUTIVE_FRAMES] consecutive frames.
     */
    fun stepThreatHysteresis(
        previous: ThreatHysteresisState?,
        incoming: ThreatLevel
    ): ThreatHysteresisState {
        if (previous == null || incoming == ThreatLevel.NONE) {
            return ThreatHysteresisState(displayed = incoming)
        }
        if (incoming == previous.displayed) {
            return previous.copy(pending = null, pendingCount = 0)
        }
        val requiredFrames = if (incoming == ThreatLevel.FAST_APPROACHING) {
            THREAT_ESCALATE_CONSECUTIVE_FRAMES
        } else {
            THREAT_DEESCALATE_CONSECUTIVE_FRAMES
        }
        val nextCount = if (previous.pending == incoming) previous.pendingCount + 1 else 1
        return if (nextCount >= requiredFrames) {
            ThreatHysteresisState(displayed = incoming)
        } else {
            previous.copy(pending = incoming, pendingCount = nextCount)
        }
    }

    /**
     * Returns true strictly when [radarState] carries genuine 2D lateral offset / lane-separation data
     * suitable for drawing full-screen lane guides (REQ-TRK-089.1, ATT-8958).
     * Returns false for 1D range-only V1 BLE/simulator streams and ANT+ frames without lateral separation.
     */
    fun hasFullScreen2DLateralData(radarState: RadarState): Boolean {
        if (radarState.source == RadarSource.ANT_PLUS) {
            return radarState.targets.any {
                (it.lateralOffsetMeters != null && it.lateralOffsetMeters != 0.0f) ||
                    it.laneState == LaneState.PASSING_LANE ||
                    it.laneState == LaneState.CHANGING_LANE
            }
        }
        return radarState.targets.any {
            it.lateralOffsetMeters != null || it.laneState != LaneState.UNKNOWN
        }
    }

    /**
     * Resolves dynamic maximum rail range in meters: 175m for V2 radar or distant targets, 140m otherwise.
     */
    fun resolveRailMaxRange(radarState: RadarState): Float {
        val hasDistantOrV2 = radarState.targets.any {
            it.rangeMeters > MAX_RADAR_RANGE_METERS ||
            it.vehicleSizeCategory != VehicleSizeCategory.UNKNOWN_OR_LIGHT ||
            it.lateralOffsetMeters != null
        }
        return if (hasDistantOrV2) MAX_RADAR_RANGE_V2_METERS else MAX_RADAR_RANGE_METERS
    }

    /**
     * Normalizes a target distance (0..maxRange) to a vertical coordinate fraction (0.0..1.0),
     * where 1.0 corresponds to max range (bottom of rail, farthest vehicle)
     * and 0.0 corresponds to 0m range (top of rail, closest vehicle / cyclist).
     *
     * Approaching vehicles enter at the bottom of the rail and travel upward toward the cyclist.
     */
    fun normalizeRadarRange(rangeMeters: Float, maxRangeMeters: Float = MAX_RADAR_RANGE_METERS): Float {
        if (maxRangeMeters <= 0f) return 0.0f
        return (rangeMeters / maxRangeMeters).coerceIn(0.0f, 1.0f)
    }

    /**
     * Translates target lateral deviation into horizontal dp displacement across [-maxOffsetDp, +maxOffsetDp].
     * Respects [RadarTrafficOrientation] (RHT vs LHT).
     */
    fun calculateHorizontalOffsetDp(
        target: RadarTarget,
        trafficOrientation: RadarTrafficOrientation = RadarTrafficOrientation.RIGHT_HAND,
        maxOffsetDp: Float = 6.0f
    ): Float {
        val rawOffset = target.lateralOffsetMeters
        val offsetDp = if (rawOffset != null) {
            val clampedM = rawOffset.coerceIn(-3.0f, 3.0f)
            (clampedM / 3.0f) * maxOffsetDp
        } else {
            when (target.laneState) {
                LaneState.PASSING_LANE -> -4.0f
                LaneState.CHANGING_LANE -> -2.0f
                LaneState.IN_RIDER_LANE, LaneState.UNKNOWN -> 0.0f
            }
        }

        return if (trafficOrientation == RadarTrafficOrientation.LEFT_HAND) {
            -offsetDp
        } else {
            offsetDp
        }
    }

    /**
     * Translates target lateral deviation and lane state into a horizontal fraction [0.0..1.0] across screen width.
     * Respects [RadarTrafficOrientation] (RHT vs LHT) and provides safe margins [0.15f, 0.85f] (REQ-TRK-062.1a).
     */
    fun calculateFullScreenHorizontalFraction(
        target: RadarTarget,
        trafficOrientation: RadarTrafficOrientation = RadarTrafficOrientation.RIGHT_HAND
    ): Float {
        val rawOffset = target.lateralOffsetMeters
        val rhtFraction = if (rawOffset != null) {
            val offsetDelta = (rawOffset / 3.0f) * 0.40f
            (0.70f + offsetDelta).coerceIn(0.15f, 0.85f)
        } else {
            when (target.laneState) {
                LaneState.IN_RIDER_LANE, LaneState.UNKNOWN -> 0.70f
                LaneState.CHANGING_LANE -> 0.50f
                LaneState.PASSING_LANE -> 0.30f
            }
        }

        return if (trafficOrientation == RadarTrafficOrientation.LEFT_HAND) {
            (1.0f - rhtFraction).coerceIn(0.15f, 0.85f)
        } else {
            rhtFraction
        }
    }

    /**
     * Checks if the radar is in fail-safe disconnect / error mode (REQ-TRK-062.4).
     */
    fun isFailSafeDisconnect(connectionState: RadarConnectionState): Boolean {
        return connectionState == RadarConnectionState.DISCONNECTED_ERROR
    }

    /**
     * Resolves the authoritative threat color token for a given [ThreatLevel] (REQ-TRK-062.2).
     */
    fun resolveThreatColor(threatLevel: ThreatLevel): Color {
        return TTColor.getRadarThreatColor(threatLevel)
    }

    /**
     * Formats compact status badge pill text during audio snooze / suppression (REQ-TRK-064.2, REQ-CON-056.3).
     * Derives remaining minutes from monotonic [RadarAudioSnoozeState.snoozeUntilElapsedRealtimeMs] without a 1-Hz repository ticker.
     */
    @JvmOverloads
    fun formatSnoozeBadgeText(
        snoozeState: RadarAudioSnoozeState,
        nowElapsedRealtimeMs: Long = RadarSnoozeController.DEFAULT_TIME_PROVIDER()
    ): String? {
        if (!snoozeState.isSnoozed) return null
        val secs = snoozeState.remainingSecondsAt(nowElapsedRealtimeMs)
        return when {
            secs != null -> {
                val mins = (secs + 59) / 60
                "${mins}m"
            }
            snoozeState.mode == RadarAudioSnoozeMode.FAST_ONLY -> "!"
            snoozeState.isPelotonSyncActive -> "P"
            snoozeState.isUrbanMuteActive -> "U"
            else -> "M"
        }
    }

    /**
     * Resolves composite threat accessibility description string (REQ-TRK-064.2).
     */
    fun buildThreatDescription(
        count: Int,
        isFastApproaching: Boolean,
        snoozeSuffix: String? = null,
        formatBase: String? = null
    ): String {
        val base = if (formatBase != null) {
            String.format(formatBase, count)
        } else if (isFastApproaching) {
            "$count threats (high speed)"
        } else {
            "$count threats"
        }
        return if (snoozeSuffix != null) "$base, $snoozeSuffix" else base
    }
}

/**
 * Live rearview radar vertical edge overlay across all Tracking tabs (REQ-TRK-062).
 *
 * Invariants:
 * - Touch pass-through: The outer container and vertical rail do NOT attach click or pointer listeners,
 *   allowing unhindered HorizontalPager paging, Live Map pan/zoom, and system edge back gestures (REQ-TRK-062.3).
 * - Only the top compact status badge pill (rendered strictly for [RadarConnectionState.DISCONNECTED_ERROR]
 *   or active threats) consumes tap events for reconnect/settings (REQ-TRK-062.3).
 * - Clear-road status suppresses [RadarStatusBadgePill] completely (including snoozed clear) and guards
 *   top vertical spacers on badge visibility so [RadarVerticalRail] does not shift downward (REQ-TRK-062.3).
 * - Fail-safe disconnect: Displays an amber hazard badge and NEVER displays green "All Clear" when
 *   in [RadarConnectionState.DISCONNECTED_ERROR] (REQ-TRK-062.4).
 */
@Suppress("LongMethod")
@Composable
fun RadarEdgeOverlay(
    radarState: RadarState,
    edgeSide: RadarEdgeSide = RadarEdgeSide.RIGHT,
    clearMode: RadarClearMode = RadarClearMode.SLIM_RAIL,
    trafficOrientation: RadarTrafficOrientation = RadarTrafficOrientation.RIGHT_HAND,
    fullScreenMarkerStyle: RadarFullScreenMarkerStyle = TuningPreferencesDefaults.RADAR_FULL_SCREEN_MARKER_STYLE,
    modifier: Modifier = Modifier,
    onBadgeClick: () -> Unit = {},
    onReconnectClick: (() -> Unit)? = null
) {
    val hasActiveThreats = radarState.targets.isNotEmpty() || radarState.overallThreatLevel != ThreatLevel.NONE
    val isConnected = radarState.connectionState == RadarConnectionState.CONNECTED
    val isClear = isConnected && !hasActiveThreats
    val isDisconnectError = RadarOverlayMath.isFailSafeDisconnect(radarState.connectionState)
    val showStatusBadge = isDisconnectError || hasActiveThreats
    val has2DData = radarState.targets.any {
        it.lateralOffsetMeters != null || it.vehicleSizeCategory != VehicleSizeCategory.UNKNOWN_OR_LIGHT
    }

    // Auto-hide timer for AUTO_HIDE_3S mode (rail only)
    var isAutoHideActive by remember { mutableStateOf(false) }
    LaunchedEffect(isClear, clearMode) {
        if (clearMode == RadarClearMode.AUTO_HIDE_3S && isClear) {
            delay(3000L)
            isAutoHideActive = true
        } else {
            isAutoHideActive = false
        }
    }

    val railAlpha by animateFloatAsState(
        targetValue = if (isAutoHideActive) 0f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "radarRailAlpha"
    )

    if (edgeSide == RadarEdgeSide.FULL_SCREEN) {
        // FULL_SCREEN Mode (REQ-TRK-062.1a, REQ-TRK-062.3a, REQ-TRK-062.5b, REQ-TRK-089)
        // Per-target threat-level hysteresis prevents 8-Hz V1 boundary jitter from flashing red/orange.
        val threatHysteresisSession = remember { RadarThreatHysteresisSession() }
        val fullScreenRadarState = remember(radarState) {
            threatHysteresisSession.stabilize(radarState)
        }
        val showLaneGuides = RadarOverlayMath.hasFullScreen2DLateralData(fullScreenRadarState)

        Box(
            modifier = modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .testTag("RadarEdgeOverlay")
        ) {
            // Full-screen vehicle threat overlay (zero pointer capture)
            if (hasActiveThreats) {
                RadarFullScreenThreatOverlay(
                    radarState = fullScreenRadarState,
                    showLaneGuides = showLaneGuides,
                    trafficOrientation = trafficOrientation,
                    markerStyle = fullScreenMarkerStyle,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 36.dp, bottom = 12.dp)
                )
            }

            if (showStatusBadge) {
                // Top Status Badge Pill (only clickable element in full-screen overlay; solid opaque fill)
                RadarStatusBadgePill(
                    radarState = fullScreenRadarState,
                    isDisconnectError = isDisconnectError,
                    pulseAlphaProvider = { 1.0f },
                    onClick = {
                        if (isDisconnectError && onReconnectClick != null) {
                            onReconnectClick()
                        } else {
                            onBadgeClick()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                )
            }
        }
    } else {
        // Pulsing animation for FAST_APPROACHING red alert in Edge Rail mode only
        val pulseAlphaState: State<Float> = if (radarState.overallThreatLevel == ThreatLevel.FAST_APPROACHING) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(350, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "fastApproachingPulse"
            )
        } else {
            remember { mutableFloatStateOf(1.0f) }
        }

        // Edge Rail Mode (LEFT or RIGHT)
        val containerWidth = if (has2DData) 30.dp else 24.dp

        Column(
            modifier = modifier
                .width(containerWidth)
                .fillMaxHeight()
                .navigationBarsPadding()
                .testTag("RadarEdgeOverlay"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showStatusBadge) {
                Spacer(modifier = Modifier.height(8.dp))

                // Top Status Badge Pill (only clickable element in the overlay)
                RadarStatusBadgePill(
                    radarState = radarState,
                    isDisconnectError = isDisconnectError,
                    pulseAlphaProvider = { pulseAlphaState.value },
                    onClick = {
                        if (isDisconnectError && onReconnectClick != null) {
                            onReconnectClick()
                        } else {
                            onBadgeClick()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))
            }

            // Vertical Target Rail
            val showRail = when {
                hasActiveThreats -> true
                isDisconnectError -> true
                clearMode == RadarClearMode.BADGE_ONLY -> false
                else -> true // SLIM_RAIL or AUTO_HIDE_3S before timeout
            }

            if (showRail && railAlpha > 0f) {
                RadarVerticalRail(
                    radarState = radarState,
                    hasActiveThreats = hasActiveThreats,
                    isClear = isClear,
                    isDisconnectError = isDisconnectError,
                    clearMode = clearMode,
                    pulseAlpha = pulseAlphaState.value,
                    edgeSide = edgeSide,
                    trafficOrientation = trafficOrientation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .alpha(railAlpha)
                        .padding(bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Plain non-Compose session tracker applying [RadarOverlayMath.stepThreatHysteresis] per active
 * targetId across incoming [RadarState] frames while pruning departed targets (REQ-TRK-089.2).
 */
private class RadarThreatHysteresisSession {
    private var states: Map<Int, RadarOverlayMath.ThreatHysteresisState> = emptyMap()

    fun stabilize(radarState: RadarState): RadarState {
        if (radarState.targets.isEmpty()) {
            states = emptyMap()
            return radarState
        }
        val nextStates = HashMap<Int, RadarOverlayMath.ThreatHysteresisState>(radarState.targets.size)
        var anyTargetChanged = false
        val stabilizedTargets = radarState.targets.map { target ->
            val stepped = RadarOverlayMath.stepThreatHysteresis(
                previous = states[target.targetId],
                incoming = target.threatLevel
            )
            nextStates[target.targetId] = stepped
            if (stepped.displayed == target.threatLevel) {
                target
            } else {
                anyTargetChanged = true
                target.copy(threatLevel = stepped.displayed)
            }
        }
        states = nextStates

        val stabilizedOverall = when {
            stabilizedTargets.any { it.threatLevel == ThreatLevel.FAST_APPROACHING } -> ThreatLevel.FAST_APPROACHING
            stabilizedTargets.any { it.threatLevel == ThreatLevel.APPROACHING } -> ThreatLevel.APPROACHING
            else -> ThreatLevel.NONE
        }
        return if (!anyTargetChanged && stabilizedOverall == radarState.overallThreatLevel) {
            radarState
        } else {
            radarState.copy(
                overallThreatLevel = stabilizedOverall,
                targets = stabilizedTargets
            )
        }
    }
}

internal data class FullScreenVehicleVisualSpec(
    val icon: ImageVector,
    val iconSize: Dp,
    val minWidth: Dp,
    val minHeight: Dp,
    val fontSize: TextUnit,
    val descriptionRes: Int
)

internal fun resolveFullScreenVehicleVisualSpec(
    category: VehicleSizeCategory
): FullScreenVehicleVisualSpec = when (category) {
    VehicleSizeCategory.UNKNOWN_OR_LIGHT -> FullScreenVehicleVisualSpec(
        icon = Icons.Filled.DirectionsCar,
        iconSize = 24.dp,
        minWidth = 92.dp,
        minHeight = 42.dp,
        fontSize = 18.sp,
        descriptionRes = R.string.radar_vehicle_size_light
    )
    VehicleSizeCategory.CAR -> FullScreenVehicleVisualSpec(
        icon = Icons.Filled.DirectionsCar,
        iconSize = 26.dp,
        minWidth = 100.dp,
        minHeight = 46.dp,
        fontSize = 19.sp,
        descriptionRes = R.string.radar_vehicle_size_car
    )
    VehicleSizeCategory.TRUCK_OR_BUS -> FullScreenVehicleVisualSpec(
        icon = Icons.Filled.LocalShipping,
        iconSize = 28.dp,
        minWidth = 114.dp,
        minHeight = 52.dp,
        fontSize = 21.sp,
        descriptionRes = R.string.radar_vehicle_size_truck
    )
}

/**
 * Subtle non-interactive road-lane framing guides rendered strictly when genuine 2D lateral
 * data is present alongside active threats in FULL_SCREEN mode (REQ-TRK-089.1/.3).
 */
@Composable
private fun RadarFullScreenLaneGuides(
    guideColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("RadarFullScreenLaneGuides")
    ) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 14f), 0f)
        val strokePx = 1.dp.toPx()
        for (fraction in listOf(0.30f, 0.50f, 0.70f)) {
            val x = size.width * fraction
            drawLine(
                color = guideColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokePx,
                pathEffect = dash
            )
        }
    }
}

/**
 * Full-screen vehicle threat overlay rendering approaching vehicles directly on top
 * of the Tracking Screen with 100% touch pass-through (REQ-TRK-062.1/.3, REQ-TRK-089).
 */
@Composable
private fun RadarFullScreenThreatOverlay(
    radarState: RadarState,
    showLaneGuides: Boolean,
    trafficOrientation: RadarTrafficOrientation,
    markerStyle: RadarFullScreenMarkerStyle = TuningPreferencesDefaults.RADAR_FULL_SCREEN_MARKER_STYLE,
    modifier: Modifier = Modifier
) {
    val maxRangeMeters = RadarOverlayMath.resolveRailMaxRange(radarState)
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val guideColor = if (isDarkSurface) {
        TTColor.RadarFullScreenLaneGuideDark
    } else {
        TTColor.RadarFullScreenLaneGuideLight
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("RadarFullScreenThreatOverlay")
    ) {
        val usableHeightPx = constraints.maxHeight.toFloat()
        val usableWidthPx = constraints.maxWidth.toFloat()

        if (showLaneGuides) {
            RadarFullScreenLaneGuides(guideColor = guideColor)
        }

        // Draw farthest targets first so closer (higher-urgency) vehicles and distance badges always render on top
        val orderedTargets = remember(radarState.targets) {
            radarState.targets.sortedByDescending { it.rangeMeters }
        }
        orderedTargets.forEach { target ->
            key(target.targetId) {
                when (markerStyle) {
                    RadarFullScreenMarkerStyle.COMPACT_BADGE -> RadarFullScreenVehicleMarker(
                        target = target,
                        maxRangeMeters = maxRangeMeters,
                        trafficOrientation = trafficOrientation,
                        usableWidthPx = usableWidthPx,
                        usableHeightPx = usableHeightPx
                    )
                    RadarFullScreenMarkerStyle.TOP_DOWN_VEHICLES -> RadarTopDownVehicleMarker(
                        target = target,
                        maxRangeMeters = maxRangeMeters,
                        trafficOrientation = trafficOrientation,
                        usableWidthPx = usableWidthPx,
                        usableHeightPx = usableHeightPx
                    )
                }
            }
        }
    }
}

/**
 * Touch-transparent, elevated, handlebar-legible stadium capsule marker for a single approaching
 * radar vehicle in FULL_SCREEN mode with neutral V1/V2 iconography, enlarged tabular distance
 * readout, 100% solid opaque threat fill, dual-rim framing, and single-pass `.layout` clamping
 * (REQ-TRK-089.1/.2/.3, ATT-8958).
 */
@Composable
private fun RadarFullScreenVehicleMarker(
    target: RadarTarget,
    maxRangeMeters: Float,
    trafficOrientation: RadarTrafficOrientation,
    usableWidthPx: Float,
    usableHeightPx: Float
) {
    val spec = resolveFullScreenVehicleVisualSpec(target.vehicleSizeCategory)
    val vehicleDesc = stringResource(spec.descriptionRes)
    val distanceMeters = target.rangeMeters.roundToInt()

    val normalizedY = RadarOverlayMath.normalizeRadarRange(target.rangeMeters, maxRangeMeters)
    val animatedFractionState = animateFloatAsState(
        targetValue = normalizedY,
        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
        label = "fullScreenTarget_${target.targetId}_pos"
    )
    val horizontalFraction = RadarOverlayMath.calculateFullScreenHorizontalFraction(target, trafficOrientation)

    val baseThreatColor = RadarOverlayMath.resolveThreatColor(target.threatLevel)
    val contentColor = if (target.threatLevel == ThreatLevel.APPROACHING) {
        TTColor.RadarFullScreenApproachingContent
    } else {
        TTColor.RadarFullScreenFastContent
    }

    val capsuleShape = RoundedCornerShape(percent = 50)

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val fraction = animatedFractionState.value
                val perspectiveScale = 0.94f + (1.0f - fraction) * 0.12f
                val scaleOverhangX = (perspectiveScale - 1f).coerceAtLeast(0f) * placeable.width * 0.5f
                val scaleOverhangY = (perspectiveScale - 1f).coerceAtLeast(0f) * placeable.height * 0.5f

                val minX = ceil(scaleOverhangX).toInt()
                val maxX = floor(usableWidthPx - placeable.width - scaleOverhangX).toInt().coerceAtLeast(minX)
                val desiredCenterX = horizontalFraction * usableWidthPx
                val xOffsetPx = (desiredCenterX - placeable.width / 2f).roundToInt().coerceIn(minX, maxX)

                val minY = ceil(scaleOverhangY).toInt()
                val maxY = floor(usableHeightPx - placeable.height - scaleOverhangY).toInt().coerceAtLeast(minY)
                val travelHeightPx = (maxY - minY).toFloat().coerceAtLeast(0f)
                val yOffsetPx = (minY + fraction * travelHeightPx).roundToInt().coerceIn(minY, maxY)

                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(xOffsetPx, yOffsetPx)
                }
            }
            .graphicsLayer {
                val perspectiveScale = 0.94f + (1.0f - animatedFractionState.value) * 0.12f
                scaleX = perspectiveScale
                scaleY = perspectiveScale
            }
            .background(TTColor.RadarFullScreenMarkerScrim, capsuleShape)
            .padding(2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$vehicleDesc, ${distanceMeters}m"
            }
            .testTag("RadarFullScreenMarker_${target.targetId}")
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = spec.minWidth)
                .heightIn(min = spec.minHeight)
                .shadow(elevation = 6.dp, shape = capsuleShape, clip = false)
                .drawBehind {
                    val radiusPx = size.height / 2f
                    drawRoundRect(
                        color = baseThreatColor,
                        cornerRadius = CornerRadius(radiusPx, radiusPx)
                    )
                }
                .border(
                    width = 1.5.dp,
                    color = TTColor.RadarFullScreenMarkerInnerBorder,
                    shape = capsuleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(spec.iconSize)
                )
                Text(
                    text = "${distanceMeters}m",
                    color = contentColor,
                    fontSize = spec.fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}

/**
 * Resolves localized snooze label for threat badge accessibility descriptions (REQ-TRK-064.2).
 */
@Composable
private fun resolveSnoozeLabel(
    snoozeState: RadarAudioSnoozeState,
    snoozeBadgeText: String?
): String = when {
    snoozeState.mode == RadarAudioSnoozeMode.FAST_ONLY -> stringResource(R.string.radar_snooze_fast_only)
    snoozeState.isPelotonSyncActive -> stringResource(R.string.radar_light_mode_peloton)
    snoozeState.isUrbanMuteActive -> stringResource(R.string.radar_smart_urban_mute_title)
    snoozeBadgeText != null -> snoozeBadgeText
    else -> stringResource(R.string.radar_snooze_title)
}

/**
 * Top compact clickable status badge pill for disconnect error and active threats (REQ-TRK-062.3/.4).
 */
@Composable
private fun RadarStatusBadgePill(
    radarState: RadarState,
    isDisconnectError: Boolean,
    pulseAlphaProvider: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description: String
    val badgeColor: Color
    val badgeTextColor: Color

    val snoozeState = radarState.audioSnoozeState
    var snoozeClockMs by remember(snoozeState.snoozeUntilElapsedRealtimeMs) {
        mutableStateOf(RadarSnoozeController.DEFAULT_TIME_PROVIDER())
    }
    LaunchedEffect(snoozeState.isSnoozed, snoozeState.snoozeUntilElapsedRealtimeMs) {
        val until = snoozeState.snoozeUntilElapsedRealtimeMs
        if (snoozeState.isSnoozed && until != null) {
            while (true) {
                val now = RadarSnoozeController.DEFAULT_TIME_PROVIDER()
                snoozeClockMs = now
                if (now >= until) break
                delay(1000L)
            }
        }
    }
    val snoozeBadgeText = RadarOverlayMath.formatSnoozeBadgeText(snoozeState, snoozeClockMs)
    val isFastApproaching = !isDisconnectError && radarState.overallThreatLevel == ThreatLevel.FAST_APPROACHING

    when {
        isDisconnectError -> {
            description = stringResource(R.string.radar_overlay_disconnected)
            badgeColor = TTColor.RadarDisconnectedAmber
            badgeTextColor = Color.White
        }
        isFastApproaching -> {
            val baseDesc = stringResource(R.string.radar_overlay_threats_fast_format, radarState.targets.size)
            description = if (snoozeState.isSnoozed) {
                val snoozeLabel = resolveSnoozeLabel(snoozeState, snoozeBadgeText)
                "$baseDesc, " + stringResource(R.string.radar_snooze_active_format, snoozeLabel)
            } else {
                baseDesc
            }
            badgeColor = TTColor.RadarThreatRed
            badgeTextColor = Color.White
        }
        else -> {
            val baseDesc = stringResource(R.string.radar_overlay_threats_format, radarState.targets.size)
            description = if (snoozeState.isSnoozed) {
                val snoozeLabel = resolveSnoozeLabel(snoozeState, snoozeBadgeText)
                "$baseDesc, " + stringResource(R.string.radar_snooze_active_format, snoozeLabel)
            } else {
                baseDesc
            }
            badgeColor = TTColor.RadarThreatAmber
            badgeTextColor = Color.Black
        }
    }

    val hasThreats = radarState.targets.isNotEmpty() || radarState.overallThreatLevel != ThreatLevel.NONE
    val isSnoozedWithThreats = snoozeState.isSnoozed && hasThreats && !isDisconnectError
    val badgeShape = if (isSnoozedWithThreats) RoundedCornerShape(12.dp) else CircleShape
    val sizedModifier = if (isSnoozedWithThreats) {
        modifier.width(24.dp).height(34.dp)
    } else {
        modifier.size(24.dp)
    }
    val badgeModifier = sizedModifier
        .clip(badgeShape)
        .drawBehind {
            val alpha = if (isFastApproaching) pulseAlphaProvider() else 1.0f
            drawRect(color = badgeColor.copy(alpha = alpha))
        }
        .clickable(onClick = onClick)
        .semantics { this.contentDescription = description }
        .testTag("RadarStatusBadge")

    Box(
        modifier = badgeModifier,
        contentAlignment = Alignment.Center
    ) {
        when {
            isDisconnectError -> {
                Icon(
                    painter = painterResource(R.drawable.ic_device_not_available),
                    contentDescription = stringResource(R.string.radar_overlay_disconnected),
                    modifier = Modifier.size(16.dp),
                    tint = badgeTextColor
                )
            }
            isSnoozedWithThreats -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_radar_bell_muted),
                            contentDescription = null,
                            modifier = Modifier.size(8.dp),
                            tint = badgeTextColor
                        )
                        if (snoozeBadgeText != null) {
                            Text(
                                text = snoozeBadgeText,
                                color = badgeTextColor,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "${radarState.targets.size}",
                        color = badgeTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            else -> {
                Text(
                    text = "${radarState.targets.size}",
                    color = badgeTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Vertical rail track displaying approaching target dots (REQ-TRK-062.1).
 */
@Suppress("LongMethod")
@Composable
private fun RadarVerticalRail(
    radarState: RadarState,
    hasActiveThreats: Boolean,
    isClear: Boolean,
    isDisconnectError: Boolean,
    clearMode: RadarClearMode,
    pulseAlpha: Float,
    edgeSide: RadarEdgeSide = RadarEdgeSide.RIGHT,
    trafficOrientation: RadarTrafficOrientation = RadarTrafficOrientation.RIGHT_HAND,
    modifier: Modifier = Modifier
) {
    val isSlimRail = isClear && clearMode == RadarClearMode.SLIM_RAIL
    val has2DData = radarState.targets.any {
        it.lateralOffsetMeters != null || it.vehicleSizeCategory != VehicleSizeCategory.UNKNOWN_OR_LIGHT
    }
    val trackWidth = when {
        isSlimRail -> 4.dp
        has2DData -> 28.dp
        else -> 22.dp
    }

    val trackColor = when {
        isDisconnectError -> TTColor.RadarRailTrack
        isClear -> TTColor.RadarClearGreen.copy(alpha = 0.25f)
        radarState.overallThreatLevel == ThreatLevel.FAST_APPROACHING -> {
            TTColor.RadarThreatRed.copy(alpha = 0.35f * pulseAlpha)
        }
        else -> TTColor.RadarThreatAmber.copy(alpha = 0.25f)
    }

    val railShape = if (edgeSide == RadarEdgeSide.LEFT) {
        RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
    } else {
        RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
    }

    Box(
        modifier = modifier.testTag("RadarVerticalRail"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Track background
        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight()
                .clip(railShape)
                .background(trackColor)
                .border(
                    width = 1.dp,
                    color = if (isSlimRail) Color.Transparent else TTColor.RadarRailTrackBorder,
                    shape = railShape
                )
        )

        // Target dots inside usable travel height
        if (hasActiveThreats) {
            val contentWidth = if (has2DData) 28.dp else 22.dp
            val maxRangeMeters = RadarOverlayMath.resolveRailMaxRange(radarState)

            BoxWithConstraints(
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
            ) {
                val usableHeightPx = constraints.maxHeight.toFloat()

                radarState.targets.forEach { target ->
                    key(target.targetId) {
                        val (targetWidthDp, targetHeightDp, targetShape) = when (target.vehicleSizeCategory) {
                            VehicleSizeCategory.UNKNOWN_OR_LIGHT -> Triple(10.dp, 10.dp, CircleShape)
                            VehicleSizeCategory.CAR -> Triple(14.dp, 14.dp, CircleShape)
                            VehicleSizeCategory.TRUCK_OR_BUS -> Triple(20.dp, 12.dp, RoundedCornerShape(4.dp))
                        }

                        val targetHeightPx = with(LocalDensity.current) { targetHeightDp.toPx() }
                        val travelHeightPx = (usableHeightPx - targetHeightPx).coerceAtLeast(0f)

                        val normalizedY = RadarOverlayMath.normalizeRadarRange(target.rangeMeters, maxRangeMeters)
                        val animatedFraction by animateFloatAsState(
                            targetValue = normalizedY,
                            animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                            label = "target_${target.targetId}_pos"
                        )

                        val yOffsetPx = (animatedFraction * travelHeightPx).roundToInt()
                        val xOffsetDp = RadarOverlayMath.calculateHorizontalOffsetDp(target, trafficOrientation)
                        val xOffsetPx = with(LocalDensity.current) { xOffsetDp.dp.toPx() }.roundToInt()
                        val dotColor = RadarOverlayMath.resolveThreatColor(target.threatLevel)

                        val vehicleDesc = when (target.vehicleSizeCategory) {
                            VehicleSizeCategory.TRUCK_OR_BUS -> stringResource(R.string.radar_vehicle_size_truck)
                            VehicleSizeCategory.CAR -> stringResource(R.string.radar_vehicle_size_car)
                            VehicleSizeCategory.UNKNOWN_OR_LIGHT -> null
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset { IntOffset(x = xOffsetPx, y = yOffsetPx) }
                                .size(width = targetWidthDp, height = targetHeightDp)
                                .clip(targetShape)
                                .background(dotColor)
                                .border(1.5.dp, Color.White, targetShape)
                                .semantics {
                                    if (vehicleDesc != null) {
                                        contentDescription = "$vehicleDesc, ${target.rangeMeters.roundToInt()}m"
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Radar Edge Overlay - Clear Light", showBackground = true)
@Composable
private fun PreviewRadarEdgeOverlayClearLight() {
    ATrainingTrackerTheme {
        Surface {
            RadarEdgeOverlay(
                radarState = RadarState(
                    connectionState = RadarConnectionState.CONNECTED,
                    overallThreatLevel = ThreatLevel.NONE,
                    targets = emptyList()
                ),
                edgeSide = RadarEdgeSide.RIGHT,
                clearMode = RadarClearMode.SLIM_RAIL,
                modifier = Modifier.height(400.dp)
            )
        }
    }
}

@Preview(
    name = "Radar Edge Overlay - Approaching Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun PreviewRadarEdgeOverlayApproachingDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            RadarEdgeOverlay(
                radarState = RadarState(
                    connectionState = RadarConnectionState.CONNECTED,
                    overallThreatLevel = ThreatLevel.APPROACHING,
                    targets = listOf(
                        RadarTarget(
                            targetId = 1,
                            rangeMeters = 80f,
                            approachSpeedMps = 10f,
                            threatLevel = ThreatLevel.APPROACHING
                        ),
                        RadarTarget(
                            targetId = 2,
                            rangeMeters = 30f,
                            approachSpeedMps = 12f,
                            threatLevel = ThreatLevel.APPROACHING
                        )
                    )
                ),
                edgeSide = RadarEdgeSide.RIGHT,
                clearMode = RadarClearMode.SLIM_RAIL,
                modifier = Modifier.height(400.dp)
            )
        }
    }
}

@Preview(
    name = "Radar Edge Overlay - Fast Threat AMOLED",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun PreviewRadarEdgeOverlayFastThreatAmoled() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(color = Color.Black) {
            RadarEdgeOverlay(
                radarState = RadarState(
                    connectionState = RadarConnectionState.CONNECTED,
                    overallThreatLevel = ThreatLevel.FAST_APPROACHING,
                    targets = listOf(
                        RadarTarget(
                            targetId = 1,
                            rangeMeters = 40f,
                            approachSpeedMps = 22f,
                            threatLevel = ThreatLevel.FAST_APPROACHING
                        )
                    )
                ),
                edgeSide = RadarEdgeSide.RIGHT,
                clearMode = RadarClearMode.SLIM_RAIL,
                modifier = Modifier.height(400.dp)
            )
        }
    }
}

@Preview(name = "Radar Edge Overlay - Disconnected", showBackground = true)
@Composable
private fun PreviewRadarEdgeOverlayDisconnected() {
    ATrainingTrackerTheme {
        Surface {
            RadarEdgeOverlay(
                radarState = RadarState(
                    connectionState = RadarConnectionState.DISCONNECTED_ERROR,
                    overallThreatLevel = ThreatLevel.NONE,
                    targets = emptyList()
                ),
                edgeSide = RadarEdgeSide.RIGHT,
                clearMode = RadarClearMode.SLIM_RAIL,
                modifier = Modifier.height(400.dp)
            )
        }
    }
}

@Preview(name = "Radar FullScreen - Mixed V2 Light", showBackground = true)
@Composable
private fun PreviewRadarFullScreenOverlayLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(modifier = Modifier.width(320.dp).height(420.dp)) {
            RadarEdgeOverlay(
                radarState = RadarState(
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
                ),
                edgeSide = RadarEdgeSide.FULL_SCREEN,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(
    name = "Radar FullScreen - Mixed V2 Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun PreviewRadarFullScreenOverlayDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(color = Color.Black, modifier = Modifier.width(320.dp).height(420.dp)) {
            RadarEdgeOverlay(
                radarState = RadarState(
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
                ),
                edgeSide = RadarEdgeSide.FULL_SCREEN,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(name = "Radar FullScreen - Map Backdrop", showBackground = true)
@Composable
private fun PreviewRadarFullScreenOverlayMapBackdrop() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(
            color = TTColor.RadarFullScreenMapBackdrop,
            modifier = Modifier.width(320.dp).height(420.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                RadarEdgeOverlay(
                    radarState = RadarState(
                        connectionState = RadarConnectionState.CONNECTED,
                        overallThreatLevel = ThreatLevel.APPROACHING,
                        targets = listOf(
                            RadarTarget(
                                targetId = 1,
                                rangeMeters = 45f,
                                approachSpeedMps = 13f,
                                threatLevel = ThreatLevel.APPROACHING,
                                lateralOffsetMeters = 3.0f,
                                vehicleSizeCategory = VehicleSizeCategory.CAR
                            ),
                            RadarTarget(
                                targetId = 2,
                                rangeMeters = 148f,
                                approachSpeedMps = 10f,
                                threatLevel = ThreatLevel.APPROACHING,
                                lateralOffsetMeters = -3.0f,
                                vehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT
                            )
                        )
                    ),
                    edgeSide = RadarEdgeSide.FULL_SCREEN,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Preview(name = "Radar FullScreen - V1 1D No Lane Guides", showBackground = true)
@Composable
private fun PreviewRadarFullScreenOverlayV1Light() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(modifier = Modifier.width(360.dp).height(480.dp)) {
            RadarEdgeOverlay(
                radarState = RadarState(
                    connectionState = RadarConnectionState.CONNECTED,
                    overallThreatLevel = ThreatLevel.FAST_APPROACHING,
                    targets = listOf(
                        RadarTarget(
                            targetId = 1,
                            rangeMeters = 80f,
                            approachSpeedMps = 12f,
                            threatLevel = ThreatLevel.APPROACHING
                        ),
                        RadarTarget(
                            targetId = 2,
                            rangeMeters = 123f,
                            approachSpeedMps = 22f,
                            threatLevel = ThreatLevel.FAST_APPROACHING
                        )
                    )
                ),
                edgeSide = RadarEdgeSide.FULL_SCREEN,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
