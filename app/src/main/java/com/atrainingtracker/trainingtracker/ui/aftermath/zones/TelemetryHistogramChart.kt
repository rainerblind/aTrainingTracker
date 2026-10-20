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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import java.util.Locale

/**
 * Interactive fine-grained frequency histogram composable for telemetry curves (Heart Rate & Cycling Power).
 *
 * Renders discrete vertical bars with 1 dp spacing shaded with active athlete zone colors.
 * Supports interactive touch tapping and drag scrubbing to inspect individual bin ranges,
 * exact duration, percentage of active time, and training zone index.
 *
 * For Cycling Power with an extended sprint tail (REQ-UI-419), seamlessly toggles between
 * the Main View (aerobic and threshold distribution with rightmost overflow bar) and an
 * expanded Sprint Tail View (high-wattage spikes with independent local Y-axis scaling and
 * leftmost return bar).
 */
@Composable
fun TelemetryHistogramChart(
    histogram: TelemetryHistogramData?,
    unit: String,
    modifier: Modifier = Modifier
) {
    if (histogram == null || histogram.bins.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(110.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "–",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var isSprintTailView by remember(histogram) { mutableStateOf(false) }
    var selectedBinIndex by remember(histogram, isSprintTailView) { mutableStateOf<Int?>(null) }

    val activeHistogram = if (isSprintTailView && histogram.sprintTail != null) {
        histogram.sprintTail
    } else {
        histogram
    }
    val bins = activeHistogram.bins
    val numBins = bins.size

    val maxDuration = remember(activeHistogram, isSprintTailView) {
        if (isSprintTailView) {
            bins.filter { !it.isReturn }.maxOfOrNull { it.durationSec }?.coerceAtLeast(1L) ?: 1L
        } else {
            bins.maxOfOrNull { it.durationSec }?.coerceAtLeast(1L) ?: 1L
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Readout Header Line
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val selectedBin = selectedBinIndex?.let { idx ->
                    bins.getOrNull(idx)
                }

                if (selectedBin != null) {
                    val durationFormatted = ZoneDistributionChartMath.formatZoneDuration(selectedBin.durationSec)
                    val pctFormatted = String.format(Locale.getDefault(), "%.1f", selectedBin.percentage)
                    val readoutColor = if (selectedBin.color == TTColor.Zone7) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        selectedBin.color
                    }
                    Text(
                        text = "${selectedBin.rangeMin}–${selectedBin.rangeMax} $unit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = readoutColor
                    )
                    Text(
                        text = "$durationFormatted ($pctFormatted%)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Z${selectedBin.zoneIndex}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = readoutColor
                    )
                } else if (histogram.tailCutoff == null) {
                    Text(
                        text = "${histogram.dataMin}–${histogram.dataMax} $unit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Δ ${histogram.binWidth} $unit",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!isSprintTailView) {
                    // Main View with symmetrical 2-segment Range Toggle (REQ-UI-419.3)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Segment 1: Main Range (Active - highlighted, non-clickable)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "${histogram.dataMin}–${histogram.tailCutoff} $unit",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Segment 2: Sprint Tail Range (Inactive - clickable switch chip)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clickable {
                                isSprintTailView = true
                                selectedBinIndex = null
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.power_histogram_overflow_label,
                                        histogram.tailCutoff,
                                        unit
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = stringResource(
                                        R.string.power_histogram_overflow_desc,
                                        histogram.tailCutoff,
                                        unit
                                    ),
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = "Δ ${histogram.binWidth} $unit",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    // Sprint Tail View with symmetrical 2-segment Range Toggle (REQ-UI-419.3)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Segment 1: Main Range (Inactive - clickable return chip)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clickable {
                                isSprintTailView = false
                                selectedBinIndex = null
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(
                                        R.string.power_histogram_return_desc,
                                        histogram.tailCutoff,
                                        unit
                                    ),
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(
                                        R.string.power_histogram_return_label,
                                        histogram.dataMin,
                                        histogram.tailCutoff,
                                        unit
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Segment 2: Sprint Tail Range (Active - highlighted, non-clickable)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "${histogram.tailCutoff}–${histogram.dataMax} $unit",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Δ ${histogram.binWidth} $unit",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

        // Discrete Histogram Bars Canvas with Touch/Drag Scrubbing
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .pointerInput(bins, isSprintTailView) {
                    detectTapGestures(
                        onTap = { offset ->
                            val binWidthWithGap = size.width / numBins.toFloat()
                            val clickedIndex = (offset.x / binWidthWithGap).toInt().coerceIn(0, numBins - 1)
                            val clickedBin = bins.getOrNull(clickedIndex)
                            if (clickedBin?.isOverflow == true) {
                                isSprintTailView = true
                                selectedBinIndex = null
                            } else if (clickedBin?.isReturn == true) {
                                isSprintTailView = false
                                selectedBinIndex = null
                            } else {
                                selectedBinIndex = if (selectedBinIndex == clickedIndex) null else clickedIndex
                            }
                        }
                    )
                }
                .pointerInput(bins, isSprintTailView) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val binWidthWithGap = size.width / numBins.toFloat()
                            val scrubIndex = (offset.x / binWidthWithGap).toInt().coerceIn(0, numBins - 1)
                            val scrubBin = bins.getOrNull(scrubIndex)
                            if (scrubBin?.isOverflow == true) {
                                isSprintTailView = true
                                selectedBinIndex = null
                            } else if (scrubBin?.isReturn == true) {
                                isSprintTailView = false
                                selectedBinIndex = null
                            } else {
                                selectedBinIndex = scrubIndex
                            }
                        },
                        onDrag = { change, _ ->
                            val binWidthWithGap = size.width / numBins.toFloat()
                            val scrubIndex = (change.position.x / binWidthWithGap).toInt().coerceIn(0, numBins - 1)
                            val scrubBin = bins.getOrNull(scrubIndex)
                            if (scrubBin?.isOverflow == true) {
                                isSprintTailView = true
                                selectedBinIndex = null
                            } else if (scrubBin?.isReturn == true) {
                                isSprintTailView = false
                                selectedBinIndex = null
                            } else {
                                selectedBinIndex = scrubIndex
                            }
                            change.consume()
                        }
                    )
                }
        ) {
            val totalWidth = size.width
            val totalHeight = size.height
            val spacingPx = 1.dp.toPx()
            val totalSpacing = (numBins - 1) * spacingPx
            val barWidthPx = ((totalWidth - totalSpacing) / numBins.toFloat()).coerceAtLeast(0.5f)

            for (i in 0 until numBins) {
                val bin = bins[i]
                val left = i * (barWidthPx + spacingPx)
                val isSelected = (selectedBinIndex == i)

                if (bin.isReturn) {
                    // Return Bar: full-height neutral outline pillar
                    val returnHeightPx = totalHeight
                    val returnTop = 0f
                    drawRoundRect(
                        color = bin.color.copy(alpha = 0.20f),
                        topLeft = Offset(left, returnTop),
                        size = Size(barWidthPx, returnHeightPx),
                        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                    )
                    drawRoundRect(
                        color = bin.color.copy(alpha = 0.70f),
                        topLeft = Offset(left, returnTop),
                        size = Size(barWidthPx, returnHeightPx),
                        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                } else {
                    val heightFraction = (bin.durationSec.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
                    val barHeightPx = when {
                        bin.isOverflow -> (heightFraction * totalHeight).coerceAtLeast(6.dp.toPx())
                        bin.durationSec > 0L -> (heightFraction * totalHeight).coerceAtLeast(2.dp.toPx())
                        else -> 1.dp.toPx() // baseline tick for zero duration bins
                    }
                    val top = totalHeight - barHeightPx

                    val alpha = when {
                        isSelected -> 1.0f
                        selectedBinIndex != null -> 0.45f
                        bin.durationSec == 0L -> 0.20f
                        else -> 0.90f
                    }

                    drawRoundRect(
                        color = bin.color.copy(alpha = alpha),
                        topLeft = Offset(left, top),
                        size = Size(barWidthPx, barHeightPx),
                        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                    )

                    if (isSelected) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(left - 0.5f, top - 1f),
                            size = Size(barWidthPx + 1f, barHeightPx + 1f),
                            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    } else if (bin.color == TTColor.Zone7 || bin.color == TTColor.Zone6) {
                        drawRoundRect(
                            color = outlineVariantColor,
                            topLeft = Offset(left, top),
                            size = Size(barWidthPx, barHeightPx),
                            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                            style = Stroke(width = 0.75.dp.toPx())
                        )
                    }
                }
            }
        }

        // X-Axis Bounds Readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val minText: String
            val midText: String
            val maxText: String

            if (histogram.tailCutoff == null) {
                minText = "${histogram.dataMin} $unit"
                midText = "${(histogram.dataMin + histogram.dataMax) / 2} $unit"
                maxText = "${histogram.dataMax} $unit"
            } else if (!isSprintTailView) {
                minText = "${histogram.dataMin} $unit"
                midText = "${(histogram.dataMin + histogram.tailCutoff) / 2} $unit"
                maxText = "${histogram.tailCutoff} $unit"
            } else {
                minText = "${histogram.tailCutoff} $unit"
                midText = "${(histogram.tailCutoff + histogram.dataMax) / 2} $unit"
                maxText = "${histogram.dataMax} $unit"
            }

            Text(
                text = minText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = midText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = maxText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
