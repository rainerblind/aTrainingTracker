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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.atrainingtracker.trainingtracker.ui.theme.Zone1
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.LightBackground
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldView
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize

interface GridActions {
    fun onEditField(fieldState: SensorFieldState)
    fun onDeleteField(fieldState: SensorFieldState)
    fun onAddRow(beforeRow: Int)
    fun onAddCol(atRow: Int, beforeCol: Int)
}

/**
 * A generic screen that displays a grid of sensor fields for either tracking or configuration.
 * It adapts its UI and behavior based on the provided [screenMode].
 */
@Composable
fun SensorGridScreen(
    state: TrackingScreenState,
    screenMode: ScreenMode,
    gridActions: GridActions,
    showMap: Boolean = false,
    mapContent: @Composable () -> Unit = {}
) {
    Column(Modifier.fillMaxSize()) {
        val fieldsByRow = state.fields.groupBy { it.rowNr }
        val sortedRows = fieldsByRow.keys.sorted()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var maxRowNr = 0
            sortedRows.forEach { rowNr ->
                maxRowNr = rowNr
                // --- ADD field BETWEEN ROWS ---
                if (screenMode == ScreenMode.CONFIGURATION) {
                    RowAdder(onClick = { gridActions.onAddRow(rowNr) })
                }

                val fieldsInThisRow = fieldsByRow[rowNr]?.sortedBy { it.colNr } ?: emptyList()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(IntrinsicSize.Min) // Important for vertical adder alignment
                ) {

                    var maxColNr = 0
                    fieldsInThisRow.forEach { fieldState ->
                        // --- ADD Field BETWEEN FIELDS ---
                        if (screenMode == ScreenMode.CONFIGURATION) {
                            ColAdder(onClick = { gridActions.onAddCol(rowNr,fieldState.colNr) } )
                        }
                        maxColNr = fieldState.colNr
                        Box(modifier = Modifier.weight(1f)) {
                            SensorFieldView(
                                fieldState = fieldState,
                                screenMode = screenMode,
                                onEdit = { gridActions.onEditField(fieldState) },
                                onDelete = { gridActions.onDeleteField(fieldState) }
                            )
                        }
                    }
                    // --- ADD Field AT END OF the ROW ---
                    if (screenMode == ScreenMode.CONFIGURATION) {
                        ColAdder(onClick = { gridActions.onAddCol(rowNr, maxColNr + 1) })
                    }

                }
            }
            // -- ADD Field as a new row
            if (screenMode == ScreenMode.CONFIGURATION) {
                RowAdder(onClick = { gridActions.onAddRow(maxRowNr + 1) })
            }
        }


        // Conditionally display the map
        if (showMap) {
            Box(modifier = Modifier.weight(1f)) {
                mapContent()
            }
        }
    }
}

@Composable
private fun RowAdder(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Row",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ColAdder(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.fillMaxHeight()) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Column",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}


// Preview for Configuration Mode
@Preview(showBackground = true, name = "Config Mode")
@Composable
fun SensorGridScreenConfigPreview() {
    ATrainingTrackerTheme {
        val previewFields = listOf(
            SensorFieldState(configHash = 1, sensorFieldId = 1, rowNr = 0, colNr = 0, viewSize = ViewSize.NORMAL, label = "Pace", value = "5:31", units = "/min", zoneColor = LightBackground, filterDescription = "GPS: 5s avg"),
            SensorFieldState(configHash = 2, sensorFieldId = 2, rowNr = 0, colNr = 1, viewSize = ViewSize.NORMAL, label = "Heart Rate", value = "145", units = "bpm", zoneColor = Zone1, filterDescription = ""),
            SensorFieldState(configHash = 3, sensorFieldId = 3, rowNr = 1, colNr = 0, viewSize = ViewSize.NORMAL, label = "Distance", value = "10.3", units = "km", zoneColor = LightBackground, filterDescription = "")
        )
        val mockActions = object : GridActions {
            override fun onEditField(fieldState: SensorFieldState) {}
            override fun onDeleteField(fieldState: SensorFieldState) {}
            override fun onAddRow(beforeRow: Int) {}
            override fun onAddCol(atRow: Int, beforeCol: Int) {}
        }
        SensorGridScreen(
            state = TrackingScreenState(fields = previewFields),
            screenMode = ScreenMode.CONFIGURATION,
            gridActions = mockActions
        )
    }
}

// Preview for Tracking Mode
@Preview(showBackground = true, name = "Tracking Mode")
@Composable
fun SensorGridScreenTrackingPreview() {
    ATrainingTrackerTheme {
        val previewFields = listOf(
            SensorFieldState(configHash = 1, sensorFieldId = 1, rowNr = 0, colNr = 0, viewSize = ViewSize.LARGE, label = "Pace", value = "5:31", units = "/min", zoneColor = LightBackground, filterDescription = "GPS: 5s avg"),
            SensorFieldState(configHash = 2, sensorFieldId = 2, rowNr = 0, colNr = 1, viewSize = ViewSize.LARGE, label = "Heart Rate", value = "145", zoneColor = Zone1, units = "bpm", filterDescription = ""),
            SensorFieldState(configHash = 3, sensorFieldId = 3, rowNr = 1, colNr = 0, viewSize = ViewSize.NORMAL, label = "Distance", value = "10.3", units = "km", zoneColor = LightBackground, filterDescription = "")
        )
        val mockActions = object : GridActions {
            override fun onEditField(fieldState: SensorFieldState) {}
            override fun onDeleteField(fieldState: SensorFieldState) {}
            override fun onAddRow(beforeRow: Int) {}
            override fun onAddCol(atRow: Int, beforeCol: Int) {}
        }
        SensorGridScreen(
            state = TrackingScreenState(fields = previewFields),
            screenMode = ScreenMode.TRACKING,
            gridActions = mockActions
        )
    }
}