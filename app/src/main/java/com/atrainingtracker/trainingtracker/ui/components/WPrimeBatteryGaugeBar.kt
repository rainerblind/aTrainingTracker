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

package com.atrainingtracker.trainingtracker.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.atrainingtracker.trainingtracker.ui.tracking.WPrimeBatteryState

val W_PRIME_BATTERY_GAUGE_HEIGHT: Dp = 14.dp
val W_PRIME_BATTERY_END_PADDING: Dp = 3.dp
val W_PRIME_BATTERY_BADGE_MIN_WIDTH: Dp = 28.dp
val W_PRIME_BATTERY_TERMINAL_WIDTH: Dp = 2.dp
val W_PRIME_BATTERY_TERMINAL_HEIGHT: Dp = 6.dp
val W_PRIME_BATTERY_CORNER_RADIUS: Dp = 3.dp

/**
 * High-visibility anaerobic battery gauge bar (REQ-UI-416, TST-UI-379.3).
 *
 * Visualizes real-time W' balance expenditure and reconstitution as an ergonomic
 * battery capsule with terminal nipple, animated level fill, and optional percentage badge.
 * Adheres strictly to True-Black AMOLED contrast guidelines (Rule 23) and layout stability invariants.
 *
 * @param state The current W' anaerobic battery state.
 * @param modifier Composable modifier for outer positioning.
 * @param viewSize Active tile ViewSize, used to toggle percentage text badge display.
 * @param showBadge Explicit override to display or hide the text badge. Defaults to viewSize >= ViewSize.NORMAL.
 */
@Composable
fun WPrimeBatteryGaugeBar(
    state: WPrimeBatteryState,
    modifier: Modifier = Modifier,
    viewSize: ViewSize = ViewSize.NORMAL,
    showBadge: Boolean = (viewSize >= ViewSize.NORMAL)
) {
    val isStale = state.isStale || state.batteryFraction == null
    val trackAlpha = if (isStale) 0.35f else 1.0f

    val animatedFraction by animateFloatAsState(
        targetValue = if (isStale) 0f else state.batteryFraction,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "wPrimeBatteryFill"
    )

    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = trackAlpha)
    val bodyBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f * trackAlpha)
    val fillColor = state.batteryColor.copy(alpha = trackAlpha)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(W_PRIME_BATTERY_GAUGE_HEIGHT)
            .testTag("w_prime_battery_container"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Battery Capsule Body + Terminal Nipple
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("w_prime_battery_capsule")
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("w_prime_battery_canvas")
            ) {
                val totalWidth = size.width
                val totalHeight = size.height
                val terminalWidthPx = W_PRIME_BATTERY_TERMINAL_WIDTH.toPx()
                val terminalHeightPx = W_PRIME_BATTERY_TERMINAL_HEIGHT.toPx()
                val strokeWidthPx = 1.dp.toPx()
                val cornerRadiusPx = W_PRIME_BATTERY_CORNER_RADIUS.toPx()
                val endPaddingPx = W_PRIME_BATTERY_END_PADDING.toPx()

                val bodyWidth = (totalWidth - terminalWidthPx - 1.dp.toPx()).coerceAtLeast(0f)
                val bodyHeight = totalHeight

                // 1. Draw capsule body background
                drawRoundRect(
                    color = bodyBgColor,
                    topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                    size = Size(bodyWidth - strokeWidthPx, bodyHeight - strokeWidthPx),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = Fill
                )

                // 2. Draw animated fill inside capsule body, constrained by endPadding
                if (animatedFraction > 0f) {
                    val maxFillWidth = (bodyWidth - (strokeWidthPx * 2) - endPaddingPx).coerceAtLeast(0f)
                    val currentFillWidth = maxFillWidth * animatedFraction
                    if (currentFillWidth > 0f) {
                        val fillHeight = (bodyHeight - strokeWidthPx * 2).coerceAtLeast(0f)
                        val fillCornerRadius = (cornerRadiusPx - strokeWidthPx).coerceAtLeast(1f)
                        drawRoundRect(
                            color = fillColor,
                            topLeft = Offset(strokeWidthPx, strokeWidthPx),
                            size = Size(currentFillWidth, fillHeight),
                            cornerRadius = CornerRadius(fillCornerRadius, fillCornerRadius),
                            style = Fill
                        )
                    }
                }

                // 3. Draw capsule body outline
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                    size = Size(bodyWidth - strokeWidthPx, bodyHeight - strokeWidthPx),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = Stroke(width = strokeWidthPx)
                )

                // 4. Draw positive terminal nipple on the right
                val nippleTop = (bodyHeight - terminalHeightPx) / 2f
                val nippleLeft = bodyWidth
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(nippleLeft, nippleTop),
                    size = Size(terminalWidthPx, terminalHeightPx),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                    style = Fill
                )
            }
        }

        // Percentage Text Badge (displayed when showBadge == true)
        if (showBadge) {
            val badgeText = if (!isStale) {
                stringResource(R.string.w_prime_battery_pct, state.percentage)
            } else {
                "--%"
            }

            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = trackAlpha),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .widthIn(min = W_PRIME_BATTERY_BADGE_MIN_WIDTH)
                    .testTag("w_prime_battery_badge")
            )
        }
    }
}
