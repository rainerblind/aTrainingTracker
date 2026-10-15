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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlin.math.abs
import kotlin.math.min

private val NEEDLE_OVERHANG_DP: Dp = 2.dp
private val NEEDLE_TOTAL_EXTRA_HEIGHT: Dp = NEEDLE_OVERHANG_DP * 2

/**
 * Compact, high-visibility visual pedal power balance indicator bar (REQ-UI-377).
 *
 * Displays a center-anchored horizontal split track showing left/right power balance
 * contribution, deviation magnitude, dominant side, and physiological asymmetry tier
 * color coding (Optimal Green, Mild Amber, Severe Red).
 *
 * Adheres strictly to True-Black AMOLED contrast guidelines (REQ-UI-190, REQ-UI-220, Rule 23).
 * Explicitly forces LTR layout direction to guarantee Left/Right physical pedal spatial alignment
 * invariant of device locale.
 *
 * @param state Immutable state containing Left/Right percentages, deviation, tier color, and indicator fraction.
 * @param modifier Composable modifier for layout customization.
 * @param trackHeight Height of the horizontal balance track (default 8.dp).
 */
@Composable
fun PedalPowerBalanceBar(
    state: PedalBalanceState,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 8.dp
) {
    val isStale = state.isStale || state.indicatorFraction == null
    val trackAlpha = if (isStale) 0.35f else 1.0f
    val barShape = RoundedCornerShape(trackHeight / 2)
    val outlineBorder = BorderStroke(
        width = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
    val totalBarHeight = trackHeight + NEEDLE_TOTAL_EXTRA_HEIGHT

    // Explicitly enforce LTR so mechanical left/right bicycle pedal geometry is preserved regardless of locale
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Peripheral Left Marker
            Text(
                text = stringResource(R.string.pedal_balance_left_marker),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = trackAlpha),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .testTag("pedal_balance_label_l")
            )

            // Main Balance Track + Deviation Fill + Needle Overlay
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(totalBarHeight)
                    .testTag("pedal_balance_bar_container"),
                contentAlignment = Alignment.Center
            ) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val totalHeightPx = constraints.maxHeight.toFloat()

                // 1. Horizontal Balance Track with Center Anchor & Deviation Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(barShape)
                        .border(outlineBorder, barShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pedal_balance_track_canvas")
                    ) {
                        val width = size.width
                        val height = size.height
                        val centerX = width / 2f

                        // Draw deviation fill from center (50%) towards needle position
                        if (!isStale) {
                            val needleX = (state.indicatorFraction * width).coerceIn(0f, width)
                            val leftFill = min(centerX, needleX)
                            val fillWidth = abs(needleX - centerX)
                            if (fillWidth > 0f) {
                                drawRect(
                                    color = state.tierColor.copy(alpha = trackAlpha),
                                    topLeft = Offset(leftFill, 0f),
                                    size = Size(fillWidth, height)
                                )
                            }
                        }

                        // Center 50/50 anchor divider
                        val dividerWidthPx = 1.5.dp.toPx()
                        drawLine(
                            color = Color.White.copy(alpha = 0.7f * trackAlpha),
                            start = Offset(centerX, 0f),
                            end = Offset(centerX, height),
                            strokeWidth = dividerWidthPx
                        )
                    }
                }

                // 2. High-Contrast Needle Overlay
                if (!isStale && totalWidthPx > 0f) {
                    val needleFraction = state.indicatorFraction.coerceIn(0.0f, 1.0f)
                    val needleWidthPx = 3.dp.value * (totalHeightPx / totalBarHeight.value)
                    val halfNeedleWidth = needleWidthPx / 2f
                    val clampedCenterX = (needleFraction * totalWidthPx).coerceIn(halfNeedleWidth, totalWidthPx - halfNeedleWidth)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pedal_balance_needle")
                    ) {
                        val needleLeft = clampedCenterX - halfNeedleWidth
                        val needleTop = 0f
                        val needleHeight = size.height
                        val cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())

                        // Dark shadow / outline (ensures > 12:1 contrast against bright fills)
                        drawRoundRect(
                            color = Color(0xFF1B1B1F),
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

            // Peripheral Right Marker
            Text(
                text = stringResource(R.string.pedal_balance_right_marker),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = trackAlpha),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .testTag("pedal_balance_label_r")
            )
        }
    }
}

// ==========================================
// Previews
// ==========================================

@Preview(name = "Optimal Balance 50/50 - Light Theme", showBackground = true)
@Composable
private fun PreviewPedalPowerBalanceBarLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(modifier = Modifier.padding(16.dp)) {
            PedalPowerBalanceBar(
                state = PedalPowerBalanceMath.calculateBalanceState(50)
            )
        }
    }
}

@Preview(name = "Mild Asymmetry 46/54 - Dark Theme", showBackground = true)
@Composable
private fun PreviewPedalPowerBalanceBarDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(modifier = Modifier.padding(16.dp)) {
            PedalPowerBalanceBar(
                state = PedalPowerBalanceMath.calculateBalanceState(54)
            )
        }
    }
}

@Preview(name = "Severe Asymmetry 40/60 - AMOLED Dark Theme", showBackground = true)
@Composable
private fun PreviewPedalPowerBalanceBarAmoled() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = Color.Black
        ) {
            PedalPowerBalanceBar(
                state = PedalPowerBalanceMath.calculateBalanceState(60)
            )
        }
    }
}

@Preview(name = "Stale / Disconnected - AMOLED Dark Theme", showBackground = true)
@Composable
private fun PreviewPedalPowerBalanceBarStale() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = Color.Black
        ) {
            PedalPowerBalanceBar(
                state = PedalPowerBalanceMath.calculateBalanceState(null)
            )
        }
    }
}
