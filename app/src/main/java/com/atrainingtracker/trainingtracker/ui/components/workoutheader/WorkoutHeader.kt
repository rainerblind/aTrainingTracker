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

package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

@Composable
fun WorkoutHeader(
    data: WorkoutHeaderData,
    onClicked: () -> Unit,
    onExport: (FileFormat) -> Unit,
    onSaveAsRoute: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
    menuEnabled: Boolean = true
) {
    // State to control menu visibility
    var showMenu by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = if (menuEnabled) {
            modifier.fillMaxWidth()
                .combinedClickable(
                    onClick = onClicked,
                    onLongClick = { showContextMenu = true }
                )
        }
        else {
            modifier.fillMaxWidth()
        },
        color = Color.Transparent
    ) {
        // Box allows us to place the Menu Button at the absolute top-right
        Box(modifier = Modifier.fillMaxWidth()) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. TOP ROW: Sport Icon and Workout Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = data.bSportType.iconResId),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        colorFilter = null // Original color
                    )
                    Text(
                        text = data.workoutName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Spacer for the Menu Button area
                    Spacer(modifier = Modifier.width(32.dp))
                }

                // 2. BOTTOM CONTENT: Organized in horizontal rows
                // Row A: Sport Specific Info (Sport Name, Equipment, Commute/Trainer)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = data.sportName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    data.equipmentName?.let { equipmentName ->
                        Text(
                            text = equipmentName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Trainer / Commute Logic
                    if (data.trainer || data.commute) {
                        val label = when {
                            data.commute -> stringResource(R.string.commute)
                            else -> stringResource(data.bSportType.indoorEquipmentResId)
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Row B: Date and Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconTextRow(
                        iconRes = R.drawable.ic_date_start,
                        text = data.formattedDate
                    )
                    IconTextRow(
                        iconRes = R.drawable.ic_time_start,
                        text = data.formattedTime
                    )
                }
            }

            // 4. Menu Button (Pinned to Top-End)
            if (menuEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd) // This moves it to the right
                        .padding(top = 4.dp, end = 4.dp)
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_more_vert_24),
                            contentDescription = stringResource(R.string.ExportFiles),
                        )
                    }

                    // Material 3 Dropdown Menu for exporting to various file formats.
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        // 1. Define standard formats
                        val standardFormats = listOf(
                            FileFormat.TCX to R.string.tcxWrite,
                            FileFormat.GPX to R.string.gpxWrite,
                            FileFormat.CSV to R.string.csvWrite,
                            FileFormat.GC to R.string.jsonWrite
                        )

                        // 2. Render standard formats
                        standardFormats.forEach { (format, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    showMenu = false
                                    onExport(format)
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_as_route)) },
                            onClick = {
                                showMenu = false
                                onSaveAsRoute()
                            },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_route), contentDescription = null) }
                        )

                        // Check if Strava is globally enabled AND if this workout specifically allows it
                        val stravaGloballyEnabled = TrainingApplication.uploadToCommunity(FileFormat.STRAVA)
                        val stravaMappingAvailable = data.stravaSportName != null
                        val stravaIndividuallyEnabled = data.uploadToStrava != 0

                        if (stravaGloballyEnabled && stravaIndividuallyEnabled && stravaMappingAvailable) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.logo_square_strava),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = Color.Unspecified
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.stravaUpload))
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    onExport(FileFormat.STRAVA)
                                }
                            )
                        }
                    }

                    // Context Menu for deletion
                    DropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = { showContextMenu = false; onDeleteRequest() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper to mimic the drawableStart + drawablePadding behavior from your XML
 */
@Composable
private fun IconTextRow(iconRes: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// --- PREVIEWS ---

/**
 * Parameter Provider allows us to test multiple data states in the same Preview block
 */
class WorkoutHeaderPreviewProvider : PreviewParameterProvider<WorkoutHeaderData> {
    override val values = sequenceOf(
        // Case 1: Standard Finished Ride
        WorkoutHeaderData(
            workoutName = "Afternoon Ride",
            formattedDate = "Tuesday, Apr 21",
            formattedTime = "14:30",
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            finished = true,
            equipmentName = "Specialized Tarmac",
            startTimeS = 0,
            commute = false,
            trainer = false,
            uploadToStrava = 0,
            stravaSportName = "Ride"
        ),
        // Case 2: Commute / Trainer Run (Testing Chips)
        WorkoutHeaderData(
            workoutName = "Morning Run",
            formattedDate = "Wednesday, Apr 22",
            formattedTime = "08:00",
            sportName = "Running",
            bSportType = BSportType.RUN,
            finished = true,
            commute = false,
            trainer = true,
            startTimeS = 0,
            equipmentName = null,
            uploadToStrava = 1,
            stravaSportName = "Run"
        ),
        // Case 3: No Strava Mapping
        WorkoutHeaderData(
            workoutName = "Private Walk",
            formattedDate = "Thursday, Apr 23",
            formattedTime = "10:00",
            sportName = "Walk",
            bSportType = BSportType.RUN,
            finished = true,
            commute = false,
            trainer = false,
            startTimeS = 0,
            equipmentName = null,
            uploadToStrava = 1,
            stravaSportName = "NONE"
        )
    )
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewWorkoutHeader(
    @PreviewParameter(WorkoutHeaderPreviewProvider::class) data: WorkoutHeaderData
) {
    ATrainingTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WorkoutHeader(
                data = data,
                onClicked = {},
                onExport = {},
                onSaveAsRoute = {},
                onDeleteRequest = {}
            )
        }
    }
}

@Preview(name = "Commute Tag", showBackground = true)
@Composable
fun PreviewCommuteHeader() {
    ATrainingTrackerTheme {
        WorkoutHeader(
            data = WorkoutHeaderData(
                workoutName = "Ride to Work",
                formattedDate = "Today",
                formattedTime = "09:00",
                sportName = "Cycling",
                bSportType = BSportType.BIKE,
                commute = true,
                finished = true,
                startTimeS = 0,
                equipmentName = null,
                trainer = false,
                uploadToStrava = -1,
                stravaSportName = "Ride"
            ),
            onClicked = {},
            onExport = {},
            onSaveAsRoute = {},
            onDeleteRequest = {}
        )
    }
}