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
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarState
import com.atrainingtracker.banalservice.devices.radar.RadarTarget
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.trainingtracker.settings.RadarClearMode
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Pure helper and kinematics functions for cycling radar edge overlay (REQ-TRK-062).
 */
object RadarOverlayMath {
    const val MAX_RADAR_RANGE_METERS = 140.0f

    /**
     * Normalizes a target distance (0..140m) to a vertical coordinate fraction (0.0..1.0),
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
    modifier: Modifier = Modifier,
    onBadgeClick: () -> Unit = {},
    onReconnectClick: (() -> Unit)? = null
) {
    val hasActiveThreats = radarState.targets.isNotEmpty() || radarState.overallThreatLevel != ThreatLevel.NONE
    val isConnected = radarState.connectionState == RadarConnectionState.CONNECTED
    val isClear = isConnected && !hasActiveThreats
    val isDisconnectError = RadarOverlayMath.isFailSafeDisconnect(radarState.connectionState)

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

    // Outer container: width 24dp, fillMaxHeight, zero pointer capture
    Column(
        modifier = modifier
            .width(24.dp)
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
    modifier: Modifier = Modifier
) {
    val isSlimRail = isClear && clearMode == RadarClearMode.SLIM_RAIL
    val trackWidth = if (isSlimRail) 4.dp else 22.dp

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
            BoxWithConstraints(
                modifier = Modifier
                    .width(22.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
            ) {
                val usableHeightPx = constraints.maxHeight.toFloat()
                val dotSizePx = with(LocalDensity.current) { 14.dp.toPx() }
                val travelHeightPx = (usableHeightPx - dotSizePx).coerceAtLeast(0f)

                radarState.targets.forEach { target ->
                    key(target.targetId) {
                        val normalizedY = RadarOverlayMath.normalizeRadarRange(target.rangeMeters)
                        val animatedFraction by animateFloatAsState(
                            targetValue = normalizedY,
                            animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                            label = "target_${target.targetId}_pos"
                        )

                        val yOffsetPx = (animatedFraction * travelHeightPx).roundToInt()
                        val dotColor = RadarOverlayMath.resolveThreatColor(target.threatLevel)

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset { IntOffset(x = 0, y = yOffsetPx) }
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(1.5.dp, Color.White, CircleShape)
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
