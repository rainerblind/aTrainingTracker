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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Compact, high-visibility horizontal segmented training zone gauge bar (REQ-UI-376).
 *
 * Partitions the bar into equal-width contiguous segments corresponding to the athlete's
 * zone model (5-zone HR or 7-zone Power). Renders a high-contrast needle indicator with dark outline
 * centered vertically over the track. When telemetry is stale or null, dims track opacity to 0.35 alpha
 * and hides the needle.
 *
 * Adheres strictly to True-Black AMOLED contrast guidelines (REQ-UI-190, REQ-UI-220, Rule 23).
 *
 * @param state Immutable state containing segment colors, needle fraction, and active zone index.
 * @param modifier Composable modifier for layout customization.
 * @param trackHeight Height of the horizontal zone track (default 6.dp).
 * @param showBadge Whether to display the compact active zone badge (e.g. "Z3") adjacent to the bar.
 */
@Composable
fun ZoneGaugeBar(
    state: ZoneGaugeState,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 6.dp,
    showBadge: Boolean = false
) {
    if (state.segmentColors.isEmpty()) return

    val isStale = (state.needleFraction == null)
    val trackAlpha = if (isStale) 0.35f else 1.0f
    val barShape = RoundedCornerShape(trackHeight / 2)
    val outlineBorder = BorderStroke(
        width = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
    val totalBarHeight = trackHeight + 4.dp // 2dp needle overhang top & bottom

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Track + Needle Overlay
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(totalBarHeight)
                .testTag("zone_gauge_bar_container"),
            contentAlignment = Alignment.Center
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val totalHeightPx = constraints.maxHeight.toFloat()
            val trackHeightPx = (trackHeight.value / totalBarHeight.value) * totalHeightPx
            val trackTopPx = (totalHeightPx - trackHeightPx) / 2f

            // 1. Horizontal Segmented Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(barShape)
                    .border(outlineBorder, barShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    state.segmentColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(color.copy(alpha = trackAlpha))
                        )
                        if (index < state.segmentColors.lastIndex) {
                            Spacer(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                        }
                    }
                }
            }

            // 2. High-Contrast Needle Overlay
            if (!isStale && totalWidthPx > 0f) {
                val needleFraction = state.needleFraction.coerceIn(0.0f, 1.0f)
                val needleWidthPx = 3.dp.value * (totalHeightPx / totalBarHeight.value)
                val halfNeedleWidth = needleWidthPx / 2f
                val clampedCenterX = (needleFraction * totalWidthPx).coerceIn(halfNeedleWidth, totalWidthPx - halfNeedleWidth)

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("zone_gauge_needle")
                ) {
                    val needleLeft = clampedCenterX - halfNeedleWidth
                    val needleTop = 0f
                    val needleHeight = size.height
                    val cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())

                    // Dark shadow / outline (ensures > 12:1 contrast against bright zones like Chartreuse)
                    drawRoundRect(
                        color = TTColor.ZoneGaugeTrack,
                        topLeft = Offset(needleLeft - 0.75.dp.toPx(), needleTop),
                        size = Size(needleWidthPx + 1.5.dp.toPx(), needleHeight),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Pure White Fill
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(needleLeft, needleTop),
                        size = Size(needleWidthPx, needleHeight),
                        cornerRadius = cornerRadius
                    )
                }
            }
        }

        // Optional Active Zone Badge
        if (showBadge && state.activeZoneIndex != null) {
            val activeColor = state.segmentColors.getOrElse(state.activeZoneIndex - 1) { MaterialTheme.colorScheme.primary }
            Text(
                text = "Z${state.activeZoneIndex}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = activeColor,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .testTag("zone_gauge_badge")
            )
        }
    }
}

// ==========================================
// Previews
// ==========================================

@Preview(name = "HR 5-Zone Active - Light Theme", showBackground = true)
@Composable
private fun PreviewZoneGaugeBarHrLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(modifier = Modifier.padding(16.dp)) {
            ZoneGaugeBar(
                state = ZoneGaugeState(
                    segmentColors = ZoneGaugeMath.HR_SEGMENT_COLORS,
                    needleFraction = 0.55f,
                    activeZoneIndex = 3
                ),
                showBadge = true
            )
        }
    }
}

@Preview(name = "Power 7-Zone Active - AMOLED Dark Theme", showBackground = true)
@Composable
private fun PreviewZoneGaugeBarPowerAmoled() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = Color.Black
        ) {
            ZoneGaugeBar(
                state = ZoneGaugeState(
                    segmentColors = ZoneGaugeMath.POWER_SEGMENT_COLORS,
                    needleFraction = 0.72f,
                    activeZoneIndex = 5
                ),
                showBadge = true
            )
        }
    }
}

@Preview(name = "Power 7-Zone Stale - Dark Theme", showBackground = true)
@Composable
private fun PreviewZoneGaugeBarPowerStale() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(modifier = Modifier.padding(16.dp)) {
            ZoneGaugeBar(
                state = ZoneGaugeState(
                    segmentColors = ZoneGaugeMath.POWER_SEGMENT_COLORS,
                    needleFraction = null,
                    activeZoneIndex = null
                ),
                showBadge = true
            )
        }
    }
}
