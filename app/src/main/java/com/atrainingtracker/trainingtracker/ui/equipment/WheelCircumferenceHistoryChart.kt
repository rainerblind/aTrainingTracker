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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.ui.equipment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WheelCircumferenceRecord
import com.atrainingtracker.trainingtracker.database.WheelPosition

/**
 * Historical time-series chart rendering Front and Rear wheel circumferences over time (REQ-SEN-030.4, TST-SEN-030.4, ATT-7753).
 *
 * Includes baseline pump reset action ("Reifen gerade aufgepumpt (Baseline setzen)") and zero-clipping Canvas rendering (Rule 84).
 */
@Composable
fun WheelCircumferenceHistoryChart(
    historyRecords: List<WheelCircumferenceRecord>,
    onResetBaseline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wheel_circumference_history_chart"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Chart Title
            Text(
                text = stringResource(R.string.equipment_tpms_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Chart Legends (Front & Rear)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(
                    color = MaterialTheme.colorScheme.primary,
                    label = stringResource(R.string.equipment_tpms_front_legend)
                )
                LegendItem(
                    color = MaterialTheme.colorScheme.secondary,
                    label = stringResource(R.string.equipment_tpms_rear_legend)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (historyRecords.isEmpty()) {
                // Empty state card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.equipment_tpms_no_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Render Canvas Chart
                val frontRecords = historyRecords.filter { it.wheelPosition == WheelPosition.FRONT }
                val rearRecords = historyRecords.filter { it.wheelPosition == WheelPosition.REAR }

                val allCircumferences = historyRecords.map { it.circumferenceMm }
                val minCirc = (allCircumferences.minOrNull() ?: 2050) - 20
                val maxCirc = (allCircumferences.maxOrNull() ?: 2150) + 20
                val circRange = (maxCirc - minCirc).coerceAtLeast(10)

                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val outlineColor = MaterialTheme.colorScheme.outlineVariant

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // Draw grid reference lines
                    val steps = 3
                    for (i in 0..steps) {
                        val y = h * (i.toFloat() / steps)
                        drawLine(
                            color = outlineColor.copy(alpha = 0.4f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Helper to draw series
                    fun drawSeries(records: List<WheelCircumferenceRecord>, seriesColor: Color) {
                        if (records.isEmpty()) return

                        if (records.size == 1) {
                            val r = records.first()
                            val cy = h - ((r.circumferenceMm - minCirc).toFloat() / circRange) * h
                            drawCircle(
                                color = seriesColor,
                                radius = 5.dp.toPx(),
                                center = Offset(w / 2f, cy)
                            )
                            return
                        }

                        val path = Path()
                        val stepX = w / (records.size - 1).coerceAtLeast(1)

                        records.forEachIndexed { idx, record ->
                            val cx = idx * stepX
                            val cy = h - ((record.circumferenceMm - minCirc).toFloat() / circRange) * h
                            if (idx == 0) {
                                path.moveTo(cx, cy)
                            } else {
                                path.lineTo(cx, cy)
                            }
                        }

                        drawPath(
                            path = path,
                            color = seriesColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        records.forEachIndexed { idx, record ->
                            val cx = idx * stepX
                            val cy = h - ((record.circumferenceMm - minCirc).toFloat() / circRange) * h
                            drawCircle(
                                color = seriesColor,
                                radius = 4.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                        }
                    }

                    drawSeries(frontRecords, primaryColor)
                    drawSeries(rearRecords, secondaryColor)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Baseline Reset Action Button
            Button(
                onClick = onResetBaseline,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wheel_circumference_reset_baseline_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.equipment_tpms_reset_baseline),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, name = "Wheel Circumference History Chart Preview")
@Composable
private fun PreviewWheelCircumferenceHistoryChart() {
    val sampleHistory = listOf(
        WheelCircumferenceRecord(1, 1, 1000L, WheelPosition.FRONT, 2105),
        WheelCircumferenceRecord(2, 1, 1000L, WheelPosition.REAR, 2102),
        WheelCircumferenceRecord(3, 1, 2000L, WheelPosition.FRONT, 2103),
        WheelCircumferenceRecord(4, 1, 2000L, WheelPosition.REAR, 2098),
        WheelCircumferenceRecord(5, 1, 3000L, WheelPosition.FRONT, 2102),
        WheelCircumferenceRecord(6, 1, 3000L, WheelPosition.REAR, 2091)
    )

    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            WheelCircumferenceHistoryChart(
                historyRecords = sampleHistory,
                onResetBaseline = {}
            )
        }
    }
}
