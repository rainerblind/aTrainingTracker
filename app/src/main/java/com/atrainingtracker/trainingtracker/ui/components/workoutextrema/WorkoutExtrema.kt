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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

@Composable
fun WorkoutExtrema(
    data: ExtremaData,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // .padding(vertical = 8.dp)
    ) {
        if (data.isCalculating) {
            // Displays the message (e.g., "Calculating extrema values...")
            // from the data model if present, otherwise uses a default string resource.
            Text(
                text = data.calculationMessage ?: stringResource(R.string.calculating_extrema_values),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (data.dataRows.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // --- Table Header ---
                ExtremaRow(
                    label = "",
                    min = stringResource(R.string.min),
                    avg = stringResource(R.string.average),
                    max = stringResource(R.string.max),
                    unit = "",
                    isHeader = true
                )

                // --- Table Data Rows ---
                data.dataRows.filter { it.hasAnyData() }.forEach { row ->
                    ExtremaRow(
                        label = row.sensorLabel,
                        min = row.minValue ?: "-",
                        avg = row.avgValue ?: "-",
                        max = row.maxValue ?: "-",
                        unit = row.unitLabel
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
    isHeader: Boolean = false
) {
    val style = if (isHeader) {
        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    } else {
        MaterialTheme.typography.bodyMedium
    }

    val color = if (isHeader) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Column 1: Sensor Label (Weight 2.5)
        Text(
            text = label,
            modifier = Modifier.weight(2.5f),
            style = style,
            color = color
        )

        // Column 2: Min (Weight 2.0)
        Text(
            text = min,
            modifier = Modifier.weight(2.0f),
            style = style,
            color = color,
            textAlign = TextAlign.End
        )

        // Column 3: Average (Weight 2.0)
        Text(
            text = avg,
            modifier = Modifier.weight(2.0f),
            style = style,
            color = color,
            textAlign = TextAlign.End
        )

        // Column 4: Max (Weight 2.0)
        Text(
            text = max,
            modifier = Modifier.weight(2.0f),
            style = style,
            color = color,
            textAlign = TextAlign.End
        )

        // Column 5: Unit (Weight 1.5)
        Text(
            text = unit,
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Data View")
@Composable
fun PreviewWorkoutExtrema() {
    MaterialTheme {
        WorkoutExtrema(
            data = ExtremaData(
                workoutId = 123L,
                isCalculating = false,
                dataRows = listOf(
                    ExtremaDataRow("Heart Rate", "bpm", "65", "142", "185"),
                    ExtremaDataRow("Cadence", "rpm", "0", "85", "112"),
                    ExtremaDataRow("Power", "W", "0", "215", "640")
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
                isCalculating = true,
                calculationMessage = "Recalculating sensor extrema...",
                dataRows = emptyList()
            )
        )
    }
}

@Preview(showBackground = true, name = "Calculating HR")
@Composable
fun PreviewWorkoutExtremaCalculatingHR() {
    MaterialTheme {
        WorkoutExtrema(
            data = ExtremaData(
                workoutId = 123L,
                isCalculating = true,
                calculationMessage = "Calculating HR ...",
                dataRows = listOf(
                    ExtremaDataRow("Cadence", "rpm", "0", "85", "112"),
                    ExtremaDataRow("Power", "W", "0", "215", "640")
                )
            )
        )
    }
}

@Preview(showBackground = true, name = "Nothing to show")
@Composable
fun PreviewWorkoutExtremaNothing() {
    MaterialTheme {
        WorkoutExtrema(
            data = ExtremaData(
                workoutId = 123L,
                isCalculating = false,
                calculationMessage = null,
                dataRows = emptyList()
            )
        )
    }
}
