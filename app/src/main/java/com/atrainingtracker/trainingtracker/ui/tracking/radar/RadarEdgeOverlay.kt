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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
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
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Pure helper and kinematics functions for cycling radar edge overlay (REQ-TRK-062).
 */
object RadarOverlayMath {
    const val MAX_RADAR_RANGE_METERS = 140.0f
    const val MAX_RADAR_RANGE_V2_METERS = 175.0f

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
     * Formats compact status badge pill text during audio snooze / suppression (REQ-TRK-064.2).
     */
    fun formatSnoozeBadgeText(snoozeState: RadarAudioSnoozeState): String? {
        if (!snoozeState.isSnoozed) return null
        val secs = snoozeState.remainingSeconds
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
 * - Only the top compact status badge pill consumes tap events for reconnect/settings (REQ-TRK-062.3).
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
    modifier: Modifier = Modifier,
    onBadgeClick: () -> Unit = {},
    onReconnectClick: (() -> Unit)? = null
) {
    val hasActiveThreats = radarState.targets.isNotEmpty() || radarState.overallThreatLevel != ThreatLevel.NONE
    val isConnected = radarState.connectionState == RadarConnectionState.CONNECTED
    val isClear = isConnected && !hasActiveThreats
    val isDisconnectError = RadarOverlayMath.isFailSafeDisconnect(radarState.connectionState)
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

    // Pulsing animation for FAST_APPROACHING red alert (only composed when fast threats active)
    val pulseAlpha = if (radarState.overallThreatLevel == ThreatLevel.FAST_APPROACHING) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(350, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "fastApproachingPulse"
        )
        alpha
    } else {
        1.0f
    }

    if (edgeSide == RadarEdgeSide.FULL_SCREEN) {
        // FULL_SCREEN Mode (REQ-TRK-062.1a, REQ-TRK-062.3a, REQ-TRK-062.5b)
        Box(
            modifier = modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .testTag("RadarEdgeOverlay")
        ) {
            // Full-screen vehicle threat overlay (zero pointer capture)
            if (hasActiveThreats) {
                RadarFullScreenThreatOverlay(
                    radarState = radarState,
                    pulseAlpha = pulseAlpha,
                    trafficOrientation = trafficOrientation,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 36.dp, bottom = 12.dp)
                )
            }

            // Top Status Badge Pill (only clickable element in full-screen overlay)
            RadarStatusBadgePill(
                radarState = radarState,
                isClear = isClear,
                isDisconnectError = isDisconnectError,
                pulseAlpha = pulseAlpha,
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
    } else {
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
            Spacer(modifier = Modifier.height(8.dp))

            // Top Status Badge Pill (only clickable element in the overlay, permanently visible)
            RadarStatusBadgePill(
                radarState = radarState,
                isClear = isClear,
                isDisconnectError = isDisconnectError,
                pulseAlpha = pulseAlpha,
                onClick = {
                    if (isDisconnectError && onReconnectClick != null) {
                        onReconnectClick()
                    } else {
                        onBadgeClick()
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

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
                    pulseAlpha = pulseAlpha,
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
 * Full-screen vehicle threat overlay rendering approaching vehicles directly on top
 * of the Tracking Screen with 100% touch pass-through (REQ-TRK-062.1a, REQ-TRK-062.3a).
 */
@Composable
private fun RadarFullScreenThreatOverlay(
    radarState: RadarState,
    pulseAlpha: Float,
    trafficOrientation: RadarTrafficOrientation,
    modifier: Modifier = Modifier
) {
    val maxRangeMeters = RadarOverlayMath.resolveRailMaxRange(radarState)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val usableHeightPx = constraints.maxHeight.toFloat()
        val usableWidthPx = constraints.maxWidth.toFloat()

        radarState.targets.forEach { target ->
            key(target.targetId) {
                val (targetWidthDp, targetHeightDp, targetShape) = when (target.vehicleSizeCategory) {
                    VehicleSizeCategory.UNKNOWN_OR_LIGHT -> Triple(28.dp, 20.dp, RoundedCornerShape(6.dp))
                    VehicleSizeCategory.CAR -> Triple(32.dp, 22.dp, RoundedCornerShape(6.dp))
                    VehicleSizeCategory.TRUCK_OR_BUS -> Triple(36.dp, 26.dp, RoundedCornerShape(6.dp))
                }

                val targetHeightPx = with(LocalDensity.current) { targetHeightDp.toPx() }
                val targetWidthPx = with(LocalDensity.current) { targetWidthDp.toPx() }
                val travelHeightPx = (usableHeightPx - targetHeightPx).coerceAtLeast(0f)
                val travelWidthPx = (usableWidthPx - targetWidthPx).coerceAtLeast(0f)

                val normalizedY = RadarOverlayMath.normalizeRadarRange(target.rangeMeters, maxRangeMeters)
                val animatedFraction by animateFloatAsState(
                    targetValue = normalizedY,
                    animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                    label = "fullScreenTarget_${target.targetId}_pos"
                )

                val yOffsetPx = (animatedFraction * travelHeightPx).roundToInt()
                val horizontalFraction = RadarOverlayMath.calculateFullScreenHorizontalFraction(target, trafficOrientation)
                val xOffsetPx = (horizontalFraction * travelWidthPx).roundToInt()

                val baseThreatColor = RadarOverlayMath.resolveThreatColor(target.threatLevel)
                val badgeColor = if (target.threatLevel == ThreatLevel.FAST_APPROACHING) {
                    baseThreatColor.copy(alpha = 0.85f * pulseAlpha)
                } else {
                    baseThreatColor.copy(alpha = 0.85f)
                }
                val badgeTextColor = if (target.threatLevel == ThreatLevel.APPROACHING) Color.Black else Color.White

                val vehicleDesc = when (target.vehicleSizeCategory) {
                    VehicleSizeCategory.TRUCK_OR_BUS -> stringResource(R.string.radar_vehicle_size_truck)
                    VehicleSizeCategory.CAR -> stringResource(R.string.radar_vehicle_size_car)
                    VehicleSizeCategory.UNKNOWN_OR_LIGHT -> null
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(x = xOffsetPx, y = yOffsetPx) }
                        .size(width = targetWidthDp, height = targetHeightDp)
                        .clip(targetShape)
                        .background(badgeColor)
                        .border(1.5.dp, Color.White, targetShape)
                        .semantics {
                            val prefix = vehicleDesc ?: "Target"
                            contentDescription = "$prefix, ${target.rangeMeters.roundToInt()}m"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${target.rangeMeters.roundToInt()}m",
                        color = badgeTextColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Top compact clickable status badge pill (REQ-TRK-062.3).
 */
@Composable
private fun RadarStatusBadgePill(
    radarState: RadarState,
    isClear: Boolean,
    isDisconnectError: Boolean,
    pulseAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description: String
    val badgeColor: Color
    val badgeTextColor: Color

    val snoozeState = radarState.audioSnoozeState
    val snoozeBadgeText = RadarOverlayMath.formatSnoozeBadgeText(snoozeState)

    when {
        isDisconnectError -> {
            description = stringResource(R.string.radar_overlay_disconnected)
            badgeColor = TTColor.RadarDisconnectedAmber
            badgeTextColor = Color.White
        }
        radarState.connectionState == RadarConnectionState.CONNECTING -> {
            description = stringResource(R.string.radar_overlay_connecting)
            badgeColor = MaterialTheme.colorScheme.primaryContainer
            badgeTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        }
        snoozeState.isSnoozed && isClear -> {
            val snoozeLabel = when {
                snoozeState.mode == RadarAudioSnoozeMode.FAST_ONLY -> stringResource(R.string.radar_snooze_fast_only)
                snoozeState.isPelotonSyncActive -> stringResource(R.string.radar_light_mode_peloton)
                snoozeState.isUrbanMuteActive -> stringResource(R.string.radar_smart_urban_mute_title)
                snoozeBadgeText != null -> snoozeBadgeText
                else -> stringResource(R.string.radar_snooze_title)
            }
            description = stringResource(R.string.radar_snooze_active_format, snoozeLabel)
            badgeColor = TTColor.RadarClearGreen
            badgeTextColor = Color.White
        }
        isClear -> {
            description = stringResource(R.string.radar_overlay_clear)
            badgeColor = TTColor.RadarClearGreen
            badgeTextColor = Color.White
        }
        radarState.overallThreatLevel == ThreatLevel.FAST_APPROACHING -> {
            val baseDesc = stringResource(R.string.radar_overlay_threats_fast_format, radarState.targets.size)
            description = if (snoozeState.isSnoozed) {
                val snoozeLabel = when {
                    snoozeState.mode == RadarAudioSnoozeMode.FAST_ONLY ->
                        stringResource(R.string.radar_snooze_fast_only)
                    snoozeState.isPelotonSyncActive ->
                        stringResource(R.string.radar_light_mode_peloton)
                    snoozeState.isUrbanMuteActive ->
                        stringResource(R.string.radar_smart_urban_mute_title)
                    snoozeBadgeText != null -> snoozeBadgeText
                    else -> stringResource(R.string.radar_snooze_title)
                }
                "$baseDesc, " + stringResource(R.string.radar_snooze_active_format, snoozeLabel)
            } else {
                baseDesc
            }
            badgeColor = TTColor.RadarThreatRed.copy(alpha = pulseAlpha)
            badgeTextColor = Color.White
        }
        else -> {
            val baseDesc = stringResource(R.string.radar_overlay_threats_format, radarState.targets.size)
            description = if (snoozeState.isSnoozed) {
                val snoozeLabel = when {
                    snoozeState.mode == RadarAudioSnoozeMode.FAST_ONLY ->
                        stringResource(R.string.radar_snooze_fast_only)
                    snoozeState.isPelotonSyncActive ->
                        stringResource(R.string.radar_light_mode_peloton)
                    snoozeState.isUrbanMuteActive ->
                        stringResource(R.string.radar_smart_urban_mute_title)
                    snoozeBadgeText != null -> snoozeBadgeText
                    else -> stringResource(R.string.radar_snooze_title)
                }
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
    val badgeModifier = if (isSnoozedWithThreats) {
        modifier
            .width(24.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(badgeColor)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = description }
            .testTag("RadarStatusBadge")
    } else {
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(badgeColor)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = description }
            .testTag("RadarStatusBadge")
    }

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
            snoozeState.isSnoozed && isClear -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_radar_bell_muted),
                        contentDescription = null,
                        modifier = Modifier.size(9.dp),
                        tint = badgeTextColor
                    )
                    if (snoozeBadgeText != null) {
                        Spacer(modifier = Modifier.width(1.dp))
                        Text(
                            text = snoozeBadgeText,
                            color = badgeTextColor,
                            fontSize = if (snoozeBadgeText.length > 2) 7.sp else 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            isClear -> {
                Icon(
                    painter = painterResource(R.drawable.ic_device_available),
                    contentDescription = stringResource(R.string.radar_overlay_clear),
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
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
        modifier = modifier,
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
