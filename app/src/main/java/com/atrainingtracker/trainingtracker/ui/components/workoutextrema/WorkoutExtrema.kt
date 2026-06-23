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

package com.atrainingtracker.trainingtracker.ui.components.workoutextrema

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R

@Composable
fun WorkoutExtrema(
    data: ExtremaData,
    modifier: Modifier = Modifier
) {
    if (data.dataRows.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // --- Table Header (Flat and Subtle) ---
        ExtremaRow(
            label = "",
            min = stringResource(R.string.min),
            avg = "Ø",
            max = stringResource(R.string.max),
            unit = "",
            isHeader = true,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // --- Table Data Rows ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            val rows = data.dataRows.filter { it.hasAnyData() }
            rows.forEachIndexed { index, row ->
                ExtremaRow(
                    label = row.sensorLabel,
                    min = if (row.isMinRelevant) (row.minValue ?: "-") else "-",
                    avg = row.avgValue ?: "-",
                    max = row.maxValue ?: "-",
                    unit = row.unitLabel,
                    iconResId = row.iconResId,
                    boldMin = row.boldMin,
                    boldAvg = row.boldAvg,
                    boldMax = row.boldMax,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                if (index < rows.size - 1) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 28.dp) // Align divider with text after icon
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtremaRow(
    label: String,
    min: String,
    avg: String,
    max: String,
    unit: String,
    modifier: Modifier = Modifier,
    iconResId: Int? = null,
    boldMin: Boolean = false,
    boldAvg: Boolean = false,
    boldMax: Boolean = false,
    isHeader: Boolean = false
) {
    val style = if (isHeader) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.bodyLarge
    }

    val color = if (isHeader) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // World-class typography: disable font padding to allow true baseline alignment
    val baseStyle = style.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Bottom,
            trim = LineHeightStyle.Trim.Both
        )
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        // Column 0: Icon
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            if (iconResId != null && !isHeader) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Column 1: Sensor Label + Unit (Weight 3.5)
        Row(
            modifier = Modifier.weight(3.5f),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = label,
                style = if (isHeader) baseStyle else baseStyle.copy(fontSize = 16.sp),
                color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (unit.isNotEmpty() && !isHeader) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "[$unit]",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }

        // Column 2: Min (Weight 2.0)
        val isMinDash = min == "-"
        Text(
            text = min,
            modifier = Modifier.weight(2.0f),
            style = if (boldMin && !isHeader && !isMinDash) baseStyle.copy(fontWeight = FontWeight.Bold) else baseStyle,
            color = if (isMinDash) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else color,
            textAlign = TextAlign.End
        )

        // Column 3: Average (Weight 2.0)
        val isAvgDash = avg == "-"
        Text(
            text = avg,
            modifier = Modifier.weight(2.0f),
            style = if (((boldAvg && !isHeader) || (isHeader && avg == "Ø")) && !isAvgDash) baseStyle.copy(fontWeight = FontWeight.Bold) else baseStyle,
            color = if (isAvgDash) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else color,
            textAlign = TextAlign.End
        )

        // Column 4: Max (Weight 2.0)
        val isMaxDash = max == "-"
        Text(
            text = max,
            modifier = Modifier.weight(2.0f),
            style = if (boldMax && !isHeader && !isMaxDash) baseStyle.copy(fontWeight = FontWeight.Bold) else baseStyle,
            color = if (isMaxDash) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else color,
            textAlign = TextAlign.End
        )
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Core Performance Metrics")
@Composable
fun PreviewAllCoreSensors() {
    MaterialTheme {
        WorkoutExtrema(
            data = ExtremaData(
                workoutId = 123L,
                dataRows = listOf(
                    ExtremaDataRow(sensorLabel = "Power", unitLabel = "W", minValue = "0", avgValue = "215", maxValue = "640", iconResId = R.drawable.ic_power, isMinRelevant = false, boldAvg = true, boldMax = true),
                    ExtremaDataRow(sensorLabel = "Heart Rate", unitLabel = "bpm", minValue = "65", avgValue = "142", maxValue = "185", iconResId = R.drawable.ic_heart_rate, boldAvg = true, boldMax = true),
                    ExtremaDataRow(sensorLabel = "Speed", unitLabel = "km/h", minValue = "0.0", avgValue = "24.5", maxValue = "52.1", iconResId = R.drawable.ic_speed, isMinRelevant = false, boldAvg = true),
                    ExtremaDataRow(sensorLabel = "Pace", unitLabel = "min/km", minValue = "4:30", avgValue = "5:12", maxValue = "6:45", iconResId = R.drawable.ic_speed, isMinRelevant = true, boldAvg = true),
                    ExtremaDataRow(sensorLabel = "Cadence", unitLabel = "rpm", minValue = "0", avgValue = "85", maxValue = "112", iconResId = R.drawable.ic_cadence, isMinRelevant = false, boldAvg = true),
                    ExtremaDataRow(sensorLabel = "Altitude", unitLabel = "m", minValue = "150", avgValue = "210", maxValue = "410", iconResId = R.drawable.ic_altitude, boldMin = true, boldMax = true),
                    ExtremaDataRow(sensorLabel = "Temp", unitLabel = "°C", minValue = "18", avgValue = "22", maxValue = "25", iconResId = R.drawable.ic_temp_max, boldMin = true, boldMax = true)
                )
            )
        )
    }
}

@Preview(showBackground = true, name = "Calculating State")
@Composable
fun PreviewWorkoutExtremaCalculating() {
    MaterialTheme {
        WorkoutExtrema(
            data = ExtremaData(
                workoutId = 123L,
                dataRows = emptyList()
            )
        )
    }
}
