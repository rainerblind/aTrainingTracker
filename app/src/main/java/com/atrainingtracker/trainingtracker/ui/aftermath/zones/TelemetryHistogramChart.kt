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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Interactive fine-grained frequency histogram composable for telemetry curves (Heart Rate & Cycling Power).
 *
 * Renders discrete vertical bars with 1 dp spacing shaded with active athlete zone colors.
 * Supports interactive touch tapping and drag scrubbing to inspect individual bin ranges,
 * exact duration, percentage of active time, and training zone index.
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

    var selectedBinIndex by remember { mutableStateOf<Int?>(null) }
    val maxDuration = remember(histogram) {
        histogram.bins.maxOfOrNull { it.durationSec }?.coerceAtLeast(1L) ?: 1L
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
                    histogram.bins.getOrNull(idx)
                }

                if (selectedBin != null) {
                    val durationFormatted = ZoneDistributionChartMath.formatZoneDuration(selectedBin.durationSec)
                    val pctFormatted = String.format(Locale.getDefault(), "%.1f", selectedBin.percentage)
                    Text(
                        text = "${selectedBin.rangeMin}–${selectedBin.rangeMax} $unit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = selectedBin.color
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
                        color = selectedBin.color
                    )
                } else {
                    Text(
                        text = "${histogram.dataMin}–${histogram.dataMax} $unit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${histogram.bins.count { it.durationSec > 0 }} active bins",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Δ ${histogram.binWidth} $unit",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Discrete Histogram Bars Canvas with Touch/Drag Scrubbing
        val bins = histogram.bins
        val numBins = bins.size

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .pointerInput(bins) {
                    detectTapGestures(
                        onTap = { offset ->
                            val binWidthWithGap = size.width / numBins.toFloat()
                            val clickedIndex = (offset.x / binWidthWithGap).toInt().coerceIn(0, numBins - 1)
                            selectedBinIndex = if (selectedBinIndex == clickedIndex) null else clickedIndex
                        }
                    )
                }
                .pointerInput(bins) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val binWidthWithGap = size.width / numBins.toFloat()
                            selectedBinIndex = (offset.x / binWidthWithGap).toInt().coerceIn(0, numBins - 1)
                        },
                        onDrag = { change, _ ->
                            val binWidthWithGap = size.width / numBins.toFloat()
                            selectedBinIndex = (change.position.x / binWidthWithGap).toInt().coerceIn(0, numBins - 1)
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

                val heightFraction = (bin.durationSec.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
                val barHeightPx = if (bin.durationSec > 0L) {
                    (heightFraction * totalHeight).coerceAtLeast(2.dp.toPx())
                } else {
                    1.dp.toPx() // baseline tick for zero duration bins
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
                }
            }
        }

        // X-Axis Bounds Readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${histogram.dataMin} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(histogram.dataMin + histogram.dataMax) / 2} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${histogram.dataMax} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
