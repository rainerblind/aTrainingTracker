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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Pure mathematical calculations, active entry filtering, and width-threshold evaluation
 * for stacked distribution bars (REQ-UI-372 / REQ-UI-398).
 */
object ZoneDistributionStackedBarMath {
    const val DEFAULT_CHAR_WIDTH_DP = 6.5f
    const val DEFAULT_HORIZONTAL_PADDING_DP = 4f

    /**
     * Filters and returns active zone entries with non-zero duration and positive percentage.
     */
    fun getActiveEntries(entries: List<ZoneTimeEntry>): List<ZoneTimeEntry> {
        return entries.filter { it.durationSec > 0L && it.percentage > 0f }
    }

    /**
     * Returns true if all entries are inactive or the distribution contains zero active time.
     */
    fun isAllZeroDuration(distribution: ZoneDistributionData): Boolean {
        return distribution.totalActiveTimeSec <= 0L || getActiveEntries(distribution.entries).isEmpty()
    }

    /**
     * Formats a zone segment time percentage as a compact whole-integer percentage string (e.g., "24%") (REQ-UI-398.1).
     */
    fun formatSegmentPercentage(percentage: Float, locale: Locale = Locale.getDefault()): String {
        return String.format(locale, "%.0f%%", percentage)
    }

    /**
     * Computes the minimum horizontal segment width in dp required to display [labelText] on a single line
     * without clipping or crowding adjacent segment labels (REQ-UI-398.1).
     */
    fun minRequiredSegmentWidthDp(
        labelText: String,
        charWidthDp: Float = DEFAULT_CHAR_WIDTH_DP,
        horizontalPaddingDp: Float = DEFAULT_HORIZONTAL_PADDING_DP
    ): Float {
        return labelText.length * charWidthDp + horizontalPaddingDp
    }

    /**
     * Returns true if a stacked bar segment of width [segmentWidthDp] has sufficient horizontal space
     * to render [labelText] cleanly on a single line (REQ-UI-398.1).
     */
    fun canFitPercentageLabel(
        segmentWidthDp: Float,
        labelText: String,
        charWidthDp: Float = DEFAULT_CHAR_WIDTH_DP,
        horizontalPaddingDp: Float = DEFAULT_HORIZONTAL_PADDING_DP
    ): Boolean {
        return labelText.isNotEmpty() &&
            segmentWidthDp >= minRequiredSegmentWidthDp(labelText, charWidthDp, horizontalPaddingDp)
    }
}

/**
 * Continuous horizontal stacked distribution bar for metabolic and physiological training zones (REQ-UI-372 / REQ-UI-387 / REQ-UI-398).
 *
 * - Renders a contiguous horizontal bar representing 100% of active workout time.
 * - Active zone segment widths are weighted strictly proportionally using Compose weight(entry.percentage).
 * - Directly beneath the stacked bar, renders an aligned horizontal Row displaying each active segment's
 *   formatted percentage (e.g. "24%") centered below the segment when sufficient horizontal space is available (REQ-UI-398).
 * - Uniform contour border around the bar container and circular legend swatches ensures WCAG 2.1 AA
 *   contrast (>= 3:1) across both light and dark themes.
 * - Zero-duration zones are omitted from the horizontal bar and rendered with dimmed alpha in the legend
 *   when [showLegend] is enabled.
 *
 * @param distribution The physiological zone distribution dataset.
 * @param modifier Compose layout modifier.
 * @param barHeight Height of the continuous horizontal stacked bar (defaults to `16.dp`).
 * @param showLegend Controls whether the responsive `FlowRow` per-zone time and percentage breakdown
 *   list is rendered beneath the bar. Defaults to `false` so `ZoneCardDisplayMode.STACKED_BAR` serves
 *   as a clean, compact single-bar overview (`REQ-UI-387` / `ATT-3772`); pass `true` only when an
 *   explicit per-zone breakdown legend is desired beneath the stacked bar.
 */
@Composable
fun ZoneDistributionStackedBar(
    distribution: ZoneDistributionData,
    modifier: Modifier = Modifier,
    barHeight: Dp = 16.dp,
    showLegend: Boolean = false
) {
    val activeEntries = ZoneDistributionStackedBarMath.getActiveEntries(distribution.entries)
    val barShape = RoundedCornerShape(6.dp)
    val outlineBorder = BorderStroke(
        width = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Continuous Horizontal Stacked Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(barShape)
                    .border(outlineBorder, barShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                if (activeEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        activeEntries.forEachIndexed { index, entry ->
                            Box(
                                modifier = Modifier
                                    .weight(entry.percentage)
                                    .fillMaxHeight()
                                    .then(
                                        if (entry.color == TTColor.Zone7 || entry.color == TTColor.Zone6) {
                                            Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        } else Modifier
                                    )
                                    .background(entry.color)
                            )
                            if (index < activeEntries.lastIndex) {
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
            }

            // Aligned Per-Segment Percentage Labels (REQ-UI-398.2)
            if (activeEntries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeEntries.forEachIndexed { index, entry ->
                        BoxWithConstraints(
                            modifier = Modifier.weight(entry.percentage),
                            contentAlignment = Alignment.Center
                        ) {
                            val label = ZoneDistributionStackedBarMath.formatSegmentPercentage(entry.percentage)
                            var didOverflowWidth by remember(label, maxWidth) { mutableStateOf(false) }

                            if (ZoneDistributionStackedBarMath.canFitPercentageLabel(maxWidth.value, label) && !didOverflowWidth) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    onTextLayout = { layoutResult ->
                                        if (layoutResult.didOverflowWidth) {
                                            didOverflowWidth = true
                                        }
                                    }
                                )
                            }
                        }
                        if (index < activeEntries.lastIndex) {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                    }
                }
            }
        }

        // Responsive Wrapping Legend
        if (showLegend && distribution.entries.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                distribution.entries.forEach { entry ->
                    StackedBarLegendItem(
                        entry = entry,
                        swatchBorder = outlineBorder
                    )
                }
            }
        }
    }
}

@Composable
private fun StackedBarLegendItem(
    entry: ZoneTimeEntry,
    swatchBorder: BorderStroke,
    modifier: Modifier = Modifier
) {
    val isActive = entry.durationSec > 0L && entry.percentage > 0f
    val textAlpha = if (isActive) 1f else 0.45f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Swatch dot with uniform outline border
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(entry.color)
                .border(swatchBorder, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Z${entry.zoneIndex}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = ZoneDistributionChartMath.formatZoneDuration(entry.durationSec),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = String.format(Locale.getDefault(), "(%.0f%%)", entry.percentage),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
        )
    }
}
